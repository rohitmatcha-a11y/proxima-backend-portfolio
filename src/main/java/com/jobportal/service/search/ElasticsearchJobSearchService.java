package com.jobportal.service.search;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.json.JsonData;
import com.jobportal.api.dto.JobSearchRequest;
import com.jobportal.domain.document.JobDocument;
import org.springframework.data.elasticsearch.core.SearchHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchJobSearchService {

    private final ElasticsearchOperations esOps;

    public SearchHits<JobDocument> search(JobSearchRequest request) {
        List<Query> filters = new ArrayList<>();

        filters.add(TermQuery.of(t -> t.field("status").value("ACTIVE"))._toQuery());

        if (request.getCompanySlugs() != null && !request.getCompanySlugs().isEmpty()) {
            filters.add(TermsQuery.of(t -> t.field("companySlug")
                    .terms(tv -> tv.value(request.getCompanySlugs().stream()
                            .map(s -> co.elastic.clients.elasticsearch._types.FieldValue.of(s))
                            .toList())))._toQuery());
        } else if (request.getCompanySlug() != null) {
            filters.add(TermQuery.of(t -> t.field("companySlug").value(request.getCompanySlug()))._toQuery());
        }

        if (request.getWorkMode() != null && !request.getWorkMode().isEmpty()) {
            filters.add(TermsQuery.of(t -> t.field("workMode")
                    .terms(tv -> tv.value(request.getWorkMode().stream()
                            .map(s -> co.elastic.clients.elasticsearch._types.FieldValue.of(s))
                            .toList())))._toQuery());
        }

        if (request.getExperienceLevel() != null && !request.getExperienceLevel().isEmpty()) {
            filters.add(TermsQuery.of(t -> t.field("experienceLevel")
                    .terms(tv -> tv.value(request.getExperienceLevel().stream()
                            .map(s -> co.elastic.clients.elasticsearch._types.FieldValue.of(s))
                            .toList())))._toQuery());
        }

        if (request.getVisaStatus() != null && !request.getVisaStatus().isEmpty()) {
            filters.add(TermsQuery.of(t -> t.field("visaStatus")
                    .terms(tv -> tv.value(request.getVisaStatus().stream()
                            .map(s -> co.elastic.clients.elasticsearch._types.FieldValue.of(s))
                            .toList())))._toQuery());
        }

        if (request.getCountries() != null && !request.getCountries().isEmpty()) {
            filters.add(TermsQuery.of(t -> t.field("country")
                    .terms(tv -> tv.value(request.getCountries().stream()
                            .map(s -> co.elastic.clients.elasticsearch._types.FieldValue.of(s))
                            .toList())))._toQuery());
        } else if (request.getCountry() != null) {
            filters.add(TermQuery.of(t -> t.field("country").value(request.getCountry()))._toQuery());
        }

        if (request.getContinents() != null && !request.getContinents().isEmpty()) {
            filters.add(TermsQuery.of(t -> t.field("continent")
                    .terms(tv -> tv.value(request.getContinents().stream()
                            .map(s -> co.elastic.clients.elasticsearch._types.FieldValue.of(s))
                            .toList())))._toQuery());
        } else if (request.getContinent() != null) {
            filters.add(TermQuery.of(t -> t.field("continent").value(request.getContinent()))._toQuery());
        }

        if (request.getJobType() != null && !request.getJobType().isEmpty()) {
            filters.add(TermsQuery.of(t -> t.field("jobType")
                    .terms(tv -> tv.value(request.getJobType().stream()
                            .map(s -> co.elastic.clients.elasticsearch._types.FieldValue.of(s))
                            .toList())))._toQuery());
        }

        // Company size categories
        if (request.getCompanySizeCategory() != null && !request.getCompanySizeCategory().isEmpty()) {
            filters.add(TermsQuery.of(t -> t.field("companySizeCategory.keyword")
                    .terms(tv -> tv.value(request.getCompanySizeCategory().stream()
                            .map(s -> co.elastic.clients.elasticsearch._types.FieldValue.of(s))
                            .toList())))._toQuery());
        }

        if (Boolean.TRUE.equals(request.getRemoteOnly())) {
            filters.add(TermQuery.of(t -> t.field("workMode").value("REMOTE"))._toQuery());
        }

        if (Boolean.TRUE.equals(request.getVisaSponsorshipOnly())) {
            filters.add(TermQuery.of(t -> t.field("visaStatus").value("SPONSORED"))._toQuery());
        }

        Instant from = request.getPostedAfter();
        Instant to = request.getPostedBefore();
        if (from == null && request.getDatePosted() != null && !request.getDatePosted().isBlank()) {
            from = switch (request.getDatePosted().toLowerCase()) {
                case "24h", "1d"    -> Instant.now().minusSeconds(86400);
                case "3d"           -> Instant.now().minusSeconds(3 * 86400);
                case "7d", "week"   -> Instant.now().minusSeconds(7 * 86400);
                case "30d", "month" -> Instant.now().minusSeconds(30L * 86400);
                default -> null;
            };
        }
        if (from != null || to != null) {
            final Instant finalFrom = from;
            final Instant finalTo = to;
            filters.add(RangeQuery.of(r -> {
                r.field("postedAt");
                if (finalFrom != null) r.gte(JsonData.of(finalFrom.toString()));
                if (finalTo != null) r.lte(JsonData.of(finalTo.toString()));
                return r;
            })._toQuery());
        }

        Query boolQuery;
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            boolQuery = BoolQuery.of(b -> b
                    .must(MultiMatchQuery.of(m -> m
                            .query(request.getKeyword())
                            .fields("title^3", "description")
                            .type(TextQueryType.BestFields))._toQuery())
                    .filter(filters))._toQuery();
        } else {
            boolQuery = BoolQuery.of(b -> b.filter(filters))._toQuery();
        }

        List<co.elastic.clients.elasticsearch._types.SortOptions> sortOptions = new ArrayList<>();
        if (!"relevance".equals(request.getSortBy()) || request.getKeyword() == null) {
            String sortField = "salary".equals(request.getSortBy()) ? "salaryMax" : "title".equals(request.getSortBy()) ? "title.keyword" : "postedAt";
            SortOrder order = "asc".equalsIgnoreCase(request.getSortDir()) ? SortOrder.Asc : SortOrder.Desc;
            sortOptions.add(co.elastic.clients.elasticsearch._types.SortOptions.of(
                    s -> s.field(f -> f.field(sortField).order(order))));
        }

        final Query finalQuery = boolQuery;
        final List<co.elastic.clients.elasticsearch._types.SortOptions> finalSort = sortOptions;

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(finalQuery)
                .withSort(finalSort)
                .withPageable(PageRequest.of(request.getPage(), request.getSize()))
                .build();

        return esOps.search(nativeQuery, JobDocument.class);
    }

    public List<JobDocument> findSimilarDocuments(String jobId, String title, String companySlug, String workMode) {
        List<Query> shoulds = new ArrayList<>();

        shoulds.add(MoreLikeThisQuery.of(m -> m
                .fields("title")
                .like(l -> l.document(d -> d.index("jobs").id(jobId)))
                .minTermFreq(1)
                .minDocFreq(1)
                .maxQueryTerms(6))._toQuery());

        shoulds.add(MatchQuery.of(m -> m
                .field("title")
                .query(title)
                .minimumShouldMatch("40%"))._toQuery());

        if (companySlug != null) {
            shoulds.add(TermQuery.of(t -> t.field("companySlug").value(companySlug))._toQuery());
        }

        Query finalQuery = BoolQuery.of(b -> b
                .should(shoulds)
                .filter(TermQuery.of(t -> t.field("status").value("ACTIVE"))._toQuery())
                .mustNot(IdsQuery.of(i -> i.values(jobId))._toQuery())
                .minimumShouldMatch("1"))._toQuery();

        NativeQuery query = NativeQuery.builder()
                .withQuery(finalQuery)
                .withPageable(PageRequest.of(0, 5))
                .build();

        return esOps.search(query, JobDocument.class).stream()
                .map(SearchHit::getContent)
                .toList();
    }
}

package com.jobportal.service.search;

import com.jobportal.api.dto.JobSearchRequest;
import com.jobportal.domain.entity.Job;
import com.jobportal.domain.enums.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class JobSpecification {

    private JobSpecification() {}

    public static Specification<Job> buildSpec(JobSearchRequest req) {
        Specification<Job> spec = Specification.where(isActive());

        if (hasText(req.getKeyword())) {
            spec = spec.and(keywordSearch(req.getKeyword()));
        }

        // Single company slug (backward compat)
        if (hasText(req.getCompanySlug())) {
            spec = spec.and(byCompanySlug(req.getCompanySlug()));
        }

        // Multiple company slugs
        if (notEmpty(req.getCompanySlugs())) {
            spec = spec.and(byCompanySlugs(req.getCompanySlugs()));
        }

        // Single continent (backward compat, case-insensitive)
        if (hasText(req.getContinent())) {
            spec = spec.and(byContinent(req.getContinent()));
        }

        // Multiple continents
        if (notEmpty(req.getContinents())) {
            spec = spec.and(byContinents(req.getContinents()));
        }

        // Single country (backward compat, case-insensitive)
        if (hasText(req.getCountry())) {
            spec = spec.and(byCountry(req.getCountry()));
        }

        // Multiple countries
        if (notEmpty(req.getCountries())) {
            spec = spec.and(byCountries(req.getCountries()));
        }

        if (hasText(req.getCity())) {
            spec = spec.and(byCity(req.getCity()));
        }

        if (notEmpty(req.getJobType())) {
            spec = spec.and(byJobTypes(req.getJobType()));
        }

        if (notEmpty(req.getWorkMode())) {
            spec = spec.and(byWorkModes(req.getWorkMode()));
        }

        if (notEmpty(req.getExperienceLevel())) {
            spec = spec.and(byExperienceLevels(req.getExperienceLevel()));
        }

        if (notEmpty(req.getIndustry())) {
            spec = spec.and(byIndustries(req.getIndustry()));
        }

        if (notEmpty(req.getVisaStatus())) {
            spec = spec.and(byVisaStatuses(req.getVisaStatus()));
        }

        if (notEmpty(req.getCompanySizeCategory())) {
            spec = spec.and(byCompanySizeCategories(req.getCompanySizeCategory()));
        }

        if (Boolean.TRUE.equals(req.getRemoteOnly())) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("isRemoteFriendly")));
        }

        if (Boolean.TRUE.equals(req.getVisaSponsorshipOnly())) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("visaStatus"), VisaStatus.SPONSORED));
        }

        if (req.getSalaryMin() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("salaryMax"), req.getSalaryMin()));
        }

        if (req.getSalaryMax() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("salaryMin"), req.getSalaryMax()));
        }

        if (hasText(req.getSalaryCurrency())) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("salaryCurrency"), req.getSalaryCurrency()));
        }

        spec = spec.and(byDatePosted(req));

        return spec;
    }

    // ── Specification methods ──

    private static Specification<Job> isActive() {
        return (root, query, cb) -> cb.equal(root.get("status"), JobStatus.ACTIVE);
    }


    private static Specification<Job> keywordSearch(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("company").get("name")), pattern),
                    cb.like(cb.lower(root.get("city")), pattern),
                    cb.like(cb.lower(root.get("country")), pattern)
            );
        };
    }

    private static Specification<Job> byCompanySlug(String slug) {
        return (root, query, cb) -> cb.equal(root.get("company").get("slug"), slug);
    }

    private static Specification<Job> byCompanySlugs(List<String> slugs) {
        return (root, query, cb) -> root.get("company").get("slug").in(slugs);
    }

    private static Specification<Job> byContinent(String continent) {
        return (root, query, cb) ->
                cb.equal(cb.lower(root.get("continent")), continent.toLowerCase());
    }

    private static Specification<Job> byContinents(List<String> continents) {
        return (root, query, cb) -> {
            List<Predicate> predicates = continents.stream()
                    .map(c -> cb.equal(cb.lower(root.get("continent")), c.toLowerCase()))
                    .collect(Collectors.toList());
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    private static Specification<Job> byCountry(String country) {
        return (root, query, cb) ->
                cb.equal(cb.lower(root.get("country")), country.toLowerCase());
    }

    private static Specification<Job> byCountries(List<String> countries) {
        return (root, query, cb) -> {
            List<String> lower = countries.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
            return cb.lower(root.get("country")).in(lower);
        };
    }

    private static Specification<Job> byCity(String city) {
        return (root, query, cb) ->
                cb.equal(cb.lower(root.get("city")), city.toLowerCase());
    }

    private static Specification<Job> byJobTypes(List<String> types) {
        return (root, query, cb) -> {
            List<JobType> values = types.stream()
                    .map(t -> safeEnum(JobType.class, t))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (values.isEmpty()) return cb.conjunction();
            return root.get("jobType").in(values);
        };
    }

    private static Specification<Job> byWorkModes(List<String> modes) {
        return (root, query, cb) -> {
            List<WorkMode> values = modes.stream()
                    .map(m -> safeEnum(WorkMode.class, m))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (values.isEmpty()) return cb.conjunction();
            return root.get("workMode").in(values);
        };
    }

    private static Specification<Job> byExperienceLevels(List<String> levels) {
        return (root, query, cb) -> {
            List<ExperienceLevel> values = levels.stream()
                    .map(l -> safeEnum(ExperienceLevel.class, l))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (values.isEmpty()) return cb.conjunction();
            return root.get("experienceLevel").in(values);
        };
    }

    private static Specification<Job> byIndustries(List<String> industries) {
        return (root, query, cb) -> {
            List<Industry> values = industries.stream()
                    .map(i -> safeEnum(Industry.class, i))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (values.isEmpty()) return cb.conjunction();
            return root.get("industry").in(values);
        };
    }

    private static Specification<Job> byCompanySizeCategories(List<String> categories) {
        return (root, query, cb) -> {
            List<com.jobportal.domain.enums.CompanySizeCategory> values = categories.stream()
                    .map(c -> safeEnum(com.jobportal.domain.enums.CompanySizeCategory.class, c))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (values.isEmpty()) return cb.conjunction();
            if (query != null) query.distinct(true);
            jakarta.persistence.criteria.Join<com.jobportal.domain.entity.Job, com.jobportal.domain.entity.Company> company =
                root.join("company", jakarta.persistence.criteria.JoinType.LEFT);
            return company.get("sizeCategory").in(values);
        };
    }

    private static Specification<Job> byVisaStatuses(List<String> statuses) {
        return (root, query, cb) -> {
            List<VisaStatus> values = statuses.stream()
                    .map(s -> safeEnum(VisaStatus.class, s))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (values.isEmpty()) return cb.conjunction();
            return root.get("visaStatus").in(values);
        };
    }

    private static Specification<Job> byDatePosted(JobSearchRequest req) {
        Instant cutoff = null;

        if (req.getPostedAfter() != null) {
            cutoff = req.getPostedAfter();
        } else if (hasText(req.getDatePosted())) {
            cutoff = switch (req.getDatePosted().toLowerCase()) {
                case "24h", "1d"    -> Instant.now().minusSeconds(86400);
                case "3d"           -> Instant.now().minusSeconds(3 * 86400);
                case "7d", "week"   -> Instant.now().minusSeconds(7 * 86400);
                case "30d", "month" -> Instant.now().minusSeconds(30L * 86400);
                default             -> null;
            };
        }

        if (cutoff == null) return (root, query, cb) -> cb.conjunction();

        final Instant finalCutoff = cutoff;
        final Instant finalBefore = req.getPostedBefore() != null
                ? req.getPostedBefore()
                : Instant.now().plusSeconds(86400);

        return (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("postedAt")),
                cb.between(root.get("postedAt"), finalCutoff, finalBefore)
        );
    }

    // ── Helpers ──

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }

    private static <E extends Enum<E>> E safeEnum(Class<E> clazz, String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(clazz, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

package com.jobportal.domain.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;
import com.jobportal.domain.enums.CompanySizeCategory;
import java.util.List;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "jobs")
@Setting(settingPath = "elasticsearch/job-settings.json")
public class JobDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "english")
    private String title;

    @Field(type = FieldType.Keyword)
    private String companyName;

    @Field(type = FieldType.Keyword)
    private String companySlug;

    @Field(type = FieldType.Text, analyzer = "english")
    private String description;

    @Field(type = FieldType.Keyword)
    private String jobType;

    @Field(type = FieldType.Keyword)
    private String workMode;

    @Field(type = FieldType.Keyword)
    private String experienceLevel;

    @Field(type = FieldType.Keyword)
    private String industry;

    @Field(type = FieldType.Keyword)
    private String country;

    @Field(type = FieldType.Keyword)
    private String city;

    @Field(type = FieldType.Keyword)
    private String continent;

    @Field(type = FieldType.Text)
    private String locationText;

    @Field(type = FieldType.Double)
    private Double salaryMin;

    @Field(type = FieldType.Double)
    private Double salaryMax;

    @Field(type = FieldType.Keyword)
    private String salaryCurrency;

    @Field(type = FieldType.Keyword)
    private String visaStatus;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Keyword)
    private String status;

    private boolean isRemoteFriendly;

    @Field(type = FieldType.Date)
    private Instant postedAt;

    @Field(type = FieldType.Keyword)
    private String applyUrl;

    @Field(type = FieldType.Keyword)
    private String logoUrl;

    @Field(type = FieldType.Keyword)
    private String companySizeCategory;
}

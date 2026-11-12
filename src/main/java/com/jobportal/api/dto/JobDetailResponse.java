package com.jobportal.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JobDetailResponse extends JobResponse {

    private String description;
    private String requirements;
    private String responsibilities;
    private String companyWebsite;
    private String companyDescription;
    private String companySize;
    private String companyHeadquarters;
}

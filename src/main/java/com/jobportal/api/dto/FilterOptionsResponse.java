package com.jobportal.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterOptionsResponse implements Serializable {
    private List<String> continents;
    private List<String> companySizeCategories;
    private List<String> countries;
    private Map<String, List<String>> countriesByContinent;
    private List<CompanyOption> companies;
    private List<String> workModes;
    private List<String> jobTypes;
    private List<String> experienceLevels;
    private List<String> industries;
    private List<String> visaStatuses;
}

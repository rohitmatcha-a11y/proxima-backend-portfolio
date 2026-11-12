package com.jobportal.service.enrichment;

import com.jobportal.domain.enums.VisaStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisaDetectionResult {

    private VisaStatus visaStatus;
    private double confidence;
    private String reason;
    private List<String> keywords;

    public static VisaDetectionResult unknown() {
        return VisaDetectionResult.builder()
                .visaStatus(VisaStatus.UNKNOWN)
                .confidence(0.0)
                .reason("Could not determine visa status")
                .keywords(List.of())
                .build();
    }

    public static VisaDetectionResult notMentioned() {
        return VisaDetectionResult.builder()
                .visaStatus(VisaStatus.NOT_MENTIONED)
                .confidence(1.0)
                .reason("No visa sponsorship information found in job description")
                .keywords(List.of())
                .build();
    }
}

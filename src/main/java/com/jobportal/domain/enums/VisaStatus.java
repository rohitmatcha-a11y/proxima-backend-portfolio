package com.jobportal.domain.enums;

public enum VisaStatus {
    SPONSORED,       // explicitly mentioned in JD
    NOT_SPONSORED,   // explicitly says no sponsorship
    NOT_MENTIONED,   // JD silent on visa
    UNKNOWN          // AI could not determine
}

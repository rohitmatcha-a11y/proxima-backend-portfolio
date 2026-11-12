package com.jobportal.service.company;

import com.jobportal.client.wikipedia.WikipediaClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** Resolves concise company descriptions without requiring per-company copy in code. */
@Service
@RequiredArgsConstructor
public class CompanyDescriptionService {

    private final WikipediaClient wikipediaClient;

    public String resolve(String companyName, String encyclopediaTitle, String suppliedDescription) {
        String supplied = StringUtils.hasText(suppliedDescription) ? suppliedDescription.trim() : null;
        if (!needsImprovement(companyName, supplied)) return supplied;

        String title = StringUtils.hasText(encyclopediaTitle) ? encyclopediaTitle : companyName;
        String description = wikipediaClient.fetchDescription(title);
        if (StringUtils.hasText(description)) return description;

        // Keep a source-provided description when a reference description is unavailable. It is
        // still more useful than a generic placeholder, and can be refreshed later.
        if (StringUtils.hasText(supplied)) return supplied;

        return "Explore open roles and career opportunities at " + companyName + ".";
    }

    /**
     * Career sites often expose a short recruitment slogan as their meta description. Those are
     * useful for a careers page, but not as a company profile. Treat them as incomplete so we can
     * prefer a fuller public company description when one is available.
     */
    public boolean needsImprovement(String companyName, String description) {
        if (!StringUtils.hasText(description)) return true;

        String normalized = description.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String company = companyName == null ? "" : companyName.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(company)
                || normalized.equals(company + " jobs")
                || normalized.equals(company + " careers")
                || normalized.equals("careers")
                || normalized.startsWith("explore open roles and career opportunities at ")) {
            return true;
        }

        if (normalized.length() < 100) return true;

        return normalized.contains("join our team")
                || normalized.contains("join us")
                || normalized.contains("we're hiring")
                || normalized.contains("we are hiring")
                || normalized.contains("work with us")
                || normalized.contains("build your career");
    }
}

package com.jobportal.exception;

public class SearchException extends JobPortalException {

    public SearchException(ErrorCode errorCode, String message) {
        super(errorCode, message, 400);
    }

    public static SearchException invalidSortField(String field) {
        return new SearchException(ErrorCode.INVALID_SORT_FIELD,
                "Invalid sort field: '" + field + "'. Allowed: postedAt, salary, relevance, title");
    }

    public static SearchException invalidPagination(String message) {
        return new SearchException(ErrorCode.INVALID_PAGINATION, message);
    }
    public static SearchException relevanceRequiresKeyword() {
        return new SearchException(ErrorCode.INVALID_SORT_FIELD,
                "Sort by relevance requires a keyword to be provided");
    }
}

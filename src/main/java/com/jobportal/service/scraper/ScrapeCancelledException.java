package com.jobportal.service.scraper;

/** Signals that an administrator stopped the active scraper. */
public class ScrapeCancelledException extends RuntimeException {

    public ScrapeCancelledException() {
        super("Stopped manually by an administrator");
    }
}

package com.jobportal.client.geocoding;

public record GeoLocation(String city, String country, String continent) {
    public static GeoLocation empty() {
        return new GeoLocation(null, null, null);
    }
}

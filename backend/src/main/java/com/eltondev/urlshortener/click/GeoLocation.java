package com.eltondev.urlshortener.click;

public record GeoLocation(String country, String city) {
    public static final GeoLocation UNKNOWN = new GeoLocation(null, null);
}

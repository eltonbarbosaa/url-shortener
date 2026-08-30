package com.eltondev.urlshortener.click;

public record ParsedUserAgent(String deviceType, String browser, String os) {
    public static final ParsedUserAgent UNKNOWN = new ParsedUserAgent("unknown", "unknown", "unknown");
}

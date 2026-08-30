package com.eltondev.urlshortener.link.dto;

public class LinkResponse {
    private final String shortCode;
    private final String shortUrl;
    private final String originalUrl;

    public LinkResponse(String shortCode, String shortUrl, String originalUrl) {
        this.shortCode = shortCode;
        this.shortUrl = shortUrl;
        this.originalUrl = originalUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }
}

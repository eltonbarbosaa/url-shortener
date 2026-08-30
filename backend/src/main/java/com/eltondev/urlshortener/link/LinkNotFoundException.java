package com.eltondev.urlshortener.link;

public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException(String shortCode) {
        super("No active link found for code: " + shortCode);
    }
}

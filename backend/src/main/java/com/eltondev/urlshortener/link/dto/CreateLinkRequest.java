package com.eltondev.urlshortener.link.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateLinkRequest {

    @NotBlank(message = "originalUrl is required")
    @Pattern(regexp = "^https?://.+", message = "originalUrl must start with http:// or https://")
    private String originalUrl;

    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,32}$", message = "customAlias must be 3-32 chars of letters, digits, - or _")
    private String customAlias;

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getCustomAlias() {
        return customAlias;
    }

    public void setCustomAlias(String customAlias) {
        this.customAlias = customAlias;
    }
}

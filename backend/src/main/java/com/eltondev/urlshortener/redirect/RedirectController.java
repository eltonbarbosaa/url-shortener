package com.eltondev.urlshortener.redirect;

import com.eltondev.urlshortener.click.ClickTrackingService;
import com.eltondev.urlshortener.link.LinkCacheService;
import com.eltondev.urlshortener.link.LinkNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class RedirectController {

    private final LinkCacheService linkCacheService;
    private final ClickTrackingService clickTrackingService;

    public RedirectController(LinkCacheService linkCacheService, ClickTrackingService clickTrackingService) {
        this.linkCacheService = linkCacheService;
        this.clickTrackingService = clickTrackingService;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        String originalUrl = linkCacheService.resolve(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));

        clickTrackingService.recordClickAsync(shortCode, request);

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(originalUrl))
            .build();
    }
}

package com.eltondev.urlshortener.redirect;

import com.eltondev.urlshortener.link.LinkCacheService;
import com.eltondev.urlshortener.link.LinkNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class RedirectController {

    private final LinkCacheService linkCacheService;

    public RedirectController(LinkCacheService linkCacheService) {
        this.linkCacheService = linkCacheService;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = linkCacheService.resolve(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(originalUrl))
            .build();
    }
}

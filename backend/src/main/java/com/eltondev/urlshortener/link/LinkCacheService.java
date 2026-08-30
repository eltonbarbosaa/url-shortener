package com.eltondev.urlshortener.link;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LinkCacheService {

    private static final String CACHE_PREFIX = "short:";

    private final StringRedisTemplate redisTemplate;
    private final LinkRepository linkRepository;

    public LinkCacheService(StringRedisTemplate redisTemplate, LinkRepository linkRepository) {
        this.redisTemplate = redisTemplate;
        this.linkRepository = linkRepository;
    }

    public Optional<String> resolve(String shortCode) {
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + shortCode);
        if (cached != null) {
            return Optional.of(cached);
        }
        return linkRepository.findByShortCodeAndActiveTrue(shortCode)
            .map(link -> {
                put(link.getShortCode(), link.getOriginalUrl());
                return link.getOriginalUrl();
            });
    }

    public void put(String shortCode, String originalUrl) {
        redisTemplate.opsForValue().set(CACHE_PREFIX + shortCode, originalUrl);
    }
}

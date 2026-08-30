package com.eltondev.urlshortener.link;

import com.eltondev.urlshortener.link.dto.CreateLinkRequest;
import com.eltondev.urlshortener.link.dto.LinkResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LinkService {

    private final LinkRepository linkRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final LinkCacheService linkCacheService;
    private final String baseUrl;

    public LinkService(LinkRepository linkRepository,
                        ShortCodeGenerator shortCodeGenerator,
                        LinkCacheService linkCacheService,
                        @Value("${app.base-url}") String baseUrl) {
        this.linkRepository = linkRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.linkCacheService = linkCacheService;
        this.baseUrl = baseUrl;
    }

    public LinkResponse createLink(CreateLinkRequest request) {
        String shortCode;
        boolean isCustomAlias = request.getCustomAlias() != null && !request.getCustomAlias().isBlank();

        if (isCustomAlias) {
            shortCode = request.getCustomAlias();
            if (linkRepository.existsByShortCode(shortCode)) {
                throw new AliasAlreadyExistsException(shortCode);
            }
        } else {
            shortCode = generateUniqueCode();
        }

        Link link = new Link(shortCode, request.getOriginalUrl(), isCustomAlias);
        linkRepository.save(link);
        linkCacheService.put(shortCode, request.getOriginalUrl());

        return new LinkResponse(shortCode, baseUrl + "/" + shortCode, request.getOriginalUrl());
    }

    private String generateUniqueCode() {
        String candidate;
        int attempts = 0;
        do {
            candidate = shortCodeGenerator.generate();
            attempts++;
        } while (linkRepository.existsByShortCode(candidate) && attempts < 5);
        return candidate;
    }
}

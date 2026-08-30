package com.eltondev.urlshortener.link;

import com.eltondev.urlshortener.link.dto.CreateLinkRequest;
import com.eltondev.urlshortener.link.dto.LinkResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock
    private LinkRepository linkRepository;
    @Mock
    private ShortCodeGenerator shortCodeGenerator;
    @Mock
    private LinkCacheService linkCacheService;

    @Test
    void createsLinkWithGeneratedCodeWhenNoAliasGiven() {
        LinkService service = new LinkService(linkRepository, shortCodeGenerator, linkCacheService, "http://localhost:8080");
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com/some/long/path");

        when(shortCodeGenerator.generate()).thenReturn("abc1234");
        when(linkRepository.existsByShortCode("abc1234")).thenReturn(false);
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LinkResponse response = service.createLink(request);

        assertEquals("abc1234", response.getShortCode());
        assertEquals("http://localhost:8080/abc1234", response.getShortUrl());
        verify(linkCacheService).put("abc1234", "https://example.com/some/long/path");
    }

    @Test
    void usesCustomAliasWhenProvidedAndAvailable() {
        LinkService service = new LinkService(linkRepository, shortCodeGenerator, linkCacheService, "http://localhost:8080");
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com");
        request.setCustomAlias("promo2026");

        when(linkRepository.existsByShortCode("promo2026")).thenReturn(false);
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LinkResponse response = service.createLink(request);

        assertEquals("promo2026", response.getShortCode());
        verifyNoInteractions(shortCodeGenerator);
    }

    @Test
    void rejectsCustomAliasAlreadyInUse() {
        LinkService service = new LinkService(linkRepository, shortCodeGenerator, linkCacheService, "http://localhost:8080");
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com");
        request.setCustomAlias("taken");

        when(linkRepository.existsByShortCode("taken")).thenReturn(true);

        assertThrows(AliasAlreadyExistsException.class, () -> service.createLink(request));
        verify(linkRepository, never()).save(any());
    }
}

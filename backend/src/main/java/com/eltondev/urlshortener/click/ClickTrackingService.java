package com.eltondev.urlshortener.click;

import com.eltondev.urlshortener.link.Link;
import com.eltondev.urlshortener.link.LinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ClickTrackingService {

    private static final Logger log = LoggerFactory.getLogger(ClickTrackingService.class);

    private final LinkRepository linkRepository;
    private final ClickEventRepository clickEventRepository;
    private final GeoIpService geoIpService;
    private final UserAgentParsingService userAgentParsingService;

    public ClickTrackingService(LinkRepository linkRepository,
                                 ClickEventRepository clickEventRepository,
                                 GeoIpService geoIpService,
                                 UserAgentParsingService userAgentParsingService) {
        this.linkRepository = linkRepository;
        this.clickEventRepository = clickEventRepository;
        this.geoIpService = geoIpService;
        this.userAgentParsingService = userAgentParsingService;
    }

    @Async
    public void recordClickAsync(String shortCode, String clientIp, String userAgentHeader, String referer) {
        linkRepository.findByShortCodeAndActiveTrue(shortCode).ifPresentOrElse(
            link -> persistClick(link, clientIp, userAgentHeader, referer),
            () -> log.warn("Skipped click recording: no active link for code {}", shortCode)
        );
    }

    private void persistClick(Link link, String clientIp, String userAgentHeader, String referer) {
        var geo = geoIpService.lookup(clientIp);
        var ua = userAgentParsingService.parse(userAgentHeader);

        ClickEvent event = new ClickEvent(
            link,
            hashIp(clientIp),
            geo.country(),
            geo.city(),
            ua.deviceType(),
            ua.browser(),
            ua.os(),
            referer
        );
        clickEventRepository.save(event);
    }

    private String hashIp(String ip) {
        if (ip == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

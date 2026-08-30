package com.eltondev.urlshortener;

import com.eltondev.urlshortener.link.dto.CreateLinkRequest;
import com.eltondev.urlshortener.link.dto.LinkResponse;
import com.eltondev.urlshortener.stats.dto.StatsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LinkFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createRedirectAndSeeStats() {
        CreateLinkRequest createRequest = new CreateLinkRequest();
        createRequest.setOriginalUrl("https://example.com/integration-test");

        ResponseEntity<LinkResponse> createResponse =
            restTemplate.postForEntity("/api/links", createRequest, LinkResponse.class);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        String shortCode = createResponse.getBody().getShortCode();

        ResponseEntity<Void> redirectResponse =
            restTemplate.getForEntity("/" + shortCode, Void.class);
        assertEquals(HttpStatus.FOUND, redirectResponse.getStatusCode());
        assertEquals("https://example.com/integration-test",
            redirectResponse.getHeaders().getLocation().toString());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            ResponseEntity<StatsResponse> statsResponse =
                restTemplate.getForEntity("/api/links/" + shortCode + "/stats", StatsResponse.class);
            assertEquals(HttpStatus.OK, statsResponse.getStatusCode());
            assertEquals(1L, statsResponse.getBody().totalClicks());
        });
    }
}

package com.eltondev.urlshortener.stats;

import com.eltondev.urlshortener.stats.dto.StatsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/links")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/{shortCode}/stats")
    public StatsResponse stats(@PathVariable String shortCode) {
        return statsService.getStats(shortCode);
    }
}

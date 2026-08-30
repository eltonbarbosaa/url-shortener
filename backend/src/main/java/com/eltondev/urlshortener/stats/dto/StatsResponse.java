package com.eltondev.urlshortener.stats.dto;

import java.util.List;
import java.util.Map;

public record StatsResponse(
    String shortCode,
    long totalClicks,
    List<DailyClickCount> dailySeries,
    Map<String, Long> byCountry,
    Map<String, Long> byDevice
) {
}

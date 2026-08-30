package com.eltondev.urlshortener.stats.dto;

import java.time.LocalDate;

public record DailyClickCount(LocalDate date, long count) {
}

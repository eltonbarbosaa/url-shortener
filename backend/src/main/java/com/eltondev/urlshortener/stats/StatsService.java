package com.eltondev.urlshortener.stats;

import com.eltondev.urlshortener.click.ClickEventRepository;
import com.eltondev.urlshortener.link.LinkNotFoundException;
import com.eltondev.urlshortener.link.LinkRepository;
import com.eltondev.urlshortener.stats.dto.DailyClickCount;
import com.eltondev.urlshortener.stats.dto.StatsResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final LinkRepository linkRepository;
    private final ClickEventRepository clickEventRepository;

    public StatsService(LinkRepository linkRepository, ClickEventRepository clickEventRepository) {
        this.linkRepository = linkRepository;
        this.clickEventRepository = clickEventRepository;
    }

    public StatsResponse getStats(String shortCode) {
        linkRepository.findByShortCodeAndActiveTrue(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));

        long total = clickEventRepository.countByLinkShortCode(shortCode);

        List<DailyClickCount> dailySeries = clickEventRepository.dailyCounts(shortCode).stream()
            .map(row -> new DailyClickCount(toLocalDate(row[0]), (Long) row[1]))
            .collect(Collectors.toList());

        Map<String, Long> byCountry = toMap(clickEventRepository.countryBreakdown(shortCode));
        Map<String, Long> byDevice = toMap(clickEventRepository.deviceBreakdown(shortCode));

        return new StatsResponse(shortCode, total, dailySeries, byCountry, byDevice);
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        throw new IllegalStateException("Unexpected date type: " + value.getClass());
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }
}

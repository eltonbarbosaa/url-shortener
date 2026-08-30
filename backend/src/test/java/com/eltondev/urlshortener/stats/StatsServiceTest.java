package com.eltondev.urlshortener.stats;

import com.eltondev.urlshortener.click.ClickEventRepository;
import com.eltondev.urlshortener.link.Link;
import com.eltondev.urlshortener.link.LinkRepository;
import com.eltondev.urlshortener.link.LinkNotFoundException;
import com.eltondev.urlshortener.stats.dto.StatsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private LinkRepository linkRepository;
    @Mock
    private ClickEventRepository clickEventRepository;

    @Test
    void aggregatesStatsForExistingLink() {
        StatsService service = new StatsService(linkRepository, clickEventRepository);
        Link link = new Link("abc1234", "https://example.com", false);
        when(linkRepository.findByShortCodeAndActiveTrue("abc1234")).thenReturn(Optional.of(link));
        when(clickEventRepository.countByLinkShortCode("abc1234")).thenReturn(3L);

        List<Object[]> dailyRows = List.<Object[]>of(new Object[]{LocalDate.of(2026, 8, 30), 3L});
        List<Object[]> countryRows = List.<Object[]>of(new Object[]{"Brazil", 3L});
        List<Object[]> deviceRows = List.<Object[]>of(new Object[]{"Desktop", 3L});

        when(clickEventRepository.dailyCounts("abc1234")).thenReturn(dailyRows);
        when(clickEventRepository.countryBreakdown("abc1234")).thenReturn(countryRows);
        when(clickEventRepository.deviceBreakdown("abc1234")).thenReturn(deviceRows);

        StatsResponse stats = service.getStats("abc1234");

        assertEquals(3L, stats.totalClicks());
        assertEquals(1, stats.dailySeries().size());
        assertEquals(3L, stats.byCountry().get("Brazil"));
        assertEquals(3L, stats.byDevice().get("Desktop"));
    }

    @Test
    void throwsWhenLinkDoesNotExist() {
        StatsService service = new StatsService(linkRepository, clickEventRepository);
        when(linkRepository.findByShortCodeAndActiveTrue("missing")).thenReturn(Optional.empty());

        assertThrows(LinkNotFoundException.class, () -> service.getStats("missing"));
    }
}

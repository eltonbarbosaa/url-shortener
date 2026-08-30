package com.eltondev.urlshortener.click;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByLinkShortCode(String shortCode);

    @Query("""
        SELECT CAST(c.clickedAt AS date) AS day, COUNT(c)
        FROM ClickEvent c
        WHERE c.link.shortCode = :shortCode
        GROUP BY CAST(c.clickedAt AS date)
        ORDER BY day
        """)
    List<Object[]> dailyCounts(@Param("shortCode") String shortCode);

    @Query("""
        SELECT COALESCE(c.country, 'unknown'), COUNT(c)
        FROM ClickEvent c
        WHERE c.link.shortCode = :shortCode
        GROUP BY c.country
        """)
    List<Object[]> countryBreakdown(@Param("shortCode") String shortCode);

    @Query("""
        SELECT COALESCE(c.deviceType, 'unknown'), COUNT(c)
        FROM ClickEvent c
        WHERE c.link.shortCode = :shortCode
        GROUP BY c.deviceType
        """)
    List<Object[]> deviceBreakdown(@Param("shortCode") String shortCode);
}

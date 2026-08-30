package com.eltondev.urlshortener.click;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {
    long countByLinkShortCode(String shortCode);
}

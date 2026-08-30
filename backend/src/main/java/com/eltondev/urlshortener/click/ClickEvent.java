package com.eltondev.urlshortener.click;

import com.eltondev.urlshortener.link.Link;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "click_events")
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "link_id")
    private Link link;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt = Instant.now();

    @Column(name = "ip_hash")
    private String ipHash;

    private String country;
    private String city;

    @Column(name = "device_type")
    private String deviceType;

    private String browser;
    private String os;
    private String referrer;

    protected ClickEvent() {
    }

    public ClickEvent(Link link, String ipHash, String country, String city,
                       String deviceType, String browser, String os, String referrer) {
        this.link = link;
        this.ipHash = ipHash;
        this.country = country;
        this.city = city;
        this.deviceType = deviceType;
        this.browser = browser;
        this.os = os;
        this.referrer = referrer;
    }

    public Link getLink() {
        return link;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public String getCountry() {
        return country;
    }

    public String getDeviceType() {
        return deviceType;
    }
}

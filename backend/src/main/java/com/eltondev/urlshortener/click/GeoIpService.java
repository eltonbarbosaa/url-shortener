package com.eltondev.urlshortener.click;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;

@Service
public class GeoIpService {

    private static final Logger log = LoggerFactory.getLogger(GeoIpService.class);

    private final ResourceLoader resourceLoader;
    private final String databasePath;
    private DatabaseReader reader;

    public GeoIpService(ResourceLoader resourceLoader,
                         @Value("${app.geoip.database-path}") String databasePath) {
        this.resourceLoader = resourceLoader;
        this.databasePath = databasePath;
    }

    @PostConstruct
    void init() {
        try {
            Resource resource = resourceLoader.getResource(databasePath);
            this.reader = new DatabaseReader.Builder(resource.getInputStream()).build();
        } catch (IOException e) {
            log.warn("GeoLite2 database not found at {}, geo lookups will return UNKNOWN", databasePath);
            this.reader = null;
        }
    }

    public GeoLocation lookup(String ip) {
        if (reader == null || ip == null || ip.isBlank()) {
            return GeoLocation.UNKNOWN;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            CityResponse response = reader.city(address);
            String country = response.getCountry() != null ? response.getCountry().getName() : null;
            String city = response.getCity() != null ? response.getCity().getName() : null;
            return new GeoLocation(country, city);
        } catch (IOException | GeoIp2Exception e) {
            return GeoLocation.UNKNOWN;
        }
    }
}

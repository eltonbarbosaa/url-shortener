# URL Shortener Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Bitly-style URL shortener: create short links (with optional custom alias), redirect through them, record every click, and expose aggregated click statistics — backend API in Spring Boot, frontend in React, running fully via Docker Compose.

**Architecture:** Spring Boot REST API backed by PostgreSQL (source of truth for links and click events) and Redis (cache-aside for the redirect hot path). Click recording is asynchronous so it never slows down the redirect response. Statistics are computed with aggregate SQL queries over `click_events` — no separate counter to keep in sync. React (Vite) frontend calls the API directly.

**Tech Stack:** Java 21, Spring Boot 3.3, Maven, PostgreSQL 16, Redis 7, Flyway, Testcontainers, MaxMind GeoLite2 (`com.maxmind.geoip2`), `ua-parser` (`uap-java`), React 18 + Vite, `recharts`, Docker Compose, Nginx (serves the frontend build).

**Spec:** `docs/superpowers/specs/2026-08-30-url-shortener-design.md`

## Global Constraints

- No user accounts / authentication (spec: "Fora do escopo (v1)")
- No message queue (Kafka/RabbitMQ) — async click recording uses Spring `@Async` only
- Click counts and stats are computed via `COUNT(*)`/aggregate queries on `click_events`, not a Redis counter
- IPs are never stored in clear text — only a SHA-256 hash (`ip_hash`)
- Redis is used only as a cache-aside for `short_code → original_url`, populated on link creation and on cache miss
- No AWS resources are provisioned in this plan — local Docker Compose only
- Integration tests use Testcontainers with real Postgres and Redis, not mocks

---

## File Structure

```
url-shortener/
  backend/
    pom.xml
    Dockerfile
    src/main/java/com/eltondev/urlshortener/
      UrlShortenerApplication.java
      config/AsyncConfig.java
      link/
        Link.java
        LinkRepository.java
        LinkService.java
        LinkController.java
        LinkCacheService.java
        ShortCodeGenerator.java
        LinkNotFoundException.java
        AliasAlreadyExistsException.java
        ApiExceptionHandler.java
        dto/CreateLinkRequest.java
        dto/LinkResponse.java
        dto/LinkDetailsResponse.java
      click/
        ClickEvent.java
        ClickEventRepository.java
        ClickTrackingService.java
        GeoIpService.java
        GeoLocation.java
        UserAgentParsingService.java
        ParsedUserAgent.java
      redirect/
        RedirectController.java
      stats/
        StatsService.java
        StatsController.java
        dto/StatsResponse.java
        dto/DailyClickCount.java
    src/main/resources/
      application.yml
      db/migration/V1__create_links_table.sql
      db/migration/V2__create_click_events_table.sql
      geoip/GeoLite2-City.mmdb
    src/test/java/com/eltondev/urlshortener/
      link/ShortCodeGeneratorTest.java
      link/LinkServiceTest.java
      stats/StatsServiceTest.java
      IntegrationTestBase.java
      LinkFlowIntegrationTest.java
  frontend/
    package.json
    vite.config.js
    Dockerfile
    nginx.conf
    src/
      main.jsx
      App.jsx
      api/client.js
      pages/CreateLinkPage.jsx
      pages/StatsPage.jsx
  docker-compose.yml
  .gitignore
  README.md
```

---

### Task 1: Backend project scaffolding

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/eltondev/urlshortener/UrlShortenerApplication.java`
- Create: `docker-compose.yml` (postgres + redis services only, for now)
- Create: `.gitignore`
- Test: `backend/src/test/java/com/eltondev/urlshortener/UrlShortenerApplicationTests.java`

**Interfaces:**
- Produces: a bootable Spring Boot app on port 8080, with `spring.profiles.active=default` reading Postgres/Redis connection info from environment variables `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT` (defaults for local `mvn spring-boot:run`: `localhost`/`5432`/`urlshortener`/`urlshortener`/`urlshortener`/`localhost`/`6379`).

- [ ] **Step 1: Create `backend/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
    <relativePath/>
  </parent>

  <groupId>com.eltondev</groupId>
  <artifactId>url-shortener</artifactId>
  <version>0.1.0</version>
  <name>url-shortener</name>

  <properties>
    <java.version>21</java.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>com.maxmind.geoip2</groupId>
      <artifactId>geoip2</artifactId>
      <version>4.2.1</version>
    </dependency>
    <dependency>
      <groupId>com.github.ua-parser</groupId>
      <artifactId>uap-java</artifactId>
      <version>1.6.1</version>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-bom</artifactId>
        <version>1.20.1</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Create `backend/src/main/resources/application.yml`**

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:urlshortener}
    username: ${DB_USER:urlshortener}
    password: ${DB_PASSWORD:urlshortener}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  flyway:
    locations: classpath:db/migration

app:
  base-url: ${APP_BASE_URL:http://localhost:8080}
  geoip:
    database-path: classpath:geoip/GeoLite2-City.mmdb
```

- [ ] **Step 3: Create `backend/src/main/java/com/eltondev/urlshortener/UrlShortenerApplication.java`**

```java
package com.eltondev.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class UrlShortenerApplication {
    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
```

- [ ] **Step 4: Create `docker-compose.yml` (postgres + redis only for now)**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: urlshortener
      POSTGRES_USER: urlshortener
      POSTGRES_PASSWORD: urlshortener
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  postgres_data:
```

- [ ] **Step 5: Create `.gitignore`**

```
target/
node_modules/
dist/
.env
*.log
.idea/
.vscode/
```

- [ ] **Step 6: Write the smoke test**

```java
package com.eltondev.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:smoke;MODE=PostgreSQL",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=false"
})
class UrlShortenerApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

Note: this smoke test only proves the Spring context wires up; it does not
load Flyway migrations. Task 11's integration test is what exercises the
real schema against Testcontainers Postgres.

- [ ] **Step 7: Start Postgres/Redis and verify the app boots**

Run: `docker compose up -d postgres redis`
Run: `cd backend && mvn spring-boot:run`
Expected: log line `Started UrlShortenerApplication` with no errors (stop with Ctrl+C once confirmed).

- [ ] **Step 8: Commit**

```bash
git add backend/pom.xml backend/src docker-compose.yml .gitignore
git commit -m "chore: scaffold Spring Boot backend and local Postgres/Redis"
```

---

### Task 2: Database schema (Flyway migrations)

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__create_links_table.sql`
- Create: `backend/src/main/resources/db/migration/V2__create_click_events_table.sql`

**Interfaces:**
- Produces: tables `links` (`id`, `short_code`, `original_url`, `is_custom_alias`, `created_at`, `active`) and `click_events` (`id`, `link_id`, `clicked_at`, `ip_hash`, `country`, `city`, `device_type`, `browser`, `os`, `referrer`), consumed by the JPA entities in Tasks 4 and 9.

- [ ] **Step 1: Create `V1__create_links_table.sql`**

```sql
CREATE TABLE links (
    id              BIGSERIAL PRIMARY KEY,
    short_code      VARCHAR(32) NOT NULL UNIQUE,
    original_url    TEXT NOT NULL,
    is_custom_alias BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    active          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_links_short_code ON links (short_code);
```

- [ ] **Step 2: Create `V2__create_click_events_table.sql`**

```sql
CREATE TABLE click_events (
    id          BIGSERIAL PRIMARY KEY,
    link_id     BIGINT NOT NULL REFERENCES links (id),
    clicked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_hash     VARCHAR(64),
    country     VARCHAR(100),
    city        VARCHAR(100),
    device_type VARCHAR(50),
    browser     VARCHAR(100),
    os          VARCHAR(100),
    referrer    TEXT
);

CREATE INDEX idx_click_events_link_id ON click_events (link_id);
CREATE INDEX idx_click_events_clicked_at ON click_events (clicked_at);
```

- [ ] **Step 3: Verify migrations run cleanly**

Run: `docker compose up -d postgres` then `cd backend && mvn spring-boot:run` (with `spring.flyway.enabled` left at its default `true` for the main app, i.e. run it without the H2 test properties).
Expected: log lines `Migrating schema "public" to version "1"` and `"2"`, app starts successfully. Stop with Ctrl+C.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration
git commit -m "feat: add links and click_events schema migrations"
```

---

### Task 3: Short code generator

**Files:**
- Create: `backend/src/main/java/com/eltondev/urlshortener/link/ShortCodeGenerator.java`
- Test: `backend/src/test/java/com/eltondev/urlshortener/link/ShortCodeGeneratorTest.java`

**Interfaces:**
- Produces: `ShortCodeGenerator.generate(): String` — returns a 7-character alphanumeric code. Consumed by `LinkService` in Task 4.

- [ ] **Step 1: Write the failing test**

```java
package com.eltondev.urlshortener.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortCodeGeneratorTest {

    @Test
    void generatesSevenCharacterAlphanumericCode() {
        ShortCodeGenerator generator = new ShortCodeGenerator();

        String code = generator.generate();

        assertEquals(7, code.length());
        assertTrue(code.matches("[a-zA-Z0-9]+"), "code should be alphanumeric, was: " + code);
    }

    @Test
    void generatesDifferentCodesAcrossCalls() {
        ShortCodeGenerator generator = new ShortCodeGenerator();

        String first = generator.generate();
        String second = generator.generate();

        assertTrue(!first.equals(second), "two consecutive codes should not collide in practice");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=ShortCodeGeneratorTest test`
Expected: FAIL (compile error) — `ShortCodeGenerator` does not exist yet.

- [ ] **Step 3: Write the implementation**

```java
package com.eltondev.urlshortener.link;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ShortCodeGenerator {

    private static final String ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int LENGTH = 7;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -Dtest=ShortCodeGeneratorTest test`
Expected: PASS (2 tests green).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eltondev/urlshortener/link/ShortCodeGenerator.java backend/src/test/java/com/eltondev/urlshortener/link/ShortCodeGeneratorTest.java
git commit -m "feat: add short code generator"
```

---

### Task 4: Link creation (entity, repository, service, controller)

**Files:**
- Create: `backend/src/main/java/com/eltondev/urlshortener/link/Link.java`
- Create: `backend/src/main/java/com/eltondev/urlshortener/link/LinkRepository.java`
- Create: `backend/src/main/java/com/eltondev/urlshortener/link/LinkService.java`
- Create: `backend/src/main/java/com/eltondev/urlshortener/link/LinkController.java`
- Create: `backend/src/main/java/com/eltondev/urlshortener/link/AliasAlreadyExistsException.java`
- Create: `backend/src/main/java/com/eltondev/urlshortener/link/ApiExceptionHandler.java`
- Create: `backend/src/main/java/com/eltondev/urlshortener/link/dto/CreateLinkRequest.java`
- Create: `backend/src/main/java/com/eltondev/urlshortener/link/dto/LinkResponse.java`
- Test: `backend/src/test/java/com/eltondev/urlshortener/link/LinkServiceTest.java`

**Interfaces:**
- Consumes: `ShortCodeGenerator.generate(): String` (Task 3).
- Produces: `LinkService.createLink(CreateLinkRequest): LinkResponse`, `LinkRepository.findByShortCode(String): Optional<Link>`, `LinkRepository.existsByShortCode(String): boolean` — consumed by Task 5 (cache), Task 6 (redirect), Task 9 (click tracking needs `Link` by short code).

- [ ] **Step 1: Create the `Link` entity**

```java
package com.eltondev.urlshortener.link;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "links")
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true)
    private String shortCode;

    @Column(name = "original_url", nullable = false)
    private String originalUrl;

    @Column(name = "is_custom_alias", nullable = false)
    private boolean customAlias;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Link() {
    }

    public Link(String shortCode, String originalUrl, boolean customAlias) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.customAlias = customAlias;
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public boolean isCustomAlias() {
        return customAlias;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return active;
    }
}
```

- [ ] **Step 2: Create `LinkRepository`**

```java
package com.eltondev.urlshortener.link;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {
    Optional<Link> findByShortCodeAndActiveTrue(String shortCode);

    boolean existsByShortCode(String shortCode);
}
```

- [ ] **Step 3: Create the request/response DTOs**

```java
package com.eltondev.urlshortener.link.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateLinkRequest {

    @NotBlank(message = "originalUrl is required")
    @Pattern(regexp = "^https?://.+", message = "originalUrl must start with http:// or https://")
    private String originalUrl;

    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,32}$", message = "customAlias must be 3-32 chars of letters, digits, - or _")
    private String customAlias;

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getCustomAlias() {
        return customAlias;
    }

    public void setCustomAlias(String customAlias) {
        this.customAlias = customAlias;
    }
}
```

```java
package com.eltondev.urlshortener.link.dto;

public class LinkResponse {
    private final String shortCode;
    private final String shortUrl;
    private final String originalUrl;

    public LinkResponse(String shortCode, String shortUrl, String originalUrl) {
        this.shortCode = shortCode;
        this.shortUrl = shortUrl;
        this.originalUrl = originalUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }
}
```

- [ ] **Step 4: Create `AliasAlreadyExistsException`**

```java
package com.eltondev.urlshortener.link;

public class AliasAlreadyExistsException extends RuntimeException {
    public AliasAlreadyExistsException(String alias) {
        super("Alias already in use: " + alias);
    }
}
```

- [ ] **Step 5: Write the failing test for `LinkService`**

```java
package com.eltondev.urlshortener.link;

import com.eltondev.urlshortener.link.dto.CreateLinkRequest;
import com.eltondev.urlshortener.link.dto.LinkResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock
    private LinkRepository linkRepository;
    @Mock
    private ShortCodeGenerator shortCodeGenerator;
    @Mock
    private LinkCacheService linkCacheService;

    @Test
    void createsLinkWithGeneratedCodeWhenNoAliasGiven() {
        LinkService service = new LinkService(linkRepository, shortCodeGenerator, linkCacheService, "http://localhost:8080");
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com/some/long/path");

        when(shortCodeGenerator.generate()).thenReturn("abc1234");
        when(linkRepository.existsByShortCode("abc1234")).thenReturn(false);
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LinkResponse response = service.createLink(request);

        assertEquals("abc1234", response.getShortCode());
        assertEquals("http://localhost:8080/abc1234", response.getShortUrl());
        verify(linkCacheService).put("abc1234", "https://example.com/some/long/path");
    }

    @Test
    void usesCustomAliasWhenProvidedAndAvailable() {
        LinkService service = new LinkService(linkRepository, shortCodeGenerator, linkCacheService, "http://localhost:8080");
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com");
        request.setCustomAlias("promo2026");

        when(linkRepository.existsByShortCode("promo2026")).thenReturn(false);
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LinkResponse response = service.createLink(request);

        assertEquals("promo2026", response.getShortCode());
        verifyNoInteractions(shortCodeGenerator);
    }

    @Test
    void rejectsCustomAliasAlreadyInUse() {
        LinkService service = new LinkService(linkRepository, shortCodeGenerator, linkCacheService, "http://localhost:8080");
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com");
        request.setCustomAlias("taken");

        when(linkRepository.existsByShortCode("taken")).thenReturn(true);

        assertThrows(AliasAlreadyExistsException.class, () -> service.createLink(request));
        verify(linkRepository, never()).save(any());
    }
}
```

Add the Mockito JUnit5 dependency needed by this test to `backend/pom.xml`
(inside `<dependencies>`, alongside `spring-boot-starter-test` which already
pulls in `mockito-core` — this adds the JUnit 5 extension):

```xml
<dependency>
  <groupId>org.mockito</groupId>
  <artifactId>mockito-junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=LinkServiceTest test`
Expected: FAIL (compile error) — `LinkService` and `LinkCacheService` do not exist yet.

- [ ] **Step 7: Create a placeholder-free `LinkCacheService` stub needed to compile**

This class is fully implemented in Task 5; here it only needs the shape
`LinkServiceTest` depends on.

```java
package com.eltondev.urlshortener.link;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LinkCacheService {

    private static final String CACHE_PREFIX = "short:";

    private final StringRedisTemplate redisTemplate;
    private final LinkRepository linkRepository;

    public LinkCacheService(StringRedisTemplate redisTemplate, LinkRepository linkRepository) {
        this.redisTemplate = redisTemplate;
        this.linkRepository = linkRepository;
    }

    public Optional<String> resolve(String shortCode) {
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + shortCode);
        if (cached != null) {
            return Optional.of(cached);
        }
        return linkRepository.findByShortCodeAndActiveTrue(shortCode)
            .map(link -> {
                put(link.getShortCode(), link.getOriginalUrl());
                return link.getOriginalUrl();
            });
    }

    public void put(String shortCode, String originalUrl) {
        redisTemplate.opsForValue().set(CACHE_PREFIX + shortCode, originalUrl);
    }
}
```

- [ ] **Step 8: Create `LinkService`**

```java
package com.eltondev.urlshortener.link;

import com.eltondev.urlshortener.link.dto.CreateLinkRequest;
import com.eltondev.urlshortener.link.dto.LinkResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LinkService {

    private final LinkRepository linkRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final LinkCacheService linkCacheService;
    private final String baseUrl;

    public LinkService(LinkRepository linkRepository,
                        ShortCodeGenerator shortCodeGenerator,
                        LinkCacheService linkCacheService,
                        @Value("${app.base-url}") String baseUrl) {
        this.linkRepository = linkRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.linkCacheService = linkCacheService;
        this.baseUrl = baseUrl;
    }

    public LinkResponse createLink(CreateLinkRequest request) {
        String shortCode;
        boolean isCustomAlias = request.getCustomAlias() != null && !request.getCustomAlias().isBlank();

        if (isCustomAlias) {
            shortCode = request.getCustomAlias();
            if (linkRepository.existsByShortCode(shortCode)) {
                throw new AliasAlreadyExistsException(shortCode);
            }
        } else {
            shortCode = generateUniqueCode();
        }

        Link link = new Link(shortCode, request.getOriginalUrl(), isCustomAlias);
        linkRepository.save(link);
        linkCacheService.put(shortCode, request.getOriginalUrl());

        return new LinkResponse(shortCode, baseUrl + "/" + shortCode, request.getOriginalUrl());
    }

    private String generateUniqueCode() {
        String candidate;
        int attempts = 0;
        do {
            candidate = shortCodeGenerator.generate();
            attempts++;
        } while (linkRepository.existsByShortCode(candidate) && attempts < 5);
        return candidate;
    }
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: `cd backend && mvn -Dtest=LinkServiceTest test`
Expected: PASS (3 tests green).

- [ ] **Step 10: Create `ApiExceptionHandler`**

```java
package com.eltondev.urlshortener.link;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AliasAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleAliasConflict(AliasAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(LinkNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleLinkNotFound(LinkNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
            ? "Invalid request"
            : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }
}
```

- [ ] **Step 11: Create `LinkNotFoundException` (used above and by Task 6)**

```java
package com.eltondev.urlshortener.link;

public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException(String shortCode) {
        super("No active link found for code: " + shortCode);
    }
}
```

- [ ] **Step 12: Create `LinkController`**

```java
package com.eltondev.urlshortener.link;

import com.eltondev.urlshortener.link.dto.CreateLinkRequest;
import com.eltondev.urlshortener.link.dto.LinkResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        LinkResponse response = linkService.createLink(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

- [ ] **Step 13: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/eltondev/urlshortener/link backend/src/test/java/com/eltondev/urlshortener/link/LinkServiceTest.java
git commit -m "feat: add link creation with custom alias support"
```

---

### Task 5: Redirect endpoint (Redis cache-aside)

**Files:**
- Create: `backend/src/main/java/com/eltondev/urlshortener/redirect/RedirectController.java`

**Interfaces:**
- Consumes: `LinkCacheService.resolve(String): Optional<String>` (Task 4), `LinkNotFoundException` (Task 4).
- Produces: `GET /{shortCode}` — consumed by Task 9's click-tracking wiring (this controller calls `ClickTrackingService.recordClickAsync`, added there).

- [ ] **Step 1: Create `RedirectController` (without click tracking for now)**

```java
package com.eltondev.urlshortener.redirect;

import com.eltondev.urlshortener.link.LinkCacheService;
import com.eltondev.urlshortener.link.LinkNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class RedirectController {

    private final LinkCacheService linkCacheService;

    public RedirectController(LinkCacheService linkCacheService) {
        this.linkCacheService = linkCacheService;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = linkCacheService.resolve(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(originalUrl))
            .build();
    }
}
```

- [ ] **Step 2: Manual verification**

Run: `docker compose up -d postgres redis && cd backend && mvn spring-boot:run`
Run in another terminal: `curl -s -X POST http://localhost:8080/api/links -H "Content-Type: application/json" -d '{"originalUrl":"https://example.com"}'`
Then: `curl -i http://localhost:8080/<shortCode returned above>`
Expected: `HTTP/1.1 302` (or `HTTP/1.1 302 Found` depending on client) with `Location: https://example.com`.
Stop the app with Ctrl+C.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/eltondev/urlshortener/redirect
git commit -m "feat: add redirect endpoint using Redis cache-aside"
```

---

### Task 6: GeoIP service (MaxMind)

**Files:**
- Create: `backend/src/main/java/com/eltondev/urlshortener/click/GeoLocation.java`
- Create: `backend/src/main/java/com/eltondev/urlshortener/click/GeoIpService.java`
- Manual: download `GeoLite2-City.mmdb` into `backend/src/main/resources/geoip/`

**Interfaces:**
- Produces: `GeoIpService.lookup(String ip): GeoLocation` where `GeoLocation` has `country()` and `city()` (both nullable) — consumed by `ClickTrackingService` in Task 9.

- [ ] **Step 1: Obtain the GeoLite2 City database**

MaxMind requires a free account to download GeoLite2. Sign up at
`https://www.maxmind.com/en/geolite2/signup`, generate a license key, then
download:

```bash
curl -L "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&license_key=YOUR_LICENSE_KEY&suffix=tar.gz" -o geolite2-city.tar.gz
tar -xzf geolite2-city.tar.gz --strip-components=1 --wildcards '*/GeoLite2-City.mmdb'
mkdir -p backend/src/main/resources/geoip
mv GeoLite2-City.mmdb backend/src/main/resources/geoip/
```

Add `backend/src/main/resources/geoip/GeoLite2-City.mmdb` to `.gitignore`
(the file is ~60MB and redistributable only under MaxMind's license — each
developer downloads their own copy):

```bash
echo "backend/src/main/resources/geoip/*.mmdb" >> .gitignore
```

- [ ] **Step 2: Create `GeoLocation`**

```java
package com.eltondev.urlshortener.click;

public record GeoLocation(String country, String city) {
    public static final GeoLocation UNKNOWN = new GeoLocation(null, null);
}
```

- [ ] **Step 3: Create `GeoIpService`**

```java
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
```

Note: `init()` degrades gracefully to `UNKNOWN` lookups when the `.mmdb`
file is missing (e.g. a fresh clone before Step 1 is done locally), so the
rest of the app keeps working without it.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/eltondev/urlshortener/click/GeoLocation.java backend/src/main/java/com/eltondev/urlshortener/click/GeoIpService.java .gitignore
git commit -m "feat: add GeoIP lookup service"
```

---

### Task 7: User-Agent parsing service

**Files:**
- Create: `backend/src/main/java/com/eltondev/urlshortener/click/ParsedUserAgent.java`
- Create: `backend/src/main/java/com/eltondev/urlshortener/click/UserAgentParsingService.java`

**Interfaces:**
- Produces: `UserAgentParsingService.parse(String userAgentHeader): ParsedUserAgent` where `ParsedUserAgent` has `deviceType()`, `browser()`, `os()` — consumed by `ClickTrackingService` in Task 9.

- [ ] **Step 1: Create `ParsedUserAgent`**

```java
package com.eltondev.urlshortener.click;

public record ParsedUserAgent(String deviceType, String browser, String os) {
    public static final ParsedUserAgent UNKNOWN = new ParsedUserAgent("unknown", "unknown", "unknown");
}
```

- [ ] **Step 2: Create `UserAgentParsingService`**

```java
package com.eltondev.urlshortener.click;

import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;

@Service
public class UserAgentParsingService {

    private final Parser parser = new Parser();

    public ParsedUserAgent parse(String userAgentHeader) {
        if (userAgentHeader == null || userAgentHeader.isBlank()) {
            return ParsedUserAgent.UNKNOWN;
        }
        Client client = parser.parse(userAgentHeader);
        String deviceType = client.device != null && client.device.family != null
            ? client.device.family
            : "unknown";
        String browser = client.userAgent != null && client.userAgent.family != null
            ? client.userAgent.family
            : "unknown";
        String os = client.os != null && client.os.family != null
            ? client.os.family
            : "unknown";
        return new ParsedUserAgent(deviceType, browser, os);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/eltondev/urlshortener/click/ParsedUserAgent.java backend/src/main/java/com/eltondev/urlshortener/click/UserAgentParsingService.java
git commit -m "feat: add user-agent parsing service"
```

---

### Task 8: Click event persistence

**Files:**
- Create: `backend/src/main/java/com/eltondev/urlshortener/click/ClickEvent.java`
- Create: `backend/src/main/java/com/eltondev/urlshortener/click/ClickEventRepository.java`

**Interfaces:**
- Consumes: `Link` entity (Task 4).
- Produces: `ClickEventRepository extends JpaRepository<ClickEvent, Long>` — consumed by `ClickTrackingService` (Task 9) and `StatsService` (Task 10).

- [ ] **Step 1: Create `ClickEvent`**

```java
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
```

- [ ] **Step 2: Create `ClickEventRepository`**

```java
package com.eltondev.urlshortener.click;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {
    long countByLinkShortCode(String shortCode);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/eltondev/urlshortener/click/ClickEvent.java backend/src/main/java/com/eltondev/urlshortener/click/ClickEventRepository.java
git commit -m "feat: add click event entity and repository"
```

---

### Task 9: Async click tracking wired into the redirect

**Files:**
- Create: `backend/src/main/java/com/eltondev/urlshortener/config/AsyncConfig.java`
- Create: `backend/src/main/java/com/eltondev/urlshortener/click/ClickTrackingService.java`
- Modify: `backend/src/main/java/com/eltondev/urlshortener/redirect/RedirectController.java`

**Interfaces:**
- Consumes: `GeoIpService.lookup` (Task 6), `UserAgentParsingService.parse` (Task 7), `ClickEventRepository.save` (Task 8), `LinkRepository.findByShortCodeAndActiveTrue` (Task 4).
- Produces: `ClickTrackingService.recordClickAsync(String shortCode, HttpServletRequest request): void` — consumed by `RedirectController`.

- [ ] **Step 1: Create `AsyncConfig`**

```java
package com.eltondev.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("click-tracking-");
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 2: Create `ClickTrackingService`**

```java
package com.eltondev.urlshortener.click;

import com.eltondev.urlshortener.link.Link;
import com.eltondev.urlshortener.link.LinkRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ClickTrackingService {

    private static final Logger log = LoggerFactory.getLogger(ClickTrackingService.class);

    private final LinkRepository linkRepository;
    private final ClickEventRepository clickEventRepository;
    private final GeoIpService geoIpService;
    private final UserAgentParsingService userAgentParsingService;

    public ClickTrackingService(LinkRepository linkRepository,
                                 ClickEventRepository clickEventRepository,
                                 GeoIpService geoIpService,
                                 UserAgentParsingService userAgentParsingService) {
        this.linkRepository = linkRepository;
        this.clickEventRepository = clickEventRepository;
        this.geoIpService = geoIpService;
        this.userAgentParsingService = userAgentParsingService;
    }

    @Async
    public void recordClickAsync(String shortCode, HttpServletRequest request) {
        linkRepository.findByShortCodeAndActiveTrue(shortCode).ifPresentOrElse(
            link -> persistClick(link, request),
            () -> log.warn("Skipped click recording: no active link for code {}", shortCode)
        );
    }

    private void persistClick(Link link, HttpServletRequest request) {
        String ip = extractClientIp(request);
        var geo = geoIpService.lookup(ip);
        var ua = userAgentParsingService.parse(request.getHeader("User-Agent"));

        ClickEvent event = new ClickEvent(
            link,
            hashIp(ip),
            geo.country(),
            geo.city(),
            ua.deviceType(),
            ua.browser(),
            ua.os(),
            request.getHeader("Referer")
        );
        clickEventRepository.save(event);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String hashIp(String ip) {
        if (ip == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 3: Wire it into `RedirectController`**

Replace the full file `backend/src/main/java/com/eltondev/urlshortener/redirect/RedirectController.java`:

```java
package com.eltondev.urlshortener.redirect;

import com.eltondev.urlshortener.click.ClickTrackingService;
import com.eltondev.urlshortener.link.LinkCacheService;
import com.eltondev.urlshortener.link.LinkNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class RedirectController {

    private final LinkCacheService linkCacheService;
    private final ClickTrackingService clickTrackingService;

    public RedirectController(LinkCacheService linkCacheService, ClickTrackingService clickTrackingService) {
        this.linkCacheService = linkCacheService;
        this.clickTrackingService = clickTrackingService;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        String originalUrl = linkCacheService.resolve(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));

        clickTrackingService.recordClickAsync(shortCode, request);

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(originalUrl))
            .build();
    }
}
```

- [ ] **Step 4: Manual verification**

Run: `docker compose up -d postgres redis && cd backend && mvn spring-boot:run`
Run: create a link via `curl -X POST .../api/links`, then `curl -i http://localhost:8080/<code>`.
Then check Postgres: `docker compose exec postgres psql -U urlshortener -d urlshortener -c "SELECT * FROM click_events;"`
Expected: one row appears within a second or two of the redirect (async, so allow a brief delay).
Stop the app with Ctrl+C.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eltondev/urlshortener/config/AsyncConfig.java backend/src/main/java/com/eltondev/urlshortener/click/ClickTrackingService.java backend/src/main/java/com/eltondev/urlshortener/redirect/RedirectController.java
git commit -m "feat: record clicks asynchronously on redirect"
```

---

### Task 10: Stats endpoint

**Files:**
- Create: `backend/src/main/java/com/eltondev/urlshortener/stats/dto/DailyClickCount.java`
- Create: `backend/src/main/java/com/eltondev/urlshortener/stats/dto/StatsResponse.java`
- Create: `backend/src/main/java/com/eltondev/urlshortener/stats/StatsService.java`
- Create: `backend/src/main/java/com/eltondev/urlshortener/stats/StatsController.java`
- Modify: `backend/src/main/java/com/eltondev/urlshortener/click/ClickEventRepository.java` (add aggregate queries)
- Test: `backend/src/test/java/com/eltondev/urlshortener/stats/StatsServiceTest.java`

**Interfaces:**
- Consumes: `ClickEventRepository` (Task 8), `LinkRepository.findByShortCodeAndActiveTrue` (Task 4).
- Produces: `GET /api/links/{shortCode}/stats` → `StatsResponse`.

- [ ] **Step 1: Add aggregate queries to `ClickEventRepository`**

Replace the full file:

```java
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
```

- [ ] **Step 2: Create the stats DTOs**

```java
package com.eltondev.urlshortener.stats.dto;

import java.time.LocalDate;

public record DailyClickCount(LocalDate date, long count) {
}
```

```java
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
```

- [ ] **Step 3: Write the failing test for `StatsService`**

```java
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
        when(clickEventRepository.dailyCounts("abc1234"))
            .thenReturn(List.of(new Object[]{LocalDate.of(2026, 8, 30), 3L}));
        when(clickEventRepository.countryBreakdown("abc1234"))
            .thenReturn(List.of(new Object[]{"Brazil", 3L}));
        when(clickEventRepository.deviceBreakdown("abc1234"))
            .thenReturn(List.of(new Object[]{"Desktop", 3L}));

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
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=StatsServiceTest test`
Expected: FAIL (compile error) — `StatsService` does not exist yet.

- [ ] **Step 5: Create `StatsService`**

```java
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
            .map(row -> new DailyClickCount((LocalDate) row[0], (Long) row[1]))
            .collect(Collectors.toList());

        Map<String, Long> byCountry = toMap(clickEventRepository.countryBreakdown(shortCode));
        Map<String, Long> byDevice = toMap(clickEventRepository.deviceBreakdown(shortCode));

        return new StatsResponse(shortCode, total, dailySeries, byCountry, byDevice);
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn -Dtest=StatsServiceTest test`
Expected: PASS (2 tests green).

- [ ] **Step 7: Create `StatsController`**

```java
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
```

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/eltondev/urlshortener/stats backend/src/main/java/com/eltondev/urlshortener/click/ClickEventRepository.java
git commit -m "feat: add click statistics endpoint"
```

---

### Task 11: Full-flow integration test (Testcontainers)

**Files:**
- Create: `backend/src/test/java/com/eltondev/urlshortener/IntegrationTestBase.java`
- Create: `backend/src/test/java/com/eltondev/urlshortener/LinkFlowIntegrationTest.java`
- Modify: `backend/pom.xml` (add `awaitility` test dependency)

**Interfaces:**
- Consumes: the full stack built in Tasks 1-10.
- Produces: none (this is the plan's end-to-end verification, not a dependency of later tasks).

- [ ] **Step 1: Create `IntegrationTestBase`**

```java
package com.eltondev.urlshortener;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("urlshortener")
        .withUsername("urlshortener")
        .withPassword("urlshortener");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

- [ ] **Step 2: Write the integration test**

```java
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
```

Add the `awaitility` test dependency to `backend/pom.xml`:

```xml
<dependency>
  <groupId>org.awaitility</groupId>
  <artifactId>awaitility</artifactId>
  <version>4.2.2</version>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 3: Run the integration test**

Run: `cd backend && mvn -Dtest=LinkFlowIntegrationTest test`
Expected: PASS. Requires Docker running locally (Testcontainers pulls
`postgres:16-alpine` and `redis:7-alpine` automatically).

- [ ] **Step 4: Run the full test suite**

Run: `cd backend && mvn test`
Expected: all tests PASS (unit tests from Tasks 3, 4, 10 plus this
integration test).

- [ ] **Step 5: Commit**

```bash
git add backend/pom.xml backend/src/test/java/com/eltondev/urlshortener/IntegrationTestBase.java backend/src/test/java/com/eltondev/urlshortener/LinkFlowIntegrationTest.java
git commit -m "test: add end-to-end integration test with Testcontainers"
```

---

### Task 12: Frontend scaffold + create-link page

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.js`
- Create: `frontend/index.html`
- Create: `frontend/src/main.jsx`
- Create: `frontend/src/App.jsx`
- Create: `frontend/src/api/client.js`
- Create: `frontend/src/pages/CreateLinkPage.jsx`

**Interfaces:**
- Consumes: `POST /api/links` (Task 4).
- Produces: `apiClient.createLink(originalUrl, customAlias)`, `apiClient.getStats(shortCode)` — the latter consumed by `StatsPage` in Task 13.

- [ ] **Step 1: Create the package/build files**

`frontend/package.json`:

```json
{
  "name": "url-shortener-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.26.2",
    "recharts": "^2.12.7"
  },
  "devDependencies": {
    "@vitejs/plugin-react": "^4.3.1",
    "vite": "^5.4.6"
  }
}
```

`frontend/vite.config.js`:

```js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173
  }
})
```

`frontend/index.html`:

```html
<!doctype html>
<html lang="pt-br">
  <head>
    <meta charset="UTF-8" />
    <title>URL Shortener</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.jsx"></script>
  </body>
</html>
```

- [ ] **Step 2: Create the API client**

```js
// frontend/src/api/client.js
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export async function createLink(originalUrl, customAlias) {
  const response = await fetch(`${API_BASE_URL}/api/links`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ originalUrl, customAlias: customAlias || undefined })
  })
  const data = await response.json()
  if (!response.ok) {
    throw new Error(data.error || 'Failed to create link')
  }
  return data
}

export async function getStats(shortCode) {
  const response = await fetch(`${API_BASE_URL}/api/links/${shortCode}/stats`)
  const data = await response.json()
  if (!response.ok) {
    throw new Error(data.error || 'Failed to load stats')
  }
  return data
}
```

- [ ] **Step 3: Create `CreateLinkPage`**

```jsx
// frontend/src/pages/CreateLinkPage.jsx
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { createLink } from '../api/client'

export default function CreateLinkPage() {
  const [originalUrl, setOriginalUrl] = useState('')
  const [customAlias, setCustomAlias] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setResult(null)
    try {
      const response = await createLink(originalUrl, customAlias)
      setResult(response)
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div>
      <h1>Encurtador de Links</h1>
      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="originalUrl">URL original</label>
          <input
            id="originalUrl"
            type="url"
            required
            value={originalUrl}
            onChange={(e) => setOriginalUrl(e.target.value)}
            placeholder="https://exemplo.com/pagina"
          />
        </div>
        <div>
          <label htmlFor="customAlias">Alias personalizado (opcional)</label>
          <input
            id="customAlias"
            type="text"
            value={customAlias}
            onChange={(e) => setCustomAlias(e.target.value)}
            placeholder="promo2026"
          />
        </div>
        <button type="submit">Encurtar</button>
      </form>

      {error && <p role="alert">{error}</p>}

      {result && (
        <div>
          <p>
            Link criado: <a href={result.shortUrl}>{result.shortUrl}</a>
          </p>
          <Link to={`/stats/${result.shortCode}`}>Ver estatísticas</Link>
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 4: Create `App.jsx` and `main.jsx` (routing placeholder for Task 13's `StatsPage`)**

```jsx
// frontend/src/App.jsx
import { Routes, Route } from 'react-router-dom'
import CreateLinkPage from './pages/CreateLinkPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<CreateLinkPage />} />
    </Routes>
  )
}
```

```jsx
// frontend/src/main.jsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
)
```

- [ ] **Step 5: Manual verification**

Run: `cd frontend && npm install && npm run dev`
Open `http://localhost:5173`, with the backend running (`docker compose up -d postgres redis && cd backend && mvn spring-boot:run` in another terminal).
Expected: form submits, shows the generated short URL and a "Ver estatísticas" link (the link's target route doesn't exist yet — that's Task 13).

- [ ] **Step 6: Commit**

```bash
git add frontend/package.json frontend/vite.config.js frontend/index.html frontend/src
git commit -m "feat: scaffold React frontend with link creation page"
```

---

### Task 13: Stats dashboard page

**Files:**
- Create: `frontend/src/pages/StatsPage.jsx`
- Modify: `frontend/src/App.jsx` (add `/stats/:shortCode` route)

**Interfaces:**
- Consumes: `apiClient.getStats` (Task 12), `GET /api/links/{shortCode}/stats` (Task 10).

- [ ] **Step 1: Create `StatsPage`**

```jsx
// frontend/src/pages/StatsPage.jsx
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer } from 'recharts'
import { getStats } from '../api/client'

export default function StatsPage() {
  const { shortCode } = useParams()
  const [stats, setStats] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    getStats(shortCode).then(setStats).catch((err) => setError(err.message))
  }, [shortCode])

  if (error) {
    return <p role="alert">{error}</p>
  }

  if (!stats) {
    return <p>Carregando...</p>
  }

  return (
    <div>
      <h1>Estatísticas de {shortCode}</h1>
      <p>Total de cliques: {stats.totalClicks}</p>

      <h2>Cliques por dia</h2>
      <ResponsiveContainer width="100%" height={300}>
        <LineChart data={stats.dailySeries}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="date" />
          <YAxis allowDecimals={false} />
          <Tooltip />
          <Line type="monotone" dataKey="count" stroke="#2563eb" />
        </LineChart>
      </ResponsiveContainer>

      <h2>Por país</h2>
      <ul>
        {Object.entries(stats.byCountry).map(([country, count]) => (
          <li key={country}>{country}: {count}</li>
        ))}
      </ul>

      <h2>Por dispositivo</h2>
      <ul>
        {Object.entries(stats.byDevice).map(([device, count]) => (
          <li key={device}>{device}: {count}</li>
        ))}
      </ul>
    </div>
  )
}
```

- [ ] **Step 2: Add the route**

Replace the full file `frontend/src/App.jsx`:

```jsx
import { Routes, Route } from 'react-router-dom'
import CreateLinkPage from './pages/CreateLinkPage'
import StatsPage from './pages/StatsPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<CreateLinkPage />} />
      <Route path="/stats/:shortCode" element={<StatsPage />} />
    </Routes>
  )
}
```

- [ ] **Step 3: Manual verification**

With backend and `npm run dev` running, create a link, follow it once via
`curl` or the browser (to generate a click), wait a couple seconds, then
click "Ver estatísticas".
Expected: total clicks = 1, one point on the line chart, one entry each
under "Por país" and "Por dispositivo".

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/StatsPage.jsx frontend/src/App.jsx
git commit -m "feat: add stats dashboard page with charts"
```

---

### Task 14: Dockerize everything and wire the full Compose stack

**Files:**
- Create: `backend/Dockerfile`
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`
- Modify: `docker-compose.yml` (add `backend` and `frontend` services)
- Create: `README.md`

**Interfaces:**
- Consumes: everything from Tasks 1-13.
- Produces: a fully runnable stack via `docker compose up`.

- [ ] **Step 1: Create `backend/Dockerfile`**

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/url-shortener-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Create `frontend/Dockerfile`**

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json .
RUN npm install
COPY . .
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

- [ ] **Step 3: Create `frontend/nginx.conf`**

```nginx
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

- [ ] **Step 4: Replace `docker-compose.yml` with the full stack**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: urlshortener
      POSTGRES_USER: urlshortener
      POSTGRES_PASSWORD: urlshortener
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U urlshortener"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: urlshortener
      DB_USER: urlshortener
      DB_PASSWORD: urlshortener
      REDIS_HOST: redis
      REDIS_PORT: 6379
      APP_BASE_URL: http://localhost:8080
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_started

  frontend:
    build:
      context: ./frontend
      args:
        VITE_API_BASE_URL: http://localhost:8080
    ports:
      - "5173:80"
    depends_on:
      - backend

volumes:
  postgres_data:
```

- [ ] **Step 5: Create `README.md`**

```markdown
# URL Shortener

Serviço de encurtamento de links (estilo Bitly): cria links curtos com alias
opcional, registra cliques com geolocalização e dispositivo, e exibe
estatísticas de acesso.

## Stack

Java 21, Spring Boot 3, PostgreSQL, Redis, React (Vite), Docker Compose.

## Rodando localmente

1. Baixe o banco GeoLite2 City da MaxMind (gratuito, requer conta) e coloque
   em `backend/src/main/resources/geoip/GeoLite2-City.mmdb` — veja o passo
   detalhado no plano de implementação
   (`docs/superpowers/plans/2026-08-30-url-shortener.md`, Task 6).
2. `docker compose up --build`
3. Frontend em `http://localhost:5173`, API em `http://localhost:8080`.

## Rodando os testes do backend

```bash
cd backend
mvn test
```

Os testes de integração usam Testcontainers e exigem Docker rodando.

## Design e decisões técnicas

Ver `docs/superpowers/specs/2026-08-30-url-shortener-design.md`.
```

- [ ] **Step 6: Full stack verification**

Run: `docker compose up --build`
Expected: all four containers start; `curl -X POST http://localhost:8080/api/links -H "Content-Type: application/json" -d '{"originalUrl":"https://example.com"}'` returns 201; visiting `http://localhost:5173` shows the create-link form and it works end-to-end against the containerized backend.
Stop with `docker compose down`.

- [ ] **Step 7: Commit**

```bash
git add backend/Dockerfile frontend/Dockerfile frontend/nginx.conf docker-compose.yml README.md
git commit -m "feat: dockerize backend and frontend, wire full compose stack"
```

---

## Self-Review Notes

- **Spec coverage:** custom alias (Task 4), redirect (Task 5), click
  tracking with geo/device/referrer (Tasks 6-9), stats endpoint with
  totals/daily series/country/device (Task 10), React frontend for
  creation + dashboard (Tasks 12-13), Docker Compose local execution
  (Task 14), Testcontainers integration test (Task 11), AWS path documented
  in the spec itself (no task needed — explicitly out of scope for this
  plan). All spec sections are covered.
- **Placeholder scan:** no TBD/TODO markers; every step has runnable code
  or an exact shell command.
- **Type consistency:** `LinkResponse.getShortCode()`/`getShortUrl()` (Task
  4) match usage in the integration test (Task 11) and `client.js` (Task
  12). `StatsResponse` field names (`totalClicks`, `dailySeries`,
  `byCountry`, `byDevice`, Task 10) match `StatsPage.jsx` (Task 13).
  `LinkCacheService.resolve`/`put` (introduced in Task 4 Step 7, reused as
  the final version) match calls in `LinkService` (Task 4) and
  `RedirectController` (Tasks 5 and 9).

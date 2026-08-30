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

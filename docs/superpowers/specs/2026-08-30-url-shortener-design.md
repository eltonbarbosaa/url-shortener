# URL Shortener — Design Spec

**Data:** 2026-08-30
**Status:** Aprovado para planejamento de implementação

## Objetivo

Serviço de encurtamento de links no estilo Bitly: gerar links curtos, redirecionar
para a URL original, contabilizar cliques e exibir estatísticas de acesso
(total, série temporal, geografia, dispositivo/navegador/referrer). Sem
sistema de contas — uso aberto, incluindo alias customizado opcional.
Projeto de estudo/portfólio (baixo volume esperado), com boas práticas de
arquitetura para ficar pronto para escalar depois.

## Escopo

**Incluído:**
- Criar link curto a partir de uma URL longa, com alias customizado opcional
  (unicidade validada; se vazio, gera código aleatório)
- Redirecionamento (302) pelo código curto
- Registro de cada clique: timestamp, geolocalização por IP, dispositivo,
  navegador, sistema operacional, referrer
- Estatísticas por link: contagem total, série diária, breakdown por país,
  breakdown por dispositivo/navegador
- Frontend React (Vite): tela de criação de link + dashboard de estatísticas
- Execução local completa via Docker Compose

**Fora do escopo (v1):**
- Autenticação / contas de usuário
- Deploy real na AWS (documentado como próximo passo, não provisionado)
- Fila de mensagens (Kafka/RabbitMQ) — não necessária no volume esperado
- Expiração de links, domínios customizados, QR code

## Arquitetura

```
[React SPA] → [Spring Boot API] → [PostgreSQL]  (dados duráveis: links, cliques)
                     ↓
                  [Redis]  (cache do mapeamento short_code → URL)
```

- **Spring Boot 3 (Java 21)**: API REST, validação, orquestração.
- **PostgreSQL**: fonte da verdade para `links` e `click_events`. Estatísticas
  são queries agregadas sobre `click_events` (sem necessidade de sincronizar
  contadores com o Redis).
- **Redis**: cache-aside para o hot path do redirect (`short:{code} → url`).
  Ao criar um link, grava no Postgres e popula o Redis. No redirect, primeiro
  consulta o Redis; cache miss cai para o Postgres e repovoa o cache.
- **Geolocalização**: banco MaxMind GeoLite2 (offline, sem chamada externa),
  via lib `com.maxmind.geoip2`.
- **Parsing de User-Agent**: lib `ua-parser` para extrair device/browser/OS.
- Registro do clique é assíncrono (`@Async`) para não bloquear a resposta do
  redirect.

### Por que não contador atômico no Redis (INCR)?

Considerado como alternativa, mas descartado para v1: exigiria sincronização
periódica entre o contador Redis e o Postgres, complexidade que só se paga em
alto tráfego. Como o volume esperado é baixo (projeto de portfólio),
`COUNT(*)` agregado em `click_events` já entrega contagem total, série
temporal e breakdowns sem esse custo extra. Pode ser revisitado se o projeto
evoluir para produção com tráfego real.

## Modelo de dados

**`links`**
- `id` (PK)
- `short_code` (unique, indexado)
- `original_url`
- `is_custom_alias` (bool)
- `created_at`
- `active` (bool)

**`click_events`**
- `id` (PK)
- `link_id` (FK → links)
- `clicked_at`
- `ip_hash` (IP não é guardado em claro, por privacidade)
- `country`, `city`
- `device_type`, `browser`, `os`
- `referrer`

Migrations gerenciadas via Flyway.

## API

- `POST /api/links` — body `{originalUrl, customAlias?}` → `{shortCode, shortUrl}`.
  Valida formato da URL e unicidade do alias customizado (409 se já existir).
- `GET /{shortCode}` — redireciona (302) para `original_url`; registra o
  clique de forma assíncrona.
- `GET /api/links/{shortCode}` — detalhes do link.
- `GET /api/links/{shortCode}/stats` — total de cliques, série diária,
  breakdown por país, breakdown por dispositivo/navegador.

## Frontend

React + Vite, servido via Nginx em produção/Docker.
- Tela 1: formulário de criação (URL original + alias opcional), exibe o link
  gerado.
- Tela 2: dashboard de estatísticas por link (contagem total, gráfico de
  série temporal, breakdown geográfico, breakdown de dispositivo).

## Infraestrutura local

`docker-compose.yml` com 4 serviços:
- `backend` (Spring Boot)
- `frontend` (Nginx + build estático do React)
- `postgres`
- `redis`

Testes de integração usando Testcontainers (Postgres e Redis reais em
container, não mocks).

## Caminho para AWS (documentado, não executado agora)

Aplicação desenhada stateless de propósito para permitir migração futura:
- Backend → ECS Fargate (ou Elastic Beanstalk)
- PostgreSQL → RDS
- Redis → ElastiCache
- Frontend estático → S3 + CloudFront

Nenhum recurso AWS será provisionado nesta fase — fica documentado como
próximo passo.

## Testes

- Testes unitários para regras de negócio (geração de código, validação de
  alias, parsing de estatísticas).
- Testes de integração com Testcontainers cobrindo o fluxo completo:
  criar link → redirecionar → registrar clique → consultar estatísticas.

## Decisões em aberto para revisão futura

Nenhuma — escopo e decisões técnicas fechados para v1 nesta spec.

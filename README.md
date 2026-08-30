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

> **Nota (Windows):** se `curl`/o navegador travarem ao chamar `localhost`
> logo após instalar o Docker Desktop com WSL2, teste com `127.0.0.1`
> explicitamente — foi um problema observado de resolução de `localhost`
> para IPv6 (`::1`) sem o encaminhamento de porta correspondente do Docker
> Desktop. Some após um `wsl --shutdown` + reinício do Docker Desktop, ou
> desaparece sozinho depois de mais um reboot completo do Windows.

## Rodando os testes do backend

```bash
cd backend
mvn test
```

Os testes de integração usam Testcontainers e exigem Docker rodando.

> **Nota (Windows):** em algumas instalações do Docker Desktop, a lib
> `docker-java` (usada pelo Testcontainers) não consegue detectar o daemon
> via named pipe (`Could not find a valid Docker environment`), mesmo com
> `docker info` funcionando normalmente pelo CLI. É uma incompatibilidade
> conhecida entre `docker-java` e versões recentes do Docker Desktop no
> Windows, não um problema do código. Nesse caso, valide manualmente via
> `docker compose up --build` + chamadas HTTP (veja acima), como foi feito
> durante o desenvolvimento deste projeto.

## Design e decisões técnicas

Ver `docs/superpowers/specs/2026-08-30-url-shortener-design.md`.

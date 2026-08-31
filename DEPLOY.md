# Deploy no Railway

Guia passo a passo pra colocar o projeto no ar gratuitamente via Railway,
usando o free tier (sem cartão de crédito pro trial).

## 1. Criar o projeto

1. Acesse [railway.app](https://railway.app) e faça login com sua conta
   GitHub.
2. **New Project → Deploy from GitHub repo → eltonbarbosaa/url-shortener**.

## 2. Adicionar os bancos gerenciados

Em vez de rodar Postgres/Redis nos nossos próprios containers (como no
`docker-compose.yml` local), use os plugins gerenciados do Railway —
mais simples e sem volume pra gerenciar:

1. No projeto, clique em **+ New → Database → Add PostgreSQL**.
2. Clique em **+ New → Database → Add Redis**.

## 3. Criar o serviço do backend

1. **+ New → GitHub Repo** → selecione o mesmo repositório de novo.
2. Nas configurações desse serviço (**Settings**):
   - **Root Directory**: `backend`
   - Railway detecta o `Dockerfile` automaticamente.
3. Em **Variables**, adicione (usando as referências das variáveis do
   Postgres/Redis que você criou no passo 2 — Railway permite referenciar
   `${{Postgres.PGHOST}}` etc diretamente):

   | Variável         | Valor |
   |------------------|-------|
   | `DB_HOST`        | `${{Postgres.PGHOST}}` |
   | `DB_PORT`        | `${{Postgres.PGPORT}}` |
   | `DB_NAME`        | `${{Postgres.PGDATABASE}}` |
   | `DB_USER`        | `${{Postgres.PGUSER}}` |
   | `DB_PASSWORD`    | `${{Postgres.PGPASSWORD}}` |
   | `REDIS_HOST`     | `${{Redis.REDISHOST}}` |
   | `REDIS_PORT`     | `${{Redis.REDISPORT}}` |
   | `APP_BASE_URL`   | a URL pública que o Railway vai gerar pra esse serviço (veja Settings → Networking → Generate Domain primeiro, depois cole aqui) |
   | `FRONTEND_ORIGINS` | a URL pública do serviço de frontend (passo 4) — pode deixar em branco e voltar aqui depois de criar o frontend |

4. Em **Settings → Networking**, clique em **Generate Domain** pra ter uma
   URL pública HTTPS pro backend.

## 4. Criar o serviço do frontend

1. **+ New → GitHub Repo** → selecione o repositório de novo.
2. **Settings → Root Directory**: `frontend`
3. Em **Variables**, adicione a variável de build:

   | Variável              | Valor |
   |-----------------------|-------|
   | `VITE_API_BASE_URL`   | a URL pública do backend gerada no passo 3.4 |

   > Importante: `VITE_API_BASE_URL` é lida em **build time** pelo Vite
   > (não em runtime), então qualquer mudança nela exige um novo deploy do
   > frontend pra ter efeito.
4. **Settings → Networking → Generate Domain** pra ter a URL pública do
   frontend.
5. Volte no serviço do **backend** e finalize a variável `FRONTEND_ORIGINS`
   com essa URL do frontend (necessário pro CORS liberar as chamadas).

## 5. Redeploy

Depois de ajustar as variáveis cruzadas (backend precisa saber a URL do
frontend, frontend precisa saber a URL do backend), clique em **Deploy**
nos dois serviços de novo pra aplicar.

## 6. GeoIP em produção (opcional)

O banco GeoLite2 (`.mmdb`) não vai pro Git (está no `.gitignore`, ~60MB e
licenciado pela MaxMind). Pra ter geolocalização real em produção, a forma
mais simples é baixar o arquivo durante o build: adicione ao
`backend/Dockerfile`, antes do build Maven, um `curl` autenticado com sua
license key da MaxMind salvando em
`src/main/resources/geoip/GeoLite2-City.mmdb`. Sem isso, o campo
país/cidade retorna "desconhecido" — o resto do app funciona normalmente.

## Custos

O free tier do Railway inclui um crédito mensal (varia por época — confira
em [railway.app/pricing](https://railway.app/pricing)) suficiente pra um
projeto de portfólio com tráfego baixo. Passar do limite pausa os
serviços até o próximo ciclo, não gera cobrança automática sem você
adicionar um cartão.

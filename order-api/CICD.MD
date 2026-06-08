# ⚙️ CI/CD Pipeline — GitHub Actions

Pipeline di build, test e deploy automatico per `order-api`. Ogni push su `main` scatena l'intera catena: test → Docker image → deploy su Railway.

---

## Indice

1. [Come funziona](#1-come-funziona)
2. [I tre job della pipeline](#2-i-tre-job-della-pipeline)
3. [Secrets da configurare](#3-secrets-da-configurare)
4. [Setup Railway](#4-setup-railway)
5. [Attivare la pipeline](#5-attivare-la-pipeline)
6. [Leggere i risultati su GitHub](#6-leggere-i-risultati-su-github)
7. [Errori comuni](#7-errori-comuni)

---

## 1. Come funziona

GitHub Actions esegue automaticamente il file `.github/workflows/ci-cd.yml` ad ogni evento definito nel trigger:

```
push su main o develop   →   pipeline parte
pull request verso main  →   solo Job 1 (test)
```

La pipeline è sequenziale con dipendenze: se un job fallisce, i successivi non partono.

```
git push → main
    │
    ▼
[Job 1] Build & Test
    └── ./mvnw clean verify
    └── Testcontainers avvia PostgreSQL in Docker
    └── tutti i test girano
    └── report salvato come artifact
    │
    ▼ solo se test OK + branch main
[Job 2] Build Docker Image
    └── ./mvnw package -DskipTests
    └── build Docker image (multi-stage)
    └── push su ghcr.io/tuonome/order-api:latest
    │
    ▼ solo se image OK
[Job 3] Deploy
    └── Railway scarica la nuova immagine
    └── zero-downtime deploy in produzione
```

---

## 2. I tre job della pipeline

### Job 1 — Build & Test

Gira su ogni push e ogni pull request. È il gate di qualità: niente va in produzione se i test non passano.

```yaml
- name: Setup Java 21
  uses: actions/setup-java@v4
  with:
    java-version: '21'
    distribution: 'temurin'
    cache: maven        # cache dipendenze — build successivi più veloci
```

```yaml
- name: Build e test
  run: ./mvnw clean verify
  env:
    JWT_SECRET: chiave-di-test-lunga-almeno-32-caratteri
```

`mvn clean verify` esegue compile + test + package. Testcontainers avvia un container PostgreSQL reale dentro il runner GitHub — nessun DB esterno necessario.

Il report dei test viene sempre caricato come artifact (`if: always()`), anche quando i test falliscono — così puoi vedere cosa è andato storto direttamente su GitHub.

---

### Job 2 — Build Docker Image

Parte solo se Job 1 è verde e solo su branch `main`. Builda il JAR, costruisce l'immagine Docker con il Dockerfile multi-stage e la pusha su **GitHub Container Registry** (`ghcr.io`).

```yaml
needs: build-and-test
if: github.ref == 'refs/heads/main'
```

L'immagine viene taggata con due tag:
- `latest` — sempre aggiornato all'ultimo commit su main
- `sha-abc1234` — tag immutabile per ogni commit (utile per rollback)

```yaml
tags: |
  type=sha,prefix=sha-
  type=raw,value=latest
```

`GITHUB_TOKEN` è automatico — GitHub lo genera senza configurazione manuale. Serve per pushare su `ghcr.io`.

---

### Job 3 — Deploy

Parte solo se Job 2 è verde. Notifica Railway di fare il deploy della nuova immagine.

```yaml
needs: build-image
if: github.ref == 'refs/heads/main'
```

Railway si occupa di tutto il resto: pull della nuova immagine, zero-downtime swap, health check.

---

## 3. Secrets da configurare

Vai su: `GitHub repo` → `Settings` → `Secrets and variables` → `Actions` → `New repository secret`

| Secret | Come ottenerlo |
|---|---|
| `RAILWAY_TOKEN` | Railway dashboard → Account Settings → Tokens → New token |
| `JWT_SECRET` | genera con `openssl rand -base64 32` |

`GITHUB_TOKEN` **non va configurato** — è automatico su ogni repository.

### Generare una chiave JWT sicura

```bash
# Mac / Linux
openssl rand -base64 32

# Windows PowerShell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
```

---

## 4. Setup Railway

### Prima volta

1. Crea account su [railway.app](https://railway.app)
2. `New Project` → `Deploy from GitHub repo` → seleziona `order-api`
3. Dal marketplace Railway aggiungi un servizio **PostgreSQL**
4. In `Variables` del servizio `order-api` aggiungi:

```
JWT_SECRET=la-tua-chiave-generata
SPRING_DATASOURCE_URL=${{Postgres.DATABASE_URL}}
```

Railway inietta automaticamente le variabili del PostgreSQL collegato — `${{Postgres.DATABASE_URL}}` si risolve al valore reale.

### Token per GitHub Actions

`Account Settings` → `Tokens` → `New Token` → copia il valore → incollalo nel secret `RAILWAY_TOKEN` su GitHub.

---

## 5. Attivare la pipeline

```bash
# crea la cartella workflows se non esiste
mkdir -p .github/workflows

# aggiungi il file ci-cd.yml (vedi repository)
# poi committa e pusha

git add .github/workflows/ci-cd.yml
git commit -m "feat: aggiungi pipeline CI/CD"
git push origin main
```

La pipeline parte automaticamente. Vai su `github.com/tuonome/order-api` → tab **Actions** per vedere i job in tempo reale.

---

## 6. Leggere i risultati su GitHub

### Tab Actions

Ogni push crea una nuova pipeline run. Clicca per vedere:
- i tre job con stato (verde ✓ / rosso ✗ / giallo in corso)
- i log di ogni step
- gli artifact scaricabili (report test)

### Badge di stato

Aggiungi questo in cima al README principale per mostrare lo stato della pipeline:

```markdown
![CI/CD](https://github.com/tuonome/order-api/actions/workflows/ci-cd.yml/badge.svg)
```

### Report test come artifact

Se i test falliscono, scarica l'artifact `test-reports` dalla run fallita — contiene i file XML Surefire con il dettaglio di ogni test e il messaggio di errore.

---

## 7. Errori comuni

| Errore | Causa | Soluzione |
|---|---|---|
| `RAILWAY_TOKEN` not found | Secret non configurato | Aggiungilo in Settings → Secrets |
| `release version 21 not supported` | Runner usa Java vecchio | Verificare `java-version: '21'` nello step setup-java |
| Testcontainers: `Cannot connect to Docker` | Docker non disponibile nel runner | Usare `ubuntu-latest` — ha Docker preinstallato |
| `JWT_SECRET` too short | Chiave troppo corta nell'env del job | Usare `openssl rand -base64 32` per generarla |
| Build OK ma deploy non parte | Job 2 non su branch main | La pipeline deploy gira solo su `main`, non su altri branch |
| Immagine non trovata su Railway | Railway non collegato a ghcr.io | Configurare il deploy da GitHub nel progetto Railway |

---

> 🚀 Con questa pipeline ogni `git push main` porta automaticamente le modifiche in produzione — build, test, deploy in meno di 3 minuti.
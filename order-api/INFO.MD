# 🔐 Spring Security & JWT — order-api

Guida pratica all'implementazione dell'autenticazione in **Java 21 + Spring Boot 3**.

---

## Indice

1. [Perché Spring Security](#1-perché-spring-security)
2. [Cos'è un JWT](#2-cosè-un-jwt)
3. [Access Token e Refresh Token](#3-access-token-e-refresh-token)
4. [I componenti](#4-i-componenti)
5. [Il flusso completo](#5-il-flusso-completo)
6. [Configurazione](#6-configurazione)
7. [Errori comuni](#7-errori-comuni)
8. [Prossimi passi](#8-prossimi-passi)

---

## 1. Perché Spring Security

Senza protezione, qualsiasi chiamata a `POST /api/orders` è accessibile da chiunque.
Spring Security intercetta ogni richiesta HTTP **prima** che raggiunga il controller e decide se lasciarla passare o bloccarla.

Ogni richiesta attraversa una catena di filtri (`FilterChain`). Il nostro `JwtAuthFilter` si inserisce in questa catena per validare il token.

| Componente | Ruolo |
|---|---|
| `FilterChain` | Sequenza di filtri che ogni richiesta attraversa in ordine |
| `JwtAuthFilter` | Legge il token dall'header, lo valida, popola il SecurityContext |
| `SecurityContext` | Contenitore thread-local con i dati dell'utente autenticato |
| `Authentication` | Oggetto che rappresenta chi è autenticato: email + ruolo |

> ⚠️ Spring Security blocca **tutto** di default. Devi esplicitamente dichiarare cosa è pubblico.

---

## 2. Cos'è un JWT

JWT (JSON Web Token) è un token compatto e autocontenuto. Il server **non consulta il database** per sapere chi sei — tutte le informazioni sono dentro il token, firmate crittograficamente.

### Struttura

```
xxxxx.yyyyy.zzzzz
Header.Payload.Signature
```

| Parte | Contenuto |
|---|---|
| `Header` | Algoritmo usato per la firma (HS256). Codificato in Base64URL. |
| `Payload` | I dati: subject (email), ruolo, issued-at, expiration. Base64URL. **NON cifrato.** |
| `Signature` | HMAC-SHA256 di header+payload firmato con la chiave segreta. Garantisce l'integrità. |

> 🔑 Il Payload è solo codificato, non cifrato. Non mettere mai password o dati sensibili dentro un JWT.

### Perché il server non va sul DB?

Quando il client manda il token, il server:
1. Ricalcola la firma con la propria chiave segreta
2. Se combacia → il token è autentico e non manomesso
3. Legge email e ruolo direttamente dal Payload
4. Controlla la data di scadenza (`exp`)

Nessuna query al database. Ideale per microservizi.

---

## 3. Access Token e Refresh Token

| Token | Scopo | Durata |
|---|---|---|
| **Access Token** | JWT per autenticare ogni richiesta API | 15 minuti |
| **Refresh Token** | UUID opaco per ottenere un nuovo access token | 7 giorni |

### Perché due token separati?

Un solo token long-lived è pericoloso: se rubato, l'attaccante ha accesso per giorni.

- **Access token corto** — anche se intercettato, scade in 15 minuti
- **Refresh token nel DB** — il server può revocarlo immediatamente
- **Rotation** — ogni uso del refresh token ne genera uno nuovo e revoca il vecchio

### Refresh Token Rotation

```
Client usa refreshToken A
  → Server revoca A, emette refreshToken B + nuovo accessToken

Se un attaccante aveva rubato A e lo usa DOPO il client legittimo:
  → A è già revocato → errore → segnale di compromissione
```

---

## 4. I componenti

### `JwtService`
Genera e valida i JWT con firma HMAC-SHA256 (libreria JJWT).

```java
jwtService.generate(email, role)   // crea JWT con subject, claims e scadenza
jwtService.isValid(token)          // verifica firma e scadenza senza eccezioni
jwtService.extractEmail(token)     // legge il Payload
jwtService.extractRole(token)      // legge il Payload
```

### `RefreshTokenService`
Gestisce il ciclo di vita dei refresh token nel database.

```java
refreshTokenService.create(user)     // revoca i precedenti, genera UUID, salva con scadenza
refreshTokenService.validate(token)  // cerca nel DB, controlla revoca e scadenza
refreshTokenService.revoke(token)    // soft delete — marca come revocato
```

### `JwtAuthFilter`
Filtro che intercetta ogni richiesta HTTP. Estende `OncePerRequestFilter`.

| Caso | Comportamento |
|---|---|
| Header assente o non Bearer | Passa al filtro successivo senza autenticare |
| Token presente ma non valido | Passa al filtro successivo senza autenticare |
| Token valido | Popola il SecurityContext con email e ruolo |

### `SecurityConfig`
Configura regole di accesso e catena di filtri.

```java
.requestMatchers("/api/auth/**").permitAll()              // pubblico
.requestMatchers("/api/orders/**").hasAnyRole("USER", "ADMIN")
.requestMatchers("/api/admin/**").hasRole("ADMIN")
```

- Sessioni disabilitate: `STATELESS` — nessun cookie, nessuna sessione server-side
- CSRF disabilitato — inutile per API REST stateless

### `AuthService`
Orchestratore: collega tutti i pezzi.

```java
authService.register(req)         // valida email, BCrypt su password, salva utente, emette token
authService.login(req)            // trova utente, BCrypt.matches(), emette token
authService.refresh(refreshToken) // valida, revoca (rotation), emette nuovi token
```

> 🔒 BCrypt è un algoritmo di hashing a senso unico con salt automatico. Non è possibile risalire alla password originale.

---

## 5. Il flusso completo

### Registrazione

```
POST /api/auth/register  { email, password, role? }

1. Verifica che l'email non esista già
2. Hash della password con BCrypt
3. Salva utente nel DB (ruolo USER di default)
4. Genera accessToken (JWT, 15 min)
5. Genera refreshToken (UUID, 7 giorni) → salvato nel DB
6. Restituisce AuthResponse con entrambi i token
```

### Login

```
POST /api/auth/login  { email, password }

1. Cerca utente per email → 401 se non esiste
2. BCrypt confronta password con hash nel DB → 401 se non corrisponde
3. Genera accessToken + refreshToken
4. Restituisce AuthResponse
```

### Richiesta autenticata

```
GET /api/orders
Authorization: Bearer <accessToken>

1. JwtAuthFilter legge l'header, estrae il token
2. JwtService.isValid() verifica firma e scadenza
3. Estrae email e ruolo, popola SecurityContext
4. Spring verifica che il ruolo abbia accesso alla rotta
5. La richiesta arriva al controller
```

### Refresh

```
POST /api/auth/refresh  { refreshToken }

1. RefreshTokenService cerca il token nel DB
2. Controlla che non sia revocato e non sia scaduto
3. Revoca il token corrente (rotation)
4. Genera nuovo accessToken + nuovo refreshToken
5. Restituisce AuthResponse
```

---

## 6. Configurazione

```properties
# application.properties

# JWT
jwt.secret=chiave-segreta-lunga-almeno-32-caratteri-cambiala-in-prod
jwt.expiration-seconds=900

# Spring Security — nessuna configurazione aggiuntiva necessaria
# tutto gestito da SecurityConfig
```

> ⚠️ In produzione: usa variabili d'ambiente per `jwt.secret`, mai hardcoded nel properties.

```bash
# Docker / Railway / Fly.io
JWT_SECRET=la-tua-chiave-sicura-generata-con-openssl
```

```bash
# Genera una chiave sicura
openssl rand -base64 32
```

---

## 7. Errori comuni

| Errore | Causa | Soluzione |
|---|---|---|
| `403 Forbidden` su `/api/orders` | Token mancante o scaduto | Fare login o refresh prima |
| `403` su endpoint ADMIN con USER | Ruolo insufficiente | Fare login con utente ADMIN |
| `500` su refresh token | Token già usato (rotation) o scaduto | Fare login di nuovo |
| `500` su register (email duplicata) | Manca gestione errori | Aggiungere `@ControllerAdvice` |
| `Jwt key too short` | Segreto troppo corto | Minimo 32 caratteri in `jwt.secret` |

---

## 8. Prossimi passi

| Feature | Descrizione |
|---|---|
| `@ControllerAdvice` | Global exception handler: trasforma le eccezioni in risposte HTTP strutturate |
| Testcontainers | Test di integrazione con PostgreSQL reale dentro Docker |
| GitHub Actions | Pipeline CI/CD: build, test, Docker image, deploy automatico |
| Rate limiting | Limitare tentativi di login con Bucket4j per prevenire brute-force |
| HTTPS | Mai in produzione senza TLS — su Railway/Fly.io è automatico |

---

> 🚀 Con Security + JWT implementato, `order-api` è già un portfolio piece solido.  
> Aggiungi Testcontainers e una pipeline CI/CD per renderlo indistinguibile da un progetto professionale reale.


# 🛡️ Global Exception Handler — @ControllerAdvice

Gestione centralizzata degli errori per `order-api`. Ogni eccezione viene trasformata in una risposta HTTP strutturata con status code corretto — nessuno stack trace esposto al client.

---

## Il problema senza handler

Senza `@ControllerAdvice`, Spring restituisce risposte generiche e inutili:

```json
{
  "timestamp": "2024-01-15T10:30:00.000+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/api/orders/99999"
}
```

Status code sempre 500, nessun dettaglio utile, stack trace nei log ma non nella risposta.

---

## Struttura

```
exception/
├── ErrorResponse.java              ← record risposta strutturata
├── ResourceNotFoundException.java  ← 404
├── ConflictException.java          ← 409
├── BadRequestException.java        ← 400
└── GlobalExceptionHandler.java     ← @RestControllerAdvice
```

---

## ErrorResponse — il record di risposta

Ogni errore restituisce sempre la stessa struttura:

```java
public record ErrorResponse(
    int status,
    String error,
    String message,
    String path,
    LocalDateTime timestamp,
    List<String> details    // per validazioni con più errori
) {}
```

---

## Eccezioni custom del dominio

Invece di `RuntimeException` ovunque, eccezioni con semantica precisa:

| Eccezione | Status | Quando usarla |
|---|---|---|
| `ResourceNotFoundException` | `404` | Entità non trovata per ID |
| `ConflictException` | `409` | Email duplicata, risorsa già esistente |
| `BadRequestException` | `400` | Errore logica di business |
| `MethodArgumentNotValidException` | `400` | Bean Validation (`@NotBlank`, `@Positive`...) |
| `BadCredentialsException` | `401` | Credenziali di login sbagliate |
| `AccessDeniedException` | `403` | Ruolo insufficiente per la rotta |
| `Exception` | `500` | Fallback — tutto il resto |

---

## Esempi di risposta

**404 — risorsa non trovata:**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Ordine non trovato con id: 99999",
  "path": "/api/orders/99999",
  "timestamp": "2025-01-15T10:30:00",
  "details": []
}
```

**400 — validazione fallita (più errori contemporanei):**
```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "Uno o più campi non sono validi",
  "path": "/api/orders",
  "timestamp": "2025-01-15T10:30:00",
  "details": [
    "customerId obbligatorio",
    "importo deve essere positivo"
  ]
}
```

**409 — email duplicata:**
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Email già registrata: user@demo.com",
  "path": "/api/auth/register",
  "timestamp": "2025-01-15T10:30:00",
  "details": []
}
```

**401 — credenziali sbagliate:**
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Credenziali non valide",
  "path": "/api/auth/login",
  "timestamp": "2025-01-15T10:30:00",
  "details": []
}
```

**500 — errore interno (il messaggio reale rimane nei log, mai esposto):**
```json
{
  "status": 500,
  "error": "Internal Server Error",
  "message": "Si è verificato un errore interno",
  "path": "/api/orders",
  "timestamp": "2025-01-15T10:30:00",
  "details": []
}
```

---

## Dove vengono lanciate le eccezioni

**`OrderService`:**
```java
// prima
.orElseThrow(() -> new RuntimeException("Ordine non trovato: " + id));

// dopo
.orElseThrow(() -> new ResourceNotFoundException("Ordine", id));
```

**`AuthService`:**
```java
// email duplicata → 409
if (userRepository.existsByEmail(req.email())) {
    throw new ConflictException("Email già registrata: " + req.email());
}
```

**`RefreshTokenService`:**
```java
// token non valido → 400
.orElseThrow(() -> new BadRequestException("Refresh token non valido o scaduto"));
```

---

## Note importanti

- Il fallback `Exception.class` logga l'eccezione reale ma **non la espone mai al client** — fondamentale per la sicurezza in produzione
- `@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody` — restituisce automaticamente JSON
- L'handler `AccessDeniedException` richiede che Spring Security sia configurato correttamente (`SecurityConfig`) — se non viene intercettato qui, Spring Security risponde prima con il suo handler di default
- Il campo `details` è una lista vuota `[]` per errori semplici, popolata solo per `MethodArgumentNotValidException`
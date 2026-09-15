# Blog Backend — Spring Boot

REST API per un blog, costruita con **Spring Boot 4**, **Java 17** e **PostgreSQL**.

È un progetto di apprendimento sviluppato per fasi, dove ogni decisione tecnica è documentata insieme alle alternative scartate e al perché. Il diario completo sta in [`docs/`](#diario-di-sviluppo); questo file è l'indice.

---

## Cosa fa

| verbo | endpoint | accesso | note |
|---|---|---|---|
| `POST` | `/auth/register` | pubblico | password mai in chiaro nel DB (BCrypt) |
| `POST` | `/auth/login` | pubblico | restituisce l'access token JWT |
| `GET` | `/posts` | pubblico | paginato, filtrabile per categoria, ordinabile |
| `GET` | `/posts/{id}` | pubblico | 404 con body strutturato se non esiste |
| `POST` | `/posts` | autenticato | validazione Bean Validation + regole di dominio; l'autore viene dal token, non dal body |
| `PUT` | `/posts/{id}` | autore del post | sostituzione completa della risorsa |
| `DELETE` | `/posts/{id}` | autore del post | 204, hard delete |
| `GET` | `/categories` | pubblico | albero a due livelli, una query sola |
| `POST` | `/categories` | `ADMIN` | slug generato dal server |

**Filtri e paginazione:** `?page=`, `?size=`, `?sort=campo,direzione`, `?category=slug` (accetta più valori: `?category=a,b` o parametro ripetuto).

Le categorie sono gerarchiche su due livelli: filtrare per una macro-categoria espande automaticamente la ricerca alle sottocategorie, e solo le foglie sono assegnabili a un post.

**Autenticazione:** JWT nell'header `Authorization: Bearer <token>`. Le `GET` di post e categorie restano pubbliche — è il modello di qualunque blog: contenuto leggibile da chiunque, scrittura riservata. La regola è espressa **sul verbo** (`GET` su `/posts/**` e `/categories/**`), cosa possibile solo perché in Fase 1 si è rifiutato `/posts/create`: la risorsa è l'URL, l'operazione è il verbo.

**Errori:** tutte le risposte di errore hanno la stessa forma, comprese quelle che nascono nella filter chain di Spring Security (401 e 403), che non passano dal `@RestControllerAdvice`.

```json
{
  "timestamp": "2026-09-02T11:39:15.383Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "title: non deve essere vuoto; categoryId: non deve essere null",
  "path": "/posts"
}
```

---

## Stack

Java 17 · Spring Boot 4.1 · Spring Data JPA / Hibernate 7 · PostgreSQL · Spring Security + JWT · Spring Validation · Lombok · Maven

---

## Stato

| fase | stato | contenuto |
|---|---|---|
| **1 — Fondamentali** | ✅ | Entity, repository, service, controller, DTO, mapper |
| **2 — Database & relazioni** | ✅ | `@ManyToOne`, query custom, N+1, paginazione, sottocategorie, filtri, sorting |
| **3 — Validation & errori** | ✅ | Bean Validation, CRUD completo, transazioni, error handling centralizzato, logging |
| **4 — Security** | 🔄 | `User` e ruoli, BCrypt, JWT, filter chain con regole per verbo, 401/403 con `ApiError`, autore dei post, `@PostAuthor`, `POST` e `GET /categories`. **In corso:** refresh token (4.8) |
| **5 — Polish** | ⬜ | Audit fields, soft delete, test, Swagger, Docker, CI |

> Nota: `docs/fase-4-security.md` è ancora nella versione "piano" scritta a fine Fase 3 e non riflette il codice già scritto.

Due voci sono rimandate deliberatamente, con la condizione di sblocco scritta: **Specification/Querydsl** (aspetta che il modello abbia abbastanza criteri filtrabili da rendere la combinatoria un problema vero — ora che esiste `author` la condizione si sta avvicinando) e **Flyway** (aspetta Docker, dove un Postgres vuoto rende le migrazioni il flusso normale invece del caso particolare della baseline).

---

## Diario di sviluppo

Ogni file contiene le decisioni prese, le alternative scartate, gli errori commessi e i test verificati.

- **[Fase 1 — Fondamentali & Setup](docs/fase-1-fondamentali.md)**
  Perché due DTO separati e non uno, perché l'entity è mutabile e i DTO immutabili, `@Component` vs `@Bean`, perché i POST rispondevano 401.

- **[Fase 2 — Database & Relationships](docs/fase-2-database.md)**
  Quando una tabella separata batte una colonna stringa, dove sta la foreign key, `JOIN` vs `JOIN FETCH`, l'N+1 osservato nei log e le due volte in cui si è nascosto, `Pageable` vs `Page`, self-reference per le sottocategorie, l'allowlist di sorting.

- **[Fase 3 — Validation & Error Handling](docs/fase-3-validation-errori.md)**
  I tre pezzi di Bean Validation, `PUT` vs `PATCH`, `@Transactional` che porta un update da 4 query a 2, il criterio per estrarre codice duplicato, una classe di eccezione per status HTTP, perché il messaggio di un errore imprevisto non va al client.

- **[Fase 4 — Security & Autenticazione](docs/fase-4-security.md)**
  Ruoli come `enum` e non come tabella, quali endpoint restano pubblici e cosa comporta per i query param, perché il refresh token va conservato in DB, perché le eccezioni di Spring Security non arrivano al `@RestControllerAdvice`, e `POST /categories` rimandato qui per scriverlo con le regole di ruolo già disponibili.

- **[Fase 5 — Miglioramenti & Polish](docs/fase-5-polish.md)** *(pianificata)*
  Cosa confluisce qui dalle fasi precedenti: soft delete con recupero a 30 giorni e le tre insidie già identificate, gli integration test che coprono il debito lasciato aperto in 3.3, Swagger come posto giusto per pubblicare l'allowlist di sorting, Docker che sblocca Flyway.

---

## Alcune decisioni tecniche

Una selezione, per dare l'idea del tipo di ragionamento che sta nei documenti.

**`open-in-view: false` insieme a `FetchType.LAZY`.** Con l'impostazione di default la sessione Hibernate resta aperta durante la serializzazione JSON, quindi un accesso lazy inatteso non esplode: parte una query in silenzio, fuori dal service, dove non la vedi. Con `false` diventa un'eccezione rumorosa — che è ciò che si vuole, perché dice dove il fetching non è stato deciso in modo esplicito.

**Il fetching è una decisione del caso d'uso, non del mapping.** Da cui `JOIN FETCH` sulla singola query invece di `FetchType.EAGER` sull'entity: `GET /posts` vuole le categorie, un "conta i post" no.

**Una classe di eccezione per status HTTP, non per tipo di errore.** "Post inesistente" e "categoria inesistente" sono lo stesso fatto ma due risposte diverse (404 e 400), e l'handler discrimina sul tipo: accorparle per natura avrebbe cancellato l'informazione necessaria a scegliere.

**I query param sono un canale che il DTO non copre.** `?sort=` raggiunge l'entity direttamente, quindi un campo escluso dalla risposta restava comunque ordinabile — cioè parte del contratto pubblico senza essere esposto. Da cui l'allowlist esplicita: i campi ordinabili devono coincidere con i campi visibili. Ora che le `GET` sono pubbliche, quell'allowlist è l'unica difesa su quel canale, e lo è per utenti anonimi.

**Il ruolo è un `enum`, la categoria è una tabella.** Insieme chiuso e piccolo (`USER`, `ADMIN`), verificato dal compilatore, nessuna tabella in più. È il caso opposto a quello della categoria, che aveva identità e attributi propri: stesso ragionamento, esito rovesciato dal contesto.

**Il refresh token esiste perché un JWT non si può revocare.** Il server non tiene traccia di quali ha emesso, quindi un token rubato resta valido fino alla scadenza e un logout non ha niente da cancellare. Access token corto più refresh a vita lunga scioglie la tensione — ma solo se il refresh sta **in DB**, altrimenti è irrevocabile quanto l'altro e non si è guadagnato niente.

**Le eccezioni di Spring Security nascono prima del `DispatcherServlet`.** Vivono nella filter chain, e il `@ControllerAdvice` viene interrogato solo dal servlet: senza un `AuthenticationEntryPoint` e un `AccessDeniedHandler` dedicati, l'API tornerebbe ad avere due formati di errore subito dopo averli unificati.

**Frontend e backend validano per scopi diversi, non per ridondanza.** Il primo fa UX (feedback immediato, campo per campo, senza rete), il secondo correttezza (blinda il dato per qualunque client).

---

## Setup

**Database**

```sql
CREATE DATABASE blog;
```

**Variabili d'ambiente**

| variabile | obbligatoria | note |
|---|---|---|
| `JWT_SECRET` | sì | nessun default: senza, l'applicazione non parte |
| `DB_PASSWORD` | no | default `postgres` |

**`application.yaml`**

```yaml
spring:
  application:
    name: blog
  datasource:
    url: jdbc:postgresql://localhost:5433/blog
    username: postgres
    password: ${DB_PASSWORD:postgres}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    com.simone.blog: DEBUG

jwt:
  secret: ${JWT_SECRET}
  expiration: 7200000

debug: true
```

Note: porta **5433** (la 5432 è occupata da un'altra installazione locale). Nessun `context-path`, quindi gli endpoint stanno su `/posts`, non `/api/posts`. `debug: true` serve a leggere il CONDITIONS EVALUATION REPORT all'avvio, ma **non** abbassa il livello dei logger applicativi — da cui il blocco `logging.level` separato. Le credenziali fisse di Basic auth (`spring.security.user`) sono sparite con l'arrivo di JWT.

**Avvio**

```bash
./mvnw spring-boot:run
```

---

## Struttura

```
src/main/java/com/simone/blog/
├── entity/          Post, Category, User, Role, RefreshToken
├── repository/      PostRepository, CategoryRepository, UserRepository, RefreshTokenRepository
├── service/         PostService, CategoryService, AuthService
├── controller/      PostController, CategoryController, AuthController
├── dto/             PostDTO, CreatePostDTO, UpdatePostDTO, CategoryDTO, CategoryTreeDTO,
│                    CreateCategoryDTO, AuthorDTO, UserDTO, CreateUserDTO,
│                    LoginRequestDTO, LoginResponseDTO, ApiError
├── mapper/          PostMapper, CategoryMapper, UserMapper
├── security/        JwtTokenProvider, JwtAuthenticationFilter, JwtPrincipal,
│                    JwtAuthenticationEntryPoint, JwtAccessDeniedHandler,
│                    PostAuthor, PostSecurity
├── exception/       ResourceNotFoundException, BadRequestException, UnauthorizedException,
│                    AuthenticatedUserNotFoundException, GlobalExceptionHandler
├── config/          SecurityConfig, PaginationConfig
├── validation/      SortValidator
└── BlogApplication.java
```

Organizzato **per layer**. L'alternativa — package by feature — raggruppa per cosa una classe *parla* invece che per cosa *è*, e scala meglio oltre una certa dimensione; su questo numero di classi la differenza è teorica.

---

## Metodo

Sviluppo incrementale, una fase alla volta, con tre regole:

1. **Ogni decisione va motivata prima di essere scritta**, e le alternative scartate restano documentate — servono a capire perché la scelta fatta è quella giusta *in questo contesto*, e a riconoscere quando il contesto cambia.
2. **Ogni fase si verifica prima di passare oltre**: test su Postman, e conteggio delle query nei log SQL quando è in gioco una decisione di performance.
3. **I debiti tecnici si registrano appena si aprono**, con la condizione che li renderà risolvibili. Diversi sono stati chiusi fasi dopo, altri sono ancora aperti di proposito.

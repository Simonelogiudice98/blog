# Blog Backend — Spring Boot

REST API per un blog, costruita con **Spring Boot 4**, **Java 17** e **PostgreSQL**.

È un progetto di apprendimento sviluppato per fasi, dove ogni decisione tecnica è documentata insieme alle alternative scartate e al perché. Il diario completo sta in [`docs/`](#diario-di-sviluppo); questo file è l'indice.

---

## Cosa fa

| verbo | endpoint | note |
|---|---|---|
| `GET` | `/posts` | paginato, filtrabile per categoria, ordinabile |
| `GET` | `/posts/{id}` | 404 con body strutturato se non esiste |
| `POST` | `/posts` | validazione Bean Validation + regole di dominio |
| `PUT` | `/posts/{id}` | sostituzione completa della risorsa |
| `DELETE` | `/posts/{id}` | 204, hard delete |

**Filtri e paginazione:** `?page=`, `?size=`, `?sort=campo,direzione`, `?category=slug` (accetta più valori: `?category=a,b` o parametro ripetuto).

Le categorie sono gerarchiche su due livelli: filtrare per una macro-categoria espande automaticamente la ricerca alle sottocategorie.

**Errori:** tutte le risposte di errore hanno la stessa forma.

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

Java 17 · Spring Boot 4.1 · Spring Data JPA / Hibernate 7 · PostgreSQL · Spring Validation · Lombok · Maven

Spring Security è nel classpath con una configurazione minimale (Basic auth); JWT arriva in Fase 4.

---

## Stato

| fase | stato | contenuto |
|---|---|---|
| **1 — Fondamentali** | ✅ | Entity, repository, service, controller, DTO, mapper |
| **2 — Database & relazioni** | ✅ | `@ManyToOne`, query custom, N+1, paginazione, sottocategorie, filtri, sorting |
| **3 — Validation & errori** | ✅ | Bean Validation, CRUD completo, transazioni, error handling centralizzato, logging |
| **4 — Security** | ⬜ | JWT, ruoli, autore dei post |
| **5 — Polish** | ⬜ | Soft delete, test, Swagger, Docker, CI |

Due voci sono rimandate deliberatamente, con la condizione di sblocco scritta: **Specification/Querydsl** (aspetta che il modello abbia abbastanza criteri filtrabili da rendere la combinatoria un problema vero) e **Flyway** (aspetta Docker, dove un Postgres vuoto rende le migrazioni il flusso normale invece del caso particolare della baseline).

---

## Diario di sviluppo

Ogni file contiene le decisioni prese, le alternative scartate, gli errori commessi e i test verificati.

- **[Fase 1 — Fondamentali & Setup](docs/fase-1-fondamentali.md)**
  Perché due DTO separati e non uno, perché l'entity è mutabile e i DTO immutabili, `@Component` vs `@Bean`, perché i POST rispondevano 401.

- **[Fase 2 — Database & Relationships](docs/fase-2-database.md)**
  Quando una tabella separata batte una colonna stringa, dove sta la foreign key, `JOIN` vs `JOIN FETCH`, l'N+1 osservato nei log e le due volte in cui si è nascosto, `Pageable` vs `Page`, self-reference per le sottocategorie, l'allowlist di sorting.

- **[Fase 3 — Validation & Error Handling](docs/fase-3-validation-errori.md)**
  I tre pezzi di Bean Validation, `PUT` vs `PATCH`, `@Transactional` che porta un update da 4 query a 2, il criterio per estrarre codice duplicato, una classe di eccezione per status HTTP, perché il messaggio di un errore imprevisto non va al client.

- **[Fase 4 — Security & Autenticazione](docs/fase-4-security.md)** *(pianificata)*
  Piano e decisioni già prese: ruoli come enum, endpoint pubblici, refresh token.

- **[Fase 5 — Miglioramenti & Polish](docs/fase-5-polish.md)** *(pianificata)*

---

## Alcune decisioni tecniche

Una selezione, per dare l'idea del tipo di ragionamento che sta nei documenti.

**`open-in-view: false` insieme a `FetchType.LAZY`.** Con l'impostazione di default la sessione Hibernate resta aperta durante la serializzazione JSON, quindi un accesso lazy inatteso non esplode: parte una query in silenzio, fuori dal service, dove non la vedi. Con `false` diventa un'eccezione rumorosa — che è ciò che si vuole, perché dice dove il fetching non è stato deciso in modo esplicito.

**Il fetching è una decisione del caso d'uso, non del mapping.** Da cui `JOIN FETCH` sulla singola query invece di `FetchType.EAGER` sull'entity: `GET /posts` vuole le categorie, un "conta i post" no.

**Una classe di eccezione per status HTTP, non per tipo di errore.** "Post inesistente" e "categoria inesistente" sono lo stesso fatto ma due risposte diverse (404 e 400), e l'handler discrimina sul tipo: accorparle per natura avrebbe cancellato l'informazione necessaria a scegliere.

**I query param sono un canale che il DTO non copre.** `?sort=` raggiunge l'entity direttamente, quindi un campo escluso dalla risposta restava comunque ordinabile — cioè parte del contratto pubblico senza essere esposto. Da cui l'allowlist esplicita: i campi ordinabili devono coincidere con i campi visibili.

**Frontend e backend validano per scopi diversi, non per ridondanza.** Il primo fa UX (feedback immediato, campo per campo, senza rete), il secondo correttezza (blinda il dato per qualunque client).

---

## Setup

**Database**

```sql
CREATE DATABASE blog;
```

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
  security:
    user:
      name: user
      password: password

logging:
  level:
    com.simone.blog: DEBUG

debug: true
```

Note: porta **5433** (la 5432 è occupata da un'altra installazione locale). Nessun `context-path`, quindi gli endpoint stanno su `/posts`, non `/api/posts`. `debug: true` serve a leggere il CONDITIONS EVALUATION REPORT all'avvio, ma **non** abbassa il livello dei logger applicativi — da cui il blocco `logging.level` separato.

**Avvio**

```bash
./mvnw spring-boot:run
```

---

## Struttura

```
src/main/java/com/simone/blog/
├── entity/          Post, Category
├── repository/      PostRepository, CategoryRepository
├── service/         PostService
├── controller/      PostController
├── dto/             PostDTO, CreatePostDTO, UpdatePostDTO, CategoryDTO, ApiError
├── mapper/          PostMapper, CategoryMapper
├── exception/       ResourceNotFoundException, BadRequestException, GlobalExceptionHandler
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

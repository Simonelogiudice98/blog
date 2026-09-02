# Fase 1 — Fondamentali & Setup

> Obiettivo: capire DI, REST, annotazioni, configurazione Spring Boot.
> Parte del [diario di sviluppo](../README.md) del blog backend.

- [x] Progetto Maven con dipendenze Spring Boot
- [x] **1.1** Configurazione PostgreSQL (application.yaml) — DB locale `blog` su porta **5433** (non 5432, occupata da altro)
- [x] **1.2** Entity `Post` (id, title, content, imageUrl, createdAt, updatedAt) — completata con Lombok (`@Data`, `@NoArgsConstructor`), timestamp automatici via `@PrePersist`/`@PreUpdate`. Tabella `post` verificata su pgAdmin.
- [x] **1.3** Repository `PostRepository` (Spring Data JPA) — interfaccia che estende `JpaRepository<Post, Long>`, nessuna implementazione scritta a mano: Spring genera un dynamic proxy a runtime che implementa il contratto (CRUD completo) traducendo le chiamate in query SQL via Hibernate. Testata con un `CommandLineRunner` temporaneo in `BlogApplication` (poi rimosso): salvataggio di un `Post` di prova confermato in console (`id` generato, `createdAt` popolato, `updatedAt=null` come da design) e verificato via query SQL nei log.
- [x] **1.4** Service `PostService` (logica di business) — layer separato dal repository, `PostRepository` iniettato via **constructor injection** (campo `final`, niente `@Autowired`). Metodi iniziali: `getPosts()`, `getPostById(Long id)`, `createPost(Post post)`. Firme poi riviste in 1.6 per parlare DTO.
- [x] **1.5** Controller `PostController` (GET /posts, GET /posts/{id}, POST /posts) — `@RestController` + `@RequestMapping("/posts")` sulla classe; i metodi sono di una riga sola, delegano al service senza logica propria.
- [x] **1.6** DTO `PostDTO` e `CreatePostDTO` (request/response) + `PostMapper`
- [x] **1.7** Primo test API con Postman — tutti e tre gli endpoint verificati (vedi sezione dedicata più sotto)

**Concetti:**
- Dependency Injection con `@Autowired`, `@Component`, `@Service`, `@Repository`
- Entity mapping con JPA
- CRUD basic con Spring Data
- REST endpoint basics

**Note tecniche reali del progetto (diverse dal template originale):**
- Stack reale: **Spring Boot 4.1.0** (non 3.x), Java 17, Hibernate 7.4.1
- Package root: `com.simone.blog`
- Database si chiama **`blog`** (non `blog_db`), porta **5433** (non 5432)
- Config in `application.yaml` (YAML, non `.properties`), password gestita con `${DB_PASSWORD:postgres}`
- `pom.xml` originale aveva starter inesistenti (`spring-boot-starter-webmvc`, vari `-test` starter falsi) — corretti in `spring-boot-starter-web` + `spring-boot-starter-test` + `spring-security-test`
- `spring-boot-starter-security` è già nel classpath → login Basic auto-generato attivo su tutti gli endpoint. **`SecurityConfig` minimale anticipato dalla Fase 4** per poter testare i POST (vedi sotto)

**Entity Post — decisioni di design prese:**
- `id`: `Long` con `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- `title`: `VARCHAR(255)`, `nullable = false`
- `content`: `TEXT`, `nullable = false`
- `imageUrl`: `VARCHAR(255)`, nullable (un post può non avere copertina)
- `createdAt`/`updatedAt`: `LocalDateTime`, popolati automaticamente via `@PrePersist`/`@PreUpdate`; `updatedAt` resta `null` finché il post non viene mai modificato
- Lombok: `@Data` + `@NoArgsConstructor` (il costruttore vuoto è obbligatorio per Hibernate)

**DTO e Mapper — decisioni di design prese (Fase 1.6):**

Due DTO separati perché sono **due contratti diversi in due direzioni diverse**. Una sola classe non funziona: se ci metti `id` (serve in risposta) diventa scrivibile in ingresso, e il buco si riapre.

| campo | `CreatePostDTO` | `PostDTO` | chi decide |
|---|---|---|---|
| `id` | ✗ | ✓ | DB (`IDENTITY`) |
| `title` | ✓ | ✓ | client |
| `content` | ✓ | ✓ | client |
| `imageUrl` | ✓ | ✓ | client |
| `createdAt` | ✗ | ✓ | server (`@PrePersist`) |
| `updatedAt` | ✗ | ✗ | server (`@PreUpdate`) — escluso anche dalla risposta |

- Entrambi sono **`record`** (Java 17): immutabili per costruzione, Jackson li deserializza nativamente. L'entity `Post` invece **non può** essere un record — Hibernate ha bisogno del costruttore vuoto e dei setter per la reflection. Entity mutabile, DTO immutabile: due esigenze opposte, due strumenti diversi.
- **Perché il DTO in ingresso risolve davvero il problema**: `@GeneratedValue` non è una guardia, dice solo "se l'id manca, chiedilo al DB". Se Jackson popola `id` dal JSON, `save()` vede un'entity *detached* e fa **`UPDATE` invece di `INSERT`** — un endpoint "crea" che sovrascrive. Non esponendo il campo, Jackson non ha dove metterlo e il buco si chiude a monte, senza controlli difensivi nel service.
- **`PostMapper` in un package `mapper/` dedicato**, non in `utils/`: `utils/` è un package definito per negazione ("roba che non sapevo dove mettere") e cresce per accumulo. Il mapper è uno strato architetturale con un'identità precisa.
- `PostMapper` è un **`@Component`** con metodi d'istanza (`toEntity`, `toDto`). I metodi sarebbero funzioni pure e `static` funzionerebbe, ma il bean si presta meglio se in futuro servirà una dipendenza (i punti di chiamata non cambiano) ed è più facile da mockare nei test.
- **Il mapper vive nel service, non nel controller**: `PostService` parla DTO verso l'esterno ed entity verso il repository. Il controller resta sottile. Trade-off accettato: il service è legato al formato HTTP, ma la conversione sta in un posto solo.
- Firme finali del service: `getPosts()` → `List<PostDTO>`, `getPostById(Long)` → `PostDTO`, `createPost(CreatePostDTO)` → `PostDTO`.
- In `createPost` si mappa **il valore di ritorno di `save()`**, non l'oggetto passato: solo quello ha `id` e `createdAt` valorizzati.
- `getPosts()` usa uno stream (`.stream().map(postMapper::toDto).toList()`) invece di un `toDtoList` nel mapper: un wrapper attorno a una riga, e ne servirebbe uno nuovo per ogni collezione (es. `Page<Post>` in 2.4).

**Controller — decisioni di design prese (Fase 1.5):**

- `@RestController` (non `@Controller`): serializza il valore di ritorno direttamente nel body come JSON, invece di cercare un template da renderizzare.
- `@RequestMapping("/posts")` sulla classe come prefisso; i metodi dichiarano solo la parte che si aggiunge (`@GetMapping("/{id}")`, oppure annotazione nuda per `/posts`).
- `@PathVariable` per l'id (arriva dall'**URL**), `@RequestBody` per il DTO di creazione (arriva dal **body** JSON).
- Niente `/create` nel path: in REST la risorsa è l'URL e l'operazione è il **verbo**. `GET /posts` e `POST /posts` convivono senza conflitto — Spring distingue sul verbo. La coppia (verbo, path) deve essere unica.
- Nessun `context-path` configurato → gli URL reali sono `/posts` e `/posts/{id}`.

**SecurityConfig minimale (anticipato dalla Fase 4):**

I POST rispondevano **401** mentre le GET passavano. Causa: la **protezione CSRF** attiva di default, che non tocca i metodi sicuri (GET/HEAD/OPTIONS) ma blocca le scritture. Il codice è 401 e non 403 perché il `CsrfFilter` gira *prima* del `BasicAuthenticationFilter`: al momento del blocco la richiesta non è ancora autenticata, quindi Spring risponde "identificati" invece di "non puoi".

Soluzione in `config/SecurityConfig.java`:
- `@Configuration` + `@EnableWebSecurity`, con un metodo `@Bean` che restituisce un `SecurityFilterChain`
- `csrf.disable()` — legittimo per un'API REST stateless: il CSRF sfrutta i cookie inviati in automatico dal browser
- `anyRequest().authenticated()` + `httpBasic(...)` — tutto protetto, Basic auth per Postman
- Nota su `@Bean` vs `@Component`: `@Component` annota una **tua** classe perché Spring la istanzi; `@Bean` sta su un metodo di una classe `@Configuration` e serve a costruire un oggetto di una classe **del framework**
- Definendo un bean `SecurityFilterChain`, l'auto-configurazione si disattiva (`@ConditionalOnMissingBean`)

Credenziali fissate in `application.yaml` (`spring.security.user.name/password` = `user`/`password`) per non dover rileggere la password generata a ogni riavvio.

**Test API (Fase 1.7) — verificato su Postman:**

| verbo | URL | esito |
|---|---|---|
| `POST` | `/posts` | 200, `id` e `createdAt` valorizzati dal server, `updatedAt` assente dal JSON |
| `GET` | `/posts` | 200, lista di `PostDTO` |
| `GET` | `/posts/{id}` | 200, singolo `PostDTO` |

Catena completa confermata: JSON → `CreatePostDTO` → `toEntity` → `save` → `toDto` → JSON.

**Debiti tecnici aperti:**
- ~~`getPostById` lancia un `RuntimeException` nudo → `GET /posts/999` restituisce **500 invece di 404**~~ — **risolto in 3.3**: `ResourceNotFoundException` + `@RestControllerAdvice`, ora 404 con `ApiError`
- `SecurityConfig` è volutamente grezzo: tutto autenticato, nessuna distinzione tra lettura e scrittura, Basic auth. Da rifare in **Fase 4** con JWT e regole granulari
- ~~Nessuna validazione sui DTO: `title` e `content` possono arrivare vuoti o `null`~~ — **risolto in 3.1**: `@NotBlank`/`@Size`/`@NotNull`/`@URL` sui componenti dei record, `@Valid` come innesco nel controller

---

## Riepilogo

**Fase 1 completata.** La catena end-to-end funziona: HTTP → `PostController` → `PostService` → `PostMapper`/`PostRepository` → Hibernate → Postgres, e ritorno.

Concetti consolidati in Fase 1:
- Constructor injection applicata tre volte (`PostRepository` nel service, `PostMapper` nel service, `PostService` nel controller)
- Separazione degli strati: il controller conosce HTTP e non la logica, il service conosce la logica e non HTTP, l'entity non attraversa mai il confine
- Perché l'entity è mutabile (Hibernate) e i DTO sono immutabili (record)
- `@Component` vs `@Bean`

---

[← Indice](../README.md)

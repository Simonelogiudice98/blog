# Fase 2 — Database & Relationships

> Obiettivo: padroneggiare JPA, relazioni, query.
> Parte del [diario di sviluppo](../README.md) del blog backend.

- [x] **2.1** Entity `Category` (id, name, slug) — `@Entity` con Lombok (`@Data`, `@NoArgsConstructor`), `name` e `slug` entrambi `nullable = false, unique = true`. Tabella verificata su pgAdmin.
- [x] **2.2** Relazione `Post @ManyToOne Category` — unidirezionale, `CategoryRepository`, `CategoryDTO`, `CategoryMapper`, `CreatePostDTO.categoryId`, `PostDTO.category` annidato. Catena testata end-to-end su Postman.
- [x] **2.3** Custom queries con `@Query` — tre metodi in `PostRepository` (`findAllWithCategory`, `findByIdWithCategory`, `findByCategorySlug`), **debito N+1 chiuso** con `JOIN FETCH`, filtro per slug esposto come query param opzionale
- [x] **2.4** Paginazione `Page<Post>` in GET /posts — `Pageable` su entrambe le query con `countQuery` esplicito, `Page.map()` nel service, `PaginationConfig` con `VIA_DTO` per la serializzazione
- [x] **2.4b** Sottocategorie — self-reference `@ManyToOne` su `Category` (`parent_id` nullable), `findBySlug` + `findByParentId`, `findByCategoryIds` con `IN`, espansione della categoria nei discendenti dentro `getPosts`
- [x] **2.5** Filtering e sorting (query params: page, size, sort, category) — **completata**: selezione multipla (`?category=a,b` e param ripetuto), `findBySlugIn` + `findByParentIdIn`, deduplicazione con `Set`, `parent` eager chiuso con `FetchType.LAZY` + `open-in-view: false`. Sorting verificato sui log e ristretto con allowlist esplicita
- [ ] **2.5c** Criteri di filtro aggiuntivi con Specification/Querydsl — **rimandata deliberatamente**: con un solo criterio l'`if` nel service è leggibile e corretto, e Specification risolve una combinatoria che oggi non esiste. Da riprendere quando il modello avrà `author` (4.7) e altri campi filtrabili, così l'astrazione nasce sul problema vero invece che su uno immaginato
- [ ] **2.6** Migrazioni con Flyway — **rimandata a Docker**: introdurlo ora su un DB già popolato richiede la *baseline* (dire a Flyway di considerare lo stato attuale come già migrato), che è il caso particolare; su un container Postgres vuoto le migrazioni girano da zero in ordine, che è il flusso normale e insegna meglio il concetto centrale

**Concetti:**
- JPA relationships (@OneToMany, @ManyToOne, @JoinColumn)
- Custom JPQL queries
- Paginazione con Pageable
- Lazy vs Eager loading
- N+1 query problem

**Perché una tabella `category` e non una colonna stringa su `post` (Fase 2.1):**

L'alternativa concreta era `post.category VARCHAR`. Scartata perché con la stringa: niente impedisce che finiscano in DB "Backend", "backend" e "back-end" come tre categorie distinte; rinominare una categoria significa un UPDATE massivo su `post`; non c'è dove mettere `slug` o altri attributi senza ripeterli su ogni riga; e le categorie senza post non esistono affatto, quindi non puoi costruire il menu.

Regola pratica ricavata: **la tabella separata serve quando l'entità ha identità e attributi propri, e il valore deve restare consistente tra molte righe.** Il caso opposto — un valore senza attributi e con insieme chiuso, tipo uno `status` — si risolve con un `enum`, non con una tabella.

Confermato dai sistemi reali: WordPress usa tre tabelle (`wp_terms`, `wp_term_taxonomy`, `wp_term_relationships`); Ghost e Strapi lo stesso schema con altri nomi. L'unica differenza è la cardinalità: quasi tutti fanno **many-to-many** (un post, cinque tag) con tabella di join. `@ManyToOne` è la versione semplificata — corretta, solo più stretta.

**Entity Category — decisioni di design prese (Fase 2.1):**
- `id`: `Long` con `IDENTITY`, come `Post`
- `name`: forma **leggibile**, quella che il frontend mostra a schermo ("Spring Boot")
- `slug`: la stessa cosa in forma **URL-safe** ("spring-boot"). Non è un identificativo — l'identificativo è `id`. Serve per URL leggibili (`/blog/categoria/spring-boot`) e come query param (`GET /posts?category=spring-boot`), che è più leggibile di `?category=3` e non espone gli id interni
- `unique = true` su **entrambi**: due categorie "Backend" sono un errore anche con slug diversi. I due vincoli restano indipendenti anche se `name` unico di fatto implica `slug` unico
- Nessun timestamp: aggiunto solo se serve, non per simmetria con `Post`
- **Nessun campo `List<Post> posts`** — vedi sotto

**Relazione — decisioni di design prese (Fase 2.2):**

- La colonna sta su `post`, non su `category`: dal lato "molti" della relazione. Una singola colonna non può contenere gli id di N post.
- **Chiave = `id`, non `slug`.** Lo slug è unico e tecnicamente sarebbe una chiave valida, ma: se rinomini la categoria e lo slug cambia, con l'id **non tocchi nessuna riga di `post`**; un `BIGINT` è più economico di una stringa da indicizzare. Lo slug resta la chiave giusta *nell'URL* — ingresso e filtro possono usare chiavi diverse.
- **Due piani da non confondere**: sul DB c'è una colonna `category_id BIGINT` che contiene `3`; in Java c'è un campo `private Category category` che contiene un *oggetto*. Hibernate traduce tra i due. In `toEntity` si chiama `post.setCategory(category)` — un `Long` non compilerebbe nemmeno.
- **`@ManyToOne` è ciò che distingue i due mondi**: lo stesso `private Category category` significa "valore serializzato in colonna" se `Category` è un `enum` (con `@Enumerated`), e "riferimento a un'altra riga" se è un'entity. Non lo si deduce dal tipo del campo.
- Nome colonna per convenzione: **nome del campo + `_` + nome della PK dell'altra entity** → `category_id`. `@JoinColumn(name = ...)` è **opzionale** qui (a differenza di `image_url`, dove senza si otterrebbe `imageurl`); messo comunque per coerenza e per non dipendere da una convenzione implicita.
- **Doppio vincolo di obbligatorietà**, i due agiscono in posti diversi:
  - `@JoinColumn(nullable = false)` → **DDL**: la colonna nasce `NOT NULL`, è **Postgres** a rifiutare. Vale anche per chi bypassa l'app.
  - `@ManyToOne(optional = false)` → **runtime**: è **Hibernate** a fermarsi prima di interrogare il DB, con un errore più vicino al punto in cui hai sbagliato.
  - Stesso schema di `unique` su `slug`, e stesso schema di `@NotBlank` sui DTO in 3.1: **tre livelli, tre momenti diversi in cui l'errore viene intercettato.**
- **Relazione unidirezionale**: nessun `@OneToMany(mappedBy = "category")` in `Category`. La foreign key sta già su `post`, quindi quel campo **non creerebbe nessuna colonna** — è una scelta di *modello a oggetti*, non di schema. Il costo non è sul DB ma **in memoria a runtime**: caricare una categoria con 5000 post significa 5000 oggetti in RAM, e potenzialmente nel JSON. Si naviga sempre da `Post` verso `Category`; per l'altra direzione basterà un `findByCategoryId(Long)` sul repository, che tra l'altro dà la paginazione gratis.
- Nota su `mappedBy`: se un domani servisse il lato inverso, senza `mappedBy` Hibernate interpreterebbe i due lati come **due relazioni separate** e creerebbe una tabella di join indesiderata.

**Migrazione su dati esistenti — problema incontrato:**

Con `ddl-auto: update`, aggiungere una colonna `NOT NULL` a una tabella che ha già righe fa fallire l'`ALTER TABLE` (le righe esistenti avrebbero `category_id = NULL`) e **l'app non parte**. Non è risolvibile "aggiornando i post dopo".

Due strade: (A) colonna nullable → assegnare le categorie → stringere il vincolo; (B) cancellare i dati. Scelta la **B**, essendo dati di test. In produzione servirebbe la A, in tre passi — ed è esattamente il caso d'uso di **Flyway (2.6)**. `update` è anche pigro: su una tabella già esistente con duplicati, un `unique` aggiunto dopo non viene applicato.

**DTO e mapper — decisioni di design prese (Fase 2.2):**

- **In ingresso**: `CreatePostDTO` guadagna `Long categoryId`. Il frontend popola la tendina con `GET /categories` e ha già entrambe le chiavi; conviene l'id perché `findById` esiste già (con lo slug servirebbe un `findBySlug`) e perché non si rompe se lo slug cambia.
- **In uscita**: `PostDTO` guadagna un `CategoryDTO category` **annidato**, non due campi sciolti `categoryName` + `categorySlug`. Il `name` da solo non basta: il frontend mostra "Spring Boot" ma il link punta a `/categoria/spring-boot`, e ricalcolare lo slug lato client duplicherebbe la logica di slugificazione in due linguaggi. Il JSON rispecchia il dominio, `CategoryDTO` serve comunque per `GET /categories`, e aggiungere un campo alla categoria non tocca nessun altro DTO. Non è overkill: la soglia sarebbe un oggetto con un campo solo.
- **Mai l'entity `Category` dentro `PostDTO`**: Jackson la serializzerebbe, ma sarebbe un'entity che attraversa il confine HTTP — e con un eventuale `List<Post>` sull'altro lato si andrebbe in ricorsione infinita.
- **`CategoryMapper` separato**, non la conversione a mano dentro `PostMapper`: la stessa identica conversione servirà in `GET /categories`, e duplicarla significa doverla ricordare in due posti il giorno che la categoria guadagna un campo.
- **`CategoryMapper` iniettato in `PostMapper` via constructor injection.** È la quarta applicazione del pattern, e conferma la scelta di 1.6: `PostMapper` era `@Component` "in caso servisse una dipendenza" — quel momento è arrivato, e i punti di chiamata nel service non sono cambiati di una riga.
- **La risoluzione `categoryId` → `Category` sta nel service, non nel mapper.** Il mapper traduce forme, non va a cercare dati: dargli un repository lo renderebbe capace di I/O e di fallire, e da mockare nei test. Soprattutto, la categoria **può non esistere** — e decidere cosa fare in quel caso è una scelta di business, che è del service. Stesso schema di `getPostById` in 1.4.
- **La firma resta `createPost(CreatePostDTO)`**, un solo argomento. Mettere la `Category` nella firma sposterebbe il recupero nel **controller**, che finirebbe con un repository iniettato: logica di business nel layer HTTP. Il service è l'ultimo posto che può risolvere quell'id — sotto ci sono solo i repository.
- **`findById` e non `getReferenceById`**: `getReferenceById` non interroga il DB, restituisce un **proxy** con dentro solo l'id, e legge la riga vera al primo getter. È un'ottimizzazione sensata quando serve solo agganciare una FK, ma con un id inesistente non te ne accorgi: l'errore esplode più tardi (violazione di FK da Postgres, o `EntityNotFoundException` al primo getter), lontano dal punto in cui hai sbagliato. `findById` restituisce un `Optional` e ti costringe a **decidere**.

**Test API (Fase 2.2) — verificato su Postman:**

| verbo | URL | body | esito |
|---|---|---|---|
| `POST` | `/posts` | `categoryId: 1` | 200, `category` annidato con `id`/`name`/`slug` valorizzati |
| `POST` | `/posts` | `categoryId: 99` | 500 senza `message` — debito tecnico noto, **poi risolto in 3.3**: ora 400 con `ApiError` |
| `GET` | `/posts` | — | 200, ogni post con la sua categoria annidata |

Catena confermata: `"categoryId": 3` → `CreatePostDTO` → `findById(3)` → `Category` → `toEntity(dto, category)` → `save` → `category_id = 3` → `toDto` → `"category": {...}`.

**N+1 osservato dal vivo (log SQL della `GET /posts`):**

Due query con un solo post: `select ... from post`, poi `select ... from category where id = ?`. La seconda è **mirata a una singola riga**, non un caricamento in blocco. Con 50 post → **51 query** (1 + N, da cui il nome). La SELECT parte **dentro il mapper**, dopo che il repository ha già finito: `post.getCategory()` sembra una lettura in memoria ma è una query.

Perché è infido: se i 50 post fossero tutti nella stessa categoria le query sarebbero **2**, perché la cache di primo livello (persistence context, valida per la durata della richiesta) riusa l'oggetto già caricato. In test con pochi dati non si vede quasi mai; esplode in produzione con 500 post e 30 categorie.

Tre soluzioni, da conoscere insieme:
1. `fetch = FetchType.EAGER` sul `@ManyToOne` — **da evitare**: diventa una proprietà globale del mapping (paghi la categoria anche dove non serve) e spesso non elimina nemmeno l'N+1
2. `@Query` con `JOIN FETCH` — esplicito, il lazy resta il default e decidi caso per caso → **è la strada, in Fase 2.3**
3. `@EntityGraph` — la stessa cosa dichiarata con un'annotazione invece che con JPQL

Concetto chiave: **il fetching è una decisione del caso d'uso, non del mapping.** `GET /posts` vuole le categorie, un "conta i post" no. Per questo si esprime sulla query e non sull'entity.

**Custom queries — decisioni di design prese (Fase 2.3):**

Tre metodi aggiunti a `PostRepository`. L'interfaccia resta senza implementazione: cambia solo che la JPQL la scrivi tu invece di lasciarla dedurre.

```java
@Query("SELECT p FROM Post p JOIN FETCH p.category c")
List<Post> findAllWithCategory();

@Query("SELECT p FROM Post p JOIN FETCH p.category c WHERE p.id = :id")
Optional<Post> findByIdWithCategory(@Param("id") Long id);

@Query("SELECT p FROM Post p JOIN FETCH p.category c WHERE c.slug = :slug")
List<Post> findByCategorySlug(@Param("slug") String slug);
```

- **I due meccanismi per aggiungere una query a un repository**: le *derived query* (Spring fa il **parsing del nome del metodo** — `findByCategoryId` → `category`, poi `id` — e costruisce la JPQL da sola) e `@Query` (JPQL scritta a mano, il nome del metodo torna libero). Il primo esprime **cosa filtrare**, mai **cosa caricare**: non esiste nessuna parola che significhi `JOIN FETCH`. Da qui la scelta.
- **`JOIN` e `FETCH` fanno due lavori distinti**: la `JOIN` è quella SQL, unisce le tabelle e porta le colonne fino a Java; `FETCH` dice a Hibernate di **usarle per popolare il campo** invece di scartarle. Senza `FETCH` la `JOIN` viene eseguita, le colonne arrivano e vengono buttate, il campo resta un proxy → **l'N+1 è ancora tutto lì**. In una riga: *`JOIN` porta i dati fino a Java, `FETCH` li mette dentro l'oggetto.*
- **Non chiamarlo `findAll()`**: stessa firma di quello ereditato da `JpaRepository` = **override**, non overload (l'overload richiede parametri diversi). L'implementazione ereditata diventa irraggiungibile e *ogni* chiamata porta con sé la categoria — di nuovo una proprietà globale, lo stesso difetto di `FetchType.EAGER` con un'altra sintassi. Il nome esplicito tiene entrambe le strade disponibili e dice al punto di chiamata cosa sta caricando.
- **Convenzione sui nomi**: nel repository `findBy...` (vocabolario di persistenza), nel service `getPosts`/`getPostById` (vocabolario di dominio). `get` non fa parte del vocabolario di Spring Data (`find`, `count`, `exists`, `delete`): con `@Query` funzionerebbe comunque, ma resta fuori convenzione.
- **Una sola `JOIN FETCH` filtra e carica insieme**: l'alias `c` è usabile anche nella `WHERE`. Non servono due join.
- **Parametri nominati** (`:slug` + `@Param`): il valore viaggia separato dalla query, come il `?` che si vede nei log. Concatenarlo nella stringa sarebbe SQL injection.
- **La query è validata all'avvio**, non alla prima chiamata: un nome di entity o di campo sbagliato impedisce all'applicazione di partire.

**Il filtro per categoria (endpoint):**
- `GET /posts?category=<slug>`, **stesso endpoint** di `GET /posts` con un query param **opzionale** — non un path nuovo. Un solo metodo nel service, due query nel repository.
- `@RequestParam(name = "category", required = false) String slug`: **nome esterno ≠ nome interno**. L'URL espone `category` (nome di dominio, quello che il frontend capisce), il codice continua a chiamarlo `slug` (quello che è davvero). Senza `required = false` una `GET /posts` senza param prenderebbe **400**.
- Nel service il parametro **non è opzionale** — Java non ha il concetto: "assente" è `null`, e il ramo è `slug != null`. L'opzionalità è un fatto HTTP, vive solo nel controller.
- Struttura del metodo: `List<Post> posts;` dichiarata senza inizializzare, l'`if` assegna in entrambi i rami, **il mapping resta uno solo** dopo. Duplicare lo stream nei due rami sarebbe la stessa riga scritta due volte. (Niente `new List<>()`: `List` è un'interfaccia, non si istanzia.)
- **Nota per la 2.5**: con un filtro solo l'`if` regge. Aggiungendo sorting e altri criteri gli `if` esplodono combinatoriamente — è lì che entrano **Specification** o **Querydsl**, che costruiscono la query dinamicamente. Sovradimensionati adesso.

**`getPostById` — perché sistemarlo comunque:**

Con un post solo il costo è 1 + 1 = **2 query, costante**: non è N+1 in senso proprio, l'N non cresce con le righe. L'argomento contro era YAGNI (nessun problema misurato su tre post). Sistemato lo stesso perché il costo reale era **due righe** — `Optional` funziona senza attriti come tipo di ritorno di `@Query` — e perché `GET /posts/{id}` è tipicamente l'endpoint più chiamato di tutti.

**Verifica sui log SQL (il punto di tutta la fase):**

| chiamata | prima | dopo |
|---|---|---|
| `GET /posts` (3 post, 3 categorie) | 4 query | **1** |
| `GET /posts/{id}` | 2 query | **1** |

La query nuova, letta nei log:

```sql
select p1_0.id, p1_0.category_id,
       c1_0.id, c1_0.name, c1_0.slug,     -- queste tre prima non c'erano
       p1_0.content, p1_0.created_at, ...
from post p1_0
join category c1_0 on c1_0.id = p1_0.category_id
```

Le colonne `c1_0.*` nella `SELECT` **sono** il `FETCH` che agisce. Dopo la query non parte più nessun `select ... from category where id=?`: quando il mapper chiama `getCategory()` l'oggetto è già in memoria.

Da notare: la condizione `on c1_0.id = p1_0.category_id` l'ha scritta **Hibernate**. Nella JPQL c'è solo `p.category`, cioè il *campo Java*; colonna e condizione di join le ricava da `@ManyToOne` + `@JoinColumn`. È il "doppio piano" della 2.2 visto in azione.

**Inciampo utile**: al primo test i log mostravano ancora 4 query. La `@Query` era scritta, valida e registrata all'avvio — ma `getPosts()` nel service chiamava ancora `findAll()`. Il repository **offre** un contratto, è il service a decidere quale usare: una query nuova non ha nessun effetto finché nessuno la chiama.

**Paginazione — decisioni di design prese (Fase 2.4):**

`GET /posts` restituiva *tutti* i post. Con tre va bene, con cinquemila si spediscono megabyte di JSON che nessuno legge. Ora il client chiede una fetta: `GET /posts?page=0&size=20`.

- **`Pageable` è la domanda, `Page` è la risposta.** `Pageable` impacchetta numero di pagina, dimensione ed eventuale ordinamento, ed entra nel metodo; `Page` esce e contiene gli elementi **più i metadati**. Due tipi diversi, due direzioni, da non confondere.
- **Perché non basta `List<Post>`**: il frontend deve sapere se disegnare il bottone "pagina successiva", quindi gli serve il totale. Una lista è solo una sequenza di elementi, non ha dove mettere "in totale sono 137". Da qui il contenitore.
- **La `@Query` non cambia di una riga.** `LIMIT`/`OFFSET` li aggiunge Spring a partire dal `Pageable`; nei log si legge come `fetch first ? rows only` (sintassi SQL standard). Attenzione all'omonimia: **quel `fetch` non è il `FETCH` di `JOIN FETCH`** — uno dice *quante righe*, l'altro dice *riempi l'oggetto*.
- **`countQuery` esplicito, obbligatorio qui.** Per costruire un `Page` Spring deve sapere il totale, quindi esegue una seconda query di conteggio. Se la ricava da sola dalla principale, ma con una `JOIN FETCH` dentro non ci riesce (un `COUNT` con un fetch non ha senso in JPQL). Va scritta a mano, con due modifiche rispetto alla principale: `SELECT COUNT(p)` invece di `SELECT p`, e `JOIN` senza `FETCH` — non si sta caricando niente. Usando due attributi, la query principale va nominata con `value =`.
- **Il `COUNT` deve filtrare come la principale.** Su `findByCategorySlug` la `WHERE c.slug = :slug` resta identica: contare tutti i post direbbe "137" mentre quelli della categoria sono 8, e il frontend disegnerebbe sette pagine vuote. È il secondo caso d'uso della `JOIN` **senza** `FETCH`: serve solo a raggiungere `c.slug`.
- **`Page.map()` al posto dello stream.** `.stream().map(...).toList()` produrrebbe una `List<PostDTO>`, buttando via i metadati appena recuperati. `Page.map()` converte gli elementi e **restituisce un `Page` con i metadati intatti**: `return posts.map(postMapper::toDto);`.
- **Anche il filtro è paginato**: un filtro può restituire centinaia di righe quanto la lista completa, non c'è motivo di trattarlo diversamente. E con tipi di ritorno diversi nei due rami il metodo non compilerebbe comunque.
- **`Pageable` nel controller non ha bisogno di annotazione**: basta dichiararlo come parametro e Spring lo costruisce leggendo `?page=`, `?size=`, `?sort=`. Se mancano, default a pagina 0 e 20 elementi. Il resolver arriva da `DataWebAutoConfiguration` (visibile nel CONDITIONS EVALUATION REPORT).
- **Il `COUNT` viene saltato** quando i risultati stanno tutti in una pagina: con 3 post e `size=20` Spring sa già il totale. Da tenere presente quando si contano le query nei log — normalmente la paginazione ne costa una in più.

**Serializzazione: `PagedModel` invece di `PageImpl`.**

Serializzato così com'è, `Page` produce una quindicina di campi con un oggetto `pageable` annidato che **ripete** `pageNumber`/`pageSize`/`sort` già presenti al livello superiore. Il problema non è la verbosità: quella è la **struttura interna di una classe del framework**, e Spring stesso avvisa a runtime che non c'è garanzia di stabilità tra le versioni. È lo stesso argomento per cui l'entity non attraversa il confine HTTP — con l'aggravante che la classe non è nemmeno nostra.

Due strade: `PagedModel` via configurazione, o un `PagedResponse<T>` scritto a mano. Scelta la **prima** — senza un requisito che Spring non copre, la classe in più sarebbe solo codice da mantenere, e il passaggio alla seconda resta possibile senza buttare niente.

```java
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class PaginationConfig { }
```

- **Classe dedicata, non dentro `SecurityConfig`**: funzionerebbe (`@Configuration` è solo un contenitore), ma "sicurezza" e "serializzazione delle pagine" non sono lo stesso tema, e chi cerca questa config tra sei mesi non guarderà lì. Stesso motivo per cui non sta su `BlogApplication`.
- **Corpo vuoto**: `@EnableSpringDataWebSupport` è un'annotazione di *abilitazione* — dietro c'è un `@Import` che registra i bean necessari. Non si scrive niente perché quei bean li definisce Spring. Differenza con `SecurityConfig`, che ha un metodo `@Bean` perché lì il default andava **personalizzato**.
- `VIA_DTO` è un valore di enum (`PageSerializationMode`), non una stringa: insieme chiuso, verificato dal compilatore. Stesso schema di `GenerationType.IDENTITY`.

Risultato: `content` più un oggetto `page` con quattro campi (`size`, `number`, `totalElements`, `totalPages`).

**Debiti tecnici aperti (Fase 2):**
- ~~**N+1 su `GET /posts`**~~ — **risolto in 2.3** con `JOIN FETCH` su `findAllWithCategory` e `findByIdWithCategory`
- **`countQuery` con filtro attivo, ancora non forzato**: `findByCategorySlug` non è più chiamato da nessuno (sostituito da `findByCategoryIds` in 2.4b), e anche sul nuovo metodo il `COUNT` viene saltato perché i risultati stanno in una pagina sola. Da provare con `?category=sviluppo&size=1`, che costringe Spring a eseguirlo
- ~~**`?category=<slug inesistente>` restituisce `[]` con 200**~~ — **risolto in 2.4b**: `findBySlug` + `orElseThrow` distinguono lo slug inesistente. Serve ancora l'eccezione giusta (oggi 500), vedi sotto. Resta valido il ragionamento sul codice da usare, **400**: Perché 400 e non 404: la 404 dice "la risorsa a questo URL non esiste", ma `/posts` esiste eccome — è il **valore del filtro** a essere invalido, che è un errore nella richiesta. Stessa natura del `categoryId: 99` nel body della POST, quindi stessa risposta: due 400 coerenti tra loro. In più il 400 si porta dietro un `ApiError` con il messaggio esatto (3.4), mentre un 404 finisce nell'handler globale del frontend e il dettaglio si perde. Da tenere separato il caso "categoria esistente senza post", che resta **200 `[]`** senza discussione: la collezione esiste, il filtro semplicemente non seleziona niente
- ~~**Sei `RuntimeException` nudi**~~ — **tutti chiusi in 3.3**. La tabella resta come mappa di dove nascono gli errori e con che status escono:

| dove | causa | classe | status |
|---|---|---|---|
| `getPostById` | post inesistente | `ResourceNotFoundException` | **404** |
| `updatePost` / `deletePost` | post inesistente | `ResourceNotFoundException` | **404** |
| `resolveAssignableCategory` | `categoryId` inesistente | `BadRequestException` | **400** |
| `resolveAssignableCategory` | categoria è una macro (3.2) | `BadRequestException` | **400** |
| `getPosts` | nessuno slug di filtro matcha | `BadRequestException` | **400** |
| `SortValidator` | campo di sort non ammesso (2.5b) | `BadRequestException` | **400** |

I quattro 400 sono coerenti tra loro: in tutti i casi l'errore è nella richiesta del client, non nella risorsa richiesta. Ed è proprio questa tabella ad aver dettato il criterio di raggruppamento in 3.3 — **una classe per status**, non per causa.
- ~~**`existsByParentId(Long)`** ancora da scrivere, insieme alla validazione "solo le foglie sono assegnabili"~~ — **chiuso in 3.2**, in `createPost` *e* in `updatePost`, estratto in `resolveAssignableCategory`
- ~~**Query in più sul `parent` eager**~~ — **risolto in 2.5** con `FetchType.LAZY` + `open-in-view: false`, vedi sotto. **Nota importante: la classificazione originale era sbagliata.** Qui era scritto "è una query costante, non un N+1", conclusione tratta osservando `?category=react` (4 query) e `?category=sviluppo` (3). Con più post in pagina i log hanno mostrato il contrario: su `GET /posts` partiva **una query per ogni categoria distinta con genitore**, quindi 1 + N. Era un N+1 vero, letto male perché i dati di test avevano una categoria sola per pagina
- Nessun endpoint per creare categorie: si inseriscono a mano da pgAdmin (`INSERT INTO category (name, slug) VALUES (...)`)

**Sottocategorie — decisioni di design prese (Fase 2.4b):**

Scelta di farle: oltre al valore didattico, il progetto è anche portfolio, e una gerarchia con espansione dei discendenti dice più di tre categorie piatte.

Requisiti raccolti (modello di prodotto):
- **Due livelli** (macro → micro). Nota: lo **schema è identico** a profondità arbitraria — `parent_id` regge sia 2 livelli sia 20. Cambia solo *come si espande* una categoria nei discendenti, cioè una query. Scegliere due adesso **non chiude nessuna porta**: con due livelli i figli sono `WHERE parent_id = :id`, una query normale, niente `WITH RECURSIVE`.
- **Cliccare una macro mostra anche i post dei figli.** Necessario, dato il punto sotto: le macro non hanno post propri, quindi senza espansione non mostrerebbero nulla.
- **Solo le foglie sono assegnabili.** Le macro sono etichette di raggruppamento, non contenitori di post. Questo **semplifica** il filtro: espandere "Sviluppo" nei figli basta, non serve includere anche sé stessa.
- **Selezione multipla** (checkbox in sidebar): `?category=sviluppo,frontend` → `WHERE c.slug IN :slugs`. È il cambiamento più profondo della gerarchia in sé, e tocca direttamente la 2.5.
- Viste previste a frontend: home con tutti i post, vista a card delle sottocategorie di una macro, sidebar con la gerarchia e checkbox.

**Il modello: `parent_id` e nient'altro.**

```java
@ManyToOne
@JoinColumn(name = "parent_id")
private Category parent;
```

- **Self-reference**: la colonna sta sulla riga del **figlio**, per la stessa regola di 2.2 — il lato "molti" della relazione. Una categoria ha un solo genitore, un genitore ha molti figli. La differenza è solo che la FK punta alla propria tabella; per Postgres è una foreign key normale (nei log delle constraint: `fk2y94...`, nome generato con hash — nominabile a mano con `@ForeignKey(name = ...)` se un giorno serve leggibilità nei messaggi d'errore).
- **Il campo è `Category parent`, non `Long parentId`**: di nuovo i due piani della 2.2. In Java tieni un *riferimento a un oggetto*, sul DB c'è una colonna `parent_id BIGINT`. Hibernate traduce.
- **Nullable, obbligatoriamente**: le macro-categorie non hanno genitore. Da cui il fatto più utile del modello — **`parent_id IS NULL` *è* la definizione di macro-categoria**. Nessun campo `isRoot` o `livello`: sarebbe ridondante e potrebbe andare fuori sync con la struttura reale.
- **Migrazione indolore, al contrario di 2.2**: `ddl-auto: update` è riuscito ad aggiungere la colonna a una tabella con righe dentro proprio perché è nullable (le righe esistenti prendono `NULL` senza conflitto). Stesso comando, esito opposto — dipende solo dal vincolo.
- **Lo schema non codifica "due livelli"**: `parent_id` regge 2 come 20. Il limite vive nelle query e nei requisiti, non nella tabella.

**Una query o due? Scelte due.**

Da `?category=sviluppo` ai post servono: slug → categoria → suoi figli → post di quei figli. Si può fare in una sola JPQL (`WHERE p.category.parent.slug = :slug`, due join generate da Hibernate) oppure in passi separati.

Scelte **due query** (tre, in realtà), perché:
- La versione monolitica dà **zero risultati ambigui**: non distingue slug inesistente, foglia senza figli, e categoria con figli ma senza post. Avere l'oggetto `Category` in mano dopo il primo passo permette invece di decidere — ed è così che il debito "slug inesistente" si è chiuso senza l'`existsBySlug` in più che era stato ipotizzato.
- `p.category.parent` **codifica la profondità nella query**: è esattamente un salto. Con tre livelli va riscritta.
- La **selezione multipla** prevista dai requisiti dovrà comunque risolvere una lista di slug in categorie, espanderle, e cercare i post dell'insieme. La versione monolitica non ci arriva.

È il ragionamento di 2.3 letto al contrario: lì una query sola vinceva perché il problema era *caricare* dati già determinati; qui il problema è *decidere* quali dati cercare, e per decidere serve l'oggetto prima.

**I tre metodi:**

```java
// CategoryRepository — derived query, niente da caricare insieme
Optional<Category> findBySlug(String slug);
List<Category> findByParentId(Long parentId);
```

```java
// PostRepository
@Query(value = "SELECT p FROM Post p JOIN FETCH p.category c WHERE c.id IN :categoryIds",
       countQuery = "SELECT COUNT(p) FROM Post p JOIN p.category c WHERE c.id IN :categoryIds")
Page<Post> findByCategoryIds(@Param("categoryIds") List<Long> categoryIds, Pageable pageable);
```

- **Derived query sui due di `CategoryRepository`**, `@Query` sul terzo. Il criterio è quello di 2.3: il nome del metodo esprime *cosa filtrare*, mai *cosa caricare*. Qui la categoria serve nuda, quindi il nome basta; sui post serve la `JOIN FETCH`, quindi serve `@Query`.
- **`findByParentId` attraversa la relazione nel nome**: `Parent` → il campo, `Id` → la sua PK. Genera `WHERE parent.id = :parentId`. L'alternativa `findByParent(Category)` produce **la stessa SQL** (Hibernate confronta comunque per chiave primaria) — cambia solo cosa devi avere in mano al punto di chiamata. Attenzione a non mescolare: `findByParent(Long)` non parte all'avvio, perché il campo è di tipo `Category`.
- **`IN` e non `WITH RECURSIVE`**: `IN` risolve "una riga tra questi valori", che è il problema qui. `WITH RECURSIVE` servirebbe per scendere una gerarchia di profondità ignota — non è questo il caso, ed è precisamente il costo che i due livelli evitano.
- **Filtro sugli `id`, non sugli slug**: lo slug fa il suo lavoro *all'ingresso* (risolve la macro dall'URL), poi si lavora con la chiave interna. È la stessa frase della 2.2 — *ingresso e filtro possono usare chiavi diverse* — applicata un livello più in là.
- **`countQuery` esplicito**, per il motivo di 2.4, con le due modifiche di sempre: `COUNT(p)` e `JOIN` senza `FETCH`. Nota che qui la join si potrebbe anche omettere (`WHERE p.category.id IN ...` non richiede di toccare `category`, l'id è già nella colonna `category_id`): mantenuta per simmetria con la principale.

**Il service: sempre sé stessa più i figli.**

```java
Category category = categoryRepository.findBySlug(slug)
        .orElseThrow(() -> new RuntimeException("Categoria non trovata: " + slug));

List<Category> children = categoryRepository.findByParentId(category.getId());

List<Long> ids = new ArrayList<>();
ids.add(category.getId());
ids.addAll(children.stream().map(Category::getId).toList());

posts = postRepository.findByCategoryIds(ids, pageable);
```

- **Bug trovato al primo test**: `?category=sviluppo` funzionava, `?category=react` restituiva `[]` con 200. Il codice cercava *solo* i figli — corretto per una macro, sbagliato per una foglia, che di figli non ne ha e i cui post sono i propri. Il requisito scritto in fase di raccolta ("espandere Sviluppo nei figli basta, non serve includere anche sé stessa") era vero **per le macro** e non copriva l'altro caso.
- **Due modi di aggiustarlo**: un `if` sulla lista dei figli vuota, oppure includere sempre la categoria stessa. Scelto il secondo. Il motivo che pesa più degli altri: la versione con l'`if` costringe a chiedere *"è una foglia?"*, e quella domanda il modello **unidirezionale non la risponde guardando l'oggetto in memoria** — l'informazione sta nelle altre righe. Inoltre "non avere figli" è uno stato temporaneo, non una proprietà: il giorno che React guadagna un figlio, quel ramo cambia comportamento da solo.
- Costo della scelta: nell'`IN` finisce anche l'id della macro, che per requisito non ha post. Una condizione in più su colonna indicizzata, irrilevante. E se il vincolo mancante lasciasse passare un post assegnato a una macro, il filtro lo **mostrerebbe** invece di nasconderlo — comportamento meno sorprendente.
- **`orElseThrow` apre la scatola**: l'`if (isEmpty())` controlla ma non converte, e dopo l'`if` la variabile è ancora `Optional<Category>` (niente `getId()`). `orElseThrow` fa entrambe le cose e restituisce una `Category`. L'argomento è una **lambda che costruisce** l'eccezione, non l'eccezione già costruita: così l'oggetto (e il suo stack trace) nasce solo nel ramo d'errore.
- **`new ArrayList<>()` obbligatorio**: `.toList()` restituisce una lista **immutabile**, `add` ci esploderebbe a runtime. In Java non c'è lo spread operator (`[category.id, ...children.map(...)]` di TypeScript); l'alternativa tutta-espressione sarebbe `Stream.concat(Stream.of(...), ...)`, più verbosa.

**Test API (Fase 2.4b) — verificato su Postman:**

| URL | esito | query SQL |
|---|---|---|
| `?category=sviluppo` (macro) | 200, i post di React e TypeScript, non quelli di Design | 3 |
| `?category=react` (foglia) | 200, solo il post di React | **4** |
| `?category=design` (macro) | 200, solo il post di CSS | 3 |
| `?category=aurora` (inesistente) | 500 con `RuntimeException` — debito noto, **poi 400 in 3.3** | 1 |

La quarta query su `?category=react` è il `parent` eager, vedi debiti tecnici. Il `COUNT` non parte in nessun caso perché i risultati stanno in una pagina sola.

**Il problema ancora aperto: chi impedisce di assegnare un post a una macro-categoria?**

Il DDL non può esprimerlo — "essere una foglia" dipende da *altre righe* della stessa tabella e cambia nel tempo (oggi "Spring Boot" è foglia, domani ha un figlio). Quindi niente vincolo Postgres.

Non basta nemmeno "il frontend popola la tendina solo con le foglie": è una guardia **lato client**, e il client non è sotto il nostro controllo (Postman, curl, uno script). Stessa lezione dei DTO in 1.6 — con una differenza: lì il buco si chiudeva *a monte* non esponendo il campo, qui `categoryId` **deve** essere esposto perché il client deve poter scegliere. Quindi il valore arriva e va validato.

Il controllo va nel **service**, in `createPost`, subito dopo il `findById(categoryId)` già presente. Stesso schema di 2.2 (la categoria può non esistere → decisione di business) e di 1.4 (`Optional` dal repository, il service decide che l'assenza è un errore).

Da risolvere: con la relazione **unidirezionale** scelta in 2.2, `Category` non ha un campo `children` — quindi guardando l'oggetto in memoria non si sa se è una foglia. L'informazione sta nelle altre righe della tabella.

Aggiornamento dopo la 2.4b: `findByParentId` esisteva, quindi il controllo era tecnicamente a portata (`findByParentId(id).isEmpty()` → è una foglia). Ma era una risposta ottenuta *di rimbalzo* da una query fatta per un altro scopo, e costava una query in più su ogni `createPost`.

Aggiornamento dopo la 2.5: `findByParentId` **non esiste più**, sostituito da `findByParentIdIn`. La scelta di cancellarlo invece di commentarlo è deliberata — il codice commentato non compila, non viene validato all'avvio, non compare nelle ricerche di utilizzo e invecchia in silenzio; e Git conserva già tutto. In più si stava conservando la cosa sbagliata: il controllo in Fase 3 vorrà `existsByParentId(Long)`, che è un metodo diverso (ritorna `boolean`, non carica righe) e si scrive da zero in dieci secondi. Da aggiungere insieme alla validazione in Fase 3.

**Selezione multipla — decisioni di design prese (Fase 2.5a):**

`GET /posts?category=sviluppo,react`. È il primo pezzo della 2.5, quello previsto dai requisiti raccolti in 2.4b (checkbox in sidebar).

- **`List<String>` invece di `String` + `split(",")`.** Con `List<String>` Spring accetta **due sintassi** e le fa arrivare entrambe come lista: `?category=a,b` e `?category=a&category=b`. La seconda è la forma che producono i client HTTP standard quando serializzano un array (`URLSearchParams.append`, axios con `params: {category: [...]}`), quindi si prende gratis la compatibilità col frontend. L'argomento di fondo è però un altro: **lo split è parsing del formato HTTP**, mestiere del controller — anzi, del framework sotto. Tenerlo come `String` significherebbe portare fino al service una stringa con dentro una sintassi da interpretare, mentre il service deve ricevere dati già nella forma del dominio. Stessa linea di 2.3: *l'opzionalità è un fatto HTTP e vive nel controller*.
- **`null` e lista vuota sono due cose diverse.** `required = false` + parametro assente → `null`, mai una lista vuota: Spring non inventa un contenitore che l'utente non ha mandato (vale identico per `String` e per `List<String>`). Una lista vuota **può** arrivare, ma per un'altra strada — `?category=` con valore vuoto. Da cui la condizione `slugs != null && !slugs.isEmpty()`, che copre entrambi. (`CollectionUtils.isEmpty(slugs)` di Spring fa la stessa cosa in una chiamata; scartata solo per leggibilità.)
- **Nome esterno del param: `category`, singolare, invariato.** Il plurale era difendibile (il nome direbbe la verità sul contratto) ma cambiarlo avrebbe **rotto il contratto** esistente: ogni `?category=...` sarebbe diventato `null` in silenzio, restituendo tutti i post invece di un errore. In questo progetto il costo era zero — nessun client là fuori — ma la convenzione più diffusa nelle API reali (GitHub, Stripe) è il singolare, perché la forma ripetuta `?category=a&category=b` legge male al plurale e il nome descrive cosa contiene *un'occorrenza* del parametro. È di nuovo 2.3: nome esterno e nome interno sono due contratti indipendenti, e qui la cosa si sfrutta davvero (`category` fuori, `slugs` dentro).

**Due `In` al posto dei due metodi singoli.**

```java
// CategoryRepository — i due vecchi (findBySlug, findByParentId) cancellati
List<Category> findBySlugIn(List<String> slugs);
List<Category> findByParentIdIn(List<Long> parentIds);
```

- **`In` fa parte del vocabolario delle derived query**, come `Between`, `GreaterThan`, `Like`: nessuna `@Query` necessaria, perché qui si deve solo *filtrare* e non caricare niente insieme — il criterio di 2.3. `findByParentIdIn` si legge come si compone: `Parent` (il campo) → `Id` (la sua PK) → `In` (l'operatore).
- **Il ciclo era l'alternativa sbagliata.** Chiamare `findBySlug` una volta per slug significa N query per N checkbox: è l'N+1 in versione nuova. Con gli `In` il numero di query resta **costante** — tre, indipendentemente da quanti slug arrivano. Verificato nei log: `?category=sviluppo` e `?category=sviluppo&category=react` fanno esattamente le stesse tre query.
- **Il tipo di ritorno non è una scelta libera.** Il primo tentativo teneva `Optional<Category>` su `findBySlugIn`, per riusare l'`orElseThrow` della 2.4b. Ma il tipo di ritorno descrive **quello che la query può restituire**, non quello che è comodo avere: `findBySlug` (campo unique, una riga al massimo) → `Optional`; `findBySlugIn` (N slug → N righe) → `List`, o si prende un errore a runtime. E di conseguenza `orElseThrow` sparisce, perché è un metodo di `Optional`.
- **`isEmpty()` sulla lista non ha lo stesso difetto di `if (isEmpty())` sull'`Optional`.** In 2.4b il problema era che dopo l'`if` la variabile restava la scatola; una `List` invece è già utilizzabile, l'`if` serve solo a bloccare il caso limite. Nessun `!= null` necessario: Spring Data restituisce una lista vuota, mai `null`.

**Slug parzialmente inesistenti: fallire solo se non ne matcha nessuno.**

Con più slug, `findBySlugIn` non segnala quelli fantasma — `?category=sviluppo,aurora` restituisce semplicemente una lista da un elemento. Tre opzioni: ignorare sempre, fallire sempre, o fallire solo se la lista torna vuota. **Scelta la terza.**

- Con una selezione multipla da checkbox il filtro è un **restringimento progressivo**, non l'indirizzo di una risorsa: se tre categorie su quattro esistono, rifiutare tutto per la quarta è sproporzionato.
- Ma ignorare sempre riaprirebbe il debito chiuso in 2.4b: `?category=aurora` da solo tornerebbe a dare `200 []` invece di un errore, e un typo diventerebbe di nuovo silenzioso.
- Vantaggio pratico che serve comunque: con la lista vuota si finirebbe a passare un `IN ()` al DB, caso da evitare a prescindere.

**Il `Set` per gli id, e cosa fa davvero.**

```java
List<Long> requestedIds = categories.stream().map(Category::getId).toList();
List<Category> children = categoryRepository.findByParentIdIn(requestedIds);

Set<Long> ids = new LinkedHashSet<>();
ids.addAll(requestedIds);
ids.addAll(children.stream().map(Category::getId).toList());
```

- **Due passi distinti, non uno.** Gli id delle categorie richieste servono *prima* come argomento di `findByParentIdIn` ("dammi i figli di questi"), *poi* come parte dell'insieme finale. Il duplicato non nasce nel primo passo — `findBySlugIn` restituisce righe distinte — ma nel secondo: con `?category=sviluppo,react`, React arriva sia dalla richiesta esplicita sia dai figli di Sviluppo.
- **Il duplicato non era un bug funzionale.** `IN (1, 3, 4, 3)` non produce righe ripetute: il DB valuta "questa riga appartiene all'insieme?", vero o falso, una volta per riga. Il `Set` serve per pulizia e perché la lista finisce nei log, non a tappare una falla.
- **`LinkedHashSet` e non `HashSet`**: mantiene l'ordine di inserimento. Qui la differenza è invisibile (a Postgres l'ordine dentro un `IN` non interessa, l'ordinamento dei risultati lo decide il `Pageable`), ma costa zero e rende le query riproducibili tra un run e l'altro — confrontare log dove `IN (1,3,4)` diventa `IN (4,1,3)` è fastidioso senza motivo. Regola: `LinkedHashSet` quando il contenuto finisce sotto gli occhi di qualcuno, `HashSet` quando serve solo `contains`.
- **`Set<Long> ids` e non `LinkedHashSet<Long> ids`**: si dichiara l'interfaccia, l'implementazione concreta è un dettaglio. E `new LinkedHashSet<>()` col diamond, senza ripetere il tipo.
- **Firma del repository allargata a `Collection<Long>`**: `findByCategoryIds` deve solo iterare i valori per costruire l'`IN`, non gli serve né l'ordine di una `List` né l'unicità di un `Set`. Accetta il tipo più generale che basta, così il punto di chiamata non deve convertire. Il tipo di **ritorno** segue la regola opposta — lì si è il più specifici possibile, perché chi riceve deve sapere cosa ha in mano.
- **Sempre sé stessa più i figli**, come in 2.4b: `?category=react` (foglia, nessun figlio) darebbe `[]` se si mandassero solo i figli. Con la selezione multipla l'argomento si rafforza — `?category=sviluppo,react` mescola una macro e una foglia nella stessa richiesta, e un `if` dovrebbe girare per ogni slug separatamente.

**L'N+1 nascosto dietro il `JOIN FETCH` (il pezzo più utile della fase).**

Contando le query di un `GET /posts` con più post in pagina:

```
select ... from post join category ...              ← 1
select ... from category left join category p1_0 ...  ← 2
select ... from category left join category p1_0 ...  ← 3
```

Una query in più **per ogni categoria distinta con genitore** presente nella pagina. Con 10 sottocategorie: 1 + 10 = **11**. È l'N+1, ricomparso su un'altra relazione dopo essere stato chiuso in 2.3.

Il meccanismo: `JOIN FETCH p.category` carica la categoria dentro il post, ma la categoria appena materializzata ha un campo `parent`, e `@ManyToOne` è **EAGER di default** — quindi Hibernate deve riempirlo subito. **Il fetching deciso sulla query copre solo il primo salto della catena.** Un `JOIN FETCH` che sembra aver risolto il problema può nasconderne un altro un livello più in là.

Il debito era stato classificato come "query costante, non un N+1", conclusione tratta da `?category=react` (4 query) contro `?category=sviluppo` (3). Sbagliato: con una categoria sola per pagina l'N vale 1, e cresce solo quando i dati crescono. **Lo stesso errore di lettura che in 2.2 la cache di primo livello faceva sull'N+1 originale.**

**La cura: `LAZY` e `open-in-view: false`, insieme.**

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_id")
private Category parent;
```

```yaml
spring:
  jpa:
    open-in-view: false
```

- **Perché sono legate.** `LAZY` rende `parent` un **proxy**: la riga vera viene letta solo se qualcuno chiama `getParent()`. Il rischio è che lo chiami *dopo* la chiusura della sessione Hibernate → `LazyInitializationException`, l'errore classico quando un'entity con campi lazy finisce nel serializzatore JSON.
- **`open-in-view` (attivo di default, è il WARN all'avvio) tiene la sessione aperta fino alla serializzazione.** Quindi un accesso tardivo non esplode: parte semplicemente una query in silenzio, fuori dal service, dove non la vedi. **Maschera il problema invece di segnalarlo.** Con `false` la sessione si chiude quando finisce il service, e un accesso tardivo diventa un'eccezione rumorosa — che è quello che si vuole, perché dice esattamente dove il fetching non è stato deciso in modo esplicito.
- **Qui il rischio è zero**: `parent` non finisce mai nel JSON, perché `CategoryMapper` produce un `CategoryDTO` con `id`/`name`/`slug` e basta. Nessuno chiama `getParent()`.
- **Verifica nel CONDITIONS EVALUATION REPORT**: `JpaBaseConfiguration.JpaWebConfiguration` passa da positive a negative match (`found different value in property 'spring.jpa.open-in-view'`), e il WARN all'avvio sparisce. L'interceptor che teneva aperta la sessione non viene più registrato.
- **Dettaglio da notare nei log**: nella `SELECT` resta `c1_0.parent_id`. È la **colonna FK**, già presente nella riga di `category`, che non costa niente e serve a Hibernate per costruire il proxy — sa a quale id punta senza aver letto quella riga. È la differenza tra *conoscere il riferimento* e *avere l'oggetto*: i due piani della 2.2.

**Test API (Fase 2.5a) — verificato su Postman:**

| URL | esito | query SQL |
|---|---|---|
| `/posts` | 200, tutti i post paginati | **1** (erano 3) |
| `?category=react` (foglia) | 200, solo i post di React | **3** (erano 4) |
| `?category=sviluppo` (macro) | 200, i post dei figli | 3 |
| `?category=sviluppo,react` | 200, nessun post duplicato, `IN (?, ?, ?)` | 3 |
| `?category=sviluppo&category=react` | identica alla riga sopra | 3 |
| `?category=react,aurora` | 200, i post di React, "aurora" ignorata | 3 |
| `?category=aurora` | 500 con `RuntimeException` — debito noto, **poi 400 in 3.3** | 1 |

Il punto della tabella sono le due colonne insieme: `sviluppo,react` produce `IN (?, ?, ?)` e non quattro placeholder (il `Set` ha assorbito React, che arrivava da due strade), e il numero di query non cresce con gli slug.

**Sorting — decisioni di design prese (Fase 2.5b):**

`GET /posts?sort=createdAt,desc`. La 2.5a diceva che il sorting "arriva quasi gratis": vero per il funzionamento, falso per il contratto.

- **La `@Query` non cambia di una riga**, come per `LIMIT`/`OFFSET` in 2.4. Spring inietta l'`ORDER BY` nella JPQL a partire dal `Pageable`, e nei log si legge nella posizione giusta:

```sql
from post p1_0
join category c1_0 on c1_0.id=p1_0.category_id
order by p1_0.created_at desc
fetch first ? rows only
```

L'`ORDER BY` sta **prima** di `fetch first`, e non è cosmesi: il DB deve ordinare l'insieme completo *prima* di tagliare la fetta, altrimenti la pagina 2 non conterrebbe gli elementi 21–40 dell'ordinamento richiesto ma un taglio arbitrario riordinato.

- **`sort` parla JPQL, non SQL.** Il client manda `createdAt`, nei log compare `created_at`: c'è una traduzione in mezzo, la stessa che Hibernate fa su `imageUrl` → `image_url`. Conseguenza pratica: `?sort=created_at` (nome della colonna reale) **fallisce** con `PropertyReferenceException`, perché Spring cerca una proprietà con quel nome sull'entity e non la trova.
- **`?sort=category.name` funziona già**, e senza join aggiuntive: il `Pageable` naviga la relazione, e `category` è già in `JOIN FETCH` dalla 2.3. Ordinare per `category` nudo non significherebbe niente — serve dire *per quale campo* della categoria. Scelto `name` e non `slug`: è la forma che l'utente vede a schermo, quindi l'ordine alfabetico che si aspetta.
- **Ordinare non è filtrare.** Il filtro va nella `WHERE` e riduce le righe (137 → 8); il sort va nell'`ORDER BY` e restituisce sempre le stesse righe, disposte diversamente. Nei log di `?sort=createdAt,desc` non compare nessuna `WHERE`.

**Il buco vero: il `Pageable` scavalca il DTO.**

Senza restrizioni, `?sort=` accetta **qualsiasi** campo di `Post`. Il caso che ha deciso la fase è `?sort=updatedAt,desc`: rispondeva **200 OK**, con i post in un ordine che il client **non può spiegare guardando la risposta**, perché `updatedAt` è escluso da `PostDTO` fin dalla 1.6.

Due problemi in uno. Il client riceve un ordinamento non verificabile dal payload; e un campo deliberatamente nascosto diventa **parte del contratto pubblico senza essere esposto** — qualcuno ci costruisce sopra una funzionalità, e il giorno che quel campo si tocca si rompe qualcosa che nel DTO non compare nemmeno.

È il buco di 1.6 letto al contrario. Lì il problema era `id` in ingresso, e si chiudeva *non esponendo il campo*. Qui il campo è già nascosto dal DTO, ma i **query param sono un terzo canale** che il DTO non copre: difende il body (in entrambe le direzioni) e non l'URL.

Da cui il criterio: **l'insieme dei campi ordinabili deve coincidere con l'insieme dei campi visibili nella risposta.** E quell'insieme è già deciso — è `PostDTO`.

**L'allowlist: `SORTABLE_FIELDS` + `SortValidator`.**

```java
// PostController
private static final Set<String> SORTABLE_FIELDS = Set.of("title", "createdAt", "category.name");
```

```java
public final class SortValidator {

    private SortValidator() { }

    public static void checkValidity(Pageable pageable, Set<String> allowedFields) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedFields.contains(order.getProperty())) {
                throw new RuntimeException("Campo di sorting non valido: " + order.getProperty());
            }
        }
    }
}
```

- **Allowlist e non solo un 400 pulito.** L'alternativa era lasciare passare tutto e limitarsi a tradurre la `PropertyReferenceException` in un 400 in Fase 3.3. Copre il typo, **non** copre `updatedAt`: quello non è un errore, è un campo che esiste davvero. Obiezione considerata e valida: l'allowlist è manutenzione, e un campo nuovo dimenticato dà un 400 su qualcosa che dovrebbe funzionare. Accettata perché l'insieme è piccolo e coincide con un altro insieme già mantenuto (`PostDTO`).
- **Nel controller, non nel service.** Sembra contraddire "controller sottile", ma la regola è *niente logica di business*, non *zero righe*. Il criterio è un altro, e i tre casi già decisi lo mostrano: `categoryId: 99` e lo slug che non matcha stanno nel **service** perché richiedono il **DB** per sapere se sono validi; l'opzionalità di `?category=` sta nel **controller** perché è un fatto del **protocollo**. `?sort=pippo` è del secondo tipo — l'insieme ammesso è una costante nota a compile time, non serve interrogare niente. È validazione **di forma**, più vicina a `@NotBlank` (3.1) che a `findById().orElseThrow()`. Verificato nello stack trace: **nessuna query SQL parte** prima dell'errore.
- **`static` e non `@Component`.** In 1.6 `PostMapper` fu fatto bean "in caso servisse una dipendenza", e in 2.2 la scommessa ha pagato. Qui no: `SortValidator` riceve **tutto** dalla firma, non ha stato né collaboratori possibili. Regola generalizzabile: **bean quando l'oggetto potrebbe aver bisogno di collaboratori, `static` quando la firma è già completa.** Come contorno, `static` evita a ogni controller di iniettarlo nel costruttore per un helper senza stato.
- **`final` + costruttore privato**, convenzione standard per le classi di soli metodi statici (`Collections`, `Objects`). Nota che `final` su una classe significa "non estendibile", mentre sui campi (`private final PostRepository`) significa "non riassegnabile": stessa keyword, due contratti.
- **Package `validation/` e non `utils/`.** È il ragionamento di 1.6 applicato bene: `validation/` è definito **per affermazione** (contiene ciò che valida), `utils/` per negazione. E si riempirà davvero — 3.1 e 3.2 sono già in piano. Singolare come `mapper/`, `config/`, `service/`. Nota per la 3.2: la classe **non** implementa `org.springframework.validation.Validator` né `jakarta.validation.ConstraintValidator`, è un helper con un metodo — lì il suffisso significherà qualcos'altro.
- **Costante `private static final` in SCREAMING_SNAKE_CASE.** `static` perché la lista appartiene alla classe, non a un'istanza; `Set.of(...)` perché deve essere **immutabile** — `final` protegge il riferimento, non il contenuto, stesso doppio piano di `.toList()` in 2.4b. (La convenzione maiuscola vale per le costanti *di valore*: un `private static final Logger log` in 3.6 resterà minuscolo, perché è un collaboratore.)
- **`Set` e non `List` per l'allowlist**: `contains` in tempo costante via hashing, invece di scorrere gli elementi. Nessun ciclo annidato.
- **Fail-fast, un campo per volta.** Con `?sort=pippo&sort=pluto` l'errore parla solo del primo. Scelta consapevole per semplicità; l'alternativa (accumulare in una `List` e lanciare dopo il ciclo) è più gentile col client ed è quello che farà Bean Validation di default in 3.1.
- **`allowedFields` non finisce nel messaggio.** L'elenco dei campi ammessi è documentazione (Swagger, 5.5), non testo d'errore. Da rivalutare in 3.4 se il frontend dovrà costruire una tendina di ordinamenti.
- **Un helper condiviso, non un'annotazione custom.** Tre strade valutate: costante + controllo inline nel controller, helper con firma `(Pageable, Set<String>)`, oppure `@SortableBy({...})` con un `ConstraintValidator`. Scelta la seconda: separa la logica (una, condivisa) dai dati (la lista, per risorsa), e con un controller solo la terza sarebbe macchineria per risparmiare tre righe — oltre al fatto che il `Pageable` non passa dal validation pipeline standard, quindi il terreno è più fragile. La terza resta il candidato per la **3.2**, ma con un caso d'uso migliore: lo slug unique, che è validazione *di dominio* su DTO.

**Test API (Fase 2.5b) — verificato su Postman e sui log:**

| URL | esito | note |
|---|---|---|
| `?sort=createdAt,desc` | 200, ordine corretto | **1 query**, `order by p1_0.created_at desc` prima di `fetch first` |
| `?sort=category.name,asc` | 200, ordine corretto | nessuna join aggiuntiva, riusa la `JOIN FETCH` |
| `?sort=pippo` | 500 — debito noto, **poi 400 in 3.3** | `Campo di sorting non valido: pippo`, **nessuna query SQL** |
| `?sort=updatedAt,desc` | 500 — debito noto, **poi 400 in 3.3** | **prima dava 200**: è il buco che la fase chiude |
| `?category=sviluppo&size=1` | 200, `totalElements: 2`, `totalPages: 2` | il `COUNT` **parte davvero** (vedi sotto) |
| `?category=` (valore vuoto) | 200, tutti i post | la lista arriva vuota, non `[""]` (vedi sotto) |

Il 500 non è il risultato sbagliato: è la `RuntimeException` nuda, coerente con gli altri tre debiti. Il punto è **cosa** lo causa — su `?sort=pippo` non è più la `PropertyReferenceException` di Spring Data (la validazione arriva prima, come si legge nello stack trace: `SortValidator.checkValidity` chiamato da `PostController.getPosts`), e su `updatedAt` prima non c'era nessun errore.

**Due verifiche in sospeso dalla Fase 2, chiuse.**

- **`countQuery` finalmente forzato.** `?category=sviluppo&size=1` restituisce `totalElements: 2` con un solo elemento in `content`: quel `2` Spring non poteva dedurlo dai risultati, quindi la query di conteggio è stata eseguita. Chiude il debito aperto dalla 2.4, dove il `COUNT` veniva sempre saltato perché i risultati stavano in una pagina sola.
- **`?category=` con valore vuoto → 200 con tutti i post.** Il ramo del filtro non viene preso. L'ipotesi alternativa era che Spring producesse `[""]` — una lista con dentro una stringa vuota, che **non** è vuota e sarebbe finita nel filtro, con `findBySlugIn([""])` a vuoto e quindi un 500. Non succede: la lista arriva davvero vuota, e `!slugs.isEmpty()` fa il suo lavoro.

**Idee in sospeso (da valutare, non ancora decise):**
- **Colore/hex sulla categoria** per il badge a frontend. L'argomento a favore è corretto (è un attributo della categoria, non del post, quindi non va ripetuto ovunque). Da decidere però se il backend debba decidere la *presentazione*: la scuola opposta dice che il frontend mappa categoria → colore nel proprio tema, e cambiare palette non tocca il DB. **Rimandato al frontend**: è additivo (una colonna, mezz'ora in qualsiasi momento), e si capisce se serve davvero solo costruendo i badge
- Domande ancora aperte sulle sottocategorie: lo slug diventa `sviluppo/spring-boot` o resta piatto, e l'unicità è globale o tra fratelli? Cosa impedisce i cicli?
- **`package by feature` invece di `package by layer`**: la struttura attuale raggruppa per cosa una classe *è* (`dto/`, `entity/`); l'alternativa raggruppa per cosa *parla* (`post/`, `category/`). Non è estetica: per feature tutto ciò che serve sta in una cartella e le classi interne possono essere `package-private`, cosa impossibile per layer dove tutto deve essere `public` per attraversare i package. Scala meglio oltre una certa dimensione, ma su sette classi la differenza è teorica. **Non sottocartelle dentro `dto/`**: la soglia utile è intorno ai 10-15 file per package, qui siamo a tre

---

## Riepilogo

**Fase 2.1 e 2.2 completate.** Il modello dati non è più una tabella isolata: `Post` punta a `Category` via `@ManyToOne`, e la catena regge dal JSON in ingresso al JSON in uscita.

Concetti consolidati in Fase 2.1–2.2:
- Quando una tabella separata si giustifica rispetto a una colonna stringa o a un `enum`
- Dove sta la foreign key e perché: sul lato "molti" della relazione
- I due piani della relazione: colonna `category_id BIGINT` sul DB, campo `Category category` in Java, Hibernate che traduce
- `@ManyToOne` + `@JoinColumn`, e la convenzione sul nome della colonna
- Perché il costruttore vuoto serve **in lettura** (materializzare le entity da un `ResultSet`), non in scrittura né per creare la tabella — quella nasce dalle annotazioni
- Vincoli a più livelli: DDL (Postgres) vs runtime (Hibernate) vs validazione DTO (3.1)
- Relazione unidirezionale: `@OneToMany(mappedBy)` è una scelta di modello a oggetti, non di schema
- `findById` vs `getReferenceById`, e perché il secondo sposta l'errore lontano dalla causa
- **N+1 osservato dal vivo nei log**, con la cache di primo livello che lo maschera nei test

**Fase 2.3 completata.** Il debito N+1 è chiuso: `GET /posts` e `GET /posts/{id}` fanno **una query ciascuno**, verificato sui log SQL. In più `GET /posts?category=<slug>` filtra per categoria, e lo `slug` fa il suo lavoro di chiave nell'URL come previsto in 2.1.

Concetti consolidati in Fase 2.3:
- I due meccanismi per aggiungere una query a un repository: parsing del nome del metodo vs `@Query`, e perché solo il secondo può esprimere il fetching
- `JOIN` vs `JOIN FETCH`: la prima porta i dati fino a Java, la seconda li mette dentro l'oggetto
- Override vs overload, e perché un `findAll()` ridefinito riporta il fetching a essere globale
- Una sola `JOIN FETCH` che filtra e carica insieme, con l'alias usato nella `WHERE`
- Parametri nominati con `@Param`, e perché non si concatena mai nella stringa
- `@RequestParam(required = false)`: l'opzionalità è HTTP e vive nel controller; nel service è `null` e un `if`
- Nome esterno del param ≠ nome della variabile Java: due contratti diversi, come DTO ed entity
- N+1 "vero" (cresce con le righe) vs una query costante in più: due problemi di gravità diversa
- Che una query nuova non serve a niente finché il service continua a chiamare quella vecchia

**Fase 2.4 completata.** `GET /posts` non restituisce più tutto: `Page<PostDTO>` con `Pageable` risolto dai query param, e il JSON di risposta è quello stabile di `PagedModel`.

Concetti consolidati in Fase 2.4:
- `Pageable` (la domanda) vs `Page` (la risposta): due tipi, due direzioni
- Perché una `List` non basta: i metadati non hanno dove stare
- `LIMIT`/`OFFSET` aggiunti da Spring senza toccare la JPQL — e `fetch first ? rows only` che **non** è il `FETCH` di `JOIN FETCH`
- `countQuery` obbligatorio con `JOIN FETCH`, e perché deve filtrare come la query principale
- Il secondo caso d'uso della `JOIN` senza `FETCH`: raggiungere un campo per la `WHERE` senza caricare niente
- `Page.map()` invece dello stream, per non perdere i metadati nella conversione a DTO
- Perché non si serializza `PageImpl` direttamente: è la struttura interna di una classe del framework, e il WARN a runtime lo dice esplicitamente
- Annotazioni di abilitazione (`@Enable...`) con classe dal corpo vuoto: i bean li registra Spring via `@Import`

**Fase 2.4b completata — sottocategorie.** `Category` punta a sé stessa via `parent_id` nullable, e `GET /posts?category=<slug>` espande la categoria nei suoi discendenti: una macro restituisce i post dei figli, una foglia i propri.

Concetti consolidati in Fase 2.4b:
- Self-reference `@ManyToOne`: stessa regola di 2.2 (la FK sul lato "molti"), applicata a una tabella che punta a sé stessa
- `parent_id IS NULL` come definizione di macro-categoria, invece di un flag ridondante che può andare fuori sync
- Che lo schema non codifica la profondità: `parent_id` regge 2 livelli come 20, il limite vive nelle query
- Foreign key: il vincolo che garantisce che il valore corrisponda a una riga esistente — e che blocca il `DELETE` del padre finché qualcuno lo punta, **anche a chi bypassa l'applicazione**
- Perché la `IDENTITY` non ricicla gli id dopo un `DELETE`: un riferimento vecchio potrebbe esistere ancora da qualche parte
- Una query sola vs più query: la prima vince quando devi *caricare* dati già determinati (2.3), la seconda quando devi *decidere* cosa cercare
- `findByParentId` vs `findByParent(Category)`: stessa SQL, cambia solo cosa hai in mano al punto di chiamata — ma il tipo del parametro deve combaciare col campo, o l'app non parte
- `IN` (una riga tra questi valori) vs `WITH RECURSIVE` (scendere una gerarchia di profondità ignota): due problemi diversi
- `orElseThrow` converte `Optional<T>` in `T`, mentre `if (isEmpty())` controlla e basta — la variabile resta la scatola
- `.toList()` è immutabile: serve `new ArrayList<>()` per poterci fare `add`/`addAll`
- Che una formulazione uniforme (sempre sé stessa più i figli) batte un `if` quando la condizione dell'`if` è una domanda che il modello non sa rispondere bene
- **`@ManyToOne` e `@OneToOne` sono EAGER di default**, `@OneToMany` e `@ManyToMany` lazy — visto nei log come una query in più solo per le categorie che hanno un genitore

**Fase 2.5a completata — selezione multipla.** `GET /posts?category=sviluppo,react` (e la forma ripetuta `?category=a&category=b`) risolve più slug in una query sola, espande ognuno nei figli e deduplica gli id con un `Set`. In più è stato chiuso il `parent` eager, che si è rivelato un N+1 e non la query costante che il README dichiarava.

Concetti consolidati in Fase 2.5a:
- `List<String>` su `@RequestParam` accetta due sintassi (`a,b` e param ripetuto), e lo split resta un fatto di parsing HTTP che non deve arrivare al service
- `null` (parametro assente) e lista vuota (`?category=`) sono due situazioni diverse che arrivano per strade diverse
- Il suffisso `In` nelle derived query, e perché il ciclo di `findBySlug` sarebbe stato un N+1 in versione nuova
- Che il **tipo di ritorno di un metodo repository non è una scelta libera**: descrive quello che la query può restituire, non quello che è comodo avere (`Optional` solo se al massimo una riga)
- `orElseThrow` non esiste sulle liste, e non serve: una `List` dopo l'`if` è già utilizzabile, a differenza di un `Optional`
- Fallire solo quando *nessuno* degli slug matcha: un filtro multiplo è un restringimento progressivo, non l'indirizzo di una risorsa
- `LinkedHashSet` vs `HashSet`: determinismo gratuito quando il contenuto finisce sotto gli occhi di qualcuno
- Che un duplicato dentro un `IN` non produce righe ripetute — il `Set` serve per pulizia, non a tappare una falla
- **Accetta il tipo più generale che basta** (`Collection<Long>` nel parametro), restituisci il più specifico che puoi
- **Un `JOIN FETCH` copre solo il primo salto della catena**: la categoria caricata aveva a sua volta un `parent` EAGER, e l'N+1 si era riformato un livello più in là
- Che un debito classificato come "costante" può essere un N+1 letto con dati di test troppo piccoli — stesso inganno della cache di primo livello in 2.2
- `FetchType.LAZY` e `open-in-view: false` vanno decisi insieme: il secondo tiene la sessione aperta fino alla serializzazione e trasforma gli errori di fetching in query silenziose
- Che il codice commentato è la forma peggiore di conservazione: non compila, non viene validato, non compare nelle ricerche — e Git conserva già tutto

**Fase 2.5b completata — sorting.** `?sort=` funziona su `title`, `createdAt` e `category.name`, verificato sui log SQL; tutto il resto viene rifiutato da un'allowlist esplicita. In più sono state chiuse le due verifiche che la 2.5a aveva lasciato in sospeso.

Concetti consolidati in Fase 2.5b:
- Il `Pageable` porta anche il **sorting**, e Spring lo inietta come `ORDER BY` nella JPQL senza toccare la `@Query` — stesso meccanismo di `LIMIT`/`OFFSET` in 2.4
- L'`ORDER BY` deve stare **prima** di `OFFSET`/`FETCH`: si ordina l'insieme completo, poi si taglia la fetta
- `?sort=` parla **JPQL**: nomi di campo Java, non di colonna — `createdAt` funziona, `created_at` no
- **Ordinare e filtrare sono due meccanismi diversi**: il filtro va nella `WHERE` e riduce le righe, il sort va nell'`ORDER BY` e le dispone
- Il `Pageable` **naviga le relazioni** (`category.name`), e riusa la join già presente senza aggiungerne
- **I query param sono un terzo canale che il DTO non copre**: il DTO difende il body in entrambe le direzioni, ma `?sort=` raggiunge l'entity direttamente — un campo escluso dalla risposta restava ordinabile, quindi parte del contratto senza essere esposto
- Il criterio che ne esce: **i campi ordinabili devono coincidere con i campi visibili nella risposta**
- Validazione **di forma** (insieme noto a compile time → controller) vs **di dominio** (serve il DB per decidere → service). "Controller sottile" significa niente logica di business, non zero righe
- **Bean quando l'oggetto potrebbe aver bisogno di collaboratori, `static` quando la firma è già completa** — la scommessa di 1.6 su `PostMapper` era giusta lì e sarebbe stata sbagliata qui
- `final` su una classe (non estendibile) vs su un campo (non riassegnabile): stessa keyword, due contratti
- `Set.of(...)` per una costante: `final` protegge il riferimento, non il contenuto — stesso doppio piano di `.toList()` in 2.4b
- Package definiti **per affermazione** (`validation/`) e non per negazione (`utils/`): il ragionamento di 1.6 riapplicato
- Che un debito "mai verificato" va verificato prima di dichiararlo chiuso: il `COUNT` si vede solo forzando `size` sotto il numero di risultati

**Fase 2 chiusa** per quello che aveva senso chiudere adesso. I due pezzi rimasti sono rimandi motivati, non voci lasciate a metà: la **2.5c** (Specification/Querydsl) aspetta che il modello abbia abbastanza criteri da rendere la combinatoria un problema vero — scriverla ora significherebbe indovinare la forma dell'astrazione prima di conoscerla, e il costo del rimando è zero perché Specification si aggiunge accanto alle `@Query` esistenti senza riscrivere niente. La **2.6** (Flyway) aspetta Docker, dove il container Postgres nasce vuoto e le migrazioni girano da zero in ordine, invece del caso particolare della baseline su un DB già popolato.

---

[← Indice](../README.md)

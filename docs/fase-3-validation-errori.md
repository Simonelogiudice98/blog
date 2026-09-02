# Fase 3 — Validation & Error Handling

> Obiettivo: validare input, gestire errori elegantemente.
> Parte del [diario di sviluppo](../README.md) del blog backend.

- [x] **3.1** `@Valid` e `@NotBlank`, `@Size`, `@Pattern` su DTO — quattro vincoli su `CreatePostDTO`, `@Valid` come innesco nel controller, 400 automatico
- [x] **3.1b** `PUT /posts/{id}` — `UpdatePostDTO`, `@Transactional` su tutto il service, `updatedAt` finalmente popolato
- [x] **3.1c** `DELETE /posts/{id}` — hard delete, 204, 404 su id inesistente
- [x] **3.2** Custom validator — **uscita diversa dal piano**: non Bean Validation ma un controllo di dominio nel service (`resolveAssignableCategory`), perché la regola "solo le foglie sono assegnabili" ha bisogno del DB. Lo slug unique resta il candidato per un vero `ConstraintValidator`, ma aspetta `POST /categories` (Fase 4)
- [x] **3.3** GlobalExceptionHandler con `@RestControllerAdvice` — due eccezioni custom (`ResourceNotFoundException`, `BadRequestException`) più quattro handler, che coprono anche `MethodArgumentNotValidException` e il catch-all su `Exception`
- [x] **3.4** Risposte di errore strutturate — record `ApiError` con cinque campi (`timestamp`, `status`, `code`, `message`, `path`). Niente `ApiResponse` wrapper sulle risposte di successo: non serviva
- [x] **3.5** HTTP status codes corretti — conseguenza diretta della 3.3, tutti e sei i debiti chiusi
- [x] **3.6** Logging con SLF4J — `@Slf4j` di Lombok, `debug` sui 4xx e `error` con stack trace sui 5xx, livello configurato per package

**Concetti:**
- Bean Validation (JSR-380)
- Exception handling centralizzato
- ApiResponse pattern
- Logging best practices
- Transazioni: dirty checking, entity managed vs detached, `readOnly`

**Bean Validation — cos'è e come si compone (Fase 3.1):**

La specifica JSR-380 (implementazione: Hibernate Validator, già nel classpath via `spring-boot-starter-validation`) esprime i vincoli **come annotazioni sul campo** invece che come `if` nel service. La regola sta attaccata al dato, non al punto in cui il dato viene usato — così il service torna a contenere solo logica di business.

Tre pezzi distinti, e il terzo è quello che si dimentica:
1. **Le annotazioni di vincolo** (`@NotNull`, `@NotBlank`, `@Size`, `@URL`) — sono solo metadati, da sole non fanno niente
2. **Il validatore** — Hibernate Validator, che a runtime le legge via reflection
3. **L'innesco**: `@Valid` sul parametro del controller. **Senza `@Valid` le annotazioni sul DTO sono inerti**: ci sono ma nessuno le guarda

Sui **record** le annotazioni stanno sui componenti, dentro la parentesi — non c'è un corpo con i campi dove metterle. Il compilatore le propaga al campo privato generato.

**I quattro campi di `CreatePostDTO` (e `UpdatePostDTO`, identico):**

```java
public record CreatePostDTO(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content,
        @URL(protocol = "https") @Size(max = 255) String imageUrl,
        @NotNull Long categoryId) { }
```

| campo | vincoli | perché |
|---|---|---|
| `title` | `@NotBlank`, `@Size(max = 255)` | obbligatorio; il `@Size` rispecchia il `VARCHAR(255)` del DDL |
| `content` | `@NotBlank` | obbligatorio; `TEXT` non ha limite, **nessun tetto per scelta consapevole** |
| `imageUrl` | `@Size(max = 255)`, `@URL(protocol = "https")` | opzionale, ma ben formato se presente |
| `categoryId` | `@NotNull` | difetto di **forma**, non di dominio |

- **`@NotNull` e non `@NotBlank` su `categoryId`**: `@NotBlank` significa "non nullo e non solo spazi", e quel "spazi" presuppone una `String`. Su un `Long` **l'applicazione non parte proprio** — ogni vincolo dichiara su quali tipi è applicabile, e il validatore lo verifica all'avvio. I tre da non confondere, tutti su `String`: `@NotNull` (blocca solo `null`), `@NotEmpty` (anche `""`), `@NotBlank` (anche `"   "`). Solo `@NotNull` è neutro rispetto al tipo.
- **Perché `categoryId` ha bisogno di un vincolo anche se il service lo controlla già.** L'obiezione iniziale era che `@ManyToOne(optional = false)` e `findById().orElseThrow()` bastassero. Ma i due input invalidi si comportano in modo diverso: `categoryId: 99` finisce nell'`orElseThrow` — un errore **deciso da noi**, col nostro messaggio; `categoryId: null` finisce in `findById(null)`, che Spring Data rifiuta con una `InvalidDataAccessApiUsageException` — errore **del framework**, con un messaggio che parla di API JPA, dopo aver attraversato controller e service. E soprattutto: `null` è un difetto **di forma** (lo vedi guardando il JSON), `99` è di **dominio** (serve il DB). È di nuovo il criterio della 2.5b.
- **Il `@Size` che rispecchia il DDL non è ridondante.** Senza, un titolo da 400 caratteri attraversa tutto lo stack, Postgres rifiuta l'`INSERT` (`value too long for type character varying(255)`), Hibernate lo avvolge in una `DataIntegrityViolationException` e il client prende un 500 che parla di tipi SQL — senza sapere quale campo né qual è il limite. Con il vincolo, l'errore nasce prima del service, nomina il campo e il limite, e nessuna connessione al DB viene sprecata. È lo schema dei tre livelli di 2.2 applicato al *contenuto* invece che alla presenza.
- **Perché sul DTO e non sull'entity.** Bean Validation funzionerebbe anche lì (Hibernate la controlla prima dell'`INSERT`), ma sarebbe mettere sull'entity una regola che riguarda il contratto HTTP. `@Column(nullable = false)` è **DDL**; `@NotBlank` sul DTO è **validazione in ingresso**. Momenti diversi, entrambi utili. `PostDTO` non ha vincoli: lo costruiamo noi dal DB, non arriva da nessuno.
- **`content` senza `@Size`, deciso e non dimenticato.** `TEXT` esiste apposta per lunghezze ignote, e un tetto arbitrario rifiuterebbe un giorno un articolo legittimo. L'argomento contrario (un client può mandare 50MB) è reale ma non è un problema di Bean Validation: la difesa strutturale è `server.max-http-request-size`, che agisce prima del parsing JSON.
- **`@URL(protocol = "https")` è una difesa XSS, non pedanteria.** Quella stringa finisce in `PostDTO` e poi in un `<img src>` o `<a href>` a frontend: `javascript:alert(document.cookie)` salvato in DB è **XSS stored**, e si esegue nel browser di ogni visitatore. La difesa vera è lato rendering (React blocca gli schema pericolosi), ma vale la lezione di 2.4b — *il client non è sotto il nostro controllo*. Nota che `protocol` accetta **un solo valore**, non una lista.
- **Niente vincolo sull'estensione del file.** `@URL` verifica la sintassi, non che l'URL punti a un'immagine — `https://x.com/doc.pdf` passa. Ma controllare `.jpg`/`.png` rifiuterebbe URL legittimi: molti CDN servono immagini senza estensione (`/photo?w=800`). E quando arriverà il bucket sarà il **nostro sistema** a generare quella stringa, quindi non ci sarà niente da validare in ingresso.
- **I vincoli di formato ignorano `null`.** `@Size`, `@Pattern`, `@Email`, `@URL` considerano `null` valido — la presenza è compito di `@NotNull`. È esattamente ciò che serve per un campo opzionale: se c'è deve essere ben formato, se non c'è va bene. Verificato: `imageUrl: null` passa.

**Il 400 arriva gratis (e il body no).**

`@Valid` che fallisce produce una `MethodArgumentNotValidException`, che **Spring MVC già conosce**: il `DefaultHandlerExceptionResolver` la mappa su 400 senza che noi scriviamo niente. È la differenza con i `RuntimeException` nudi, che Spring non conosce e quindi diventano 500.

Ma la risposta è muta: `{timestamp, status: 400, error: "Bad Request", path}` e basta. Nel log ci sono nome del campo, valore rifiutato e messaggio; nel JSON niente. **Le informazioni esistono già dentro l'eccezione, manca solo chi le estrae** — ed è il lavoro di 3.3 + 3.4.

Due dettagli osservati nei log:
- **Le violazioni si accumulano**: `Validation failed ... with 2 errors`. È l'opposto del fail-fast scelto per `SortValidator` in 2.5b — per un form è il comportamento giusto, il client vede tutti gli errori in una volta.
- **I messaggi arrivano localizzati** (`non deve essere null`, `deve essere un URL valido`): Hibernate Validator usa il `Locale` della richiesta. Feature se il frontend è multilingua, altrimenti da forzare con `message = "..."` sulle annotazioni.

**Test API (Fase 3.1) — verificato su Postman:**

| body | esito | dove si ferma |
|---|---|---|
| `title: ""` + `categoryId: null` | 400, **2 violazioni** raccolte insieme | controller, **nessuna query SQL** |
| `imageUrl: "javascript:alert(1)"` | 400 | controller, nessuna query |
| `imageUrl: null` | 200 | passa, come deve |

---

**`PUT /posts/{id}` — decisioni di design prese (Fase 3.1b):**

L'update mancava, e c'era un indizio: `updatedAt` esiste su `Post` dalla 1.2 con `@PreUpdate` che lo popola, ma era **sempre `null`** per ogni riga in DB. La macchina c'era, il pulsante no.

**`PUT` e non `PATCH`.**

La differenza non è "quanti campi mandi" — `PATCH` ne accetta benissimo più d'uno. È **cosa succede ai campi che non mandi**: con `PUT` la richiesta *è* la nuova risorsa (ometti `imageUrl` → l'immagine viene cancellata), con `PATCH` il resto resta com'è.

`PATCH` sembrava più comodo, ma ha un costo preciso:
- Con `String title` a `null` il service **non può distinguere** "campo assente" da `"title": null` esplicito. In Java sono lo stesso valore, e il DTO non conserva l'informazione "era presente nel JSON". Le vie d'uscita (`Optional<String>`, `JsonNullable`, merge manuale) sono tutte più macchinose.
- **`@NotBlank` diventa inutilizzabile**: se lo metti, un update che cambia solo `content` viene rifiutato perché `title` è assente. Ogni campo diventa opzionale, e la garanzia "il titolo non è mai vuoto" si sposta nel service, a mano.
- Il risparmio è illusorio: in un editor di blog il client **ha già il post intero** (l'ha caricato per la form), quindi rispedirlo costa qualche centinaio di byte.

Scelto `PUT`: contratto più semplice, validazione dichiarativa che resta.

**`UpdatePostDTO` separato, benché oggi identico a `CreatePostDTO`.**

Stessi quattro campi, stessi quattro vincoli. L'argomento per unificarli è la duplicazione; quello per separarli è di 1.6, e vince: **due contratti restano separati anche quando coincidono, perché divergono appena uno dei due cambia.** Il caso concreto è già in piano — `author` in 4.7 si prende dal token in creazione, e in modifica probabilmente non deve cambiare affatto. Se sono la stessa classe, quel giorno ogni punto di chiamata è già scritto sul tipo condiviso.

Criterio generalizzabile: **la duplicazione strutturale non è duplicazione se i due pezzi hanno ragioni indipendenti di cambiare.** (È lo stesso criterio che poco dopo, in 3.2, ha portato alla conclusione *opposta* per `resolveAssignableCategory`: lì le due copie cambiano sempre insieme, quindi vanno unite.)

Nota che "quanti campi manda il client" (fatto del verbo) e "quali campi il client *può* mandare" (fatto del contratto) sono due domande diverse: `PUT` garantisce che spedisca l'intera risorsa **secondo quel contratto**, non che i contratti siano lo stesso.

**`id` nell'URL, mai nel body.**

`UpdatePostDTO` non ha `id`, come `CreatePostDTO`. Se ci fosse, `PUT /posts/14` con `{"id": 99}` porrebbe la domanda "chi vince?", e tutte le risposte sono scomode: fidarsi dell'URL (campo decorativo ignorato in silenzio), fidarsi del body (l'URL mente e il client modifica un post diverso da quello richiesto), o confrontarli e rifiutare (un controllo scritto per gestire un problema creato da sé).

Regola: **l'identità della risorsa sta nell'URL, il contenuto nel body.** Non si sovrappongono. È la cura di 1.6 — *un campo non esposto è un campo che non devi difendere* — applicata a un buco diverso.

**Il flusso nel service: caricare, modificare, non ricostruire.**

```java
Post post = postRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Post non trovato: " + id));

Category category = resolveAssignableCategory(dto.categoryId());

post.setTitle(dto.title());
post.setContent(dto.content());
post.setImageUrl(dto.imageUrl());
post.setCategory(category);
```

- **Non esiste un `update()` in `JpaRepository`.** `save()` fa entrambe le cose e decide guardando **l'id dell'entity che gli passi**: `null` → `INSERT`, valorizzato → `UPDATE`.
- **Da cui l'errore da non fare**: `save(postMapper.toEntity(dto, category))` costruirebbe un `Post` **nuovo**, con `id = null` (l'`UpdatePostDTO` non ce l'ha) → `INSERT`, e ti ritrovi un post in più invece che modificato. Il mapper costruisce oggetti nuovi, il repository restituisce oggetti esistenti: due cose diverse.
- **I setter sull'entity, gli accessori senza `get` sul DTO** (`dto.title()`): entity mutabile per Hibernate, DTO immutabile perché record. La coppia di 1.6, vista in azione.
- **Tipo di ritorno `PostDTO` e non `UpdatePostDTO`**: la risposta della PUT ha la stessa forma di quella della POST e della GET — è sempre "un post". `UpdatePostDTO` è ciò che il client *manda*, non contiene `id` né `createdAt`, quindi non potrebbe nemmeno rappresentare il risultato.

**`updatedAt` resta fuori da `PostDTO`.**

Ora che l'update esiste, il campo si popola davvero — e la domanda "va esposto?" è tornata aperta. Scelto **no**: per un lettore la data di pubblicazione basta, e "aggiornato il..." invita la domanda "cos'è cambiato?" a cui non c'è risposta.

La conseguenza è che `updatedAt` resta un **campo interno** (audit, debug), e questo **riconferma retroattivamente la 2.5b**: l'allowlist di sorting che lo esclude non era una toppa in attesa che il campo diventasse esponibile, era la decisione giusta e lo resta. I due insiemi — campi ordinabili e campi visibili — continuano a coincidere. Scelta reversibile a costo zero: due righe, il giorno che il frontend lo chiede.

---

**`@Transactional` — il pezzo più utile della sessione.**

Il primo `PUT` funzionante faceva **quattro query** dove ne bastavano due:

```
1. select post join category where p1_0.id=?     ← findById(14)
2. select category where c1_0.id=?               ← findById(5)
3. select post join category where p1_0.id=?     ← non scritta da noi
4. update post set ... where id=?
```

Causa: **il metodo non era transazionale**. I metodi di `SimpleJpaRepository` lo sono singolarmente, quindi un service senza annotazione non è *una* transazione ma **una sequenza di transazioni scollegate**. Conseguenze, una per query di troppo:

- La `Post` caricata dalla query 1 diventa **detached** appena `findById` finisce: la sessione che la tracciava si è chiusa. I setter modificano un oggetto scollegato e il **dirty checking** non può scattare.
- La query 2 non può riusare la categoria già portata in memoria dalla `JOIN FETCH`: nuova sessione, **cache di primo livello vuota**.
- La query 3 è `save()` che, ricevendo un'entity detached con id valorizzato, deve **rileggere la riga** per capire cosa è cambiato. È il `merge` di JPA.

Con `@Transactional` sul metodo, tutte le chiamate condividono una sessione sola: **da 4 query a 2.**

```
1. select post join category where p1_0.id=?    ← findById(14)
2. update post set ... where id=?               ← al commit
```

- La SELECT sulla categoria sparisce: servita dalla **cache di primo livello**, perché React era già in memoria via `JOIN FETCH`. È lo stesso meccanismo che in 2.2 *mascherava* l'N+1 — qui lavora a favore.
- La SELECT prima dell'update sparisce: entity **managed**, quindi niente `merge`, e l'`UPDATE` parte al commit dal confronto con lo snapshot già in memoria.
- Caso non testato ma prevedibile: cambiando *anche* la categoria, la query 2 ritorna (quella riga non è in cache). Diventano 3, ed è corretto.

**`readOnly = true` sulle letture.** Dice a Hibernate di saltare il dirty checking al commit: niente snapshot da tenere né da confrontare. Su una pagina di 20 post è lavoro e memoria risparmiati, e comunica l'intento a chi legge.

| metodo | annotazione |
|---|---|
| `getPosts`, `getPostById` | `@Transactional(readOnly = true)` |
| `createPost`, `updatePost`, `deletePost` | `@Transactional` |

- **Nel service, non nel controller.** Primo tentativo sbagliato: messe sui metodi del controller. `@PathVariable` è HTTP e sta nel controller, `@Transactional` è persistenza e sta nel service — il controller non deve sapere che sotto c'è un database. E c'è la ragione pratica: una transazione aperta nel controller resterebbe aperta **durante la serializzazione JSON**, che è esattamente ciò che `open-in-view: false` ha chiuso in 2.5. Sarebbe riaprire quella porta da un'altra parte.
- **Import da `org.springframework.transaction.annotation`**, non quello di Jakarta.
- **Regola**: `@Transactional` su ogni metodo di service che fa più di una chiamata al repository, e su **tutti** quelli che scrivono. Il costo non è solo in query: senza transazione, se una scrittura fallisce dopo che un'altra è già andata, non c'è niente da annullare.
- Alternativa considerata: `@Transactional(readOnly = true)` **sulla classe**, sovrascritto con `@Transactional` sui metodi che scrivono. Il default diventa sicuro e la scrittura è esplicita.

**Test API (Fase 3.1b) — verificato su Postman e sui log:**

| chiamata | esito | query |
|---|---|---|
| `PUT /posts/14`, body valido | 200, titolo aggiornato | **2** (erano 4) |
| `GET /posts/14` dopo | 200, modifica persistita, `updatedAt` assente dal JSON (per design) | 1 |
| `GET /posts` dopo le annotazioni | 200, invariata | 1 |
| `PUT` senza body | 400 `HttpMessageNotReadableException` — gestita da Spring | 0 |

---

**"Solo le foglie sono assegnabili" — decisioni di design prese (Fase 3.2):**

Il debito più vecchio ancora aperto, registrato in 2.4b e mai chiuso. La 3.2 doveva essere "custom validator (es. slug unique)", ma lo slug appartiene a `Category`, che **non ha nessun DTO in ingresso** — `POST /categories` è rimandato alla Fase 4. Bersaglio cambiato.

**Perché non è Bean Validation.**

Per sapere se la categoria 5 è una foglia serve **interrogare il DB**: l'informazione non è sulla riga 5, è nelle *altre* righe (ce n'è qualcuna con `parent_id = 5`?). Le annotazioni lavorano in memoria sull'oggetto e non sanno niente della tabella.

Quindi, per il criterio della 2.5b — *validazione di forma nel controller, di dominio nel service* — il controllo vive nel **service**, e la 3.2 esce senza una sola annotazione. Lo slug unique resta il candidato per un vero `ConstraintValidator`, ma con `POST /categories` in Fase 4.

**Errore da non fare: `parent_id` non dice se è una foglia.**

`parent_id` valorizzato significa "ha un **genitore**", cioè è una sottocategoria. Foglia significa il contrario: **non ha figli**. Non sono la stessa cosa — con tre livelli una categoria di mezzo ha entrambi, e anche con due livelli una macro senza figli avrebbe `parent_id = null` ma sarebbe comunque una foglia.

**`existsByParentId` e il rovesciamento.**

```java
boolean existsByParentId(Long parentId);
```

- Si legge come si compone: `existsBy` + `ParentId` → "esiste almeno una riga il cui campo `parent` ha `id` uguale a...?". Passi l'id 5 e la query cerca **chi punta a 5**, non cosa punta la 5.
- **`exists` fa parte del vocabolario delle derived query** (con `find`, `count`, `delete`). Genera qualcosa come `select 1 from category where parent_id = ? fetch first 1 row only`: si ferma al primo risultato, **non carica nessuna entity**.
- **Tipo di ritorno `boolean`**, per la regola di 2.5a: il tipo descrive quello che serve sapere, e qui la domanda è sì/no. È il motivo per cui in 2.5a si era deciso di *non* conservare `findByParentId` — è un metodo diverso, non una variante.

**`resolveAssignableCategory`: estrarre perché cambiano insieme.**

Il controllo serve in `createPost` **e** in `updatePost` — un `PUT` con il `categoryId` di una macro passerebbe altrimenti. Sei righe duplicate:

```java
private Category resolveAssignableCategory(Long categoryId) {
    Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new RuntimeException("Categoria non trovata: " + categoryId));

    if (categoryRepository.existsByParentId(categoryId)) {
        throw new RuntimeException("Non è possibile assegnare un post a una macro-categoria: " + category.getName());
    }
    return category;
}
```

- **Restituisce la `Category`, non un `boolean`.** Primo tentativo sbagliato: un metodo che ritornava `true/false`. Ma chi chiama non vuole sapere *se* va bene, vuole **l'oggetto** — subito dopo ci farà `setCategory(category)` o `toEntity(dto, category)`. Con il boolean il `findById` sarebbe rimasto duplicato in entrambi i metodi, più un `if` in aggiunta: sei righe sarebbero diventate sette. Il nome lo diceva già: *resolve*, non *is*.
- **È la forma di `orElseThrow`**: o ti dà la cosa buona, o esplode. Mai un valore da interpretare.
- **`findById` prima, `existsByParentId` dopo.** L'obiezione era che se la categoria è una macro il `findById` è sprecato. Ma con l'ordine inverso, un id inesistente darebbe `existsByParentId(999) = false` (ovvio: la 999 non esiste, nessuno la punta), il controllo passerebbe, e la query sarebbe sprecata *comunque*. Il conto è pari, con i costi spostati sul caso d'errore più raro invece che sul più comune (il typo). E soprattutto la `findById` non è sprecata: serve per il **messaggio** — `"...macro-categoria: Sviluppo"` è utile, `"...: 3"` costringe chi legge ad andare a guardare cos'è la 3.
- **Estratto, al contrario dei due DTO.** Sembra contraddire la decisione presa poche ore prima su `UpdatePostDTO`, ma è lo stesso criterio applicato a un caso opposto: i due DTO hanno **ragioni indipendenti** di cambiare, queste due copie cambiano **sempre insieme** (una regola nuova, un messaggio diverso, l'eccezione tipizzata in 3.3). Stessa ragione di cambiare = una cosa sola. È il ragionamento di 2.2 su `CategoryMapper`.
- **`private`**: è un dettaglio interno del service, non parte del contratto verso il controller.

**Test API (Fase 3.2) — verificato su Postman:**

| chiamata | esito |
|---|---|
| `POST /posts` con `categoryId` di una foglia (React) | 200 |
| `POST /posts` con `categoryId` di una macro (Sviluppo) | 500 con `RuntimeException` — debito noto, **risolto in 3.3**: ora 400 |
| `PUT /posts/{id}` con `categoryId` di una macro | idem |

---

**`DELETE /posts/{id}` — decisioni di design prese (Fase 3.1c):**

Il CRUD era a tre lettere su quattro; i post si cancellavano a mano da pgAdmin.

- **Hard delete adesso, soft delete in 5.2.** Il contratto HTTP è identico nei due casi (`DELETE /posts/{id}` → 204), quindi cambiare implementazione dopo sarà tre righe dentro il service. Piano per la 5.2: **recupero entro 30 giorni, poi cancellazione definitiva** — il che richiede anche un job schedulato (`@Scheduled`) per la pulizia, altrimenti la tabella cresce per sempre.
- **Perché il soft delete si fa**: il cestino (un utente che cancella per sbaglio non ha recupero), l'audit (si lega alla 5.1), e l'integrità referenziale il giorno che `comment` punterà a `post` — nascondere un post non obbliga a decidere cosa fare dei commenti. **Perché non sempre**: ogni query deve ricordarsi `WHERE deleted = false` e dimenticarlo in un punto solo fa riapparire i cancellati; le `unique` smettono di comportarsi come ci si aspetta (uno slug occupato da una riga cancellata blocca il riuso); e "cancella i miei dati" del GDPR non si soddisfa con un flag.
- **204 e non 200 con messaggio.** Il messaggio non lo legge nessuno: il client sa già che è andata dallo status code, e un frontend fa `if (res.ok) rimuoviDallaLista()`. In più sarebbe una stringa in italiano che entra nel contratto e va tradotta il giorno che l'app è multilingua. `204 No Content` dice esattamente "è andata, non c'è niente da darti indietro".
- **404 e non idempotenza.** `deleteById` su un id inesistente **non fa niente e non lancia**, quindi il default sarebbe 204 su un post mai esistito. La specifica HTTP direbbe che va bene (`DELETE` è idempotente), ma il 404 è più utile e più diffuso. Il caso concreto: due schede aperte sulla lista, nella prima cancelli il post 14, nella seconda la lista è vecchia. Con 204 il frontend toglie l'elemento e **resta convinto di essere allineato**; con 404 sa che la sua vista è stale e può ricaricare. Il controllo blinda il backend *e* dà al frontend l'informazione che gli serve — non è una scelta tra i due. Argomento decisivo: `getPostById` e `updatePost` lanciano già entrambi se il post non c'è, e tre metodi che trattano lo stesso fatto in due modi diversi obbligano chi legge a scoprire perché.
- **`delete(post)` e non `deleteById(id)`**: il post è già stato caricato dall'`orElseThrow`, e `deleteById` rifarebbe internamente lo stesso `findById`.
- **`@ResponseStatus(HttpStatus.NO_CONTENT)` e non `ResponseEntity<Void>`.** Spring risponde 200 di default a un metodo `void`. `ResponseEntity` serve quando lo status dipende da una condizione a runtime; qui è fisso, e l'annotazione è coerente con gli altri metodi del controller che non usano `ResponseEntity`.

---

**Uniformato il service (refactoring di contorno):**

Tutti i punti che decidevano sull'assenza usavano due stili diversi — `if (isEmpty())` + `.get()` in `getPostById` e `createPost`, `orElseThrow` in `updatePost`. Uniformati tutti al secondo, che è quello scelto in 2.4b: apre la scatola *e* decide, senza lasciare un `.get()` in giro. In più **tutti i `RuntimeException` hanno ora un messaggio** (alcuni erano completamente nudi): serviranno comunque in 3.3, e nel frattempo lo stack trace dice quale è scattato.

---

**Error handling centralizzato — decisioni di design prese (Fase 3.3 + 3.4):**

Sei `RuntimeException` nudi, tutti che producevano 500. La tabella dei debiti diceva già quale status ciascuno *doveva* produrre: metà del design era quindi già fatta.

**Quante classi di eccezione, e con che criterio.**

Il primo istinto era una classe per **tipo di errore**: post inesistente, categoria inesistente, categoria macro, e così via. Quattro classi. Poi un accorpamento per **natura del fatto**: "post inesistente" e "`categoryId` inesistente" sono entrambi *qualcosa non esiste*, quindi insieme.

Ma quel criterio taglia nella direzione sbagliata, e la tabella dei debiti lo dimostrava già: i due casi hanno **status diversi** (404 e 400), per il ragionamento scritto in 2.4b — `/posts/999` è una risorsa che non esiste, `/posts` con `categoryId: 99` è un URL che esiste con dentro un valore invalido. E il `@ControllerAdvice` discrimina **sul tipo** per decidere lo status: se sono la stessa classe, quando l'eccezione arriva l'informazione per scegliere non c'è più.

Da cui il criterio adottato: **una classe per status**. Due classi, e il raggruppamento è dettato da cosa il layer web dovrà farne.

```java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
}
```

- **`extends RuntimeException`, non `Exception`.** Le *checked* obbligano ogni chiamante a dichiarare `throws` o a fare `try/catch`, e hanno senso quando il chiamante immediato **può fare qualcosa**: riprovare, usare un fallback. Qui non può: l'unica risposta sensata a "post non trovato" è dirlo al client via HTTP, cioè il lavoro del `@ControllerAdvice`. L'eccezione deve **attraversare** il controller, e con la checked ogni firma sul percorso si riempirebbe di `throws` senza che nessuno di quei livelli ne faccia niente.
- **Argomento decisivo, non stilistico**: `@Transactional` fa rollback automatico **solo sulle unchecked**. Su una checked Spring committa lo stesso, e servirebbe `rollbackFor = ...`. Con le annotazioni già messe in 3.1b, la checked avrebbe dato il comportamento sbagliato per default.
- Coerente con l'ecosistema: tutte le eccezioni di Spring sono unchecked.
- **I costruttori non si ereditano** (metodi e campi sì): senza dichiararlo esplicitamente, `new ResourceNotFoundException("...")` non compilerebbe.
- Sul **nome**: `BadRequestException` porta vocabolario HTTP dentro il service, il che stona con la linea tenuta altrove (`@Transactional` non nel controller, lo split di `?category=a,b` non nel service). Considerato e accantonato: il costo di un nome meno puro è zero, quello di un nome oscuro no.

**Il contenuto dell'eccezione: solo il messaggio.**

Due strade. **A**: l'eccezione porta la frase già montata (`"Post non trovato: 999"`), l'handler la copia nel JSON. **B**: porta dati strutturati (`resourceName`, `id`) e la frase la compone qualcun altro.

La B è più "seria" in astratto, ed è dove il progetto dovrebbe arrivare — ma è lo stesso schema di 2.5c e del colore sulla categoria: **il suo unico vantaggio reale richiede un client che oggi non esiste.** Progettare adesso il vocabolario dei codici significherebbe indovinare quali errori il frontend vorrà distinguere, con ottime probabilità di scriverne sei e vederne usare uno.

Scelta la **via intermedia**: A per il payload, più un campo `code` sull'`ApiError`. Il messaggio resta montato nel service, ma il JSON porta un identificatore stabile su cui un client può scrivere `if` — invece di fare pattern matching su una frase in italiano che nessuno considera parte del contratto. Il giorno che serve la B piena, i dati strutturati si aggiungono senza toccare niente.

**`ApiError`: cinque campi, e perché non tre.**

```java
public record ApiError(Instant timestamp, int status, String code, String message, String path) { }
```

- **In `dto/` e non in `exception/`.** Il dubbio era un package terzo, scartato perché sarebbe stato da un file solo. Il criterio che scioglie: **cos'è, non a cosa serve.** `ApiError` è un oggetto immutabile che viene serializzato in JSON e spedito al client — la definizione di DTO, identico a `PostDTO` in natura. `exception/` contiene cose che si **lanciano**; `ApiError` non si lancia mai. Raggruppare per tema ("roba che riguarda gli errori") invece che per natura sarebbe incoerente col resto del progetto, organizzato per layer.
- **`status` e `path` sono ridondanti per il client**, che ha già entrambi (lo status nella status line, il path perché l'ha chiamato lui). Si tengono per un altro motivo: **il body viene separato dalla richiesta** appena finisce in un log, in Sentry, o in uno screenshot allegato a un ticket. Il criterio: *si tiene ciò che il body non può ricostruire da solo quando viaggia da solo.*
- **`timestamp` come `Instant` e non `LocalDateTime`.** `LocalDateTime` non porta il fuso, quindi `14:32:07` è ambiguo tra un server in UTC e un utente a Milano. Per un timestamp di **evento**, che serve proprio a correlare cose successe in posti diversi, l'ambiguità è il difetto peggiore. `Instant` serializza come `...Z`, esplicito.
- **`status` come `int` e non `HttpStatus`**: il secondo serializzerebbe come `"BAD_REQUEST"`, e sarebbe un tipo del framework web dentro un DTO.
- **`error: "Bad Request"` scartato** (c'è nel default di Spring): è la reason phrase, derivabile meccanicamente dallo status, e con il `code` sarebbe la sua versione peggiore.

**Il `@RestControllerAdvice` e il meccanismo dietro.**

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.debug("Richiesta fallita: {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return new ApiError(Instant.now(), HttpStatus.NOT_FOUND.value(),
                "RESOURCE_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }
    // ... handleBadRequest, handleValidation, handleGeneralServerError
}
```

- **Il flusso completo**: il service lancia → l'eccezione risale fino al `DispatcherServlet`, che la cattura → interroga in sequenza i suoi *exception resolver* → l'`ExceptionHandlerExceptionResolver` cerca tra i `@ControllerAdvice` registrati un metodo `@ExceptionHandler` per quel tipo → lo chiama passandogli l'eccezione → usa il ritorno come body. **Il metodo non lo invochi mai tu**, come `PostController.getPostById`: lì l'innesco è una richiesta, qui un'eccezione.
- **Chi lancia non sa niente di chi ascolta.** `PostService` continua a fare `throw` senza sapere che esiste un handler — è il punto: il service dice cosa è successo nel dominio, il layer web decide come tradurlo in HTTP. Se domani il service fosse chiamato da un job schedulato, le `throw` resterebbero identiche e nessun handler entrerebbe in gioco.
- **`@ResponseStatus` e non `ResponseEntity`**, stesso criterio del `DELETE` in 3.1c: lo status è **fisso per metodo**, perché ogni handler copre un tipo e a ogni tipo corrisponde uno status (che è stato il criterio per fare due classi). `ResponseEntity` serve quando lo status dipende da una condizione a runtime.
- **Errore commesso e utile**: al primo tentativo il `404` c'era solo dentro l'`ApiError`, senza `@ResponseStatus`. Risultato: **status line `200 OK` con body che dice `"status": 404`** — Spring risponde 200 di default a un metodo che termina normalmente, e l'handler termina normalmente. Quel numero nel record è un dato come gli altri, nessuno gli ha detto di essere anche lo status. Il frontend avrebbe fatto `if (res.ok)` e tirato dritto.
- **`HttpStatus.NOT_FOUND.value()` e non `404` scritto a mano**: lo status compare in due posti (annotazione e body) e potrebbero divergere per distrazione. Una costante sola toglie un modo di sbagliare.
- **`HttpServletRequest` nel `@ControllerAdvice` è al suo posto**, benché sia HTTP puro. Il criterio "certi tipi non attraversano i layer" vale, ma il `@ControllerAdvice` **è** il layer web — sta accanto ai controller e il suo unico lavoro è produrre risposte HTTP. Chiedergli di ignorare HTTP sarebbe come chiedere al controller di non conoscere i `@PathVariable`. Il rischio, dopo aver interiorizzato una regola, è applicarla ovunque.

**`MethodArgumentNotValidException`: sovrascrivere il default di Spring.**

Il buco lasciato aperto dalla 3.1: `@Valid` che fallisce dava un 400 già corretto ma **muto**, in un formato diverso da quello degli altri errori. Due forme di errore dalla stessa API.

- **Il proprio handler vince su quello di Spring.** Il `DispatcherServlet` interroga i resolver in ordine: l'`ExceptionHandlerExceptionResolver` (che guarda i `@ControllerAdvice`) viene **prima** del `DefaultHandlerExceptionResolver` (che produce il 400 muto). Il framework mette il suo comportamento come *fallback*, non come regola — vale per tutte le eccezioni standard, incluso `HttpMessageNotReadableException` se un giorno servisse.
- **`ex.getMessage()` qui è inutilizzabile**: restituisce un muro di testo con dentro la firma del metodo del controller e i `codes [NotNull.createPostDTO.categoryId,...]`. Il messaggio si costruisce da `ex.getBindingResult().getFieldErrors()`, dove ogni `FieldError` ha `getField()` e `getDefaultMessage()`.
- **Stringa concatenata e non struttura per campo** — decisione contro-intuitiva, quindi vale la pena il ragionamento. La struttura (`List<FieldError>` nel JSON) permetterebbe al frontend di evidenziare i singoli campi in rosso. Ma un frontend fatto bene **valida in locale** e non manda mai una richiesta che sa già invalida: quando questo body arriva davvero, siamo già in uno scenario anomalo (client non previsto, bug, divergenza tra le regole dei due lati), non nel flusso normale della form.
- **Da cui il criterio, che è il pezzo riutilizzabile**: frontend e backend validano per **scopi diversi**, non per ridondanza. Il frontend fa **UX** (feedback immediato, campo per campo, senza rete); il backend fa **correttezza** (blinda il dato, qualunque sia il client). È lo schema dei tre livelli di 2.2 esteso oltre il confine dell'applicazione. Non essendo il canale di feedback normale, la struttura per campo serve meno di quanto sembri — e resta additiva il giorno che serve.
- Conseguenza sul `code`: se tutte le violazioni finiscono in una stringa, il codice è generico (`"VALIDATION_ERROR"`), non per campo.

**Il catch-all su `Exception`, e il 500 che non parla.**

Con tre handler, un `NullPointerException` o una `DataAccessException` non sarebbero coperti: il client riceverebbe la pagina di errore di default di Spring, cioè una **quarta forma** dopo averne appena uniformate tre.

- **`Exception` e non `Throwable`.** La gerarchia è `Throwable` → (`Error`, `Exception`) → `RuntimeException` → le nostre. `Error` è il ramo dei problemi della JVM (`OutOfMemoryError`, `StackOverflowError`): condizioni da cui l'applicazione non si riprende, dove rispondere educatamente con un JSON non serve e rischia di mascherare il problema.
- **Non cattura le altre tre**: Spring sceglie l'handler **più specifico** che copre il tipo lanciato, come nell'overload resolution. Un handler su `ResourceNotFoundException` batte uno su `Exception`.
- **`ex.getMessage()` NON va nel body.** È la differenza sostanziale con gli altri tre: là il messaggio l'abbiamo **scritto noi**, per il client, sapendo cosa contiene. Qui è un artefatto interno generato dal JDK o da una libreria, e può contenere nomi di tabelle e colonne, frammenti di SQL, percorsi di file, nomi di classi. È *information disclosure*, materiale utile a chi sta studiando come attaccare. Ed è inutile al client, che non ha sbagliato niente e non può correggere niente.
- Da cui la coppia: **messaggio generico fisso nel body, tutto nel log** (messaggio *e* stack trace).

**SLF4J — decisioni di design prese (Fase 3.6):**

Il logging è diventato necessario **proprio per via della 3.3**: prima un'eccezione non gestita arrivava in fondo e Spring stampava lo stack trace in console. Ora il `@RestControllerAdvice` la cattura e restituisce un `ApiError` pulito — il client è contento, ma in console non compare più niente. Il tappo ha portato via anche la visibilità.

- **`@Slf4j` di Lombok** genera `private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);`. `static` perché il logger appartiene alla classe, non all'istanza. Il nome della classe passato a `getLogger` **finisce in ogni riga** — è così che sai da dove viene un messaggio ed è così che i livelli si configurano per package. Nome `log` minuscolo anche se `static final`, come previsto in 2.5b: è un collaboratore, non una costante di valore.
- **Il segnaposto `{}` e non la concatenazione.** `log.debug("Post {} non trovato", id)` compone la stringa **solo se quel livello è attivo**; con `+` la concatenazione viene valutata sempre, anche quando il messaggio non verrà mai stampato.
- **Il `Throwable` come ultimo argomento stampa lo stack trace**, ma solo se è **in eccesso** rispetto ai `{}`. Errore commesso: tre segnaposti e tre argomenti con `ex` in terza posizione → `ex` viene consumato come segnaposto normale e stampato con `toString()`, senza traccia. Tolto un `{}`, l'overload corretto scatta.

**La scelta dei livelli: `debug` sui 4xx, `error` sui 5xx.**

Il criterio non è "quanto è grave l'evento" ma **"chi deve svegliarsi"**. In produzione i livelli si filtrano e spesso si allertano: se un livello scatta un alert, deve significare che c'è qualcosa da fare.

`GET /posts/999` da un bot che scandaglia gli id: nessuno deve fare niente, il sistema ha funzionato — ha risposto 404, che è la risposta **corretta**. È un errore del *client*. Loggarlo a `warn` significa migliaia di warn al giorno che nessuno leggerà, e il warn che conta annegato in mezzo.

| categoria | livello | contenuto |
|---|---|---|
| errori del client (4xx) | `debug` | verbo, path, messaggio |
| errori del server (5xx) | `error` | verbo, path, **+ stack trace** |

`warn` resta per il territorio di mezzo: anomalo, non colpa del client, non una rottura (una chiamata esterna lenta, un fallback scattato). In sviluppo `debug` è comunque visibile, quindi non si perde niente adesso.

**La configurazione, e l'inciampo.**

```yaml
logging:
  level:
    com.simone.blog: DEBUG
```

- **`debug: true` non basta.** Quella proprietà attiva il DEBUG per un insieme selezionato di logger del *framework* (è ciò che produce il CONDITIONS EVALUATION REPORT). Il **root logger** resta a `INFO`, e `com.simone.blog` eredita da lì: `log.debug(...)` veniva chiamato, vedeva il livello spento e non stampava niente. Conferma pratica dell'utilità di `{}` — la stringa non veniva nemmeno composta.
- **I livelli si configurano per package**, gerarchicamente: quella riga vale per `com.simone.blog` e tutto ciò che sta sotto.
- **Inciampo YAML**: `logging` era stato messo *dentro* `spring`, producendo `spring.logging.level.*` — una proprietà inesistente, **ignorata in silenzio**. In YAML l'indentazione *è* la struttura: due spazi cambiano il significato della chiave, senza nessun errore all'avvio.

**Cosa lo stack trace del 500 ha rivelato (bonus non cercato):**

```
at ...TransactionInterceptor.invoke(...)
at ...CglibAopProxy$DynamicAdvisedInterceptor.intercept(...)
at com.simone.blog.service.PostService$$SpringCGLIB$$0.getPostById(<generated>)
at com.simone.blog.controller.PostController.getPostById(PostController.java:36)
```

Quel `PostService$$SpringCGLIB$$0` è **`@Transactional` visto in azione**: il controller non chiama il tuo `PostService`, chiama una sottoclasse generata a runtime che apre la transazione, delega al metodo vero e chiude al ritorno. Stesso meccanismo del dynamic proxy che implementa `PostRepository` (1.3).

Conseguenza pratica da ricordare: **`@Transactional` viene ignorato quando un metodo chiama un altro metodo della stessa classe**, perché quella chiamata non passa dal proxy. Trappola classica, meglio conoscerla prima di incontrarla.

**Test API (Fase 3.3–3.6) — verificato su Postman e sui log:**

| chiamata | prima | ora | log |
|---|---|---|---|
| `GET /posts/999` | 500 | **404** `RESOURCE_NOT_FOUND` | `DEBUG` |
| `POST /posts` con `categoryId: 99` | 500 | **400** `BAD_REQUEST` | `DEBUG` |
| `?sort=updatedAt` | 500 | **400** `BAD_REQUEST` | `DEBUG` |
| `?category=aurora` | 500 | **400** `BAD_REQUEST` | `DEBUG` |
| `title: ""` + `categoryId: null` | 400 **muto** | **400** `VALIDATION_ERROR`, due violazioni concatenate | `DEBUG` |
| errore imprevisto (`IllegalStateException` iniettata) | pagina Spring | **500** `INTERNAL_SERVER_ERROR`, messaggio generico | `ERROR` + stack trace |

Body tipo:

```json
{
  "timestamp": "2026-09-02T11:39:15.383519800Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "title: non deve essere spazio; categoryId: non deve essere null",
  "path": "/posts"
}
```

Nei log, la conferma del meccanismo:

```
Using @ExceptionHandler ...GlobalExceptionHandler#handleValidation(...)
c.s.b.exception.GlobalExceptionHandler : Richiesta fallita: POST /posts - title: ...
Completed 400 BAD_REQUEST
```

Da notare: la `RuntimeException` di `SortValidator` nasce nel **controller**, non nel service, ed è stata intercettata identicamente. Il `@ControllerAdvice` non guarda *da dove* arriva l'eccezione, guarda **di che tipo è**: qualunque punto del percorso di una richiesta va bene, purché nessuno la catturi prima.

**Debiti tecnici aperti (Fase 3.3–3.6):**
- **Messaggi di Bean Validation localizzati e brutti**: `"non deve essere spazio"` è la traduzione italiana di default di `@NotBlank`. Prima finiva solo nei log, ora va dritta al client — quindi è diventata visibile. Da sistemare con `message = "..."` esplicito sulle annotazioni. Cosmesi, ma cosmesi che il client vede
- **Duplicazione nei quattro handler**: la riga di log è identica in tre, e tutti e quattro costruiscono l'`ApiError` allo stesso modo cambiando solo status e code. Per il criterio di 3.2 (*"due pezzi che cambiano sempre insieme diventano uno"*) sarebbe da estrarre in un metodo privato. Non fatto perché con quattro handler il guadagno è marginale; da rivalutare quando arriveranno gli handler di Security (Fase 4), che porteranno 401 e 403
- **Il `code` non discrimina davvero**: `BAD_REQUEST` copre quattro situazioni diverse (categoria inesistente, categoria macro, slug che non matcha, sort non ammesso). È coerente con la scelta di non progettare il vocabolario adesso, ma il giorno che il frontend vorrà reagire diversamente a una di quelle, serviranno codici distinti — che è precisamente il momento in cui passare alla B
- **Nessun test automatico sugli handler**: verificato tutto a mano su Postman. Candidato naturale per la 5.4 (integration test), dove `@WebMvcTest` permette di asserire status e body senza avviare il DB

---

## Riepilogo

**Fase 3.1, 3.1b, 3.1c e 3.2 completate.** Il CRUD è completo (`GET`, `POST`, `PUT`, `DELETE`), i DTO in ingresso sono validati, e tutti i metodi del service sono transazionali.

| endpoint | novità |
|---|---|
| `POST /posts` | validazione DTO, regola "solo foglie assegnabili", `@Transactional` |
| `PUT /posts/{id}` | **nuovo** — `UpdatePostDTO`, `updatedAt` finalmente popolato, da 4 query a 2 |
| `DELETE /posts/{id}` | **nuovo** — hard delete, 204, 404 su id inesistente |
| `GET /posts`, `/posts/{id}` | `@Transactional(readOnly = true)` |

Concetti consolidati in Fase 3.1–3.2:
- I **tre pezzi** di Bean Validation: annotazioni (metadati inerti), validatore (Hibernate Validator), e `@Valid` come **innesco** — senza il terzo i primi due non fanno niente
- Sui record le annotazioni stanno **sui componenti**, dentro la parentesi
- `@NotNull` vs `@NotEmpty` vs `@NotBlank`, e che un vincolo applicato a un tipo sbagliato **impedisce l'avvio dell'applicazione**, non fallisce a runtime
- Che i vincoli di formato (`@Size`, `@URL`, `@Pattern`) **ignorano `null`**: è ciò che li rende adatti ai campi opzionali
- Che un `@Size` che rispecchia il DDL **non è ridondante**: sposta l'errore dal fondo dello stack (500 che parla di tipi SQL) al confine (400 che nomina il campo)
- `MethodArgumentNotValidException` è **nota a Spring MVC** → 400 automatico, a differenza dei `RuntimeException` nudi → 500
- Che Bean Validation **accumula** le violazioni invece di fermarsi alla prima, l'opposto della scelta fatta per `SortValidator`
- `PUT` vs `PATCH`: la differenza non è quanti campi mandi, ma **cosa succede a quelli che ometti** — e che `PATCH` non sa distinguere "campo assente" da `null` esplicito, il che rende `@NotBlank` inutilizzabile
- **L'identità della risorsa sta nell'URL, il contenuto nel body**: `id` fuori dal DTO chiude il disallineamento prima che esista
- Che **non esiste un `update()`** in `JpaRepository`: `save()` decide guardando l'id dell'entity — e che il mapper costruisce oggetti *nuovi* mentre il repository restituisce oggetti *esistenti*
- **`@Transactional` non è decorativo**: senza, un metodo di service è una sequenza di transazioni scollegate — entity detached, cache di primo livello inutile, `merge` al posto del dirty checking
- Entity **managed** vs **detached**, e il **dirty checking**: l'`UPDATE` parte al commit dal confronto con lo snapshot, senza rileggere la riga
- Che la cache di primo livello, che in 2.2 *mascherava* un N+1, dentro una transazione lavora a favore
- `readOnly = true` salta il dirty checking sulle letture
- Che `@Transactional` va nel **service**: nel controller resterebbe aperta durante la serializzazione, riaprendo ciò che `open-in-view: false` aveva chiuso
- Che **`parent_id` non dice se una categoria è una foglia**: dice se ha un *genitore*, che è la domanda opposta
- Il verbo `exists` nelle derived query, e il **rovesciamento** di `existsByParentId`: cerchi chi punta a te, non cosa punti tu
- Il criterio che decide se estrarre: **due pezzi con ragioni indipendenti di cambiare restano separati** (i due DTO), **due pezzi che cambiano sempre insieme diventano uno** (`resolveAssignableCategory`)
- Che un metodo estratto deve restituire **ciò che serve al punto di chiamata** (`Category`), non un verdetto (`boolean`) che costringe chi chiama a rifare il lavoro
- `DELETE`: 204 e non 200-con-messaggio (lo status code è già l'informazione), 404 e non idempotenza (il segnale serve al frontend per sapere che la sua vista è stale)

**Fase 3.3, 3.4, 3.5 e 3.6 completate — Fase 3 chiusa.** L'API non risponde più 500 a nessun errore prevedibile, e tutte le risposte di errore hanno la stessa forma.

| chiamata | prima | ora |
|---|---|---|
| `GET /posts/999` | 500 | **404** con `ApiError` |
| `categoryId: 99` | 500 | **400** |
| `?sort=updatedAt` | 500 | **400** |
| `?category=aurora` | 500 | **400** |
| `title: ""` | 400 **muto** | **400** con i campi violati |
| errore imprevisto | pagina di Spring | **500** generico, stack trace nei log |

Cinque forme di risposta diverse ridotte a una.

Concetti consolidati in Fase 3.3–3.6:
- Un'eccezione **è un oggetto**, quindi ha una classe — e con `RuntimeException` nuda il *tipo* non porta nessuna informazione, solo il messaggio. Tipizzarla non "gestisce" niente: sposta l'informazione dal testo al tipo, dove qualcuno può discriminarla
- **Il criterio di raggruppamento delle eccezioni è lo status che devono produrre**, non la natura del fatto: "post inesistente" e "categoria inesistente" sono lo stesso tipo di evento ma due risposte HTTP diverse, e l'handler decide guardando il tipo
- **Unchecked e non checked**: le checked hanno senso solo se il chiamante immediato può *fare* qualcosa; qui l'eccezione deve attraversare il controller. E `@Transactional` fa rollback **solo** sulle unchecked
- Che **i costruttori non si ereditano**, a differenza di metodi e campi
- Il flusso del `@RestControllerAdvice`: `DispatcherServlet` cattura → interroga i resolver in ordine → l'`ExceptionHandlerExceptionResolver` cerca un `@ExceptionHandler` per quel tipo. **Chi lancia non sa niente di chi ascolta**
- Che il proprio handler **vince sul default di Spring**, perché il framework mette il suo comportamento come fallback (ultimo resolver), non come regola
- Che **lo status nel body non è lo status della risposta**: senza `@ResponseStatus` l'handler termina normalmente e Spring risponde 200, con dentro un JSON che dice 404
- Che Spring sceglie l'handler **più specifico**, come nell'overload resolution — quindi un catch-all su `Exception` non ruba il lavoro agli altri
- `Throwable` → (`Error`, `Exception`) → `RuntimeException`: e perché il catch-all si ferma a `Exception`, lasciando fuori il ramo da cui la JVM non si riprende
- Che il messaggio di un'eccezione **imprevista** non va al client: *information disclosure* (nomi di tabelle, SQL, path) e comunque inutile a chi non ha sbagliato niente. Messaggio generico nel body, tutto nel log
- Che un tipo "di layer" come `HttpServletRequest` **è al suo posto** nel `@ControllerAdvice`: il rischio, dopo aver interiorizzato una regola sui confini, è applicarla anche dove il confine non c'è
- **Frontend e backend validano per scopi diversi, non per ridondanza**: il primo fa UX (feedback immediato, senza rete), il secondo correttezza (blinda il dato per qualunque client). Estensione dello schema a tre livelli di 2.2 oltre il confine dell'applicazione
- Che la scelta del **livello di log** risponde a "chi deve svegliarsi", non a "quanto è grave": un 404 è la risposta *corretta* a una richiesta sbagliata del client, e loggarlo a `warn` annega i warn che contano
- Il segnaposto `{}` che compone la stringa **solo se il livello è attivo**, e il `Throwable` che stampa lo stack trace solo se è **in eccesso** rispetto ai segnaposti
- Che `debug: true` **non** abbassa il livello dei propri logger: i livelli si configurano per package, gerarchicamente
- Che in YAML **l'indentazione è la struttura**: una chiave annidata nel posto sbagliato diventa una proprietà inesistente, ignorata senza nessun errore
- Che `@Transactional` funziona via **proxy generato** (`PostService$$SpringCGLIB$$0` visibile nello stack trace) — da cui il fatto che una chiamata interna alla stessa classe **non** passa dal proxy e ignora l'annotazione

Il seguito, insieme alle insidie che la Fase 3 lascia in eredità a Security, sta in [Fase 4](fase-4-security.md).

---

[← Indice](../README.md)

# Fase 4 — Security & Autenticazione

> Obiettivo: JWT authentication, autorizzazione.
> Parte del [diario di sviluppo](../README.md) del blog backend.
> **Stato: non iniziata.** Questo file contiene il piano e le decisioni prese in anticipo.

## Voci

- [ ] **4.1** Entity `User` (id, email, password, role)
- [ ] **4.2** Service `AuthService` con password encoding (BCrypt)
- [ ] **4.3** JWT token generation e validation
- [~] **4.4** `SecurityConfig` con filter chain — versione **minimale già presente** (anticipata in Fase 1.7 per sbloccare i POST): csrf disabilitato, `anyRequest().authenticated()`, Basic auth. Da estendere qui con JWT e regole per endpoint
- [ ] **4.5** Endpoint `/auth/register`, `/auth/login`
- [ ] **4.6** `@PostAuthor` custom annotation per posts
- [ ] **4.7** Author field in Post (chi ha scritto il post)
- [ ] **4.8** Refresh token — aggiunto al piano, vedi decisione sotto

**Concetti:**
- Spring Security basics
- JWT authentication
- Password encoding
- Custom security filters
- Role-based access

## Ordine di esecuzione

Non è quello numerato. La sequenza sensata è:

1. **`User` (4.1)** — modello dati puro, come `Post` e `Category`
2. **Password encoding (4.2)** — `PasswordEncoder` come bean, BCrypt. La password in chiaro non tocca mai il DB
3. **`/auth/register` e `/auth/login` (4.5)** — a questo stadio il login può rispondere con qualcosa di semplice, il token viene dopo
4. **JWT (4.3)** — generazione, firma, claim, scadenza
5. **`SecurityConfig` esteso (4.4)** — il filtro che legge l'header `Authorization`, valida il token e popola il `SecurityContext`, più le regole per endpoint
6. **`author` su `Post` (4.7)** — `@ManyToOne`, ma l'id non arriva dal body: si prende dall'utente autenticato
7. **`@PostAuthor` (4.6)** — autorizzazione a livello di risorsa: non "sei autenticato?" ma "sei l'autore *di questo* post?"

---

## Decisioni prese in anticipo

Prese a fine Fase 3, prima di scrivere codice, perché condizionano il modello dati.

**Ruoli: `enum`, un ruolo per utente.**

Insieme chiuso e piccolo (`USER`, `ADMIN`), verificato dal compilatore, nessuna tabella in più. È il caso opposto a quello di [2.1](fase-2-database.md): lì la categoria aveva identità e attributi propri e serviva la tabella, qui il ruolo è un valore nudo — esattamente la riga *"il caso opposto si risolve con un `enum`, non con una tabella"* scritta allora.

**Endpoint pubblici: le `GET` di post e categorie.**

Tutto il resto autenticato. È il modello di qualunque blog: contenuto leggibile da chiunque (anche dai crawler), scrittura riservata. `/auth/register` e `/auth/login` devono essere pubblici per forza, altrimenti servirebbe un token per ottenere un token.

Conseguenze da tenere presenti:
- La regola in `SecurityConfig` può essere espressa sul **verbo** (`GET` su `/posts/**` e `/categories/**` permesse) proprio perché in [1.5](fase-1-fondamentali.md) si è rifiutato `/posts/create`: la risorsa è l'URL e l'operazione è il verbo. Un ipotetico `POST /posts/search` romperebbe questa simmetria.
- Paginazione, `?category=` e `?sort=` diventano tutti pubblici di conseguenza — sono query param su una `GET`. L'allowlist di `SortValidator` (2.5b) è ora l'unica difesa su quel canale, e lo è per utenti anonimi.

**Refresh token: sì, non solo access token.**

Il nodo è che **un JWT non si può revocare**: il server non tiene traccia di quali ha emesso, quindi un token rubato resta valido fino alla scadenza e un logout non ha niente da cancellare. Da qui la pressione a fare scadenze corte, che però buttano fuori l'utente ogni quarto d'ora.

Il refresh token scioglie la tensione: access token corto, refresh a vita lunga che serve solo a ottenerne uno nuovo. Costo: raddoppia il lavoro della fase e apre la questione di dove il client conserva i token (cookie `httpOnly` vs `localStorage`).

Scelto comunque perché il logout vero senza non si fa, ed è lo standard che vale la pena imparare.

**Nodo da sciogliere quando ci si arriva:** il refresh token va conservato **in DB**, altrimenti resta irrevocabile come l'altro e non si è guadagnato niente. Il che significa una tabella in più, e la domanda "cosa succede al login da due dispositivi": una riga per utente (il secondo login butta fuori il primo) o una riga per sessione.

---

## Note tecniche da tenere presenti

**Le eccezioni di Spring Security non passano dal `@RestControllerAdvice`.**

Nascono nella **filter chain**, cioè *prima* del `DispatcherServlet` — e il `@ControllerAdvice` viene interrogato solo da quest'ultimo. Quindi gli handler scritti in [3.3](fase-3-validation-errori.md) non le vedranno.

Serviranno un `AuthenticationEntryPoint` (401) e un `AccessDeniedHandler` (403) che producano lo stesso `ApiError`, altrimenti l'API torna ad avere due formati di errore subito dopo averli unificati.

**CORS e JWT si configurano nello stesso posto.** La scelta tra "frontend su porta diversa" e "reverse proxy davanti" decide se la configurazione CORS serve davvero.

**Gli handler nuovi (401, 403) si aggiungono al `@RestControllerAdvice` esistente** per la parte che passa dal `DispatcherServlet`. È anche il momento buono per rivalutare il debito sulla duplicazione dei quattro handler: con sei, estrarre un metodo privato inizia a pagare.

---

## `POST /categories`, rimandato qui

Serve: oggi le categorie si inseriscono a mano da pgAdmin, e con la gerarchia è peggio — creare una sottocategoria richiede di conoscere l'`id` del genitore, quindi due `INSERT` e un giro a leggere il risultato del primo.

Vivrà dietro una pagina admin autenticata, quindi vuole sia la validazione della 3.1 sia le regole per ruolo di questa fase: scriverlo prima significherebbe scriverlo senza il pezzo che più lo caratterizza.

È anche il posto dove torna il candidato originale della 3.2: lo **slug unique** come vero `ConstraintValidator`, che è validazione di dominio su un DTO in ingresso — cosa che oggi `Category` non ha affatto.

---

[← Indice](../README.md)

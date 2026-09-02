# Fase 5 — Miglioramenti & Polish

> Obiettivo: preproduzione ready.
> Parte del [diario di sviluppo](../README.md) del blog backend.
> **Stato: non iniziata.**

## Voci

- [ ] **5.1** Audit fields (`created_by`, `updated_by`, `created_at`, `updated_at`)
- [ ] **5.2** Soft delete per `Post`
- [ ] **5.3** Unit test per Service
- [ ] **5.4** Integration test per Controller
- [ ] **5.5** API documentation (Springdoc OpenAPI/Swagger)
- [ ] **5.6** Docker setup (PostgreSQL)
- [ ] **5.7** CI/CD basic (GitHub Actions)

## Cosa confluisce qui dalle fasi precedenti

**5.2 — soft delete.** Deciso in [3.1c](fase-3-validation-errori.md) di fare hard delete adesso: il contratto HTTP è identico nei due casi (`DELETE /posts/{id}` → 204), quindi il cambio sarà tre righe dentro il service.

Piano: **recupero entro 30 giorni, poi cancellazione definitiva** — il che richiede anche un job schedulato (`@Scheduled`) per la pulizia, altrimenti la tabella cresce per sempre.

Le tre insidie già identificate: ogni query deve ricordarsi `WHERE deleted = false` e dimenticarlo in un punto solo fa riapparire i cancellati; le `unique` smettono di comportarsi come ci si aspetta (uno slug occupato da una riga cancellata blocca il riuso); e "cancella i miei dati" del GDPR non si soddisfa con un flag.

**5.4 — integration test.** Copre il debito lasciato aperto in 3.3: tutti gli handler di errore sono stati verificati a mano su Postman e nessuno è coperto da test. `@WebMvcTest` permette di asserire status e body senza avviare il DB.

**5.5 — Swagger.** È anche il posto giusto per l'allowlist dei campi ordinabili (2.5b), che oggi non compare in nessuna risposta: l'elenco dei campi ammessi è documentazione, non testo d'errore.

**5.6 — Docker, che sblocca Flyway (2.6).** Le migrazioni sono state rimandate proprio a qui: introdurle su un DB già popolato richiede la *baseline* (dire a Flyway di considerare lo stato attuale come già migrato), che è il caso particolare; su un container Postgres vuoto girano da zero in ordine, che è il flusso normale e insegna meglio il concetto centrale.

Piano: `docker compose` con **un container per processo** (backend, Postgres, frontend), non tutto in uno. Il Postgres containerizzato risolve anche il problema delle due installazioni locali che si contendono la porta.

---

## Dopo il backend

Frontend React su bucket Cloudflare R2, in programmazione assistita.

È il momento in cui diverse decisioni rimandate diventano decidibili, perché esisterà finalmente un client vero:
- il **colore/hex sulla categoria** per i badge — additivo, una colonna, e si capisce se serve davvero solo costruendo i badge
- il passaggio alla **variante B** dell'error handling (dati strutturati nell'eccezione), quando si vedrà a quali errori il frontend vuole reagire in modo diverso
- la **struttura per campo** nelle violazioni di validazione, se mai servirà

---

[← Indice](../README.md)

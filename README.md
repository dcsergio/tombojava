# Tombojava Tombola Web

Applicazione Spring Boot web con interfaccia Thymeleaf per generare cartelle della tombola in background.

## Funzionalita principali
- Home page con pulsante **Genera cartelle**.
- Popup con parametri: numero serie, seed (opzionale), tempo massimo di attesa (secondi).
- Job asincrono con barra di progresso aggiornata via polling.
- Download del PDF finale con nome: `<seed>-<nr-serie>.pdf`.
- Tabellone di gioco con 90 numeri cliccabili per segnare le estrazioni.
- Verifica serie: inserisci numero serie e visualizza le 6 cartelle con i numeri estratti evidenziati.

## Avvio (Windows PowerShell)
```powershell
Set-Location "C:\Users\sergi\workspace\tombojava"
.\gradlew.bat bootRun
```

Apri poi `http://localhost:8080`.

## API usate dalla UI
- `POST /api/jobs` avvia un job.
- `GET /api/jobs/{jobId}` restituisce stato/progresso.
- `GET /api/jobs/{jobId}/download` scarica il PDF quando il job e' completato.
- `POST /api/jobs/{jobId}/verify` verifica una serie con body JSON:
  - `seriesNumber`: numero serie (1..N)
  - `extractedNumbers`: array di numeri estratti (1..90)

## Configurazione
In `src/main/resources/application.yaml`:
- `tombojava.cli.enabled`: `false` di default (modalita web).
- `tombojava.output-dir`: directory di output PDF (default `out`).
- `tombojava.max-series-attempts-default`: tentativi massimi per serie (default `5000`).

## Modalita CLI (opzionale)
Il runner CLI e' ancora disponibile, ma disabilitato di default. Per abilitarlo:
```powershell
.\gradlew.bat bootRun --args="--tombojava.cli.enabled=true --output=out/tombojava.pdf --series=2 --seed=123456789 --max-series-attempts=8000"
```

## Test
```powershell
Set-Location "C:\Users\sergi\workspace\tombojava"
.\gradlew.bat test
```

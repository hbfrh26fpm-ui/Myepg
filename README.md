# MYEPG Player

MYEPG Player è un player Android TV originale con supporto playlist M3U/M3U8 ed EPG XMLTV da URL o file locale, inclusi file XML compressi GZIP (`.xml.gz`/`.gz`).

## Funzioni principali

- Playlist M3U/M3U8 da URL o file
- EPG XMLTV da URL o file `.xml`, `.xml.gz`, `.gz`
- Più sorgenti EPG
- Decompressione GZIP automatica
- Associazione EPG tramite `tvg-id` e fallback sul nome canale
- Ricerca canali
- Preferiti
- Riproduzione video
- Interfaccia landscape pensata per Android TV e telecomando

## Build

Il workflow GitHub Actions in `.github/workflows/android.yml` compila automaticamente un APK debug ad ogni push su `main` e lo pubblica come artifact `MYEPG-Player-debug`.

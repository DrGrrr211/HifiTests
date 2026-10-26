# HiFi Tests

Android-App, die Testberichte aus dem HiFi-Bereich sammelt: neue Tests, Suche mit Filtern, Bestenlisten und eigene Readly-Einträge.

## So funktioniert es

| Teil | Was es tut |
|---|---|
| `collector/collect.py` | Läuft zweimal täglich per GitHub Actions. Liest die RSS-Feeds aus `collector/sources.json`, erkennt Testberichte und liest Kategorie, Marke, Preis, Watt, Technologie und Wertung heraus. Schreibt `data/reviews.json` und `data/sources_status.json`. |
| `app/` | Kotlin/Compose-App. Lädt `data/reviews.json` direkt aus diesem Repo. |
| `.github/workflows/build-apk.yml` | Baut bei jeder App-Änderung die APK und stellt sie unter **Releases → neueste** bereit. |

Gespeichert werden nur Titel, Link, Quelle, ein kurzer Anreißer und Eckdaten – keine kompletten Artikel.

## Installation

1. **Releases → neueste → HifiTests.apk** auf dem Handy herunterladen und öffnen.
2. „Installation aus unbekannten Quellen“ für den Browser erlauben.
3. Updates genauso installieren – sie ersetzen die alte Version, eigene Einträge bleiben.

## Reiter

- **Neu** – alle Tests, neueste zuerst. ⓘ zeigt, welche Quellen beim letzten Lauf erreichbar waren.
- **Suche** – Freitext plus Filter: Kategorie, Marke, Preis von/bis, Mindest-Watt, Technologie (Bändchen, Planar, Elektrostat …), Mindestwertung, Quelle, Zeitraum, Sortierung.
- **Bestenlisten** – je Kategorie, sortiert nach dem Mittelwert aller Wertungen (umgerechnet auf 0–100). Tippen zeigt die einzelnen Tests.
- **Readly** – eigene Einträge aus STEREO, AUDIO, stereoplay usw. mit Ausgabe, Seite und Wertung. „In Readly öffnen“ startet die Readly-App und legt Magazin + Ausgabe in die Zwischenablage. Menü ⋮: Sicherung teilen / importieren.

Benachrichtigung: Die App prüft etwa alle 12 Stunden auf neue Tests.

## Quellen anpassen

`collector/sources.json` bearbeiten. Pro Quelle können mehrere Feed-Adressen stehen; die erste funktionierende wird genutzt. Unter **Actions → Tests sammeln → Run workflow** sofort einen Lauf starten.

## Grenzen

- Readly hat keine öffentliche Schnittstelle. Heft-Tests werden deshalb von Hand eingetragen.
- Viele Online-Portale nennen keine Punktzahl. Solche Tests erscheinen in Suche und „Neu“, aber nur bewertete Tests kommen in die Bestenlisten.
- Preise in $ oder £ werden grob in € umgerechnet („ca.“).
- Das Repo muss **öffentlich** sein, damit die App die Daten ohne Anmeldung laden kann.

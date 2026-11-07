# LightTimeClouds

[English](README.md) | **Deutsch**

Android-Homescreen-Widget: eine analoge Sonnen- und Monduhr, inspiriert von [sunclock.net](https://sunclock.net).

Ein Ring um ein gewöhnliches Zifferblatt zeigt den Tagesverlauf von Nacht über Dämmerung bis Tag am aktuellen Standort, ergänzt um einen Mond-Marker mit aktueller Phase. Anders als die 24h-Vorlage nutzt LightTimeClouds standardmäßig ein normales **12-Stunden-Zifferblatt** (optional auch 24h).

## Features

- Analoges 12h- oder 24h-Zifferblatt, minutengenau, ohne Sekundenzeiger, optional mit Minutenstrichen
- Tag/Dämmerung/Nacht-Ring nach aktuellem Sonnenstand am konfigurierten Standort, wahlweise als „Himmel“ (Standard) oder in festen Phasenfarben. „Himmel“ zeigt in jedem Ringabschnitt den Himmel vom Horizont nach oben (Horizont innen oder außen einstellbar): Tag, Morgen-/Abendrot und das Blau der Dämmerung aus einem physikalischen Streuungsmodell (spektral, mit Mehrfachstreuung und Dunst aus gemessenen Aerosolen), so wie das menschliche Auge ihn sieht: an das Licht angepasst, mit der Blauverschiebung und den verblassenden Farben bei wenig Licht, bis zum schwachen Eigenleuchten des Nachthimmels
- Sonne und Mond (mit Phase) im Ring zur aktuellen Uhrzeit, so weit vom Horizontrand entfernt, wie sie über dem Horizont stehen; die Sonne als kleine Scheibe in ihrer wahren Größe entlang des Rings, in der Farbe, die sie durch die Atmosphäre hat, mit ihrer Aureole; der Mond ebenso in wahrer Größe, nur sein beleuchteter Teil, am Taghimmel blass und am Horizont orange bis rot, nachts mit einem Hof, wobei er den Himmel im Ring je nach Phase aufhellt; höher als dargestellt als kleiner Punkt am Rand gegenüber dem Horizont; unter dem Horizont gar nicht. Optional die Bahn des Mondes über den ganzen angezeigten Zeitraum als dünne graue Linie. Wie hoch über dem Horizont der äußere Rand reicht (10°–80°), ist einstellbar; der Himmelsverlauf passt sich an
- Konfigurierbare Umschalt-Strategie für den Ring: feste Uhrzeiten (12h: zwei, Standard 6 und 18 Uhr; 24h: eine, Standard Mitternacht) oder mitlaufend, sodass der Zeiger immer am Anfang des gezeigten Zeitraums steht. Der Ring kann auf seinen letzten 0°–30° (Standard 15°) durchsichtig auslaufen, damit sein Ende nicht mit der Zeit direkt nach seinem Anfang verwechselt wird
- Optionale Markierungen für Sonnenmittag/-mitternacht und Sonnenauf-/untergang
- Optional ein roter Stundenstrich beim nächsten Wecker, sobald er im angezeigten Zeitraum liegt (Android gibt nur den nächsten Wecker preis); folgt sofort jeder Änderung, beim Schlummern also der neuen Weckzeit, sofern die Wecker-App sie meldet
- Mond mit Phase, wahlweise am Stundenzeiger (Bahn schneidet den Horizontkreis zu den echten Auf- und Untergangszeiten) oder als eigener Zeiger, der dem Stundenzeiger um den Abstand des Mondes zur Sonne nachläuft; Abstand zur Mitte zeigt die Mondhöhe, dazu Tagesbahn des Mondes mit den Himmelsrichtungen, die er durchläuft, und Horizontkreis (ausblendbar)
- Tab „Vorschau“ in der App: das Widget mit den aktuellen, auch noch nicht gespeicherten Einstellungen; Schieberegler für Breiten- und Längengrad, Datum im Jahresverlauf und Uhrzeit verändern nur die Vorschau
- Tab „Zeiten“ in der App: alle Sonnenereignisse des Sonnentags, Mondphase, Mondauf- und -untergang
- Gespeicherte Orte mit Name, Koordinaten und Zeitzone; jedes Widget zeigt die Ortszeit (Weltuhr mit mehreren Widgets). Nur manuelle Eingabe, kein GPS, keine Standort-Berechtigung. Koordinaten behält die App mit höchstens drei Nachkommastellen, also auf etwa 100 m genau. Netzwerkzugriff nur für den optionalen Wetter-Ring
- Antippen öffnet wahlweise die System-Uhr-App oder die App selbst
- Eigenes Launcher-Icon für die Konfiguration, unabhängig vom Widget
- Frei skalierbares, quadratisches Widget (Standard 2×2), mehrere Instanzen mit eigenen Einstellungen möglich
- Farbmodi Hell/Dunkel/Automatisch (folgt dem System-Dunkelmodus); Ziffern und Zeiger in Material-You-Akzentfarben
- Optionaler Wetter-Ring um den Tag/Nacht-Ring herum (Standard) oder direkt innerhalb davon: Bewölkung als Grauton (bei klarem Himmel durchsichtig), darüber Niederschlag in Blau (Schnee violett) – je dunkler, desto mehr; je deckender, desto wahrscheinlicher. Vergangene Stunden abgeschwächt. Vorhersage von Open-Meteo, etwa alle zwei Stunden im Hintergrund geladen (nicht bei niedrigem Akkustand), nur wenn ein Widget den Ring nutzt; dafür werden die Koordinaten des Orts an open-meteo.com übertragen
- Luft- (Standard) oder gefühlte Temperatur (Mittelwert je Abschnitt) als Skala um den äußersten Ring (Standard) oder zwischen den Stundenziffern, oder keine
- „Wetter in Worten“ im Tab „Wetter“ und optional links, rechts oder unter der Uhr: Vergleich mit gestern um dieselbe Zeit, eine deutlich abweichende gefühlte Temperatur und Regen oder Schnee in den nächsten 3 bzw. 6 Stunden, nach festen Regeln aus der Vorhersage

## Projektstruktur

```
core/   – reine Kotlin/JVM-Logik ohne Android-Abhängigkeit: Sonnen-/Mondberechnung, Zifferblatt-Zeitmapping, Wettervorhersage, Himmelsfarben (inkl. Unit-Tests)
app/    – Android-Modul: Widget, Konfigurations-App (Jetpack Compose), Persistenz, Rendering
```

## Bauen & Testen

Voraussetzungen:

- **JDK 17–27** (z. B. Arch: `jdk-openjdk`). Gradle selbst lädt der Wrapper (`./gradlew`) herunter.
- **Android SDK** mit `platforms;android-<compileSdk>` und `build-tools` (Versionen siehe `app/build.gradle.kts` bzw. AGP-Default). Android Studio ist nicht nötig; die Command-line-Tools genügen, z. B.:

  ```sh
  android --sdk=$HOME/Android/Sdk sdk install platforms/android-37.0 build-tools/36.0.0
  echo "sdk.dir=$HOME/Android/Sdk" > local.properties
  ```

  Ohne `local.properties` nimmt Gradle `ANDROID_HOME`; manche Paketierungen setzen das systemweit auf einen anderen Pfad.

```sh
./gradlew :core:test        # Unit-Tests der Astronomie-, Zeit-, Wetter- und Himmelslogik
./gradlew :app:testDebugUnitTest  # Renderer-Tests; rendert Vorschaubilder nach app/build/previews/<Gruppe>/ (Robolectric)
./gradlew assembleDebug     # APK unter app/build/outputs/apk/debug/
./gradlew installDebug      # direkt auf ein per adb verbundenes Gerät
```

Die Himmelsfarben werden beim Build aus `core/.../sky/SkyModel.kt` als Tabelle vorberechnet (Gradle-Task `:core:generateSkyTable`); im Repo liegt keine Binärdatei.

Zwei Python-Skripte unter `tools/` (Ausführung mit [uv](https://docs.astral.sh/uv/), Abhängigkeiten stehen im Skript) gehören zur Astronomie; der App-Build braucht sie nicht:

```sh
uv run tools/astro_series.py        # kürzt ELP 2000-82B und VSOP87 neu nach core/.../astro/AstroSeries.kt
uv run tools/reference_ephemeris.py # Referenzwerte aus JPL DE440s für die Tests (core/src/test/resources/astro/)
```

Beide laden ihre Rohdaten (IMCCE-Reihen, ~32 MB Ephemeride) in `~/.cache/lighttimeclouds/`, nicht ins Repo.

Zum manuellen Testen: Widget auf den Homescreen ziehen, über das Launcher-Icon „LightTimeClouds“ konfigurieren; Logs per `adb logcat`.

### Übersetzungen

Alle UI-Texte liegen in `app/src/main/res/values/strings.xml` (Englisch, zugleich Fallback für alle anderen Sprachen) und `values-de/strings.xml` (Deutsch). Für eine weitere Sprache `values-de/strings.xml` nach `values-<Sprachcode>/` kopieren und übersetzen; `./gradlew :app:lintDebug` meldet fehlende Einträge (`MissingTranslation`). Datum, Uhrzeit und Zahlen formatiert die App selbst passend zur Sprache. Ab Android 13 lässt sich die App-Sprache in den Systemeinstellungen getrennt vom Gerät wählen.

## Credits

Die Sonnen- und Mondberechnung (`core/.../astro/`) beruht direkt auf den Primärquellen: der Mond auf der Mondtheorie ELP 2000-82B (M. Chapront-Touzé, J. Chapront, A&A 124, 50, 1983 und A&A 190, 342, 1988), die Sonne auf der Planetentheorie VSOP87 (P. Bretagnon, G. Francou, A&A 202, 309, 1988), beide in der Fassung des [IMCCE](https://www.imcce.fr/) (Observatoire de Paris) und für die App gekürzt (zwischen 2000 und 2050 höchstens 4″ Fehler beim Mond, 2″ bei der Sonne); Nutation, Schiefe der Ekliptik und Sternzeit folgen den IERS Conventions 2010 (IERS Technical Note 36), die Refraktion Sæmundsson (1986). Geprüft wird gegen die JPL-Ephemeride DE440s. Angeregt wurden Umfang und Auswahl der Sonnenereignisse von [SunCalc](https://github.com/mourner/suncalc) von Volodymyr Agafonkin, das frühere Versionen der App portiert hatten.

Die Himmelsfarben des Rings (Darstellung „Himmel“) berechnet `core/.../sky/SkyModel.kt`, eine Portierung von [Horizon](https://github.com/dnlzro/horizon) von Daniel Lazaro (MIT-Lizenz), das auf [„Production Sky Rendering“](https://www.shadertoy.com/view/slSXRW) von Andrew Helmer (MIT laut Autor) und dem Artikel von Sébastien Hillaire (2020) beruht, erweitert um Mehrfachstreuung nach Hillaire (2020), eine spektrale Rechnung mit Sonnenspektrum und Ozon-Absorptionsquerschnitten aus [Precomputed Atmospheric Scattering](https://github.com/ebruneton/precomputed_atmospheric_scattering) von Eric Bruneton (BSD-3-Clause), Aerosole aus der Mie-Theorie (Bohren und Huffman) mit einer von AERONET gemessenen Größenverteilung (Dubovik et al. 2002) und ein Modell des menschlichen Sehens (Helligkeit nach Tumblin, Hodgins und Guenter 1999, der Farbbeitrag der Stäbchen nach Cao et al. 2008 und Wanat und Mantiuk 2014); Lizenztexte in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

Die Wetterdaten stammen von [Open-Meteo.com](https://open-meteo.com/) (Lizenz CC BY 4.0).

Alle Formeln, Konstanten und Vereinfachungen der Astronomie, des Himmelsmodells und der Wetterauswertung, mit Quellen, beschreibt [`docs/PHYSIK.md`](docs/PHYSIK.md) (englisch: [`docs/PHYSICS.md`](docs/PHYSICS.md)); wie aus dem Licht des Himmels die Farben werden, die das Auge sieht, [`docs/PHYSIOLOGIE.md`](docs/PHYSIOLOGIE.md) (englisch: [`docs/PHYSIOLOGY.md`](docs/PHYSIOLOGY.md)).

## Lizenz

LightTimeClouds steht unter der [MIT-Lizenz](LICENSE.md). Portierte oder übernommene Teile fremder Projekte (Horizon, Precomputed Atmospheric Scattering) behalten ihre eigenen Lizenzen, siehe [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

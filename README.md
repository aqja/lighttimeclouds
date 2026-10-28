# LightTimeClouds

Android-Homescreen-Widget: eine analoge Sonnen- und Monduhr, inspiriert von [sunclock.net](https://sunclock.net).

Ein Ring um ein gewöhnliches Zifferblatt zeigt den Tagesverlauf von Nacht über Dämmerung bis Tag am aktuellen Standort, ergänzt um einen Mond-Marker mit aktueller Phase. Anders als die 24h-Vorlage nutzt LightTimeClouds standardmäßig ein normales **12-Stunden-Zifferblatt** (optional auch 24h).

## Features

- Analoges 12h- oder 24h-Zifferblatt, minutengenau, ohne Sekundenzeiger, optional mit Minutenstrichen
- Tag/Dämmerung/Nacht-Ring nach aktuellem Sonnenstand am konfigurierten Standort, wahlweise als „Himmel“ (Standard), Verlauf oder in festen Phasenfarben. „Himmel“ zeigt in jedem Ringabschnitt den Himmel vom Horizont nach oben (Horizont innen oder außen einstellbar): Tag und Morgen-/Abendrot aus einem physikalischen Streuungsmodell, dazu die blaue Stunde und das Nachtlila der Dämmerung
- Konfigurierbare Umschalt-Strategie für den Ring im 12h-Modus: feste Uhrzeiten oder kontinuierlich hinter dem Zeiger herlaufend
- Optionale Markierungen für Sonnenmittag/-mitternacht und Sonnenauf-/untergang
- Optional ein roter Stundenstrich beim nächsten Wecker, sobald er im angezeigten Zeitraum liegt (Android gibt nur den nächsten Wecker preis); folgt sofort jeder Änderung, beim Schlummern also der neuen Weckzeit, sofern die Wecker-App sie meldet
- Mondzeiger mit Phase; Abstand zur Mitte zeigt die Mondhöhe, dazu Tagesbahn des Mondes und Horizontkreis (ausblendbar)
- Tab „Zeiten“ in der App: alle Sonnenereignisse des Sonnentags, Mondphase, Mondauf- und -untergang
- Gespeicherte Orte mit Name, Koordinaten und Zeitzone; jedes Widget zeigt die Ortszeit (Weltuhr mit mehreren Widgets). Nur manuelle Eingabe, kein GPS, keine Standort-Berechtigung. Netzwerkzugriff nur für den optionalen Wetter-Ring
- Antippen öffnet wahlweise die System-Uhr-App oder die App selbst
- Eigenes Launcher-Icon für die Konfiguration, unabhängig vom Widget
- Frei skalierbares, quadratisches Widget (Standard 2×2), mehrere Instanzen mit eigenen Einstellungen möglich
- Farbmodi Hell/Dunkel/Automatisch (folgt dem System-Dunkelmodus); Ziffern und Zeiger in Material-You-Akzentfarben
- Optionaler Wetter-Ring innerhalb des Tag/Nacht-Rings oder ganz außen: Bewölkung als Grauton (bei klarem Himmel durchsichtig), darüber Niederschlag in Blau (Schnee violett) – je dunkler, desto mehr; je deckender, desto wahrscheinlicher. Vergangene Stunden abgeschwächt. Vorhersage von Open-Meteo, etwa alle zwei Stunden im Hintergrund geladen (nicht bei niedrigem Akkustand), nur wenn ein Widget den Ring nutzt; dafür werden die Koordinaten des Orts an open-meteo.com übertragen
- Optional Luft- oder gefühlte Temperatur (Mittelwert je Abschnitt) zwischen den Stundenziffern oder als Skala um den äußersten Ring
- Geplant: genauere Astronomie nach Meeus

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
./gradlew :app:testDebugUnitTest  # Renderer-Tests; rendert Vorschaubilder nach app/build/previews/ (Robolectric)
./gradlew assembleDebug     # APK unter app/build/outputs/apk/debug/
./gradlew installDebug      # direkt auf ein per adb verbundenes Gerät
```

Die Himmelsfarben werden beim Build aus `core/.../sky/SkyModel.kt` als Tabelle vorberechnet (Gradle-Task `:core:generateSkyTable`); im Repo liegt keine Binärdatei.

Zum manuellen Testen: Widget auf den Homescreen ziehen, über das Launcher-Icon „LightTimeClouds“ konfigurieren; Logs per `adb logcat`.

### Übersetzungen

Alle UI-Texte liegen in `app/src/main/res/values/strings.xml` (Englisch, zugleich Fallback für alle anderen Sprachen) und `values-de/strings.xml` (Deutsch). Für eine weitere Sprache `values-de/strings.xml` nach `values-<Sprachcode>/` kopieren und übersetzen; `./gradlew :app:lintDebug` meldet fehlende Einträge (`MissingTranslation`). Datum, Uhrzeit und Zahlen formatiert die App selbst passend zur Sprache. Ab Android 13 lässt sich die App-Sprache in den Systemeinstellungen getrennt vom Gerät wählen.

## Credits

Die Sonnen-/Mondberechnung in `core/.../astro/SunMoonCalculator.kt` ist eine Portierung der Formeln aus [SunCalc](https://github.com/mourner/suncalc) von Vladimir Agafonkin (BSD-2-Clause, Lizenztext in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)).

Die Himmelsfarben des Rings (Darstellung „Himmel“) berechnet `core/.../sky/SkyModel.kt`, eine Portierung von [Horizon](https://github.com/dnlzro/horizon) von Daniel Lazaro (MIT-Lizenz, Lizenztext in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)).

Die Wetterdaten stammen von [Open-Meteo.com](https://open-meteo.com/) (Lizenz CC BY 4.0).

## Lizenz

LightTimeClouds steht unter der [MIT-Lizenz](LICENSE). Portierte Teile fremder Projekte (SunCalc, Horizon) behalten ihre eigenen Lizenzen, siehe [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

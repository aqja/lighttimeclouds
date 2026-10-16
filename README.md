# LightTimeClouds

Android-Homescreen-Widget: eine analoge Sonnen- und Monduhr, inspiriert von [sunclock.net](https://sunclock.net).

Ein Ring um ein gewöhnliches Zifferblatt zeigt den Tagesverlauf von Nacht über Dämmerung bis Tag am aktuellen Standort, ergänzt um einen Mond-Marker mit aktueller Phase. Anders als die 24h-Vorlage nutzt LightTimeClouds standardmäßig ein normales **12-Stunden-Zifferblatt** (optional auch 24h).

## Features

- Analoges 12h- oder 24h-Zifferblatt, minutengenau, ohne Sekundenzeiger, optional mit Minutenstrichen
- Tag/Dämmerung/Nacht-Ring nach aktuellem Sonnenstand am konfigurierten Standort, als Verlauf oder in festen Phasenfarben
- Konfigurierbare Umschalt-Strategie für den Ring im 12h-Modus: feste Uhrzeiten oder kontinuierlich hinter dem Zeiger herlaufend
- Optionale Markierungen für Sonnenmittag/-mitternacht und Sonnenauf-/untergang
- Mondzeiger mit Phase; Abstand zur Mitte zeigt die Mondhöhe, dazu Tagesbahn des Mondes und Horizontkreis (ausblendbar)
- Tab „Zeiten“ in der App: alle Sonnenereignisse des Sonnentags, Mondphase, Mondauf- und -untergang
- Gespeicherte Orte mit Name, Koordinaten und Zeitzone; jedes Widget zeigt die Ortszeit (Weltuhr mit mehreren Widgets). Nur manuelle Eingabe, kein GPS, keine Standort-Berechtigung
- Antippen öffnet wahlweise die System-Uhr-App oder die App selbst
- Eigenes Launcher-Icon für die Konfiguration, unabhängig vom Widget
- Frei skalierbares, quadratisches Widget (Standard 2×2), mehrere Instanzen mit eigenen Einstellungen möglich
- Farbmodi Hell/Dunkel/Automatisch (folgt dem System-Dunkelmodus); Ziffern und Zeiger in Material-You-Akzentfarben
- Geplant: V2 Wetter-Ring (Bewölkung/Niederschlag) und Wetterstation je Ort; V3 genauere Astronomie nach Meeus

## Projektstruktur

```
core/   – reine Kotlin/JVM-Logik ohne Android-Abhängigkeit: Sonnen-/Mondberechnung, Zifferblatt-Zeitmapping (inkl. Unit-Tests)
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
./gradlew :core:test        # Unit-Tests der Astronomie-/Zeitlogik
./gradlew assembleDebug     # APK unter app/build/outputs/apk/debug/
./gradlew installDebug      # direkt auf ein per adb verbundenes Gerät
```

Zum manuellen Testen: Widget auf den Homescreen ziehen, über das Launcher-Icon „LightTimeClouds“ konfigurieren; Logs per `adb logcat`.

## Credits

Die Sonnen-/Mondberechnung in `core/.../astro/SunMoonCalculator.kt` ist eine Portierung der Formeln aus [SunCalc](https://github.com/mourner/suncalc) von Vladimir Agafonkin (BSD-2-Clause, Lizenztext in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)).

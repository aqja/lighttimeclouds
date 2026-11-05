# LightTimeClouds

**English** | [Deutsch](LIESMICH.md)

Android home screen widget: an analog sun and moon clock, inspired by [sunclock.net](https://sunclock.net).

A ring around an ordinary clock face shows the course of the day at the current location, from night through twilight to day, together with a moon marker showing the current phase. Unlike the 24-hour original, LightTimeClouds uses a regular **12-hour clock face** by default (24 hours optional).

## Features

- Analog 12-hour or 24-hour clock face, updated every minute, without a second hand, optionally with minute ticks
- Day/twilight/night ring following the current position of the sun at the configured location, shown as "Sky" (default) or in fixed colors. "Sky" shows in every section of the ring the sky from the horizon upwards (horizon inside or outside, as you choose): daylight, sunrise and sunset colors and the blue of twilight, from a physical scattering model (spectral, with multiple scattering and haze from measured aerosols), as the human eye sees it: adapted to the light, with the blue shift and fading colors of dim light, down to the night sky's own faint glow
- Sun and moon (with phase) in the ring at the current time, as far from the horizon edge as they stand above the horizon; the sun as a small disc at its true size along the ring, colored as seen through the atmosphere, with its aureole; the moon likewise at its true size, only its lit part, pale in the day sky and orange to red near the horizon, at night with a halo, lighting the sky in the ring according to its phase; higher than shown as a small dot at the edge opposite the horizon; not at all below the horizon. Optionally the moon's path over the whole displayed period as a thin gray line. How high above the horizon the outer edge reaches (10°–80°) is adjustable; the sky gradient adapts
- Configurable strategy for advancing the ring: fixed times (12 hours: two, by default 6:00 and 18:00; 24 hours: one, by default midnight) or moving with the hand, which then always sits at the start of the shown period. The ring can fade out over its last 0°–30° (default 15°), so its end is not mistaken for the time right after its start
- Optional marks for solar noon and midnight and for sunrise and sunset
- Optionally a red hour tick at the next alarm as soon as it falls within the displayed period (Android only reveals the next alarm); follows every change immediately, so when snoozing it moves to the new alarm time if the clock app reports it
- Moon with phase, either on the hour hand (its path crosses the horizon circle at the actual times of moonrise and moonset) or as its own hand that trails the hour hand by the moon's distance from the sun; the distance from the center shows the moon's altitude, together with the moon's daily path, the compass points it passes and the horizon circle (can be hidden)
- "Preview" tab in the app: the widget with the current settings, including unsaved ones; sliders for latitude, longitude, date in the course of the year and time of day change only the preview
- "Times" tab in the app: all sun events of the solar day, moon phase, moonrise and moonset
- Saved places with name, coordinates and time zone; every widget shows the local time of its place (world clock with several widgets). Manual entry only, no GPS, no location permission. Network access only for the optional weather ring
- Tapping opens either the system clock app or the app itself
- Separate launcher icon for the settings, independent of the widget
- Freely resizable, square widget (2×2 by default); several instances with their own settings
- Light, dark and automatic color modes (following the system dark mode); numerals and hands in Material You accent colors
- Optional weather ring around the day/night ring (default) or just inside it: cloud cover in gray (transparent when the sky is clear), precipitation on top in blue (snow in violet): the darker, the more; the more opaque, the more likely. Past hours are dimmed. Forecast from Open-Meteo, loaded in the background about every two hours (not when the battery is low) and only if a widget uses the ring; for this, the place's coordinates are sent to open-meteo.com
- Air (default) or apparent temperature (mean per section) as a scale around the outermost ring (default) or between the hour numerals, or none
- "Weather summary" in the "Weather" tab and optionally left of, right of or below the clock: comparison with yesterday at the same time, an apparent temperature that differs noticeably, and rain or snow in the next 3 or 6 hours, by fixed rules from the forecast

## Project structure

```
core/   – plain Kotlin/JVM logic without Android dependencies: sun and moon calculation, mapping of time to the clock face, weather forecast, sky colors (with unit tests)
app/    – Android module: widget, settings app (Jetpack Compose), persistence, rendering
```

## Building and testing

Requirements:

- **JDK 17–27** (e.g. Arch: `jdk-openjdk`). The wrapper (`./gradlew`) downloads Gradle itself.
- **Android SDK** with `platforms;android-<compileSdk>` and `build-tools` (versions in `app/build.gradle.kts` or the AGP default). Android Studio is not needed; the command-line tools are enough, e.g.:

  ```sh
  android --sdk=$HOME/Android/Sdk sdk install platforms/android-37.0 build-tools/36.0.0
  echo "sdk.dir=$HOME/Android/Sdk" > local.properties
  ```

  Without `local.properties`, Gradle uses `ANDROID_HOME`; some packagings set it system-wide to a different path.

```sh
./gradlew :core:test        # unit tests of the astronomy, time, weather and sky logic
./gradlew :app:testDebugUnitTest  # renderer tests; renders preview images to app/build/previews/<group>/ (Robolectric)
./gradlew assembleDebug     # APK in app/build/outputs/apk/debug/
./gradlew installDebug      # straight to a device connected via adb
```

The sky colors are precomputed as a table from `core/.../sky/SkyModel.kt` during the build (Gradle task `:core:generateSkyTable`); the repository contains no binary file for it.

Two Python scripts in `tools/` (run with [uv](https://docs.astral.sh/uv/); dependencies are declared in the script) belong to the astronomy; the app build does not need them:

```sh
uv run tools/astro_series.py        # re-truncates ELP 2000-82B and VSOP87 into core/.../astro/AstroSeries.kt
uv run tools/reference_ephemeris.py # reference values from JPL DE440s for the tests (core/src/test/resources/astro/)
```

Both download their raw data (IMCCE series, ~32 MB ephemeris) to `~/.cache/lighttimeclouds/`, not into the repository.

For manual testing: drag the widget onto the home screen and configure it via the "LightTimeClouds" launcher icon; logs via `adb logcat`.

### Translations

All UI texts are in `app/src/main/res/values/strings.xml` (English, also the fallback for all other languages) and `values-de/strings.xml` (German). For another language, copy `values-de/strings.xml` to `values-<language code>/` and translate it; `./gradlew :app:lintDebug` reports missing entries (`MissingTranslation`). The app formats dates, times and numbers itself to suit the language. From Android 13 on, the app language can be chosen in the system settings independently of the device language.

## Credits

The sun and moon calculation (`core/.../astro/`) is based directly on the primary sources: the moon on the lunar theory ELP 2000-82B (M. Chapront-Touzé, J. Chapront, A&A 124, 50, 1983 and A&A 190, 342, 1988), the sun on the planetary theory VSOP87 (P. Bretagnon, G. Francou, A&A 202, 309, 1988), both as distributed by the [IMCCE](https://www.imcce.fr/) (Paris Observatory) and truncated for the app (between 2000 and 2050 at most 4″ error for the moon, 2″ for the sun); nutation, obliquity of the ecliptic and sidereal time follow the IERS Conventions 2010 (IERS Technical Note 36), refraction follows Sæmundsson (1986). The results are checked against the JPL ephemeris DE440s. The scope and selection of sun events were inspired by [SunCalc](https://github.com/mourner/suncalc) by Volodymyr Agafonkin, which earlier versions of the app had ported.

The sky colors of the ring ("Sky" style) are computed by `core/.../sky/SkyModel.kt`, a port of [Horizon](https://github.com/dnlzro/horizon) by Daniel Lazaro (MIT License), which is based on ["Production Sky Rendering"](https://www.shadertoy.com/view/slSXRW) by Andrew Helmer (MIT according to its author) and on the paper by Sébastien Hillaire (2020), extended with multiple scattering after Hillaire (2020), a spectral calculation with the solar spectrum and ozone absorption cross-sections from [Precomputed Atmospheric Scattering](https://github.com/ebruneton/precomputed_atmospheric_scattering) by Eric Bruneton (BSD-3-Clause), aerosols from Mie theory (Bohren and Huffman) with a size distribution measured by AERONET (Dubovik et al. 2002), and a model of human vision (brightness after Tumblin, Hodgins and Guenter 1999, the rods' color contribution after Cao et al. 2008 and Wanat and Mantiuk 2014); license texts in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

Weather data from [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0 license).

All formulas, constants and simplifications of the astronomy, the sky model and the weather evaluation, with sources, are described in [`docs/PHYSICS.md`](docs/PHYSICS.md) (German: [`docs/PHYSIK.md`](docs/PHYSIK.md)); how the sky's light is turned into the colors the eye sees, in [`docs/PHYSIOLOGY.md`](docs/PHYSIOLOGY.md) (German: [`docs/PHYSIOLOGIE.md`](docs/PHYSIOLOGIE.md)).

## License

LightTimeClouds is licensed under the [MIT License](LICENSE). Ported or adopted parts of other projects (Horizon, Precomputed Atmospheric Scattering) keep their own licenses, see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

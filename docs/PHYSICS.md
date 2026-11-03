# Physics and Astronomy in LightTimeClouds

*Deutsche Fassung: [PHYSIK.md](PHYSIK.md)*

This document describes every physical and astronomical computation in the app: what is computed,
with which formulas and constants, where it lives in the code, where model and numbers come from,
and which simplifications were made on purpose. Sources are numbered at the end
([Sources](#15-sources)); references in the text look like this: [7].

Almost all of the computation is in the `core` module (plain Kotlin/JVM, no Android):

| Area | File(s) |
|---|---|
| Time scales | [`astro/TimeScales.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/TimeScales.kt) |
| Earth orientation (nutation, obliquity, sidereal time) | [`astro/EarthOrientation.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/EarthOrientation.kt) |
| Ephemerides of Sun and Moon | [`astro/Ephemeris.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/Ephemeris.kt), [`astro/AstroSeries.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/AstroSeries.kt) (generated) |
| Observer's place, horizontal coordinates, refraction | [`astro/Topocentric.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/Topocentric.kt) |
| Events, moon phase, public API | [`astro/SunMoonCalculator.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/SunMoonCalculator.kt), [`astro/SolarDay.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/SolarDay.kt) |
| Atmospheric scattering model | [`sky/SkyModel.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyModel.kt) |
| Spectrum and colorimetry | [`sky/Spectrum.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Spectrum.kt) |
| Mapping elevation → ring | [`sky/SkyProjection.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyProjection.kt) |
| Precomputed color table | [`sky/SkyGradientTable.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyGradientTable.kt), [`sky/SkyTableGenerator.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyTableGenerator.kt) |
| Weather quantities and thresholds | [`weather/WeatherForecast.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/weather/WeatherForecast.kt), [`weather/WeatherOutlook.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/weather/WeatherOutlook.kt) |
| Drawing on the dial | [`widget/ClockFaceRenderer.kt`](../app/src/main/kotlin/com/umfluten/lighttimeclouds/widget/ClockFaceRenderer.kt) (module `app`) |
| Truncating the series, reference values | [`tools/astro_series.py`](../tools/astro_series.py), [`tools/reference_ephemeris.py`](../tools/reference_ephemeris.py) |

## Contents

1. [Conventions](#1-conventions)
2. [Time scales](#2-time-scales)
3. [Earth orientation](#3-earth-orientation)
4. [Ephemerides of Sun and Moon](#4-ephemerides-of-sun-and-moon)
5. [From the Earth's center to the observer](#5-from-the-earths-center-to-the-observer)
6. [Sun events](#6-sun-events)
7. [Moon: rise and set, phase, lag, tilt](#7-moon-rise-and-set-phase-lag-tilt)
8. [Validation against JPL DE440s](#8-validation-against-jpl-de440s)
9. [Sky colors: the scattering model](#9-sky-colors-the-scattering-model)
10. [Sky colors: spectrum, colorimetry, image processing](#10-sky-colors-spectrum-colorimetry-image-processing)
11. [Sky colors: projection and table](#11-sky-colors-projection-and-table)
12. [Drawing on the dial](#12-drawing-on-the-dial)
13. [Weather quantities](#13-weather-quantities)
14. [Simplifications at a glance](#14-simplifications-at-a-glance)
15. [Sources](#15-sources)

---

## 1. Conventions

- **Angles** are computed in radians; series coefficients are often given in arcseconds (″) and
  converted with $1″ = \pi / 648\,000$.
- **Azimuth** counts as in Meeus [1, ch. 13] **from south, positive toward west** (south 0°,
  west +90°, east −90°, north ±180°).
- **Altitude** is the altitude of a body's center above the horizon. For the Sun it is
  **geometric** (without refraction) throughout; refraction is part of the thresholds of the sun
  events. The Moon's altitude is **topocentric with refraction**, i.e. as one sees it
  (section [5.3](#53-refraction)).
- **Time** is a `java.time.Instant` (UTC without leap seconds); the astronomical time scales are
  derived from it (section [2](#2-time-scales)).
- $T$ denotes **Julian centuries of Terrestrial Time (TT) since J2000.0**
  (2000-01-01 12:00 TT, JD 2451545.0).

---

## 2. Time scales

Code: [`TimeScales.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/TimeScales.kt)

**Julian date.** From the Unix seconds $s$ (UTC):

```math
\mathrm{JD}_\mathrm{UT1} \approx \mathrm{JD}_\mathrm{UTC} = 2\,440\,587.5 + \frac{s}{86\,400}
```

(2 440 587.5 is the JD of 1970-01-01 00:00 UTC [1, ch. 7]).

**Terrestrial Time.** The argument of the planetary and lunar theories is TT [1, ch. 10; 4]:

```math
\mathrm{TT} = \mathrm{TAI} + 32.184\,\mathrm{s}, \qquad
\mathrm{TT} - \mathrm{UTC} = 32.184\,\mathrm{s} + (\mathrm{TAI} - \mathrm{UTC})
```

$\mathrm{TAI} - \mathrm{UTC}$ is the sum of the leap seconds; the code keeps the table from 1972
(10 s) up to the latest leap second so far on 2017-01-01 (37 s), after IERS Bulletin C [5]. Since
then $\mathrm{TT} - \mathrm{UTC} = 69.184\,\mathrm{s}$. Hence

```math
T = \frac{\mathrm{JD}_\mathrm{UTC} + (\mathrm{TT}-\mathrm{UTC})/86\,400 - 2\,451\,545.0}{36\,525}.
```

**UT1 ≈ UTC.** The Earth's rotation angle needs UT1. By definition, leap seconds keep
$|\mathrm{UT1} - \mathrm{UTC}| < 0.9\,\mathrm{s}$ [5], so the app takes UT1 = UTC. 0.9 s of
rotation amount to at most $0.9\,\mathrm{s} \cdot 15″/\mathrm{s} \approx 14″$ on the sky. This
bound also sets the tolerance of the topocentric tests (section [8](#8-validation-against-jpl-de440s)).

---

## 3. Earth orientation

Code: [`EarthOrientation.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/EarthOrientation.kt).
Based on the IERS Conventions 2010 (IERS Technical Note 36) [4]. For one moment, the code computes
the nutation, the true obliquity of the ecliptic and the Greenwich apparent sidereal time.

### 3.1 Fundamental arguments

After Simon et al. (1994) [6], as in TN36 eq. 5.43 [4], up to $T^2$ (in arcseconds):

```math
\begin{aligned}
l' &= 1\,287\,104.79305 + 129\,596\,581.0481\,T - 0.5532\,T^2 &&\text{(mean anomaly of the Sun)}\\
F  &= 335\,779.526232 + 1\,739\,527\,262.8478\,T - 12.7512\,T^2 &&(L - \Omega)\\
D  &= 1\,072\,260.70369 + 1\,602\,961\,601.2090\,T - 6.3706\,T^2 &&\text{(mean elongation of the Moon)}\\
\Omega &= 450\,160.398036 - 6\,962\,890.5431\,T + 7.4722\,T^2 &&\text{(longitude of the Moon's ascending node)}
\end{aligned}
```

The constant terms of $l'$ and $D$ are rounded to five decimals as in the MHB2000 code, which TN36
mentions right below eq. 5.43 (a difference of 2 µas).
The Moon's mean anomaly $l$ is not needed, since none of the nutation terms used depends on it.

### 3.2 Nutation

The largest terms of the IAU 2000A lunisolar nutation (Mathews, Herring, Buffett 2002 [7]) in the
original MHB2000 version, as also used by the SOFA routine `nut00a`, in arcseconds. TN36, tables
5.3a/5.3b [4], lists the same terms in the version adjusted to IAU 2006, IAU 2000A_R06; its
coefficients differ by at most a few µas (time terms: a few µas per century).

```math
\begin{aligned}
\Delta\psi ={}& (-17.2064161 - 0.0174666\,T)\sin\Omega
  + (-1.3170906 - 0.0001675\,T)\sin(2F - 2D + 2\Omega)\\
  &- 0.2276413\,\sin(2F + 2\Omega) + 0.2074554\,\sin 2\Omega + 0.1475877\,\sin l'\\[4pt]
\Delta\varepsilon ={}& (9.2052331 + 0.0009086\,T)\cos\Omega
  + (0.5730336 - 0.0003015\,T)\cos(2F - 2D + 2\Omega)\\
  &+ 0.0978459\,\cos(2F + 2\Omega) - 0.0897492\,\cos 2\Omega
\end{aligned}
```

Five terms in longitude and four in obliquity are enough for about 0.2″. The largest term left out
($0.0711″ \sin l$) is far below the error budget of the ephemerides.

### 3.3 Obliquity of the ecliptic

Mean obliquity, IAU 2006 (Capitaine, Wallace, Chapront 2003 [8]; TN36 eq. 5.40 [4]):

```math
\varepsilon_A = 84\,381.406″ - 46.836769″\,T - 0.0001831″\,T^2 + 0.00200340″\,T^3,
\qquad \varepsilon = \varepsilon_A + \Delta\varepsilon .
```

### 3.4 Sidereal time

Earth rotation angle (Capitaine, Guinot, McCarthy 2000 [9]; TN36 eq. 5.14 [4]; the code uses the
equivalent form eq. 5.15) with
$D_U = \mathrm{JD}_\mathrm{UT1} - 2\,451\,545.0$:

```math
\theta_\mathrm{ERA} = 2\pi\,\bigl(0.7790572732640 + 1.00273781191135448\,D_U\bigr) \bmod 2\pi .
```

Greenwich mean sidereal time (TN36 eq. 5.32, up to $T^2$), and the apparent sidereal time via the
equation of the equinoxes:

```math
\mathrm{GMST} = \theta_\mathrm{ERA} + 0.014506″ + 4612.156534″\,T + 1.3915817″\,T^2,
\qquad
\mathrm{GAST} = \mathrm{GMST} + \Delta\psi\cos\varepsilon_A .
```

Left out are polar motion (< 0.5″) and the complementary terms of the equation of the equinoxes
(a few milliarcseconds).

---

## 4. Ephemerides of Sun and Moon

Code: [`Ephemeris.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/Ephemeris.kt).
The series themselves are in [`AstroSeries.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/AstroSeries.kt),
which [`tools/astro_series.py`](../tools/astro_series.py) generates from the IMCCE's original data
(section [4.6](#46-truncating-the-series)).

### 4.1 Sun: VSOP87D

VSOP87 (Bretagnon, Francou 1988 [10]), version D: heliocentric spherical coordinates $L, B, R$ of
the Earth, referred to the mean ecliptic and equinox of date. With $\tau = T/10$ (Julian
millennia):

```math
X = \sum_{\alpha} \tau^{\alpha} \sum_k A_{k}\cos(B_{k} + C_{k}\,\tau), \qquad X \in \{L, B, R\}.
```

The geocentric Sun is the Earth as seen from the Sun's center, turned around:
$\lambda_\odot = L + \pi$, $\beta_\odot = -B$, distance $R$ in au [1, ch. 25 and 32].

**Apparent place.** Nutation and annual aberration are added to the geometric longitude
[1, ch. 23 and 25]:

```math
\lambda_\mathrm{app} = \lambda_\odot + \Delta\psi - \frac{\kappa}{R},
\qquad \kappa = 20.49552″ \text{ (constant of aberration, IAU 1976)}.
```

### 4.2 Moon: ELP 2000-82B

The semi-analytical lunar theory ELP 2000-82B (Chapront-Touzé, Chapront 1983 [11], 1988 [12])
gives longitude and latitude (in ″) and distance (in km), referred to the mean ecliptic of date and
the departure point γ′ of J2000. Every term depends on 13 arguments $a_i(T)$ (polynomials up to
$T^4$): the Delaunay arguments $D, l', l, F$, the Moon's mean longitude including precession
$\zeta$, and the mean longitudes of the planets Mercury to Neptune ($Me, V, T, Ma, J, S, U, N$).
All terms are stored as sines with a phase $\varphi_k$:

```math
\begin{aligned}
\lambda &= W_1(T) + \sum_k A_k\,T^{n_k}\sin\Bigl(\varphi_k + \sum_{i=1}^{13} m_{k,i}\,a_i(T)\Bigr) + p_A(T),\\
\beta, r &= \sum_k A_k\,T^{n_k}\sin\Bigl(\varphi_k + \sum_{i} m_{k,i}\,a_i(T)\Bigr).
\end{aligned}
```

$W_1$ is the Moon's mean longitude. The precession in longitude after Laskar (1986) [13], as given
together with ELP 2000-82B, leads from the departure point of J2000 to the mean equinox of date:

```math
p_A = 5029.0966″\,T + 1.1120″\,T^2 + 0.000077″\,T^3 - 0.00002353″\,T^4 .
```

**Apparent place.** $\lambda_\mathrm{app} = \lambda + \Delta\psi$. The light time from the Moon
(≈ 1.3 s, < 1″) is left out.

### 4.3 Ecliptic → equator

With the true obliquity $\varepsilon$ [1, eq. 13.3, 13.4]:

```math
\alpha = \operatorname{atan2}\bigl(\sin\lambda\cos\varepsilon - \tan\beta\sin\varepsilon,\ \cos\lambda\bigr),
\qquad
\delta = \arcsin\bigl(\sin\beta\cos\varepsilon + \cos\beta\sin\varepsilon\sin\lambda\bigr).
```

### 4.4 Interpolation

A dial needs hundreds of positions per minute (the Moon's daily path, the ring colors). The series
are therefore evaluated only at **nodes 3 hours apart** (and cached, at most 512 nodes), and
interpolated in between with the cubic Lagrange polynomial through the four nearest nodes
$u = -1, 0, 1, 2$:

```math
y(u) = -\tfrac{u(u-1)(u-2)}{6}y_{-1} + \tfrac{(u+1)(u-1)(u-2)}{2}y_0
       - \tfrac{(u+1)u(u-2)}{2}y_1 + \tfrac{(u+1)u(u-1)}{6}y_2 .
```

Longitudes are first continued across 2π. The interpolation error is below 0.001″; the test
requires < 0.01″.

### 4.5 What "sun" and "moon" return

| | Longitude, latitude | Distance | Frame |
|---|---|---|---|
| `Ephemeris.sun` | apparent (nutation + aberration) | au → km ($1\,\mathrm{au} = 149\,597\,870.7\,\mathrm{km}$ [3]) | true ecliptic and equinox of date |
| `Ephemeris.moon` | apparent (nutation) | km | true ecliptic and equinox of date |

### 4.6 Truncating the series

[`tools/astro_series.py`](../tools/astro_series.py) downloads ELP 2000-82B and VSOP87D from the
IMCCE [14], evaluates the complete series as the reference programs do, checks them against the
check values published with them, and then keeps every term above an amplitude threshold. The
threshold is chosen so that the error from the dropped terms stays within the budget from 2000 to
2050 (4000 samples):

| Coordinate | Terms (kept / total) | Budget | Largest error 2000–2050 |
|---|---|---|---|
| Moon, longitude | 136 / 20 560 | 4″ | 3.96″ |
| Moon, latitude | 97 / 7 684 | 2″ | 1.94″ |
| Moon, distance | 84 / 9 628 | 5 km | 4.85 km |
| Earth, L | 36 / 1 080 | 2″ | 1.9″ |
| Earth, B | 2 / 348 | 0.5″ | 0.5″ |
| Earth, R | 16 / 997 | 10⁻⁵ au | 8.7·10⁻⁶ au |

(Values from the header of [`AstroSeries.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/AstroSeries.kt).)
Outside 2000–2050 the error grows slowly, mostly through the terms with $T^n$.

---

## 5. From the Earth's center to the observer

Code: [`Topocentric.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/Topocentric.kt)

### 5.1 Place on the WGS84 ellipsoid, and parallax

The observer stands at sea level on the WGS84 ellipsoid [15] ($a = 6378.137\,\mathrm{km}$,
$f = 1/298.257223563$) at geodetic latitude $\varphi$ and east longitude $\lambda_E$. With the
local sidereal time $\theta = \mathrm{GAST} + \lambda_E$ and [1, ch. 11]

```math
C = \frac{1}{\sqrt{\cos^2\varphi + (1-f)^2\sin^2\varphi}}, \qquad S = (1-f)^2\,C
```

the observer's geocentric position in the equatorial frame of date is

```math
\vec r_o = a\,\bigl(C\cos\varphi\cos\theta,\ C\cos\varphi\sin\theta,\ S\sin\varphi\bigr).
```

Instead of Meeus' parallax formulas [1, ch. 40], the code subtracts this vector directly from the
body's geocentric position ($\Delta$, $\alpha$, $\delta$):

```math
\vec r' = \Delta\,(\cos\delta\cos\alpha,\ \cos\delta\sin\alpha,\ \sin\delta) - \vec r_o,\qquad
\delta' = \operatorname{atan2}\bigl(z',\sqrt{x'^2+y'^2}\bigr),\qquad
H = \theta - \operatorname{atan2}(y', x').
```

This is rigorous and holds alike for the Moon (parallax up to ≈ 1°) and the Sun (≈ 8.8″).

### 5.2 Horizontal coordinates

[1, eq. 13.5, 13.6], azimuth from south toward west:

```math
h = \arcsin\bigl(\sin\varphi\sin\delta' + \cos\varphi\cos\delta'\cos H\bigr),\qquad
A = \operatorname{atan2}\bigl(\cos\delta'\sin H,\ \cos\delta'\cos H\sin\varphi - \sin\delta'\cos\varphi\bigr).
```

For the Sun's meridian transit the **geocentric** hour angle $H = \theta - \alpha$ (brought to
−π…π) is enough.

### 5.3 Refraction

Sæmundsson's formula (1986) [16], as in Meeus [1, eq. 16.4], for 10 °C and 1010 hPa: from the
geometric altitude $h$ (in degrees), the lift in arcminutes

```math
R = \frac{1.02'}{\tan\!\left(h + \dfrac{10.3°}{h + 5.11°}\right)} .
```

Below −1°, $h$ is held at −1° to keep the formula finite. Refraction is applied only to the
**Moon's altitude** in `moonPosition`.

The formula agrees with the standard value of 34′ used for rising and setting
(sections [6](#6-sun-events), [7.1](#71-moonrise-and-moonset)): 34′ is the refraction at an
**apparent** altitude of 0. Sæmundsson starts from the **geometric** altitude instead. At $h = 0$
the formula gives ≈ 29′ (the body then appears at +29′); at $h = -34'$ it gives 34.4′, so the body
appears right on the horizon. Sæmundsson's formula is built as the inverse of Bennett's (1982)
[41], which starts from the apparent altitude and gives 34.5′ at the horizon [1, ch. 16]. A test
guards this relation.

---

## 6. Sun events

Code: `sunTimes` in [`SunMoonCalculator.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/SunMoonCalculator.kt),
the list for the "solar day" in [`SolarDay.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/SolarDay.kt).

### 6.1 Solar noon and midnight

Noon is the upper, midnight the lower meridian transit. Starting from an estimate, the code
iterates four times

```math
t \leftarrow t - \frac{H}{2\pi}\cdot 86\,400\,\mathrm{s}
```

($H$ = the Sun's hour angle, shifted by π for the lower transit). Since the Sun's right ascension
changes by only about 1/365 of a turn per day, the error shrinks by that factor with each step;
four steps are plenty. A solar day runs from the midnight before noon to the midnight after. That
is why the list in the "Times" tab switches at solar midnight rather than at civil midnight.

### 6.2 Altitude thresholds

All other events are the moments when the **geometric** center of the Sun crosses a threshold
altitude:

| Event (morning / evening) | Altitude | Meaning, source |
|---|---|---|
| Sunrise / sunset | −0.833° | Upper limb on the horizon: 34′ refraction + 16′ semidiameter = 50′ [1, ch. 15; 17] |
| End of sunrise / start of sunset | −0.3° | Lower limb leaves the horizon (SunCalc's convention [18]) |
| End / start of golden hour | +6° | SunCalc's convention [18] |
| Civil twilight | −6° | [17; 2] |
| Nautical twilight | −12° | [17; 2] |
| Astronomical twilight | −18° | [17; 2] |

Morning events are searched between midnight and noon, evening events between noon and the
following midnight, where the altitude changes monotonically. If the threshold does not lie between
the altitudes at the ends of the interval, the event does not occur that day (`null`: polar day,
polar night, white nights).

### 6.3 Root finding

`crossing` finds the sign change of $g(t) = h(t) - h_0$ with the **regula falsi in the Illinois
variant** (Dowell, Jarratt 1971 [19]):

```math
t = \frac{a\,g(b) - b\,g(a)}{g(b) - g(a)},
```

after which $[a, b]$ is shortened to the side with the sign change. If one end stays put twice in a
row, its function value is halved. This prevents the slow one-sided convergence of the plain
regula falsi. The search stops once the interval is shorter than 0.5 s (at most 60 steps).

### 6.4 Sky state

For the ring styles "gradient" and "phase colors", `classify` maps the Sun's altitude to a
bucket: night (< −18°), astronomical (−18…−12°), nautical (−12…−6°) and civil twilight
(−6…−0.833°), golden hour (−0.833…6°) and day (≥ 6°).

---

## 7. Moon: rise and set, phase, lag, tilt

Code: [`SunMoonCalculator.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/SunMoonCalculator.kt)

### 7.1 Moonrise and moonset

Rise and set are the moments when the Moon's **upper limb** touches the horizon, with 34′ of
standard refraction. Since the altitude here is already topocentric and geometric (so the parallax
is included), the threshold for the center is

```math
h_0 = -34' - s, \qquad s = \arcsin\frac{k\,a}{\Delta}, \qquad k = 0.2725 ,
```

with the distance $\Delta$ from the Earth's center, the Earth's radius $a$, and the ratio $k$ of
the lunar to the terrestrial radius. This corresponds to Meeus' geocentric threshold
$h_0 = 0.7275\,\pi - 34'$ [1, ch. 15] ($\pi$ = horizontal parallax, $0.7275 = 1 - k$), only
without the parallax part, which the topocentric computation already contains.

The search covers the 24 hours from the start: the altitude is sampled every 30 minutes and each
sign change is then pinned down to half a second with the regula falsi
(section [6.3](#63-root-finding)). A day may have no moonrise or moonset (on average the Moon
rises 50 minutes later than the day before), and near the poles several.
`SolarDay.moonEventsBetween` combines several days and removes duplicates (same kind, same
10-minute span).

### 7.2 Illuminated fraction and phase

After Meeus [1, ch. 48], from the apparent geocentric positions of the Moon ($\lambda, \beta,
\Delta$) and the Sun ($\lambda_\odot$, $R$):

```math
\cos\psi = \cos\beta\cos(\lambda - \lambda_\odot)
\quad\text{(geocentric elongation)},
```

```math
i = \operatorname{atan2}\bigl(R\sin\psi,\ \Delta - R\cos\psi\bigr)
\quad\text{(phase angle Sun–Moon–Earth)},
\qquad
k = \frac{1 + \cos i}{2}
\quad\text{(illuminated fraction)}.
```

The **phase** is the elongation in ecliptic longitude as a fraction of a full turn,
$p = \bigl((\lambda - \lambda_\odot) \bmod 2\pi\bigr) / 2\pi$: 0 new moon, 0.25 first quarter,
0.5 full moon, 0.75 last quarter. The eight phase names each cover one eighth around their center:
index $= \lfloor 8p + 0.5 \rfloor \bmod 8$.

### 7.3 Lag behind the Sun

For the "separate hand" style, what matters is how many hours the Moon trails the Sun on its daily
path, i.e. the difference of their right ascensions as time:

```math
\Delta t = \frac{(\alpha_\mathrm{Moon} - \alpha_\odot) \bmod 2\pi}{2\pi}\cdot 24\,\mathrm{h}.
```

About 0 h at new moon, 6 h at first quarter, 12 h at full moon. A hand pointing at "now minus
$\Delta t$" shows where the Moon is in its daily circuit, the same way the hour hand shows the
Sun's. The difference between solar and sidereal time is ignored here.

### 7.4 Tilt of the crescent

The Moon's bright limb faces the Sun. How the crescent is tilted on the sky depends on place and
time. The position angle of the bright limb, counted from the north point of the disc through
east [1, eq. 48.5], is

```math
\chi = \operatorname{atan2}\bigl(\cos\delta_\odot\sin(\alpha_\odot - \alpha),\
  \sin\delta_\odot\cos\delta - \cos\delta_\odot\sin\delta\cos(\alpha_\odot - \alpha)\bigr),
```

and the parallactic angle, by which the direction to the celestial pole is turned against the
direction to the zenith [1, eq. 14.1], with the Moon's hour angle $H$:

```math
q = \operatorname{atan2}\bigl(\sin H,\ \tan\varphi\cos\delta - \sin\delta\cos H\bigr).
```

`moonBrightLimbAngle` returns $\chi - q$: the angle of the bright limb **from the zenith,
counterclockwise**, as the observer sees the Moon in front of them (90° = left). Geocentric
positions are used, as in Meeus. One test compares the result with an independent computation in
the horizontal frame (direction of the great circle from the Moon to the Sun) and requires less
than 0.5° of difference. Another checks that the waxing Moon is lit on the right in Berlin and on
the left in Canberra.

---

## 8. Validation against JPL DE440s

[`tools/reference_ephemeris.py`](../tools/reference_ephemeris.py) uses Skyfield [20] to compute
reference values from the numerical ephemeris JPL DE440s [21], with the same definitions as the app
(geometric altitudes on the WGS84 ellipsoid, azimuth from south, event thresholds as above). The
places are Berlin, Quito, Canberra and, for polar day and night, Longyearbyen. Values that depend on
the Earth's rotation stop at 2025, because after that Skyfield extrapolates UT1 from a prediction of
ΔT, which UTC need not follow.
[`EphemerisReferenceTest.kt`](../core/src/test/kotlin/com/umfluten/lighttimeclouds/astro/EphemerisReferenceTest.kt)
requires:

| Quantity | Period | Limit | Reason |
|---|---|---|---|
| Geocentric longitude (· cos β) and latitude, Sun and Moon | 2000–2050 | 5″ | Truncation (up to 4″) and the other approximations; the complete theories agree with DE440s to well below 1″ |
| Topocentric altitude and azimuth (· cos h) | 2000–2025 | 15″ | Additionally UT1 ≈ UTC (up to 14″) |
| Times of all sun events | 2000–2025 | 3 s | |
| Moonrise and moonset | 2000–2025 | 3 s | |
| Interpolation against the direct series | | 0.01″ | |

---

## 9. Sky colors: the scattering model

Code: [`SkyModel.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyModel.kt).
The model is a port of *Horizon* (Daniel Lazaro) [22], which derives from *Production Sky
Rendering* (Andrew Helmer) [23]. Physics and parameters come from Hillaire (2020) [24], who builds
on Bruneton and Neyret (2008) [25]. The app extends the original with multiple scattering after
Hillaire [24, sec. 5.5] and with a spectral computation using data from Bruneton's implementation
[26] (section [10](#10-sky-colors-spectrum-colorimetry-image-processing)).

There are two configurations of the same computation:

| | `gradient` (original) | `averagedGradient` (the app's ring) |
|---|---|---|
| Wavelengths | 3 (680, 550, 440 nm ≙ R, G, B) | 48 (360–830 nm, 10 nm steps) |
| Solar spectrum | white (1, 1, 1) | ASTM G-173 above the atmosphere |
| Scattering orders | single | single + multiple |
| View direction | 90° to the side of the Sun | mean over 12 azimuths |
| Exposure | fixed | with eye adaptation |

The original serves as a reference: a test checks that the port yields the same colors as
Horizon.

### 9.1 Atmosphere

A spherical shell between ground radius $R_g = 6360\,\mathrm{km}$ [24; 26] and top
$R_t = 6460\,\mathrm{km}$ (100 km of atmosphere) [24]; Bruneton [26] puts the top at 6420 km. Three constituents with relative density profiles over the
height $h$:

```math
\rho_R(h) = e^{-h/8\,\mathrm{km}},\qquad
\rho_M(h) = e^{-h/1.2\,\mathrm{km}},\qquad
\rho_O(h) = \max\!\left(0,\ 1 - \frac{|h - 25\,\mathrm{km}|}{15\,\mathrm{km}}\right).
```

Rayleigh (air molecules) and Mie (aerosols) fall off exponentially. Ozone is a triangle that rises
linearly from 10 km to its peak at 25 km and falls back to zero at 40 km [26].

Coefficients at the ground or at the ozone peak (in 1/m):

| Quantity | Value | Origin |
|---|---|---|
| Rayleigh scattering $\beta_R$ | 5.802·10⁻⁶ / 13.558·10⁻⁶ / 33.1·10⁻⁶ (R/G/B) | $1.24062\cdot10^{-6}\,(\lambda/\mu\mathrm{m})^{-4}$ at 680/550/440 nm [24; 26] |
| Mie extinction $\beta_M^\mathrm{ext}$ | 4.44·10⁻⁶ | Ångström β 5.328·10⁻³ / 1.2 km [26]; likewise Hillaire's reference code [24]. Table 1 of the paper gives a Mie absorption of 4.40·10⁻⁶ instead of 0.444·10⁻⁶ |
| Mie scattering $\beta_M$ | 3.996·10⁻⁶ | single-scattering albedo 0.9 · extinction [24; 26] |
| Ozone absorption $\beta_O$ (RGB) | 0.65·10⁻⁶ / 1.881·10⁻⁶ / 0.085·10⁻⁶ | [24] |
| Ozone absorption $\beta_O$ (spectral) | $\sigma_{O_3}(\lambda)\cdot n_\mathrm{max}$ | section [10.1](#101-spectral-data) |
| Ground albedo $a$ | 0.3 | as in Hillaire's paper [24] (multiple scattering only) |

Rayleigh scatters without absorbing, ozone only absorbs, aerosol does both (gray, i.e. independent
of wavelength).

### 9.2 Phase functions

Rayleigh [27]:

```math
P_R(\theta) = \frac{3}{16\pi}\,(1 + \cos^2\theta).
```

Mie after Cornette and Shanks (1992) [28] with asymmetry parameter $g = 0.8$ (forward scattering)
[24; 26]:

```math
P_M(\theta) = \frac{3}{8\pi}\,
  \frac{(1 - g^2)(1 + \cos^2\theta)}{(2 + g^2)\,(1 + g^2 - 2g\cos\theta)^{3/2}} .
```

$\theta$ is the angle between the view and Sun directions.

### 9.3 Transmittance

The transmittance from a point at height $h$ along a direction with zenith angle $\zeta$ to the top
is

```math
T(h,\zeta) = \exp\!\Bigl(-\!\!\int \bigl[\beta_R\rho_R + \beta_M^\mathrm{ext}\rho_M + \beta_O\rho_O\bigr]\,ds\Bigr),
```

computed with the midpoint rule in 32 steps. A ray that passes through the Earth reaches negative
heights there, with a very large density. Its transmittance thus becomes practically zero, and the
Earth's shadow arises by itself in single scattering. Multiple scattering checks the ground
explicitly (`sunTransmittance`).

### 9.4 Single scattering along the view ray

From the observer $c$ on the ground, the view ray $\vec v$ is divided into 32 segments $\Delta s$
up to the top. At each midpoint $x_j$, the sunlight scattered there is collected and attenuated on
its way to the observer:

```math
L(\lambda) = E_\odot(\lambda)\sum_{j} T(c \to x_j)\Bigl[
   T(x_j \to \odot)\bigl(\beta_R\rho_R P_R(\theta) + \beta_M\rho_M P_M(\theta)\bigr)
   + \Psi_\mathrm{ms}(x_j)\bigl(\beta_R\rho_R + \beta_M\rho_M\bigr)\Bigr]\Delta s .
```

$T(c \to x_j)$ follows without another integration as the ratio of two transmittances to the top,
$T(c \to \mathrm{top}) / T(x_j \to \mathrm{top})$ (inverted when looking down). $\Psi_\mathrm{ms}$
is the contribution of multiple scattering (next section; 0 in the original).

### 9.5 Multiple scattering (Hillaire 2020)

In twilight only the high atmosphere is still sunlit. Its light reaches the shadowed air below only
by scattering more than once. Hillaire [24, sec. 5.5] approximates all orders from the second on
with two assumptions: light of higher orders arrives isotropically, and the surroundings of a point
are everywhere like the point itself. The sum over all orders then becomes a geometric series:

```math
\Psi_\mathrm{ms} = L_2\,(1 + f_\mathrm{ms} + f_\mathrm{ms}^2 + \dots) = \frac{L_2}{1 - f_\mathrm{ms}} .
```

For a point at height $h$ with the Sun at local altitude $h_\odot$, the code integrates over
$N = 64$ evenly spread directions $\vec\omega$ (Fibonacci lattice on the sphere [29]), each ray in
24 steps to the ground or the top:

```math
\begin{aligned}
L_2 &= \frac{1}{N}\sum_{\vec\omega}\Bigl[\int T(x,y)\,\beta_s(y)\,T(y\to\odot)\,\frac{1}{4\pi}\,ds
       + T(x,y_g)\,T(y_g\to\odot)\,\max(0,\vec n\cdot\vec s)\,\frac{a}{\pi}\Bigr],\\
f_\mathrm{ms} &= \frac{1}{N}\sum_{\vec\omega}\int T(x,y)\,\beta_s(y)\,ds,
\qquad \beta_s = \beta_R\rho_R + \beta_M\rho_M .
\end{aligned}
```

The second summand in $L_2$ is the light reflected diffusely (Lambertian) by the sunlit ground,
where the ray hits the ground ($\vec n$ ground normal, $\vec s$ Sun direction, $a$ albedo). The
mean $\frac1N\sum$ stands for $\int \frac{1}{4\pi}\,d\omega$, the isotropic phase function at the
point itself.

$\Psi_\mathrm{ms}$ is precomputed per spectral band as a table: local Sun altitude −30°…90° in 1°
steps × 32 heights, denser toward the ground ($h = u^2 \cdot 100\,\mathrm{km}$, $u$ evenly spaced).
It is looked up bilinearly. Hillaire lays out the same table over $\cos$(Sun zenith angle) and
height [24].

---

## 10. Sky colors: spectrum, colorimetry, image processing

Code: [`Spectrum.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Spectrum.kt) and
`toSrgb` in [`SkyModel.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyModel.kt).

### 10.1 Spectral data

48 bands from 360 to 830 nm in 10 nm steps, the tables from the demo of Bruneton's *Precomputed
Atmospheric Scattering* (2017 version) [26]:

- **Solar spectrum above the atmosphere** in W/(m²·nm): the "ETR" (extraterrestrial) column of the
  reference spectrum ASTM G-173 [30], averaged over each 10 nm band.
- **Ozone absorption cross-section** in m² per molecule: reference spectra of the University of
  Bremen (IUP) at 233 K [31], averaged over each band.
- **Ozone column** 300 Dobson units (1 DU = 2.687·10²⁰ molecules/m²). Spread over the triangular
  profile (integral 15 km), the peak density is

  ```math
  n_\mathrm{max} = \frac{300 \cdot 2.687\cdot10^{20}\,\mathrm{m^{-2}}}{15\,000\,\mathrm{m}}
  \approx 5.37\cdot10^{18}\,\mathrm{m^{-3}},\qquad
  \beta_O(\lambda) = \sigma_{O_3}(\lambda)\,n_\mathrm{max}.
  ```

- **Rayleigh** $\beta_R(\lambda) = 1.24062\cdot10^{-6}\,\mathrm{m^{-1}}\cdot(\lambda/\mu\mathrm{m})^{-4}$ [26],
  so at 680 nm exactly the original's RGB value 5.802·10⁻⁶.

Only the spectral computation renders the blue of the "blue hour" correctly. It comes mostly from
ozone's Chappuis absorption, which spans broadly from yellow to red (Hulburt 1953 [37]). With only
three wavelengths it turns into a purple.

### 10.2 Spectrum → color

The radiance $L(\lambda)$ is integrated to XYZ with the CIE 1931 color matching functions. These are
replaced by the analytic multi-lobe Gaussian fit of Wyman, Sloan and Shirley (2013) [32, sec. 2.2]. The widths $\sigma_1, \sigma_2$ are given here in nm;
table 1 of the paper lists their reciprocals:

```math
X = \sum_\lambda L(\lambda)\,\bar x(\lambda)\,\Delta\lambda \ \ (\text{likewise } Y, Z),\qquad
g(\lambda;\mu,\sigma_1,\sigma_2) = \exp\!\Bigl(-\tfrac12\bigl(\tfrac{\lambda-\mu}{\sigma}\bigr)^2\Bigr),\
\sigma = \begin{cases}\sigma_1 & \lambda<\mu\\ \sigma_2 & \lambda\ge\mu\end{cases}
```

```math
\begin{aligned}
\bar x &= 1.056\,g(599.8;\,37.9,\,31.0) + 0.362\,g(442.0;\,16.0,\,26.7) - 0.065\,g(501.1;\,20.4,\,26.2)\\
\bar y &= 0.821\,g(568.8;\,46.9,\,40.5) + 0.286\,g(530.9;\,16.3,\,31.1)\\
\bar z &= 1.217\,g(437.0;\,11.8,\,36.0) + 0.681\,g(459.0;\,26.0,\,13.8)
\end{aligned}
```

XYZ → linear sRGB with the matrix for the sRGB/Rec. 709 primaries and white point D65
(IEC 61966-2-1 [33], numbers after Lindbloom [34]):

```math
\begin{pmatrix}R\\G\\B\end{pmatrix} =
\begin{pmatrix}
 3.2404542 & -1.5371385 & -0.4985314\\
-0.9692660 &  1.8760108 &  0.0415560\\
 0.0556434 & -0.2040259 &  1.0572252
\end{pmatrix}
\begin{pmatrix}X\\Y\\Z\end{pmatrix}.
```

**White balance.** Each channel is divided by the value sunlight above the atmosphere would have.
Unfiltered sunlight is thus exactly white (1, 1, 1), as in the original with its white sun. The
colors are therefore relative to sunlight, not to D65.

### 10.3 Exposure and eye adaptation

The code computes luminance in linear RGB with the Rec. 709 weights [35]:
$Y = 0.2126R + 0.7152G + 0.0722B$.

- **Original:** fixed exposure 25.
- **Ring:** the exposure is scaled so that the horizon at a Sun altitude of 10° is as bright as in
  the original: $E_\mathrm{ring} = 25 \cdot Y_\mathrm{orig}(10°)/Y_\mathrm{ring}(10°)$. On top of
  that comes a partial, capped **adaptation to the dark**, with $Y_h$ = luminance of the horizon:

  ```math
  A = \operatorname{clamp}\!\left(\left(\frac{Y_\mathrm{ref}}{Y_h}\right)^{0.5},\ 1,\ 1000\right).
  ```

  This is not a physiological model but a heuristic: like the eye, it lifts the blue of deep
  twilight into view, while the sky as a whole still darkens toward night (exponent < 1, at most
  1000-fold).

### 10.4 Tone mapping

After exposure, per channel:

1. **Warm bias for dark skies** (taken from Horizon, purely aesthetic): with $w = 1/(1 + 2Y)$,
   $R \cdot (1 + 0.05w)$, $G \cdot (1 - 0.05w)$, $B \cdot (1 + 0.1w)$.
2. **ACES filmic curve** in Narkowicz' fit (2016) [36]:

   ```math
   f(x) = \operatorname{clamp}\!\left(\frac{x\,(2.51x + 0.03)}{x\,(2.43x + 0.59) + 0.14},\ 0,\ 1\right).
   ```

3. **Gamma** $f^{1/2.2}$ as an approximation of the sRGB transfer function [33], then to 0…255.

### 10.5 Averaging over azimuth

The ring has no compass direction. For each elevation the sky is therefore averaged all around: the
**linear** RGB (before tone mapping) is averaged over 12 azimuths. Since the sky is mirror-symmetric
about the plane through Sun and zenith, only 6 are computed (15°, 45°, …, 165° from the Sun). This
brings in the red glow on the Sun's side without the solar disc itself dominating, since no sample
lies at 0°.

---

## 11. Sky colors: projection and table

### 11.1 Projecting elevation onto the ring

Code: [`SkyProjection.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyProjection.kt).
A ring segment shows the sky from the horizon (fraction 0) up to an adjustable elevation $e_t$
(fraction 1). Elevations are spread as in a perspective photo, i.e. evenly in $\tan e$ (gnomonic
projection):

```math
u(e) = \frac{\tan e}{\tan e_t},\qquad e(u) = \arctan(u\,\tan e_t),\qquad e_t \le 80° .
```

The limit of 80° (and $|e| \le 89°$) keeps $\tan$ finite. The original uses the same mapping with
$e_t = 37.5°$ (half of a vertical field of view of 75°).

### 11.2 Precomputed table

Code: [`SkyGradientTable.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyGradientTable.kt).
The scattering computation is too expensive for every minute. The Gradle task
`:core:generateSkyTable` therefore builds a table at build time: Sun altitude −18°…90° in 0.5°
steps (217 rows) × view elevation 0°…80° in 1° steps (81 columns), 3 bytes of sRGB each. At run
time it is interpolated bilinearly. Sun altitudes below −18° count as −18°. A test checks that the
bundled table matches the model.

---

## 12. Drawing on the dial

Code: [`ClockFaceRenderer.kt`](../app/src/main/kotlin/com/umfluten/lighttimeclouds/widget/ClockFaceRenderer.kt).
Little new is computed here; this section records how the quantities above reach the dial, and
where the drawing deliberately departs from the physics.

### 12.1 Ring color

Each ring segment stands for a moment $t$. For it, the **geometric** Sun altitude $h_\odot(t)$ at
the place is computed, and:

- **"Sky":** 24 colors from the table (section [11.2](#112-precomputed-table)) from the horizon up
  to $e_t$, laid over a dark night color with a *screen* blend $1 - (1 - a)(1 - b)$. At
  $h_\odot \le -18°$ (end of astronomical twilight) only the night color remains. Moonlight, stars
  and airglow are not in the model; the night color stands in for them.
- **"Gradient" / "phase colors":** designed colors, linearly interpolated between stops at the
  physical thresholds −18°, −12°, −6°, −0.833°, 6° and 90°, or one fixed color per sky state
  (section [6.4](#64-sky-state)).

### 12.2 Sun and Moon in the ring

The distance from the horizon edge of the ring band follows the same projection as the sky colors,
$u = \tan h / \tan e_t$. The bodies thus sit exactly where their altitude lies in the sky gradient.
Below the horizon ($h < 0$) they are not drawn; above $e_t$ they appear as a small dot at the edge
opposite the horizon. The Sun uses the geometric altitude (like the ring color), the Moon the
apparent one, with refraction. The optional moon path is $h_\mathrm{Moon}(t)$ over the shown time
span. Below the horizon it leaves the band (limited to $u \ge -0.5$ and clipped at the edge); above
$e_t$ it runs along the edge opposite the horizon ($u = 1$), where the Moon's dot then sits.

### 12.3 Moon path with a separate hand or on the hour hand

The distance from the center is $r = r_\mathrm{horizon} + r_A \sin h_\mathrm{Moon}$ (outward above,
inward below the horizon). The angle is the time itself (on the hour hand; the path then crosses
the horizon circle at the actual moonrise and moonset times) or "time minus lag"
(section [7.3](#73-lag-behind-the-sun)). The compass points N/E/S/W are marked where the azimuth
crosses −180°, −90°, 0°, 90° or 180°, linearly interpolated between samples; for this the azimuth
step is continued across ±180°.

### 12.4 Moon phase disc

The phase disc shows the Moon as one sees it at the place, with the top of the screen as the
zenith. The terminator, the boundary between day and night on the lunar sphere, is a great circle;
seen from the Earth it appears as half an ellipse. In a frame with $x$ toward the bright limb
(turned by the angle from section [7.4](#74-tilt-of-the-crescent)) and $y$ across it, a point is
lit where

```math
x \ \ge\ -(2k - 1)\sqrt{r^2 - y^2} .
```

This is the half disc on the bright side, enlarged by half an ellipse with semi-axis $r(2k-1)$
(gibbous) or reduced by one (crescent). The lit area is exactly $k\,\pi r^2$, since
$2k - 1 = \cos i$ with the phase angle $i$ [1, ch. 48]. A test compares the rendered disc pixel by
pixel with this shape.

The disc keeps the same orientation everywhere, even when it travels around the dial with the moon
hand: up is always the zenith.

---

## 13. Weather quantities

Code: [`WeatherForecast.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/weather/WeatherForecast.kt),
[`WeatherOutlook.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/weather/WeatherOutlook.kt).
The app does not compute weather values itself; they come hourly from Open-Meteo [38]. The app
only decides how they are read and classified.

### 13.1 Meaning of the quantities

After Open-Meteo's definitions [38]:

| Quantity | Unit | Refers to |
|---|---|---|
| `cloud_cover` | % | Total cloud cover as an area fraction, instant value at the hour |
| `precipitation` | mm | Sum of rain, showers and snow (as water) over the **preceding** hour |
| `snowfall` | cm | Fresh snow over the preceding hour |
| `precipitation_probability` | % | Probability of more than 0.1 mm in the preceding hour (from ensemble models) |
| `temperature_2m` | °C | Air temperature at 2 m |
| `apparent_temperature` | °C | Feels-like temperature from wind chill, relative humidity and solar radiation |

A ring segment (one hour) gets the amounts of the hour it covers and the mean of the instant values
at its ends. Temperatures at arbitrary moments are interpolated linearly between the hourly values.

### 13.2 Rain or snow

Open-Meteo gives the conversion: 7 cm of snow ≈ 10 mm of water [38]. An hour counts as snow if at
least half of the amount falls as snow:

```math
\frac{10}{7}\cdot s_\mathrm{cm} \ \ge\ \frac{P_\mathrm{mm}}{2}.
```

With only a probability and no amount (below 0.1 mm), the air temperature decides: ≤ 0 °C counts as
snow.

### 13.3 Intensity and warning levels

Classes of the amount per hour after the German Weather Service (DWD):

| Class | from mm/h | Source |
|---|---|---|
| very light (probability only, or from 0.1 mm) | 0.1 | Open-Meteo's threshold for "wet" [38] |
| light | 0.5 | DWD: threshold of "heavy drizzle" [39] |
| moderate | 2.5 | DWD: moderate rain [39] |
| heavy | 10 | DWD: heavy rain [39] |

For rain (not snow), additionally the DWD's heavy rain warning levels for one hour [40]: marked from
15 l/m², severe from 25 l/m², extreme from 40 l/m².

The opacity of the precipitation color is its probability, but at least 25 % for a forecast amount.

### 13.4 Weather in words

Fixed rules, not physics: the apparent temperature is mentioned when it differs from the air
temperature by at least 3 °C. The comparison with yesterday uses the air temperature at the same
time of day. Precipitation is checked for the next 3 and 6 hours; snow takes precedence over rain.

---

## 14. Simplifications at a glance

| Simplification | Effect | Section |
|---|---|---|
| UT1 = UTC | ≤ 0.9 s of rotation, ≤ 14″ on the sky | [2](#2-time-scales) |
| Leap second table fixed in the code (up to 2017) | a future leap second shifts TT by 1 s (Moon ≈ 0.5″) | [2](#2-time-scales) |
| Polar motion ignored | < 0.5″ | [3.4](#34-sidereal-time) |
| Nutation cut to 5 + 4 terms, polynomials up to $T^2$ or $T^3$ | ≈ 0.2″ | [3](#3-earth-orientation) |
| Series truncated (section 4.6) | up to 4″ (Moon), 2″ (Sun) between 2000 and 2050 | [4.6](#46-truncating-the-series) |
| Light time of the Moon ignored | < 1″ | [4.2](#42-moon-elp-2000-82b) |
| Observer at sea level | horizon and parallax for height 0 | [5.1](#51-place-on-the-wgs84-ellipsoid-and-parallax) |
| Standard atmosphere for refraction (10 °C, 1010 hPa) | at the horizon the true refraction varies by several arcminutes | [5.3](#53-refraction) |
| Moon phase from geocentric positions | difference from the topocentric phase negligible | [7.2](#72-illuminated-fraction-and-phase) |
| Tilt of the crescent from geocentric positions | difference from the topocentric view through the lunar parallax (≤ 1°), somewhat more for a thin crescent | [7.4](#74-tilt-of-the-crescent) |
| Sky without clouds, fixed aerosols and 300 DU of ozone, without moonlight and night sky | the sky color is that of a clear standard sky | [9](#9-sky-colors-the-scattering-model), [12.1](#121-ring-color) |
| Multiple scattering isotropic and locally homogeneous (Hillaire) | small errors in the brightness distribution of deep twilight | [9.5](#95-multiple-scattering-hillaire-2020) |
| Eye adaptation, warm bias, tone mapping | aesthetic, not photometric | [10.3](#103-exposure-and-eye-adaptation), [10.4](#104-tone-mapping) |

---

## 15. Sources

**Astronomy**

1. J. Meeus: *Astronomical Algorithms*, 2nd ed., Willmann-Bell, Richmond 1998.
2. S. E. Urban, P. K. Seidelmann (eds.): *Explanatory Supplement to the Astronomical Almanac*,
   3rd ed., University Science Books, Mill Valley 2013.
3. International Astronomical Union: Resolution B2 (2012), re-definition of the astronomical unit.
   <https://syrte.obspm.fr/IAU_resolutions/Res_IAU2012_B2.pdf>
4. G. Petit, B. Luzum (eds.): *IERS Conventions (2010)*, IERS Technical Note No. 36,
   Verlag des Bundesamts für Kartographie und Geodäsie, Frankfurt am Main 2010.
   <https://www.iers.org/TN36>
5. IERS Earth Orientation Centre: *Bulletin C* (leap seconds, TAI − UTC).
   <https://hpiers.obspm.fr/eoppc/bul/bulc/>
6. J. L. Simon, P. Bretagnon, J. Chapront, M. Chapront-Touzé, G. Francou, J. Laskar: Numerical
   expressions for precession formulae and mean elements for the Moon and the planets.
   *Astronomy & Astrophysics* 282, 663–683 (1994).
7. P. M. Mathews, T. A. Herring, B. A. Buffett: Modeling of nutation and precession: New nutation
   series for nonrigid Earth and insights into the Earth's interior. *Journal of Geophysical
   Research* 107 (B4), 2068 (2002). <https://doi.org/10.1029/2001JB000390>
8. N. Capitaine, P. T. Wallace, J. Chapront: Expressions for IAU 2000 precession quantities.
   *Astronomy & Astrophysics* 412, 567–586 (2003). <https://doi.org/10.1051/0004-6361:20031539>
9. N. Capitaine, B. Guinot, D. D. McCarthy: Definition of the Celestial Ephemeris Origin and of UT1
   in the International Celestial Reference Frame. *Astronomy & Astrophysics* 355, 398–405 (2000).
10. P. Bretagnon, G. Francou: Planetary theories in rectangular and spherical variables. VSOP 87
    solutions. *Astronomy & Astrophysics* 202, 309–315 (1988).
11. M. Chapront-Touzé, J. Chapront: The lunar ephemeris ELP 2000. *Astronomy & Astrophysics* 124,
    50–62 (1983).
12. M. Chapront-Touzé, J. Chapront: ELP 2000-85: a semi-analytical lunar ephemeris adequate for
    historical times. *Astronomy & Astrophysics* 190, 342–352 (1988).
13. J. Laskar: Secular terms of classical planetary theories using the results of general theory.
    *Astronomy & Astrophysics* 157, 59–70 (1986).
14. IMCCE, Observatoire de Paris: data server for ELP 2000-82B and VSOP87.
    <https://ftp.imcce.fr/pub/ephem/moon/elp82b/>, <https://ftp.imcce.fr/pub/ephem/planets/vsop87/>
15. National Imagery and Mapping Agency: *Department of Defense World Geodetic System 1984*,
    NIMA TR8350.2, 3rd ed., Amendment 1 (3 January 2000).
16. Þ. Sæmundsson: Astronomical Refraction. *Sky and Telescope* 72, 70 (July 1986).
17. U.S. Naval Observatory, Astronomical Applications Department: Rise, Set, and Twilight
    Definitions. <https://aa.usno.navy.mil/faq/RST_defs>
18. V. Agafonkin: *SunCalc* (conventions for −0.3° and the golden hour).
    <https://github.com/mourner/suncalc>
19. M. Dowell, P. Jarratt: A modified regula falsi method for computing the root of an equation.
    *BIT Numerical Mathematics* 11, 168–174 (1971). <https://doi.org/10.1007/BF01934364>
20. B. Rhodes: *Skyfield: High precision research-grade positions for planets and Earth
    satellites generator*. Astrophysics Source Code Library, ascl:1907.024 (2019).
    <https://rhodesmill.org/skyfield/>
21. R. S. Park, W. M. Folkner, J. G. Williams, D. H. Boggs: The JPL Planetary and Lunar Ephemerides
    DE440 and DE441. *The Astronomical Journal* 161, 105 (2021).
    <https://doi.org/10.3847/1538-3881/abd414>

**Atmosphere and color**

22. D. Lazaro: *Horizon* (MIT License). <https://github.com/dnlzro/horizon>
23. A. Helmer: *Production Sky Rendering*, Shadertoy (2021). <https://www.shadertoy.com/view/slSXRW>
24. S. Hillaire: A Scalable and Production Ready Sky and Atmosphere Rendering Technique.
    *Computer Graphics Forum* 39 (4), 13–22 (2020), Proc. EGSR.
    <https://doi.org/10.1111/cgf.14050>; reference implementation:
    <https://github.com/sebh/UnrealEngineSkyAtmosphere>
25. E. Bruneton, F. Neyret: Precomputed Atmospheric Scattering. *Computer Graphics Forum* 27 (4),
    1079–1086 (2008). <https://doi.org/10.1111/j.1467-8659.2008.01245.x>
26. E. Bruneton: *Precomputed Atmospheric Scattering*, new implementation of 2017 with demo
    (`atmosphere/demo/demo.cc`, BSD-3-Clause).
    <https://github.com/ebruneton/precomputed_atmospheric_scattering>
27. S. Chandrasekhar: *Radiative Transfer*, Oxford University Press 1950 (Dover 1960).
28. W. M. Cornette, J. G. Shanks: Physically reasonable analytic expression for the
    single-scattering phase function. *Applied Optics* 31 (16), 3152–3160 (1992).
    <https://doi.org/10.1364/AO.31.003152>
29. Á. González: Measurement of Areas on a Sphere Using Fibonacci and Latitude–Longitude Lattices.
    *Mathematical Geosciences* 42, 49–64 (2010). <https://doi.org/10.1007/s11004-009-9257-x>
30. ASTM G173-03: *Standard Tables for Reference Solar Spectral Irradiances: Direct Normal and
    Hemispherical on 37° Tilted Surface*, ASTM International. Data: National Laboratory of the
    Rockies (formerly NREL), <https://www.nlr.gov/grid/solar-resource/spectra-am1.5>
31. A. Serdyuchenko, V. Gorshelev, M. Weber, W. Chehade, J. P. Burrows: High spectral resolution
    ozone absorption cross-sections – Part 2: Temperature dependence. *Atmospheric Measurement
    Techniques* 7, 625–636 (2014). <https://doi.org/10.5194/amt-7-625-2014>; data: IUP Bremen,
    Molecular Spectroscopy, O₃ reference spectra 2011.
32. C. Wyman, P.-P. Sloan, P. Shirley: Simple Analytic Approximations to the CIE XYZ Color Matching
    Functions. *Journal of Computer Graphics Techniques* 2 (2), 1–11 (2013).
    <https://jcgt.org/published/0002/02/01/>
33. IEC 61966-2-1:1999: *Multimedia systems and equipment – Colour measurement and management –
    Part 2-1: Default RGB colour space – sRGB*.
34. B. Lindbloom: RGB/XYZ Matrices. <http://www.brucelindbloom.com/index.html?Eqn_RGB_XYZ_Matrix.html>
35. ITU-R Recommendation BT.709-6: *Parameter values for the HDTV standards for production and
    international programme exchange* (2015).
36. K. Narkowicz: ACES Filmic Tone Mapping Curve (2016).
    <https://knarkowicz.wordpress.com/2016/01/06/aces-filmic-tone-mapping-curve/>
37. E. O. Hulburt: Explanation of the Brightness and Color of the Sky, Particularly the Twilight
    Sky. *Journal of the Optical Society of America* 43 (2), 113–118 (1953).
    <https://doi.org/10.1364/JOSA.43.000113>

**Weather**

38. Open-Meteo: Weather Forecast API, description of the hourly variables (CC BY 4.0).
    <https://open-meteo.com/en/docs>
39. Deutscher Wetterdienst: Wetterlexikon, "Niederschlagsintensität" (precipitation intensity).
    <https://www.dwd.de/DE/service/lexikon/begriffe/N/Niederschlagsintensitaet.html>
40. Deutscher Wetterdienst: Warnkriterien (heavy rain warning criteria).
    <https://www.dwd.de/DE/wetter/warnungen_aktuell/kriterien/warnkriterien.html>

**Supplement: refraction**

41. G. G. Bennett: The Calculation of Astronomical Refraction in Marine Navigation. *Journal of
    Navigation* 35 (2), 255–259 (1982). <https://doi.org/10.1017/S0373463300022037>

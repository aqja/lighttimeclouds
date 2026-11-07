# Physics and Astronomy in LightTimeClouds

*Deutsche Fassung: [PHYSIK.md](PHYSIK.md)*

This document describes every physical and astronomical computation in the app: what is computed,
with which formulas and constants, where it lives in the code, where model and numbers come from,
and which simplifications were made on purpose. Sources are numbered at the end
([Sources](#15-sources)); references in the text look like this: [7]. How the computed light is
turned into colors as a human eye sees them is described in [PHYSIOLOGY.md](PHYSIOLOGY.md).

Almost all of the computation is in the `core` module (plain Kotlin/JVM, no Android):

| Area | File(s) |
|---|---|
| Time scales | [`astro/TimeScales.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/TimeScales.kt) |
| Earth orientation (nutation, obliquity, sidereal time) | [`astro/EarthOrientation.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/EarthOrientation.kt) |
| Ephemerides of Sun and Moon | [`astro/Ephemeris.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/Ephemeris.kt), [`astro/AstroSeries.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/AstroSeries.kt) (generated) |
| Observer's place, horizontal coordinates, refraction | [`astro/Topocentric.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/Topocentric.kt) |
| Events, moon phase, public API | [`astro/SunMoonCalculator.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/SunMoonCalculator.kt), [`astro/SolarDay.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/SolarDay.kt) |
| Atmospheric scattering model | [`sky/SkyModel.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyModel.kt) |
| Aerosols (Mie theory, size distribution) | [`sky/Mie.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Mie.kt), [`sky/Aerosol.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Aerosol.kt) |
| Spectrum and colorimetry | [`sky/Spectrum.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Spectrum.kt) |
| Mapping elevation → ring | [`sky/SkyProjection.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyProjection.kt) |
| Precomputed color tables | [`sky/SkyGradientTable.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyGradientTable.kt), [`sky/SunTable.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SunTable.kt), [`sky/SkyTableGenerator.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyTableGenerator.kt) |
| Weather quantities and thresholds | [`weather/WeatherForecast.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/weather/WeatherForecast.kt), [`weather/WeatherOutlook.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/weather/WeatherOutlook.kt) |
| Drawing on the dial | [`widget/ClockFaceRenderer.kt`](../app/src/main/kotlin/com/umfluten/lighttimeclouds/widget/ClockFaceRenderer.kt) and the files next to it (module `app`) |
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

For the ring style "phase colors", `classify` maps the Sun's altitude to a
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
on Bruneton and Neyret (2008) [25]. The app extends it with multiple scattering after
Hillaire [24, sec. 5.5], with a spectral computation using data from Bruneton's implementation
[26] (section [10](#10-sky-colors-spectrum-colorimetry-image-processing)), with aerosols from Mie
theory and measured size distributions (section [9.6](#96-aerosols-mie-theory-and-a-measured-size-distribution))
and with the night sky's own glow (section [9.7](#97-the-night-sky)).

The ring computes with 48 wavelengths from 360 to 830 nm, the solar spectrum ASTM G-173 above the
atmosphere, an urban aerosol with $\tau(440\,\mathrm{nm}) = 0.2$, single and multiple scattering
and 32 steps growing with distance along the view ray, averaged over 12 azimuths (`ringGradient`).
There is no fixed exposure; the sky is shown as the eye sees it ([PHYSIOLOGY.md](PHYSIOLOGY.md)).

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
| Rayleigh scattering $\beta_R$ | $1.24062\cdot10^{-6}\,(\lambda/\mu\mathrm{m})^{-4}$ | [24; 26], section [10.1](#101-spectral-data) |
| Mie extinction $\beta_M^\mathrm{ext}$ and scattering $\beta_M$ | $\tau(\lambda)/1.2\,\mathrm{km}$ | section [9.6](#96-aerosols-mie-theory-and-a-measured-size-distribution) |
| Ozone absorption $\beta_O$ | $\sigma_{O_3}(\lambda)\cdot n_\mathrm{max}$ | section [10.1](#101-spectral-data) |
| Ground albedo $a$ | 0.3 | as in Hillaire's paper [24] (multiple scattering only) |

Rayleigh scatters without absorbing, ozone only absorbs, aerosol does both.

### 9.2 Phase functions

Rayleigh [27]:

```math
P_R(\theta) = \frac{3}{16\pi}\,(1 + \cos^2\theta).
```

$\theta$ is the angle between the view and Sun directions. For the aerosol, Hillaire [24] and
Bruneton [26] use the approximation of Cornette and Shanks (1992) [28]. It is smooth near the Sun,
only twice as strong at 0° as at 10°. The ring uses the phase function $P_M$ of a measured aerosol
from Mie theory instead, with a real forward peak (section [9.6](#96-aerosols-mie-theory-and-a-measured-size-distribution)).

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
up to the top, growing with the square of the distance, $s_j = s_\mathrm{top}\,(j/32)^2$. At each midpoint $x_j$, the sunlight scattered there is collected and attenuated on
its way to the observer:

```math
L(\lambda) = E_\odot(\lambda)\sum_{j} T(c \to x_j)\Bigl[
   T(x_j \to \odot)\bigl(\beta_R\rho_R P_R(\theta) + \beta_M\rho_M P_M(\theta)\bigr)
   + \Psi_\mathrm{ms}(x_j)\bigl(\beta_R\rho_R + \beta_M\rho_M\bigr)\Bigr]\Delta s .
```

$T(c \to x_j)$ is the optical depth summed along the way, $\Psi_\mathrm{ms}$ the contribution of
multiple scattering (next section). Looking along the horizon, the first
step is about 1 km long instead of 35 km: the haze near the ground lies within the first few
steps, which with even steps gave a wrong, brownish hue to the horizon.

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

### 9.6 Aerosols: Mie theory and a measured size distribution

Code: [`Mie.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Mie.kt),
[`Aerosol.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Aerosol.kt).
Real haze scatters strongly toward the side and has a sharp forward peak, which makes the bright
aureole around the Sun. The ring's aerosol is computed from a measured particle population.

**Size distribution.** AERONET, the worldwide network of sun photometers, derives the aerosol's
volume size distribution from measurements of the sky around the Sun. Dubovik et al. [43] tabulate
it for typical aerosol types as two lognormal modes whose parameters grow with the optical
thickness $\tau_{440}$ at 440 nm:

```math
\frac{dV}{d\ln r} = \sum_{i=1}^{2}\frac{C_i}{\sqrt{2\pi}\,\sigma_i}
  \exp\!\left(-\frac{(\ln r - \ln r_i)^2}{2\sigma_i^2}\right).
```

The app takes the urban aerosol of Créteil near Paris (1999) [43, table 1], the closest site in the
table to a European city, with $\tau_{440} = 0.2$, a fairly clear day (the mean measured there was
0.26, the table's range 0.1 to 0.9):

| Mode | Volume $C_i$ (µm³/µm²) | Median radius $r_i$ (µm) | $\sigma_i$ |
|---|---|---|---|
| fine | $0.01 + 0.12\,\tau_{440}$ | $0.11 + 0.13\,\tau_{440}$ | 0.43 |
| coarse | $0.01 + 0.05\,\tau_{440}$ | $2.76 + 0.48\,\tau_{440}$ | 0.79 |

with the refractive index $1.40 + 0.009i$, taken as independent of the wavelength.

**Mie theory.** Each particle is a homogeneous sphere. Its extinction and scattering efficiencies
$Q_\mathrm{ext}, Q_\mathrm{sca}$ and its scattering amplitudes $S_1, S_2$ follow from the
Lorenz-Mie series, computed with the algorithm BHMIE of Bohren and Huffman [42] (logarithmic
derivative by downward recursion, Riccati-Bessel functions upward, angular functions $\pi_n, \tau_n$).
Summed over 160 radii from 0.01 to 20 µm (evenly in $\ln r$), for each of the 48 wavelengths:

```math
\tau_\mathrm{ext}(\lambda) = \int \frac{dV/d\ln r}{\tfrac43\pi r^3}\,\pi r^2\,Q_\mathrm{ext}\,d\ln r,\qquad
P_M(\theta,\lambda) = \frac{\int N(r)\,\tfrac12\bigl(|S_1|^2 + |S_2|^2\bigr)\,d\ln r}{k^2\int N(r)\,\pi r^2 Q_\mathrm{sca}\,d\ln r},
```

likewise $\tau_\mathrm{sca}$ with $Q_\mathrm{sca}$; $N(r)$ is the number of particles per $d\ln r$,
$k = 2\pi/\lambda$. The phase function is tabulated at 721 angles, densest near 0°. Tests check the
algorithm against the example run of Bohren and Huffman ($Q = 3.10543$, backscatter 2.92534), the
normalization $\int P\,d\Omega = 1$ and the Rayleigh limit.

**Result** for $\tau_{440} = 0.2$, compared with the values Dubovik et al. measured at Créteil:

| Quantity | Model | Measured [43] |
|---|---|---|
| Optical thickness at 440 / 550 / 670 nm | 0.208 / 0.136 / 0.092 | 0.2 (given) |
| Single-scattering albedo at 440 / 670 nm | 0.92 / 0.89 | 0.94 / 0.93 ± 0.03 |
| Asymmetry parameter $g$ at 440 / 670 nm | 0.67 / 0.57 | 0.68 / 0.61 ± 0.07 |
| Ångström exponent 440–670 nm | 1.93 | 1.2 to 2.3 |
| Phase function at 550 nm, 0° / 3° / 10° / 90° | 6.0 / 1.8 / 0.57 / 0.026 sr⁻¹ | |

**In the model** the aerosol has the height profile of section 9.1: $\beta(h) = \tau(\lambda)/H \cdot e^{-h/H}$
with $H = 1.2$ km. Looking along the horizon, the line of sight thus crosses about 100 times the
vertical optical thickness. At sunset this haze near the ground is hardly lit any more: the
horizon itself darkens under a brighter band, as on hazy evenings. The share of light from the
Sun's surroundings out to 2.5° (circumsolar ratio, in luminance) comes out at 0.4 % with the Sun
at 40°, 1.4 % at 10° and 5.7 % at 2°.

### 9.7 The night sky

Without the Sun the sky still glows: airglow, starlight, zodiacal light. The model adds the natural
night sky brightness that Falchi et al. [44] assume for the background away from the Milky Way at
low solar activity, 22.0 mag/arcsec² or 174 µcd/m², everywhere in the sky and with the Sun's
spectrum. Light pollution is not included. This glow sets the night's color
([PHYSIOLOGY.md](PHYSIOLOGY.md), section 5).

---

## 10. Sky colors: spectrum, colorimetry, image processing

Code: [`Spectrum.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Spectrum.kt) and
`perceiveLight` in [`SkyModel.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyModel.kt).

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

- **Rayleigh** $\beta_R(\lambda) = 1.24062\cdot10^{-6}\,\mathrm{m^{-1}}\cdot(\lambda/\mu\mathrm{m})^{-4}$ [26].

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
Unfiltered sunlight is thus exactly white (1, 1, 1). The colors are therefore relative to sunlight, not to D65.

### 10.3 Exposure

There is no fixed exposure. The light is taken in absolute units (luminance in cd/m²) and turned
into display colors as an eye adapted to the whole sky sees them: the brightness after Stevens' law,
the hue with the rods' contribution in dim light, the colors fading as it gets dark. All of it is
described in [PHYSIOLOGY.md](PHYSIOLOGY.md). The code computes a color's luminance with the Rec. 709
weights [35]: $Y = 0.2126R + 0.7152G + 0.0722B$.

### 10.4 Tone mapping

At the end, on the brightest channel (see [PHYSIOLOGY.md](PHYSIOLOGY.md), section 6):

1. **ACES filmic curve** in Narkowicz' fit (2016) [36]:

   ```math
   f(x) = \operatorname{clamp}\!\left(\frac{x\,(2.51x + 0.03)}{x\,(2.43x + 0.59) + 0.14},\ 0,\ 1\right).
   ```

2. **Gamma** $f^{1/2.2}$ as an approximation of the sRGB transfer function [33], then to 0…255.

### 10.5 Averaging over azimuth

The ring has no compass direction. For each elevation the sky is therefore averaged all around: the
spectral radiance (before anything nonlinear) is averaged over 12 azimuths. Since the sky is mirror-symmetric
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

The limit of 80° (and $|e| \le 89°$) keeps $\tan$ finite.

### 11.2 Precomputed table

Code: [`SkyGradientTable.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyGradientTable.kt).
The scattering computation is too expensive for every minute. The Gradle task
`:core:generateSkyTable` therefore builds the tables at build time:

- **Sky colors:** Sun altitude −18°…90° in 0.5° steps (217 rows) × view elevation 0°…80° in 1°
  steps (81 columns), 3 bytes of sRGB each, interpolated bilinearly at run time. Sun altitudes
  below −18° count as −18°: by then only the night sky's own glow is left.
- **Sun** ([`SunTable.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SunTable.kt)):
  Sun altitude 0°…90° in 0.5° steps × the color of the disc and 24 colors of its aureole from the
  disc's edge out to 12° (section [12.2](#122-sun-and-moon-in-the-ring)).
- **Moon** ([`MoonTable.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/MoonTable.kt)):
  the color of its lit part for Moon altitudes 0°…90° in 1° steps, Sun altitudes −18°…90° in 2°
  steps and a lit share of 2, 10, 30, 60 and 100 %, interpolated in all three.
- **Moonlit sky** ([`MoonlitSky.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/MoonlitSky.kt)):
  not colors but the sky's light, as light adds up: for a body's altitude −18°…90° in 0.5° steps and
  view elevation 0°…80° in 1° steps five floats (linear RGB, the rods' signal, luminance), plus the
  sky's mean luminance and the aureole's excess of light. Colors would need a table over the Sun's
  altitude, the Moon's altitude, the phase and the view elevation at once.

Tests check that the bundled tables match the model. As F-Droid rebuilds the app and compares it
byte for byte, the sky model computes with `java.lang.StrictMath`, whose results are the same on
every machine, instead of the platform's math functions, which may differ in the last bit.

---

## 12. Drawing on the dial

Code: [`ClockFaceRenderer.kt`](../app/src/main/kotlin/com/umfluten/lighttimeclouds/widget/ClockFaceRenderer.kt) with [`DayNightRing.kt`](../app/src/main/kotlin/com/umfluten/lighttimeclouds/widget/DayNightRing.kt), [`SkyBodies.kt`](../app/src/main/kotlin/com/umfluten/lighttimeclouds/widget/SkyBodies.kt), [`WeatherRing.kt`](../app/src/main/kotlin/com/umfluten/lighttimeclouds/widget/WeatherRing.kt), [`TemperatureLabels.kt`](../app/src/main/kotlin/com/umfluten/lighttimeclouds/widget/TemperatureLabels.kt) and [`MoonOnDial.kt`](../app/src/main/kotlin/com/umfluten/lighttimeclouds/widget/MoonOnDial.kt).
Little new is computed here; this section records how the quantities above reach the dial, and
where the drawing deliberately departs from the physics.

### 12.1 Ring color

Each ring segment stands for a moment $t$. For it, the **geometric** Sun altitude $h_\odot(t)$ at
the place is computed, and:

- **"Sky":** 24 colors from the table (section [11.2](#112-precomputed-table)) from the horizon up
  to $e_t$. The night's color is the night sky's own glow as the eye sees it (section
  [9.7](#97-the-night-sky)), and what the Moon adds to it (section
  [12.2](#122-sun-and-moon-in-the-ring)).
- **"Phase colors":** one fixed color per sky state (section [6.4](#64-sky-state)), taken from a
  designed palette that is interpolated linearly between stops at the physical thresholds −18°,
  −12°, −6°, −0.833°, 6° and 90°.

### 12.2 Sun and Moon in the ring

The distance from the horizon edge of the ring band follows the same projection as the sky colors,
$u = \tan h / \tan e_t$. The bodies thus sit exactly where their altitude lies in the sky gradient.
Below the horizon ($h < 0$) they are not drawn; above $e_t$ they appear as a small dot at the edge
opposite the horizon. The Sun uses the geometric altitude (like the ring color), the Moon the
apparent one, with refraction. The optional moon path is $h_\mathrm{Moon}(t)$ over the shown time
span. Below the horizon it leaves the band (limited to $u \ge -0.5$ and clipped at the edge); above
$e_t$ it runs along the edge opposite the horizon ($u = 1$), where the Moon's dot then sits.

The Sun's disc takes its color from the sunlight that reaches the observer through the
atmosphere: the spectrum above the atmosphere times the transmittance $T(0,\,90° - h)$ (section
9.3, here in 512 steps for the long path at the horizon, with the ring's aerosol), spread over the
disc's solid angle $\pi\,(0.2666°)^2$, in front of the sky at the Sun's altitude. This radiance is
seen like the sky ([PHYSIOLOGY.md](PHYSIOLOGY.md), section 6). The disc is far brighter than the
display can show, so it is shown at full brightness with its hue kept: high up almost white, a few
degrees above the horizon golden, then orange, and at the horizon deep red through the haze.
Limb darkening is left out: at a few pixels across it cannot be seen.

**Moon.** The Moon's disc is made the same way, but only its lit part is drawn: the dark part lets
the sky through, as it does in the day sky. The radiance of the lit part is the sunlight it
reflects, spread over the lit part's solid angle $k\,\pi\,(0.2590°)^2$, with $k$ the illuminated
fraction. Its brightness relative to the Sun ($V_\odot = -26.74$) follows the phase law Krisciunas
and Schaefer use [45], from the full Moon's magnitude in Allen [46],

```math
V(\alpha) = -12.73 + 0.026\,|\alpha| + 4\cdot 10^{-9}\,\alpha^4
```

with the phase angle $\alpha$ in degrees. This holds at the Moon's mean distance, but the radiance does not depend on the
distance: light and disc change alike. Its spectrum follows the Sun's. The Moon's ground
reflects the red better, and it is 0.26 mag redder in $B-V$ [47], but at night the moonlight is
all the eye has to judge white by, and by day the sky outshines the difference, so it is left
out. Through the same atmosphere as the Sun's light and in front of the sky at the Moon's altitude, the
Moon is white high up in the dark and orange to red near the horizon; by day it adds little to the
sky and stays pale. How the eye sees it is in [PHYSIOLOGY.md](PHYSIOLOGY.md), section 6. The colors
come from a third table over the Moon's altitude, the Sun's altitude and $k$.

**Moonlit sky.** The Moon lights the same atmosphere as the Sun, and the scattering is linear in the
light source: its sky is the sky with the Sun at the Moon's altitude, dimmed by the ratio
$10^{-0.4\,(V_\text{Moon}(\alpha) - V_\odot)}$, $2.5\cdot 10^{-6}$ at full Moon. While the Sun is
below the horizon, the widget adds in every ring segment the Moon's sky at that moment to the Sun's,
also for a Moon just below the horizon, and sees the sum with an eye adapted to both. One phase holds
for the whole ring: it hardly changes over a day. At full Moon the night turns gray-blue, with a
crescent it stays nearly dark, and in twilight the Sun outshines the Moon's share. Where two times
meet in the ring between which the Moon has moved far, the brightness jumps.

The Moon's aureole arises like the Sun's, from the Sun's excess of light at the Moon's altitude,
dimmed by the same ratio.

For comparison there is the model of Krisciunas and Schaefer [45], fitted to 33 measurements on
Mauna Kea, with the extinction $k$ as a parameter. Our atmosphere has $k = 0.29$ mag per air mass in
the visual: about 0.12 from Rayleigh scattering, 0.15 from the aerosol and 0.03 from ozone. With this
$k$, our sky at full Moon lies a factor of 1.2 to 1.7 above their model from 10° to 60° from the
Moon, with the Moon 40° high and 10° away at 0.032 against 0.026 cd/m². Most of that is a known error
of their model: it dims the moonlight by the whole atmosphere before it is scattered, not just by
the part above [48]. With the same air mass $X$ for the Moon and the line of sight this amounts to the
factor $\tau X/(1 - e^{-\tau X})$, with $\tau = 0.921\,k$: 1.16 to 1.47 depending on the Moon's altitude.
Corrected, 1 to 3 % remain at 10° from the Moon and up to 27 % at 60°, where multiple scattering and
the bright ground add to it. The model itself is good to 8 to 23 %.

Below 10° their model falls as $\rho^{-2}$ and grows steeper than ours: our value lies a factor of
0.8 to 1.0 below it at 5°, 0.5 to 0.7 at 3° and 0.3 to 0.4 at 2°. Right next to the Moon the aureole
here is thus rather too weak; the strength of the forward peak depends on the aerosol's large
particles, which change from day to day. For Xinglong (950 m), the Mie and Rayleigh parts of [45]
were fitted with $k = 0.23$ by the factors 1.5 and 0.9 [49]; with these our sky would be off by −6 to
+31 % at 10° to 20° from the Moon.

**Size.** The ring is a chart of time (angle) and altitude (distance from the horizon edge), not a
picture of the sky: one direction of the sky, the azimuth, is missing. Of the disc's 0.53°, only
its extent along the two axes is defined. Along the time axis it is the time the Sun takes to move
by its own diameter, which the code measures from two positions of the Sun 10 minutes apart:
$\omega_\odot$ = their angular distance per hour (15°/h · cos δ). One degree of sky at the Sun is
then $r\,\Delta\varphi/\omega_\odot$ pixels along the ring, with $r$ the distance from the center and
$\Delta\varphi$ = 15°/h (24-hour dial) or 30°/h (12-hour dial) in radians. Across the ring the
projection's scale, $(R_\mathrm{far} - R_\mathrm{horizon})\sec^2 h/\tan e_t$ per radian, is
usually several times smaller (5 to 10 times for a far edge at 45°), so a true ellipse would mostly
be a sliver about a pixel high. The disc is drawn round, with its true diameter along the time
axis: 2 to 8 pixels, depending on the widget's size and the dial. The Moon's disc is measured the
same way, from its own motion and its current distance ($\arcsin(0.2725\,R_\oplus/d)$), so its
phase can only just be made out in the ring.

**Aureole.** Around the disc lies the sky in the Sun's direction, seen like the rest of the ring
([PHYSIOLOGY.md](PHYSIOLOGY.md), section 6): at angles $\theta$ from 0.27° to 12° from the Sun's
center, along the almucantar (the circle of the Sun's altitude), the radiance in excess of that at
12° is added to the ring's color at the Sun's altitude. The table stores, per angle, the color
that gives this sum when laid over that ring color in *screen* blending, $1 - (1 - a)(1 - b)$. The
widget lays it over the ring round, at the scale of the projection across the ring (altitude) over
the 12° toward the horizon. Along the ring, the time axis is stretched several times as much: at
its scale the aureole would be a long, thin streak, which neither looks nor reads like the sky
around the Sun. It is drawn around where the Sun stands even when that lies beyond the far edge, so
it fades out of the band as the Sun climbs. Its size and strength come from
the aerosol's forward peak (section [9.6](#96-aerosols-mie-theory-and-a-measured-size-distribution)).

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
hand: up is always the zenith. Only in the ring does it turn: there the zenith is the direction of
the far edge, so the bright limb's angle is counted from that direction, without the rim and the
dark part (section [12.2](#122-sun-and-moon-in-the-ring)).

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
| Sky without clouds, 300 DU of ozone | the sky color is that of a cloudless standard sky | [9](#9-sky-colors-the-scattering-model), [12.1](#121-ring-color) |
| One urban aerosol of fixed amount (τ₄₄₀ = 0.2), spherical, refractive index independent of the wavelength, same height profile everywhere | haze, horizon and aureole of a fairly clear day; real haze varies from day to day | [9.6](#96-aerosols-mie-theory-and-a-measured-size-distribution) |
| Night sky uniform, with the Sun's spectrum, without light pollution | the night of a natural, moonless sky | [9.7](#97-the-night-sky) |
| Multiple scattering isotropic and locally homogeneous (Hillaire) | small errors in the brightness distribution of deep twilight | [9.5](#95-multiple-scattering-hillaire-2020) |
| Seeing the sky (adaptation, brightness, colors in dim light) | see [PHYSIOLOGY.md](PHYSIOLOGY.md), section 8 | [10.3](#103-exposure) |
| Sun disc round at the scale of the time axis, without limb darkening, at full brightness with its hue | true size along the ring and true hue, not true shape and brightness | [12.2](#122-sun-and-moon-in-the-ring) |
| Moon's light with the Sun's spectrum, evenly bright over the lit part, without earthshine or opposition surge | the measured reddening of 0.26 mag is left out as the eye does not see it; the dark part stays fully transparent | [12.2](#122-sun-and-moon-in-the-ring) |

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

**Supplement: aerosols and night sky**

42. C. F. Bohren, D. R. Huffman: *Absorption and Scattering of Light by Small Particles*, Wiley
    1983, appendix A (BHMIE).
43. O. Dubovik, B. N. Holben, T. F. Eck, A. Smirnov, Y. J. Kaufman, M. D. King, D. Tanré,
    I. Slutsker: Variability of Absorption and Optical Properties of Key Aerosol Types Observed in
    Worldwide Locations. *Journal of the Atmospheric Sciences* 59, 590–608 (2002).
    <https://doi.org/10.1175/1520-0469(2002)059<0590:VOAAOP>2.0.CO;2>
44. F. Falchi, P. Cinzano, D. Duriscoe, C. C. M. Kyba, C. D. Elvidge, K. Baugh, B. A. Portnov,
    N. A. Rybnikova, R. Furgoni: The new world atlas of artificial night sky brightness.
    *Science Advances* 2, e1600377 (2016). <https://doi.org/10.1126/sciadv.1600377>

**Supplement: moonlight**

45. K. Krisciunas, B. E. Schaefer: A Model of the Brightness of Moonlight. *Publications of the
    Astronomical Society of the Pacific* 103, 1033–1039 (1991). <https://doi.org/10.1086/132921>
46. A. N. Cox (ed.): *Allen's Astrophysical Quantities*, 4th edition, Springer.
47. P. Thejll, C. Flynn, H. Gleisner, T. Andersen, A. Ulla, M. Pérez, A. Darudi, H. Schwarz:
    The colour of the dark side of the Moon. *Astronomy & Astrophysics* 563, A38 (2014).
    <https://doi.org/10.1051/0004-6361/201322776>
48. H. Winkler: A revised simplified scattering model for the moonlit sky brightness profile based
    on photometry at SAAO. *Monthly Notices of the Royal Astronomical Society* (2022).
    <https://doi.org/10.1093/mnras/stac1387>
49. S. Yao, H.-T. Zhang, H.-L. Yuan, Y.-H. Zhao, Y.-Q. Dong, Z.-R. Bai, L.-C. Deng, Y.-J. Lei: Moon
    night sky brightness simulation for Xinglong station. *Research in Astronomy and Astrophysics*
    (2013). <https://arxiv.org/abs/1304.7107>

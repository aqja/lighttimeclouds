# Physik und Astronomie in LightTimeClouds

*English version: [PHYSICS.md](PHYSICS.md)*

Dieses Dokument beschreibt alle physikalischen und astronomischen Rechnungen der App: was gerechnet
wird, mit welchen Formeln und Konstanten, wo es im Code steht, woher Modell und Zahlen stammen und
welche Vereinfachungen bewusst gemacht wurden. Die Quellen stehen nummeriert am Ende
([Quellen](#15-quellen)); Verweise im Text sehen so aus: [7]. Wie aus dem berechneten Licht die
Farben werden, die ein menschliches Auge sieht, beschreibt [PHYSIOLOGIE.md](PHYSIOLOGIE.md).

Die Rechnungen stehen fast vollständig im Modul `core` (reines Kotlin/JVM, ohne Android):

| Bereich | Datei(en) |
|---|---|
| Zeitskalen | [`astro/TimeScales.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/TimeScales.kt) |
| Orientierung der Erde (Nutation, Schiefe, Sternzeit) | [`astro/EarthOrientation.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/EarthOrientation.kt) |
| Ephemeriden von Sonne und Mond | [`astro/Ephemeris.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/Ephemeris.kt), [`astro/AstroSeries.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/AstroSeries.kt) (generiert) |
| Ort des Beobachters, Horizontsystem, Refraktion | [`astro/Topocentric.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/Topocentric.kt) |
| Ereignisse, Mondphase, öffentliche API | [`astro/SunMoonCalculator.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/SunMoonCalculator.kt), [`astro/SolarDay.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/SolarDay.kt) |
| Streumodell der Atmosphäre | [`sky/SkyModel.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyModel.kt) |
| Aerosole (Mie-Theorie, Größenverteilung) | [`sky/Mie.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Mie.kt), [`sky/Aerosol.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Aerosol.kt) |
| Spektrum und Farbmetrik | [`sky/Spectrum.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Spectrum.kt) |
| Abbildung Höhenwinkel → Ring | [`sky/SkyProjection.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyProjection.kt) |
| Vorberechnete Farbtabelle | [`sky/SkyGradientTable.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyGradientTable.kt), [`sky/SkyTableGenerator.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyTableGenerator.kt) |
| Wettergrößen und Schwellen | [`weather/WeatherForecast.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/weather/WeatherForecast.kt), [`weather/WeatherOutlook.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/weather/WeatherOutlook.kt) |
| Darstellung im Zifferblatt | [`widget/ClockFaceRenderer.kt`](../app/src/main/kotlin/com/umfluten/lighttimeclouds/widget/ClockFaceRenderer.kt) (Modul `app`) |
| Kürzung der Reihen, Referenzwerte | [`tools/astro_series.py`](../tools/astro_series.py), [`tools/reference_ephemeris.py`](../tools/reference_ephemeris.py) |

## Inhalt

1. [Konventionen](#1-konventionen)
2. [Zeitskalen](#2-zeitskalen)
3. [Orientierung der Erde](#3-orientierung-der-erde)
4. [Ephemeriden von Sonne und Mond](#4-ephemeriden-von-sonne-und-mond)
5. [Vom Erdmittelpunkt zum Beobachter](#5-vom-erdmittelpunkt-zum-beobachter)
6. [Sonnenereignisse](#6-sonnenereignisse)
7. [Mond: Auf- und Untergang, Phase, Nachlauf, Neigung](#7-mond-auf--und-untergang-phase-nachlauf-neigung)
8. [Prüfung gegen JPL DE440s](#8-prüfung-gegen-jpl-de440s)
9. [Himmelsfarben: das Streumodell](#9-himmelsfarben-das-streumodell)
10. [Himmelsfarben: Spektrum, Farbmetrik, Bildaufbereitung](#10-himmelsfarben-spektrum-farbmetrik-bildaufbereitung)
11. [Himmelsfarben: Projektion und Tabelle](#11-himmelsfarben-projektion-und-tabelle)
12. [Darstellung im Zifferblatt](#12-darstellung-im-zifferblatt)
13. [Wettergrößen](#13-wettergrößen)
14. [Vereinfachungen im Überblick](#14-vereinfachungen-im-überblick)
15. [Quellen](#15-quellen)

---

## 1. Konventionen

- **Winkel** rechnet der Code im Bogenmaß; Reihenkoeffizienten sind oft in Bogensekunden (″)
  angegeben und werden mit $1″ = \pi / 648\,000$ umgerechnet.
- **Azimut** zählt wie bei Meeus [1, Kap. 13] **von Süden, positiv nach Westen** (Süden 0°,
  Westen +90°, Osten −90°, Norden ±180°).
- **Höhe** ist die Höhe des Mittelpunkts eines Körpers über dem Horizont. Für die Sonne ist sie
  überall **geometrisch** (ohne Refraktion); die Refraktion steckt in den Schwellen der
  Sonnenereignisse. Die Höhe des Mondes ist **topozentrisch mit Refraktion**, also so, wie man ihn
  sieht (Abschnitt [5.3](#53-refraktion)).
- **Zeit** ist ein `java.time.Instant` (UTC ohne Schaltsekunden); daraus werden die astronomischen
  Zeitskalen abgeleitet (Abschnitt [2](#2-zeitskalen)).
- $T$ bezeichnet **julianische Jahrhunderte Terrestrischer Zeit (TT) seit J2000.0**
  (2000-01-01 12:00 TT, JD 2451545,0).

---

## 2. Zeitskalen

Code: [`TimeScales.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/TimeScales.kt)

**Julianisches Datum.** Aus den Unix-Sekunden $s$ (UTC) wird

```math
\mathrm{JD}_\mathrm{UT1} \approx \mathrm{JD}_\mathrm{UTC} = 2\,440\,587{,}5 + \frac{s}{86\,400}
```

(2 440 587,5 ist das JD von 1970-01-01 00:00 UTC [1, Kap. 7]).

**Terrestrische Zeit.** Das Argument der Planeten- und Mondtheorien ist TT [1, Kap. 10; 4]:

```math
\mathrm{TT} = \mathrm{TAI} + 32{,}184\,\mathrm{s}, \qquad
\mathrm{TT} - \mathrm{UTC} = 32{,}184\,\mathrm{s} + (\mathrm{TAI} - \mathrm{UTC})
```

$\mathrm{TAI} - \mathrm{UTC}$ ist die Summe der Schaltsekunden; der Code führt die Tabelle ab
1972 (10 s) bis zur bislang letzten Schaltsekunde am 2017-01-01 (37 s) nach IERS Bulletin C [5].
Seitdem gilt also $\mathrm{TT} - \mathrm{UTC} = 69{,}184\,\mathrm{s}$. Daraus

```math
T = \frac{\mathrm{JD}_\mathrm{UTC} + (\mathrm{TT}-\mathrm{UTC})/86\,400 - 2\,451\,545{,}0}{36\,525}.
```

**UT1 ≈ UTC.** Der Drehwinkel der Erde braucht UT1. UTC wird per Definition durch Schaltsekunden
innerhalb von $|\mathrm{UT1} - \mathrm{UTC}| < 0{,}9\,\mathrm{s}$ gehalten [5]; die App setzt
deshalb UT1 = UTC. 0,9 s Drehung entsprechen höchstens $0{,}9\,\mathrm{s} \cdot 15″/\mathrm{s}
\approx 14″$ am Himmel. Diese Grenze bestimmt auch die Toleranz der topozentrischen Tests
(Abschnitt [8](#8-prüfung-gegen-jpl-de440s)).

---

## 3. Orientierung der Erde

Code: [`EarthOrientation.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/EarthOrientation.kt).
Grundlage sind die IERS Conventions 2010 (IERS Technical Note 36) [4]. Für einen Zeitpunkt werden
Nutation, wahre Schiefe der Ekliptik und die scheinbare Sternzeit von Greenwich berechnet.

### 3.1 Fundamentalargumente

Nach Simon et al. (1994) [6], wie in TN36 Gl. 5.43 [4], bis $T^2$ (in Bogensekunden):

```math
\begin{aligned}
l' &= 1\,287\,104{,}79305 + 129\,596\,581{,}0481\,T - 0{,}5532\,T^2 &&\text{(mittlere Anomalie der Sonne)}\\
F  &= 335\,779{,}526232 + 1\,739\,527\,262{,}8478\,T - 12{,}7512\,T^2 &&(L - \Omega)\\
D  &= 1\,072\,260{,}70369 + 1\,602\,961\,601{,}2090\,T - 6{,}3706\,T^2 &&\text{(mittlere Elongation des Mondes)}\\
\Omega &= 450\,160{,}398036 - 6\,962\,890{,}5431\,T + 7{,}4722\,T^2 &&\text{(Länge des aufsteigenden Mondknotens)}
\end{aligned}
```

Die konstanten Glieder von $l'$ und $D$ sind auf fünf Nachkommastellen gerundet wie im
MHB2000-Code, den TN36 direkt unter Gl. 5.43 erwähnt (Unterschied 2 µas).
Die mittlere Anomalie des Mondes $l$ wird nicht gebraucht, weil keiner der verwendeten
Nutationsterme von ihr abhängt.

### 3.2 Nutation

Die größten Terme der lunisolaren Nutation IAU 2000A (Mathews, Herring, Buffett 2002 [7]) in
der ursprünglichen Fassung MHB2000, wie sie auch die SOFA-Routine `nut00a` verwendet, in
Bogensekunden. TN36, Tabellen 5.3a/5.3b [4], führt dieselben Terme in der an IAU 2006 angepassten
Fassung IAU 2000A_R06; deren Koeffizienten weichen um höchstens einige µas (Zeitterme: einige
µas pro Jahrhundert) ab.

```math
\begin{aligned}
\Delta\psi ={}& (-17{,}2064161 - 0{,}0174666\,T)\sin\Omega
  + (-1{,}3170906 - 0{,}0001675\,T)\sin(2F - 2D + 2\Omega)\\
  &- 0{,}2276413\,\sin(2F + 2\Omega) + 0{,}2074554\,\sin 2\Omega + 0{,}1475877\,\sin l'\\[4pt]
\Delta\varepsilon ={}& (9{,}2052331 + 0{,}0009086\,T)\cos\Omega
  + (0{,}5730336 - 0{,}0003015\,T)\cos(2F - 2D + 2\Omega)\\
  &+ 0{,}0978459\,\cos(2F + 2\Omega) - 0{,}0897492\,\cos 2\Omega
\end{aligned}
```

Fünf Terme in der Länge und vier in der Schiefe genügen für etwa 0,2″. Der größte weggelassene
Term ($0{,}0711″ \sin l$) liegt weit unter dem Fehlerbudget der Ephemeriden.

### 3.3 Schiefe der Ekliptik

Mittlere Schiefe nach IAU 2006 (Capitaine, Wallace, Chapront 2003 [8]; TN36 Gl. 5.40 [4]):

```math
\varepsilon_A = 84\,381{,}406″ - 46{,}836769″\,T - 0{,}0001831″\,T^2 + 0{,}00200340″\,T^3,
\qquad \varepsilon = \varepsilon_A + \Delta\varepsilon .
```

### 3.4 Sternzeit

Erdrotationswinkel (Capitaine, Guinot, McCarthy 2000 [9]; TN36 Gl. 5.14 [4], der Code rechnet
mit der gleichwertigen Form Gl. 5.15) mit
$D_U = \mathrm{JD}_\mathrm{UT1} - 2\,451\,545{,}0$:

```math
\theta_\mathrm{ERA} = 2\pi\,\bigl(0{,}7790572732640 + 1{,}00273781191135448\,D_U\bigr) \bmod 2\pi .
```

Mittlere Sternzeit von Greenwich (TN36 Gl. 5.32, bis $T^2$) und scheinbare Sternzeit über die
Gleichung der Äquinoktien:

```math
\mathrm{GMST} = \theta_\mathrm{ERA} + 0{,}014506″ + 4612{,}156534″\,T + 1{,}3915817″\,T^2,
\qquad
\mathrm{GAST} = \mathrm{GMST} + \Delta\psi\cos\varepsilon_A .
```

Weggelassen sind die Polbewegung (< 0,5″) und die Ergänzungsterme der Äquinoktialgleichung (wenige
Millibogensekunden).

---

## 4. Ephemeriden von Sonne und Mond

Code: [`Ephemeris.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/Ephemeris.kt).
Die Reihen selbst stehen in [`AstroSeries.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/AstroSeries.kt),
das [`tools/astro_series.py`](../tools/astro_series.py) aus den Originaldaten des IMCCE erzeugt
(Abschnitt [4.6](#46-kürzung-der-reihen)).

### 4.1 Sonne: VSOP87D

VSOP87 (Bretagnon, Francou 1988 [10]), Version D: heliozentrische sphärische Koordinaten
$L, B, R$ der Erde, bezogen auf die mittlere Ekliptik und das mittlere Äquinoktium des Datums. Mit
$\tau = T/10$ (julianische Jahrtausende):

```math
X = \sum_{\alpha} \tau^{\alpha} \sum_k A_{k}\cos(B_{k} + C_{k}\,\tau), \qquad X \in \{L, B, R\}.
```

Die geozentrische Sonne ist die Erde, vom Sonnenmittelpunkt aus gesehen, umgedreht:
$\lambda_\odot = L + \pi$, $\beta_\odot = -B$, Abstand $R$ in au [1, Kap. 25 und 32].

**Scheinbarer Ort.** Zur geometrischen Länge kommen die Nutation und die jährliche Aberration
[1, Kap. 23 und 25]:

```math
\lambda_\mathrm{app} = \lambda_\odot + \Delta\psi - \frac{\kappa}{R},
\qquad \kappa = 20{,}49552″ \text{ (Aberrationskonstante, IAU 1976)}.
```

### 4.2 Mond: ELP 2000-82B

Die semianalytische Mondtheorie ELP 2000-82B (Chapront-Touzé, Chapront 1983 [11], 1988 [12])
liefert Länge und Breite (in ″) und Abstand (in km), bezogen auf die mittlere Ekliptik des Datums
und den Ausgangspunkt γ′ von J2000. Jeder Term hängt von 13 Argumenten $a_i(T)$ ab (Polynome bis
$T^4$): den Delaunay-Argumenten $D, l', l, F$, der mittleren Mondlänge samt Präzession $\zeta$ und
den mittleren Längen der Planeten Merkur bis Neptun ($Me, V, T, Ma, J, S, U, N$). Alle Terme liegen
als Sinus mit einer Phase $\varphi_k$ vor:

```math
\begin{aligned}
\lambda &= W_1(T) + \sum_k A_k\,T^{n_k}\sin\Bigl(\varphi_k + \sum_{i=1}^{13} m_{k,i}\,a_i(T)\Bigr) + p_A(T),\\
\beta, r &= \sum_k A_k\,T^{n_k}\sin\Bigl(\varphi_k + \sum_{i} m_{k,i}\,a_i(T)\Bigr).
\end{aligned}
```

$W_1$ ist die mittlere Länge des Mondes. Die Präzession in der Länge nach Laskar (1986) [13], wie
sie mit ELP 2000-82B zusammen angegeben wird, führt vom Ausgangspunkt J2000 zum mittleren
Äquinoktium des Datums:

```math
p_A = 5029{,}0966″\,T + 1{,}1120″\,T^2 + 0{,}000077″\,T^3 - 0{,}00002353″\,T^4 .
```

**Scheinbarer Ort.** $\lambda_\mathrm{app} = \lambda + \Delta\psi$. Die Lichtlaufzeit vom Mond
(≈ 1,3 s, < 1″) fällt weg.

### 4.3 Ekliptik → Äquator

Mit der wahren Schiefe $\varepsilon$ [1, Gl. 13.3, 13.4]:

```math
\alpha = \operatorname{atan2}\bigl(\sin\lambda\cos\varepsilon - \tan\beta\sin\varepsilon,\ \cos\lambda\bigr),
\qquad
\delta = \arcsin\bigl(\sin\beta\cos\varepsilon + \cos\beta\sin\varepsilon\sin\lambda\bigr).
```

### 4.4 Interpolation

Ein Zifferblatt braucht pro Minute hunderte Positionen (Tagesbahn des Mondes, Ringfarben). Die
Reihen werden deshalb nur an **Stützstellen im Abstand von 3 Stunden** ausgewertet (und
zwischengespeichert, höchstens 512 Stützstellen) und dazwischen mit dem kubischen
Lagrange-Polynom durch die vier nächsten Stützstellen $u = -1, 0, 1, 2$ interpoliert:

```math
y(u) = -\tfrac{u(u-1)(u-2)}{6}y_{-1} + \tfrac{(u+1)(u-1)(u-2)}{2}y_0
       - \tfrac{(u+1)u(u-2)}{2}y_1 + \tfrac{(u+1)u(u-1)}{6}y_2 .
```

Längen werden vorher über 2π stetig fortgesetzt. Der Interpolationsfehler liegt unter 0,001″; der
Test verlangt < 0,01″.

### 4.5 Was „Sonne“ und „Mond“ zurückgeben

| | Länge, Breite | Abstand | Bezug |
|---|---|---|---|
| `Ephemeris.sun` | scheinbar (Nutation + Aberration) | au → km ($1\,\mathrm{au} = 149\,597\,870{,}7\,\mathrm{km}$ [3]) | wahre Ekliptik und Äquinoktium des Datums |
| `Ephemeris.moon` | scheinbar (Nutation) | km | wahre Ekliptik und Äquinoktium des Datums |

### 4.6 Kürzung der Reihen

[`tools/astro_series.py`](../tools/astro_series.py) lädt ELP 2000-82B und VSOP87D vom IMCCE [14],
wertet die vollständigen Reihen wie die Referenzprogramme aus, prüft sie gegen die mitgelieferten
Kontrollwerte und behält dann alle Terme oberhalb einer Amplitudenschwelle. Die Schwelle ist so
gewählt, dass der Fehler durch die weggelassenen Terme von 2000 bis 2050 (4000 Stichproben)
im Budget bleibt:

| Koordinate | Terme (behalten / gesamt) | Budget | größter Fehler 2000–2050 |
|---|---|---|---|
| Mond, Länge | 136 / 20 560 | 4″ | 3,96″ |
| Mond, Breite | 97 / 7 684 | 2″ | 1,94″ |
| Mond, Abstand | 84 / 9 628 | 5 km | 4,85 km |
| Erde, L | 36 / 1 080 | 2″ | 1,9″ |
| Erde, B | 2 / 348 | 0,5″ | 0,5″ |
| Erde, R | 16 / 997 | 10⁻⁵ au | 8,7·10⁻⁶ au |

(Werte aus dem Kopf von [`AstroSeries.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/AstroSeries.kt).)
Außerhalb von 2000–2050 wächst der Fehler langsam, vor allem durch die Terme mit $T^n$.

---

## 5. Vom Erdmittelpunkt zum Beobachter

Code: [`Topocentric.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/Topocentric.kt)

### 5.1 Ort auf dem WGS84-Ellipsoid und Parallaxe

Der Beobachter steht auf Meereshöhe auf dem WGS84-Ellipsoid [15] ($a = 6378{,}137\,\mathrm{km}$,
$f = 1/298{,}257223563$) bei geodätischer Breite $\varphi$ und östlicher Länge $\lambda_E$. Mit der
Ortssternzeit $\theta = \mathrm{GAST} + \lambda_E$ und [1, Kap. 11]

```math
C = \frac{1}{\sqrt{\cos^2\varphi + (1-f)^2\sin^2\varphi}}, \qquad S = (1-f)^2\,C
```

ist sein geozentrischer Ort im Äquatorsystem des Datums

```math
\vec r_o = a\,\bigl(C\cos\varphi\cos\theta,\ C\cos\varphi\sin\theta,\ S\sin\varphi\bigr).
```

Statt der Parallaxenformeln aus Meeus [1, Kap. 40] zieht der Code diesen Vektor direkt vom
geozentrischen Ort des Körpers ($\Delta$, $\alpha$, $\delta$) ab:

```math
\vec r' = \Delta\,(\cos\delta\cos\alpha,\ \cos\delta\sin\alpha,\ \sin\delta) - \vec r_o,\qquad
\delta' = \operatorname{atan2}\bigl(z',\sqrt{x'^2+y'^2}\bigr),\qquad
H = \theta - \operatorname{atan2}(y', x').
```

Das ist streng und gilt gleichermaßen für den Mond (Parallaxe bis ≈ 1°) und die Sonne (≈ 8,8″).

### 5.2 Horizontsystem

[1, Gl. 13.5, 13.6], Azimut von Süden nach Westen:

```math
h = \arcsin\bigl(\sin\varphi\sin\delta' + \cos\varphi\cos\delta'\cos H\bigr),\qquad
A = \operatorname{atan2}\bigl(\cos\delta'\sin H,\ \cos\delta'\cos H\sin\varphi - \sin\delta'\cos\varphi\bigr).
```

Für den Meridiandurchgang der Sonne genügt der **geozentrische** Stundenwinkel
$H = \theta - \alpha$ (auf −π…π gebracht).

### 5.3 Refraktion

Formel von Sæmundsson (1986) [16], wie bei Meeus [1, Gl. 16.4], für 10 °C und 1010 hPa: aus der
geometrischen Höhe $h$ (in Grad) die Hebung in Bogenminuten

```math
R = \frac{1{,}02'}{\tan\!\left(h + \dfrac{10{,}3°}{h + 5{,}11°}\right)} .
```

Unter −1° wird $h$ auf −1° gehalten, damit die Formel endlich bleibt. Die Refraktion wird nur auf
die **Mondhöhe** in `moonPosition` angewandt.

Die Formel passt zum Standardwert von 34′, mit dem Auf- und Untergänge gerechnet werden
(Abschnitte [6](#6-sonnenereignisse), [7.1](#71-mondaufgang-und--untergang)): 34′ ist die
Refraktion bei **gesehener** Höhe 0. Sæmundsson geht dagegen von der **geometrischen** Höhe aus.
Bei $h = 0$ ergibt die Formel ≈ 29′ (der Körper erscheint dann bei +29′), bei $h = -34'$ dagegen
34,4′, sodass der Körper gerade auf dem Horizont erscheint. Sæmundssons Formel ist als Umkehrung
der Formel von Bennett (1982) [41] gebaut, die von der gesehenen Höhe ausgeht und am Horizont
34,5′ liefert [1, Kap. 16]. Ein Test sichert diesen Zusammenhang ab.

---

## 6. Sonnenereignisse

Code: `sunTimes` in [`SunMoonCalculator.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/SunMoonCalculator.kt),
Liste für den „Sonnentag“ in [`SolarDay.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/SolarDay.kt).

### 6.1 Sonnenmittag und -mitternacht

Mittag ist der obere, Mitternacht der untere Meridiandurchgang. Ausgehend von einer Schätzung
wird viermal

```math
t \leftarrow t - \frac{H}{2\pi}\cdot 86\,400\,\mathrm{s}
```

gerechnet ($H$ = Stundenwinkel der Sonne, für den unteren Durchgang um π verschoben). Weil die
Rektaszension der Sonne sich pro Tag nur um etwa 1/365 Umlauf ändert, schrumpft der Fehler je
Schritt um diesen Faktor; vier Schritte reichen weit. Ein Sonnentag läuft von der Mitternacht vor
dem Mittag bis zur Mitternacht danach. Deshalb wechselt die Liste im Tab „Zeiten“ zur
Sonnenmitternacht und nicht zur bürgerlichen Mitternacht.

### 6.2 Höhenschwellen

Alle anderen Ereignisse sind Zeitpunkte, zu denen der **geometrische** Sonnenmittelpunkt eine
Schwellhöhe kreuzt:

| Ereignis (morgens / abends) | Höhe | Bedeutung, Quelle |
|---|---|---|
| Sonnenauf- / -untergang | −0,833° | Oberer Sonnenrand am Horizont: 34′ Refraktion + 16′ Halbmesser = 50′ [1, Kap. 15; 17] |
| Ende des Aufgangs / Beginn des Untergangs | −0,3° | Unterer Rand löst sich vom Horizont (Konvention von SunCalc [18]) |
| Ende / Beginn der goldenen Stunde | +6° | Konvention von SunCalc [18] |
| bürgerliche Dämmerung | −6° | [17; 2] |
| nautische Dämmerung | −12° | [17; 2] |
| astronomische Dämmerung | −18° | [17; 2] |

Die Ereignisse am Morgen werden zwischen Mitternacht und Mittag gesucht, die am Abend zwischen
Mittag und der folgenden Mitternacht, wo sich die Höhe monoton ändert. Liegt die Schwelle nicht
zwischen den Höhen an den Intervallenden, fällt das Ereignis an diesem Tag aus (`null`:
Polartag, Polarnacht, weiße Nächte).

### 6.3 Nullstellensuche

`crossing` sucht den Vorzeichenwechsel von $g(t) = h(t) - h_0$ mit der **Regula falsi in der
Illinois-Variante** (Dowell, Jarratt 1971 [19]):

```math
t = \frac{a\,g(b) - b\,g(a)}{g(b) - g(a)},
```

danach wird $[a, b]$ auf die Seite mit Vorzeichenwechsel verkürzt. Bleibt ein Ende zweimal
hintereinander stehen, wird dessen Funktionswert halbiert. Das verhindert die langsame einseitige
Konvergenz der reinen Regula falsi. Abbruch, sobald das Intervall kürzer als 0,5 s ist (höchstens
60 Schritte).

### 6.4 Himmelszustand

Für den Ringstil „Phasenfarben“ ordnet `classify` die Sonnenhöhe einer Stufe zu:
Nacht (< −18°), astronomische (−18…−12°), nautische (−12…−6°) und bürgerliche Dämmerung
(−6…−0,833°), goldene Stunde (−0,833…6°) und Tag (≥ 6°).

---

## 7. Mond: Auf- und Untergang, Phase, Nachlauf, Neigung

Code: [`SunMoonCalculator.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/SunMoonCalculator.kt)

### 7.1 Mondaufgang und -untergang

Auf- bzw. Untergang ist der Moment, in dem der **obere Mondrand** den Horizont berührt, mit 34′
Standardrefraktion. Weil die Höhe hier schon topozentrisch und geometrisch ist (die Parallaxe also
enthalten ist), lautet die Schwelle für den Mittelpunkt

```math
h_0 = -34' - s, \qquad s = \arcsin\frac{k\,a}{\Delta}, \qquad k = 0{,}2725 ,
```

mit dem Abstand $\Delta$ zum Erdmittelpunkt, dem Erdradius $a$ und dem Verhältnis Mond- zu
Erdradius $k$. Das entspricht Meeus' geozentrischer Schwelle $h_0 = 0{,}7275\,\pi - 34'$
[1, Kap. 15] ($\pi$ = Horizontalparallaxe, $0{,}7275 = 1 - k$), nur ohne den Parallaxenanteil,
den die topozentrische Rechnung schon enthält.

Gesucht wird in den 24 Stunden ab dem Start: Die Höhe wird alle 30 Minuten abgetastet und jeder
Vorzeichenwechsel mit der Regula falsi (Abschnitt [6.3](#63-nullstellensuche)) auf eine halbe
Sekunde genau bestimmt. Es kann an einem Tag keinen Auf- oder Untergang geben (der Mond geht im
Mittel 50 Minuten später auf als am Vortag), in Polnähe auch mehrere.
`SolarDay.moonEventsBetween` fasst mehrere Tage zusammen und entfernt Doppelte (gleiche Art,
gleiche 10-Minuten-Spanne).

### 7.2 Beleuchteter Anteil und Phase

Nach Meeus [1, Kap. 48], aus den scheinbaren geozentrischen Orten von Mond ($\lambda, \beta,
\Delta$) und Sonne ($\lambda_\odot$, $R$):

```math
\cos\psi = \cos\beta\cos(\lambda - \lambda_\odot)
\quad\text{(geozentrische Elongation)},
```

```math
i = \operatorname{atan2}\bigl(R\sin\psi,\ \Delta - R\cos\psi\bigr)
\quad\text{(Phasenwinkel Sonne–Mond–Erde)},
\qquad
k = \frac{1 + \cos i}{2}
\quad\text{(beleuchteter Anteil)}.
```

Die **Phase** ist die Elongation in ekliptikaler Länge als Bruchteil eines Umlaufs,
$p = \bigl((\lambda - \lambda_\odot) \bmod 2\pi\bigr) / 2\pi$: 0 Neumond, 0,25 erstes Viertel,
0,5 Vollmond, 0,75 letztes Viertel. Die acht Phasennamen decken je ein Achtel um ihre Mitte ab:
Index $= \lfloor 8p + 0{,}5 \rfloor \bmod 8$.

### 7.3 Nachlauf hinter der Sonne

Für den Stil „eigener Zeiger“ zählt, wie viele Stunden der Mond der Sonne auf ihrer täglichen
Bahn hinterherläuft, also die Differenz der Rektaszensionen als Zeit:

```math
\Delta t = \frac{(\alpha_\mathrm{Mond} - \alpha_\odot) \bmod 2\pi}{2\pi}\cdot 24\,\mathrm{h}.
```

Etwa 0 h bei Neumond, 6 h beim ersten Viertel, 12 h bei Vollmond. Steht der Zeiger auf „jetzt
minus $\Delta t$“, zeigt er die Lage des Mondes in seinem Tageslauf so, wie der Stundenzeiger die
der Sonne zeigt. Der Unterschied zwischen Sonnen- und Sternzeit bleibt dabei außer Acht.

### 7.4 Neigung der Sichel

Der helle Rand des Mondes zeigt zur Sonne. Wie die Sichel am Himmel geneigt ist, hängt von Ort und
Uhrzeit ab. Der Positionswinkel des hellen Rands, gezählt vom Nordpunkt der Mondscheibe über
Osten [1, Gl. 48.5], ist

```math
\chi = \operatorname{atan2}\bigl(\cos\delta_\odot\sin(\alpha_\odot - \alpha),\
  \sin\delta_\odot\cos\delta - \cos\delta_\odot\sin\delta\cos(\alpha_\odot - \alpha)\bigr),
```

der parallaktische Winkel, um den die Richtung zum Himmelsnordpol gegen die zum Zenit gedreht ist
[1, Gl. 14.1], mit dem Stundenwinkel $H$ des Mondes

```math
q = \operatorname{atan2}\bigl(\sin H,\ \tan\varphi\cos\delta - \sin\delta\cos H\bigr).
```

`moonBrightLimbAngle` gibt $\chi - q$ zurück: den Winkel des hellen Rands **vom Zenit aus, gegen
den Uhrzeigersinn**, so wie der Beobachter den Mond vor sich sieht (90° = links). Gerechnet wird
mit geozentrischen Orten wie bei Meeus. Ein Test vergleicht das Ergebnis mit einer davon
unabhängigen Rechnung im Horizontsystem (Richtung des Großkreises vom Mond zur Sonne) und
verlangt weniger als 0,5° Abweichung. Ein weiterer prüft, dass der zunehmende Mond in Berlin rechts
und in Canberra links beleuchtet ist.

---

## 8. Prüfung gegen JPL DE440s

[`tools/reference_ephemeris.py`](../tools/reference_ephemeris.py) erzeugt mit Skyfield [20] aus der
numerischen Ephemeride JPL DE440s [21] Referenzwerte nach denselben Definitionen wie die App
(geometrische Höhen auf dem WGS84-Ellipsoid, Azimut von Süden, Ereignisschwellen wie oben). Orte
sind Berlin, Quito, Canberra und, für Polartag und -nacht, Longyearbyen. Werte, die von der
Erdrotation abhängen, enden 2025, weil Skyfield danach UT1 aus einer Prognose von ΔT
extrapoliert, der UTC nicht folgen muss.
[`EphemerisReferenceTest.kt`](../core/src/test/kotlin/com/umfluten/lighttimeclouds/astro/EphemerisReferenceTest.kt)
verlangt:

| Größe | Zeitraum | Grenze | Begründung |
|---|---|---|---|
| geozentrische Länge (· cos β) und Breite, Sonne und Mond | 2000–2050 | 5″ | Kürzung (bis 4″) und übrige Näherungen; die vollständigen Theorien stimmen mit DE440s weit unter 1″ überein |
| topozentrische Höhe und Azimut (· cos h) | 2000–2025 | 15″ | zusätzlich UT1 ≈ UTC (bis 14″) |
| Zeitpunkte aller Sonnenereignisse | 2000–2025 | 3 s | |
| Mondauf- und -untergang | 2000–2025 | 3 s | |
| Interpolation gegenüber direkter Reihe | | 0,01″ | |

---

## 9. Himmelsfarben: das Streumodell

Code: [`SkyModel.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyModel.kt).
Das Modell ist eine Portierung von *Horizon* (Daniel Lazaro) [22], das auf *Production Sky
Rendering* (Andrew Helmer) [23] zurückgeht. Physik und Parameter stammen aus Hillaire (2020) [24],
der wiederum auf Bruneton und Neyret (2008) [25] aufbaut. Die App erweitert das Original um
Mehrfachstreuung nach Hillaire [24, Abschn. 5.5], um eine spektrale Rechnung mit Daten aus
Brunetons Implementierung [26] (Abschnitt [10](#10-himmelsfarben-spektrum-farbmetrik-bildaufbereitung)),
um Aerosole aus der Mie-Theorie mit gemessenen Größenverteilungen (Abschnitt
[9.6](#96-aerosole-mie-theorie-und-eine-gemessene-größenverteilung)) und um das Eigenleuchten des
Nachthimmels (Abschnitt [9.7](#97-der-nachthimmel)).

Es gibt zwei Konfigurationen derselben Rechnung:

| | `gradient` (Original) | `ringGradient` (Ring der App) |
|---|---|---|
| Wellenlängen | 3 (680, 550, 440 nm ≙ R, G, B) | 48 (360–830 nm, 10-nm-Schritte) |
| Sonnenspektrum | weiß (1, 1, 1) | ASTM G-173 oberhalb der Atmosphäre |
| Aerosole | grau, Phasenfunktion nach Cornette-Shanks, optische Dicke 0,005 | Stadtaerosol aus der Mie-Theorie, τ(440 nm) = 0,2 |
| Streuordnungen | einfach | einfach + Mehrfachstreuung |
| Integration entlang des Sehstrahls | 32 gleiche Schritte | 32 mit der Entfernung wachsende Schritte |
| Blickrichtung | 90° seitlich zur Sonne | Mittel über 12 Azimute |
| Belichtung | fest | so, wie das Auge sieht ([PHYSIOLOGIE.md](PHYSIOLOGIE.md)) |

Das Original dient als Referenz: Ein Test prüft, dass die Portierung dieselben Farben wie Horizon
liefert.

### 9.1 Atmosphäre

Kugelschale zwischen Bodenradius $R_g = 6360\,\mathrm{km}$ [24; 26] und Oberkante
$R_t = 6460\,\mathrm{km}$ (100 km Atmosphäre) [24]; Bruneton [26] setzt die Oberkante bei
6420 km an. Drei Bestandteile mit relativen Dichteprofilen über der Höhe $h$:

```math
\rho_R(h) = e^{-h/8\,\mathrm{km}},\qquad
\rho_M(h) = e^{-h/1{,}2\,\mathrm{km}},\qquad
\rho_O(h) = \max\!\left(0,\ 1 - \frac{|h - 25\,\mathrm{km}|}{15\,\mathrm{km}}\right).
```

Rayleigh (Luftmoleküle) und Mie (Aerosole) nehmen exponentiell ab. Ozon ist ein Dreieck, das von
10 km linear auf sein Maximum bei 25 km steigt und bis 40 km wieder auf null fällt [26].

Koeffizienten am Boden bzw. im Ozonmaximum (in 1/m):

| Größe | Wert | Herkunft |
|---|---|---|
| Rayleigh-Streuung $\beta_R$ | 5,802·10⁻⁶ / 13,558·10⁻⁶ / 33,1·10⁻⁶ (R/G/B) | $1{,}24062\cdot10^{-6}\,(\lambda/\mu\mathrm{m})^{-4}$ bei 680/550/440 nm [24; 26] |
| Mie-Extinktion $\beta_M^\mathrm{ext}$ (Original) | 4,44·10⁻⁶ | Ångström-β 5,328·10⁻³ / 1,2 km [26]; ebenso Hillaires Referenzcode [24]. Tabelle 1 des Artikels nennt als Mie-Absorption 4,40·10⁻⁶ statt 0,444·10⁻⁶ |
| Mie-Streuung $\beta_M$ (Original) | 3,996·10⁻⁶ | Einfachstreu-Albedo 0,9 · Extinktion [24; 26] |
| Ozon-Absorption $\beta_O$ (RGB) | 0,65·10⁻⁶ / 1,881·10⁻⁶ / 0,085·10⁻⁶ | [24] |
| Ozon-Absorption $\beta_O$ (spektral) | $\sigma_{O_3}(\lambda)\cdot n_\mathrm{max}$ | Abschnitt [10.1](#101-spektrale-daten) |
| Bodenalbedo $a$ | 0,3 | wie im Artikel von Hillaire [24] (nur Mehrfachstreuung) |

Rayleigh streut ohne Absorption, Ozon absorbiert nur, Aerosol tut beides: im Original grau, also
unabhängig von der Wellenlänge, mit einer optischen Dicke von nur 0,005 (eine sehr saubere
Atmosphäre); für den Ring mit den spektralen Koeffizienten aus Abschnitt
[9.6](#96-aerosole-mie-theorie-und-eine-gemessene-größenverteilung) und demselben Höhenprofil.

### 9.2 Phasenfunktionen

Rayleigh [27]:

```math
P_R(\theta) = \frac{3}{16\pi}\,(1 + \cos^2\theta).
```

Mie nach Cornette und Shanks (1992) [28] mit Asymmetrieparameter $g = 0{,}8$ (Vorwärtsstreuung) [24; 26]:

```math
P_M(\theta) = \frac{3}{8\pi}\,
  \frac{(1 - g^2)(1 + \cos^2\theta)}{(2 + g^2)\,(1 + g^2 - 2g\cos\theta)^{3/2}} .
```

$\theta$ ist der Winkel zwischen Blick- und Sonnenrichtung. Das ist die des Originals; sie ist nahe
der Sonne glatt (bei 0° nur doppelt so stark wie bei 10°). Der Ring nutzt stattdessen die
Phasenfunktion eines gemessenen Aerosols aus der Mie-Theorie, mit einer echten Vorwärtsspitze
(Abschnitt [9.6](#96-aerosole-mie-theorie-und-eine-gemessene-größenverteilung)).

### 9.3 Transmission

Die Transmission von einem Punkt in Höhe $h$ entlang einer Richtung mit Zenitwinkel $\zeta$ bis zur
Oberkante ist

```math
T(h,\zeta) = \exp\!\Bigl(-\!\!\int \bigl[\beta_R\rho_R + \beta_M^\mathrm{ext}\rho_M + \beta_O\rho_O\bigr]\,ds\Bigr),
```

numerisch mit der Mittelpunktsregel in 32 Schritten. Ein Strahl, der durch die Erde läuft, kommt
dort in negative Höhen mit sehr großer Dichte. Seine Transmission wird so praktisch null, und der
Erdschatten entsteht in der Einfachstreuung von selbst. In der Mehrfachstreuung wird der Boden
explizit geprüft (`sunTransmittance`).

### 9.4 Einfachstreuung entlang des Sehstrahls

Vom Beobachter $c$ am Boden aus wird der Sehstrahl $\vec v$ bis zur Oberkante in 32 Abschnitte
$\Delta s$ geteilt. In jedem Mittelpunkt $x_j$ wird das dorthin gestreute Sonnenlicht gesammelt und
zum Beobachter hin abgeschwächt:

```math
L(\lambda) = E_\odot(\lambda)\sum_{j} T(c \to x_j)\Bigl[
   T(x_j \to \odot)\bigl(\beta_R\rho_R P_R(\theta) + \beta_M\rho_M P_M(\theta)\bigr)
   + \Psi_\mathrm{ms}(x_j)\bigl(\beta_R\rho_R + \beta_M\rho_M\bigr)\Bigr]\Delta s .
```

$T(c \to x_j)$ ergibt sich ohne zusätzliche Integration als Quotient zweier Transmissionen bis zur
Oberkante, $T(c \to \mathrm{top}) / T(x_j \to \mathrm{top})$ (bei Blick nach unten umgekehrt).
$\Psi_\mathrm{ms}$ ist der Beitrag der Mehrfachstreuung (nächster Abschnitt, im Original 0).

Für den Ring wachsen die Schritte mit dem Quadrat der Entfernung, $s_j = s_\mathrm{top}\,(j/32)^2$,
und $T(c \to x_j)$ ist die unterwegs aufsummierte optische Tiefe. Mit Blick am Horizont entlang ist
der erste Schritt dann etwa 1 km lang statt 35 km: Der Dunst in Bodennähe liegt in den ersten paar
Schritten, die ihm mit gleichen Schritten einen falschen, bräunlichen Farbton gaben.

### 9.5 Mehrfachstreuung (Hillaire 2020)

In der Dämmerung ist nur noch die hohe Atmosphäre sonnenbeschienen. Ihr Licht erreicht die
beschattete Luft darunter erst durch mehrfache Streuung. Hillaire [24, Abschn. 5.5] nähert alle
Ordnungen ab der zweiten mit zwei Annahmen: Licht höherer Ordnung kommt isotrop an, und die
Umgebung eines Punktes ist überall wie an diesem Punkt. Damit wird die Summe über alle Ordnungen
eine geometrische Reihe:

```math
\Psi_\mathrm{ms} = L_2\,(1 + f_\mathrm{ms} + f_\mathrm{ms}^2 + \dots) = \frac{L_2}{1 - f_\mathrm{ms}} .
```

Für einen Punkt in Höhe $h$ und Sonne in lokaler Höhe $h_\odot$ wird über $N = 64$ gleich
verteilte Richtungen $\vec\omega$ (Fibonacci-Gitter auf der Kugel [29]) integriert, je Strahl mit
24 Schritten bis zum Boden oder zur Oberkante:

```math
\begin{aligned}
L_2 &= \frac{1}{N}\sum_{\vec\omega}\Bigl[\int T(x,y)\,\beta_s(y)\,T(y\to\odot)\,\frac{1}{4\pi}\,ds
       + T(x,y_g)\,T(y_g\to\odot)\,\max(0,\vec n\cdot\vec s)\,\frac{a}{\pi}\Bigr],\\
f_\mathrm{ms} &= \frac{1}{N}\sum_{\vec\omega}\int T(x,y)\,\beta_s(y)\,ds,
\qquad \beta_s = \beta_R\rho_R + \beta_M\rho_M .
\end{aligned}
```

Der zweite Summand in $L_2$ ist das vom sonnenbeschienenen Boden diffus (lambertsch) reflektierte
Licht, falls der Strahl den Boden trifft ($\vec n$ Bodennormale, $\vec s$ Sonnenrichtung, $a$
Albedo). Der Mittelwert $\frac1N\sum$ steht für $\int \frac{1}{4\pi}\,d\omega$, die isotrope
Phasenfunktion am Punkt selbst.

$\Psi_\mathrm{ms}$ wird je spektralem Band vorab als Tabelle berechnet: lokale Sonnenhöhe −30°…90°
in 1°-Schritten × 32 Höhen, die zum Boden hin dichter liegen ($h = u^2 \cdot 100\,\mathrm{km}$,
$u$ gleichverteilt). Abgefragt wird bilinear. Hillaire legt dieselbe Tabelle über
$\cos$(Sonnenzenitwinkel) und Höhe an [24].

### 9.6 Aerosole: Mie-Theorie und eine gemessene Größenverteilung

Code: [`Mie.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Mie.kt),
[`Aerosol.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Aerosol.kt).
Das Aerosol des Originals ist sehr dünn und seine Phasenfunktion glatt. Echter Dunst ist bis zu
zehnmal dicker, streut stärker zur Seite und hat eine scharfe Vorwärtsspitze, die die helle Aureole
um die Sonne macht. Das Aerosol des Rings wird deshalb aus einer gemessenen Teilchenpopulation
berechnet.

**Größenverteilung.** AERONET, das weltweite Netz von Sonnenphotometern, leitet die
Volumen-Größenverteilung des Aerosols aus Messungen des Himmels um die Sonne ab. Dubovik et al.
[43] geben sie für typische Aerosolarten als zwei Lognormal-Moden an, deren Parameter mit der
optischen Dicke $\tau_{440}$ bei 440 nm wachsen:

```math
\frac{dV}{d\ln r} = \sum_{i=1}^{2}\frac{C_i}{\sqrt{2\pi}\,\sigma_i}
  \exp\!\left(-\frac{(\ln r - \ln r_i)^2}{2\sigma_i^2}\right).
```

Die App nimmt das Stadtaerosol von Créteil bei Paris (1999) [43, Tabelle 1], den Ort der Tabelle,
der einer europäischen Stadt am nächsten kommt, mit $\tau_{440} = 0{,}2$, einem ziemlich klaren Tag
(gemessen wurden dort im Mittel 0,26, die Tabelle gilt von 0,1 bis 0,9):

| Mode | Volumen $C_i$ (µm³/µm²) | Medianradius $r_i$ (µm) | $\sigma_i$ |
|---|---|---|---|
| fein | $0{,}01 + 0{,}12\,\tau_{440}$ | $0{,}11 + 0{,}13\,\tau_{440}$ | 0,43 |
| grob | $0{,}01 + 0{,}05\,\tau_{440}$ | $2{,}76 + 0{,}48\,\tau_{440}$ | 0,79 |

mit dem Brechungsindex $1{,}40 + 0{,}009i$, als unabhängig von der Wellenlänge angenommen.

**Mie-Theorie.** Jedes Teilchen ist eine homogene Kugel. Seine Effizienzen für Extinktion und
Streuung $Q_\mathrm{ext}, Q_\mathrm{sca}$ und seine Streuamplituden $S_1, S_2$ folgen aus der
Lorenz-Mie-Reihe, gerechnet mit dem Algorithmus BHMIE von Bohren und Huffman [42] (logarithmische
Ableitung per Abwärtsrekursion, Riccati-Bessel-Funktionen aufwärts, Winkelfunktionen
$\pi_n, \tau_n$). Summiert über 160 Radien von 0,01 bis 20 µm (gleichmäßig in $\ln r$), für jede
der 48 Wellenlängen:

```math
\tau_\mathrm{ext}(\lambda) = \int \frac{dV/d\ln r}{\tfrac43\pi r^3}\,\pi r^2\,Q_\mathrm{ext}\,d\ln r,\qquad
P_M(\theta,\lambda) = \frac{\int N(r)\,\tfrac12\bigl(|S_1|^2 + |S_2|^2\bigr)\,d\ln r}{k^2\int N(r)\,\pi r^2 Q_\mathrm{sca}\,d\ln r},
```

ebenso $\tau_\mathrm{sca}$ mit $Q_\mathrm{sca}$; $N(r)$ ist die Teilchenzahl je $d\ln r$,
$k = 2\pi/\lambda$. Die Phasenfunktion ist für 721 Winkel tabelliert, am dichtesten nahe 0°. Tests
prüfen den Algorithmus am Rechenbeispiel von Bohren und Huffman ($Q = 3{,}10543$, Rückstreuung
2,92534), die Normierung $\int P\,d\Omega = 1$ und den Rayleigh-Grenzfall.

**Ergebnis** für $\tau_{440} = 0{,}2$, verglichen mit den Werten, die Dubovik et al. in Créteil
gemessen haben:

| Größe | Modell | Gemessen [43] |
|---|---|---|
| Optische Dicke bei 440 / 550 / 670 nm | 0,208 / 0,136 / 0,092 | 0,2 (vorgegeben) |
| Einfachstreu-Albedo bei 440 / 670 nm | 0,92 / 0,89 | 0,94 / 0,93 ± 0,03 |
| Asymmetrieparameter $g$ bei 440 / 670 nm | 0,67 / 0,57 | 0,68 / 0,61 ± 0,07 |
| Ångström-Exponent 440–670 nm | 1,93 | 1,2 bis 2,3 |
| Phasenfunktion bei 550 nm, 0° / 3° / 10° / 90° | 6,0 / 1,8 / 0,57 / 0,026 sr⁻¹ | |

**Im Modell** behält das Aerosol das Höhenprofil des Originals: $\beta(h) = \tau(\lambda)/H \cdot e^{-h/H}$
mit $H = 1{,}2$ km. Mit Blick am Horizont entlang durchquert die Sichtlinie so etwa das Hundertfache
der senkrechten optischen Dicke. Bei Sonnenuntergang wird dieser bodennahe Dunst kaum noch
beleuchtet: Der Horizont selbst wird dunkler, darüber liegt ein helleres Band, wie an diesigen
Abenden. Der Anteil des Lichts aus der Umgebung der Sonne bis 2,5° (Circumsolar Ratio, in
Leuchtdichte) ergibt sich zu 0,4 % bei 40° Sonnenhöhe, 1,4 % bei 10° und 5,7 % bei 2°.

### 9.7 Der Nachthimmel

Ohne Sonne leuchtet der Himmel noch: Airglow, Sternlicht, Zodiakallicht. Das Modell legt die
natürliche Helligkeit des Nachthimmels darunter, die Falchi et al. [44] für den Hintergrund fern
der Milchstraße bei geringer Sonnenaktivität annehmen, 22,0 mag/arcsec² oder 174 µcd/m², überall am
Himmel gleich und mit dem Spektrum der Sonne. Lichtverschmutzung ist nicht enthalten. Dieses Leuchten
bestimmt die Farbe der Nacht ([PHYSIOLOGIE.md](PHYSIOLOGIE.md), Abschnitt 5).

---

## 10. Himmelsfarben: Spektrum, Farbmetrik, Bildaufbereitung

Code: [`Spectrum.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Spectrum.kt) und
`toSrgb` in [`SkyModel.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyModel.kt).

### 10.1 Spektrale Daten

48 Bänder von 360 bis 830 nm in 10-nm-Schritten, die Tabellen aus der Demo von Brunetons
*Precomputed Atmospheric Scattering* (Fassung 2017) [26]:

- **Sonnenspektrum oberhalb der Atmosphäre** in W/(m²·nm): Spalte „ETR“ (extraterrestrisch) des
  Referenzspektrums ASTM G-173 [30], gemittelt über jedes 10-nm-Band.
- **Ozon-Absorptionsquerschnitt** in m² pro Molekül: Referenzspektren der Universität Bremen
  (IUP) bei 233 K [31], gemittelt über jedes Band.
- **Ozon-Säule** 300 Dobson-Einheiten (1 DU = 2,687·10²⁰ Moleküle/m²). Verteilt auf das
  Dreiecksprofil (Integral 15 km) ergibt sich die Spitzendichte

  ```math
  n_\mathrm{max} = \frac{300 \cdot 2{,}687\cdot10^{20}\,\mathrm{m^{-2}}}{15\,000\,\mathrm{m}}
  \approx 5{,}37\cdot10^{18}\,\mathrm{m^{-3}},\qquad
  \beta_O(\lambda) = \sigma_{O_3}(\lambda)\,n_\mathrm{max}.
  ```

- **Rayleigh** $\beta_R(\lambda) = 1{,}24062\cdot10^{-6}\,\mathrm{m^{-1}}\cdot(\lambda/\mu\mathrm{m})^{-4}$ [26],
  bei 680 nm also genau der RGB-Wert 5,802·10⁻⁶ des Originals.

Erst die spektrale Rechnung gibt das Blau der „blauen Stunde“ richtig wieder. Es entsteht vor allem
durch die Chappuis-Absorption des Ozons, die breit über Gelb bis Rot reicht (Hulburt 1953 [37]). Mit nur drei
Wellenlängen wird daraus ein Violett.

### 10.2 Spektrum → Farbe

Die Strahldichte $L(\lambda)$ wird mit den CIE-1931-Normspektralwertfunktionen zu XYZ
integriert. Die Funktionen sind durch die analytische Mehrfach-Gauß-Näherung von Wyman, Sloan und
Shirley (2013) [32, Abschn. 2.2] ersetzt. Die Breiten $\sigma_1, \sigma_2$ stehen hier in nm; der
Artikel gibt in Tabelle 1 ihre Kehrwerte an:

```math
X = \sum_\lambda L(\lambda)\,\bar x(\lambda)\,\Delta\lambda \ \ (\text{analog } Y, Z),\qquad
g(\lambda;\mu,\sigma_1,\sigma_2) = \exp\!\Bigl(-\tfrac12\bigl(\tfrac{\lambda-\mu}{\sigma}\bigr)^2\Bigr),\
\sigma = \begin{cases}\sigma_1 & \lambda<\mu\\ \sigma_2 & \lambda\ge\mu\end{cases}
```

```math
\begin{aligned}
\bar x &= 1{,}056\,g(599{,}8;\,37{,}9,\,31{,}0) + 0{,}362\,g(442{,}0;\,16{,}0,\,26{,}7) - 0{,}065\,g(501{,}1;\,20{,}4,\,26{,}2)\\
\bar y &= 0{,}821\,g(568{,}8;\,46{,}9,\,40{,}5) + 0{,}286\,g(530{,}9;\,16{,}3,\,31{,}1)\\
\bar z &= 1{,}217\,g(437{,}0;\,11{,}8,\,36{,}0) + 0{,}681\,g(459{,}0;\,26{,}0,\,13{,}8)
\end{aligned}
```

XYZ → lineares sRGB mit der Matrix für die Primärvalenzen von sRGB/Rec. 709 und Weißpunkt D65
(IEC 61966-2-1 [33], Zahlen nach Lindbloom [34]):

```math
\begin{pmatrix}R\\G\\B\end{pmatrix} =
\begin{pmatrix}
 3{,}2404542 & -1{,}5371385 & -0{,}4985314\\
-0{,}9692660 &  1{,}8760108 &  0{,}0415560\\
 0{,}0556434 & -0{,}2040259 &  1{,}0572252
\end{pmatrix}
\begin{pmatrix}X\\Y\\Z\end{pmatrix}.
```

**Weißabgleich.** Jeder Kanal wird durch den Wert geteilt, den das Sonnenlicht oberhalb der
Atmosphäre hätte. Ungefiltertes Sonnenlicht ist damit genau weiß (1, 1, 1), wie im Original mit
seiner weißen Sonne. Die Farben sind also relativ zum Sonnenlicht, nicht zu D65.

### 10.3 Belichtung

- **Original:** feste Belichtung 25 auf dem linearen RGB. Die Leuchtdichte rechnet der Code dort
  mit den Rec.-709-Gewichten [35]: $Y = 0{,}2126R + 0{,}7152G + 0{,}0722B$.
- **Ring:** keine feste Belichtung. Das Licht wird in absoluten Einheiten genommen (photopische und
  skotopische Leuchtdichte in cd/m²) und so in Displayfarben umgerechnet, wie ein an den ganzen
  Himmel angepasstes Auge sie sieht: die Helligkeit nach dem Gesetz von Stevens, der Farbton mit
  dem Beitrag der Stäbchen bei wenig Licht, die Farben verblassend, wenn es dunkel wird. All das
  beschreibt [PHYSIOLOGIE.md](PHYSIOLOGIE.md).

### 10.4 Tonwertabbildung

Im Original nach der Belichtung, je Kanal (der Ring nutzt die Schritte 2 und 3, siehe
[PHYSIOLOGIE.md](PHYSIOLOGIE.md), Abschnitt 6):

1. **Warmer Ausgleich für dunkle Himmel** (aus Horizon übernommen, rein gestalterisch): mit
   $w = 1/(1 + 2Y)$ werden $R \cdot (1 + 0{,}05w)$, $G \cdot (1 - 0{,}05w)$, $B \cdot (1 + 0{,}1w)$
   gerechnet.
2. **ACES-Filmkurve** in der Näherung von Narkowicz (2016) [36]:

   ```math
   f(x) = \operatorname{clamp}\!\left(\frac{x\,(2{,}51x + 0{,}03)}{x\,(2{,}43x + 0{,}59) + 0{,}14},\ 0,\ 1\right).
   ```

3. **Gamma** $f^{1/2{,}2}$ als Näherung der sRGB-Übertragungsfunktion [33], dann auf 0…255.

### 10.5 Mittelung über den Azimut

Der Ring hat keine Himmelsrichtung. Für jede Höhe wird daher der Himmel ringsum gemittelt: Die
spektrale Strahldichte (vor allem Nichtlinearen) wird über 12 Azimute gemittelt. Weil der Himmel zur
Ebene durch Sonne und Zenit spiegelsymmetrisch ist, werden nur 6 berechnet (15°, 45°, …, 165°
von der Sonne). So kommt das Morgen- und Abendrot auf der Sonnenseite mit hinein, ohne dass die
Sonnenscheibe selbst dominiert, denn keine Stützstelle liegt bei 0°.

---

## 11. Himmelsfarben: Projektion und Tabelle

### 11.1 Projektion der Höhe auf den Ring

Code: [`SkyProjection.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyProjection.kt).
Ein Ringabschnitt zeigt den Himmel vom Horizont (Anteil 0) bis zu einer einstellbaren Höhe $e_t$
(Anteil 1). Die Höhen werden wie in einem perspektivischen Foto, also gleichmäßig in $\tan e$,
verteilt (gnomonische Projektion):

```math
u(e) = \frac{\tan e}{\tan e_t},\qquad e(u) = \arctan(u\,\tan e_t),\qquad e_t \le 80° .
```

Die Begrenzung auf 80° (und $|e| \le 89°$) hält $\tan$ endlich. Das Original nutzt dieselbe
Abbildung mit $e_t = 37{,}5°$ (halbes vertikales Sichtfeld von 75°).

### 11.2 Vorberechnete Tabelle

Code: [`SkyGradientTable.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyGradientTable.kt).
Die Streurechnung ist für jede Minute zu teuer. Der Gradle-Task `:core:generateSkyTable` legt
deshalb beim Build die Tabelle an:

- **Himmelsfarben:** Sonnenhöhe −18°…90° in 0,5°-Schritten (217 Zeilen) × Blickhöhe 0°…80° in
  1°-Schritten (81 Spalten), je 3 Byte sRGB, zur Laufzeit bilinear interpoliert. Sonnenhöhen unter
  −18° zählen als −18°: Dann bleibt nur noch das Eigenleuchten des Nachthimmels.

Ein Test prüft, dass die mitgelieferte Tabelle mit dem Modell übereinstimmt. Weil F-Droid die App
nachbaut und Byte für Byte vergleicht, rechnet das Himmelsmodell mit `java.lang.StrictMath`, dessen
Ergebnisse auf jedem Rechner gleich sind, statt mit den Mathefunktionen der Plattform, die im
letzten Bit abweichen können.

---

## 12. Darstellung im Zifferblatt

Code: [`ClockFaceRenderer.kt`](../app/src/main/kotlin/com/umfluten/lighttimeclouds/widget/ClockFaceRenderer.kt).
Hier wird nichts Neues gerechnet; der Abschnitt hält fest, wie die Größen von oben auf das
Zifferblatt kommen und wo die Darstellung bewusst von der Physik abweicht.

### 12.1 Ringfarbe

Jeder Ringabschnitt steht für einen Zeitpunkt $t$. Dafür wird die **geometrische** Sonnenhöhe
$h_\odot(t)$ am Ort berechnet, und:

- **„Himmel“:** 24 Farben aus der Tabelle (Abschnitt [11.2](#112-vorberechnete-tabelle)) vom
  Horizont bis $e_t$. Die Farbe der Nacht ist das Eigenleuchten des Nachthimmels, so wie das Auge
  es sieht (Abschnitt [9.7](#97-der-nachthimmel)); Mondlicht ist nicht im Modell.
- **„Phasenfarben“:** eine feste Farbe je Himmelszustand (Abschnitt [6.4](#64-himmelszustand)),
  entnommen einer gestalteten Palette, die zwischen Stützstellen an den physikalischen Schwellen
  −18°, −12°, −6°, −0,833°, 6° und 90° linear interpoliert ist.

### 12.2 Sonne und Mond im Ring

Der Abstand vom Horizontrand des Ringbands folgt derselben Projektion wie die Himmelsfarben,
$u = \tan h / \tan e_t$. Die Körper stehen also genau dort, wo ihre Höhe im Himmelsverlauf liegt.
Unter dem Horizont ($h < 0$) werden sie nicht gezeichnet, über $e_t$ als kleiner Punkt am Rand
gegenüber dem Horizont. Die Sonne nutzt die geometrische Höhe (wie die Ringfarbe), der Mond die
gesehene, mit Refraktion. Die optionale Mondbahn ist $h_\mathrm{Mond}(t)$ über den angezeigten
Zeitraum. Unter dem Horizont verlässt sie das Band (begrenzt auf $u \ge -0{,}5$ und am Rand
abgeschnitten), über $e_t$ läuft sie am Rand gegenüber dem Horizont entlang ($u = 1$), wo dann
auch der Punkt des Mondes sitzt.

### 12.3 Mondbahn mit eigenem Zeiger oder am Stundenzeiger

Die Entfernung von der Mitte ist $r = r_\mathrm{Horizont} + r_A \sin h_\mathrm{Mond}$ (außen über,
innen unter dem Horizont). Der Winkel ist die Uhrzeit selbst (am Stundenzeiger; die Bahn schneidet
den Horizontkreis dann zu den echten Auf- und Untergangszeiten) oder „Uhrzeit minus Nachlauf“
(Abschnitt [7.3](#73-nachlauf-hinter-der-sonne)). Die Himmelsrichtungen N/O/S/W werden dort
markiert, wo der Azimut −180°, −90°, 0°, 90° oder 180° kreuzt, linear interpoliert zwischen
Abtastpunkten; der Azimutschritt wird dafür über ±180° stetig fortgesetzt.

### 12.4 Mondphasenscheibe

Die Phasenscheibe zeigt den Mond so, wie man ihn am Ort sieht, mit dem oberen Bildschirmrand als
Zenit. Die Terminator-Grenze zwischen Tag und Nacht auf der Mondkugel ist ein Großkreis; von der
Erde aus gesehen erscheint er als halbe Ellipse. In einem Koordinatensystem mit $x$ zum hellen Rand
(gedreht um den Winkel aus Abschnitt [7.4](#74-neigung-der-sichel)) und $y$ quer dazu ist hell, was

```math
x \ \ge\ -(2k - 1)\sqrt{r^2 - y^2}
```

erfüllt. Das ist die halbe Scheibe auf der hellen Seite, vergrößert um eine halbe Ellipse mit der
Halbachse $r(2k-1)$ (Dreiviertelmond) oder verkleinert um eine solche (Sichel). Die helle Fläche ist
genau $k\,\pi r^2$, denn $2k - 1 = \cos i$ mit dem Phasenwinkel $i$ [1, Kap. 48]. Ein Test
vergleicht die gerenderte Scheibe pixelweise mit dieser Form.

Die Scheibe hat überall dieselbe Ausrichtung, auch wenn sie mit dem Mondzeiger um das Zifferblatt
wandert: Oben ist immer der Zenit.

---

## 13. Wettergrößen

Code: [`WeatherForecast.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/weather/WeatherForecast.kt),
[`WeatherOutlook.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/weather/WeatherOutlook.kt).
Die Wetterwerte rechnet die App nicht selbst; sie kommen stündlich von Open-Meteo [38]. Die App
legt nur fest, wie sie gedeutet und eingeteilt werden.

### 13.1 Bedeutung der Größen

Nach den Definitionen von Open-Meteo [38]:

| Größe | Einheit | Bezug |
|---|---|---|
| `cloud_cover` | % | Gesamtbedeckung als Flächenanteil, Momentanwert zur Stunde |
| `precipitation` | mm | Summe aus Regen, Schauern und Schnee (als Wasser) der **vorangehenden** Stunde |
| `snowfall` | cm | Neuschnee der vorangehenden Stunde |
| `precipitation_probability` | % | Wahrscheinlichkeit für mehr als 0,1 mm in der vorangehenden Stunde (aus Ensemble-Modellen) |
| `temperature_2m` | °C | Lufttemperatur in 2 m Höhe |
| `apparent_temperature` | °C | gefühlte Temperatur aus Windchill, relativer Feuchte und Sonnenstrahlung |

Ein Ringabschnitt (eine Stunde) bekommt die Mengen der Stunde, die er abdeckt, und den Mittelwert
der Momentanwerte an seinen Enden. Temperaturen zu beliebigen Zeitpunkten werden linear zwischen
den Stundenwerten interpoliert.

### 13.2 Regen oder Schnee

Open-Meteo gibt für die Umrechnung an: 7 cm Schnee ≈ 10 mm Wasser [38]. Eine Stunde gilt als
Schnee, wenn mindestens die Hälfte der Menge als Schnee fällt:

```math
\frac{10}{7}\cdot s_\mathrm{cm} \ \ge\ \frac{P_\mathrm{mm}}{2}.
```

Gibt es nur eine Wahrscheinlichkeit und keine Menge (unter 0,1 mm), entscheidet die
Lufttemperatur: ≤ 0 °C gilt als Schnee.

### 13.3 Intensität und Warnstufen

Einteilung der Menge pro Stunde nach dem Deutschen Wetterdienst:

| Klasse | ab mm/h | Quelle |
|---|---|---|
| sehr leicht (nur Wahrscheinlichkeit, oder ab 0,1 mm) | 0,1 | Open-Meteo-Schwelle für „nass“ [38] |
| leicht | 0,5 | DWD: Grenze „starker Sprühregen“ [39] |
| mäßig | 2,5 | DWD: Regen mäßig [39] |
| stark | 10 | DWD: Regen stark [39] |

Für Regen (nicht Schnee) zusätzlich die Starkregen-Warnstufen des DWD für eine Stunde [40]:
markant ab 15 l/m², Unwetter ab 25 l/m², extremes Unwetter ab 40 l/m².

Die Deckkraft der Niederschlagsfarbe ist die Wahrscheinlichkeit, bei einer vorhergesagten Menge
aber mindestens 25 %.

### 13.4 Wetter in Worten

Feste Regeln, keine Physik: Die gefühlte Temperatur wird erwähnt, wenn sie mindestens 3 °C von der
Lufttemperatur abweicht. Der Vergleich mit gestern nutzt die Lufttemperatur zur selben Uhrzeit.
Niederschlag wird für die nächsten 3 und 6 Stunden geprüft; Schnee hat dabei Vorrang vor Regen.

---

## 14. Vereinfachungen im Überblick

| Vereinfachung | Wirkung | Abschnitt |
|---|---|---|
| UT1 = UTC | ≤ 0,9 s Drehung, ≤ 14″ am Himmel | [2](#2-zeitskalen) |
| Schaltsekundentabelle fest im Code (bis 2017) | eine künftige Schaltsekunde verschiebt TT um 1 s (Mond ≈ 0,5″) | [2](#2-zeitskalen) |
| Polbewegung vernachlässigt | < 0,5″ | [3.4](#34-sternzeit) |
| Nutation auf 5 + 4 Terme gekürzt, Polynome bis $T^2$ bzw. $T^3$ | ≈ 0,2″ | [3](#3-orientierung-der-erde) |
| Reihen gekürzt (Abschnitt 4.6) | bis 4″ (Mond), 2″ (Sonne) zwischen 2000 und 2050 | [4.6](#46-kürzung-der-reihen) |
| Lichtlaufzeit des Mondes vernachlässigt | < 1″ | [4.2](#42-mond-elp-2000-82b) |
| Beobachter auf Meereshöhe | Horizont und Parallaxe für Höhe 0 | [5.1](#51-ort-auf-dem-wgs84-ellipsoid-und-parallaxe) |
| Standardatmosphäre für die Refraktion (10 °C, 1010 hPa) | am Horizont schwankt die wahre Refraktion um mehrere Bogenminuten | [5.3](#53-refraktion) |
| Mondphase aus geozentrischen Orten | Unterschied zur topozentrischen Phase vernachlässigbar | [7.2](#72-beleuchteter-anteil-und-phase) |
| Himmel ohne Wolken, 300 DU Ozon, ohne Mondlicht | Himmelsfarbe gilt für einen wolkenlosen Standardhimmel | [9](#9-himmelsfarben-das-streumodell), [12.1](#121-ringfarbe) |
| Ein Stadtaerosol fester Menge (τ₄₄₀ = 0,2), kugelförmig, Brechungsindex unabhängig von der Wellenlänge, überall dasselbe Höhenprofil | Dunst, Horizont und Aureole eines ziemlich klaren Tages; echter Dunst wechselt von Tag zu Tag | [9.6](#96-aerosole-mie-theorie-und-eine-gemessene-größenverteilung) |
| Nachthimmel gleichmäßig, mit dem Spektrum der Sonne, ohne Lichtverschmutzung | die Nacht eines natürlichen, mondlosen Himmels | [9.7](#97-der-nachthimmel) |
| Mehrfachstreuung isotrop und lokal homogen (Hillaire) | kleine Fehler in der Helligkeitsverteilung der tiefen Dämmerung | [9.5](#95-mehrfachstreuung-hillaire-2020) |
| Sehen des Himmels (Anpassung, Helligkeit, Farben bei wenig Licht) | siehe [PHYSIOLOGIE.md](PHYSIOLOGIE.md), Abschnitt 8 | [10.3](#103-belichtung) |
| Neigung der Sichel aus geozentrischen Orten | Abweichung zur topozentrischen Ansicht durch die Mondparallaxe (≤ 1°), bei schmaler Sichel etwas mehr | [7.4](#74-neigung-der-sichel) |

---

## 15. Quellen

**Astronomie**

1. J. Meeus: *Astronomical Algorithms*, 2. Aufl., Willmann-Bell, Richmond 1998.
2. S. E. Urban, P. K. Seidelmann (Hrsg.): *Explanatory Supplement to the Astronomical Almanac*,
   3. Aufl., University Science Books, Mill Valley 2013.
3. International Astronomical Union: Resolution B2 (2012), Neudefinition der Astronomischen
   Einheit. <https://syrte.obspm.fr/IAU_resolutions/Res_IAU2012_B2.pdf>
4. G. Petit, B. Luzum (Hrsg.): *IERS Conventions (2010)*, IERS Technical Note No. 36,
   Verlag des Bundesamts für Kartographie und Geodäsie, Frankfurt am Main 2010.
   <https://www.iers.org/TN36>
5. IERS Earth Orientation Centre: *Bulletin C* (Schaltsekunden, TAI − UTC).
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
14. IMCCE, Observatoire de Paris: Datenserver für ELP 2000-82B und VSOP87.
    <https://ftp.imcce.fr/pub/ephem/moon/elp82b/>, <https://ftp.imcce.fr/pub/ephem/planets/vsop87/>
15. National Imagery and Mapping Agency: *Department of Defense World Geodetic System 1984*,
    NIMA TR8350.2, 3. Aufl., Amendment 1 (3. Januar 2000).
16. Þ. Sæmundsson: Astronomical Refraction. *Sky and Telescope* 72, 70 (Juli 1986).
17. U.S. Naval Observatory, Astronomical Applications Department: Rise, Set, and Twilight
    Definitions. <https://aa.usno.navy.mil/faq/RST_defs>
18. V. Agafonkin: *SunCalc* (Konventionen für −0,3° und goldene Stunde).
    <https://github.com/mourner/suncalc>
19. M. Dowell, P. Jarratt: A modified regula falsi method for computing the root of an equation.
    *BIT Numerical Mathematics* 11, 168–174 (1971). <https://doi.org/10.1007/BF01934364>
20. B. Rhodes: *Skyfield: High precision research-grade positions for planets and Earth
    satellites generator*. Astrophysics Source Code Library, ascl:1907.024 (2019).
    <https://rhodesmill.org/skyfield/>
21. R. S. Park, W. M. Folkner, J. G. Williams, D. H. Boggs: The JPL Planetary and Lunar Ephemerides
    DE440 and DE441. *The Astronomical Journal* 161, 105 (2021).
    <https://doi.org/10.3847/1538-3881/abd414>

**Atmosphäre und Farbe**

22. D. Lazaro: *Horizon* (MIT-Lizenz). <https://github.com/dnlzro/horizon>
23. A. Helmer: *Production Sky Rendering*, Shadertoy (2021). <https://www.shadertoy.com/view/slSXRW>
24. S. Hillaire: A Scalable and Production Ready Sky and Atmosphere Rendering Technique.
    *Computer Graphics Forum* 39 (4), 13–22 (2020), Proc. EGSR.
    <https://doi.org/10.1111/cgf.14050>; Referenzimplementierung:
    <https://github.com/sebh/UnrealEngineSkyAtmosphere>
25. E. Bruneton, F. Neyret: Precomputed Atmospheric Scattering. *Computer Graphics Forum* 27 (4),
    1079–1086 (2008). <https://doi.org/10.1111/j.1467-8659.2008.01245.x>
26. E. Bruneton: *Precomputed Atmospheric Scattering*, Neuimplementierung 2017 mit Demo
    (`atmosphere/demo/demo.cc`, BSD-3-Clause).
    <https://github.com/ebruneton/precomputed_atmospheric_scattering>
27. S. Chandrasekhar: *Radiative Transfer*, Oxford University Press 1950 (Dover 1960).
28. W. M. Cornette, J. G. Shanks: Physically reasonable analytic expression for the
    single-scattering phase function. *Applied Optics* 31 (16), 3152–3160 (1992).
    <https://doi.org/10.1364/AO.31.003152>
29. Á. González: Measurement of Areas on a Sphere Using Fibonacci and Latitude–Longitude Lattices.
    *Mathematical Geosciences* 42, 49–64 (2010). <https://doi.org/10.1007/s11004-009-9257-x>
30. ASTM G173-03: *Standard Tables for Reference Solar Spectral Irradiances: Direct Normal and
    Hemispherical on 37° Tilted Surface*, ASTM International. Daten: National Laboratory of the
    Rockies (vormals NREL), <https://www.nlr.gov/grid/solar-resource/spectra-am1.5>
31. A. Serdyuchenko, V. Gorshelev, M. Weber, W. Chehade, J. P. Burrows: High spectral resolution
    ozone absorption cross-sections – Part 2: Temperature dependence. *Atmospheric Measurement
    Techniques* 7, 625–636 (2014). <https://doi.org/10.5194/amt-7-625-2014>; Daten: IUP Bremen,
    Molecular Spectroscopy, O₃-Referenzspektren 2011.
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

**Wetter**

38. Open-Meteo: Weather Forecast API, Beschreibung der stündlichen Variablen (CC BY 4.0).
    <https://open-meteo.com/en/docs>
39. Deutscher Wetterdienst: Wetterlexikon, „Niederschlagsintensität“.
    <https://www.dwd.de/DE/service/lexikon/begriffe/N/Niederschlagsintensitaet.html>
40. Deutscher Wetterdienst: Warnkriterien (Starkregen).
    <https://www.dwd.de/DE/wetter/warnungen_aktuell/kriterien/warnkriterien.html>

**Ergänzung: Refraktion**

41. G. G. Bennett: The Calculation of Astronomical Refraction in Marine Navigation. *Journal of
    Navigation* 35 (2), 255–259 (1982). <https://doi.org/10.1017/S0373463300022037>

**Ergänzung: Aerosole und Nachthimmel**

42. C. F. Bohren, D. R. Huffman: *Absorption and Scattering of Light by Small Particles*, Wiley
    1983, Anhang A (BHMIE).
43. O. Dubovik, B. N. Holben, T. F. Eck, A. Smirnov, Y. J. Kaufman, M. D. King, D. Tanré,
    I. Slutsker: Variability of Absorption and Optical Properties of Key Aerosol Types Observed in
    Worldwide Locations. *Journal of the Atmospheric Sciences* 59, 590–608 (2002).
    <https://doi.org/10.1175/1520-0469(2002)059<0590:VOAAOP>2.0.CO;2>
44. F. Falchi, P. Cinzano, D. Duriscoe, C. C. M. Kyba, C. D. Elvidge, K. Baugh, B. A. Portnov,
    N. A. Rybnikova, R. Furgoni: The new world atlas of artificial night sky brightness.
    *Science Advances* 2, e1600377 (2016). <https://doi.org/10.1126/sciadv.1600377>

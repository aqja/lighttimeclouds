# Menschliches Sehen in LightTimeClouds

*English version: [PHYSIOLOGY.md](PHYSIOLOGY.md)*

Das Streumodell ([PHYSIK.md](PHYSIK.md), Abschnitte 9–11) liefert die spektrale Strahldichte des
Himmels: physikalisches Licht, von etwa 10⁴ cd/m² am Tag bis 10⁻⁴ cd/m² in der Nacht. Diesen
Umfang kann kein Display zeigen, und das Auge sieht ihn auch nicht so: Es passt sich dem Licht an,
wechselt bei Dunkelheit von den Zapfen zu den Stäbchen, sieht Farben sich verschieben und
verblassen und beurteilt Helligkeit relativ zu dem, woran es angepasst ist. Dieses Dokument
beschreibt, wie die App aus dem berechneten Licht die Farben des Rings macht, so wie ein Mensch den
Himmel sähe: welche Modelle sie nutzt, mit welchen Formeln und Zahlen, woher sie stammen und
welche Vereinfachungen gemacht wurden. Die Quellen stehen nummeriert am Ende ([Quellen](#9-quellen)).

Alles steht in `perceive` und seinen Hilfsfunktionen in
[`sky/SkyModel.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyModel.kt), die
photometrischen Funktionen in
[`sky/Spectrum.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Spectrum.kt). Es läuft
beim Build, in die Farbtabellen, aus denen das Widget zeichnet (PHYSIK.md, Abschnitt 11.2).

## Inhalt

1. [Maße des Lichts für das Auge](#1-maße-des-lichts-für-das-auge)
2. [Anpassung](#2-anpassung)
3. [Helligkeit](#3-helligkeit)
4. [Farbe bei wenig und viel Licht](#4-farbe-bei-wenig-und-viel-licht)
5. [Der Nachthimmel](#5-der-nachthimmel)
6. [Auf das Display](#6-auf-das-display)
7. [Was herauskommt](#7-was-herauskommt)
8. [Vereinfachungen im Überblick](#8-vereinfachungen-im-überblick)
9. [Quellen](#9-quellen)

---

## 1. Maße des Lichts für das Auge

Das Auge hat zwei Arten von Rezeptoren. **Zapfen** (drei Typen, L, M und S, für lange, mittlere und
kurze Wellenlängen) sehen Farbe und arbeiten bei Tageslicht; **Stäbchen** sehen keine Farbe, sind
aber weit empfindlicher und übernehmen in der Nacht. Beide gewichten das Spektrum verschieden, darum
behält das Modell die spektrale Strahldichte $L(\lambda)$ (48 Bänder von 360 bis 830 nm im Abstand
von 10 nm), bis sie das Auge erreicht.

**Photopische Leuchtdichte** (Zapfensehen), in cd/m²:

```math
L_p = 683\,\tfrac{\mathrm{lm}}{\mathrm W}\sum_\lambda L(\lambda)\,V(\lambda)\,\Delta\lambda ,
```

mit $V(\lambda)$, der photopischen Hellempfindlichkeit der CIE, hier die Normspektralwertfunktion
$\bar y$ in der Näherung von Wyman, Sloan und Shirley (PHYSIK.md, Abschnitt 10.2).

**Skotopische Leuchtdichte** (Stäbchensehen), in skotopischen cd/m²:

```math
L_s = 1700\,\tfrac{\mathrm{lm}}{\mathrm W}\sum_\lambda L(\lambda)\,V'(\lambda)\,\Delta\lambda ,
```

mit $V'(\lambda)$, der skotopischen Hellempfindlichkeit der CIE von 1951, tabelliert von 380 bis
770 nm [11], mit dem Maximum bei 507 nm. Sonnenlicht und Taghimmel haben etwa 2,5- bis 3-mal so
viel skotopische wie photopische Leuchtdichte; die Stäbchen sind für blaugrünes Licht
empfindlicher.

**Zapfenerregungen** $E_L, E_M, E_S$: die Zapfengrundempfindlichkeiten von Smith und Pokorny [10]
als Linearkombinationen der Normspektralwertfunktionen,

```math
\begin{aligned}
L &= 0{,}15514\,X + 0{,}54312\,Y - 0{,}03286\,Z,\\
M &= -0{,}15514\,X + 0{,}45684\,Y + 0{,}03286\,Z,\\
S &\propto Z,
\end{aligned}
```

jede auf ein Maximum von 1 normiert, so wie Wanat und Mantiuk sie verwenden [9]. Die
**Stäbchenerregung** $E_R$ ist die Summe mit $V'(\lambda)$ (Maximum 1). Smith und Pokorny haben ihre
Transformation auf den nach Judd und Vos korrigierten Funktionen definiert; die App wendet sie auf
CIE 1931 an (Abschnitt [8](#8-vereinfachungen-im-überblick)).

## 2. Anpassung

Das Auge passt sich dem Licht um es herum an, in Sekunden bis Minuten. Die Dämmerung ändert sich
langsam, deshalb nimmt das Modell ein Auge an, das in jedem Augenblick vollständig angepasst ist,
ohne zeitlichen Verlauf.

Woran passt es sich an? Wer zum Himmel schaut, hat den Himmel selbst im Blickfeld. Das Modell nimmt
die **mittlere Leuchtdichte des ganzen Himmels** über dem Horizont, getrennt für Zapfen und
Stäbchen:

```math
L_{wa} = \frac{\sum_e \bar L(e)\cos e}{\sum_e \cos e},\qquad e = 3{,}75°, 11{,}25°, \dots, 86{,}25° ,
```

wobei $\bar L(e)$ die Leuchtdichte in der Höhe $e$ ist, wie beim Ring über alle Azimute gemittelt
(PHYSIK.md, Abschnitt 10.5), und $\cos e$ die Höhen nach Raumwinkel gewichtet. Ohne die
Sonnenscheibe selbst ist das die Leuchtdichte des Himmels, wie das Auge ihn sieht, von etwa
6800 cd/m² (Sonne auf 40°) bis etwa 2·10⁻⁴ cd/m² in der Nacht (Abschnitt [7](#7-was-herauskommt)).

## 3. Helligkeit

### 3.1 Das Gesetz von Stevens und der überarbeitete Operator von Tumblin und Rushmeier

Wie hell ein Licht erscheint, wächst mit seiner Leuchtdichte nach einem Potenzgesetz, dessen
Exponent von der Anpassung abhängt (Stevens und Stevens [5]). Tumblin und Rushmeier [3] bauten
darauf einen Operator, der den Helligkeitseindruck zwischen realer Szene und Display erhält; in
seiner überarbeiteten Form (Tumblin, Hodgins und Guenter 1999 [4]) erscheint eine Szene, die fast
dunkel ist, auch dunkel und nicht als das mittlere Grau der ersten Fassung [4; 16]. Mit der
Leuchtdichte $L$ eines Punktes am Himmel, der Anpassungsleuchtdichte $L_{wa}$ (Abschnitt
[2](#2-anpassung)) und der des Auges am Display $L_{da}$:

```math
L_d = L_{da}\; m\,\left(\frac{L}{L_{wa}}\right)^{\gamma(L_{wa})/\gamma(L_{da})},\qquad
m = C_\mathrm{max}^{\,(\gamma(L_{wa})/\gamma(L_{da}) - 1)/2},
```

```math
\gamma(L) = \begin{cases}2{,}655 & L > 100\ \mathrm{cd/m^2}\\ 1{,}855 + 0{,}4\,\log_{10}(L + 2{,}3\cdot10^{-5}) & \text{sonst.}\end{cases}
```

So steht $\gamma$ in der Übersicht von Reinhard und Devlin [6]; der ganze Operator folgt der
Implementierung in der HDR Toolbox [7] (die an einer Stelle den natürlichen Logarithmus nimmt, wo
die App wie bei $\gamma$ selbst $\log_{10}$ verwendet). Displayparameter:

| Größe | Wert |
|---|---|
| Höchste Leuchtdichte des Displays $L_{d,\max}$ | 100 cd/m² |
| Anpassung des Auges am Display $L_{da}$ | 40 cd/m², etwas unter der halben Höchstleuchtdichte, die Ward [2] und Ferwerda et al. [1] annehmen (Standardwert der Toolbox: 20) |
| Kontrast des Displays $C_\mathrm{max}$ | 1000, wie bei einem gängigen LCD (Standardwert der Toolbox: 100) |

Der Operator arbeitet mit der photopischen Leuchtdichte. Seine wichtigste Eigenschaft hier: Die
dargestellte Helligkeit des Anpassungsniveaus, $L_{da}\,m$, fällt stetig mit $L_{wa}$, von
$1{,}2\,L_{da}$ bei Tageslicht bis $0{,}03\,L_{da}$ in der Dunkelheit ($\gamma \to 0$,
$m \to C_\mathrm{max}^{-1/2}$). Die beiden Displayparameter bestimmen den Gesamteindruck: $L_{da}$,
wie hell der Tag erscheint, $C_\mathrm{max}$, wie weit die Nacht darunter liegt; mit den
Standardwerten der Toolbox war das Blau des hohen Himmels dunkel und die Nacht zu hell. Der Ring wird deshalb vom Tag über die Dämmerung bis in die Nacht
gleichmäßig dunkler (ein Test prüft das ab 2° unter dem Horizont), und die Nacht ist dunkel, aber
nicht schwarz.

### 3.2 Warum kein Schwellenmodell

Eine frühere Fassung dieses Modells nutzte den Operator von Ferwerda, Pattanaik, Shirley und
Greenberg [1], der **Schwellen** angleicht: Ein Kontrast, der in der realen Szene gerade sichtbar
ist, ist es auch auf dem Display (nach Ward [2]). Er hat getrennte Schwellenkurven für Zapfen und
Stäbchen und blendet die Stäbchen über den mesopischen Bereich (0,01 bis 10 cd/m²) ein. Für den Ring
ging das schief: Zwischen etwa 0,04 und 10 cd/m² sind die Stäbchen voll angepasst (Weber-Gesetz),
ihr Beitrag erscheint auf dem Display also fast so hell wie Tageslicht, während ihr Gewicht wächst,
je dunkler es wird. Der obere Himmel wurde dunkler, bis die Sonne etwa 10° unter dem Horizont stand,
und dann bis −13° wieder heller (um fast das Dreifache). Die Autoren nennen die Grenze selbst: Ein
kontrastbasierter Operator sagt die Sichtbarkeit an der Schwelle voraus, „may not account well for
suprathreshold appearance“ [1]. Beim Ring geht es um das Aussehen, darum wird stattdessen die
Helligkeit angeglichen (Abschnitt 3.1).

## 4. Farbe bei wenig und viel Licht

### 4.1 Der Purkinje-Effekt: Stäbchen in den Kanälen der Zapfen

Wenn das Licht schwindet, verschieben sich die Farben: Rot wird dunkler, Blau und Grün erscheinen
heller und blauer. Cao, Pokorny, Smith und Zele [8] haben gemessen, warum: Das Signal der Stäbchen
läuft über dieselben Nervenbahnen wie die Signale der Zapfen und addiert sich zu den Kanälen L, M
und S, bei wenig Licht am stärksten zum Kanal S. Wanat und Mantiuk [9] geben die Gewichte als
Tabelle an:

| Leuchtdichte | 10 cd/m² | 0,62 cd/m² | 0,1 cd/m² |
|---|---|---|---|
| $k_1$ (zu L und M) | 0 | 0,0173 | 0,173 |
| $k_2$ (zu S) | 0 | 0,0101 | 0,357 |

Die App interpoliert dazwischen linear, wie Wanat und Mantiuk, und hält die Werte unter
0,1 cd/m² fest, wo es keine Messungen gibt. Die Leuchtdichte ist das Anpassungsniveau $L_{wa}$. Die
gesehene Farbe ist dann

```math
\begin{pmatrix}L\\M\\S\end{pmatrix}_\mathrm{gesehen} =
\begin{pmatrix}E_L\\E_M\\E_S\end{pmatrix} + E_R\begin{pmatrix}k_1\\k_1\\k_2\end{pmatrix},
```

über die Umkehrung der Zapfentransformation (Abschnitt 1) und den Weißabgleich aus PHYSIK.md,
Abschnitt 10.2, zurück in lineares sRGB gerechnet. Ihr Farbton geht in die Displayfarbe ein, ihre
Helligkeit kommt aus Abschnitt 3.

### 4.2 Verblassende Farben

Die Zapfen verlieren bei wenig Licht an Empfindlichkeit, und die Farben verblassen. Wanat und
Mantiuk [9] haben gemessen, wie viel Sättigung man wegnehmen muss, damit eine Szene bei geringerer
Leuchtdichte gleich aussieht, und angepasst:

```math
s(Y) = \frac{Y}{Y + 0{,}108},\qquad
C_d = \left(\frac{C}{Y}\right)^{s(L_{wa})/s(L_{da})} L_d
```

je Kanal $C$ der gesehenen Farbe (Abschnitt 4.1), $Y$ ihre Leuchtdichte und $L_d$ die Helligkeit aus
Abschnitt 3. Ihr Experiment reichte bis etwa 0,1 cd/m²; darunter hält die App $s$ auf dem Wert dort
fest, wie die Gewichte aus Abschnitt 4.1. Ohne das würde ein schwacher, warmer Horizont in
gesättigtem Orange gezeigt, mit der Helligkeit, die ihm die Stäbchen geben.

### 4.3 Kräftigere Farben im Hellen

Umgekehrt wirken Farben umso kräftiger, je heller es ist: der Hunt-Effekt [21]. Der Taghimmel leuchtet
rund 60-mal so hell wie das Weiß des Displays, dieselbe Farbart wirkt dort also blasser als am Himmel.
Abschnitt 4.2 erfasst das nicht, $s$ ist oberhalb von etwa 10 cd/m² praktisch 1. Kim, Weyrich und
Kautz [22] haben die Buntheit $M$ bis 16 860 cd/m² schätzen lassen und fanden sie linear im
Logarithmus der Leuchtdichte $L_w$ des Referenzweiß, mit einem Chroma $C$, das mit der Potenz 0,62
der Gegenfarbensignale wächst:

```math
M = C\,(0{,}11\log_{10} L_w + 0{,}61)
```

Damit eine Farbe auf dem Display so bunt wirkt wie am Himmel, müssen die Gegenfarbensignale also um

```math
h = \left(\frac{0{,}11\log_{10} L_{wa} + 0{,}61}{0{,}11\log_{10} L_{d,\max} + 0{,}61}\right)^{1/0{,}62}
```

wachsen. Die App multipliziert den Exponenten aus Abschnitt 4.2 mit $h$, was näherungsweise
dasselbe tut, weil er die Verhältnisse der Kanäle im Logarithmus spreizt. Als Referenzweiß dient
die mittlere Leuchtdichte des Himmels $L_{wa}$ (Abschnitt 2). Bis zum Weiß des Displays ist $h = 1$, dort gilt
Abschnitt 4.2, und über 16 860 cd/m² gibt es keine Messung, also bleibt der Wert dort stehen. Bei
6000 cd/m² ist $h = 1{,}41$.

Nachgerechnet mit dem vollständigen Modell von Kim et al. erreicht der Ring zusammen mit der
Tonkurve aus Abschnitt 6 ab 15° Höhe das 0,9- bis 1,04-Fache der Buntheit, die das Modell dem Himmel
zuschreibt; vorher waren es oft nur 60 bis 80 %. Das prüft die Näherung, nicht die Messung, denn
Maßstab und Korrektur stammen aus derselben Quelle.

### 4.4 Nicht verwendet: ein Blauton für die Nacht

Nachtszenen sind in Gemälden und Filmen oft blau. Durand und Dorsey [12] fügen dunklen Szenen eine
Blauverschiebung hinzu und nennen diese Tradition als Begründung; Thompson, Shirley und Ferwerda [17]
beschreiben sie als ein Blau, das in der Farbart etwa 0,03 unter dem Weißpunkt liegt, und Kirk und
O'Brien [13] kritisieren das Einblenden einer einzelnen blauen Farbe. Die App nutzt sie nicht: Der
Farbton bei wenig Licht kommt nur aus dem gemessenen Beitrag der Stäbchen aus Abschnitt 4.1.

## 5. Der Nachthimmel

Ohne Sonne leuchtet der Himmel noch: Airglow der hohen Atmosphäre, Sternlicht und Zodiakallicht.
Das Modell legt konstant **174 µcd/m²** (22,0 mag/arcsec²) darunter, die natürliche Helligkeit des
Himmels, die Falchi et al. [15] für den Hintergrund fern der Milchstraße bei geringer
Sonnenaktivität annehmen, überall am Himmel gleich und mit dem Spektrum der Sonne (Abschnitt
[8](#8-vereinfachungen-im-überblick)). Daran passt sich das Auge an, wenn die Dämmerung vorbei ist,
und so ergibt sich die Farbe der Nacht aus dem Modell: ein dunkles Blaugrau. Lichtverschmutzung ist
nicht enthalten; über einer Stadt wie Berlin ist der Nachthimmel 10- bis 50-mal heller.

## 6. Auf das Display

1. **Helligkeit** $L_d$ (Abschnitt 3), **Farbton und Sättigung** (Abschnitt 4), geteilt durch
   $L_{d,\max}$.
2. **Lichter** werden mit der ACES-Filmkurve in der Näherung von Narkowicz [14] gestaucht
   (PHYSIK.md, Abschnitt 10.4), statt abgeschnitten zu werden: Abschneiden macht aus dem hellen,
   dunstigen Horizont ein hartes, leicht türkises Band. Die Kurve wirkt auf den hellsten Kanal, die
   übrigen werden im selben Verhältnis skaliert, und so bleibt der Farbton erhalten. Je Kanal
   angewandt, bleicht sie helle Farben aus, was den Taghimmel weißlich machte, und ihr Fuß hebt im
   Dunkeln die Sättigung, was keine Eigenschaft des Auges ist.
3. **Gamma** $f^{1/2{,}2}$ und 8 Bit je Kanal.

Die Sonnenscheibe durchläuft dieselben Schritte, aber ohne den Hunt-Effekt: Sie ist um
Größenordnungen heller als alles Gemessene und käme sonst bei tiefem Stand rein rot heraus. Weit
heller als das Display, wird sie in voller Helligkeit mit erhaltenem Farbton gezeigt. Je Kanal
getont, liefen Rot und Grün zuerst in die Begrenzung und machten aus einer orangen Sonne reines Gelb
(PHYSIK.md, Abschnitt 12.2). Ihre Aureole ist der Himmel neben ihr, gesehen wie der übrige Himmel: die
Ringfarbe in Sonnenhöhe plus der Überschuss des Himmels zur Sonne hin, durch dieselben Schritte 1 bis
3, und in „Screen“-Mischung über den Ring gelegt. Zur Sonne hin wird sie in keinem Kanal schwächer:
Dort liegt das Licht so weit über dem Display, dass die Kurve nur noch seine etwas blauere Farbe
zeigte, während es für das Auge einfach heller wird.

Der Mond unterscheidet sich in vier Punkten. Anders als der Himmel ist er ein kleiner heller Körper,
den die Zapfen auch im Dunkeln voll sehen; Stäbchen und verblassende Farben (Abschnitt 4) richten sich
deshalb nach seiner eigenen Leuchtdichte, sonst würde sein Orange am Horizont zu Grau verblassen. Das
Auge ist nicht nur an den mondlosen Himmel angepasst, sondern auch an den mondbeschienenen, den
Himmel mit der Sonne in Mondhöhe, gedämpft um das Verhältnis von Mondlicht zu Sonnenlicht; sonst käme
ein Vollmond matt heraus. Außerdem zählt der Mond selbst mit, auf den das Auge schaut: Das Modell nimmt
dafür wie Ward Larson und andere [18] einen Fleck von 1° um den Fixierpunkt an, den die Mondscheibe zu
27 % füllt, und zählt in jeder Phase die ganze Scheibe. Ohne das fiele eine schmale Sichel am dunklen
Himmel matt und bräunlich aus, viel dunkler als in der Dämmerung, weil die Helligkeitsabbildung bei
geringer Anpassung stark staucht. Und ist er schwächer als der Himmel, wie meist am Tag, fügt er ihm nur
Licht hinzu: Wo der Blick auf ihn einen Kanal unter den Himmel dahinter senken würde, bleibt dessen Wert.
Der Hunt-Effekt richtet sich auch bei ihm nach dem Himmel, nicht nach dem Fleck auf der Scheibe.

Der Fleck ist eine Festlegung, keine Physiologie. Gemessen mittelt das Auge viel enger, in seinem
einfachsten Modell mit einem Gaußkern von σ = 0,131° über die logarithmierte Leuchtdichte [19], und passt
sich an eine Sichel weit weniger an, als der Fleck annimmt: Bei 10 % beleuchtet liegt die Leuchtdichte
das 500- bis 10 000-Fache über der Anpassung, beim Vollmond das 60- bis 100-Fache. Für das Ergebnis
zählt aber die empfundene Helligkeit, und die senkt die starke Anpassung beim Vollmond deutlich. Liest
man sie bei dieser gemessenen Anpassung aus den Kurven von Stevens und Stevens [20] ab, liegen alle Phasen eines
Mondes in 45° Höhe bei rund 30 bis 40 brils, obwohl die Leuchtdichte der Scheibe um das 15-Fache
schwankt. Eine Sichel ist also fast so hell wie ein Vollmond, und genauso nah liegen die Farben, die der
Fleck ergibt. Der Vergleich ist grob: Die Kurven gelten für eine Fläche von 5° bei kurzem Hinsehen, und
der Nachthimmel liegt unter dem Messbereich von [19].

Auch den Himmel selbst hellt er auf (PHYSIK.md, Abschnitt 12.2): Dort sieht das Auge die Summe beider
Himmel mit derselben Anpassung an beide. Der Ring folgt dabei den Schritten für den Himmel, seine
Aureole richtet sich nach der Leuchtdichte an jeder Stelle, weil die Zapfen neben dem Mond noch Farben
sehen.

## 7. Was herauskommt

Die mittlere Leuchtdichte des ganzen Himmels (Abschnitt 2) und die dargestellten Farben eines Rings
bis 45° (Horizont / 15° / 30° / 80° darüber), mit dem Aerosol aus PHYSIK.md, Abschnitt 9.6:

| Sonne | $L_{wa}$ photopisch | skotopisch | Ring (0° / 15° / 30° / 80°) |
|---|---|---|---|
| 40° | 6800 cd/m² | 19 000 | `#dfdccc` `#99bee7` `#85ace3` `#7296cf` |
| 10° | 3200 | 8200 | `#e8c58b` `#c3dcdf` `#98b8d6` `#6886b3` |
| 0° | 310 | 830 | `#8f9199` `#d6dbe0` `#a9bdde` `#8099c8` |
| −6° | 2,1 | 6,6 | `#5d6474` `#8da3c4` `#6e89b9` `#536c9e` |
| −10° | 0,029 | 0,084 | `#333133` `#525a6a` `#3f485b` `#343d4f` |
| −18° | 2,0·10⁻⁴ | 5,1·10⁻⁴ | `#232326` `#232429` `#232429` `#232428` |

- **Tag:** blau, zum dunstigen Horizont hin blasser.
- **Sonnenuntergang:** Der Horizont selbst wird dunkler, darüber liegt ein helleres Band: Mit flachem
  Blick sieht man nur den nahen Dunst, den die tiefe Sonne kaum noch erreicht (PHYSIK.md,
  Abschnitt 9.6).
- **Blaue Stunde** (Sonne um −6°): ein klares Blau.
- **Späte Dämmerung:** schieferblau, wenn die Stäbchen übernehmen und ihr Blau beitragen.
- **Nacht:** das Leuchten des Nachthimmels, dunkles Blaugrau.

## 8. Vereinfachungen im Überblick

| Vereinfachung | Auswirkung | Abschnitt |
|---|---|---|
| Auge immer vollständig angepasst, ohne zeitlichen Verlauf | richtig für die langsame Dämmerung, nicht für einen plötzlichen Wechsel | [2](#2-anpassung) |
| Anpassung an das Mittel des ganzen Himmels, ohne Sonnenscheibe und Boden | eine dunkle Landschaft unter dem Horizont würde es etwas senken | [2](#2-anpassung) |
| Display: 100 cd/m², Auge auf 40 cd/m² angepasst, Kontrast 1000 | die Gesamthelligkeit von Tag und Nacht im Widget hängt davon ab | [3.1](#31-das-gesetz-von-stevens-und-der-überarbeitete-operator-von-tumblin-und-rushmeier) |
| Stäbchengewichte und Sättigung unter 0,1 cd/m² festgehalten | dort gibt es keine Messungen; der Blauton der Nacht beruht auf dem Wert bei 0,1 | [4.1](#41-der-purkinje-effekt-stäbchen-in-den-kanälen-der-zapfen), [4.2](#42-verblassende-farben) |
| Zapfengrundempfindlichkeiten aus CIE 1931 statt Judd-Vos | kleine Abweichungen im Violetten und Blauen | [1](#1-maße-des-lichts-für-das-auge) |
| Nachthimmel gleichmäßig, mit dem Spektrum der Sonne, ohne Lichtverschmutzung | die Nachtfarbe eines natürlichen, mondlosen Himmels | [5](#5-der-nachthimmel) |
| Anpassung an den Mond über einen Fleck von 1° mit der ganzen Scheibe in jeder Phase | das Auge mittelt enger; die Helligkeit der Phasen passt trotzdem zu den Messungen, aber nur grob geprüft | [6](#6-auf-das-display) |
| Hunt-Effekt aus Messungen an Farbfeldern vor dunklem Umfeld, Referenzweiß gleich mittlerer Himmelsleuchtdichte | die Stärke ist unsicher: Buntheit ist schwer zu schätzen (Streuung um 23 %), und ein heller Hintergrund senkt sie; für freien Blick in den Himmel gibt es keine Messung | [4.3](#43-kräftigere-farben-im-hellen) |
| ACES-Kurve für die Lichter, auf dem hellsten Kanal | gestalterische Stauchung der hellsten Stellen; der Farbton bleibt auch dort, wo das Auge fast Weiß sieht | [6](#6-auf-das-display) |

---

## 9. Quellen

1. J. A. Ferwerda, S. N. Pattanaik, P. Shirley, D. P. Greenberg: A Model of Visual Adaptation for
   Realistic Image Synthesis. *Proc. SIGGRAPH 96*, 249–258 (1996).
   <https://doi.org/10.1145/237170.237262>
2. G. Ward: A contrast-based scalefactor for luminance display. In P. Heckbert (Hrsg.): *Graphics
   Gems IV*, Academic Press 1994, 415–421.
3. J. Tumblin, H. Rushmeier: Tone Reproduction for Realistic Images. *IEEE Computer Graphics and
   Applications* 13 (6), 42–48 (1993).
4. J. Tumblin, J. K. Hodgins, B. K. Guenter: Two Methods for Display of High Contrast Images.
   *ACM Transactions on Graphics* 18 (1), 56–94 (1999). <https://doi.org/10.1145/300776.300783>
5. S. S. Stevens, J. C. Stevens: Helligkeit als Potenzfunktion der Leuchtdichte in Abhängigkeit von
   der Anpassung (1960), wie von Tumblin und Rushmeier [3] verwendet.
6. E. Reinhard, K. Devlin: Image Display Algorithms for High and Low Dynamic Range Display
   Devices (Übersicht; $\gamma(L)$ des überarbeiteten Operators von Tumblin und Rushmeier).
7. F. Banterle et al.: *HDR Toolbox*, `TumblinTMO.m`, `StevensCSF.m`.
   <https://github.com/banterle/HDR_Toolbox>
8. D. Cao, J. Pokorny, V. C. Smith, A. J. Zele: Rod contributions to color perception: linear with
   rod contrast. *Vision Research* 48 (26), 2586–2592 (2008).
9. R. Wanat, R. K. Mantiuk: Simulating and compensating changes in appearance between day and night
   vision. *ACM Transactions on Graphics* 33 (4) (2014), Proc. SIGGRAPH.
   <https://www.cl.cam.ac.uk/~rkm38/pdfs/wanat14lum_retargeting.pdf>
10. V. C. Smith, J. Pokorny: Spectral sensitivity of the foveal cone photopigments between 400 and
    500 nm. *Vision Research* 15 (2), 161–171 (1975).
11. Skotopische Hellempfindlichkeit $V'(\lambda)$ der CIE von 1951, tabelliert in: HyperPhysics,
    Georgia State University, Luminous Efficacy Tables.
    <https://hyperphysics.gsu.edu/hbase/vision/efficacy.html>
12. F. Durand, J. Dorsey: Interactive Tone Mapping. *Proc. Eurographics Workshop on Rendering
    2000*, 219–230.
13. A. G. Kirk, J. F. O'Brien: Perceptually Based Tone Mapping for Low-Light Conditions. *ACM
    Transactions on Graphics* 30 (4), 42 (2011). <http://graphics.berkeley.edu/papers/Kirk-PBT-2011-08/>
14. K. Narkowicz: ACES Filmic Tone Mapping Curve (2016).
    <https://knarkowicz.wordpress.com/2016/01/06/aces-filmic-tone-mapping-curve/>
15. F. Falchi et al.: The new world atlas of artificial night sky brightness. *Science Advances* 2,
    e1600377 (2016). <https://doi.org/10.1126/sciadv.1600377>
16. G. Spielmann: *A New Efficient Real-Time Global Tone Mapping Method*, Diplomarbeit, TU Wien
    (2006), Abschnitt 4.1 (zu den Schwächen des Operators von 1993).
17. W. B. Thompson, P. Shirley, J. A. Ferwerda: A Spatial Post-Processing Algorithm for Images of
    Night Scenes. *Journal of Graphics Tools* 7 (1), 1–12 (2002).
18. G. Ward Larson, H. Rushmeier, C. Piatko: A Visibility Matching Tone Reproduction Operator for
    High Dynamic Range Scenes. *IEEE Transactions on Visualization and Computer Graphics* 3 (4),
    291–306 (1997). <https://radsite.lbl.gov/radiance/papers/lbnl39882/tonemap.pdf>
19. P. Vangorp, K. Myszkowski, E. W. Graf, R. K. Mantiuk: A Model of Local Adaptation. *ACM
    Transactions on Graphics* 34 (6) (2015).
20. J. C. Stevens, S. S. Stevens: Brightness Function: Effects of Adaptation. *Journal of the Optical
    Society of America* 53 (3), 375 (1963). Abgelesen aus einer späteren Nachzeichnung der Kurven.
    <https://opg.optica.org/josa/abstract.cfm?uri=josa-53-3-375>
21. R. W. G. Hunt: Light and Dark Adaptation and the Perception of Color. *Journal of the Optical
    Society of America* 42 (3), 190–199 (1952). <https://doi.org/10.1364/JOSA.42.000190>
22. M. H. Kim, T. Weyrich, J. Kautz: Modeling Human Color Perception under Extended Luminance Levels.
    *ACM Transactions on Graphics* 28 (3), 27 (2009). <https://doi.org/10.1145/1531326.1531333>

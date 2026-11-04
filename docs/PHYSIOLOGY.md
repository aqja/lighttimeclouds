# Human Vision in LightTimeClouds

*Deutsche Fassung: [PHYSIOLOGIE.md](PHYSIOLOGIE.md)*

The scattering model ([PHYSICS.md](PHYSICS.md), sections 9–11) yields the sky's spectral radiance:
physical light, from about 10⁴ cd/m² by day down to 10⁻⁴ cd/m² at night. No display can show that
range, and the eye does not see it as it is: it adapts to the light, turns from cones to rods as it
gets dark, sees colors shift and fade, and judges brightness relative to what it is adapted to.
This document describes how the app turns the computed light into the ring's colors as a human
would see the sky: which models are used, with which formulas and numbers, where they come from,
and which simplifications were made. Sources are numbered at the end ([Sources](#9-sources)).

All of it is in `perceive` and its helpers in
[`sky/SkyModel.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyModel.kt), with the
photometric functions in
[`sky/Spectrum.kt`](../core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Spectrum.kt). It runs at
build time, into the color tables the widget draws from (PHYSICS.md, section 11.2).

## Contents

1. [Measures of light for the eye](#1-measures-of-light-for-the-eye)
2. [Adaptation](#2-adaptation)
3. [Brightness](#3-brightness)
4. [Color in dim light](#4-color-in-dim-light)
5. [The night sky](#5-the-night-sky)
6. [Onto the display](#6-onto-the-display)
7. [What comes out](#7-what-comes-out)
8. [Simplifications at a glance](#8-simplifications-at-a-glance)
9. [Sources](#9-sources)

---

## 1. Measures of light for the eye

The eye has two kinds of receptors. **Cones** (three types, L, M and S, for long, middle and short
wavelengths) see color and work in daylight; **rods** see no color but are far more sensitive and
take over at night. Both weigh the spectrum differently, so the model keeps the spectral radiance
$L(\lambda)$ (48 bands from 360 to 830 nm, 10 nm apart) until it reaches the eye.

**Photopic luminance** (cone vision), in cd/m²:

```math
L_p = 683\,\tfrac{\mathrm{lm}}{\mathrm W}\sum_\lambda L(\lambda)\,V(\lambda)\,\Delta\lambda ,
```

with $V(\lambda)$ the CIE photopic luminous efficiency, here the color matching function $\bar y$ in
the fit of Wyman, Sloan and Shirley (PHYSICS.md, section 10.2).

**Scotopic luminance** (rod vision), in scotopic cd/m²:

```math
L_s = 1700\,\tfrac{\mathrm{lm}}{\mathrm W}\sum_\lambda L(\lambda)\,V'(\lambda)\,\Delta\lambda ,
```

with $V'(\lambda)$ the CIE 1951 scotopic luminous efficiency, tabulated from 380 to 770 nm [11],
peaking at 507 nm. Sunlight and the daytime sky have about 2.5 to 3 times as much scotopic as
photopic luminance; the rods are more sensitive to blue-green light.

**Cone excitations** $E_L, E_M, E_S$: the cone fundamentals of Smith and Pokorny [10] as linear
combinations of the color matching functions,

```math
\begin{aligned}
L &= 0.15514\,X + 0.54312\,Y - 0.03286\,Z,\\
M &= -0.15514\,X + 0.45684\,Y + 0.03286\,Z,\\
S &\propto Z,
\end{aligned}
```

each normalized to a peak of 1, as Wanat and Mantiuk use them [9]. The **rod excitation** $E_R$ is
the sum with $V'(\lambda)$ (peak 1). Smith and Pokorny defined their transformation on the Judd-Vos
corrected functions; the app applies it to CIE 1931 (section [8](#8-simplifications-at-a-glance)).

## 2. Adaptation

The eye adapts to the light around it, over seconds to minutes. Twilight changes slowly, so the
model assumes an eye that is fully adapted at every moment, without a time course.

What does it adapt to? Looking at the sky, the field of view is the sky itself. The model takes
the **mean luminance of the whole sky** above the horizon, separately for cones and rods:

```math
L_{wa} = \frac{\sum_e \bar L(e)\cos e}{\sum_e \cos e},\qquad e = 3.75°, 11.25°, \dots, 86.25° ,
```

where $\bar L(e)$ is the luminance at elevation $e$, averaged over all azimuths like the ring
(PHYSICS.md, section 10.5), and $\cos e$ weights the elevations by solid angle. With the Sun's own
disc left out, this is the sky's luminance as the eye sees it, from about 6800 cd/m² (Sun at 40°)
to about 2·10⁻⁴ cd/m² at night (section [7](#7-what-comes-out)).

## 3. Brightness

### 3.1 Stevens' law and the revised operator of Tumblin and Rushmeier

How bright a light appears grows with its luminance as a power law whose exponent depends on the
adaptation (Stevens and Stevens [5]). Tumblin and Rushmeier [3] built on it an operator that keeps
the impression of brightness between the real scene and the display; in its revised form
(Tumblin, Hodgins and Guenter 1999 [4]) a scene approaching darkness appears dark, not as the
middle gray of the first version [4; 16]. With the luminance $L$ of a point of the sky, the
adaptation luminance $L_{wa}$ (section [2](#2-adaptation)) and that of the eye at the display
$L_{da}$:

```math
L_d = L_{da}\; m\,\left(\frac{L}{L_{wa}}\right)^{\gamma(L_{wa})/\gamma(L_{da})},\qquad
m = C_\mathrm{max}^{\,(\gamma(L_{wa})/\gamma(L_{da}) - 1)/2},
```

```math
\gamma(L) = \begin{cases}2.655 & L > 100\ \mathrm{cd/m^2}\\ 1.855 + 0.4\,\log_{10}(L + 2.3\cdot10^{-5}) & \text{otherwise.}\end{cases}
```

$\gamma$ is given so in the survey of Reinhard and Devlin [6]; the whole operator follows the
implementation in the HDR Toolbox [7] (which takes the natural logarithm in one place where the
app, like $\gamma$ itself, uses $\log_{10}$). Display parameters:

| Quantity | Value |
|---|---|
| Maximum display luminance $L_{d,\max}$ | 100 cd/m² |
| Adaptation of the eye at the display $L_{da}$ | 40 cd/m², a little below half the maximum, which Ward [2] and Ferwerda et al. [1] take (the toolbox's default is 20) |
| Display contrast $C_\mathrm{max}$ | 1000, as of a common LCD panel (the toolbox's default is 100) |

The operator works on the photopic luminance. Its key property here: the displayed brightness of
the adaptation level, $L_{da}\,m$, falls monotonically as $L_{wa}$ falls, from $1.2\,L_{da}$ in
daylight to $0.03\,L_{da}$ in darkness ($\gamma \to 0$, $m \to C_\mathrm{max}^{-1/2}$). The two
display parameters set the overall look: $L_{da}$ how bright the day appears, $C_\mathrm{max}$ how
far below it the night lies; with the toolbox's defaults the blue of the high sky came out dark and
the night too light. The ring
therefore darkens steadily from day through twilight into night (a test checks this from 2° below
the horizon on), and the night is dark but not black.

### 3.2 Why not a threshold model

An earlier version of this model used the operator of Ferwerda, Pattanaik, Shirley and Greenberg
[1], which matches **thresholds**: a contrast just visible in the real scene is just visible on the
display (after Ward [2]). It has separate threshold curves for cones and rods and blends the rods
in across the mesopic range (0.01 to 10 cd/m²). For the ring this went wrong: between about 0.04
and 10 cd/m² the rods are fully adapted (Weber's law), so their contribution appears on the
display almost as bright as daylight, while their weight grows as it gets darker. The upper sky
became darker until the Sun was about 10° below the horizon and then brighter again until −13°
(by a factor of almost 3). The authors note the limitation themselves: a contrast-based operator
predicts visibility at threshold, but "may not account well for suprathreshold appearance" [1].
The ring is about appearance, so brightness is matched instead (section 3.1).

## 4. Color in dim light

### 4.1 The Purkinje shift: rods in the cones' channels

As light fades, colors shift: reds darken, blues and greens appear brighter and bluer. Cao,
Pokorny, Smith and Zele [8] measured why: the rods' signal enters the same neural pathways as the
cones' signals, adding to the L, M and S channels, in dim light most strongly to the S channel.
Wanat and Mantiuk [9] give the weights as a table:

| Luminance | 10 cd/m² | 0.62 cd/m² | 0.1 cd/m² |
|---|---|---|---|
| $k_1$ (to L and M) | 0 | 0.0173 | 0.173 |
| $k_2$ (to S) | 0 | 0.0101 | 0.357 |

The app interpolates linearly in between, as Wanat and Mantiuk do, and holds the values below
0.1 cd/m², where there are no measurements. The luminance is the adaptation level $L_{wa}$. The
color seen is then

```math
\begin{pmatrix}L\\M\\S\end{pmatrix}_\mathrm{seen} =
\begin{pmatrix}E_L\\E_M\\E_S\end{pmatrix} + E_R\begin{pmatrix}k_1\\k_1\\k_2\end{pmatrix},
```

turned back into linear sRGB through the inverse of the cone transformation (section 1) and the
white balance of PHYSICS.md, section 10.2. Its hue goes into the display color; its brightness
comes from section 3.

### 4.2 Fading colors

The cones lose sensitivity in dim light, and colors fade. Wanat and Mantiuk [9] measured how much
saturation must be removed to match the appearance of a scene at lower luminance, and fit

```math
s(Y) = \frac{Y}{Y + 0.108},\qquad
C_d = \left(\frac{C}{Y}\right)^{s(L_{wa})/s(L_{da})} L_d
```

per channel $C$ of the color seen (section 4.1), $Y$ its luminance and $L_d$ the brightness of
section 3. Their experiment reached down to about 0.1 cd/m²; below, the app holds $s$ at its
value there, like the weights of section 4.1. Without this, a dim warm horizon would be shown in
saturated orange at the brightness the rods give it.

### 4.3 Not used: a blue tint for the night

Night scenes in paintings and films are often blue. Durand and Dorsey [12] add a blue shift to
dark scenes and give that tradition as its motivation; Thompson, Shirley and Ferwerda [17] describe
it as a blue about 0.03 below the white point in chromaticity, and Kirk and O'Brien [13] criticize
blending in a single blue color. The app does not use it: the hue of dim light comes only from the
measured rod contribution of section 4.1.

## 5. The night sky

Without the Sun, the sky still glows: airglow of the upper atmosphere, starlight and zodiacal
light. The model adds a constant **174 µcd/m²** (22.0 mag/arcsec²), the natural sky brightness
that Falchi et al. [15] assume for the background away from the Milky Way at low solar activity,
everywhere in the sky and with the Sun's spectrum (section [8](#8-simplifications-at-a-glance)).
It is what the eye adapts to once twilight is over, so the night color follows from the model:
a dark blue-gray. Light pollution is not included; over a city like Berlin the night sky is 10 to
50 times brighter.

## 6. Onto the display

1. **Brightness** $L_d$ (section 3), **hue and saturation** (section 4), divided by $L_{d,\max}$.
2. **Highlights** are compressed with the ACES filmic curve in Narkowicz' fit [14]
   (PHYSICS.md, section 10.4) instead of being clipped: clipping turns the bright hazy horizon
   into a hard, slightly cyan band.
3. **Gamma** $f^{1/2.2}$ and 8 bits per channel.

## 7. What comes out

The whole sky's mean luminance (section 2) and the ring's displayed colors for a ring up to 45°
(horizon / 15° / 30° / 80° above it), with the aerosol of PHYSICS.md, section 9.6:

| Sun | $L_{wa}$ photopic | scotopic | Ring (0° / 15° / 30° / 80°) |
|---|---|---|---|
| 40° | 6800 cd/m² | 19 000 | `#dfdeda` `#c8d8e3` `#b2c9dc` `#87a5c4` |
| 10° | 3200 | 8200 | `#e6ddc3` `#d6dedf` `#b3c6d2` `#6986a8` |
| 0° | 310 | 830 | `#8f9198` `#dddee0` `#c5d0dd` `#8aa4c5` |
| −6° | 2.1 | 6.6 | `#555f74` `#95abc4` `#6a8cb9` `#43649e` |
| −10° | 0.029 | 0.084 | `#323033` `#49536a` `#34405b` `#2a344f` |
| −18° | 2.0·10⁻⁴ | 5.1·10⁻⁴ | `#212126` `#212229` `#212229` `#212228` |

- **Day:** blue, paler toward the hazy horizon.
- **Sunset:** the horizon itself darkens, a brighter band lies above it: looking flat, one sees
  only the haze close by, which the low Sun hardly reaches any more (PHYSICS.md, section 9.6).
- **Blue hour** (Sun around −6°): a clear blue.
- **Late twilight:** slate blue, as the rods take over and add their blue.
- **Night:** the night sky's glow, dark blue-gray.

## 8. Simplifications at a glance

| Simplification | Effect | Section |
|---|---|---|
| Eye always fully adapted, no time course | correct for slow twilight, not for a sudden change | [2](#2-adaptation) |
| Adaptation to the whole sky's mean, without the Sun's disc and the ground | a dark landscape below the horizon would lower it somewhat | [2](#2-adaptation) |
| Display: 100 cd/m², eye adapted to 40 cd/m², contrast 1000 | the overall brightness of day and night on the widget depends on these | [3.1](#31-stevens-law-and-the-revised-operator-of-tumblin-and-rushmeier) |
| Rod weights and saturation held below 0.1 cd/m² | no measurements there; the night's blue tint rests on the value at 0.1 | [4.1](#41-the-purkinje-shift-rods-in-the-cones-channels), [4.2](#42-fading-colors) |
| Cone fundamentals from CIE 1931 instead of Judd-Vos | small differences in the violet and blue | [1](#1-measures-of-light-for-the-eye) |
| Night sky uniform, with the Sun's spectrum, no light pollution | the night color of a natural, moonless sky | [5](#5-the-night-sky) |
| ACES curve for highlights | aesthetic compression of the brightest parts | [6](#6-onto-the-display) |

---

## 9. Sources

1. J. A. Ferwerda, S. N. Pattanaik, P. Shirley, D. P. Greenberg: A Model of Visual Adaptation for
   Realistic Image Synthesis. *Proc. SIGGRAPH 96*, 249–258 (1996).
   <https://doi.org/10.1145/237170.237262>
2. G. Ward: A contrast-based scalefactor for luminance display. In P. Heckbert (ed.): *Graphics
   Gems IV*, Academic Press 1994, 415–421.
3. J. Tumblin, H. Rushmeier: Tone Reproduction for Realistic Images. *IEEE Computer Graphics and
   Applications* 13 (6), 42–48 (1993).
4. J. Tumblin, J. K. Hodgins, B. K. Guenter: Two Methods for Display of High Contrast Images.
   *ACM Transactions on Graphics* 18 (1), 56–94 (1999). <https://doi.org/10.1145/300776.300783>
5. S. S. Stevens, J. C. Stevens: brightness as a power function of luminance depending on
   adaptation (1960), as used by Tumblin and Rushmeier [3].
6. E. Reinhard, K. Devlin: Image Display Algorithms for High and Low Dynamic Range Display
   Devices (survey; $\gamma(L)$ of the revised Tumblin-Rushmeier operator).
7. F. Banterle et al.: *HDR Toolbox*, `TumblinTMO.m`, `StevensCSF.m`.
   <https://github.com/banterle/HDR_Toolbox>
8. D. Cao, J. Pokorny, V. C. Smith, A. J. Zele: Rod contributions to color perception: linear with
   rod contrast. *Vision Research* 48 (26), 2586–2592 (2008).
9. R. Wanat, R. K. Mantiuk: Simulating and compensating changes in appearance between day and night
   vision. *ACM Transactions on Graphics* 33 (4) (2014), Proc. SIGGRAPH.
   <https://www.cl.cam.ac.uk/~rkm38/pdfs/wanat14lum_retargeting.pdf>
10. V. C. Smith, J. Pokorny: Spectral sensitivity of the foveal cone photopigments between 400 and
    500 nm. *Vision Research* 15 (2), 161–171 (1975).
11. CIE 1951 scotopic luminous efficiency function $V'(\lambda)$, as tabulated in: HyperPhysics,
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
16. G. Spielmann: *A New Efficient Real-Time Global Tone Mapping Method*, diploma thesis, TU Wien
    (2006), section 4.1 (on the weaknesses of the 1993 operator).
17. W. B. Thompson, P. Shirley, J. A. Ferwerda: A Spatial Post-Processing Algorithm for Images of
    Night Scenes. *Journal of Graphics Tools* 7 (1), 1–12 (2002).

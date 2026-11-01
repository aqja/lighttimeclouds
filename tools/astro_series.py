# /// script
# requires-python = ">=3.12"
# dependencies = ["numpy>=2"]
# ///
"""Truncates the lunar theory ELP 2000-82B and the planetary theory VSOP87D (Earth) for the app.

Downloads both from the IMCCE (the institute where they were developed) into a cache outside the
repository, evaluates the complete series exactly as the reference programs do and checks them
against the check values published with them, then keeps every term above an amplitude threshold
chosen so that the truncation stays within the error budget from 2000 to 2050. The result is written
as Kotlin source to :core, so the app builds without the raw data or network access.

Run from the repository root:  uv run tools/astro_series.py
"""

from __future__ import annotations

import math
import os
import urllib.request
from dataclasses import dataclass
from functools import partial
from pathlib import Path

import numpy as np

ELP_URL = "https://ftp.imcce.fr/pub/ephem/moon/elp82b/"
VSOP_URL = "https://ftp.imcce.fr/pub/ephem/planets/vsop87/"
OUTPUT = Path("core/src/main/kotlin/com/umfluten/lighttimeclouds/astro/AstroSeries.kt")

# Truncation budget, as the largest error over 2000-2050 that the dropped terms may cause.
MOON_LONGITUDE_ARCSEC = 4.0
MOON_LATITUDE_ARCSEC = 2.0
MOON_DISTANCE_KM = 5.0
EARTH_LONGITUDE_ARCSEC = 2.0
EARTH_LATITUDE_ARCSEC = 0.5
EARTH_DISTANCE_AU = 1e-5

YEARS = (2000.0, 2050.0)
SAMPLES = 4000
MAX_TERMS = 3000

ARCSEC = math.pi / 648000.0
DEG = math.pi / 180.0


def cache_dir() -> Path:
    base = Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache"))
    path = base / "lighttimeclouds" / "imcce"
    path.mkdir(parents=True, exist_ok=True)
    return path


def fetch(url: str, name: str) -> list[str]:
    path = cache_dir() / name
    if not path.exists():
        print(f"downloading {url}{name}")
        with urllib.request.urlopen(url + name, timeout=120) as response:
            path.write_bytes(response.read())
    return path.read_text(encoding="ascii").splitlines()


# --- ELP 2000-82B -------------------------------------------------------------------------------
# Constants as in the notice "Lunar solution ELP 2000-82B" (Chapront-Touze & Chapront, IMCCE) and
# its Fortran routine ELP82B; angles in radians, polynomials in Julian centuries t from J2000 (TDB).


def dms(d: float, m: float, s: float) -> float:
    return (d + m / 60.0 + s / 3600.0) * DEG


W1 = [dms(218, 18, 59.95571), 1732559343.73604 * ARCSEC, -5.8883 * ARCSEC, 0.6604e-2 * ARCSEC, -0.3169e-4 * ARCSEC]
W2 = [dms(83, 21, 11.67475), 14643420.2632 * ARCSEC, -38.2776 * ARCSEC, -0.45047e-1 * ARCSEC, 0.21301e-3 * ARCSEC]
W3 = [dms(125, 2, 40.39816), -6967919.3622 * ARCSEC, 6.3622 * ARCSEC, 0.7625e-2 * ARCSEC, -0.3586e-4 * ARCSEC]
EARTH = [dms(100, 27, 59.22059), 129597742.2758 * ARCSEC, -0.0202 * ARCSEC, 0.9e-5 * ARCSEC, 0.15e-6 * ARCSEC]
PERIHELION = [dms(102, 56, 14.42753), 1161.2283 * ARCSEC, 0.5327 * ARCSEC, -0.138e-3 * ARCSEC, 0.0]
PRECESSION = 5029.0966 * ARCSEC
PLANETS = [  # Mercury .. Neptune: mean longitude at J2000 and rate per century
    (dms(252, 15, 3.25986), 538101628.68898 * ARCSEC),
    (dms(181, 58, 47.28305), 210664136.43355 * ARCSEC),
    (EARTH[0], EARTH[1]),
    (dms(355, 25, 59.78866), 68905077.59284 * ARCSEC),
    (dms(34, 21, 5.34212), 10925660.42861 * ARCSEC),
    (dms(50, 4, 38.89694), 4399609.65932 * ARCSEC),
    (dms(314, 3, 18.01841), 1542481.19393 * ARCSEC),
    (dms(304, 20, 55.19575), 786550.32074 * ARCSEC),
]
ATH = 384747.9806743165
A0 = 384747.9806448954
AM = 0.074801329518
ALFA = 0.002571881335
DTASM = 2.0 * ALFA / (3.0 * AM)
# Corrections of the constants (fit to DE200/LE200).
DELNU = 0.55604 * ARCSEC / W1[1]
DELE = 0.01789 * ARCSEC
DELG = -0.08066 * ARCSEC
DELNP = -0.06424 * ARCSEC / W1[1]
DELEP = -0.12879 * ARCSEC

# Delaunay arguments D, l', l, F (with D + 180 deg as in the routine), polynomials of degree 4.
DELAUNAY = [
    [W1[k] - EARTH[k] + (math.pi if k == 0 else 0.0) for k in range(5)],
    [EARTH[k] - PERIHELION[k] for k in range(5)],
    [W1[k] - W2[k] for k in range(5)],
    [W1[k] - W3[k] for k in range(5)],
]
ZETA = [W1[0], W1[1] + PRECESSION, 0.0, 0.0, 0.0]

# The compact form used by the app: every term is A * t^power * sin(sum(m_i * arg_i) + phase) with
# these 13 arguments, all as polynomials (the perturbation series use only their linear part; the
# difference is included in the measured error).
ARGUMENT_NAMES = ["D", "l'", "l", "F", "zeta", "Me", "V", "T", "Ma", "J", "S", "U", "N"]
ARGUMENTS = DELAUNAY + [ZETA] + [[lon, rate, 0.0, 0.0, 0.0] for lon, rate in PLANETS]


@dataclass
class Term:
    coordinate: int  # 0 longitude ("), 1 latitude ("), 2 distance (km)
    power: int
    amplitude: float
    phase: float
    multipliers: list[int]  # over ARGUMENTS
    exact: tuple  # data to evaluate the term exactly as the reference routine


def fixed(line: str, widths: list[int]) -> list[str]:
    fields, position = [], 0
    for width in widths:
        fields.append(line[position : position + width])
        position += width
    return fields


def load_elp() -> list[Term]:
    terms: list[Term] = []
    for number in range(1, 37):
        lines = fetch(ELP_URL, f"ELP{number}")[1:]
        coordinate = (number - 1) % 3
        for line in lines:
            if not line.strip():
                continue
            if number <= 3:
                f = fixed(line, [3, 3, 3, 3, 2, 13] + [12] * 6)
                ilu = [int(x) for x in f[0:4]]
                coef = [float(f[5])] + [float(x) for x in f[6:12]]
                tgv = coef[1] + DTASM * coef[5]
                a = coef[0]
                if number == 3:
                    a = a - 2.0 * a * DELNU / 3.0
                amplitude = a + tgv * (DELNP - AM * DELNU) + coef[2] * DELG + coef[3] * DELE + coef[4] * DELEP
                if coordinate == 2:
                    amplitude *= A0 / ATH
                phase = math.pi / 2 if coordinate == 2 else 0.0
                terms.append(Term(coordinate, 0, amplitude, phase, ilu + [0] * 9, ("main", ilu, coordinate)))
            elif 10 <= number <= 21:
                f = fixed(line, [3] * 11 + [1, 9, 1, 9])
                ipla = [int(x) for x in f[0:11]]
                pha, x = float(f[12]), float(f[14])
                power = 1 if (13 <= number <= 15 or 19 <= number <= 21) else 0
                if number < 16:
                    multipliers = [ipla[8], 0, ipla[9], ipla[10], 0] + ipla[0:8]
                else:
                    multipliers = ipla[7:11] + [0] + ipla[0:7] + [0]
                amplitude = x * (A0 / ATH if coordinate == 2 else 1.0)
                terms.append(Term(coordinate, power, amplitude, pha * DEG, multipliers, ("linear", multipliers, pha * DEG)))
            else:
                f = fixed(line, [3] * 5 + [1, 9, 1, 9])
                iz, ilu = int(f[0]), [int(x) for x in f[1:5]]
                pha, x = float(f[6]), float(f[8])
                power = 1 if (7 <= number <= 9 or 25 <= number <= 27) else 2 if number >= 34 else 0
                multipliers = ilu + [iz] + [0] * 8
                amplitude = x * (A0 / ATH if coordinate == 2 else 1.0)
                terms.append(Term(coordinate, power, amplitude, pha * DEG, multipliers, ("linear", multipliers, pha * DEG)))
    return terms


def poly(coefficients: list[float], t: np.ndarray, degree: int = 4) -> np.ndarray:
    value = np.zeros_like(t)
    for k in range(degree, -1, -1):
        value = value * t + coefficients[k]
    return value


def argument_values(t: np.ndarray, degree: int) -> np.ndarray:
    return np.array([poly(c, t, degree) for c in ARGUMENTS])


def term_value(term: Term, args: np.ndarray, t: np.ndarray) -> np.ndarray:
    angle = np.tensordot(np.array(term.multipliers, dtype=float), args, axes=1) + term.phase
    return term.amplitude * t**term.power * np.sin(angle)


def elp_exact(terms: list[Term], t: np.ndarray) -> np.ndarray:
    """Longitude V (rad, ecliptic of date, departure point of J2000), latitude U (rad), distance (km)."""
    full, linear = argument_values(t, 4), argument_values(t, 1)
    sums = np.zeros((3, t.size))
    for term in terms:
        args = full if term.exact[0] == "main" else linear
        sums[term.coordinate] += term_value(term, args, t)
    v = sums[0] * ARCSEC + poly(W1, t)
    return np.array([v, sums[1] * ARCSEC, sums[2]])


def elp_compact(terms: list[Term], t: np.ndarray) -> np.ndarray:
    args = argument_values(t, 4)
    sums = np.zeros((3, t.size))
    for term in terms:
        sums[term.coordinate] += term_value(term, args, t)
    return np.array([sums[0] * ARCSEC + poly(W1, t), sums[1] * ARCSEC, sums[2]])


def check_elp(terms: list[Term]) -> None:
    """Table H of the notice: rectangular coordinates, inertial mean ecliptic and equinox J2000."""
    table_h = {
        2469000.5: (-361602.98536, 44996.99510, -30696.65316),
        2449000.5: (-363132.34248, 35863.65378, -33196.00409),
        2429000.5: (-371577.58161, 75271.14315, -32227.94618),
        2409000.5: (-373896.15893, 127406.79129, -30037.79225),
        2389000.5: (-346331.77361, 206365.40364, -28502.11732),
    }
    jd = np.array(list(table_h))
    t = (jd - 2451545.0) / 36525.0
    v, u, r = elp_exact(terms, t)
    x1, x2, x3 = r * np.cos(u) * np.cos(v), r * np.cos(u) * np.sin(v), r * np.sin(u)
    p = (0.10180391e-4 + (0.47020439e-6 + (-0.5417367e-9 + (-0.2507948e-11 + 0.463486e-14 * t) * t) * t) * t) * t
    q = (-0.113469002e-3 + (0.12372674e-6 + (0.1265417e-8 + (-0.1371808e-11 - 0.320334e-14 * t) * t) * t) * t) * t
    ra = 2.0 * np.sqrt(1 - p * p - q * q)
    x = (1 - 2 * p * p) * x1 + 2 * p * q * x2 + p * ra * x3
    y = 2 * p * q * x1 + (1 - 2 * q * q) * x2 - q * ra * x3
    z = -p * ra * x1 + q * ra * x2 + (1 - 2 * p * p - 2 * q * q) * x3
    worst = max(max(abs(a - b) for a, b in zip(row, expected)) for row, expected in zip(np.array([x, y, z]).T, table_h.values()))
    print(f"ELP 2000-82B against Table H: largest deviation {worst * 1000:.2f} m")
    assert worst < 0.002, "ELP evaluation does not reproduce the published check values"


# --- VSOP87D Earth ------------------------------------------------------------------------------


@dataclass
class VsopTerm:
    coordinate: int  # 0 L (rad), 1 B (rad), 2 R (au)
    power: int
    a: float
    b: float
    c: float


def load_vsop() -> list[VsopTerm]:
    terms = []
    for line in fetch(VSOP_URL, "VSOP87D.ear"):
        if line.startswith(" VSOP87"):
            continue
        coordinate, power = int(line[3]) - 1, int(line[4])
        a, b, c = float(line[79:97]), float(line[97:111]), float(line[111:131])
        terms.append(VsopTerm(coordinate, power, a, b, c))
    return terms


def vsop(terms: list[VsopTerm], tau: np.ndarray) -> np.ndarray:
    sums = np.zeros((3, tau.size))
    for term in terms:
        sums[term.coordinate] += term.a * tau**term.power * np.cos(term.b + term.c * tau)
    return sums


def check_vsop(terms: list[VsopTerm]) -> None:
    """vsop87.chk, version D, Earth."""
    checks = {
        2451545.0: (1.7519238681, -0.0000039656, 0.9833276819),
        2415020.0: (1.7391225563, -0.0000005679, 0.9832689778),
        2378495.0: (1.7262638916, 0.0000002083, 0.9832274321),
    }
    tau = (np.array(list(checks)) - 2451545.0) / 365250.0
    lbr = vsop(terms, tau)
    lbr[0] = np.mod(lbr[0], 2 * math.pi)
    worst = np.max(np.abs(lbr.T - np.array(list(checks.values()))))
    print(f"VSOP87D Earth against vsop87.chk: largest deviation {worst:.1e}")
    assert worst < 2e-10, "VSOP87 evaluation does not reproduce the published check values"


# --- Truncation ---------------------------------------------------------------------------------


def choose(impacts: np.ndarray, contributions, reference: np.ndarray, limit: float, scale: float) -> tuple[int, np.ndarray, float]:
    """Fewest terms, taken by decreasing impact, from which on the compact sum stays within limit."""
    order = np.argsort(-impacts)
    running = np.zeros_like(reference)
    errors = []
    for index in order[:MAX_TERMS]:
        running += contributions(index)
        errors.append(float(np.max(np.abs(reference - running)) * scale))
    if errors[-1] > limit:
        raise SystemExit(f"{MAX_TERMS} terms do not reach {limit}")
    best = next(count for count in range(len(errors), 0, -1) if count == 1 or errors[count - 2] > limit)
    return best, order[:best], errors[best - 1]


def scaled_term(terms: list[Term], args: np.ndarray, t: np.ndarray, factor: float, index: int) -> np.ndarray:
    return term_value(terms[index], args, t) * factor


def vsop_term(terms: list[VsopTerm], tau: np.ndarray, index: int) -> np.ndarray:
    term = terms[index]
    return term.a * tau**term.power * np.cos(term.b + term.c * tau)


def main() -> None:
    rng = np.random.default_rng(1)
    years = rng.uniform(*YEARS, SAMPLES)
    t = (years - 2000.0) / 100.0
    t_max = max(abs(t))

    elp = load_elp()
    check_elp(elp)
    exact = elp_exact(elp, t)
    exact[0] -= poly(W1, t)
    args = argument_values(t, 4)
    selected_elp: list[Term] = []
    elp_report = []
    for coordinate, (limit, scale, unit) in enumerate(
        [(MOON_LONGITUDE_ARCSEC, 1 / ARCSEC, '"'), (MOON_LATITUDE_ARCSEC, 1 / ARCSEC, '"'), (MOON_DISTANCE_KM, 1.0, " km")]
    ):
        terms = [term for term in elp if term.coordinate == coordinate]
        impacts = np.array([abs(term.amplitude) * t_max**term.power for term in terms])
        factor = ARCSEC if coordinate < 2 else 1.0
        count, chosen, error = choose(impacts, partial(scaled_term, terms, args, t, factor), exact[coordinate], limit, scale)
        selected_elp += [terms[i] for i in chosen]
        elp_report.append(f"{['longitude', 'latitude', 'distance'][coordinate]} {count} of {len(terms)} terms, up to {error:.2f}{unit}")
        print("ELP", elp_report[-1])

    vsop_terms = load_vsop()
    check_vsop(vsop_terms)
    tau = t / 10.0
    tau_max = t_max / 10.0
    reference = vsop(vsop_terms, tau)
    selected_vsop: list[VsopTerm] = []
    vsop_report = []
    for coordinate, (limit, scale, unit) in enumerate(
        [(EARTH_LONGITUDE_ARCSEC, 1 / ARCSEC, '"'), (EARTH_LATITUDE_ARCSEC, 1 / ARCSEC, '"'), (EARTH_DISTANCE_AU, 1.0, " au")]
    ):
        terms = [term for term in vsop_terms if term.coordinate == coordinate]
        impacts = np.array([abs(term.a) * tau_max**term.power for term in terms])
        count, chosen, error = choose(impacts, partial(vsop_term, terms, tau), reference[coordinate], limit, scale)
        selected_vsop += [terms[i] for i in chosen]
        vsop_report.append(f"{['L', 'B', 'R'][coordinate]} {count} of {len(terms)} terms, up to {error:.2g}{unit}")
        print("VSOP", vsop_report[-1])

    OUTPUT.write_text(kotlin(selected_elp, selected_vsop, elp_report, vsop_report), encoding="utf-8")
    print(f"wrote {OUTPUT}")


def number(value: float) -> str:
    text = repr(float(value))
    return text if ("e" in text or "." in text) else text + ".0"


def kotlin(elp: list[Term], vsop_terms: list[VsopTerm], elp_report: list[str], vsop_report: list[str]) -> str:
    elp = sorted(elp, key=lambda term: (term.coordinate, term.power, -abs(term.amplitude)))
    vsop_terms = sorted(vsop_terms, key=lambda term: (term.coordinate, term.power, -abs(term.a)))

    def rows(values: list[str], per_line: int) -> str:
        return "\n".join("        " + ", ".join(values[i : i + per_line]) + "," for i in range(0, len(values), per_line))

    arguments = rows([number(c) for coefficients in ARGUMENTS for c in coefficients], 5)
    w1 = ", ".join(number(c) for c in W1)
    lines = [
        "// Generated by tools/astro_series.py - do not edit by hand.",
        "//",
        "// Truncated from the lunar theory ELP 2000-82B (M. Chapront-Touze, J. Chapront: A&A 124, 50 (1983);",
        "// A&A 190, 342 (1988)) and the planetary theory VSOP87, version D, Earth (P. Bretagnon, G. Francou:",
        "// A&A 202, 309 (1988)), both as distributed by the IMCCE, Paris Observatory. Truncation errors from",
        f"// {YEARS[0]:.0f} to {YEARS[1]:.0f}, measured against the complete series:",
        *(f"//   ELP {line}" for line in elp_report),
        *(f"//   VSOP87D Earth {line}" for line in vsop_report),
        "",
        "package com.umfluten.lighttimeclouds.astro",
        "",
        "internal object AstroSeries {",
        "    /** ELP arguments "
        + ", ".join(ARGUMENT_NAMES)
        + ": polynomial coefficients (rad, per Julian century from J2000) up to t^4, five per argument. */",
        "    val ELP_ARGUMENTS = doubleArrayOf(",
        arguments,
        "    )",
        "",
        "    /** Mean longitude W1 of the Moon, departure point of J2000 (rad), polynomial in Julian centuries. */",
        f"    val ELP_W1 = doubleArrayOf({w1})",
        "",
        '    /** ELP terms: coordinate (0 longitude ", 1 latitude ", 2 distance km) and power of t. */',
        "    val ELP_COORDINATE = intArrayOf(" + ", ".join(str(term.coordinate) for term in elp) + ")",
        "    val ELP_POWER = intArrayOf(" + ", ".join(str(term.power) for term in elp) + ")",
        "    val ELP_AMPLITUDE = doubleArrayOf(",
        rows([number(term.amplitude) for term in elp], 6),
        "    )",
        "    val ELP_PHASE = doubleArrayOf(",
        rows([number(term.phase) for term in elp], 6),
        "    )",
        f"    /** Multipliers of the {len(ARGUMENTS)} arguments, one row per term. */",
        "    val ELP_MULTIPLIERS = intArrayOf(",
        rows([", ".join(str(m) for m in term.multipliers) for term in elp], 1),
        "    )",
        "",
        "    /** VSOP87D Earth terms A * tau^power * cos(B + C * tau), tau in Julian millennia from J2000; coordinate 0 L, 1 B (rad), 2 R (au). */",
        "    val VSOP_COORDINATE = intArrayOf(" + ", ".join(str(term.coordinate) for term in vsop_terms) + ")",
        "    val VSOP_POWER = intArrayOf(" + ", ".join(str(term.power) for term in vsop_terms) + ")",
        "    val VSOP_A = doubleArrayOf(",
        rows([number(term.a) for term in vsop_terms], 6),
        "    )",
        "    val VSOP_B = doubleArrayOf(",
        rows([number(term.b) for term in vsop_terms], 6),
        "    )",
        "    val VSOP_C = doubleArrayOf(",
        rows([number(term.c) for term in vsop_terms], 6),
        "    )",
        "}",
        "",
    ]
    return "\n".join(lines)


if __name__ == "__main__":
    main()

# /// script
# requires-python = ">=3.12"
# dependencies = ["skyfield>=1.49", "numpy>=2"]
# ///
"""Reference values for the astronomy tests, from JPL's DE440s ephemeris via Skyfield.

The ephemeris (about 32 MB) is downloaded into a cache outside the repository; only the computed
values are written, as CSV files to core/src/test/resources/astro/. Definitions match the app:
altitudes are geometric (no refraction) of the body's center as seen from the place on the WGS84
ellipsoid, azimuths count from south, positive toward west. Sunrise/sunset and twilights are the
center crossing the given altitude; moonrise/moonset is the upper limb at the horizon with 34' of
refraction, i.e. the center at -34' minus the Moon's apparent radius. Eclipses follow the NASA
eclipse canons: the Earth's shadow enlarged after Danjon, the Moon's mean radius (k = 0.2725076)
for lunar eclipses and the outer contacts of a solar eclipse, that of its limb's valleys
(k = 0.2722810) for the inner ones, and the Sun's radius of IAU 1976 (696 000 km).

Values that depend on the Earth's rotation stop at the end of 2025: the app takes UT1 = UTC, while
for later years Skyfield extrapolates UT1 from a prediction of Delta T that UTC need not follow.

Run from the repository root:  uv run tools/reference_ephemeris.py
"""

from __future__ import annotations

import csv
import os
from datetime import UTC, datetime, timedelta
from pathlib import Path

import numpy as np
from skyfield import almanac, eclipselib, searchlib
from skyfield.api import Loader, wgs84
from skyfield.framelib import ecliptic_frame

OUTPUT = Path("core/src/test/resources/astro")

# Big capitals, plus Longyearbyen for polar day and night.
PLACES = {
    "berlin": (52.5208, 13.4094),
    "quito": (-0.2202, -78.5123),
    "canberra": (-35.2809, 149.1300),
    "longyearbyen": (78.2232, 15.6267),
}
SUN_ALTITUDES = [-0.833, -0.3, 6.0, -6.0, -12.0, -18.0]
EARTH_RADIUS_KM = 6378.137
MOON_RADIUS_RATIO = 0.2725
REFRACTION_AT_HORIZON_DEG = 34.0 / 60.0

ROTATION_YEARS = (2000, 2025)
POSITION_YEARS = (2000, 2050)

# Solar eclipses as seen from capitals that saw each kind: annular in Madrid (2005), total in
# Lusaka (2001), partial ones in all of them.
ECLIPSE_PLACES = {
    "berlin": PLACES["berlin"],
    "canberra": PLACES["canberra"],
    "madrid": (40.4168, -3.7038),
    "lusaka": (-15.4167, 28.2833),
}
SUN_RADIUS_KM = 696000.0
# The Moon's radius in Earth radii: its mean limb (lunar eclipses, the outer contacts of a solar
# eclipse) and the valleys of its limb (the inner contacts), as in the NASA eclipse canons.
MOON_K_OUTER = 0.2725076
MOON_K_INNER = 0.2722810
# Danjon's enlargement of the Earth's shadow by its atmosphere, applied to the Moon's parallax.
SHADOW_ENLARGEMENT = 1.01
HOUR = 1.0 / 24.0


def loader() -> Loader:
    base = Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache"))
    return Loader(str(base / "lighttimeclouds" / "skyfield"))


def iso(t) -> str:
    return t.utc_datetime().strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def random_times(ts, rng, years: tuple[int, int], count: int):
    start = datetime(years[0], 1, 1, tzinfo=UTC)
    span = (datetime(years[1] + 1, 1, 1, tzinfo=UTC) - start).total_seconds()
    seconds = np.sort(rng.uniform(0, span, count)).round()
    return [ts.from_datetime(start + timedelta(seconds=float(s))) for s in seconds]


def moon_horizon_deg(distance_km):
    return -REFRACTION_AT_HORIZON_DEG - np.degrees(np.arcsin(MOON_RADIUS_RATIO * EARTH_RADIUS_KM / distance_km))


class AboveHorizon:
    """Whether a body's center is above an altitude, for Skyfield's find_discrete (which reads step_days)."""

    def __init__(self, earth, observer, body, altitude_deg=None, step_days: float = 0.04):
        self.earth, self.observer, self.body = earth, observer, body
        self.altitude_deg = altitude_deg
        self.step_days = step_days

    def __call__(self, t):
        altitude = self.observer.at(t).observe(self.body).apparent().altaz()[0].degrees
        if self.altitude_deg is not None:
            return altitude >= self.altitude_deg
        # Moonrise and moonset: the horizon depends on the Moon's distance.
        return altitude >= moon_horizon_deg((self.body.at(t) - self.earth.at(t)).distance().km)


def main() -> None:
    load = loader()
    eph = load("de440s.bsp")
    ts = load.timescale()
    earth, sun, moon = eph["earth"], eph["sun"], eph["moon"]
    rng = np.random.default_rng(2)
    OUTPUT.mkdir(parents=True, exist_ok=True)

    # Geocentric apparent ecliptic coordinates of date, with the geometric distance (the length of
    # the apparent vector also contains the Earth's motion during the light time, up to 40 km).
    with open(OUTPUT / "geocentric.csv", "w", newline="") as f:
        out = csv.writer(f)
        out.writerow(["utc", "body", "longitude_deg", "latitude_deg", "distance_km"])
        for t in random_times(ts, rng, POSITION_YEARS, 300):
            for name, body in (("sun", sun), ("moon", moon)):
                lat, lon, _ = earth.at(t).observe(body).apparent().frame_latlon(ecliptic_frame)
                distance = (body.at(t) - earth.at(t)).distance().km
                out.writerow([iso(t), name, f"{lon.degrees:.7f}", f"{lat.degrees:.7f}", f"{distance:.3f}"])

    # Topocentric altitude and azimuth.
    with open(OUTPUT / "topocentric.csv", "w", newline="") as f:
        out = csv.writer(f)
        out.writerow(["utc", "place", "body", "altitude_deg", "azimuth_deg"])
        for place, (lat, lon) in PLACES.items():
            observer = earth + wgs84.latlon(lat, lon)
            for t in random_times(ts, rng, ROTATION_YEARS, 60):
                for name, body in (("sun", sun), ("moon", moon)):
                    alt, az, _ = observer.at(t).observe(body).apparent().altaz()
                    azimuth = (az.degrees - 180.0 + 180.0) % 360.0 - 180.0
                    out.writerow([iso(t), place, name, f"{alt.degrees:.7f}", f"{azimuth:.7f}"])

    # Rise, set, twilights and transits over one day from random start times.
    with open(OUTPUT / "events.csv", "w", newline="") as f:
        out = csv.writer(f)
        out.writerow(["place", "body", "event", "altitude_deg", "utc"])
        for place, (lat, lon) in PLACES.items():
            topos = wgs84.latlon(lat, lon)
            observer = earth + topos
            for start in random_times(ts, rng, ROTATION_YEARS, 12):
                end = ts.tt_jd(start.tt + 1.0)

                for altitude in SUN_ALTITUDES:
                    for t, up in zip(*almanac.find_discrete(start, end, AboveHorizon(earth, observer, sun, altitude))):
                        out.writerow([place, "sun", "rise" if up else "set", altitude, iso(t)])

                for t, upper in zip(*almanac.find_discrete(start, end, almanac.meridian_transits(eph, sun, topos))):
                    out.writerow([place, "sun", "noon" if upper else "midnight", "", iso(t)])

                for t, up in zip(*almanac.find_discrete(start, end, AboveHorizon(earth, observer, moon, step_days=0.02))):
                    out.writerow([place, "moon", "rise" if up else "set", "", iso(t)])
    write_moon_events(ts, eph)
    print(f"wrote {OUTPUT}/geocentric.csv, topocentric.csv, events.csv, apsides.csv, lunar_eclipses.csv, solar_eclipses.csv")


def bisect(f, a: float, b: float, steps: int = 40) -> float:
    """Where f (of a TT Julian date) changes sign between a and b."""
    fa = f(a)
    for _ in range(steps):
        m = (a + b) / 2
        fm = f(m)
        if (fm < 0) == (fa < 0):
            a, fa = m, fm
        else:
            b = m
    return (a + b) / 2


def golden_minimum(f, a: float, b: float, tolerance: float = 0.01 / 86400) -> float:
    """Where f (of a TT Julian date) is least between a and b, f having a single minimum there."""
    ratio = (5**0.5 - 1) / 2
    c, d = b - ratio * (b - a), a + ratio * (b - a)
    fc, fd = f(c), f(d)
    while b - a > tolerance:
        if fc < fd:
            b, d, fd = d, c, fc
            c = b - ratio * (b - a)
            fc = f(c)
        else:
            a, c, fc = c, d, fd
            d = a + ratio * (b - a)
            fd = f(d)
    return (a + b) / 2


def obscuration(sun_radius, moon_radius, distance) -> float:
    """The part of the Sun's disc the Moon's covers, from their radii and the distance of their centers."""
    r1, r2, d = sun_radius, moon_radius, distance
    if d >= r1 + r2:
        return 0.0
    if d <= abs(r1 - r2):
        return min(r1, r2) ** 2 / r1**2
    a1 = r1**2 * np.arccos((d * d + r1 * r1 - r2 * r2) / (2 * d * r1))
    a2 = r2**2 * np.arccos((d * d + r2 * r2 - r1 * r1) / (2 * d * r2))
    a3 = 0.5 * np.sqrt((-d + r1 + r2) * (d + r1 - r2) * (d - r1 + r2) * (d + r1 + r2))
    return float((a1 + a2 - a3) / (np.pi * r1**2))


def write_moon_events(ts, eph) -> None:
    """Perigees and apogees, lunar eclipses with their contacts, and solar eclipses as seen from a place."""
    earth, sun, moon = eph["earth"], eph["sun"], eph["moon"]
    radius_km = EARTH_RADIUS_KM
    start, end = ts.utc(POSITION_YEARS[0], 1, 1), ts.utc(POSITION_YEARS[1] + 1, 1, 1)

    def distance(t):
        return (moon.at(t) - earth.at(t)).distance().km

    distance.step_days = 1.0
    with open(OUTPUT / "apsides.csv", "w", newline="") as f:
        out = csv.writer(f)
        out.writerow(["utc", "apsis", "distance_km"])
        rows = [(t, "perigee", d) for t, d in zip(*searchlib.find_minima(start, end, distance))]
        rows += [(t, "apogee", d) for t, d in zip(*searchlib.find_maxima(start, end, distance))]
        previous = None
        for t, kind, d in sorted(rows, key=lambda row: row[0].tt):
            # The search can report one extreme twice, a second apart.
            if previous is not None and previous[1] == kind and t.tt - previous[0].tt < 1.0:
                continue
            out.writerow([iso(t), kind, f"{d:.3f}"])
            previous = (t, kind)

    # Lunar eclipses: the Moon's center against the axis of the Earth's shadow, geocentric.
    def lunar(jd: float):
        t = ts.tt_jd(jd)
        e = earth.at(t)
        s = e.observe(sun).apparent()
        m = e.observe(moon).apparent()
        d_s, d_m = s.distance().km, m.distance().km
        separation = np.pi - s.separation_from(m).radians
        parallax = SHADOW_ENLARGEMENT * np.arcsin(radius_km / d_m) + np.arcsin(radius_km / d_s)
        sun_radius = np.arcsin(SUN_RADIUS_KM / d_s)
        moon_radius = np.arcsin(MOON_K_OUTER * radius_km / d_m)
        return separation, parallax + sun_radius, parallax - sun_radius, moon_radius

    with open(OUTPUT / "lunar_eclipses.csv", "w", newline="") as f:
        out = csv.writer(f)
        out.writerow(["greatest", "kind", "umbral_magnitude", "penumbral_magnitude", "p1", "u1", "u2", "u3", "u4", "p4"])
        times, _, _ = eclipselib.lunar_eclipses(start, end, eph)
        for candidate in times:
            greatest = golden_minimum(lambda jd: lunar(jd)[0], candidate.tt - HOUR, candidate.tt + HOUR)
            separation, penumbra, umbra, moon_radius = lunar(greatest)
            if separation >= penumbra + moon_radius:
                continue
            kind = "total" if separation < umbra - moon_radius else "partial" if separation < umbra + moon_radius else "penumbral"

            def contacts(margin, greatest=greatest):
                if margin(greatest) >= 0:
                    return "", ""
                return (iso(ts.tt_jd(bisect(margin, greatest - 5 * HOUR, greatest))),
                        iso(ts.tt_jd(bisect(margin, greatest, greatest + 5 * HOUR))))

            p1, p4 = contacts(lambda jd: (g := lunar(jd))[0] - g[1] - g[3])
            u1, u4 = contacts(lambda jd: (g := lunar(jd))[0] - g[2] - g[3])
            u2, u3 = contacts(lambda jd: (g := lunar(jd))[0] - g[2] + g[3])
            out.writerow([
                iso(ts.tt_jd(greatest)), kind,
                f"{(umbra + moon_radius - separation) / (2 * moon_radius):.5f}",
                f"{(penumbra + moon_radius - separation) / (2 * moon_radius):.5f}",
                p1, u1, u2, u3, u4, p4,
            ])

    # Solar eclipses: the Moon's disc against the Sun's as seen from the place.
    new_moons = [t for t, phase in zip(*almanac.find_discrete(ts.utc(ROTATION_YEARS[0], 1, 1),
                 ts.utc(ROTATION_YEARS[1] + 1, 1, 1), almanac.moon_phases(eph))) if phase == 0]
    with open(OUTPUT / "solar_eclipses.csv", "w", newline="") as f:
        out = csv.writer(f)
        out.writerow(["place", "greatest", "kind", "magnitude", "obscuration", "c1", "c2", "c3", "c4"])
        for place, (lat, lon) in ECLIPSE_PLACES.items():
            observer = earth + wgs84.latlon(lat, lon)

            def solar(jd: float, observer=observer):
                a = observer.at(ts.tt_jd(jd))
                s, m = a.observe(sun).apparent(), a.observe(moon).apparent()
                d_m = m.distance().km
                return (s.separation_from(m).radians, np.arcsin(SUN_RADIUS_KM / s.distance().km),
                        np.arcsin(MOON_K_OUTER * radius_km / d_m), np.arcsin(MOON_K_INNER * radius_km / d_m))

            for new_moon in new_moons:
                grid = new_moon.tt + np.arange(-6 * 60, 6 * 60 + 1, 5) / 1440.0
                margins = [(g := solar(jd))[0] - g[1] - g[2] for jd in grid]
                i = int(np.argmin(margins))
                if margins[i] >= 0 or i in (0, len(grid) - 1):
                    continue
                greatest = golden_minimum(lambda jd: solar(jd)[0], grid[i - 1], grid[i + 1])
                separation, sun_radius, moon_outer, moon_inner = solar(greatest)
                if separation >= sun_radius + moon_outer:
                    continue
                central = separation < abs(moon_inner - sun_radius)
                kind = ("total" if moon_inner > sun_radius else "annular") if central else "partial"
                c1 = bisect(lambda jd: (g := solar(jd))[0] - g[1] - g[2], greatest - 4 * HOUR, greatest)
                c4 = bisect(lambda jd: (g := solar(jd))[0] - g[1] - g[2], greatest, greatest + 4 * HOUR)
                inner = ["", ""]
                if central:
                    inner = [iso(ts.tt_jd(bisect(lambda jd: (g := solar(jd))[0] - abs(g[3] - g[1]), a, b)))
                             for a, b in ((greatest - HOUR, greatest), (greatest, greatest + HOUR))]
                out.writerow([
                    place, iso(ts.tt_jd(greatest)), kind,
                    f"{(sun_radius + moon_outer - separation) / (2 * sun_radius):.5f}",
                    f"{obscuration(sun_radius, moon_outer, separation):.5f}",
                    iso(ts.tt_jd(c1)), inner[0], inner[1], iso(ts.tt_jd(c4)),
                ])


if __name__ == "__main__":
    main()

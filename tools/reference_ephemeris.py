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
refraction, i.e. the center at -34' minus the Moon's apparent radius.

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
from skyfield import almanac
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
    print(f"wrote {OUTPUT}/geocentric.csv, topocentric.csv, events.csv")


if __name__ == "__main__":
    main()

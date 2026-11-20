# /// script
# requires-python = ">=3.12"
# dependencies = ["pillow>=11"]
# ///
"""Takes the six store screenshots per language on an emulator and writes them to fastlane/.

  1  home screen, widget with the default settings
  2  home screen, widget with place name, weather ring and summary
  3–6 the widget's settings: tabs weather, times, settings (one topic open), preview

The scene is Berlin a little before sunset, with a demo status bar. Settings are written straight
into the app's preferences file and the forecast is fetched once and reused for every language, so
all shots show the same weather.

One-time setup of the emulator (kept in its AVD):
  - a launcher with room for a full-width 4x4 widget above the icon row (Lawnchair works well),
    the LightTimeClouds widget placed there and resized to full width, a calm dark wallpaper;
  - a Google-free system image, so `adb root` works.

Run from the repository root with the emulator running:
  ./gradlew :app:assembleRelease
  uv run tools/store_screenshots.py --apk app/build/outputs/apk/release/app-release.apk --fetch

`--fetch` turns the network on once to load a fresh forecast for the scene's date and keeps it in
build/store-screenshots/forecast.json; without it that file is reused, so the scene's date must
then be the date the forecast was fetched on.
"""

from __future__ import annotations

import argparse
import datetime as dt
import io
import re
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from collections.abc import Mapping
from pathlib import Path

from PIL import Image

PACKAGE = "com.umfluten.lighttimeclouds"
PROVIDER = f"{PACKAGE}/.widget.ClockWidgetProvider"
CONFIG = f"{PACKAGE}/.config.ConfigActivity"
DATA = f"/data/data/{PACKAGE}/files"
PREFS = f"{DATA}/datastore/widget_prefs.preferences_pb"
FORECAST = f"{DATA}/weather/default.json"

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "fastlane/metadata/android"
WORK = ROOT / "build/store-screenshots"

# Store locale -> Android resource directory.
LOCALES = {"de-DE": "values-de", "en-US": "values", "es-ES": "values-es", "fr-FR": "values-fr"}

# Berlin, as in all examples; about 25 minutes before sunset in late November.
PLACE = {"name": "Berlin", "lat": 52.521, "lon": 13.409, "zone": "Europe/Berlin"}
SCENE_TIME = dt.time(15, 35)

# Shot 2 changes only these from the defaults.
SHOWCASE: dict[str, object] = {
    "label_position": "ABOVE",
    "show_weather": True,
    "weather_ring_position": "OUTSIDE",
    "temperature_position": "OUTSIDE",
    "summary_position": "BELOW",
}

SETTLE_S = 3.0


class Device:
    def __init__(self, serial: str) -> None:
        self.serial = serial

    def run(self, *args: str, binary: bool = False, check: bool = True) -> str | bytes:
        result = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, check=check)
        return result.stdout if binary else result.stdout.decode(errors="replace").strip()

    def shell(self, command: str, check: bool = True) -> str:
        out = self.run("shell", command, check=check)
        assert isinstance(out, str)
        return out

    def wait_for_boot(self) -> None:
        self.run("wait-for-device")
        while self.shell("getprop sys.boot_completed", check=False) != "1":
            time.sleep(1)
        time.sleep(SETTLE_S)

    def push_file(self, local: Path, remote: str, mode: str) -> None:
        """Puts [local] at [remote] inside the app's data, owned by the app and labeled for it."""
        owner = self.shell(f"stat -c %u {DATA}")
        self.run("push", str(local), "/data/local/tmp/ltc_upload")
        self.shell(
            f"mkdir -p {remote.rsplit('/', 1)[0]} && cp /data/local/tmp/ltc_upload {remote} && "
            f"chown {owner}:{owner} {remote} && chmod {mode} {remote} && restorecon {remote} && "
            "rm /data/local/tmp/ltc_upload"
        )

    def tap(self, x: int, y: int) -> None:
        self.shell(f"input tap {x} {y}")

    def screenshot(self, path: Path) -> None:
        png = self.run("exec-out", "screencap", "-p", binary=True)
        assert isinstance(png, bytes)
        path.parent.mkdir(parents=True, exist_ok=True)
        # Re-encoding drops every metadata chunk.
        Image.open(io.BytesIO(png)).convert("RGB").save(path, optimize=True)

    def nodes(self) -> list[tuple[str, tuple[int, int, int, int]]]:
        """Visible texts on screen with their bounds."""
        self.shell("uiautomator dump /sdcard/ltc_ui.xml")
        xml = self.run("exec-out", "cat", "/sdcard/ltc_ui.xml")
        assert isinstance(xml, str)
        found = []
        for node in ET.fromstring(xml).iter("node"):
            text = node.get("text") or node.get("content-desc") or ""
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if text and m:
                found.append((text, (int(m[1]), int(m[2]), int(m[3]), int(m[4]))))
        return found

    def tap_text(self, text: str, below: int = 0) -> int:
        """Taps the topmost [text] below the y coordinate [below]; returns its bottom edge."""
        for found, (x0, y0, x1, y1) in sorted(self.nodes(), key=lambda n: n[1][1]):
            if found == text and y0 >= below:
                self.tap((x0 + x1) // 2, (y0 + y1) // 2)
                return y1
        sys.exit(f"'{text}' not found on screen")


# ---- preferences file (androidx.datastore PreferenceMap protobuf) ----


def _varint(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        out.append(b | (0x80 if n else 0))
        if not n:
            return bytes(out)


def _field(number: int, payload: bytes) -> bytes:
    return _varint(number << 3 | 2) + _varint(len(payload)) + payload


def _value(v: object) -> bytes:
    match v:
        case bool():
            return _varint(1 << 3) + _varint(int(v))
        case int():
            return _varint(3 << 3) + _varint(v & 0xFFFFFFFFFFFFFFFF)
        case float():
            return _varint(7 << 3 | 1) + struct.pack("<d", v)
        case str():
            return _field(5, v.encode())
        case set():
            return _field(6, b"".join(_field(1, s.encode()) for s in sorted(v)))
    raise TypeError(v)


def preferences(widget_id: int, changes: Mapping[str, object]) -> bytes:
    """The app's preferences with Berlin as the only place and [changes] for the widget."""
    prefs: dict[str, object] = {
        "place_ids": {"default"},
        "place_default_name": PLACE["name"],
        "place_default_lat": PLACE["lat"],
        "place_default_lon": PLACE["lon"],
        "place_default_zone": PLACE["zone"],
        f"w{widget_id}_place": "default",
        f"w{widget_id}_configured": True,
    }
    prefs.update({f"w{widget_id}_{k}": v for k, v in changes.items()})
    return b"".join(_field(1, _field(1, k.encode()) + _field(2, _value(v))) for k, v in prefs.items())


# ---- scene ----


def widget_id(device: Device) -> int:
    dump = device.shell("dumpsys appwidget")
    ids = re.findall(r"\[\d+\] id=(\d+)\s+host=[^\n]*\s+provider=ProviderId\{[^}]*" + re.escape(PACKAGE), dump)
    if not ids:
        sys.exit("No LightTimeClouds widget on the home screen; place one first (see the docstring).")
    return int(ids[0])


def set_clock(device: Device, at: dt.datetime) -> None:
    device.shell("settings put global auto_time 0")
    device.shell(f"date {at:%m%d%H%M%Y.%S}")
    device.shell("am broadcast -a android.intent.action.TIME_SET", check=False)


def demo_status_bar(device: Device, at: dt.datetime) -> None:
    device.shell("settings put global sysui_demo_allowed 1")
    for extras in (
        "enter",
        f"clock -e hhmm {at:%H%M}",
        "battery -e level 100 -e plugged false",
        "network -e wifi show -e level 4 -e mobile hide",
        "notifications -e visible false",
    ):
        command, *rest = extras.split(" ", 1)
        device.shell(f"am broadcast -a com.android.systemui.demo -e command {command} {' '.join(rest)}")


def network(device: Device, on: bool) -> None:
    state = "enable" if on else "disable"
    device.shell(f"svc wifi {state}; svc data {state}")


def apply_settings(device: Device, wid: int, changes: Mapping[str, object], forecast: Path | None = None) -> None:
    """Writes the settings, and [forecast] if given, then redraws the widget."""
    device.shell(f"am force-stop {PACKAGE}")
    file = WORK / "widget_prefs.preferences_pb"
    file.write_bytes(preferences(wid, changes))
    device.push_file(file, PREFS, "600")
    if forecast:
        device.push_file(forecast, FORECAST, "644")
    device.shell(
        f"am broadcast --include-stopped-packages -a android.appwidget.action.APPWIDGET_UPDATE "
        f"-n {PROVIDER} --eia appWidgetIds {wid}"
    )
    time.sleep(SETTLE_S)


def fetch_forecast(device: Device, wid: int, keep: Path) -> None:
    device.shell(f"rm -f {FORECAST}")
    network(device, True)
    time.sleep(5)
    apply_settings(device, wid, SHOWCASE)
    for _ in range(60):
        if device.shell(f"ls {FORECAST}", check=False) == FORECAST:
            break
        time.sleep(1)
    else:
        sys.exit("The forecast did not arrive within a minute.")
    network(device, False)
    keep.parent.mkdir(parents=True, exist_ok=True)
    device.run("pull", FORECAST, str(keep))


def strings(locale: str) -> dict[str, str]:
    tree = ET.parse(ROOT / "app/src/main/res" / LOCALES[locale] / "strings.xml")
    return {e.get("name", ""): (e.text or "").replace("\\'", "'") for e in tree.iter("string")}


def switch_locale(device: Device, locale: str) -> None:
    device.shell(f"cmd locale set-app-locales {PACKAGE} --locales ''", check=False)
    if device.shell("getprop persist.sys.locale") == locale:
        return
    device.shell(f"setprop persist.sys.locale {locale}; setprop ctl.restart zygote")
    time.sleep(5)
    device.wait_for_boot()


def shoot_locale(device: Device, wid: int, locale: str, at: dt.datetime, forecast: Path) -> None:
    texts = strings(locale)
    folder = OUT / locale / "images/phoneScreenshots"
    switch_locale(device, locale)
    network(device, False)
    set_clock(device, at)
    demo_status_bar(device, at)
    # Without weather the app drops its forecasts, so the forecast only comes along for shot 2.
    apply_settings(device, wid, {})
    device.shell("input keyevent KEYCODE_HOME")
    time.sleep(SETTLE_S)
    device.screenshot(folder / "1.png")

    apply_settings(device, wid, SHOWCASE, forecast)
    device.shell("input keyevent KEYCODE_HOME")
    time.sleep(SETTLE_S)
    device.screenshot(folder / "2.png")

    set_clock(device, at)
    device.shell(f"am start -W -a android.appwidget.action.APPWIDGET_CONFIGURE -n {CONFIG} --ei appWidgetId {wid}")
    time.sleep(SETTLE_S)
    tabs_bottom = 0
    for number, tab in ((3, "tab_weather"), (4, "tab_times"), (5, "tab_settings"), (6, "tab_preview")):
        tabs_bottom = device.tap_text(texts[tab])
        time.sleep(SETTLE_S)
        if tab == "tab_settings":
            # One topic open, the way the tab is used.
            device.tap_text(texts["section_weather"], below=tabs_bottom + 1)
            time.sleep(SETTLE_S)
        device.screenshot(folder / f"{number}.png")
    device.shell("input keyevent KEYCODE_BACK")
    print(f"{locale}: 6 screenshots")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--apk", type=Path, help="install this APK first")
    parser.add_argument("--date", type=dt.date.fromisoformat, default=dt.date.today(), help="scene date, default today")
    parser.add_argument("--fetch", action="store_true", help="load a fresh forecast for the scene")
    parser.add_argument("--locales", default=",".join(LOCALES), help="comma-separated, default all four")
    args = parser.parse_args()

    device = Device(args.serial)
    device.run("root")
    device.wait_for_boot()
    if args.apk:
        device.run("install", "-r", str(args.apk))
    WORK.mkdir(parents=True, exist_ok=True)
    wid = widget_id(device)
    at = dt.datetime.combine(args.date, SCENE_TIME)
    forecast = WORK / "forecast.json"

    set_clock(device, at)
    if args.fetch:
        fetch_forecast(device, wid, forecast)
    elif not forecast.exists():
        sys.exit(f"No saved forecast at {forecast}; run with --fetch.")

    for locale in args.locales.split(","):
        shoot_locale(device, wid, locale, at, forecast)


if __name__ == "__main__":
    main()

"""Smoke test on a real Android emulator, driven with adb (CI only; see the `emulator` job).

1. Installs the previous release, turns on today's "Rest day" as a marker, then installs the new APK over it
   and checks the marker is still there (an update keeps the data).
2. Opens every tab and Settings and saves a screenshot of each to the output folder.
3. Fails if the app crashed at any point.

Usage: python3 smoke.py NEW_APK OLD_APK OUT_DIR
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "io.github.midhun.wttracker"
new_apk, old_apk, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)


def adb(*args, check=True):
    return subprocess.run(["adb", *args], check=check, capture_output=True, text=True).stdout


def nodes():
    """The screen's accessibility nodes as (text, content-desc, checkable, checked, centre x, centre y)."""
    for _ in range(5):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
        xml = adb("exec-out", "cat", "/sdcard/ui.xml", check=False)
        if xml.startswith("<?xml"):
            found = []
            for n in ET.fromstring(xml).iter("node"):
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
                found.append((n.get("text"), n.get("content-desc"), n.get("checkable") == "true",
                              n.get("checked") == "true", (x1 + x2) // 2, (y1 + y2) // 2))
            return found
        time.sleep(1)
    sys.exit("Could not read the screen with uiautomator")


def wait_for(label, timeout=40):
    end = time.time() + timeout
    while time.time() < end:
        if any(label in (t, d) for t, d, *_ in nodes()):
            return
        time.sleep(1)
    shot("failed-waiting")
    sys.exit(f'"{label}" never appeared')


def tap(label, lowest=False):
    """Taps the node with this text or description; `lowest` picks the bottom one (the tab, not the title)."""
    hits = [(x, y) for t, d, _, _, x, y in nodes() if label in (t, d)]
    if not hits:
        shot("failed-tap")
        sys.exit(f'Nothing to tap called "{label}"')
    x, y = max(hits, key=lambda p: p[1]) if lowest else hits[0]
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1.5)


def shot(name):
    with open(os.path.join(out, f"{name}.png"), "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, capture_output=True).stdout)


def launch():
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/wt.app.MainActivity")
    wait_for("Trend")


def check_no_crash():
    log = adb("logcat", "-d", "-b", "crash", check=False)
    if PKG in log:
        print(log)
        sys.exit("The app crashed")


adb("logcat", "-c", check=False)

# 1. Previous release, then the new APK over it.
adb("install", old_apk)
launch()
wait_for("Rest day")
switch = [(x, y) for _, _, checkable, checked, x, y in nodes() if checkable and not checked]
if not switch:
    sys.exit("No rest-day switch on Today")
adb("shell", "input", "tap", str(switch[0][0]), str(switch[0][1]))
wait_for("Recovery counts")
shot("00-old-version-rest-day-on")
adb("shell", "am", "force-stop", PKG)

adb("install", "-r", new_apk)
launch()
wait_for("Recovery counts")  # the rest day logged in the old version survived the update
print("Update kept the data: today's rest day is still on")

# 2. Every tab and Settings.
shot("01-today")
for i, tab in enumerate(["Trend", "Runs", "Body", "Plan"], start=2):
    tap(tab, lowest=True)
    shot(f"0{i}-{tab.lower()}")
    adb("shell", "input", "swipe", "540", "1800", "540", "700", "400")
    time.sleep(1.5)
    shot(f"0{i}-{tab.lower()}-scrolled")
tap("Settings")
shot("06-settings")
adb("shell", "input", "keyevent", "KEYCODE_BACK")
time.sleep(1.5)
tap("Today", lowest=True)
shot("07-back-to-today")

# 3. No crash anywhere.
check_no_crash()
print("Smoke test passed")

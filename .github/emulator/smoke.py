"""Smoke test on a real Android emulator, driven with adb (CI only; see the `emulator` job).

1. Installs the latest release (going through setup if it has one), turns on today's "Rest day" as a marker, then installs the new APK over it
   and checks the marker is still there (an update keeps the data).
2. Opens every tab and Settings and saves a screenshot of each to the output folder.
3. Reinstalls fresh and goes through first-run setup with its defaults.
4. Reinstalls fresh and follows a partner: the workflow serves core/src/test/resources on the host's port 8765
   (http://10.0.2.2:8765 from the emulator; debug builds accept that URL). Checks the tabs show the data with no
   edit controls, then Stop following returns to setup.
5. Fails if the app crashed at any point.

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
            # A slow CI emulator can show "<some app> isn't responding" over everything: wait it out.
            if any((t or "").endswith("isn't responding") for t, *_ in found):
                wait = [(x, y) for t, _, _, _, x, y in found if t == "Wait"]
                if wait:
                    adb("shell", "input", "tap", str(wait[0][0]), str(wait[0][1]))
                time.sleep(2)
                continue
            return found
        time.sleep(1)
    sys.exit("Could not read the screen with uiautomator")


def wait_for(*labels, timeout=40):
    """Waits until one of [labels] is on screen and returns it."""
    end = time.time() + timeout
    while time.time() < end:
        on_screen = {v for t, d, *_ in nodes() for v in (t, d)}
        for label in labels:
            if label in on_screen:
                return label
        time.sleep(1)
    shot("failed-waiting")
    sys.exit(f"{' or '.join(labels)} never appeared")


def wait_contains(part, timeout=40):
    """Waits until some text on screen contains [part]."""
    end = time.time() + timeout
    while time.time() < end:
        if any(part in (t or "") for t, *_ in nodes()):
            return
        time.sleep(1)
    shot("failed-waiting")
    sys.exit(f'Nothing containing "{part}" appeared')


def assert_absent(label):
    if any(label in (t, d) for t, d, *_ in nodes()):
        shot("failed-absent")
        sys.exit(f'"{label}" is on screen in follow mode')


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
    """Opens the app; on a fresh install of a version with first-run setup, goes through it with its defaults."""
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/wt.app.MainActivity")
    if wait_for("Trend", "Set up my plan") == "Set up my plan":
        setup()


def setup(prefix=None):
    """Taps through first-run setup with its defaults; with [prefix], saves a screenshot of each step."""
    if prefix:
        shot(f"{prefix}0-setup-welcome")
    for i, (button, next_heading, name) in enumerate([
        ("Set up my plan", "What do you weigh today?", "setup-weight"),
        ("Next", "Where do you want to be, and by when?", "setup-goal"),
        ("Next", "Which day will you weigh in?", "setup-weigh-in"),
        ("Next", "Your plan", "setup-plan"),
    ], start=1):
        tap(button, lowest=True)
        wait_for(next_heading)
        if prefix:
            shot(f"{prefix}{i}-{name}")
    tap("Start", lowest=True)  # the button, not the timeline's "Start" label
    wait_for("Trend")


def check_no_crash():
    log = adb("logcat", "-d", "-b", "crash", check=False)
    if PKG in log:
        print(log)
        sys.exit("The app crashed")


adb("logcat", "-c", check=False)
adb("shell", "settings", "put", "global", "hide_error_dialogs", "1", check=False)
adb("shell", "am", "broadcast", "-a", "android.intent.action.CLOSE_SYSTEM_DIALOGS", check=False)

# 1. Previous release, then the new APK over it.
adb("install", old_apk)
adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS", check=False)
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

# 3. Fresh install: first-run setup with its defaults (80.0 kg today, a 75 kg goal at 0.5 kg/week).
adb("uninstall", PKG)
adb("install", new_apk)
adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS", check=False)
adb("shell", "am", "start", "-W", "-n", f"{PKG}/wt.app.MainActivity")
wait_for("Set up my plan")
setup(prefix="1")
shot("15-after-setup")
print("Setup on a fresh install works")

# 4. Fresh install that follows a partner (read-only copy of the served share file).
adb("uninstall", PKG)
adb("install", new_apk)
adb("shell", "am", "start", "-W", "-n", f"{PKG}/wt.app.MainActivity")
wait_for("Set up my plan")
tap("Follow a partner")
wait_for("Link from your partner")
tap("Link from your partner")
adb("shell", "input", "text", "http://10.0.2.2:8765/share-sample.json")
time.sleep(1)
shot("19-follow-link")
tap("Follow", lowest=True)
wait_contains("Following Midhun")
shot("20-follow-today")
assert_absent("Add run")
tap("Trend", lowest=True)
shot("21-follow-trend")
tap("Runs", lowest=True)
assert_absent("Add run")
shot("22-follow-runs")
tap("Settings")
wait_for("Stop following")
shot("23-follow-settings")
tap("Stop following")
wait_for("Stop following?")
tap("Stop following", lowest=True)
wait_for("Set up my plan")
print("Follow mode works: read-only tabs, Stop following returns to setup")

# 5. No crash anywhere.
check_no_crash()
print("Smoke test passed")

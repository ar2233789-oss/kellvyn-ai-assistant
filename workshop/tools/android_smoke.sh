#!/usr/bin/env bash
# Bounded Android 15 AVD smoke-test. No blind coordinate taps and no app-source edits.
set -Eeuo pipefail
APK="${1:?usage: android_smoke.sh path/to/app-debug.apk output-dir}"
OUT="${2:?usage: android_smoke.sh path/to/app-debug.apk output-dir}"
mkdir -p "$OUT"
ADB="${ADB:-adb}"
TIMEOUT="${LAB_ADB_TIMEOUT:-60}"
cmd() { timeout "$TIMEOUT" "$ADB" "$@"; }
finish_diag() {
  python3 - "$OUT/android-smoke.json" "${RESULT:-failed}" "${ERROR:-}" <<'PY'
import json, sys, time
path, status, error = sys.argv[1:]
with open(path, "w", encoding="utf-8") as f:
    json.dump({"schema":1,"status":status,"error":error or None,"unixTime":int(time.time())}, f, ensure_ascii=False, indent=2)
    f.write("\n")
PY
}
on_error() {
  ERROR="line $1: $2"
  finish_diag || true
  cmd logcat -d -t 800 > "$OUT/logcat-tail.txt" 2>&1 || true
  cmd shell dumpsys activity activities > "$OUT/activities.txt" 2>&1 || true
}
trap 'on_error "$LINENO" "$BASH_COMMAND"' ERR
: > "$OUT/android-smoke.json"
test -s "$APK"
device_ready=0
for _ in $(seq 1 90); do
  if cmd devices | grep -Eq '^emulator-[0-9]+[[:space:]]+device$'; then device_ready=1; break; fi
  sleep 2
done
if [ "$device_ready" != "1" ]; then ERROR="No Android virtual device appeared in ADB within 3 minutes"; false; fi
cmd wait-for-device
booted=0
for _ in $(seq 1 120); do
  state="$(cmd shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [ "$state" = "1" ]; then booted=1; break; fi
  sleep 3
done
if [ "$booted" != "1" ]; then ERROR="Android 15 virtual device did not finish booting within 6 minutes"; false; fi
# First boot continues Google/OEM provisioning for a short period after sys.boot_completed.
# Let package manager settle before installing the test APK.
sleep 18
cmd shell wm size 1220x2712
cmd shell wm density 440
if [ -e /dev/kvm ]; then sudo chmod 666 /dev/kvm || true; fi
cmd install -r "$APK" | tee "$OUT/install.txt"
cmd shell am force-stop com.kellvyn.assistant
cmd shell am start -W -n com.kellvyn.assistant/.MainActivity | tee "$OUT/launch.txt"
sleep 5
cmd logcat -d -b crash > "$OUT/crash-buffer.txt" 2>&1 || true
if grep -q "Process: com.kellvyn.assistant" "$OUT/crash-buffer.txt"; then
  ERROR="Android crash buffer contains a Kellvyn process crash"
  false
fi
cmd shell screencap -p /sdcard/super-lab-main.png
cmd pull /sdcard/super-lab-main.png "$OUT/native-screen.png" >/dev/null
cmd shell uiautomator dump /sdcard/super-lab-main.xml > "$OUT/ui-main-dump.log" 2>&1 || true
cmd pull /sdcard/super-lab-main.xml "$OUT/native-main.xml" >/dev/null 2>&1 || true
test -s "$OUT/native-screen.png"
SETTINGS_TAP=""
if test -s "$OUT/native-main.xml"; then
  SETTINGS_TAP="$(python3 - "$OUT/native-main.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root=ET.parse(sys.argv[1]).getroot()
except Exception:
    raise SystemExit(0)
for node in root.iter("node"):
    if node.attrib.get("content-desc","").strip().casefold() == "configurações":
        bounds=node.attrib.get("bounds","").replace("][",",").strip("[]").split(",")
        if len(bounds)==4:
            try:
                x1,y1,x2,y2=map(int,bounds)
                print((x1+x2)//2,(y1+y2)//2)
            except ValueError:
                pass
        break
PY
)"
fi
if [ -n "$SETTINGS_TAP" ]; then
  read -r sx sy <<< "$SETTINGS_TAP"
  cmd shell input tap "$sx" "$sy"
  sleep 2
  cmd shell screencap -p /sdcard/super-lab-settings.png
  cmd pull /sdcard/super-lab-settings.png "$OUT/native-settings.png" >/dev/null
  cmd shell uiautomator dump /sdcard/super-lab-settings.xml > "$OUT/ui-settings-dump.log" 2>&1 || true
  cmd pull /sdcard/super-lab-settings.xml "$OUT/native-settings.xml" >/dev/null 2>&1 || true
  # Android native/blurred dialogs may not appear as a second window to UIAutomator.
  # Dismiss reliably using BACK; never click a guessed screen coordinate.
  cmd shell input keyevent 4
  sleep 1
  cmd shell dumpsys activity activities > "$OUT/activities-after-settings.txt"
  grep -q 'com.kellvyn.assistant/.MainActivity' "$OUT/activities-after-settings.txt"
  cmd shell screencap -p /sdcard/super-lab-after-settings.png
  cmd pull /sdcard/super-lab-after-settings.png "$OUT/native-after-settings.png" >/dev/null
  SETTINGS_RESULT="opened_and_returned"
else
  SETTINGS_RESULT="not_exposed_in_accessibility_tree"
  printf '%s\n' "The main screen was captured, but its settings button wasn't exposed in the accessibility tree; no blind coordinate was used." > "$OUT/settings-warning.txt"
fi
cmd shell dumpsys meminfo com.kellvyn.assistant > "$OUT/memory.txt"
cmd shell dumpsys activity activities > "$OUT/activities-final.txt"
if grep -q 'com.kellvyn.assistant/.MainActivity' "$OUT/activities-final.txt"; then FOREGROUND=true; else FOREGROUND=false; fi
python3 - "$OUT/android-smoke.json" "$SETTINGS_RESULT" "$FOREGROUND" <<'PY'
import json, sys
path, settings, foreground = sys.argv[1:]
out={"schema":1,"status":"passed","package":"com.kellvyn.assistant","androidApi":35,
     "virtualProfile":{"name":"PocoF6Virtual","width":1220,"height":2712,"densityDpi":440,"ramMb":8192},
     "mainScreenshot":"native-screen.png","settingsScreenshot":"native-settings.png" if settings=="opened_and_returned" else None,
     "settingsAction":settings,"appForegroundAtEnd":foreground=="true",
     "note":"Virtual smoke-test only; does not claim physical-device thermals or local-model performance."}
out["status"]="passed" if foreground=="true" else "failed"
with open(path,"w",encoding="utf-8") as f: json.dump(out,f,ensure_ascii=False,indent=2); f.write("\n")
if out["status"]!="passed": raise SystemExit("Kellvyn did not remain in the foreground.")
PY

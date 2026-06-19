# Troubleshooting & FAQs

## SDR Device Not Recognized

If you get errors like "Source not available" or "Error with Source",
the app is not able to communicate with the SDR device.

Possible reasons are:

- Wrong SDR selected in **Source Tab**.
- Phone’s USB port doesn’t provide enough power.
- USB OTG adapter is faulty.
- Another app is already using the SDR.

**Solution:**

1. Try a powered USB hub.
2. Ensure no other apps are using the SDR.
3. Try different USB OTG adapters.
4. Turn on logging in the [Settings](./settings.md#logging) and look in the log file for more details.

## Performance Issues (Slow FFT, Choppy Audio)

Older hardware might not be powerful enough to ensure smooth operation of the
app. In this case try the following:

- Reduce **sample rate** of the source.
- Lower **FFT size & frame rate** in the FFT tab.
- Increase **waterfall speed** (fewer history samples).

## Poor Signal Quality / High Noise

- Use a **good antenna** & place it in an open area.
- Disable **automatic gain** and manually adjust gain.
- Increase **sample rate** to reduce aliasing (HackRF).

## App Crashes

- If possible, submit crash reports via Android.
- RF Analyzer can log system errors; check the [Settings](./settings.md#logging).

## File Playback Stops Working After Reboot or App Restart

Open IQ files through the app's file picker instead of sharing temporary files
from another app. Android storage providers can grant long-lived read access
when the file is selected with the picker, which lets RF Analyzer reopen the
same file source later.

If a file was moved, renamed, deleted, or lives in a provider that does not
support persisted access, select the file again from the Filesource tab.

## Large Recording Export Has No Progress Notification

On Android 13 and newer, notification permission is optional. Large exports can
continue without it, but Android will hide the progress notification. Enable
notifications for RF Analyzer in Android Settings if you want background export
progress and completion status.

## Developer Build Troubleshooting

### `mkdocs` Not Found

The app packages the user manual into assets before building. Install the docs
dependencies first:

```shell
python3 -m venv /tmp/rfanalyzer-docs-venv
/tmp/rfanalyzer-docs-venv/bin/python -m pip install -r requirements-docs.txt
export PATH="/tmp/rfanalyzer-docs-venv/bin:$PATH"
```

### Java Not Found on macOS

Use Android Studio's bundled JBR:

```shell
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

### Native Build Cannot Find CMake

Install CMake `3.22.1` through Android Studio's SDK Manager. All native modules
use this version so Gradle can configure HackRF, Airspy, Airspy HF+, HydraSDR,
libusb, and native DSP consistently.

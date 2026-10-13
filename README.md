# OpenDisplay Android — USB & low-latency fork

**Use an Android tablet as a real second monitor for your Mac, over a USB cable, with the
latency measured stage by stage.**

This is a fork of [josepacelli/opendisplay-android](https://github.com/josepacelli/opendisplay-android),
the Android receiver for [OpenDisplay](https://github.com/peetzweg/opendisplay). It exists
because the stock setup didn't work well on an older tablet (Huawei MatePad 10.4, HarmonyOS 2):
no plug-and-play cable path, decoding in normal (high-latency) mode, and no way to see where the
milliseconds went. Everything here is aimed at that: **wired, low-latency, measurable**.

It is meant to be used with the matching Mac sender fork,
**[diacunap/opendisplay](https://github.com/diacunap/opendisplay)**, which adds automatic USB
(adb) support for Android. Both stay wire-compatible with the official apps.

> Unofficial and unaffiliated. Not supported by the upstream authors — please report issues here.

## What this fork changes

| | Upstream receiver | This fork |
| --- | --- | --- |
| Low-latency decode on Android ≤ 10 (incl. HarmonyOS 2) | Off (`KEY_LOW_LATENCY` is API 30+) | **On**, via SoC vendor keys (Kirin, Qualcomm, Exynos, MediaTek), with safe fallback |
| Decoder choice | Whatever `createDecoderByType` returns | Explicit; hardware vs. software shown in the HUD |
| Stale decoded frames | Rendered in order | **Render-latest**: an older picture is skipped when a newer one is ready |
| Frame feeding | Main (UI) thread; frame dropped if the decoder is busy | **Dedicated thread**, waits up to 30 ms, 16-frame buffer: fewer drops, fewer glitches |
| Latency readout | fps · end-to-end to arrival · RTT | **Per stage**: capture · encode · network · queue · decode · display · total (p50/p95) |
| Latency in the Mac's log | — | `PHONE-STATS` line every 5 s |
| USB with the Mac | Manual `adb forward` + hidden Mac override, or AOA with a separate host tool | **Plug and play** with the [sender fork](https://github.com/diacunap/opendisplay) |

Details, rationale and the exact keys used: **[LATENCY-AND-USB.md](LATENCY-AND-USB.md)**.

## How it works

```
MAC (diacunap/opendisplay)                        ANDROID (this fork)
virtual display → ScreenCaptureKit
  → VideoToolbox H.264 (low-latency RC)
  → adb forward ═══ USB cable ═══════════════════→ listener :9000
     (or WiFi/Bonjour, unchanged)                   → feed thread → MediaCodec
                                                      (vendor low-latency) → Surface
  ← touch / scroll / ping / stats ══════════════
```

The protocol is OpenDisplay's own ([PROTOCOL.md](https://github.com/peetzweg/opendisplay/blob/main/PROTOCOL.md)):
the receiver listens on TCP 9000, the Mac connects. Over USB the Mac reaches that port through
`adb forward`; nothing on the wire changes.

## Latency HUD

Enable the performance overlay in Settings:

```
60 fps · total 38/52ms · rtt 1ms
cap 4 · enc 5 · net 2 · q 0 · dec 12 · disp 15
OMX.hisi.video.decoder.avc (hw) · low-latency: hisi
```

`cap` needs an extra timestamp that only the sender fork sends; with the official Mac app that
stage shows `–` and the rest still works.

## Install

1. **Mac:** build the sender fork — see
   [ANDROID-USB.md](https://github.com/diacunap/opendisplay/blob/main/ANDROID-USB.md).
2. **Tablet:** enable Developer options → **USB debugging** (on Huawei also *"Allow ADB debugging
   in charge only mode"*), then build and install this app:

   ```sh
   ./gradlew assembleRelease     # needs JDK 17+ and the Android SDK (local.properties → sdk.dir)
   adb uninstall io.github.josepacelli.opendisplay   # only if an upstream build is installed
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

   A release APK must be signed: see `app/build.gradle.kts` for the keystore setup, or sign the
   unsigned APK with `apksigner`.
3. Plug the cable and open the app on the tablet. The Mac connects on its own.

Diagnostics:

```sh
adb logcat -s OpenDisplay:*                         # receiver log
grep PHONE-STATS ~/Library/Logs/OpenDisplay/*.log   # latency, on the Mac
# turn the vendor low-latency keys off (to rule them out), then reopen the app:
adb shell touch /sdcard/Android/data/io.github.josepacelli.opendisplay/files/no-lowlatency
```

## Tested on

| Device | OS | Result |
| --- | --- | --- |
| Huawei MatePad 10.4 (BAH3-W59) | HarmonyOS 2 (API 29) | USB connection working with the sender fork; latency figures and glitch fixes being validated |

Upstream was tested on Samsung Galaxy Tab S9 FE+, Note10+ and Tab S7+ (Android 12–16).
Reports from other devices are welcome: include the HUD's decoder line and a `PHONE-STATS` line.

## Known issues

- **Fullscreen video in a Mac browser** (e.g. YouTube) can show only the "exit full screen"
  overlay on the extended display: macOS presents fullscreen video outside normal composition,
  so screen capture doesn't see it. Use theater mode or a maximized window. Not specific to this
  fork.
- Under investigation: the picture freezing after ~1 minute of Mac inactivity on the MatePad.

## Everything else

All upstream features are kept: hardware H.264 to a `Surface`, Bonjour discovery, touch →
click/drag, two-finger scroll, pinch-zoom and pan, remote cursor, foreground service,
Picture-in-Picture, the AOA accessory transport, and the adaptive Compose UI. See the
[upstream README](https://github.com/josepacelli/opendisplay-android#readme) for those.

## Credits & license

- Android receiver by [@josepacelli](https://github.com/josepacelli) and its contributors
  (see the upstream acknowledgments).
- OpenDisplay protocol and Mac app by [Philip Poloczek (@peetzweg)](https://github.com/peetzweg/opendisplay).
- Fork changes by [@diacunap](https://github.com/diacunap).

GPL-3.0, the same license as both upstream projects. See [`LICENSE`](LICENSE) and
[`NOTICE`](NOTICE).

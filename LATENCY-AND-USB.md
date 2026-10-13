# Latency breakdown, older-Android low-latency decode, and USB

This fork of the Android receiver adds three things on top of
[josepacelli/opendisplay-android](https://github.com/josepacelli/opendisplay-android).
It is designed to pair with the Mac sender fork
[diacunap/opendisplay](https://github.com/diacunap/opendisplay), which adds a
wired USB transport for Android (adb) — see its
[ANDROID-USB.md](https://github.com/diacunap/opendisplay/blob/main/ANDROID-USB.md).
Both directions stay compatible with the unmodified apps.

## 1. Low-latency decode on Android 10 and older (incl. HarmonyOS 2)

Upstream only enabled `MediaFormat.KEY_LOW_LATENCY`, which exists on
Android 11+ (API 30). Devices that report API ≤ 29 — every HarmonyOS 2 tablet
among them — therefore decoded in normal mode, where many SoC decoders hold
several frames before releasing output.

The decoder is now chosen explicitly (`MediaCodecList.findDecoderForFormat`)
and the SoC vendor's own low-latency switch is applied by codec name:

| Codec name contains | Keys set |
| --- | --- |
| `hisi` (Huawei Kirin) | `vendor.hisi-ext-low-latency-video-dec.video-scene-for-low-latency-req = 1`, `…-rdy = -1` |
| `qcom` / `qti` (Qualcomm) | `vendor.qti-ext-dec-low-latency.enable = 1`, `KEY_OPERATING_RATE = max` |
| `exynos` (Samsung) | `vendor.rtc-ext-dec-low-latency.enable = 1` |
| `mtk` / `mediatek` | `vendor.mtk.vdec.low-latency.enable = 1` |

On Android 11+ the standard key is set as well. If a codec rejects the vendor
keys at `configure()`, the decoder is rebuilt without them, so the worst case
is upstream behaviour. The HUD shows the codec, whether it is hardware, and
which mode was applied, e.g.
`OMX.hisi.video.decoder.avc (hw) · low-latency: hisi`.

## 2. Render-latest output policy

When the decoder already holds a newer picture, the older one is released
without rendering instead of being shown first (showing it would only add a
display refresh of lag). Decoding has already happened, so the H.264
reference chain is unaffected. Skips per second appear in the HUD as `skip N`.

## 3. Per-stage latency, in milliseconds

Every frame is timed stage by stage and the per-second medians are shown in
the performance HUD (Settings → performance overlay):

```
60 fps · total 38/52ms · rtt 1ms
cap 4 · enc 5 · net 2 · q 0 · dec 12 · disp 15
OMX.hisi.video.decoder.avc (hw) · low-latency: hisi
```

| Stage | From → to | Source |
| --- | --- | --- |
| `cap` | Mac compositor frame → encoder submit | telemetry `scr`→`cap` (needs the sender fork) |
| `enc` | encoder submit → socket send | telemetry `cap`→`snd` |
| `net` | socket send → last byte received | `snd` vs. arrival, corrected by the ping/pong clock offset |
| `q` | received → decoder input | `System.nanoTime()` |
| `dec` | decoder input → decoded picture | MediaCodec pts round-trip |
| `disp` | decoded → composited on screen | `MediaCodec.OnFrameRenderedListener` |
| `total` | sum (p50 / p95) | — |

Clock-offset error is about RTT/2, i.e. sub-millisecond over USB. Stages the
sender doesn't stamp show `–`. On devices whose decoder never reports render
times, `disp` shows `–` and the other stages are still reported.

Every 5 s the same numbers go to the Mac as a free-form `stats` message
(PROTOCOL.md §6.1), which the Mac logs as `PHONE-STATS` — one log file then
holds both ends.

## Install

The package name is unchanged, so an install from upstream must be removed
first (different signing key):

```bash
adb uninstall io.github.josepacelli.opendisplay
./gradlew installRelease        # or: adb install <apk>
```

Building needs JDK 17+ and the Android SDK (`local.properties` → `sdk.dir`).

## Tested on

| Device | OS | Status |
| --- | --- | --- |
| Huawei MatePad 10.4 (BAH3-W59) | HarmonyOS 2 (API 29) | builds and installs; latency numbers being collected |

Please report results for other devices with a HUD screenshot or a
`PHONE-STATS` line.

## Files changed vs upstream

- `video/VideoDecoder.kt` — explicit codec selection, vendor low-latency
  keys with fallback, render-latest, per-frame timing, render listener.
- `net/PhoneReceiver.kt` — `LatencySample`, stage fields on `VideoFrame`,
  per-second aggregation, `stats` to the Mac.
- `net/AnnexB.kt` — parse optional `scr` telemetry field.
- `ui/ReceiverScreen.kt` — HUD breakdown; `ui/VideoSurface.kt` — wiring.

GPL-3.0, as upstream. Changes © 2026 diacunap.

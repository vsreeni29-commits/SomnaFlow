# SomnaFlow

An evidence-based sleep app for Android: fall asleep faster, stay asleep, wake refreshed.
Native Kotlin + Jetpack Compose. No account, no server, no ads, no internet permission, no paid services —
every sound is synthesized on the phone and all data stays on the phone.

## Install

1. On your Android phone (Android 8.0+), open this repository's **Releases** page and download `SomnaFlow.apk`.
2. Open the file and allow "Install unknown apps" for your browser when asked.

Every push builds a fresh APK through GitHub Actions (`.github/workflows/build.yml`).

## What is inside

| Module | Feature | Where in the code |
|---|---|---|
| 1.1 | 6 Hz theta binaural beat (250 Hz left / 256 Hz right), held 30 dB under a nature bed (30:60 Combined Stimuli); 0.5–4 Hz delta sweep over pink noise | `audio/Mixer.kt` |
| 1.1 | Randomized procedural nature sounds: rain, ocean, waterfall, forest, river (+ campfire, chimes, wind) | `audio/Layers.kt` |
| 1.2 | 10-fader console, white/pink/brown noise, bass/mid/treble, piano and pad layers, fully offline | `audio/`, `ui/Library.kt` |
| 2.1 | Sleep Stories and sleep meditations (narrated by on-device text-to-speech); daytime meditation locked in the evening; PSQI-driven stepped care | `data/Content.kt`, `service/Player.kt` |
| 2.2 | Affect-labelling check-in; PSQI, PHQ-9, GAD-7 with progress reports | `ui/Mind.kt`, `data/Questionnaires.kt` |
| 3.1 | EEG band power (FFT, 5 bands), high-beta detection, closed-loop theta intervention; BLE heart rate + RMSSD; camera pulse sensor; snore / breathing-pause detection with micro-haptics | `bio/` , `service/SleepTrackingService.kt` |
| 3.2 | Health Connect sync (steps, resting HR, sleep stages, SpO2, respiratory rate); 14-day sleep debt and energy curve | `bio/Health.kt`, `data/SleepMath.kt` |
| 4 | Wind-down automation (Do Not Disturb, dimming, audio, stretches), daylight tracking, Sleep Readiness worklist, smart sleep-cycle alarm | `service/Routine.kt`, `ui/Routines.kt` |
| 5 | Dark purple low-blue UI, three labelled tabs, persistent mini-player with live waveform | `ui/` |

## Build it yourself

```
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest   # DSP, biosignal, sleep-maths and screen-rendering tests
```

The APK is signed with the sideload key in `keystore/` so each build installs over the last one.
That key is public: use your own keystore before publishing to an app store.

## Limits worth knowing

- Android only. Apple HealthKit/SensorKit do not exist on Android; Health Connect is the equivalent used here.
- Binaural beats need headphones. Phone volume is not calibrated in absolute decibels, so the app enforces the
  30 dB *difference* between beat and sound bed; set the overall volume to a comfortable conversation level.
- Sleep stages are estimated from mattress movement and cannot separate REM from light sleep.
- The Muse and generic-UART EEG paths and the BLE heart-rate path follow the published protocols but have not
  been run against physical hardware. A built-in demo signal exercises the closed loop without a headband.
- SomnaFlow is a wellness tool, not a medical device, and does not diagnose insomnia or sleep apnea.

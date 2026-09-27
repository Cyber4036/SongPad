# Wave 6 (Beta) — release runbook

Gate M5 (spec §20): *signed build, Play declarations complete, external testers recording daily.*

Code-side prerequisites are in place: release signing wiring, versionCode injection, R8 verified
(`HARDENING_STATUS.md`), and the crash-reporting decision (ADR-0036). Everything below that needs the Play Console
account, the upload key or testers is a manual step for the release owner.

## 1. Upload key (once)

Generate the upload key **outside the repo** and keep it backed up. Losing it means a Play upload-key reset.

```sh
keytool -genkeypair -v -keystore ~/keys/songpad-upload.jks -alias songpad-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Enrol in Play App Signing when creating the app. Google holds the app-signing key, and this is only the upload
key.

## 2. Point the build at it

Create `signing.properties` at the repo root. It is gitignored, as are `*.jks` and `*.keystore`:

```properties
storeFile=/home/you/keys/songpad-upload.jks
storePassword=…
keyAlias=songpad-upload
keyPassword=…
```

In CI, use the `SONGPAD_STORE_FILE`, `SONGPAD_STORE_PASSWORD`, `SONGPAD_KEY_ALIAS` and `SONGPAD_KEY_PASSWORD`
environment variables instead. With neither set, `release` builds come out unsigned, and no key is ever
committed. The `internal` variant (minified, `.internal` suffix) stays debug-signed for sideloaded dogfooding.

## 3. Build the bundle

Every Play upload needs a new, higher `versionCode`:

```sh
export ANDROID_HOME=~/Android/Sdk
./scripts/vendor_lame.sh                      # LAME is gitignored; without it MP3 export is disabled
./gradlew :app:bundleRelease -Psongpad.versionCode=2
# → app/build/outputs/bundle/release/app-release.aab
# → app/build/outputs/mapping/release/mapping.txt   (upload with the bundle — ADR-0036)
```

Check that `vendor_lame.sh` ran. A bundle built without LAME ships with MP3 export silently unavailable.

## 4. Play Console

1. **App content**: use the Data Safety answers, FGS declaration and privacy policy from `docs/play/README.md`,
   unchanged. No SDK or network permission was added for crash reporting (ADR-0036). Host the privacy policy at
   a public URL.
2. **Foreground service declaration (`microphone`)**: record the 30-second demo video described in
   `docs/play/README.md`. This is still outstanding.
3. **Testing → Internal testing** (up to 100 testers, no review wait) for the first week, then **Closed testing**.
   Testers install from the opt-in link, **not** a sideloaded APK. Android vitals only reports Play-installed
   builds.
4. Upload the `.aab` and its `mapping.txt` under *App bundle explorer → Downloads → ReTrace mapping file* so
   vitals stack traces are deobfuscated.

## 5. Tester cohort

Target at least one tester per device class, with at least one on Xiaomi/HyperOS (the explicit untested gap,
`HARDENING_STATUS.md`) and at least one on Transsion (Tecno/Infinix/itel).

Brief testers on the one thing that matters most: **tell us immediately if a recording is ever missing.** The
fallback for a reproducible bug is a screen recording, or `adb bugreport` for testers who can run it.

## 6. What beta measures (Wave 6, qualitative)

Collect answers to these in a short weekly check-in, not in-app telemetry. The app has none, by design:

- How quickly can someone start recording?
- Do they understand what happens after saving?
- Can they find an old idea?
- Do they understand Songs?
- Does Piano + Voice make sense? Ask headphone users specifically whether the piano is in their takes (ADR-0035).
- **Do they ever lose a recording?**
- Do they understand the difference between Notes and Takes?

**M5 closes** when external testers are recording daily on their own devices, and vitals shows no crash cluster
in the capture, save or recovery path.

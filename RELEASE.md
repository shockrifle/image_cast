# Publishing checklist

What stands between the current build and a public release. Ordered by what
blocks what, not by effort.

Status as of 2026-09-13: the app works end to end on registered hardware
(ASUS Zenfone 10 → Chromecast "Bag End"), but it cannot be published yet — see
the two blockers below.

---

## Blockers

Nothing else matters until these two are done.

- [ ] **Raise the target API level to 36.**
  `app/build.gradle.kts` currently sets `compileSdk = 35` and `targetSdk = 35`.
  Since **31 August 2026**, new apps and updates must target **Android 16
  (API 36)** to be accepted by Google Play — that deadline has already passed,
  so the current build cannot be submitted at all.
  Bump both, then re-test: API 36 tightens edge-to-edge enforcement and
  predictive back, either of which can disturb this UI.
  (An extension to 1 November 2026 can be requested in Play Console.)

- [ ] **Publish the Cast receiver.**
  Application `970FC601` is **Unpublished**, which means it only launches on
  devices registered in the Cast Developer Console. Every other user sees
  "No devices available" — the exact symptom we debugged during development.
  Publishing submits the receiver for Google review against the Cast Design
  Checklist. Until it is approved the app is non-functional for real users, so
  this gates the whole release.

---

## Play Console mechanics

- [ ] **Developer account** — US$25 one-time fee, plus identity verification.

- [ ] **Closed testing, if this is a personal account created after
      13 November 2023.**
  Production access requires **12 testers opted in continuously for 14 days**.
  The count must hold for the entire window: if a tester opts out on day 11,
  the clock restarts at zero. Clearing the bar unlocks the *application* for
  production access, which Google then reviews — it does not publish anything
  by itself.
  Organization accounts and personal accounts created before that date are
  exempt. This is normally the longest item on the schedule; start it first.

- [ ] **Release signing.**
  There is no `signingConfig` in the build at all today, so `assembleRelease`
  emits an unsigned APK. Create an upload keystore, load it from a gitignored
  `keystore.properties`, and enrol in Play App Signing.
  Never commit the keystore or its passwords — unlike the Cast application ID,
  these are real credentials.

- [ ] **Build an App Bundle, not an APK** — `bundleRelease`. Play requires AABs
      for new apps.

- [ ] **Version metadata.** `versionCode = 1` / `versionName = "1.0"` are fine
      for a first release; decide how they get bumped from here.

---

## Store assets

- [ ] **Launcher icon.** `res/drawable/ic_launcher.xml` is a placeholder drawn
      during development: a single vector, no adaptive icon, no density
      buckets, no monochrome layer for themed icons. Needs a real
      `mipmap-anydpi-v26` adaptive icon (foreground + background) and a
      512×512 store icon.

- [ ] **Feature graphic** — 1024×500.

- [ ] **Screenshots** — at least 2 phone screenshots; 4 or more reads better.
      A shot of the TV output makes the point of the app far faster than words.

- [ ] **Descriptions** — short (80 chars) and full.

- [ ] **Check the app name against Cast brand guidelines.**
      Google restricts how "Cast" and "Chromecast" may appear in third-party
      app names and icons. "Image Cast" is worth verifying before submission;
      it is a common rejection reason.

---

## Compliance forms

- [ ] **Privacy policy URL** — required for every app, including ones that
      collect nothing. It can live on the same GitHub Pages site as the
      receiver.

- [ ] **Data safety form.** Declare it accurately: no data is sent to any
      server of ours, but the selected image *is* transmitted over the local
      network to the cast device.

- [ ] **Content rating questionnaire.**

- [ ] **Target audience** and **ads declaration** (there are no ads).

---

## Code quality worth fixing first

None of these block submission; all of them are cheaper to do before there are
users.

- [ ] **Enable R8.** `isMinifyEnabled = false` today. Turn on minification and
      `shrinkResources`, then re-test casting: the Cast SDK uses reflection, and
      the single keep rule in `proguard-rules.pro` may not be enough.

- [ ] **Drop `android:largeHeap="true"`.** It is currently masking a 4096 px
      preview decode rather than sizing bitmaps to the actual display.

- [ ] **Unit-test `ViewTransform`.** It is pure maths and it is the heart of the
      app; a regression in zoom-about-centroid would stay invisible until
      someone noticed it on a television.

- [ ] **Hosting durability.** The receiver URL is bound to application ID
      `970FC601`. If GitHub Pages moves, or the repository is renamed, every
      installed copy breaks — and changing the URL means a console edit plus
      another review. Putting a custom domain in front of it now makes that
      survivable later.

- [ ] **Polish:** hardcoded colours in `ImageCastScreen.kt` instead of theme
      tokens, English-only strings, and no content description on the cast
      button.

---

## Sources

- [Target API level requirements for Google Play apps](https://support.google.com/googleplay/android-developer/answer/11926878)
- [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Google Cast Developer Console](https://cast.google.com/publish)

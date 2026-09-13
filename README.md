# Image Cast

An Android app that lets you pick a picture, frame it with pinch-zoom and free
panning, and push that framing to a Chromecast — where the receiver **renders
the image itself** rather than mirroring the phone.

## What it does

- **Pick an image** from the device (`ACTION_OPEN_DOCUMENT`, no storage permission needed).
- **Zoom and pan freely.** Pinch to scale from 5 % to 4000 %, drag in any
  direction with no bounds, so the picture can be shrunk far below the screen
  size or pushed completely off-centre. Double-tap toggles between fit and 250 %.
- **Cast with remote rendering.** The picture is streamed once to a custom web
  receiver, then only the zoom/pan state is pushed as you gesture. The receiver
  lays the image out for *its own* screen and applies the transform there, so
  the TV uses its full panel at full resolution instead of replaying a crop of
  the phone's viewport.

## How the framing stays consistent

The transform is never expressed in phone pixels. Instead:

1. Both ends first lay the image out **contained** in their own viewport —
   scaled to fit, centred. That is "scale 1, offset 0".
2. `scale` multiplies that base size.
3. `offsetX` / `offsetY` shift the image in multiples of the **base image
   width/height**, not of the viewport.

Because every quantity is relative to the image, a phone in portrait and a 16:9
TV agree on what the user framed while each one uses all of its own pixels. See
`ViewTransform.kt` and the `render()` function in `receiver/index.html` — the
maths is deliberately identical on both sides.

## Transport

Custom messages on the cast channel `urn:x-cast:com.bdaniel.imagecast`:

| Message | Direction | Payload |
| --- | --- | --- |
| `begin` | phone → TV | `gen`, `mime`, `w`, `h`, `chunks`, `bytes` |
| `chunk` | phone → TV | `gen`, `i`, `d` (base64 slice) |
| `end` | phone → TV | `gen` |
| `tf` | phone → TV | `s` (scale), `x`, `y` (normalised offsets) |
| `clear` | phone → TV | — |
| `status` | TV → phone | `state` = `ready` \| `error` |

The image is re-encoded to a maximum of 2560 px on its long edge and sent in
32 766-byte base64 chunks (cast messages are capped at 64 KB; the chunk size is
a multiple of 3 so the base64 pieces concatenate without padding seams). Each
chunk waits for its delivery acknowledgement before the next is sent, which is
what keeps a multi-megabyte transfer from overrunning the channel.

Sending the bytes over the cast channel — rather than serving them from an HTTP
server on the phone — deliberately avoids two common failure modes: receivers
run on an `https` origin and block mixed `http://` image loads, and phones on
guest/isolated Wi-Fi often cannot be reached by the TV at all.

Transform updates go through a conflated channel, so a fast gesture never
queues a backlog: the newest state is sent as soon as the channel is free and
the intermediate ones are dropped.

## Setup

### 1. Host the receiver

`receiver/index.html` is a CAF Custom Web Receiver. Put it on any HTTPS host
(GitHub Pages, Firebase Hosting, S3 + CloudFront, …).

### 2. Register it

1. Go to the [Google Cast SDK Developer Console](https://cast.google.com/publish)
   (a one-time US$5 registration fee applies).
2. **Add new application → Custom Receiver**, point it at your HTTPS URL, and
   copy the **Application ID**.
3. **Register the serial number** of your Chromecast for testing, then reboot
   the device. Unpublished receivers only launch on registered devices, and the
   registration can take up to 15 minutes to propagate.

### 3. Point the app at it

Edit `app/src/main/res/values/strings.xml`:

```xml
<string name="cast_receiver_app_id">YOUR_APP_ID_HERE</string>
```

This repository is already wired to a registered receiver:

| | |
| --- | --- |
| Application ID | `970FC601` |
| Receiver URL | https://shockrifle.github.io/image_cast/receiver/index.html |
| Status | Unpublished (launches only on authorised devices) |

The app ID is an identifier, not a credential — it is broadcast during cast
discovery and readable in any sender APK, so it is committed deliberately.
Forks should register their own receiver and replace it.

### 4. Build

A portable toolchain is already set up in `C:\workspace\android-toolchain`
(Temurin JDK 17, Gradle 8.9, Android SDK 35). Nothing is installed system-wide
and nothing was added to `PATH` or the registry — deleting that folder removes
all of it.

```powershell
.\build-local.ps1                 # assembleDebug
.\build-local.ps1 assembleRelease
.\build-local.ps1 installDebug    # to a connected device
```

To recreate the toolchain from scratch on another machine:

```powershell
.\setup-toolchain.ps1
```

That script downloads and unpacks all three pieces and accepts the
[Android SDK Terms](https://developer.android.com/studio/terms)
non-interactively, which is unavoidable for an unattended install.

Alternatively open the project in Android Studio (Ladybug or newer), or use the
checked-in wrapper with your own JDK 17 and SDK:

```bash
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

Requirements: JDK **17** (AGP 8.7 rejects newer JDKs), Android SDK 35, and a
device with Google Play services — the Cast SDK does not work on Play-less
emulators.

## Project layout

```
app/src/main/java/com/bdaniel/imagecast/
  MainActivity.kt        Compose host, warms up Cast discovery
  MainViewModel.kt       Image selection, transform state
  CastController.kt      Session handling, chunked upload, transform pump
  CastOptionsProvider.kt Receiver app ID, media notifications disabled
  ImageLoading.kt        Decoding, EXIF rotation, re-encoding for transfer
  ViewTransform.kt       The display-independent zoom/pan model
  ui/
    ImageCastScreen.kt   Top bar, status bar, empty state
    ZoomPanImage.kt      Gesture handling and local rendering
    CastButton.kt        MediaRouteButton bridged into Compose
receiver/index.html      The custom web receiver
```

## Notes and limits

- The picture sent to the TV is capped at 2560 px on its long edge
  (`ImageLoading.MAX_CAST_DIMENSION`). Raising it improves sharpness at deep
  zoom but lengthens the transfer; the cast channel is not fast.
- The local preview decodes at up to 4096 px to keep memory in check on very
  large photos; `android:largeHeap` is on for the same reason.
- The receiver keeps the session alive with `disableIdleTimeout`, since it never
  plays media and would otherwise be shut down as idle.
- Reconnecting (or resuming) a session re-uploads the current picture
  automatically — the receiver keeps no state across launches.

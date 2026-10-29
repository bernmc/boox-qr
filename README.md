# Boox QR

A QR code reader for Onyx Boox e-ink devices. No adverts, no tracking, no account, nothing sent anywhere.

The app is not allowed to use the internet at all. Android enforces this, so it cannot show adverts or send data even if someone changed their mind later. You can check this yourself: the source code is in this repository, and the app asks for only two things, the camera and the vibration motor.

Tested on the Boox Palma 2 (Android 13) and the Boox Tab Ultra C (Android 11). It should work on any Boox running Android 10 or newer that has a rear camera.

## What it does

- Point the camera at a QR code. The app reads it and shows what it says in large black text on white.
- If it is a web link, tap **Open** and it opens in your browser. Email addresses, phone numbers and map locations open in the matching app.
- **Copy** puts the text on the clipboard. **Share** sends it to another app.
- **From image** reads a QR code from a photo already on the device. Useful when the e-ink screen makes the camera hard to aim: take a photo with the normal camera app first, then pick it here.
- **Torch** turns the camera light on and off.
- **Scan again** goes back to the camera. **Close QR reader** quits the app.
- Wi-Fi QR codes show the network name and password so you can type them in.
- It also reads ordinary barcodes, the kind on products.

The screen is plain black and white with no animations, because that is what looks right on e-ink.

## Install

You need one file: **BooxQR-1.1.0.apk**. Download it from the [Releases page](https://github.com/bernmc/boox-qr/releases/latest). On a Boox you can download it straight from the browser, which puts it in the Download folder.

Then pick the first option below that works for you.

### Option 1: tap the file on the Boox

1. Open the file manager on the Boox (the app called **Storage** or **Files**).
2. Go to the **Download** folder and tap **BooxQR-1.1.0.apk**.
3. The first time, Android asks whether this source may install apps. Allow it, go back, and tap the file again.
4. If a message says the app is from an unknown developer, tap **Install anyway**. That message appears for any app that is not from the Play Store.
5. Tap **Install**. Boox QR now appears with your other apps.
6. Open it. It asks to use the camera. Choose **While using the app**.

### Option 2: send the file from a computer with BooxDrop

If you downloaded the file on a computer instead:

1. On the Boox, open **BooxDrop**. It shows a web address such as `http://192.168.1.23:8085`.
2. On the computer, type that address into a web browser. Drag the APK file onto the page.
3. Now follow Option 1 from step 1. The file is in the Download folder.

A USB cable works too: plug the Boox into the computer, choose **File transfer** on the Boox, copy the file into the Download folder, then follow Option 1.

### Option 3: when the Boox refuses to install it

If Options 1 and 2 end with a message that installing is blocked, your Google account is probably using Google's **Advanced Protection**. That setting stops every install that does not come from the Play Store, including this one.

You do not need to turn Advanced Protection off. There is another way in, using a computer and a small tool from Google called **adb**. It takes about ten minutes and does nothing to your other apps. If you are not comfortable with a terminal window, this is a good moment to ask someone who is.

1. On the computer, download **SDK Platform Tools** from Google: <https://developer.android.com/tools/releases/platform-tools>. Unzip it. The folder contains a program called `adb`.
2. Put **BooxQR-1.1.0.apk** in that same folder.
3. On the Boox: Settings, then **More Settings**, then switch **USB Debug Mode** on.
4. Plug the Boox into the computer with a USB cable. The Boox asks whether to allow USB debugging from this computer. Tick **Always allow** and tap **Allow**.
5. Open a terminal window in the unzipped folder and run:

   ```
   ./adb install BooxQR-1.1.0.apk
   ```

   On Windows, type `adb` instead of `./adb`. It should print `Success`.

6. Wait 30 seconds. Advanced Protection quietly switches the new app off right after it is installed. Then run:

   ```
   ./adb shell pm enable com.bernmc.booxqr
   ```

   Boox QR now appears with your other apps and stays there.

7. You can switch USB Debug Mode off again afterwards if you like.

To update to a newer version later, repeat steps 5 and 6 with the new file. Your settings and camera permission are kept.

## Tips for e-ink screens

- The camera picture will ghost and lag. That is the screen, not the reader. Hold the code steady inside the square for a second or two.
- If the picture is too hard to use, press and hold the Boox QR icon, choose **Optimise**, and set the refresh mode to **A2** or **X**.
- Or skip the live camera: take a photo of the code with the camera app, then use **From image**.

## Privacy

- Permissions: camera and vibration. Nothing else.
- No internet permission, so no adverts, no analytics, no updates checks, no crash reports.
- No scan history is kept. When you close the app, what you scanned is gone, except for anything you copied to the clipboard.

## Build it yourself

You need Java 17 or newer and the Android SDK (command-line tools with platform 35 and build-tools 35.0.0). The script looks for them in `JAVA_HOME` and `ANDROID_HOME`.

```
./build.sh
```

Without a signing keystore the script makes a debug build, which installs fine for personal use. To make a release build, copy `keystore.properties.example` to `keystore.properties` and fill in your own keystore details.

The code is Kotlin. It uses CameraX for the camera and ZXing for reading the codes. There are no other libraries.

## Licence

Apache 2.0. See the LICENSE file.

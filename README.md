## Image Understanding using Gemini on Android

This Android app captures an image and describes it using the Gemini API.

---

### Setup

#### 1. Requirements

- Android Studio
- Minimum SDK: **24**
- A Gemini API key from Google AI Studio

#### 2. Add Dependencies

Add OkHttp in `app/build.gradle`:

```gradle
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
```

#### 3. Insert Your API Key

Inside `MainActivity.java`:

```java
private static final String API_KEY = "YOUR_API_KEY_HERE";
```

#### 4. Internet Permission

Add this to `AndroidManifest.xml`:

```xml

<uses-permission android:name="android.permission.INTERNET" />
```

---

### Usage

#### 1. Run the App

Build and install the app on a device or emulator that has a camera.

#### 2. Capture an Image

Tap Capture to open the camera. Take a photo, and a small thumbnail will appear in the ImageView.

#### 3. Send to Gemini

Tap Gemini. The app will convert the image to Base64, build a JSON request, send the request to the
Gemini API, and receive the description

#### 4. View Results

The app will parse the description and output it in the TextView. Any errors (network issues, JSON
parsing problems) will be shown in Logcat.	
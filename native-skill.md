# Native Android Bridge integration Guide (`native-skill.md`)

Yeh documentation file aapko yeh samajhne me madad karegi ki WebView-based WebApp me Native Android capabilities (jaise Notifications, Camera, Location, Audio, Vibration aur Local Storage) kaise kaam karti hain, aur Chrome/Android backend background me inko kaise handle karta hai.

---

## 1. Background Notifications & JS Lifecycle (Auto-kill Problem)

### Sawaal: JS to background me auto-kill ho jata hai, tab notification kaise trigger ho sakti hai?
**Jawaab:** Aapka sochna bilkul sahi hai! Android devices battery aur memory save karne ke liye background apps aur unke WebView/JavaScript execution engine (such as V8) ko **pause or terminate** (auto-kill) kar dete hain. Agar aap standard JavaScript `setTimeout` ya HTML5 `Notification` (Service Workers ke bina) use karte hain, toh background me jane ke baad woh **kabhi execute nahi honge**.

### Solution: Native `AlarmManager` + `BroadcastReceiver` Approach!
Is problem ko solve karne ke liye hamara Java/Kotlin code Android core system ka use karta hai:

1. **Foreground Handshake:** Jab aapka app open aur active hota hai (Foreground), tab aapka JavaScript code native bridge function ko call karta hai:
   ```javascript
   window.Android.scheduleNotification(id, title, message, delayMs);
   ```
2. **Native Scheduling:** Android is request ko receive karke device ke **Native AlarmManager (system-level daemon)** ke pass register kar deta hai. Yeh alarm operating system level par set ho jata hai.
3. **App Closed/Killed:** Ab agar user app close kar deta hai ya system WebView runtime ko taskmanager se clear/kill kar deta hai, tab bhi **Android OS alarm state ko yaad rakhta hai**.
4. **Alarm Triggered:** Jab `delayMs` complete hota hai, tab Android OS ek native `Intent` broad-cast karta hai.
5. **No JS Required:** Android system is intent ko hamare app ke `NotificationReceiver.kt` class (jo ek `BroadcastReceiver` hai) me bhejta hai. Yeh background receiver bina pure WebView ya pure app UI ko open kiye **ekdam lightweight thread me wake up** hota hai aur native Notification Manager push alert show kar deta hai.

### Notification API Table (JavaScript Usage)
```javascript
// Schedule a notification to appear after 5 seconds (5000 milliseconds)
window.Android.scheduleNotification(
    101,                         // Notification ID (Unique Integer)
    "Hello User!",               // Title
    "This was scheduled in JS!", // Message
    5000                         // Delay in milliseconds
);
```

---

## 2. Storage Solution: Direct Web-storage vs. Native Calls

### Sawaal: Storage use karne ke liye native call karna hoga ya direct standard web storage kaam karega?
**Jawaab:** Aap **direct Standard Web APIs (databases)** use kar sakte hain! Kisi native call ki koi zaroorat nahi hai.

### Kyun aur Kaise?
Android WebView settings me niche diye gaye parameters set hain:
```kotlin
val settings = webView.settings
settings.javaScriptEnabled = true
settings.domStorageEnabled = true // Enable LocalStorage & SessionStorage
settings.allowFileAccess = true   // Access assets
```
Iska matlab hai ki aapka standard HTML5 browser APIs perfectly supported hain:
- **`localStorage`**: Key-value data ko permanent store karne ke liye (Best for theme selection, auth tokens, list items, simple preferences).
- **`IndexedDB`**: Complex, structured, offline offline data or local databases ke liye.
- **`sessionStorage`**: Tab-specific temporary session indicators ke liye.

#### JS Code Example (Storage works out-of-the-box):
```javascript
// Saving data
localStorage.setItem("username", "Samdani");
localStorage.setItem("appTheme", "dark-cosmic");

// Loading data smoothly on startup
const storedUser = localStorage.getItem("username") || "Guest";
```

*Note: Kuch high-security scenario me agar custom file system storage, downloads, ya standard gallery folder permissions ki require ho, tab WebApp direct Android MediaStore or Document Provider utilize karta hai jinki permissions hamne setup kar di hain.*

---

## 3. WebApp Native Bridge APIs Complete Reference

Aapka WebApp code WebView ke andar pure Web and Android integration ke liye niche diye gaye features dynamically interact kar sakta hai:

### 1. Show Toast Alert
Native message feedback toast dikhane ke liye.
- **JS Function:** `window.Android.showToast(messageString)`
- **Example:**
```javascript
window.Android.showToast("Progress successfully saved offline!");
```

### 2. Device Vibration
Dual-state physical vibration feedback capture.
- **JS Function:** `window.Android.vibrate(durationMilliseconds)`
- **Example:**
```javascript
// Vibro feedback on action click
window.Android.vibrate(150); // 150ms vibration
```

### 3. Native Location (GPS)
Location retrieval directly coordinates formatted in structured JSON object.
- **JS Function:** `window.Android.fetchLocation()`
- **Return Type:** Structured JSON string
- **Example:**
```javascript
try {
    const locationJsonStr = window.Android.fetchLocation();
    const locationData = JSON.parse(locationJsonStr);
    console.log("Lat:", locationData.latitude, "Lon:", locationData.longitude);
} catch (e) {
    console.error("Error fetching location:", e);
}
```

### 4. Native Camera Capture
Camera open karke immediate picture capture state push karna.
- **JS Function:** `window.Android.takePicture()`
- **Example:**
```javascript
// Captures and initiates standard device gallery callback inside app
window.Android.takePicture();
```

### 5. Play Audio Ringtones/Sounds
Physical sound elements ring without external bulky audio player scripts loading down web speeds.
- **JS Function:** `window.Android.playAudio(soundTypeString)`
- **Supported types:** `"bell"`, `"beep"`, `"alert"`, `"success"`
- **Example:**
```javascript
window.Android.playAudio("success"); // Success beep tone play
```

---

## 4. Perfect Permissions Best Practice Table (Manifest sync status)

Aapke binary compile me niche di gayi permissions configuration automatic process/ready ho gayi hain:

| Feature | Manifest Permission required | Automatic JS fallback request |
|---|---|---|
| **Audio Alert / Camera** | `android.permission.CAMERA`, `RECORD_AUDIO` | Yes, triggers system prompt dynamically |
| **Notifications** | `android.permission.POST_NOTIFICATIONS` | Prompted directly on launch |
| **Precise Location** | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Seamless browser geolocation prompt overlay |
| **Media/Gallery File Pick** | `READ_MEDIA_IMAGES` / `WRITE_EXTERNAL_STORAGE` | Auto handles standard Android `onShowFileChooser` dialog |

---

## 5. Automatic Interception: Zero-Bridge API Magic & The Honest Truth

**Sawaal:** Kya koi aisa tariqa nahi hai ki baar baar bridge (`window.Android...`) ko manually call karne ki zarurat na pade, balki code aur permission automatically native trigger ho jaye?

**Jawaab:** **Ji haan! Bilkul hai!** 

Standard Geolocation, WebRTC Camera/Mic access, aur File pickers ke liye standard Web APIs automatically background wrapper me direct translate aur request ho sakti hain. Lekin isme do bahut mahatvapurna **rules and limitations** hain jo aapko samajhna zaroori hai:

### ⚠️ IMPORTANT REQUIREMENT: Dynamic Switches in Workspace
Android OS security policies ke according, jab tak koi permission compile-time par aapke **`AndroidManifest.xml`** file me declared nahi hogi, tab tak Android OS kisi bhi runtime window or pop-up ko display karne se strictly block kar dega.
* **Problem:** Agar aapne App settings generate/build karte waqt **VoltWeb Workspace UI screen** par dynamic switches ko ON nahi kiya (jaise *"Camera Stream photo uploads"*, *"Vibrate feedback"*, etc.), to background engine Manifest me un permissions ko link hi nahi karega. Natija yeh hoga ki generated app me koi permission nahi dikhegi aur saare standard browser tests crash/fail ho jayenge!
* **Solution:** Jab bhi aap project build ya custom zip extract karein, tab generator UI me use ono wali permissions ke toggles ko select/check karna ** अनिवार्य (Mandatory) ** hai.

---

### 1. Automatic Camera & Microphone (via WebRTC standard)
* **Standard Web Code:**
  ```javascript
  // Standard Browser API (works out of the box in your webapp)
  navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    .then(stream => { videoElement.srcObject = stream; })
    .catch(err => { console.error("Camera access denied!", err); });
  ```
* **How it Automatically Detects:** Jab aapka frontend is API ko hit karega, hamara Android system hook `onPermissionRequest(request)` isko catch karega. Agar aapne build config me Camera select kiya tha, to Android directly native **CAMERA** aur **RECORD_AUDIO** permission popup generate karega aur standard camera stream secure web context variable ko pass kar dega!

---

### 2. Automatic Image Gallery & File Upload (via `<input>`)
* **Standard Web Code:**
  ```html
  <!-- Regular HTML file input element -->
  <input type="file" accept="image/*" id="gallerySelector">
  ```
* **How it Automatically Detects:** Jaise hi user click karega, WebView standard link behavior abort kar ke Android native system ko wake up karega (`onShowFileChooser`). Yeh directly elegant **System Gallery and File Selector BottomSheet** load kar dega! User click select karne par return file path directly standard `input.files` API me deliver kar dega.

---

### 3. Automatic GPS Tracker & Location mapping (Standard Geolocation) — NOW AUTOMATIC!
* **Standard Web Code:**
  ```javascript
  // Regular Browser API
  navigator.geolocation.getCurrentPosition((position) => {
      console.log("Latitude:", position.coords.latitude);
      console.log("Longitude:", position.coords.longitude);
  });
  ```
* **How it Automatically Detects:** Pehle WebView me `navigator.geolocation` direct fail ho raha tha kyunki system location prompt on-the-fly request nahi karta tha. **Humne abhi core update kar diya hai!** Ab jaise hi page standard Geolocation invoke karega, wrapper automatically device level runtime GPS permission dialog trigger karega, aur allow hote hi coordinate data standard JS variable me load ho jayega!

---

### 4. Vibration Limitations (No Auto-Detect)
* **Standard Web Code:** `navigator.vibrate(value)`
* **Honest Truth:** Standard `navigator.vibrate` high security WebViews me block hota hai, aur permissions bypass nahi kar pata. Vibration ke liye aapko **strictly hamara Native Bridge method `window.Android.vibrate(...)` call karna padega**. Yeh auto-detect nahi ho sakta.

---

## 6. Launcher Logo Rendering Solution

Android Oreo (API 26+) aur higher operating systems par custom logo dynamic build package me normal directory se display nahi hota kyunki modern phones Adaptive Icons (`res/mipmap-anydpi-v26`) demand karte hain.
Hum ne generator engine me isko update kar diya hai:
1. **Dynamic scaling:** Jab aap koi custom image upload karenge, generator aapki image ko copy karke proper density directories (`mipmap-mdpi`, `mipmap-hdpi`, `mipmap-xhdpi`, `mipmap-xxhdpi`, `mipmap-xxxhdpi`) dono normal aur round variations me ready karega taaki direct desktop screen app logo visible ho sake!
2. **Fallback layout:** Agar image specified nahi hai, to unique vector design wrapper automated system built layout display karega.

Aap in standard codes ko bina kisi modification ke use kar sakte hain! Yeh fully zero-bridge automatic detection hai.

Aap in methods ko bina kisi specialized setup ke custom index.html or visual JS frameworks (Vue, React, Svelte, Angular, standard dynamic scripts) ke sath use kar sakte hain.

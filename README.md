# 🛡️ ClipGuard

**ClipGuard** is a native, 100% Kotlin Android app built with Jetpack Compose and **Miuix** for **MIUI / HyperOS** (and Android 8.0+ devices) to toggle clipboard read access (`android:read_clipboard` app-op) for every installed user and system application via **Shizuku**.

---

## 📥 Download APK

- **[Download Latest ClipGuard.apk from Releases](../../releases/latest)** *(click **ClipGuard.apk** under the **Assets** section on the Releases page)*

---

## ✨ Features

- **Per-App Clipboard Control:** Toggle `READ_CLIPBOARD` (`MODE_ALLOWED` / `MODE_IGNORED`) for both user and system apps.
- **Pure Shizuku Binder Wrapper (No Root Required):** Uses `ShizukuBinderWrapper` around the system `appops` service (`IAppOpsService`) with `HiddenApiBypass`—no AIDL, no background daemon.
- **Optimistic UI & Safe Rollback:** Toggles respond immediately and automatically roll back with an error toast if a command fails.
- **Batch Operations:** **Allow all** or **Block all** apps in the currently filtered view with confirmation and live progress tracking.
- **Search & Smart Filters:** Filter by **All**, **User**, **System**, or **Blocked**, and search by app name or package name.
- **HyperOS Design & True AMOLED Dark Mode:** Built with `top.yukonga.miuix.kmp:miuix-android` featuring a pure `#000000` AMOLED dark theme and `#0D0D0D` cards.

---

## 🚀 Setup & Usage Guide

### 1. Install Shizuku
Download and install **Shizuku** from Google Play or the [official Shizuku website](https://shizuku.rikka.app/download/).

### 2. Start Shizuku via Wireless Debugging
1. Enable **Developer options** on your device (*Settings → About phone → tap OS Version 7 times*).
2. Go to *Settings → Additional settings → Developer options* and enable:
   - **USB debugging**
   - **USB debugging (Security settings)** *(mandatory on MIUI / HyperOS to modify App-Ops)*
   - **Wireless debugging**
3. Pair **Shizuku** using the 6-digit Wireless Debugging pairing code and tap **Start**.

### 3. Grant Permission & Use ClipGuard
1. Open **ClipGuard** and tap **Grant Permission** on the top Shizuku banner.
2. Toggle any app switch (**ON** = Allowed, **OFF** = Blocked), or use the top-right menu for **Allow all** / **Block all**.

---

## 🛠️ Tech Stack & Requirements

- **Language:** 100% Kotlin
- **UI Framework:** Jetpack Compose + Miuix (`top.yukonga.miuix.kmp:miuix-android`)
- **System Integration:** `dev.rikka.shizuku:api`, `dev.rikka.shizuku:provider`, `org.lsposed.hiddenapibypass:hiddenapibypass`
- **SDK Support:** `minSdk 26` (Android 8.0) – `targetSdk 34` (Android 14+)

---

If you find **ClipGuard** useful, please consider giving this repository a ⭐!

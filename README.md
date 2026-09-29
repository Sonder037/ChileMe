<div align="center">

# ChileMe

Track your meals and daily calories with photos and chat.

![Android 9+](https://img.shields.io/badge/Android-9%2B-5865F2?style=flat-square)
![AI Native](https://img.shields.io/badge/AI-Native-8970D5?style=flat-square)
[![License: AGPL-3.0-only](https://img.shields.io/badge/License-AGPL--3.0--only-5865F2?style=flat-square)](LICENSE)

English · [简体中文](README.zh-CN.md)

</div>

## Features

- **AI Native**: Log meals and exercise through photos or chat. Edit meal cards and confirm them before saving.
- **Local storage**: No app account required. Meal logs, body measurements, and conversations stay on your device.
- **Lightweight**: The release APK is about 4 MB. Chat history and meal lists load in batches as you browse.
- **Your choice of model**: Bring your own API key for a Chat Completions-compatible service. Configure chat and image models separately.

<p align="center">
  <img src="assets/screenshots/chat.png" width="23%" alt="Chat and meal cards" />
  <img src="assets/screenshots/records.png" width="23%" alt="Food and activity records" />
  <img src="assets/screenshots/week.png" width="23%" alt="Weekly calorie balance" />
  <img src="assets/screenshots/calendar.png" width="23%" alt="Monthly calorie calendar" />
</p>

<p align="center"><sub>Screenshots use demo data. The app interface is currently in Chinese.</sub></p>

## Get started

1. [Download the 1.0.0 release APK](https://github.com/Sonder037/ChileMe/releases/download/v1.0.0/chileme-1.0.0-release.apk) and install it, or build it using the instructions below. Requires Android 9 or later.
2. Choose your sex, age, height, weight, and usual activity level, then enter your API key.
3. Take a photo of your food or describe a meal in chat. Check the foods and portions on the card, then confirm to save it.

**DeepSeek Flash** is the recommended model. The default endpoint is `https://api.deepseek.com`, with `deepseek-flash` prefilled as the model name. Change the name in settings if your provider uses a different one. Photo recognition requires a model that accepts images.

When you use AI, messages, photos, and relevant records are sent to your chosen provider. API charges are set by that provider. Uninstalling the app or clearing its data deletes local records; export and restore are not yet available.

## Build and verify

Open the project in Android Studio with JDK 17 or later and Android SDK 36.1.

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

## License

© 2026 Sonder037 · [AGPL-3.0-only](LICENSE). Commercial use is allowed, subject to the license's corresponding-source requirements for distribution and modified network services. Third-party components retain their own licenses.

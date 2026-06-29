# Qinglv Health Manager

[![Android CI](https://github.com/gjing8865-a11y/health-manage-demo/actions/workflows/android-ci.yml/badge.svg)](https://github.com/gjing8865-a11y/health-manage-demo/actions/workflows/android-ci.yml)

Qinglv Health Manager is an Android health-management app built with Kotlin and Jetpack Compose. It combines local health records, diet recognition, outdoor exercise tracking, weather sync, and STM32 smart-device data into one mobile experience.

The project is being polished as a portfolio-grade Android project: first with reliable GitHub setup and documentation, then with tests, CI, architecture refactoring, demo mode, and stronger privacy/security handling.

## Highlights

- Account registration, login, auto-login, and local profile data.
- Health dashboard with weekly step report, exercise summary, heart-rate warning, and device sync state.
- Diet logging with image-based AI food recognition, calorie and macro statistics.
- Sleep analysis based on heart rate, blood oxygen, and step signals from STM32 hardware.
- Outdoor running flow with countdown, live tracking, distance, duration, and calorie summary.
- STM32 device connection over Wi-Fi hotspot and TCP data channel.
- Hardware-free demo mode that simulates STM32 vitals, weekly steps, battery, and sleep data.
- Weather lookup and sync-to-device flow.
- Local persistence with Room, Kotlin coroutines, Flow, and SharedPreferences.

## Tech Stack

- Language: Kotlin
- UI: Jetpack Compose, Material 3, Navigation Compose
- Persistence: Room, SharedPreferences
- Async: Kotlin Coroutines, StateFlow
- Network: OkHttp, Gson
- Location and maps: Google Play Services Location, AMap SDK
- Device integration: Wi-Fi hotspot scanning/connection, TCP socket, Nordic BLE dependency
- Image loading: Coil
- Build: Gradle Kotlin DSL, Android Gradle Plugin, KSP

## Project Structure

```text
app/src/main/java/com/example/healthmanager
+-- MainActivity.kt                 # App entry and navigation
+-- viewmodel.kt                    # Current app state and business orchestration
+-- database/                       # Room database and DAO definitions
+-- model/                          # Room entities
+-- ui/screens/                     # Compose feature screens
+-- ui/theme/                       # Compose theme
`-- Manage/                         # Device-related helpers
```

The current code works, but `viewmodel.kt` is intentionally listed as a refactor target. The next architecture step is to split it into feature ViewModels, repositories, local/remote data sources, and use cases.

For a fuller technical overview, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Getting Started

1. Clone the repository.
2. Open the project with Android Studio.
3. Copy `local.properties.example` to `local.properties`.
4. Let Android Studio fill `sdk.dir`, then optionally add API keys:

```properties
ZHIPU_API_KEY=
WEATHER_API_KEY=
AMAP_API_KEY=
```

5. Build the debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

6. Run the local unit-test smoke check:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

If the Gradle test worker fails on Windows under a non-ASCII project path, run the project from an ASCII path or directory junction. The GitHub Actions workflow runs from an ASCII Linux path.

The app can run without API keys for local data flows. AI food recognition, weather lookup, and map/provider-specific features require the corresponding keys.

## Hardware Demo

The device module expects an STM32 smart-device hotspot and a TCP endpoint, defaulting to:

```text
tcp://192.168.4.1:8080
```

For portfolio review, the device screen also includes a demo mode that simulates STM32 heart rate, blood oxygen, step, sleep, weekly-step, and battery data. Reviewers can experience the full health-data loop without physical hardware.

## Roadmap

- [x] Compose multi-screen health manager prototype.
- [x] Local Room persistence for users, notes, food, sleep, weekly steps, and exercise records.
- [x] STM32 Wi-Fi/TCP device data integration.
- [ ] GitHub repository polish: README, ignore rules, clean initial commit, screenshots.
- [x] Fix unit-test task and add GitHub Actions CI.
- [x] Add architecture notes for portfolio review.
- [ ] Split the large ViewModel into feature ViewModels, repositories, and data sources.
- [x] Add demo mode for hardware-free review.
- [x] Remove legacy storage permissions and restrict cleartext traffic to the STM32 device IP.
- [ ] Replace destructive Room migrations with explicit migrations.
- [ ] Harden release security: backup policy, API key handling, release signing, and privacy notes.

## Privacy And Security Notes

This app handles health-related data. API keys and local machine paths must stay in `local.properties`, which is ignored by Git. Build outputs and APKs are also ignored because generated artifacts can contain compiled constants.

Before publishing a public portfolio repository, rotate any API keys that were previously used in local builds.

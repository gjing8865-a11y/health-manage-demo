# Architecture Notes

This document explains how Qinglv Health Manager is organized today and what the
next portfolio-grade refactor should move toward.

## Product Scope

Qinglv Health Manager combines several health workflows in one Android app:

- account login and local profile persistence
- dashboard summaries for steps, exercise, heart-rate warning, and device status
- food logging with image-based recognition and calorie/macronutrient summaries
- outdoor exercise tracking with countdown, live metrics, and result records
- sleep analysis based on wearable/device signals
- weather lookup and device sync
- STM32 smart-device data over Wi-Fi/TCP, plus a hardware-free demo mode

## Current Implementation

The app is a Kotlin Android application using Jetpack Compose and Material 3.
`MainActivity` owns the Compose navigation graph and screens are split under
`ui/screens`.

```text
MainActivity
  -> Compose navigation destinations
  -> AppViewModel
       -> Room DAOs
       -> SharedPreferences
       -> OkHttp / Gson weather and food APIs
       -> WifiDeviceManager and TCP socket device channel
       -> StateFlow UI state
  -> Feature screens render state and send user events
```

The current implementation intentionally favors a working end-to-end prototype:
one central `AppViewModel` orchestrates local data, network calls, permissions,
device connectivity, and screen state. This makes the demo easy to follow, but
also makes the ViewModel too large for long-term maintenance.

## Data And State

Local persistence uses Room:

- `UserDao` for account data
- `NoteDao` for health notes
- `FoodDao` for food records
- `WeeklyStepDao` for weekly step snapshots
- `SleepDao` for sleep records
- `ExerciseDao` for exercise history

Runtime UI state uses `StateFlow`. Reusable UI state models live in
`viewmodel/UiStateModels.kt`, including pending food items, weather state,
sleep trend points, and demo device payload presentation data.

## Device Flow

The device module supports two reviewer paths:

```text
Real hardware path:
DeviceScreen -> scan/connect Wi-Fi hotspot -> bind network -> TCP endpoint
             -> parse STM32 payload -> update dashboard, sleep, steps, vitals

Demo path:
DeviceScreen -> startDemoDeviceMode()
             -> generated STM32-like payload
             -> same dashboard, sleep, steps, vitals UI state
```

The demo path is important for portfolio review because a recruiter or engineer
can inspect the full health-data loop without owning the STM32 device.

## Target Architecture

The next refactor should split orchestration by responsibility:

```text
ui/screens
  -> feature ViewModels
domain
  -> use cases and validation
data
  -> repositories
  -> Room data sources
  -> remote API data sources
device
  -> Wi-Fi/TCP connection
  -> payload parser
  -> demo data source
```

Recommended first cuts:

1. Move Room access behind repositories such as `FoodRepository`,
   `SleepRepository`, `ExerciseRepository`, and `UserRepository`.
2. Move weather and food-recognition API calls into remote data sources.
3. Extract STM32 payload parsing and demo payload generation from
   `AppViewModel`.
4. Split screen-specific state into smaller ViewModels after repositories exist.
5. Add focused unit tests around repositories, parsers, and data mapping.

## Known Technical Debt

- `AppViewModel` still owns too many responsibilities.
- Room currently uses destructive migrations and does not export schemas.
- Release builds still need stricter backup policy, release signing, and privacy notes.
- Some legacy comments contain encoding artifacts and should be cleaned.
- README screenshots still need to be captured from a stable demo build.

## Verification

The repository includes GitHub Actions for:

- `:app:testDebugUnitTest`
- `:app:assembleDebug`

Local Windows builds should be run from an ASCII-only path if Gradle test workers
fail under a non-ASCII project directory.

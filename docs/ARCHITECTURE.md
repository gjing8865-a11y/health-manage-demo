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
  -> MainViewModel
       -> repositories
       -> Room DAOs
       -> SharedPreferences
       -> remote data sources for weather and food recognition
       -> platform adapters for Wi-Fi, vibration, and geocoding
       -> STM32 device session for TCP/HTTP payload streaming
       -> StateFlow UI state
  -> Feature screens render state and send user events
```

The current implementation intentionally favors a working end-to-end prototype:
one central `MainViewModel` orchestrates network calls, permissions, device
connectivity, and screen state. Local persistence now sits behind a thin
repository layer, which makes the next ViewModel split safer and easier to test.
Food-recognition and weather HTTP calls also sit behind remote data sources. The
ViewModel is still too large for long-term maintenance, but data access is no
longer wired directly to DAOs, provider behavior is testable, Android system API
compatibility is isolated behind platform adapters, and pure domain calculations
have started moving out of screen orchestration.
Food-recognition prompt construction is also isolated from the ViewModel so the
AI request contract can be reviewed and tested without running the app.
Food-recognition result mapping is isolated as well, including scene type
normalization, candidate food parsing, drink checks, and nutrition fallback
rules. Weather response mapping is also isolated so empty, province-level, or
placeholder API payloads are filtered outside screen orchestration.
The STM32 data channel is also isolated in `device/Stm32DeviceSession.kt`, so
TCP/HTTP transport, writable socket access, and JSON stream framing no longer
live directly in the ViewModel.

## Data And State

Local persistence uses Room:

- `UserDao` for account data
- `NoteDao` for health notes
- `FoodDao` for food records
- `WeeklyStepDao` for weekly step snapshots
- `SleepDao` for sleep records
- `ExerciseDao` for exercise history

Runtime UI state uses `StateFlow`. Reusable ViewModel presentation models live
in `viewmodel/UiStateModels.kt`, including pending food items and sleep trend
points. Remote API-shaped UI payloads, such as weather state, live beside their
response mappers under `data/remote/`.

Android-specific platform integration now lives under `platform/`:

- `WifiPlatformGateway` wraps Wi-Fi permissions, scan results, process network
  binding, DHCP gateway discovery, and network callbacks.
- `HealthVibrationController` wraps heart-rate alert vibration across Android
  API levels.
- `AndroidWeatherLocationResolver` wraps `Geocoder` and delegates city
  normalization to the domain resolver.

Pure domain logic now lives under `domain/`:

- sleep estimation from heart rate, blood oxygen, and step samples
- sleep presentation mapping for trend labels, stage-point parsing, and score
  advice
- shared sleep hardware detail model used by device parsing and domain logic
- latest/weekly exercise summary calculation
- Monday-started weekly date range calculation for reports and exercise stats
- food calorie and macronutrient statistics
- sustained high-heart-rate alert policy
- weather location candidate resolution

Remote API shaping lives under `data/remote/`:

- `FoodRecognitionPromptBuilder` defines the multi-step recognition prompts.
- `FoodRecognitionRemoteDataSource` owns the AI HTTP request and model fallback.
- `FoodRecognitionResultMapper` parses and sanitizes model output, applies
  drink/coverage review rules, and fills conservative nutrition estimates.
- `WeatherRemoteDataSource` owns weather HTTP requests.
- `WeatherStateMapper` parses weather payloads, applies invalid-payload guards,
  and creates weather UI state for the screen.

STM32 device transport now lives under `device/`:

- `Stm32DeviceSession` owns TCP/HTTP payload streaming and note writes.
- `Stm32NotePayloadBuilder` owns the line-delimited JSON note payload sent to
  the device.
- `Stm32ConnectionMessageFormatter` turns low-level TCP/HTTP failures into
  reviewer-friendly troubleshooting messages.
- `Stm32WifiScanSummary` explains Wi-Fi scan results and reuses the shared
  STM32 hotspot-name policy.
- `Stm32JsonStreamExtractor` extracts complete JSON objects from TCP frames.
- `Stm32PayloadParser`, `Stm32EndpointResolver`, and
  `Stm32DemoPayloadFactory` keep payload parsing, endpoint discovery, and demo
  data generation testable.

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
  -> use cases, validation, and health-summary calculations
data
  -> repositories
  -> Room data sources
  -> remote API data sources
  -> remote response mappers
device
  -> STM32 TCP/HTTP session
  -> STM32 payload parser
  -> STM32 endpoint resolver
  -> STM32 JSON stream extractor
  -> deterministic demo payload factory
platform
  -> Android Wi-Fi, geocoder, and vibration adapters
```

Recommended next cuts:

1. Split screen-specific state into smaller ViewModels after repositories exist.
2. Move STM32 connection state callbacks into a dedicated coordinator.
3. Add focused unit tests around repositories and data mapping.

## Known Technical Debt

- `MainViewModel` still owns too many responsibilities.
- Sleep estimation, sleep presentation mapping, exercise, weekly date ranges, food-summary, heart-rate alert, weather-location calculations, weather response mapping, food-recognition mapping, Android platform API wrappers, and STM32 transport have been extracted, but screen-level state still needs feature ViewModels.
- Room schema export is enabled; historical migrations before v9 still need source schema history if upgrade support is required.
- Release builds still need production API-key handling, release signing, and privacy notes.
- A short demo GIF or video would make the README even easier to scan.

## Verification

The repository includes GitHub Actions for:

- `:app:testDebugUnitTest`
- `:app:assembleDebug`

Local Windows builds should be run from an ASCII-only path if Gradle test workers
fail under a non-ASCII project directory.

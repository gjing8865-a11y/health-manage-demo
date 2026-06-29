# Portfolio Review Guide

This guide is written for recruiters and interviewers who want to understand the
project quickly without owning the STM32 hardware.

## What To Look At First

1. `README.md` for the product scope, tech stack, setup, and roadmap.
2. `docs/SCREENSHOTS.md` for emulator screenshots of the core review flow.
3. `docs/ARCHITECTURE.md` for the current architecture and refactor direction.
4. `docs/SECURITY_PRIVACY.md` for health-data, API-key, backup, and release
   security notes.
5. `app/src/main/java/com/example/healthmanager/device/` for the extracted
   STM32 parser and demo data factory.
6. `app/src/main/java/com/example/healthmanager/data/remote/` for API data
   sources.
7. `app/src/main/java/com/example/healthmanager/domain/` for extracted domain
   logic.
8. `app/src/test/java/com/example/healthmanager/` for unit tests covering
   device parsing, demo data, remote data sources, and sleep estimation.
9. GitHub Actions for build and unit-test evidence on every push.

## Reviewer Demo Path

The physical device path expects an STM32 hotspot and TCP endpoint:

```text
tcp://192.168.4.1:8080
```

For hardware-free review, open the device screen and use the demo data action.
It simulates:

- heart rate
- blood oxygen
- step count
- weekly step trend
- battery level
- sleep score and sleep details

This lets reviewers inspect the same dashboard and health-data flow without
connecting physical hardware.

## Engineering Signals

The repository now demonstrates more than a small UI demo:

- Compose multi-screen Android app with local persistence.
- Room-backed health records with a repository layer in front of DAOs.
- Wi-Fi/TCP device integration with a testable STM32 payload parser.
- Hardware-free demo mode with deterministic factory tests.
- Food and weather API calls extracted into remote data sources with tests.
- Sleep-estimation logic extracted into a domain component with tests.
- Exercise summary and food nutrition statistics extracted into domain
  calculators with tests.
- GitHub Actions CI for debug build and unit tests.
- Dependabot configuration for Gradle and GitHub Actions dependency updates.
- Emulator screenshots documenting the dashboard, STM32 demo mode, and sleep
  analysis flow.
- Public-repo hygiene: ignored local secrets, sample local properties, and docs.
- Security cleanup: legacy storage permissions removed and cleartext traffic
  restricted to the STM32 local device IP; Android backup/transfer disabled for
  local health data.
- Public privacy/security notes covering local health data, API keys, backup,
  permissions, and production release steps.

## Remaining Improvements

The project is intentionally still being improved. High-value next steps:

- split `AppViewModel` into feature ViewModels
- add historical Room migrations if pre-v9 upgrade support is required
- record a short demo GIF or video for the README
- add production release signing and API-key handling notes

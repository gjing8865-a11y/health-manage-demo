# Portfolio Review Guide

This guide is written for recruiters and interviewers who want to understand the
project quickly without owning the STM32 hardware.

## What To Look At First

1. `README.md` for the product scope, tech stack, setup, and roadmap.
2. `docs/ARCHITECTURE.md` for the current architecture and refactor direction.
3. `app/src/main/java/com/example/healthmanager/device/` for the extracted
   STM32 parser and demo data factory.
4. `app/src/test/java/com/example/healthmanager/device/` for unit tests covering
   real device payload parsing and deterministic demo data.
5. GitHub Actions for build and unit-test evidence on every push.

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
- GitHub Actions CI for debug build and unit tests.
- Public-repo hygiene: ignored local secrets, sample local properties, and docs.
- Security cleanup: legacy storage permissions removed and cleartext traffic
  restricted to the STM32 local device IP.

## Remaining Improvements

The project is intentionally still being improved. High-value next steps:

- split `AppViewModel` into feature ViewModels
- move weather and food-recognition calls into remote data sources
- add explicit Room migrations and schema exports
- capture stable screenshots or a short demo GIF
- add privacy notes for health-related local data

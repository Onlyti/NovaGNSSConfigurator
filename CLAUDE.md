# NovA GNSS Configurator — agent 진입점

스마트폰을 NovAtel OEM7 수신기의 시리얼 터미널·설정 GUI 로 만드는 Android 앱.
"휴대폰 NovAtel Connect". 비상업 개인 사이드 프로젝트. rtk-router 의 형제 앱.
NovAtel ASCII 명령 전용 (u-blox config 는 UBX 바이너리라 대상 외).

## 작업 전 읽기
1. README.md — v0.1 범위 · 로드맵 · 빌드
2. 형제: ~/git/rtk-router (같은 Kotlin/Compose 스택, SerialLink 공유 원본)

## 스택 / 핵심
- Android Native Kotlin (Jetpack Compose), package com.onlyti.novagnss
- USB Host serial: usb-serial-for-android 3.10.0 (CDC-ACM / FTDI / CP210x / CH340 / PL2303)
- v0.1 은 ViewModel 이 serial 직접 관리(foreground service 없음 — 설정은 interactive 작업)

## 구조
- serial/SerialLink.kt — USB serial 연결·송수신(rtk-router 와 동일 패턴)
- config/Commands.kt — 기본 NovAtel 명령 팔레트
- ui/ConsoleViewModel.kt — serial 세션·콘솔 버퍼·명령 전송
- ui/MainActivity.kt — 터미널 UI
- ui/Prefs.kt — baud/port/팔레트 영속

## 빌드
- GitHub Actions(android.yml) 또는 ./gradlew assembleDebug. 로컬 SDK 없으면 CI 의존.

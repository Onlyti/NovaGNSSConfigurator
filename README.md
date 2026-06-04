# NovA GNSS Configurator

스마트폰을 NovAtel OEM7 GNSS 수신기의 **시리얼 터미널/설정 도구**로 만드는 Android 앱.
USB-C OTG serial(또는 USB-RS232/USB-TTL 어댑터)로 수신기에 연결해 NovAtel ASCII 명령을
보내고 응답을 콘솔로 본다. "휴대폰 안의 NovAtel Connect". rtk-router 와 serial 코드 공유.

비상업 개인 도구. NovAtel 은 등록상표이며 본 앱은 호환 유틸리티일 뿐 제휴/공식 아님.
(u-blox 는 설정이 UBX 바이너리라 본 앱 대상 외 — NovAtel ASCII 전용.)

## v0.1 (foundation)
- USB serial 연결 (CDC-ACM / FTDI / CP210x / CH340 / PL2303, usb-serial-for-android)
- 터미널 콘솔: ASCII 명령 송신(TX echo) + 응답 라인(RX) 표시, monospace
- 명령 팔레트: 자주 쓰는 NovAtel 명령(VERSION/RXSTATUS/BESTPOS/INTERFACEMODE/SAVECONFIG ...),
  탭=송신 / 롱프레스=삭제, 사용자 추가 가능(영속)
- baud / USB port index 선택

## 로드맵
- 구조화 카드: VERSION / RXSTATUS / BESTPOS / SATVIS 등 응답 파싱해 보기좋게
- 설정 마법사: INTERFACEMODE / LOG / SERIALCONFIG 원클릭 프로파일
- 로그 저장/내보내기

## 빌드
- APK: GitHub Actions(push 시 자동) 또는 `./gradlew assembleDebug`
- 스택: Kotlin/Compose, minSdk 26, usb-serial-for-android 3.10.0, package com.onlyti.novagnss

## 주의
- NovAtel enclosure COM 포트는 RS-232 → USB-RS232 어댑터 필요. baud 일치 필수.
- 명령은 ASCII. 응답도 `LOG ...A`(ASCII) 변형 사용해 사람이 읽기 좋게.

# NovA GNSS Configurator 핸드오프

작성 2026-06-05. 세션 라이프사이클 규율(6항목).

## 1. 목표

스마트폰을 NovAtel OEM7 수신기의 시리얼 터미널·SPAN 설정 도구로. "휴대폰 NovAtel Connect".
비상업 개인 사이드. NovAtel ASCII 명령 전용(u-blox config 는 UBX 바이너리라 대상 외 — 보류).

## 2. 현재 상태

빌드 통과: GitHub Actions `Onlyti/NovaGNSSConfigurator` (main), 최신 3d613ef. 로컬 SDK 없어 CI 의존.
package `com.onlyti.novagnss`, 표시명 "NovA GNSS Configurator". 실기 검증 미실시(앱만 준비).
상표: NovAtel 은 등록상표(Hexagon), 본 앱은 비공식 호환 유틸. 비상업이라 리스크 낮음. NovAtel 은
설명·기능에만 표기.

## 3. 완료 (구현됨)

v0.1 + SPAN 4탭(Status / Terminal / Config / Calib):
- SerialLink: USB serial(CDC/FTDI/CP210x/CH340/PL2303), 송수신 (rtk-router 와 동일 패턴)
- ConsoleViewModel: serial 세션 + 콘솔 라인버퍼 + 수신 라인 NovAtel 파싱 → Status StateFlow
- **Terminal**: ASCII 명령 송신(TX echo)+응답(RX) monospace, 명령 팔레트(탭=송신/롱프레스=삭제/추가), baud/port
- **Status**: BESTPOS(sol/RTK/sats/pos), INSPVAX(INS status·PoseType·roll/pitch/azimuth),
  Skyplot(SATVIS2 az/el 극좌표, constellation 색). 탭 진입 시 LOG 자동구독, 나갈 때 UNLOG
- **Config(SPAN)**: 축정렬 RBV(드롭다운→회전행렬→Z-X-Y Euler→SETINSROTATION, 전송전 편집검토),
  ANT1 lever arm, USER 출력위치(기본 enclosure 0,0,0), SAVECONFIG
- **Calib**: INSCALIBRATE RBV NEW/ADD/STOP/RESET + INSCALSTATUS 모니터 + 절차 가이드

## 4. 미완 (다음 할 일)

- **실기 검증**: NovAtel enclosure + USB-RS232 어댑터로 연결 → Terminal 응답 → Status 로그 →
  Config 명령 동작 확인. (Status/Config 는 포트가 NOVATEL ASCII 모드여야 함, RTCMV3 면 안 됨)
- 구조화 카드 확장: VERSION/RXSTATUS/BESTPOS 등 보기좋게
- 로그 저장/내보내기
- RBV Euler gimbal-lock 경계(±90°) 검증 — 현재 편집필드로 보정 가능하나 자동값 확인 필요
- (보류) u-blox UBX config 모듈 — 별도 바이너리 레이어 필요, 큰 작업

## 5. 주의·함정 (NovAtel 검증사항, docs.novatel.com 확인됨)

- **Vehicle frame = X=right / Y=forward / Z=up** (NovAtel 고유, ROS X-forward 와 다름). RBV 핵심.
- SETINSTRANSLATION/SETINSROTATION: **TYPE 토큰 먼저**, 그다음 X Y Z, SD, (translation만) InputFrame.
  InputFrame 기본 IMUBODY. SETINSROTATION rotation order **Z-X-Y, right-handed, deg**.
- **IMU body 축은 기기마다 다름**(docs 도 "enclosure 표기 참조"). 앱이 가정 안 함 → 사용자가 IMU
  데이터시트 보고 드롭다운 선택. 잘못 넣으면 셋업 망가짐 → RBV/lever arm 명령은 전송 전 화면표시·편집.
- **Skyplot az/el → SATVIS2** (TRACKSTAT 엔 az/el 없음, C/No 만). 현재 C/No 색칠 미적용(시스템색만).
- INSPVAX field1=INS status, field2=pos type. RTK fixed = NARROW_INT(50)/INS_RTKFIXED(56),
  float = NARROW_FLOAT(34)/INS_RTKFLOAT(55).
- INSCALIBRATE RBV: seed RBV + INS_SOLUTION_GOOD 정렬 + 5m/s 직선주행 필요. 코스끝 STOP, 다음 ADD.
- **ExposedDropdownMenu/menuAnchor 는 scope 멤버 — import 금지**(CI 컴파일 에러 났던 지점).

## 6. 관련 경로

- `app/src/main/java/com/onlyti/novagnss/`
  - `serial/SerialLink.kt`(USB serial)
  - `novatel/NovatelLog.kt`(INSPVAX/BESTPOS/SATVIS2 파서 + pos-type 라벨)
  - `novatel/SpanCommands.kt`(명령 빌더 + 축→RBV 수학, Direction enum)
  - `config/Commands.kt`(기본 명령 팔레트)
  - `ui/ConsoleViewModel.kt`(serial 세션·파싱·Status state), `ui/MainActivity.kt`(4탭·skyplot), `ui/Prefs.kt`
- 형제: `~/git/rtk-router`(NTRIP 라우터, SerialLink 공유 원본). README.md / CLAUDE.md.

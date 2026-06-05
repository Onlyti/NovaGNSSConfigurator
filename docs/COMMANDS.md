# NovAtel OEM7 / PwrPak7 명령 분류 + 앱 설계

**스코프: NovA = 수신기 설정 전용. 폰이 보정을 라우팅하지 않음(그건 rtk-router 몫, 사용 여부는
사용자 결정).** NTRIPCONFIG/WIFINETCONFIG 는 "수신기가 WiFi 로 자체 RTK 하도록 설정"하는 것 —
앱이 RTK 를 수행하는 게 아니라 수신기 설정값을 넣는 것이라 포함.

검증: docs.novatel.com (OEM7 Commands/Logs). 작성 2026-06-05. 구현 전 설계 문서.
syntax 는 abbreviated-ASCII. [?] = 매뉴얼 미확인(구현 전 재검증 필요, 일단 제외).

## A. 명령 분류 (성격별)

3종: **LOG**(읽기전용) · **SET**(설정변경 → SAVECONFIG 필요) · **ACTION**(실행).

### 1. Info — 1회성 확인 (LOG ... ONCE, 읽기전용)
| log | syntax | 용도 |
|---|---|---|
| VERSION | `LOG VERSIONA ONCE` | HW/FW 버전 |
| RXCONFIG | `LOG RXCONFIGA ONCE` | 현재 설정 전체(=INTERFACEMODE/SERIALCONFIG 조회) |
| RXSTATUS | `LOG RXSTATUSA ONCE` | 수신기 상태/에러워드 |
| HWMONITOR | `LOG HWMONITORA ONCE` | 온도·안테나 전류/전압 |
| PORTSTATS | `LOG PORTSTATSA ONCE` | 포트별 통신통계 |
| BESTPOS | `LOG BESTPOSA ONCE` | 위치+RTK 상태 |
| BESTVEL | `LOG BESTVELA ONCE` | 속도 |
| TIME | `LOG TIMEA ONCE` | 시계/GPS time |
| TRACKSTAT | `LOG TRACKSTATA ONCE` | 채널별 추적(C/No) |
| SATVIS2 | `LOG SATVIS2A ONCE` | 위성 az/el (skyplot) |
| INSPVAX | `LOG INSPVAXA ONCE` | INS 위치/자세 (SPAN) |
| INSCONFIG | `LOG INSCONFIGA ONCE` | INS 설정 요약 (SPAN) |

주: 포트 모드 "조회"는 별도 명령 없음 → RXCONFIG 로 확인.

### 2. Ports — 포트 설정 (SET)
- `SERIALCONFIG [port] baud [parity databits stopbits handshaking break]`
  - port COM1..10/THISPORT, baud 9600..460800, parity N/E/O, databits 7/8, stopbits 1/2
  - 예 `SERIALCONFIG COM1 115200 N 8 1 N OFF`
- `INTERFACEMODE [port] rxtype txtype [responses]`
  - rx/tx: NONE/NOVATEL/RTCM/RTCA/CMR/**RTCMV3**/NOVATELBINARY/AUTO ...
  - 예(RTCM3 입력) `INTERFACEMODE COM1 RTCMV3 NOVATEL ON`
  - **함정: 같은 포트에 SERIALCONFIG 를 INTERFACEMODE 보다 먼저.** baud 바꾸면 INTERFACEMODE 초기화됨.

### 3. Network / WiFi (PwrPak7 전용; M/Q 미지원) (SET)
- `WIFIMODE mode` — OFF/AP/**CLIENT**/ON/CONCURRENT (먼저 설정)
- `WIFINETCONFIG network_id switch [ssid passkey [address_mode [IP netmask [gateway [dns]]]]]`
  - switch DISABLE/ENABLE, address_mode DHCP/STATIC
  - 예 `WIFINETCONFIG 1 ENABLE MySSID mypassword`
- `IPCONFIG [iface] mode [IP netmask gateway]` — ETHA, DHCP/STATIC
- `ETHCONFIG iface [speed duplex crossover power]`
- `DNSCONFIG count IP` (STATIC IP 일 때만)

### 4. NTRIP / 엔드포인트 — 수신기 자가 RTK (SET) ★핵심
- `NTRIPCONFIG port type [protocol [endpoint [mountpoint [user [pass [bindif]]]]]]`
  - port **NCOM1/NCOM2/NCOM3**, type DISABLED/**CLIENT**/SERVER, protocol V1/V2
  - endpoint `host:port`, mountpoint, user, pass
  - 예 `NTRIPCONFIG NCOM1 CLIENT V2 gnss.eseoul.go.kr:2101 VRS-RTCM32 seoul pw`
- `NTRIPSOURCETABLE host:port` → 결과는 `LOG SOURCETABLEA ONCE`
- `ICOMCONFIG [port] protocol [endpoint]` — ICOM1.., TCP/UDP/TCP_SERVER (NTRIP 대신 raw TCP)

### 5. RTK / 측위 (SET)
- `RTKSOURCE type [id]` — RTCM/RTCMV3/AUTO/NONE, id 또는 ANY. 기본 `AUTO ANY`
- `PSRDIFFSOURCE type [id]` — RTCM/SBAS/AUTO/NONE
- `RTKTIMEOUT delay` — 5..60s, 기본 60
- `RTKDYNAMICS mode` — AUTO/STATIC/DYNAMIC
- `ECUTOFF angle` — 고도마스크, 기본 5.0

### 6. GNSS 신호 (SET)
- `ASSIGNALL [system] [state]` — GPS/GLONASS/GALILEO/BEIDOU/QZSS/ALL, state IDLE(끔)/AUTO(켬)
  - 예 `ASSIGNALL GLONASS IDLE`

### 7. SPAN / INS (SET; SPAN 모델)
- `SETINSTRANSLATION ANT1/ANT2/USER x y z [sd...] [IMUBODY/VEHICLE]` (구현됨)
- `SETINSROTATION RBV x y z [sd...]` (구현됨, Z-X-Y)
- `SETINSPROFILE profile` — DEFAULT/LAND/MARINE/FIXEDWING/FOOT/VTOL/RAIL/AGRICULTURE
- `ALIGNMENTMODE mode` — UNAIDED/AIDED_TRANSFER/AUTOMATIC/STATIC/KINEMATIC
- INSCALIBRATE RBV (구현됨), SETINITAZIMUTH [?]

### 8. System (ACTION)
- `LOG [port] msg ONCE|ONTIME t|ONCHANGED|ONNEW` / `UNLOG msg` / `UNLOGALL [port]`
- `SAVECONFIG` — NVM 저장 (SET 명령들 영구화)
- `FRESET [target]` — 공장초기화(STANDARD 기본). **FACTORYRESET 없음**
- `RESET [delay]` [?] — 재부팅

## B. 제외/주의 ([?] 재검증 전 미구현)
VALIDMODELS, INSCONFIG(page 404), SELECTCHANCONFIG, DGPSTIMEOUT, RESET, SETINITAZIMUTH.

## C. 앱 구조 재설계

현재 4탭: Status / Terminal / Config / Calib. **Config 탭을 카테고리 내부선택**으로 재편:

```
Config 탭 상단: [Info] [Ports] [Network] [NTRIP/RTK] [GNSS] [SPAN] [System]  (chip row)
  -> 선택된 카테고리의 폼만 표시
하단 공통: SAVECONFIG 버튼 (SET 후 영구화)
```

각 카테고리:
- **Info**: 버튼 묶음(VERSION/RXSTATUS/BESTPOS/HWMONITOR...). 누르면 LOG ONCE 전송 + 응답 파싱카드
- **Ports**: SERIALCONFIG(port·baud) + INTERFACEMODE(port·rx·tx) 폼. 순서 경고 표시
- **Network**: WIFIMODE + WIFINETCONFIG(SSID·pw) + IPCONFIG. PwrPak7 만
- **NTRIP/RTK** ★: NTRIPCONFIG 폼(NCOM·host·mount·계정) + RTKSOURCE. **+ "Network RTK 마법사"**:
  WIFIMODE CLIENT → WIFINETCONFIG → NTRIPCONFIG → INTERFACEMODE NCOM1 RTCMV3 → SAVECONFIG 원클릭
- **RTK tuning**: RTKTIMEOUT/RTKDYNAMICS/ECUTOFF
- **GNSS**: constellation on/off 토글(ASSIGNALL)
- **SPAN**: 현재 것(RBV/ANT1/ANT2/USER) + SETINSPROFILE/ALIGNMENTMODE
- **System**: SAVECONFIG/FRESET/UNLOGALL (FRESET 은 확인 다이얼로그)

명령 패턴 통일: 입력폼 → **생성된 명령 미리보기(편집가능)** → SEND. 위험명령(FRESET/RBV)은 확인.

## D. ★ 핵심 시나리오 — PwrPak7 WiFi 자가 RTK
폰이 라우터가 아니라 **설정도구**. 수신기가 직접 caster 접속:
```
WIFIMODE CLIENT
WIFINETCONFIG 1 ENABLE <SSID> <passkey>
NTRIPCONFIG NCOM1 CLIENT V2 <host:port> <mountpoint> <user> <pass>
INTERFACEMODE NCOM1 RTCMV3 NOVATEL ON   (또는 RTKSOURCE AUTO ANY)
SAVECONFIG
```
→ 이후 폰 빼도 수신기 혼자 RTK 유지. "Network RTK 마법사" 한 화면으로.

## E. 구현 우선순위 (제안)
1. Config 카테고리 내비 + Info 섹션 (기존 명령 정리)
2. **NTRIP/RTK + Network (WiFi 자가 RTK 마법사)** — 핵심 가치
3. Ports, RTK tuning, GNSS, System
4. SPAN 은 기존 유지 + Profile/Alignment 추가

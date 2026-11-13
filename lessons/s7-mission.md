# 7-미션 공급망 확인을 다른 곳에도
⏱ 약 15분(혼자 풀기) · 고칠 파일 자바 3개 + 새 파일 2개 · 영상 없이 이 노트로 진행합니다

7-2·7-5·7-6·7-7에서 손으로 만든 확인을 다른 곳에도 적용합니다.

## 1. 회원 캐시와 권한 목록 캐시에 같은 설정을 (고칩니다)
7-7에서는 관리자 캐시(`UmsAdmin`)만 형식을 고정했습니다. 같은 `CacheTypeGuard`를 두 곳에 더 씁니다.
- 회원 캐시 `UmsMemberCacheServiceImpl`(고객 앱, `getMember`·`setMember`)
- 권한 목록 캐시 `UmsAdminCacheServiceImpl`(관리자 앱, `getResourceList`·`setResourceList`). 6-4의 서명 확인은 그대로 둡니다.

```bash
make goto L=7-mission
make check L=7-mission      # 회원·권한 목록 캐시 시험이 레드
```
이벤트는 `deserialize.denied.type`, target은 `cache:mall:ums:member:*`·`cache:mall:ums:resourceList:*`.

## 2. 앱이 믿는 설정 파일에 서명을 (고칩니다)
방어 스위치 파일(`.daon/switches.env`)은 앱이 믿는 설정입니다. 누가 손으로 `ownership`을 꺼 두면 앱은 그대로 따릅니다.
- `make switch`가 꺼진 스위치 줄(`NAME=false`)을 정렬해 이은 글자의 HMAC-SHA256을 `SECUREKIT_SIG`로 함께 적습니다(키 `DAON_SWITCH_SIGN_KEY`는 파일 밖 compose 환경에 있음. 랩 전용 가짜 값).
- 앱의 `SecureKitSwitch`(✏️)가 같은 방식으로 다시 계산해 보고, **서명이 맞지 않으면 꺼짐 값을 믿지 않고 기본값(켜짐)으로 뜹니다.** 새 클래스 `SwitchSignature`가 계산·비교(상수 시간)를 맡고, 시작할 때 `config.signature.invalid`를 남깁니다.
- 단위 시험 `SwitchSignatureTest`(4개)가 합격 기준입니다.

확인: 손으로 고친 파일(서명 없음)로 앱을 다시 띄우면 소유권 검사가 켜진 채 뜹니다(`make replay T=01` 그린). `make switch`로 서명해서 끄면 따릅니다(레드). 스위치 `signed-switches`를 끄면 서명 확인 없이 따릅니다(영상용).

```bash
make check L=7-mission      # 5개 통과
make logs S=mall-portal | grep config.signature.invalid
```

## 더 해 볼 것(시험 없음)
- `make sbom`·`make kev`·`tools/ci/check_pins.py`·`tools/deploy/deploy.sh` 확인 단계를 회사의 CI에 옮길 때 무엇이 달라질지 적어 봅니다(투명성 로그, 키 위치, 매일 새로 받는 KEV 목록).
- 타입이 정해진 다른 캐시(예: `mall:oms:*`)가 있는지 Redis 키 이름 앞부분만 세어 표로 만들어 봅니다(`make redis-flush`처럼 값을 읽지 않는 방법으로).
- 다음 섹션은 기록과 경보입니다. 이 섹션의 도구 이벤트(`supply.*`·`ci.*`·`deploy.denied.*`)는 `reports/security-events.jsonl`에 쌓입니다. 8-4에서 수집기가 읽습니다.

## 막히면
- `make solution L=7-mission` · `make apply-solution L=7-mission`

## 기억할 한 줄
저장소에서 읽은 글자와 파일에 적힌 값은 남이 쓸 수 있으니, 그 글자가 타입을 고르게도 보호를 끄게도 두지 않는다.

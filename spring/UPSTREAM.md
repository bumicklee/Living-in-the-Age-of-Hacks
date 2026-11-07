# spring/ 원본과 바꾼 내용

이 폴더는 오픈소스 쇼핑몰 **mall**(Apache-2.0)을 포크한 것입니다. **원본 mall은 중국어 프로젝트이고, 이 강의는 한국어로 번역한 판으로 진행합니다.**

## 원본

| 저장소 | 고정 커밋 | 날짜 | 라이선스 |
|---|---|---|---|
| https://github.com/macrozheng/mall | `dcaa93b3150352e5044708d7211b5bed0af4509f` | 2026-09-15 | Apache-2.0 (`LICENSE`) |
| https://github.com/macrozheng/mall-admin-web → `mall-admin-web/` | `81fc17e5a19f452bd854b106a9d59fa1ed5c7eac` | 2026-05-14 | Apache-2.0 (`mall-admin-web/LICENSE`) |

## 바꾼 내용 1: 한국어 번역판 (2026-11-06)

- **중국어만 한국어로 바꿨습니다.** 영어 문구, 코드, 식별자는 그대로입니다.
- 바꾼 곳: 코드 주석, Swagger 설명, API 응답·오류 메시지, 관리자 화면 문구, `document/sql/mall.sql`의 표 주석과 시드 데이터, 설정 파일 주석, README.
- 바꾸지 않은 곳: 원본 설계 문서(`document/pdm`·`pos`·`axure`·`mind`), 함께 실린 라이브러리(`mall-admin-web/public/tinymce6.8.6`).
- 바꾼 파일 목록: `../tools/translated-files.txt` (527개). 번역 사전은 `../tools/zh_ko.json`, 적용 스크립트는 `../tools/translate_zh_ko.py`입니다. 원본 커밋에서 스크립트를 다시 돌리면 같은 결과가 나옵니다.

번역 때문에 문구가 아닌 것을 바꾼 곳은 셋입니다.

| 파일 | 바꾼 것 | 이유 |
|---|---|---|
| `mall-portal/.../OmsPromotionServiceImpl.java` | 프로모션 문구를 조립하는 `append` 순서 | 중국어와 한국어 어순이 달라서. 계산과 값은 그대로이고, 문구만 「금액 할인: 1000.00원 이상 구매 시 120.00원 할인」, 「수량 할인: 2개 이상 구매 시 정가의 8.00할 가격」 꼴입니다 |
| `document/sql/mall.sql` | `oms_order.promotion_info` 길이 `varchar(100)` → `varchar(500)` | 한국어 프로모션 문구가 100자를 넘어 시드 데이터가 들어가지 않아서 |
| `mall-admin-web/src/App.vue`, `src/components/Tinymce/index.vue` | Element Plus 언어 `zh-cn` → `ko`, TinyMCE 언어 설정 삭제(기본 영어) | 라이브러리 화면에 중국어가 나오지 않게 |

확인한 것(2026-11-06): 중국어(한자)가 남은 줄 0개, Maven으로 7개 모듈 패키징 성공(`-Ddocker.skip=true`), MySQL 8.0에 `mall.sql`을 오류 없이 적재(표 76개).

알려 둘 점:
- 번역은 기계 번역 뒤 일부만 사람이 고쳤습니다. 강의에 나오는 화면과 메시지는 촬영 전에 다시 읽습니다.
- 금액 단위는 원본 데이터 그대로입니다(원래 위안화 금액에 「원」만 붙였습니다). 한국어 시드로 바꾸는 일은 `seed/`에서 합니다.
- 원본 `docker-compose-env.yml`의 `mysql:5.7`은 Apple Silicon(arm64) 이미지가 없습니다. 강의의 compose(`../infra/compose.yml`)는 `mysql:8.4.13`을 씁니다(8.0은 2026-04 지원 종료). 앱은 원본 `application-prod.yml`을 그대로 쓰고, 랩에서 달라야 하는 값(DB 주소의 `allowPublicKeyRetrieval=true`, MinIO 주소)만 환경 변수로 덮습니다.
- 원본 `pom.xml`의 docker-maven-plugin은 원작자 서버(192.168.3.101)에 이미지를 만들려고 합니다. 빌드할 때 `-Ddocker.skip=true`를 붙입니다.

## 바꾼 내용 2: 수업 표시 (2026-11-07)

수강생이 고칠 자리에 `// ✏️ N-M: …(다온마켓 교육용 표시)` 주석을 넣었습니다. 동작은 바꾸지 않습니다. 각 수업의 고친 모습은 `../solutions/<수업>/spring/...`에 있습니다(이 파일들도 Apache-2.0, "다온마켓 2-2" 같은 주석으로 바꾼 곳을 표시).

| 파일 | 표시 | 수업 |
|---|---|---|
| `mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPortalOrderServiceImpl.java` | `detail()`에 ✏️ 2-2 | 2-2(T01) |

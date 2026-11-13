# @secure-kit/evershop (뼈대, 아직 시험하지 않음)

EverShop에 `@secure-kit/express` 부품을 붙이는 얇은 연결 확장입니다. 부품은 순수 Express 미들웨어이고, 이 확장은 EverShop의 미들웨어 파일 이름 규칙(`[앞]이름[뒤].js`)에 맞춰 부품을 끼워 넣는 일만 합니다. 라이선스는 MIT입니다.

> **주의: 뼈대입니다.** EverShop 위에서 아직 띄워 보지 않았습니다. 파일 위치와 이름 순서는 EverShop 소스의 모듈 구조를 보고 짐작해서 정했습니다. 실제로 붙일 때 확인할 점은 아래 「확인할 것」에 적었습니다.

## 붙이는 부품

| 파일 | 부품 | 위치 |
|---|---|---|
| `src/pages/global/[context]secureKitHeaders.js` | `kit.headers()` | 모든 화면 요청 |
| `src/api/global/[context]secureKitHeaders.js` | `kit.headers()` | 모든 API 요청 |
| `src/api/global/[apiResponse]secureKitErrorResponse[apiErrorHandler].js` | `kit.errorResponse()` | API 오류 응답 |
| `src/pages/frontStore/customerLoginJson/[bodyParser]secureKitRateLimit[login].js` | `kit.rateLimit('login')` | 고객 로그인 |
| `src/pages/admin/adminLoginJson/[bodyParser]secureKitRateLimit[logIn].js` | `kit.rateLimit('login')` | 관리자 로그인 |

설정은 환경 변수 `SECURE_KIT_CONFIG`에 JSON으로 넣습니다. 키는 Spring·Express와 같은 `secure-kit.*`입니다. 넣지 않으면 `src/kit.js`의 기본값을 씁니다. 기본값에서는 EverShop 로그인 본문에 맞춰 `account-field: email`을 씁니다.

## 설치(예정)

1. EverShop 저장소의 `extensions/` 아래에 이 폴더를 두거나 `file:` 경로로 받습니다.
2. EverShop 설정의 `system.extensions`에 `{ "name": "secure-kit", "resolve": "<경로>", "enabled": true }`를 넣습니다.

## 확인할 것

- 확장 안의 `pages/<영역>/<라우트 폴더>/` 미들웨어가 원래 모듈의 같은 라우트에 합쳐지는지 확인합니다.
- 인자 4개짜리 오류 처리기를 `api/global`에 두면 EverShop이 오류 처리기로 부르는지 확인합니다. 안 되면 수업 6-5의 Express diff처럼 `apiErrorHandler` 본문을 직접 고치는 편이 확실합니다.
- 로그인이 실패했을 때 EverShop이 401·403을 돌려주는지 확인합니다. 속도 제한은 이 두 상태를 실패로 셉니다. 실패를 200과 오류 JSON으로 돌려준다면 실패 횟수가 늘지 않습니다.
- EverShop 화면이 쓰는 스크립트·스타일과 기본 CSP가 맞는지 확인합니다. 화면이 깨지면 `secure-kit.headers.csp`를 조정합니다.

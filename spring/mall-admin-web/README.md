# mall-admin-web

<p>
  <a href="#공식 계정"><img src="http://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/badge/%E5%85%AC%E4%BC%97%E5%8F%B7-macrozheng-blue.svg" alt="공식 계정"></a>
  <a href="#공식 계정"><img src="http://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/badge/%E4%BA%A4%E6%B5%81-%E5%BE%AE%E4%BF%A1%E7%BE%A4-2BA245.svg" alt="소통"></a>
  <a href="https://github.com/macrozheng/mall"><img src="http://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/badge/%E5%90%8E%E5%8F%B0%E9%A1%B9%E7%9B%AE-mall-blue.svg" alt="관리자 프로젝트"></a>
  <a href="https://github.com/macrozheng/mall-swarm"><img src="http://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/badge/Cloud%E7%89%88%E6%9C%AC-mall--swarm-brightgreen.svg" alt="SpringCloud버전"></a>
  <a href="https://gitee.com/macrozheng/mall-admin-web"><img src="http://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/badge/%E7%A0%81%E4%BA%91-%E9%A1%B9%E7%9B%AE%E5%9C%B0%E5%9D%80-orange.svg" alt="기티"></a>
</p>

## 안내

> 1. **빠른 체험 프로젝트**: [온라인 접속 주소](https://www.macrozheng.com/admin/index.html) .
> 2. ****전체 학습 튜토리얼**: [mall 학습 튜토리얼](https://www.macrozheng.com) .
> 3. **브랜치 설명**:`master`브랜치는 Vue3+Element-Plus 기반이며,`dev-v2`브랜치는 Vue2+Element 기반입니다.

## 머리말

이 프로젝트는 프런트엔드와 백엔드가 분리된 프로젝트의 프런트엔드 부분이며, 백엔드 프로젝트는`mall`주소: [바로가기](https://github.com/macrozheng/mall) .

## 프로젝트 소개

`mall-admin-web`전자상거래 관리자 시스템의 프런트엔드 프로젝트로, Vue3+Element-Plus 기반으로 구현했습니다. 주요 기능은 상품 관리, 주문 관리, 회원 관리, 프로모션 관리, 운영 관리, 콘텐츠 관리, 통계 리포트, 재무 관리, 권한 관리, 설정 등입니다.

### 프로젝트 데모

프로젝트 온라인 데모 주소:[https://www.macrozheng.com/admin/](https://www.macrozheng.com/admin/)

![관리자 시스템 기능 데모](http://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/project/mall_admin_show.png)

### 기술 선정

| 기술                        | 설명                  | 공식 사이트                                                    |
| --------------------------- | --------------------- | ------------------------------------------------------- |
| Vue                         | 프런트엔드 프레임워크              | https://cn.vuejs.org/                                   |
| Element Plus                | 프런트엔드 UI 프레임워크            | https://element-plus.org/                               |
| Vue Router                  | 라우트 프레임워크              | https://router.vuejs.org/                               |
| Pinia                       | 전역 상태 관리 프레임워크      | https://pinia.vuejs.org/                                |
| Pinia Plugin Persistedstate | Pinia영속화 플러그인       | https://prazdevs.github.io/pinia-plugin-persistedstate/ |
| Axios                       | 프런트엔드 HTTP 프레임워크          | https://github.com/axios/axios                          |
| vue-charts                  | Echarts 기반 차트 프레임워크 | https://github.com/ecomfe/vue-echarts                   |
| TinyMCE Vue                 | 리치 텍스트 편집기          | https://github.com/tinymce/tinymce-vue                  |
| Js-cookie                   | cookie관리 도구        | https://github.com/js-cookie/js-cookie                  |
| vue-element-admin           | 프로젝트 스캐폴드            | https://github.com/PanJiaChen/vue-element-admin         |

### 프로젝트 레이아웃

```lua
src -- 소스 코드 디렉터리
├── apis -- axios네트워크 요청 API 정의
├── assets -- 정적 이미지 리소스 파일
├── components -- 공통 컴포넌트 래퍼
├── icons -- svg벡터 이미지 파일
├── router -- vue-router라우트 설정
├── store -- pinia상태 관리
├── styles -- 전역 css 스타일
├── types -- 유형 정의
├── utils -- 유틸리티 클래스
└── views -- 프런트엔드 페이지
    ├── home -- 홈
    ├── layout -- 공통 페이지 프레임워크
    ├── normal -- 공통 페이지(login 및 404)
    ├── oms -- 주문 모듈 페이지
    ├── pms -- 상품 모듈 페이지
    ├── sms -- 상품 모듈 페이지
    └── ums -- 사용자 모듈 페이지
```

## 구축 단계

- node를 내려받아 설치합니다. v20 이상 버전을 지원합니다(v20 LTS 버전 권장):https://nodejs.org/zh-cn/download/archive/v20.20.0
- 이 프로젝트는 프런트엔드와 백엔드가 분리된 프로젝트로, 로컬 API에 접속하려면 관리자 환경을 구축해야 합니다. 구축 방법은 백엔드 프로젝트를 참고하세요 [바로가기](https://github.com/macrozheng/mall);
- 온라인 API는 관리자 환경을 구축하지 않고도 접속할 수 있으며`.env.development`파일 안의`VITE_BASE_SERVER_URL`[https://admin-api.macrozheng.com](https://admin-api.macrozheng.com) 으로 변경하면 됩니다;
- [mall-swarm](https://github.com/macrozheng/mall-swarm) 마이크로서비스 백엔드와 연동하는 경우 모든 API를 게이트웨이를 통해 접근해야 하므로 다음을 변경해야 합니다:`.env.development`파일 안의`VITE_BASE_SERVER_URL`[http://localhost:8201/mall-admin](http://localhost:8201/mall-admin) 으로 변경;
- 소스 코드를 로컬에 클론하고 VSCode로 열어 컴파일 완료;
- 명령줄에서 명령 실행:`npm install`,관련 의존성 다운로드;
- 명령줄에서 명령 실행:`npm run dev`,프로젝트 실행;
- 접속 주소: [http://localhost:5173](http://localhost:5173) 로 관리자 페이지를 열 수 있습니다;
- 자세한 배포 과정은 다음을 참고하세요: [mall 프런트엔드 프로젝트 설치와 배포](https://www.macrozheng.com/mall/start/mall_deploy_web.html)
- 프런트엔드 자동 배포는 다음을 참고하세요: [Jenkins로 프런트엔드 앱 원클릭 빌드·배포, 이렇게 쉬웠다니!](https://www.macrozheng.com/mall/reference/jenkins_vue.html)

## 공식 계정

위챗 단체방에 참여해 소통하세요. 공식 계정 백오피스에서 [**가입**]이라고 답장하면 됩니다.

![공식 계정 이미지](http://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/banner/qrcode_for_macrozheng_258.jpg)

## 라이선스

[Apache License 2.0](https://github.com/macrozheng/mall-admin-web/blob/master/LICENSE)

Copyright (c) 2018-2026 macrozheng

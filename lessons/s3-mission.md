# 3-미션 다른 목록·다른 글자 칸에도 같은 방어
⏱ 약 15분(혼자 풀기) · 고칠 파일 2개 · 영상 없이 이 노트로 진행합니다

3-3의 허용 목록과 3-4의 출력 규칙을 다른 곳에도 적용해 봅니다.

## 1. 다른 목록의 정렬·필터: 확인만 합니다
관리자 앱의 주문 목록(`OmsOrderController.list`)·상품 목록(`PmsProductController.getList`)·반품 신청 목록을 엽니다. 정렬을 요청에서 받지 않고, 조건은 `OmsOrderQueryParam`처럼 정해진 칸으로만 받아 MyBatis `#{}`에 넣습니다. 3-1에서 찾은 대로 원본 매퍼의 `${}`는 상수뿐입니다.
```bash
.venv/bin/python tools/scan_mybatis_dollar.py     # 결과: 입력 경로 ${ 없음
```

## 2. 다른 글자 칸: 여기를 고칩니다
- 원본 반품 신청 상세(`views/oms/apply/applyDetail.vue`)의 반품 사유·설명은 `{{ }}`로 글자 출력합니다. 확인만.
- 후기 화면의 「작성자」 칸은 닉네임과 접속 주소를 한 줄로 꾸미려고 `v-html`을 씁니다. 닉네임은 고객이 2-4의 회원정보 수정으로 정하는 글입니다.

1. **레드인 것부터 봅니다.**
   ```bash
   make goto L=3-mission
   make check L=3-mission
   ```
   alice가 닉네임에 태그를 넣고 후기를 쓰면 관리자 화면의 작성자 칸에 그 태그가 요소로 그려집니다(3-5의 CSP 덕분에 스크립트는 실행되지 않지만, 첫 번째 겹이 빠진 상태입니다).
2. **출력에서 고칩니다.** `spring/mall-admin-web/src/views/pms/review/index.vue`의 작성자 칸. 꾸밈은 템플릿이 하고 값은 글자로만 넣습니다.
   ```vue
   <template #default="{ row }"><span :data-testid="`review-nick-${row.id}`">{{ row.memberNickName }} <small>({{ row.memberIp }})</small></span></template>
   ```
3. **⑤ 탐지도 같은 기준으로.** `DaonReviewServiceImpl.create()`에서 후기에 함께 저장되는 닉네임도 `tagNames()`로 봅니다. `lesson`은 `"3-mission"`, reason은 「허용 밖 태그 발견(닉네임): b」처럼.
4. **확인합니다.**
   ```bash
   make check L=3-mission
   make logs S=mall-portal | grep '"lesson":"3-mission"'
   ```

## 더 해 볼 것(시험 없음)
- 고객 앱의 회원정보 수정(2-4)에서 닉네임에 태그가 오면 저장 전에 남기는 것도 같은 생각입니다. 어디에 두는 편이 나을까요(입력 지점 vs 저장되는 곳마다)?
- 3-6의 업로드 검사기와 3-5의 보안 헤더는 섹션 끝에 secure-kit 부품(`UploadValidator`, `headers` 설정)으로 뽑습니다. SQL 인젝션은 쿼리마다 `#{}`로 쓰는 일이라 부품 대신 검색 스크립트(`tools/scan_mybatis_dollar.py`)를 CI에 둡니다.
- 다음 섹션은 로그인과 계정입니다.

## 막히면
- `make solution L=3-mission` · `make apply-solution L=3-mission`

## 기억할 한 줄
고객이 정한 글은 칸 이름과 상관없이 모두 남이 쓴 글이다.

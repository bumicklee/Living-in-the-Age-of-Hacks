# 2-미션 배송지·반품 신청에도 같은 소유권 검사
⏱ 약 15분(혼자 풀기) · 고칠 파일 1개 · 영상 없이 이 노트로 진행합니다

2-2에서 주문 상세 조회에 소유권 검사를 넣었습니다. 같은 질문(「이것이 로그인한 회원의 것인가」)을 고객 앱의 다른 기능에도 던져 봅니다.

## 1. 배송지: 이미 지켜지는지 확인만 합니다
`UmsMemberReceiveAddressServiceImpl`을 엽니다. 조회·수정·삭제 모두 조건에 `andMemberIdEqualTo(currentMember.getId())`가 들어 있습니다. 원본이 이미 2-2의 모양으로 짜여 있습니다.
- 시험 `test_address_already_checks_owner`가 확인합니다. alice가 배송지를 하나 만들고, bob이 그 번호로 조회·수정·삭제를 시도합니다. 모두 실패해야 합니다. 이 시험은 시작 지점에서도 그린입니다.
- 볼 점: 주문 만들기(`generateOrder`)도 배송지를 `getItem(id)`로 가져오므로, 남의 배송지 번호를 넣어도 쓰지 못합니다.

## 2. 반품 신청: 여기를 고칩니다
`POST /returnApply/create`는 요청 본문의 주문 번호(`orderId`)로 반품 신청을 저장합니다. 그 주문이 누구 것인지 확인하지 않고, 신청자 이름(`memberUsername`)과 주문 번호 문자열(`orderSn`)도 본문 값을 그대로 씁니다. 원본 그대로의 코드입니다(BASELINE T01 미션 줄 참고).

1. **레드인 것부터 봅니다.**
   ```bash
   make goto L=2-mission
   make check L=2-mission
   ```
   bob이 alice의 주문 9002로 반품을 신청하면 `code=200`으로 저장됩니다.
2. **고칩니다.** `spring/mall-portal/.../service/impl/OmsPortalOrderReturnApplyServiceImpl.java`의 ✏️ 2-미션 표시. 2-2와 같은 모양으로 씁니다.
   - 로그인한 회원을 `memberService.getCurrentMember()`로 가져옵니다.
   - 주문을 `id + memberId + deleteStatus=0` 조건으로 조회합니다(`OmsOrderMapper`, `OmsOrderExample`). `orderId`가 비어 있으면 조회하지 않고 거부합니다.
   - 없으면 `Asserts.fail("주문을 찾을 수 없습니다")`. 남의 주문과 없는 주문은 같은 답을 받습니다.
   - `orderSn`은 조회한 주문에서, `memberUsername`은 로그인 정보에서 채웁니다. 본문 값은 쓰지 않습니다.
3. **⑤ 탐지.** 거부하기 직전에 2-2와 같은 이벤트를 남깁니다. `lesson`만 `"2-mission"`입니다.
   ```java
   SecurityEventLogger.denied("access.denied.not_owner", "2-mission")
           .actor("member:" + member.getId())
           .target("order:" + returnApply.getOrderId())
           .reason("본인 주문이 아니거나 없는 주문으로 반품 신청")
           .log();
   ```
4. **확인합니다.**
   ```bash
   make check L=2-mission
   make logs S=mall-portal | grep '"lesson":"2-mission"'
   ```
   alice가 자기 주문으로 반품을 신청하는 정상 요청 시험도 그린이어야 합니다.

## 더 해 볼 것(시험 없음)
- 반품 신청 본문에는 상품 가격(`productPrice`, `productRealPrice`)도 있습니다. 2-4의 눈으로 보면 무엇이 문제일까요? 주문 상품 표(`oms_order_item`)에서 가져오도록 바꿔 봅니다.
- 고객 앱 컨트롤러를 모두 훑어 「요청 값으로 남의 데이터 번호를 받는 곳」을 표로 정리합니다(장바구니·쿠폰·후기). 섹션 끝 정리는 secure-kit의 `OwnershipGuard` 도우미로 이어집니다.

## 선택: 스위치로 다시 보기
정답 코드(`make goto L=2-mission AT=done`)의 반품 신청 검사도 2-2와 같은 스위치 `secure-kit.ownership.enabled`를 읽습니다. `make switch K=ownership V=off` → `make check L=2-mission`이 레드, `V=on` → 그린.

## 막히면
- `make solution L=2-mission` · `make apply-solution L=2-mission`

## 기억할 한 줄
소유권 검사는 기능 하나가 아니라 「번호를 받는 모든 곳」의 일이다. 하나를 고쳤으면 같은 모양을 모두 찾는다.

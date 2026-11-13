# 12-미션 파트너 범위·바인딩을 다른 연동 기능에도
⏱ 약 20분(혼자 풀기) · 영상 없이 이 노트로 진행합니다 · 시험 없음(15장 secure-kit 정리 때 함께 확인)

12장에서 `@PartnerScope`·`PartnerScopeInterceptor`·`PartnerClientCert`를 secure-kit(mall-security) 부품으로 만들었습니다. 다음 연동 기능에도 한 줄로 붙여 봅니다.

```bash
make goto L=12-mission
```

## 1. 배송 상태 올리기(가상): `POST /partner/orders/{id}/delivery`
- 배송 파트너가 송장 번호·배송 단계를 올리는 기능을 만든다고 해 봅니다. 범위 이름을 새로 짓습니다: `orders:write:delivery`.
- `@PartnerScope("orders:write:delivery")` + 조회 조건(`daon_partner_order.app_id`)을 함께. 범위만 붙이면 다른 파트너 주문의 배송 상태를 바꿀 수 있습니다.
- 대장의 범위(관리자 앱 `/daon/partner/apps/{id}/scopes`)와 `DaonPartnerAdminController.KNOWN_SCOPES`에 새 범위를 더합니다. 범위를 넓히면 `partner.app.changed`가 남는지 봅니다.

## 2. 대장 점검을 CI로(선택)
- `make partner-audit`의 규칙(90일·담당자·내보내기 범위·회전)을 7-5 로컬 CI의 lint 작업에 넣어, 결과가 있으면 배포 전에 알리게 합니다(11-5 `audit_dns.py`와 같은 자리).
- 대장을 DB 대신 저장소의 YAML(`infra/partners.yaml`)로 두면 변경 이력과 리뷰가 남습니다. 어느 쪽이 맞는지 팀의 운영 방식으로 정합니다.

## 3. 바인딩을 다른 입구에도(그림)
- 관리자 입구(8443)에 직원 단말 인증서를 요구하면 무엇이 달라지는지 그려 봅니다(5-5 매장 단말 그림과 같은 이야기).

## 확인(손으로)
```bash
make check L=12-2 && make check L=12-3 && make check L=12-4   # 부품을 다시 쓴 뒤에도 그린
make switch K=all V=on
```

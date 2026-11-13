# 8-미션 감사·가리기·체인을 다른 관리자 기능에도
⏱ 약 15분(혼자 풀기) · 고칠 파일 자바 2개 + 새 파일 3개(런북) · 영상 없이 이 노트로 진행합니다

8-3의 `@Audited`·`AuditAspect`, 8-5의 `LogMask`·`MaskingConverter`, 8-6의 `AuditChain`은 이미 공용 모듈(mall-security·mall-common)에 있습니다. 15장에서 `secure-kit-audit`로 뽑을 부품 그대로입니다. 이 미션은 그 부품을 감사 대상이 아니었던 기능 두 곳에 한 줄로 붙이고, 경보 세 개의 런북을 채웁니다.

```bash
make goto L=8-mission
make check L=8-mission      # 두 기능의 감사 로그·런북 시험이 레드
```

## 1. 두 기능에 감사 로그를 (고칩니다)
- 쿠폰 사용 내역 `SmsCouponHistoryController.list()`(회원 이름·주문 번호가 나감) → `@Audited(action = "coupon.history.list", resource = "couponHistory")`
- 반품 상세 `OmsOrderReturnApplyController.getItem()`(신청자 이름·전화가 나감) → `@Audited(action = "return.detail", resource = "return")`

새 줄도 같은 체인(seq·prev·hash)으로 이어지는지 `make audit-verify`로 봅니다. `docs/audit-schema.yaml`의 `actions` 표에도 두 줄을 더해 두면 좋습니다(시험은 보지 않음).

## 2. 런북 세 개를 채웁니다 (새 파일)
`docs/runbooks/_양식.md`를 복사해 `access.denied.not_owner.md`·`data.read.bulk.md`·`audit.chain.broken.md`를 만듭니다. 항목은 세 개만: **먼저 볼 질의**(Loki 질의, event·actor로), **첫 조치**(계정 잠그기·토큰 폐기 … 되돌리는 방법도), **누구에게 알리나**(가짜 주소). 8-7의 경보 메일 본문이 이 파일을 가리킵니다. 자세한 대응은 14-4에서 합니다.

## 확인
```bash
make check L=8-mission      # 4개 통과(체인·가리기 0건 정상 시험 포함)
make log-scan               # 0건
```

## 더 해 볼 것(시험 없음)
- Express: `kit.audited()`처럼 EverShop 관리자 GraphQL 처리기 한 곳에 `diffs/s8-03`의 `audit.ts`를 붙여 봅니다.
- 쿠폰 사용 내역을 한 번에 많이 보는 경우를 `rules/bulk.yaml`의 계정별 한도에 넣으려면 `action=~` 정규식에 무엇을 더해야 할지 적어 봅니다.

## 막히면
- `make solution L=8-mission` · `make apply-solution L=8-mission`

## 기억할 한 줄
기록은 남기는 것(감사)·믿을 수 있게 하는 것(가리기·체인)·사람에게 닿게 하는 것(경보·런북)이 한 벌이다.

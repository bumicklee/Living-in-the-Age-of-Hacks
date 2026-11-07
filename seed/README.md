# 가짜 시드 (A5·A6)

두 스택(Spring·Express)에 **같은 계정, 같은 번호**를 넣는다. 그래서 같은 HTTP 시험 한 벌이 두 스택을 똑같이 겨눌 수 있다.
값은 모두 가짜다: 이름은 「성+가짜+번호」(예: 김가짜03), 전화는 `010-0000-xxxx`, 메일은 `*.example`, 주소는 지어낸 동네(가상구·예시구·샘플동).

| 파일 | 넣는 때 | 내용 |
|---|---|---|
| `spring/20-daon-accounts.sql` | MySQL 볼륨을 처음 만들 때 | 수업이 기대는 최소 값: alice·bob, 주문 9001~9003, 직원 계정(손으로 관리) |
| `spring/30-daon-bulk.sql` | 같은 때(20번 다음) | 고객 103~140, 주문 9004~9300, 상품·쿠폰·포인트·배송지 (자동 생성) |
| `express/30-daon-bulk.sql` | EverShop 첫 `make up` 끝(관리자 계정·데모 상품 다음) | 위 둘을 합친 같은 값 (자동 생성) |

다시 만들기(시드 고정 `20261107`, 몇 번을 돌려도 같은 파일):

```bash
python tools/gen_seed.py
make wipe HARD=1 && make up      # 새 시드는 볼륨을 새로 만들 때 들어간다
```

## 숫자

고객 40명 · 주문 300건(주문 상품 약 480줄) · 상품 30개 · 쿠폰 5종(받은 쿠폰 55장) · 포인트(고객별 잔액 + 가입·사용 내역).
비밀번호는 모두 `daon-pass-1!`.

## 번호 대응표

| 무엇 | Spring(mall, MySQL) | Express(EverShop, PostgreSQL) |
|---|---|---|
| 고객 | `ums_member.id` 101~140 | `customer.customer_id` 101~140 |
| 로그인 | `username`: `alice`, `bob`, `daon103`~`daon140` | `email`: `<username>@daon.example` (예: `alice@daon.example`) |
| 이름 | 배송지 `name`(받는 사람) | `customer.full_name` |
| 배송지 | `ums_member_receive_address.id` = 회원 id | `customer_address.customer_address_id` = 고객 id |
| 포인트 | `ums_member.integration` | `customer.meta_data.points` (EverShop에는 포인트가 없어 메타데이터로) |
| 상품 | `pms_product.id` 201~230, `product_sn` `DAON-P201` | `product.product_id` 201~230, `sku` `DAON-P201`, `url_key` `daon-p201` |
| SKU | `pms_sku_stock.id` 2001~2030 (`1800 + 상품 id`) | (단일 상품이라 없음) |
| 쿠폰 | `sms_coupon.id` 101~105, `code` `DAON-WELCOME` 등 | `coupon.coupon_id` 101~105, `coupon` 같은 코드 |
| 주문 | `oms_order.id` 9001~9300 | `"order".order_id` 9001~9300 |
| 주문 번호 | `order_sn` `DAON` + 날짜 8자리 + 순번 4자리 (예: `DAON202611010001`) | `order_number` 같은 값 |
| 주문 상품 | `oms_order_item.id` = 주문 id × 10 + 순번 (예: 90011) | `order_item.order_item_id` 같은 값 |
| 주문 배송지 | 주문 행 안의 `receiver_*` | `order_address.order_address_id` = 주문 id |
| 택배 송장 | `delivery_sn` `DT` + 주문 id 10자리 (발송·완료 주문만) | `meta_data.delivery_sn` |

주문 상태 대응:

| Spring `status` | 뜻 | EverShop `status` / `payment_status` / `shipment_status` |
|---|---|---|
| 0 | 결제 대기 | `new` / `pending` / `pending` |
| 1 | 배송 대기 | `processing` / `paid` / `pending` |
| 2 | 발송됨 | `processing` / `paid` / `shipped` |
| 3 | 완료 | `completed` / `paid` / `delivered` |
| 4 | 닫힘 | `canceled` / `canceled` / `canceled` |

## 지켜야 할 것

- **alice(101)·bob(102)과 주문 9001~9003은 번호를 바꾸지 않는다.** 재연·시험(`replay/_http.py`의 `ACCOUNTS`)이 이 번호를 쓴다.
  bulk 시드는 alice·bob에게 주문을 더하지 않는다(쿠폰 한두 장과 배송지만 준다).
- 9001~9003의 상품: Spring 20번 파일은 원본 데모 상품 id(26·27·28)를 가리키고, Express는 같은 이름의 다온 상품(201·207·216)을 가리킨다.
  시험은 주문 번호·받는 사람·전화로 비교하고 상품 id로는 비교하지 않는다.
- 새 주문은 앱이 만든다: Spring은 9301부터, Express도 `order_id` 9301부터 이어진다(주문 번호 형식은 스택마다 다르다).
- 실제 사람·회사와 겹치는 값을 넣지 않는다. 이름에는 늘 「가짜」가 들어간다.

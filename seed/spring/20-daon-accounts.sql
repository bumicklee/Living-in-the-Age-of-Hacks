-- 다온마켓 최소 시드(11/7 뼈대용). 한국어 전체 시드(고객 40명·주문 300건)는 seed/에서 따로 만든다.
-- 모든 값은 가짜다: 이메일은 *.example, 전화는 010-0000-xxxx, 주소는 지어낸 것.
-- 비밀번호(두 계정 같음): daon-pass-1!   (bcrypt, cost 10)
SET NAMES utf8mb4;

INSERT INTO ums_member (id, member_level_id, username, password, nickname, phone, status, create_time, gender, source_type, integration, growth, history_integration)
VALUES
  (101, 4, 'alice', '$2a$10$u0wE8GEVqNE2AP6u02Lrw.MzzCBfWTdG51t.vJLpjX0hwkbwj7izu', '앨리스', '010-0000-0101', 1, '2026-11-01 10:00:00', 2, 1, 1000, 100, 1000),
  (102, 4, 'bob',   '$2a$10$u0wE8GEVqNE2AP6u02Lrw.MzzCBfWTdG51t.vJLpjX0hwkbwj7izu', '밥',     '010-0000-0102', 1, '2026-11-01 10:05:00', 1, 1, 1000, 100, 1000);

-- alice의 주문 두 건, bob의 주문 한 건. 배송지·전화번호가 담겨 있어 남에게 보이면 안 되는 정보다.
INSERT INTO oms_order (id, member_id, order_sn, create_time, member_username, total_amount, pay_amount, freight_amount,
                       promotion_amount, integration_amount, coupon_amount, discount_amount, pay_type, source_type, status, order_type,
                       auto_confirm_day, receiver_name, receiver_phone, receiver_post_code, receiver_province, receiver_city,
                       receiver_region, receiver_detail_address, note, confirm_status, delete_status)
VALUES
  (9001, 101, 'DAON202611010001', '2026-11-01 11:00:00', 'alice', 39000.00, 39000.00, 3000.00, 0, 0, 0, 0, 1, 1, 1, 0, 15,
   '김앨리스', '010-0000-0101', '00001', '서울특별시', '가상구', '다온동', '다온로 101, 101동 1001호', '문 앞에 놓아 주세요', 0, 0),
  (9002, 101, 'DAON202611020002', '2026-11-02 15:30:00', 'alice', 12000.00, 12000.00, 0.00, 0, 0, 0, 0, 1, 1, 3, 0, 15,
   '김앨리스', '010-0000-0101', '00001', '서울특별시', '가상구', '다온동', '다온로 101, 101동 1001호', NULL, 1, 0),
  (9003, 102, 'DAON202611030003', '2026-11-03 09:10:00', 'bob', 25000.00, 25000.00, 3000.00, 0, 0, 0, 0, 1, 1, 1, 0, 15,
   '이밥', '010-0000-0102', '00002', '부산광역시', '예시구', '샘플동', '샘플길 22', NULL, 0, 0);

INSERT INTO oms_order_item (id, order_id, order_sn, product_id, product_name, product_brand, product_price, product_quantity, real_amount)
VALUES
  (90011, 9001, 'DAON202611010001', 26, '다온 무선 이어폰', '다온', 36000.00, 1, 36000.00),
  (90021, 9002, 'DAON202611020002', 27, '다온 텀블러', '다온', 12000.00, 1, 12000.00),
  (90031, 9003, 'DAON202611030003', 28, '다온 우산', '다온', 22000.00, 1, 22000.00);

-- 다온마켓 직원 계정(관리자 앱, 2-3·2-5). 비밀번호는 위와 같은 daon-pass-1!
-- 역할은 원본 데모의 역할을 쓴다: 1 = 상품 관리자, 2 = 주문 관리자(5 = 슈퍼 관리자는 원본 admin 계정)
INSERT INTO ums_admin (id, username, password, nick_name, email, note, create_time, status)
VALUES
  (101, 'staff_goods', '$2a$10$u0wE8GEVqNE2AP6u02Lrw.MzzCBfWTdG51t.vJLpjX0hwkbwj7izu', '상품 담당', 'goods@daon.example', '다온마켓 상품 담당 직원', '2026-11-01 09:00:00', 1),
  (102, 'staff_order', '$2a$10$u0wE8GEVqNE2AP6u02Lrw.MzzCBfWTdG51t.vJLpjX0hwkbwj7izu', '주문 담당', 'order@daon.example', '다온마켓 주문 담당 직원', '2026-11-01 09:00:00', 1);
INSERT INTO ums_admin_role_relation (admin_id, role_id) VALUES (101, 1), (102, 2);

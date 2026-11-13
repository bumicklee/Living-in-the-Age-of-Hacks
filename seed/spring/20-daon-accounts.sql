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

-- 섹션 3(인젝션) 시드. 값은 모두 가짜다.
-- ① 공개하지 않은 상품 두 개(publish_status = 0). 정상 검색에는 나오지 않아야 한다. 3-2 재연에서 「비공개 상품이 섞여 나옴」을 보는 표시다.
INSERT INTO pms_product (id, brand_id, product_category_id, product_attribute_category_id, name, product_sn, delete_status, publish_status, new_status, recommand_status, verify_status, sort, sale, price, original_price, gift_growth, gift_point, use_point_limit, sub_title, description, stock, low_stock, unit, promotion_type, brand_name, product_category_name, keywords)
VALUES
  (231, 101, 103, 4, '다온 노이즈캔슬링 이어폰(출시 전)', 'DAON-P231', 0, 0, 1, 0, 1, 0, 0, 89000, 89000, 0, 0, 0, '출시 전 비공개 · 다온마켓 가짜 상품', '출시 전 비공개 상품. 교육용 랩의 가짜 상품입니다.', 50, 10, '개', 0, '다온', '디지털', '다온 이어폰 비공개'),
  (232, 101, 101, 4, '다온 겨울 한정 담요(출시 전)', 'DAON-P232', 0, 0, 1, 0, 1, 0, 0, 42000, 42000, 0, 0, 0, '출시 전 비공개 · 다온마켓 가짜 상품', '출시 전 비공개 상품. 교육용 랩의 가짜 상품입니다.', 80, 10, '개', 0, '다온', '생활', '다온 담요 비공개');
-- ② 상품 후기 표(원본 utf8)를 utf8mb4로: 후기에 이모지를 쓸 수 있게(3-4 정상 후기 시험)
ALTER TABLE pms_comment CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 섹션 10(10-5 원장 대사) 시드. alice·bob 가입 포인트 1,000점의 원장 줄. 이 줄이 없으면 시드 상태에서 대사가 두 사람을 불일치로 낸다.
-- id 901·902(bulk 시드의 원장은 1001부터). 2026-11-10 더함: 새 볼륨(make wipe HARD=1)에서만 들어간다.
INSERT INTO ums_integration_change_history (id, member_id, create_time, change_type, change_count, operate_man, operate_note, source_type)
VALUES
  (901, 101, '2026-11-01 10:00:00', 0, 1000, 'system', '가입 축하 포인트(가짜)', 0),
  (902, 102, '2026-11-01 10:05:00', 0, 1000, 'system', '가입 축하 포인트(가짜)', 0);

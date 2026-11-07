-- [원본 기본값] mall 배포 문서의 앱 계정(reader / 123456)을 그대로 만든다.
-- 이름은 reader지만 mall 테이블 전체 권한이다(원본 문서 그대로). 6장에서 다시 본다.
CREATE USER IF NOT EXISTS 'reader'@'%' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON mall.* TO 'reader'@'%';
FLUSH PRIVILEGES;

# Mysql주요 명령어

## 데이터 정의 언어(DDL)

### 데이터베이스 작업

- 데이터베이스 로그인:mysql -uroot -proot
- 데이터베이스 생성:create database test
- 모든 데이터베이스 확인:show databases
- 사용할 데이터베이스:use test
- 모든 테이블 확인:show tables
- 데이터베이스 삭제:drop database test

### 테이블 작업

- 테이블 생성:create table emp(ename varchar(10),hiredate date,sal decimal(10,2),deptno int(2))  
         create table dept(deptno int(2),deptname varchar(10))
- 테이블 정의 확인:desc emp
- 테이블 정의 확인(상세):show create table emp \G
- 테이블 삭제:drop table emp
- 테이블 필드 수정:alter table emp modify ename varchar(20)
- 테이블 필드 추가:alter table emp add column age int(3)
- 테이블 필드 삭제:alter table emp drop column age
- 필드 이름 변경;alter table emp change age age1 int(4)
- 테이블 이름 변경:alter table emp rename emp1

## 데이터 조작 언어(DML)

### 레코드 삽입

- 이름을 지정해 삽입:insert into emp (ename,hiredate,sal,deptno) values ('zhangsan','2018-01-01','2000',1)
- 이름을 지정하지 않고 삽입:insert into emp values ('lisi','2018-01-01','2000',1)
- 데이터 일괄 삽입:insert into dept values(1,'dept1'),(2,'dept2')

### 수정 기록

update emp set sal='4000',deptno=2 where ename='zhangsan'

### 기록 삭제

delete from emp where ename='zhangsan'

### 조회 기록

- 모든 레코드 조회:select * from emp
- 중복 없는 레코드 조회:select distinct deptno from emp
- 조건 조회:select * from emp where deptno=1 and sal<3000
- 정렬 및 제한:select * from emp order by deptno desc limit 2
- 집계(부서 인원이 1명보다 많은 부서 번호 조회):select deptno,count(1) from emp group by deptno having count(1) > 1
- 조인 조회:select * from emp e left join dept d on e.deptno=d.deptno
- 서브쿼리:select * from emp where deptno in (select deptno from dept)
- 레코드 조인:select deptno from emp union select deptno from dept

## 데이터 제어 언어(DCL)

- 작업 권한 부여:grant select,insert on test.* to 'test'@'localhost' identified by '123'
- 작업 권한 회수:revoke insert on test.* from 'test'@'localhost'

## 기타

### 문자셋 관련
- 문자셋 확인:show variables like 'character%'
- 데이터베이스 생성 시 문자 집합 지정:create database mall character set utf8

### 시간대 변경
- mysql 전역 시간대를 베이징 시간, 즉 우리가 있는 동8구역으로 변경:set global time_zone = '+8:00';
- 현재 세션 시간대 변경:set time_zone = '+8:00'
- 즉시 적용:flush privileges

### 권한 관련
- 모든 데이터베이스의 모든 권한 부여:grant all privileges on *.* to z1@localhost identified by '123'
- 모든 데이터베이스의 모든 권한 부여(grant 포함):grant all privileges on *.* to z1@localhost with grant option
- SUPER PROCESS FILE 권한 부여:grant super,process,file on *.* to z3@localhost
- 로그인 권한만 부여:grant usage on *.* to z4@localhost
- 계정 권한 확인:show grants for z1@localhost
- 내 비밀번호 변경:set password = password('123')
- 관리자가 다른 사용자의 비밀번호 수정:set password for 'z1'@'localhost' = password('123')
- 계정 삭제:drop user z2@localhost
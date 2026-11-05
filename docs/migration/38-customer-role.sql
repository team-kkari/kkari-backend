-- #38 배포 전에 기존 DB 에서 한 번 실행한다.
-- Hibernate 는 enum 컬럼에 CHECK 제약을 만들지만 ddl-auto=update 로는 기존 제약을 고치지 않아, CUSTOMER 가입이 실패한다.
ALTER TABLE tbl_user DROP CONSTRAINT IF EXISTS tbl_user_role_check;
ALTER TABLE tbl_user ADD CONSTRAINT tbl_user_role_check CHECK (role IN ('MSP_ENGINEER', 'CUSTOMER'));

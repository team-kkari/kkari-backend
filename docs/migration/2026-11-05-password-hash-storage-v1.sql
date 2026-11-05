-- 신규 PBKDF2 해시와 기존 BCrypt 해시를 함께 저장하기 위한 비파괴 컬럼 확장.
-- 스키마 자동 갱신을 사용하지 않는 환경에서는 새 애플리케이션 반영 전에 실행한다.
-- 기존 비밀번호 해시 값은 변경하지 않는다.
ALTER TABLE tbl_user ALTER COLUMN password TYPE varchar(255);

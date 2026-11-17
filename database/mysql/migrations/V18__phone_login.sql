-- Run with application writes stopped and a backup. Existing passwords/roles/links are unchanged.
SET NAMES utf8mb4;
ALTER TABLE app_user
    MODIFY COLUMN email VARCHAR(254) NULL,
    ADD COLUMN phone VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER email,
    ADD UNIQUE KEY uq_user_phone(phone),
    ADD CONSTRAINT ck_user_login_contact CHECK(email IS NOT NULL OR phone IS NOT NULL),
    ADD CONSTRAINT ck_user_phone CHECK(phone IS NULL OR REGEXP_LIKE(phone,'^[+]?[0-9]{8,15}$','c'));

-- Backfill only unambiguous valid numbers from already-linked profiles. No new accounts or links.
CREATE TEMPORARY TABLE migration_v18_phone_candidates AS
SELECT DISTINCT user_id,
    CASE WHEN compact_phone LIKE '+84%' THEN CONCAT('0',SUBSTRING(compact_phone,4))
         WHEN compact_phone LIKE '84%' AND CHAR_LENGTH(compact_phone) IN (11,12) THEN CONCAT('0',SUBSTRING(compact_phone,3))
         ELSE compact_phone END phone
FROM (
    SELECT user_id,REGEXP_REPLACE(TRIM(phone),'[[:space:].()-]','') compact_phone FROM staff WHERE user_id IS NOT NULL AND phone IS NOT NULL
    UNION ALL
    SELECT user_id,REGEXP_REPLACE(TRIM(phone),'[[:space:].()-]','') FROM guardian WHERE user_id IS NOT NULL AND phone IS NOT NULL
) source_contacts;
CREATE TEMPORARY TABLE migration_v18_unique_users AS
SELECT user_id,MIN(phone) phone FROM migration_v18_phone_candidates
WHERE REGEXP_LIKE(phone,'^[+]?[0-9]{8,15}$','c') GROUP BY user_id HAVING COUNT(DISTINCT phone)=1;
CREATE TEMPORARY TABLE migration_v18_unique_phones AS
SELECT phone FROM migration_v18_unique_users GROUP BY phone HAVING COUNT(*)=1;
UPDATE app_user u JOIN migration_v18_unique_users c ON c.user_id=u.id
JOIN migration_v18_unique_phones unique_phone ON unique_phone.phone=c.phone SET u.phone=c.phone;
DROP TEMPORARY TABLE migration_v18_unique_phones;
DROP TEMPORARY TABLE migration_v18_unique_users;
DROP TEMPORARY TABLE migration_v18_phone_candidates;

-- Check and resolve duplicate non-null phone values before running this script.
USE lixiaopu;
ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_phone(phone);
ALTER TABLE sys_user ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
-- Rename in place so existing sys_user_role links remain valid.
UPDATE sys_role SET role_code='SYS_ADMIN' WHERE role_code='ROLE_SUPER_ADMIN';
UPDATE sys_role SET role_code='ADMIN' WHERE role_code='ROLE_ADMIN';
UPDATE sys_role SET role_code='BUYER' WHERE role_code='ROLE_BUYER';
-- Then execute shop-common/src/main/resources/db/02-roles.sql.
-- After the seed, legacy buyer accounts can be assigned the BUYER role:
-- INSERT IGNORE INTO sys_user_role(user_id,role_id)
-- SELECT u.id,r.id FROM sys_user u JOIN sys_role r ON r.role_code='BUYER'
-- WHERE u.user_type=3;

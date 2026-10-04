-- 为已有的 lixiaopu.sys_user 补齐密码变更所需的授权版本字段。
-- 可重复执行；已有字段时不修改表或现有数据。
USE lixiaopu;

SET @auth_version_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND COLUMN_NAME = 'auth_version'
);

SET @auth_version_ddl = IF(
    @auth_version_exists = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `auth_version` BIGINT NOT NULL DEFAULT 0',
    'SELECT ''auth_version already exists'' AS result'
);

PREPARE auth_version_stmt FROM @auth_version_ddl;
EXECUTE auth_version_stmt;
DEALLOCATE PREPARE auth_version_stmt;

SHOW COLUMNS FROM `sys_user` LIKE 'auth_version';

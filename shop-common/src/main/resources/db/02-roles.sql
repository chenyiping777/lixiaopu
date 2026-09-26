USE lixiaopu;
INSERT INTO sys_role(role_code,role_name,sort,is_enable) VALUES
('SYS_ADMIN','系统管理员',1,1),('ADMIN','普通管理员',2,1),('BUYER','买家',3,1)
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name),is_enable=1;
INSERT INTO sys_permission(perm_code,perm_name,perm_type,parent_id,sort,is_enable) VALUES
('buyer:access','用户端访问',2,0,1,1),
('admin:access','管理端访问',2,0,2,1),
('admin:create','创建普通管理员',2,0,3,1),
('user:disable','禁用账号',2,0,4,1)
ON DUPLICATE KEY UPDATE perm_name=VALUES(perm_name),is_enable=1;
INSERT IGNORE INTO sys_role_permission(role_id,perm_id)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p
WHERE (r.role_code='BUYER' AND p.perm_code='buyer:access')
   OR (r.role_code='ADMIN' AND p.perm_code='admin:access')
   OR (r.role_code='SYS_ADMIN' AND p.perm_code IN ('admin:access','admin:create','user:disable'));

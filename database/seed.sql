USE agri_trace;

INSERT INTO sys_organization (id, org_code, org_name, org_type, contact_name, contact_phone, address)
VALUES (1, 'ORG-DEMO-FARM', '新农可信示范农场', 'FARM', '管理员', '00000000000', '示范产区')
ON DUPLICATE KEY UPDATE org_name = VALUES(org_name);

INSERT INTO sys_role (id, role_code, role_name, permissions)
VALUES (1, 'ADMIN', '系统管理员', JSON_ARRAY('product:write', 'batch:write', 'trace:write', 'iot:read', 'nfc:write'))
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

INSERT INTO sys_user (id, organization_id, username, password_hash, display_name, status)
VALUES (1, 1, 'admin', 'change-me-sha256', '系统管理员', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);

INSERT INTO sys_user_role (user_id, role_id)
VALUES (1, 1)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

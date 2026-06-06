-- 应用成员绑定部门（PERSON 按部门筛选、组织管理）
ALTER TABLE un_module_member
    ADD COLUMN dept_id BIGINT NULL COMMENT '部门 un_module_dept.id' AFTER role_id;

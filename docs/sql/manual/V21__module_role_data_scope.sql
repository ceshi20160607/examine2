-- 手工执行（Flyway 可选）：角色数据权限范围
-- 1=本人 2=本人及下属(暂同本人) 3=本部门 4=本部门及下级 5=全部
ALTER TABLE un_module_role
    ADD COLUMN data_scope TINYINT NOT NULL DEFAULT 1
        COMMENT '数据权限：1本人 2本人及下属 3本部门 4本部门及下级 5全部'
        AFTER status;

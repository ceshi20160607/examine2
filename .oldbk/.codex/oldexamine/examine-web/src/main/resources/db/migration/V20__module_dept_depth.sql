-- 部门物化路径：祖先至本级 ID，逗号分隔（如 1,5,23），便于子树筛选
ALTER TABLE un_module_dept
    ADD COLUMN depth VARCHAR(512) NULL
        COMMENT '祖先至本级部门ID路径，逗号分隔有序，如 1,5,23'
        AFTER parent_id;

CREATE INDEX idx_module_dept_depth ON un_module_dept (app_id, depth(128));

-- 回填已有数据（多轮：父节点 depth 就绪后再写子节点）
UPDATE un_module_dept SET depth = CAST(id AS CHAR) WHERE (parent_id IS NULL OR parent_id = 0) AND (depth IS NULL OR depth = '');

UPDATE un_module_dept c
    INNER JOIN un_module_dept p ON c.parent_id = p.id AND c.app_id = p.app_id
SET c.depth = CONCAT(p.depth, ',', c.id)
WHERE p.depth IS NOT NULL AND p.depth != '' AND (c.depth IS NULL OR c.depth = '');

UPDATE un_module_dept c
    INNER JOIN un_module_dept p ON c.parent_id = p.id AND c.app_id = p.app_id
SET c.depth = CONCAT(p.depth, ',', c.id)
WHERE p.depth IS NOT NULL AND p.depth != '' AND (c.depth IS NULL OR c.depth = '');

UPDATE un_module_dept c
    INNER JOIN un_module_dept p ON c.parent_id = p.id AND c.app_id = p.app_id
SET c.depth = CONCAT(p.depth, ',', c.id)
WHERE p.depth IS NOT NULL AND p.depth != '' AND (c.depth IS NULL OR c.depth = '');

UPDATE un_module_dept c
    INNER JOIN un_module_dept p ON c.parent_id = p.id AND c.app_id = p.app_id
SET c.depth = CONCAT(p.depth, ',', c.id)
WHERE p.depth IS NOT NULL AND p.depth != '' AND (c.depth IS NULL OR c.depth = '');

UPDATE un_module_dept c
    INNER JOIN un_module_dept p ON c.parent_id = p.id AND c.app_id = p.app_id
SET c.depth = CONCAT(p.depth, ',', c.id)
WHERE p.depth IS NOT NULL AND p.depth != '' AND (c.depth IS NULL OR c.depth = '');

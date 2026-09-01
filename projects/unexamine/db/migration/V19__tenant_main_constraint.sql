ALTER TABLE `sys_tenant`
  DROP CHECK `chk_sys_tenant_main_marker`,
  ADD CONSTRAINT `chk_sys_tenant_main_marker`
    CHECK ((`is_main` = 1 AND `main_marker` IS NOT NULL AND `main_marker` = 'MAIN')
      OR (`is_main` = 0 AND `main_marker` IS NULL));

-- 仪表盘按平台、系统及租户归属查询；数据源只引用已发布模块与模块关系。
CREATE TABLE IF NOT EXISTS un_dashboard (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仪表盘主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台系统或模块仪表盘作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统仪表盘所属系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统仪表盘所属租户主键',
  module_id BIGINT DEFAULT NULL COMMENT '模块首页关联模块主键',
  owner_account_id BIGINT DEFAULT NULL COMMENT '个人仪表盘所属平台账号主键',
  code VARCHAR(80) NOT NULL COMMENT '作用域内仪表盘编码',
  name VARCHAR(120) NOT NULL COMMENT '仪表盘名称',
  layout_json JSON NOT NULL COMMENT '草稿组件位置尺寸和页面布局',
  published_layout_json JSON DEFAULT NULL COMMENT '已发布页面布局快照',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '已发布仪表盘版本',
  create_account_id BIGINT NOT NULL COMMENT '创建仪表盘的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), KEY ix_dashboard_scope (scope_type, system_id, tenant_id, owner_account_id, status),
  KEY ix_dashboard_module (module_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义仪表盘';

CREATE TABLE IF NOT EXISTS un_dashboard_component (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仪表盘组件主键',
  dashboard_id BIGINT NOT NULL COMMENT '所属仪表盘主键',
  component_code VARCHAR(80) NOT NULL COMMENT '页面内稳定组件编码',
  component_type VARCHAR(40) NOT NULL COMMENT '图表指标列表待办或快捷入口类型',
  data_source_id BIGINT DEFAULT NULL COMMENT '已发布自定义数据源主键',
  module_id BIGINT DEFAULT NULL COMMENT '直接查询的已发布模块主键',
  config_json JSON NOT NULL COMMENT '草稿统计筛选样式刷新和跳转配置',
  published_config_json JSON DEFAULT NULL COMMENT '已发布组件运行配置快照',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '组件顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '组件草稿发布或停用状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_dashboard_component (dashboard_id, component_code),
  KEY ix_dashboard_component_source (data_source_id, module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表盘配置组件';

CREATE TABLE IF NOT EXISTS un_dashboard_component_field (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仪表组件字段主键',
  component_id BIGINT NOT NULL COMMENT '所属仪表组件主键',
  source_field_id BIGINT NOT NULL COMMENT '数据源输出字段或模块字段稳定主键',
  field_role VARCHAR(30) NOT NULL COMMENT '指标分组时间或排序字段用途',
  aggregation VARCHAR(30) DEFAULT NULL COMMENT '计数求和平均最大或最小计算方式',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '组件内字段顺序',
  config_json JSON DEFAULT NULL COMMENT '展示格式及字段级补充配置',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_component_field (component_id, source_field_id, field_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表组件字段配置';

CREATE TABLE IF NOT EXISTS un_dashboard_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仪表盘可见范围主键',
  dashboard_id BIGINT NOT NULL COMMENT '所属仪表盘主键',
  subject_type VARCHAR(20) NOT NULL COMMENT '授权成员角色或部门类型',
  subject_id BIGINT NOT NULL COMMENT '成员角色或部门主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
  PRIMARY KEY (id), UNIQUE KEY uk_dashboard_subject (dashboard_id, subject_type, subject_id),
  KEY ix_dashboard_user_subject (subject_type, subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表盘人员与组织访问范围';

CREATE TABLE IF NOT EXISTS un_data_source (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自定义数据源主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统数据源作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统数据源所属系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '定义数据源的租户主键',
  code VARCHAR(80) NOT NULL COMMENT '作用域内稳定数据源编码',
  name VARCHAR(120) NOT NULL COMMENT '数据源名称',
  root_module_id BIGINT DEFAULT NULL COMMENT '模块型数据源起始的已发布模块主键',
  description VARCHAR(500) DEFAULT NULL COMMENT '数据源业务口径说明',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '已发布数据源版本',
  create_account_id BIGINT NOT NULL COMMENT '创建数据源的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), KEY ix_data_source_scope (scope_type, system_id, tenant_id, status),
  KEY ix_data_source_root (root_module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按模块及关联配置的自定义数据源';

CREATE TABLE IF NOT EXISTS un_data_source_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据源查询配置主键',
  data_source_id BIGINT NOT NULL COMMENT '所属数据源主键',
  relation_json JSON DEFAULT NULL COMMENT '已配置模块关联路径与连接方式',
  output_json JSON NOT NULL COMMENT '允许输出的模块字段稳定标识',
  filter_json JSON DEFAULT NULL COMMENT '结构化固定筛选条件',
  sort_json JSON DEFAULT NULL COMMENT '默认排序和时间字段配置',
  published_json JSON DEFAULT NULL COMMENT '已发布查询配置整体快照',
  published_version INT NOT NULL DEFAULT 0 COMMENT '查询配置发布版本',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_data_source_config (data_source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义数据源的关联输出筛选配置';

CREATE TABLE IF NOT EXISTS un_kpi (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '关键指标主键',
  system_id BIGINT NOT NULL COMMENT '指标所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '指标所属租户主键',
  code VARCHAR(80) NOT NULL COMMENT '租户内指标编码',
  name VARCHAR(120) NOT NULL COMMENT '指标名称',
  data_source_id BIGINT DEFAULT NULL COMMENT '计算指标的已发布数据源主键',
  module_id BIGINT DEFAULT NULL COMMENT '直接计算指标的业务模块主键',
  measure_field_id BIGINT DEFAULT NULL COMMENT '参与指标计算的模块字段主键',
  aggregation VARCHAR(30) NOT NULL COMMENT '指标数量求和比率等统计口径',
  period_type VARCHAR(20) NOT NULL COMMENT '按月季度或年份考核',
  config_json JSON DEFAULT NULL COMMENT '草稿筛选预警和计算规则',
  published_config_json JSON DEFAULT NULL COMMENT '已发布指标计算规则快照',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '已发布指标版本',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_kpi_code (tenant_id, code),
  KEY ix_kpi_source (data_source_id, module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关键绩效指标配置';

CREATE TABLE IF NOT EXISTS un_kpi_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '指标目标与计算结果主键',
  kpi_id BIGINT NOT NULL COMMENT '所属关键指标主键',
  target_type VARCHAR(20) NOT NULL COMMENT '人员部门或角色目标类型',
  target_id BIGINT NOT NULL COMMENT '指标目标对象主键',
  period_key VARCHAR(20) NOT NULL COMMENT '月份季度或年度周期编码',
  target_value DECIMAL(20,4) NOT NULL COMMENT '该周期设定目标值',
  actual_value DECIMAL(20,4) DEFAULT NULL COMMENT '最近一次已计算实际值',
  calculated_time DATETIME(3) DEFAULT NULL COMMENT '实际值最近计算时间',
  calculation_json JSON DEFAULT NULL COMMENT '计算口径来源和结果解释快照',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '目标启用或停用状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_kpi_target_period (kpi_id, target_type, target_id, period_key),
  KEY ix_kpi_user_target (target_type, target_id, period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标人员组织目标与计算结果';

CREATE TABLE IF NOT EXISTS un_kpi_result (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '指标计算历史主键',
  kpi_user_id BIGINT NOT NULL COMMENT '所属人员或组织周期目标主键',
  kpi_version INT NOT NULL COMMENT '计算所用的已发布指标版本',
  actual_value DECIMAL(20,4) NOT NULL COMMENT '本次计算的实际指标值',
  source_json JSON NOT NULL COMMENT '来源模块数据源筛选口径与权限摘要',
  calculation_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '指标实际计算时间',
  PRIMARY KEY (id), KEY ix_kpi_result_target (kpi_user_id, calculation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可解释的指标计算历史';

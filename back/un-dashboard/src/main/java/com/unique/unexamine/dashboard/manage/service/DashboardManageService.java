package com.unique.unexamine.dashboard.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.dashboard.base.entity.UnDashboard;
import com.unique.unexamine.dashboard.base.entity.UnDashboardComponent;
import com.unique.unexamine.dashboard.base.entity.UnDashboardUser;
import com.unique.unexamine.dashboard.base.service.IUnDashboardComponentService;
import com.unique.unexamine.dashboard.base.service.IUnDashboardService;
import com.unique.unexamine.dashboard.base.service.IUnDashboardUserService;
import com.unique.unexamine.dashboard.manage.dto.DashboardManageDto.ComponentView;
import com.unique.unexamine.dashboard.manage.dto.DashboardManageDto.AccessRequest;
import com.unique.unexamine.dashboard.manage.dto.DashboardManageDto.DashboardView;
import com.unique.unexamine.dashboard.manage.dto.DashboardManageDto.SaveComponentRequest;
import com.unique.unexamine.dashboard.manage.dto.DashboardManageDto.SaveRequest;
import com.unique.unexamine.dashboard.base.entity.UnDataSource;
import com.unique.unexamine.dashboard.base.service.IUnDataSourceService;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.sys.base.entity.UnSysDepartment;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysRole;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysDepartmentService;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysRoleService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.manage.service.ModuleConfigService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 仪表盘草稿、组件和发布快照管理；发布前的配置不会被运行接口读取。 */
@Service
public class DashboardManageService {
    private final IUnDashboardService dashboards;
    private final IUnDashboardComponentService components;
    private final IUnDashboardUserService dashboardUsers;
    private final IUnDataSourceService dataSources;
    private final IUnPlatAccountService accounts;
    private final IUnSysMemberService members;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnSysDepartmentService departments;
    private final IUnSysRoleService roles;
    private final IUnSysLogOptService logs;
    private final SystemManageService systems;
    private final ModuleConfigService modules;
    private final DashboardRuntimeService runtime;
    private final ObjectMapper json;

    /** 注入仪表盘基础服务、系统管理权限和审计日志。 */
    public DashboardManageService(IUnDashboardService dashboards,
            IUnDashboardComponentService components, IUnSysLogOptService logs,
            IUnDashboardUserService dashboardUsers, IUnDataSourceService dataSources,
            IUnPlatAccountService accounts, IUnSysMemberService members,
            IUnSysTenantMemberService tenantMembers, IUnSysDepartmentService departments,
            IUnSysRoleService roles, SystemManageService systems,
            ModuleConfigService modules, DashboardRuntimeService runtime, ObjectMapper json) {
        this.dashboards = dashboards;
        this.components = components;
        this.dashboardUsers = dashboardUsers;
        this.dataSources = dataSources;
        this.accounts = accounts;
        this.members = members;
        this.tenantMembers = tenantMembers;
        this.departments = departments;
        this.roles = roles;
        this.logs = logs;
        this.systems = systems;
        this.modules = modules;
        this.runtime = runtime;
        this.json = json;
    }

    /** 查询当前租户可维护的仪表盘详情。 */
    public DashboardView query(String token, Long systemId, Long tenantId, Long dashboardId) {
        SystemContext context = systems.context(token, systemId, tenantId);
        UnDashboard dashboard = owned(context, dashboardId);
        return view(dashboard);
    }

    /** 列出当前账号可进入的仪表盘；普通成员只获取获授权的发布快照及本人的个人配置。 */
    public List<DashboardView> list(String token, Long systemId, Long tenantId) {
        SystemContext context = systems.context(token, systemId, tenantId);
        LambdaQueryWrapper<UnDashboard> query = new LambdaQueryWrapper<UnDashboard>()
                .eq(UnDashboard::getSystemId, context.systemId())
                .and(scope -> scope.isNull(UnDashboard::getTenantId)
                        .or().eq(UnDashboard::getTenantId, context.tenantId()));
        boolean administrator = context.owner() || context.tenantAdmin();
        return dashboards.list(query.orderByDesc(UnDashboard::getUpdateTime)
                        .orderByDesc(UnDashboard::getId)).stream()
                .filter(dashboard -> !"PERSONAL".equals(dashboard.getScopeType())
                        || context.accountId().equals(dashboard.getOwnerAccountId()))
                .filter(dashboard -> administrator || runtime.canAccess(dashboard, context))
                .filter(dashboard -> administrator || "PERSONAL".equals(dashboard.getScopeType())
                        || "PUBLISHED".equals(dashboard.getStatus()))
                .map(dashboard -> administrator || "PERSONAL".equals(dashboard.getScopeType())
                        ? view(dashboard) : publishedView(dashboard))
                .toList();
    }

    /** 保存仪表盘草稿；系统级和租户级仪表盘要求管理员，个人仪表盘归当前账号。 */
    @Transactional(rollbackFor = Exception.class)
    public DashboardView save(String token, Long systemId, Long tenantId, SaveRequest request) {
        if (request == null || request.scopeType() == null || request.scopeType().isBlank()
                || request.code() == null || request.code().isBlank() || request.code().length() > 80
                || request.name() == null || request.name().isBlank() || request.name().length() > 120)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "仪表盘资料无效");
        SystemContext context = systems.context(token, systemId, tenantId);
        validateScope(request.scopeType(), request.moduleId());
        validateModule(context, request.moduleId());
        JsonNode layout = object(request.layout(), "仪表盘布局");
        UnDashboard dashboard = request.id() == null ? new UnDashboard() : ownedForUpdate(context, request.id());
        boolean existing = dashboard.getId() != null;
        if (!context.owner() && !context.tenantAdmin()
                && !"PERSONAL".equalsIgnoreCase(request.scopeType())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "缺少仪表盘配置权限");
        }
        if (dashboard.getId() == null) {
            dashboard.setSystemId(context.systemId());
            dashboard.setCreateAccountId(context.accountId());
            dashboard.setCreateTime(LocalDateTime.now());
            dashboard.setPublishedVersion(0);
        }
        dashboard.setScopeType(request.scopeType().trim().toUpperCase());
        dashboard.setTenantId(tenantFor(request.scopeType(), context));
        dashboard.setModuleId(request.moduleId());
        dashboard.setOwnerAccountId("PERSONAL".equalsIgnoreCase(request.scopeType())
                ? context.accountId() : null);
        dashboard.setCode(request.code().trim());
        dashboard.setName(request.name().trim());
        dashboard.setLayoutJson(write(layout));
        dashboard.setStatus("DRAFT");
        dashboard.setUpdateTime(LocalDateTime.now());
        dashboards.addOrUpdate(dashboard);
        if (existing && (dashboard.getTenantId() == null || dashboard.getModuleId() == null
                || dashboard.getOwnerAccountId() == null)) {
            // 作用域切换会清空旧归属；基础更新默认跳过 null，需显式落库。
            var clear = dashboards.lambdaUpdate().eq(UnDashboard::getId, dashboard.getId());
            if (dashboard.getTenantId() == null) clear.set(UnDashboard::getTenantId, null);
            if (dashboard.getModuleId() == null) clear.set(UnDashboard::getModuleId, null);
            if (dashboard.getOwnerAccountId() == null) clear.set(UnDashboard::getOwnerAccountId, null);
            clear.update();
        }
        audit(context, "DASHBOARD_SAVE", dashboard.getId());
        return view(dashboard);
    }

    /** 保存组件草稿并校验数据源与模块只能选一个。 */
    @Transactional(rollbackFor = Exception.class)
    public DashboardView saveComponent(String token, Long systemId, Long tenantId,
                                       Long dashboardId, SaveComponentRequest request) {
        if (request == null || request.componentCode() == null || request.componentCode().isBlank()
                || request.componentCode().length() > 80 || request.componentType() == null
                || request.componentType().isBlank() || request.componentType().length() > 40
                || request.sortNo() != null && request.sortNo() < 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "仪表盘组件资料无效");
        SystemContext context = systems.context(token, systemId, tenantId);
        UnDashboard dashboard = ownedForUpdate(context, dashboardId);
        validateModule(context, dashboard.getModuleId());
        if (request.moduleId() != null && request.dataSourceId() != null) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "组件不能同时绑定模块和数据源");
        }
        validateModule(context, request.moduleId());
        if (request.dataSourceId() != null) validateDataSource(context, request.dataSourceId(), false);
        JsonNode config = object(request.config(), "组件配置");
        UnDashboardComponent component = request.id() == null ? new UnDashboardComponent()
                : components.getById(request.id());
        if (request.id() != null && component == null) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "仪表盘组件不存在");
        }
        if (component.getId() != null && !dashboardId.equals(component.getDashboardId())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "组件不属于当前仪表盘");
        }
        component.setDashboardId(dashboardId);
        component.setComponentCode(request.componentCode().trim());
        component.setComponentType(request.componentType().trim().toUpperCase());
        component.setDataSourceId(request.dataSourceId());
        component.setModuleId(request.moduleId());
        component.setConfigJson(write(config));
        component.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        component.setStatus("DRAFT");
        component.setUpdateTime(LocalDateTime.now());
        components.addOrUpdate(component);
        if (request.id() != null && (request.moduleId() == null || request.dataSourceId() == null)) {
            // 模块与数据源互斥，切换时旧来源列必须真正清空。
            var clear = components.lambdaUpdate().eq(UnDashboardComponent::getId, component.getId());
            if (request.moduleId() == null) clear.set(UnDashboardComponent::getModuleId, null);
            if (request.dataSourceId() == null) clear.set(UnDashboardComponent::getDataSourceId, null);
            clear.update();
        }
        dashboard.setStatus("DRAFT");
        dashboard.setUpdateTime(LocalDateTime.now());
        dashboards.addOrUpdate(dashboard);
        audit(context, "DASHBOARD_COMPONENT_SAVE", component.getId());
        return view(dashboard);
    }

    /** 替换仪表盘访问范围；保存前核对主体仍属于当前系统租户且处于启用状态。 */
    @Transactional(rollbackFor = Exception.class)
    public DashboardView saveAccess(String token, Long systemId, Long tenantId,
                                    Long dashboardId, AccessRequest request) {
        SystemContext context = systems.context(token, systemId, tenantId);
        UnDashboard dashboard = ownedForUpdate(context, dashboardId);
        if (!context.owner() && !context.tenantAdmin()) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "缺少仪表盘访问范围配置权限");
        }
        if (request == null || request.subjects() == null) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "访问主体列表不能为空");
        }
        Set<String> seen = new HashSet<>();
        for (var subject : request.subjects()) {
            if (subject == null || subject.subjectType() == null || subject.subjectId() == null
                    || subject.subjectId() < 1) {
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "访问主体无效");
            }
            String type = subject.subjectType().trim().toUpperCase();
            if (!Set.of("ACCOUNT", "MEMBER", "TENANT_MEMBER", "DEPARTMENT", "ROLE").contains(type)
                    || !seen.add(type + ":" + subject.subjectId())) {
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "访问主体类型无效或重复");
            }
            validateAccessSubject(context, type, subject.subjectId());
        }
        dashboardUsers.remove(new LambdaQueryWrapper<UnDashboardUser>()
                .eq(UnDashboardUser::getDashboardId, dashboardId));
        for (var subject : request.subjects()) {
            UnDashboardUser row = new UnDashboardUser();
            row.setDashboardId(dashboardId);
            row.setSubjectType(subject.subjectType().trim().toUpperCase());
            row.setSubjectId(subject.subjectId());
            row.setCreateTime(LocalDateTime.now());
            dashboardUsers.addOrUpdate(row);
        }
        audit(context, "DASHBOARD_ACCESS_SAVE", dashboardId);
        return view(dashboard);
    }

    /** 访问主体必须属于当前系统及租户，不能用同主键的其他租户对象越权。 */
    private void validateAccessSubject(SystemContext context, String type, Long subjectId) {
        boolean valid;
        if ("ACCOUNT".equals(type)) {
            UnPlatAccount account = accounts.queryById(subjectId);
            UnSysMember member = members.getOne(new LambdaQueryWrapper<UnSysMember>()
                    .eq(UnSysMember::getSystemId, context.systemId()).eq(UnSysMember::getAccountId, subjectId)
                    .eq(UnSysMember::getStatus, "ACTIVE"), false);
            UnSysTenantMember link = member == null ? null : tenantMembers.getOne(new LambdaQueryWrapper<UnSysTenantMember>()
                    .eq(UnSysTenantMember::getSystemId, context.systemId()).eq(UnSysTenantMember::getTenantId, context.tenantId())
                    .eq(UnSysTenantMember::getMemberId, member.getId()).eq(UnSysTenantMember::getStatus, "ACTIVE"), false);
            valid = account != null && "ACTIVE".equals(account.getStatus()) && link != null;
        } else if ("MEMBER".equals(type)) {
            UnSysMember member = members.queryById(subjectId);
            // 系统成员主键在所有租户共用，授权当前租户仪表盘时仍须有有效的租户成员映射。
            valid = member != null && context.systemId().equals(member.getSystemId())
                    && "ACTIVE".equals(member.getStatus())
                    && tenantMembers.count(new LambdaQueryWrapper<UnSysTenantMember>()
                    .eq(UnSysTenantMember::getSystemId, context.systemId())
                    .eq(UnSysTenantMember::getTenantId, context.tenantId())
                    .eq(UnSysTenantMember::getMemberId, subjectId)
                    .eq(UnSysTenantMember::getStatus, "ACTIVE")) > 0;
        } else if ("TENANT_MEMBER".equals(type)) {
            UnSysTenantMember member = tenantMembers.queryById(subjectId);
            valid = member != null && context.systemId().equals(member.getSystemId())
                    && context.tenantId().equals(member.getTenantId()) && "ACTIVE".equals(member.getStatus());
        } else if ("DEPARTMENT".equals(type)) {
            UnSysDepartment department = departments.queryById(subjectId);
            valid = department != null && context.systemId().equals(department.getSystemId())
                    && context.tenantId().equals(department.getTenantId()) && "ACTIVE".equals(department.getStatus());
        } else {
            UnSysRole role = roles.queryById(subjectId);
            valid = role != null && context.systemId().equals(role.getSystemId())
                    && context.tenantId().equals(role.getTenantId()) && "ACTIVE".equals(role.getStatus());
        }
        if (!valid) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "访问主体不存在、停用或不属于当前系统租户");
    }

    /** 将布局和全部组件快照一次性发布，运行查询随后只读取这些快照。 */
    @Transactional(rollbackFor = Exception.class)
    public DashboardView publish(String token, Long systemId, Long tenantId, Long dashboardId) {
        SystemContext context = systems.context(token, systemId, tenantId);
        UnDashboard dashboard = ownedForUpdate(context, dashboardId);
        validateModule(context, dashboard.getModuleId());
        JsonNode layout = object(readJson(dashboard.getLayoutJson()), "仪表盘布局");
        List<UnDashboardComponent> current = components.list(new LambdaQueryWrapper<UnDashboardComponent>()
                .eq(UnDashboardComponent::getDashboardId, dashboardId)
                .orderByAsc(UnDashboardComponent::getSortNo).orderByAsc(UnDashboardComponent::getId));
        current.stream().map(UnDashboardComponent::getModuleId).filter(java.util.Objects::nonNull)
                .forEach(moduleId -> validateModule(context, moduleId));
        current.stream().map(UnDashboardComponent::getDataSourceId).filter(java.util.Objects::nonNull)
                .forEach(dataSourceId -> validateDataSource(context, dataSourceId, true));
        int version = (dashboard.getPublishedVersion() == null ? 0 : dashboard.getPublishedVersion()) + 1;
        dashboard.setPublishedLayoutJson(write(layout));
        dashboard.setPublishedVersion(version);
        dashboard.setStatus("PUBLISHED");
        dashboard.setUpdateTime(LocalDateTime.now());
        dashboards.addOrUpdate(dashboard);
        for (UnDashboardComponent component : current) {
            component.setPublishedConfigJson(component.getConfigJson());
            component.setStatus("PUBLISHED");
            component.setUpdateTime(LocalDateTime.now());
            components.addOrUpdate(component);
        }
        audit(context, "DASHBOARD_PUBLISH", dashboardId);
        return view(dashboard);
    }

    private void validateScope(String scope, Long moduleId) {
        if (scope == null || !List.of("SYSTEM", "TENANT", "MODULE", "PERSONAL")
                .contains(scope.trim().toUpperCase())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "仪表盘作用域无效");
        }
        if ("MODULE".equalsIgnoreCase(scope) && moduleId == null) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模块仪表盘必须指定模块");
        }
    }

    private Long tenantFor(String scope, SystemContext context) {
        return "SYSTEM".equalsIgnoreCase(scope) ? null : context.tenantId();
    }

    private UnDashboard owned(SystemContext context, Long dashboardId) {
        if (dashboardId == null || dashboardId < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "仪表盘主键无效");
        }
        UnDashboard dashboard = dashboards.queryById(dashboardId);
        if (dashboard == null || !context.systemId().equals(dashboard.getSystemId())
                || dashboard.getTenantId() != null && !context.tenantId().equals(dashboard.getTenantId())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "仪表盘不存在");
        }
        if ("PERSONAL".equalsIgnoreCase(dashboard.getScopeType())
                && !context.accountId().equals(dashboard.getOwnerAccountId())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "没有个人仪表盘权限");
        }
        if (!context.owner() && !context.tenantAdmin() && !"PERSONAL".equalsIgnoreCase(dashboard.getScopeType())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "缺少仪表盘配置权限");
        }
        return dashboard;
    }

    /** 写入仪表盘前锁定所属行，串行化组件草稿和发布版本递增。 */
    private UnDashboard ownedForUpdate(SystemContext context, Long dashboardId) {
        if (dashboardId == null || dashboardId < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "仪表盘主键无效");
        }
        UnDashboard dashboard = dashboards.getOne(new LambdaQueryWrapper<UnDashboard>()
                .eq(UnDashboard::getId, dashboardId).last("FOR UPDATE"), false);
        if (dashboard == null || !context.systemId().equals(dashboard.getSystemId())
                || dashboard.getTenantId() != null && !context.tenantId().equals(dashboard.getTenantId())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "仪表盘不存在");
        }
        if ("PERSONAL".equalsIgnoreCase(dashboard.getScopeType())
                && !context.accountId().equals(dashboard.getOwnerAccountId())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "没有个人仪表盘权限");
        }
        if (!context.owner() && !context.tenantAdmin() && !"PERSONAL".equalsIgnoreCase(dashboard.getScopeType())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "缺少仪表盘配置权限");
        }
        return dashboard;
    }

    /** 仪表盘直接绑定的模块必须属于当前系统和当前租户可见范围。 */
    private void validateModule(SystemContext context, Long moduleId) {
        if (moduleId != null && moduleId < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模块主键无效");
        }
        if (moduleId != null) modules.activeModule(context.systemId(), moduleId, context.tenantId());
    }

    /** 数据源草稿可被组件引用，但发布仪表盘前必须属于当前范围且已经发布。 */
    private void validateDataSource(SystemContext context, Long dataSourceId, boolean published) {
        if (dataSourceId == null || dataSourceId < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "数据源主键无效");
        }
        UnDataSource source = dataSources.queryById(dataSourceId);
        if (source == null || !context.systemId().equals(source.getSystemId())
                || (source.getTenantId() != null && !context.tenantId().equals(source.getTenantId()))) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "数据源不存在或不属于当前系统租户");
        }
        if (published && (!"PUBLISHED".equals(source.getStatus())
                || source.getRootModuleId() == null)) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "仪表盘发布前必须使用已发布且配置起始模块的数据源");
        }
        if (source.getRootModuleId() != null) validateModule(context, source.getRootModuleId());
    }

    private DashboardView view(UnDashboard dashboard) {
        List<ComponentView> componentViews = components.list(new LambdaQueryWrapper<UnDashboardComponent>()
                .eq(UnDashboardComponent::getDashboardId, dashboard.getId())
                .orderByAsc(UnDashboardComponent::getSortNo).orderByAsc(UnDashboardComponent::getId))
                .stream().map(component -> new ComponentView(component.getId(), component.getDashboardId(),
                        component.getComponentCode(), component.getComponentType(), component.getDataSourceId(),
                        component.getModuleId(), component.getStatus(), component.getSortNo(),
                        readJson(component.getConfigJson()), readJson(component.getPublishedConfigJson())))
                .toList();
        return new DashboardView(dashboard.getId(), dashboard.getScopeType(), dashboard.getSystemId(),
                dashboard.getTenantId(), dashboard.getModuleId(), dashboard.getOwnerAccountId(), dashboard.getCode(),
                dashboard.getName(), dashboard.getStatus(), dashboard.getPublishedVersion(),
                readJson(dashboard.getLayoutJson()), readJson(dashboard.getPublishedLayoutJson()),
                dashboard.getUpdateTime(), componentViews);
    }

    /** 普通成员的列表仅返回发布快照，避免草稿布局和组件配置提前泄露。 */
    private DashboardView publishedView(UnDashboard dashboard) {
        List<ComponentView> componentViews = components.list(new LambdaQueryWrapper<UnDashboardComponent>()
                .eq(UnDashboardComponent::getDashboardId, dashboard.getId())
                .eq(UnDashboardComponent::getStatus, "PUBLISHED")
                .orderByAsc(UnDashboardComponent::getSortNo).orderByAsc(UnDashboardComponent::getId))
                .stream().map(component -> new ComponentView(component.getId(), component.getDashboardId(),
                        component.getComponentCode(), component.getComponentType(), component.getDataSourceId(),
                        component.getModuleId(), component.getStatus(), component.getSortNo(),
                        null, readJson(component.getPublishedConfigJson())))
                .toList();
        return new DashboardView(dashboard.getId(), dashboard.getScopeType(), dashboard.getSystemId(),
                dashboard.getTenantId(), dashboard.getModuleId(), dashboard.getOwnerAccountId(), dashboard.getCode(),
                dashboard.getName(), dashboard.getStatus(), dashboard.getPublishedVersion(), null,
                readJson(dashboard.getPublishedLayoutJson()), dashboard.getUpdateTime(), componentViews);
    }

    private JsonNode object(JsonNode node, String label) {
        if (node == null || !node.isObject()) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, label + "必须是 JSON 对象");
        }
        return node;
    }

    private JsonNode readJson(String text) {
        if (text == null || text.isBlank()) return null;
        try { return json.readTree(text); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "已保存配置格式无效"); }
    }

    private String write(JsonNode node) {
        try { return json.writeValueAsString(node); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "配置无法保存"); }
    }

    private void audit(SystemContext context, String actionCode, Long objectId) {
        UnSysLogOpt log = new UnSysLogOpt();
        log.setSystemId(context.systemId());
        log.setTenantId(context.tenantId());
        log.setAccountId(context.accountId());
        log.setActionCode(actionCode);
        log.setObjectType("DASHBOARD");
        log.setObjectId(objectId);
        logs.addOrUpdate(log);
    }
}

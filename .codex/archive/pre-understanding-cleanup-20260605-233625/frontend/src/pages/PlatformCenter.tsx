import { App, Button, Input, Space, Tabs } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { CommonStatus, PermissionType } from '../api/enums';
import { api } from '../api/sdk';
import type { PlatformManageVO } from '../api/types';
import { BaseTable, ContextGate, CreateModal, PageTitle, RowActions, StatusTag } from './shared';

type PlatformKey = 'systems' | 'tenants' | 'accounts' | 'roles' | 'permissions';

function parseIdList(value?: string): number[] {
  return (value || '')
    .split(',')
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isFinite(item));
}

export default function PlatformCenter() {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<Record<PlatformKey, PlatformManageVO[]>>({
    systems: [],
    tenants: [],
    accounts: [],
    roles: [],
    permissions: []
  });

  const refresh = async () => {
    setLoading(true);
    try {
      const [systems, tenants, accounts, roles, permissions] = await Promise.all([
        api.platform.systems(),
        api.platform.tenants(),
        api.platform.accounts(),
        api.platform.roles(),
        api.platform.permissions()
      ]);
      setRows({ systems, tenants, accounts, roles, permissions });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '平台数据加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
  }, []);

  const commonColumns: ColumnsType<PlatformManageVO> = useMemo(
    () => [
      { title: 'ID', dataIndex: 'id', width: 90 },
      { title: 'code', dataIndex: 'code', width: 180 },
      { title: 'name', dataIndex: 'name', width: 180 },
      { title: 'status', dataIndex: 'status', width: 120, render: (status) => <StatusTag status={status} /> },
      { title: 'type', dataIndex: 'type', width: 130 },
      { title: 'tenantId', dataIndex: 'tenantId', width: 110 },
      { title: 'systemId', dataIndex: 'systemId', width: 110 },
      { title: 'appId', dataIndex: 'appId', width: 90 },
      { title: 'moduleId', dataIndex: 'moduleId', width: 100 },
      { title: 'resourcePath', dataIndex: 'resourcePath', width: 220 },
      { title: 'updatedAt', dataIndex: 'updatedAt', width: 180 }
    ],
    []
  );

  return (
    <>
      <PageTitle title="平台中心" description="系统、租户、账号、角色与权限点均调用平台 manage API。" />
      <Tabs
        items={[
          {
            key: 'systems',
            label: '系统',
            children: (
              <section className="section">
                <BaseTable
                  loading={loading}
                  data={rows.systems}
                  columns={[
                    ...commonColumns,
                    {
                      title: '操作',
                      fixed: 'right',
                      render: (_, record) => (
                        <RowActions>
                          <Button size="small" onClick={() => api.platform.setSystemStatus(record.id, CommonStatus.Enabled).then(refresh)}>
                            启用
                          </Button>
                          <Button size="small" onClick={() => api.platform.setSystemStatus(record.id, CommonStatus.Disabled).then(refresh)}>
                            停用
                          </Button>
                        </RowActions>
                      )
                    }
                  ]}
                  extra={
                    <CreateModal
                      title="创建系统"
                      fields={[
                        { name: 'systemCode', label: 'systemCode', required: true },
                        { name: 'systemName', label: 'systemName', required: true },
                        { name: 'description', label: 'description', type: 'textarea' }
                      ]}
                      onSubmit={async (values) => {
                        await api.platform.createSystem({
                          systemCode: String(values.systemCode),
                          systemName: String(values.systemName),
                          description: values.description ? String(values.description) : undefined
                        });
                        message.success('系统已创建');
                        await refresh();
                      }}
                    />
                  }
                />
              </section>
            )
          },
          {
            key: 'tenants',
            label: '租户',
            children: (
              <section className="section">
                <BaseTable
                  loading={loading}
                  data={rows.tenants}
                  columns={[
                    ...commonColumns,
                    {
                      title: '操作',
                      fixed: 'right',
                      render: (_, record) => (
                        <RowActions>
                          <Button size="small" onClick={() => api.platform.setTenantStatus(record.id, CommonStatus.Enabled).then(refresh)}>
                            启用
                          </Button>
                          <Button size="small" onClick={() => api.platform.setTenantStatus(record.id, CommonStatus.Disabled).then(refresh)}>
                            停用
                          </Button>
                        </RowActions>
                      )
                    }
                  ]}
                  extra={
                    <CreateModal
                      title="创建租户"
                      fields={[
                        { name: 'tenantCode', label: 'tenantCode', required: true },
                        { name: 'tenantName', label: 'tenantName', required: true },
                        { name: 'adminAccountId', label: 'adminAccountId', type: 'number' },
                        { name: 'expireAt', label: 'expireAt' },
                        { name: 'configJson', label: 'configJson', type: 'textarea' }
                      ]}
                      onSubmit={async (values) => {
                        await api.platform.createTenant({
                          tenantCode: String(values.tenantCode),
                          tenantName: String(values.tenantName),
                          adminAccountId: values.adminAccountId ? Number(values.adminAccountId) : undefined,
                          expireAt: values.expireAt ? String(values.expireAt) : undefined,
                          configJson: values.configJson ? String(values.configJson) : undefined
                        });
                        message.success('租户已创建');
                        await refresh();
                      }}
                    />
                  }
                />
              </section>
            )
          },
          {
            key: 'accounts',
            label: '账号',
            children: (
              <section className="section">
                <BaseTable
                  loading={loading}
                  data={rows.accounts}
                  columns={[
                    ...commonColumns,
                    { title: 'mobile', dataIndex: 'mobile', width: 140 },
                    { title: 'email', dataIndex: 'email', width: 180 },
                    {
                      title: '操作',
                      fixed: 'right',
                      render: (_, record) => (
                        <RowActions>
                          <Button size="small" onClick={() => api.platform.setAccountStatus(record.id, CommonStatus.Enabled).then(refresh)}>
                            启用
                          </Button>
                          <Button size="small" onClick={() => api.platform.setAccountStatus(record.id, CommonStatus.Disabled).then(refresh)}>
                            停用
                          </Button>
                        </RowActions>
                      )
                    }
                  ]}
                  extra={
                    <CreateModal
                      title="创建账号"
                      fields={[
                        { name: 'username', label: 'username', required: true },
                        { name: 'displayName', label: 'displayName', required: true },
                        { name: 'mobile', label: 'mobile' },
                        { name: 'email', label: 'email' },
                        { name: 'password', label: 'password', type: 'password', required: true }
                      ]}
                      onSubmit={async (values) => {
                        await api.platform.createAccount({
                          username: String(values.username),
                          displayName: String(values.displayName),
                          mobile: values.mobile ? String(values.mobile) : undefined,
                          email: values.email ? String(values.email) : undefined,
                          password: String(values.password)
                        });
                        message.success('账号已创建');
                        await refresh();
                      }}
                    />
                  }
                />
              </section>
            )
          },
          {
            key: 'roles',
            label: '角色',
            children: (
              <section className="section">
                <ContextGate required={['tenantId', 'systemId']}>
                  <BaseTable
                    loading={loading}
                    data={rows.roles}
                    columns={commonColumns}
                    extra={
                      <Space wrap>
                        <CreateModal
                          title="创建角色"
                          fields={[
                            { name: 'roleCode', label: 'roleCode', required: true },
                            { name: 'roleName', label: 'roleName', required: true },
                            { name: 'roleType', label: 'roleType', required: true },
                            { name: 'appId', label: 'appId', type: 'number' }
                          ]}
                          onSubmit={async (values) => {
                            await api.platform.createRole({
                              roleCode: String(values.roleCode),
                              roleName: String(values.roleName),
                              roleType: String(values.roleType),
                              appId: values.appId ? Number(values.appId) : undefined
                            });
                            message.success('角色已创建');
                            await refresh();
                          }}
                        />
                        <CreateModal
                          title="替换角色权限"
                          buttonText="授权角色"
                          fields={[
                            { name: 'roleId', label: 'roleId', type: 'number', required: true },
                            { name: 'permissionIdsText', label: 'permissionIds', required: true, placeholder: '例如 1,2,3' }
                          ]}
                          onSubmit={async (values) => {
                            await api.platform.assignRolePermissions({
                              roleId: Number(values.roleId),
                              permissionIds: parseIdList(String(values.permissionIdsText))
                            });
                            message.success('角色权限已替换');
                            await refresh();
                          }}
                        />
                      </Space>
                    }
                  />
                </ContextGate>
              </section>
            )
          },
          {
            key: 'permissions',
            label: '权限点',
            children: (
              <section className="section">
                <ContextGate required={['tenantId', 'systemId']}>
                  <BaseTable
                    loading={loading}
                    data={rows.permissions}
                    columns={commonColumns}
                    extra={
                      <Space wrap>
                        <CreateModal
                          title="创建权限点"
                          fields={[
                            { name: 'permissionCode', label: 'permissionCode', required: true },
                            { name: 'permissionName', label: 'permissionName', required: true },
                            {
                              name: 'permissionType',
                              label: 'permissionType',
                              type: 'select',
                              required: true,
                              options: Object.values(PermissionType).map((value) => ({ value, label: value }))
                            },
                            { name: 'resourcePath', label: 'resourcePath' },
                            { name: 'appId', label: 'appId', type: 'number' },
                            { name: 'moduleId', label: 'moduleId', type: 'number' }
                          ]}
                          onSubmit={async (values) => {
                            await api.platform.createPermission({
                              permissionCode: String(values.permissionCode),
                              permissionName: String(values.permissionName),
                              permissionType: String(values.permissionType),
                              resourcePath: values.resourcePath ? String(values.resourcePath) : undefined,
                              appId: values.appId ? Number(values.appId) : undefined,
                              moduleId: values.moduleId ? Number(values.moduleId) : undefined
                            });
                            message.success('权限点已创建');
                            await refresh();
                          }}
                        />
                        <CreateModal
                          title="替换账号角色"
                          buttonText="账号授权"
                          fields={[
                            { name: 'accountId', label: 'accountId', type: 'number', required: true },
                            { name: 'roleIdsText', label: 'roleIds', required: true, placeholder: '例如 1,2,3' }
                          ]}
                          onSubmit={async (values) => {
                            await api.platform.assignAccountRoles({
                              accountId: Number(values.accountId),
                              roleIds: parseIdList(String(values.roleIdsText))
                            });
                            message.success('账号角色已替换');
                            await refresh();
                          }}
                        />
                      </Space>
                    }
                  />
                </ContextGate>
              </section>
            )
          }
        ]}
      />
    </>
  );
}

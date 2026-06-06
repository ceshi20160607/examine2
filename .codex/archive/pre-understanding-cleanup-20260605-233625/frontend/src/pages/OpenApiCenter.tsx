import { App, Space, Tabs } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/sdk';
import type { AppManageVO } from '../api/types';
import { BaseTable, ContextGate, CreateModal, JsonBlock, PageTitle, StatusTag } from './shared';

export default function OpenApiCenter() {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [clients, setClients] = useState<AppManageVO[]>([]);
  const [logs, setLogs] = useState<AppManageVO[]>([]);

  const refresh = async () => {
    setLoading(true);
    try {
      const [clientRows, logRows] = await Promise.all([api.apps.clients(), api.apps.accessLogs()]);
      setClients(clientRows);
      setLogs(logRows);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'OpenAPI 数据加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
  }, []);

  const columns: ColumnsType<AppManageVO> = useMemo(
    () => [
      { title: 'ID', dataIndex: 'id', width: 90 },
      { title: 'clientId', dataIndex: 'clientId', width: 110 },
      { title: 'code', dataIndex: 'code', width: 180 },
      { title: 'name', dataIndex: 'name', width: 180 },
      { title: 'status', dataIndex: 'status', width: 120, render: (status) => <StatusTag status={status} /> },
      { title: 'type', dataIndex: 'type', width: 120 },
      { title: 'tenantId', dataIndex: 'tenantId', width: 110 },
      { title: 'systemId', dataIndex: 'systemId', width: 110 },
      { title: 'appId', dataIndex: 'appId', width: 90 },
      { title: 'moduleId', dataIndex: 'moduleId', width: 100 },
      { title: 'value', dataIndex: 'value', width: 240 },
      { title: 'detail', dataIndex: 'detail', width: 300, render: (value) => <JsonBlock value={value} /> },
      { title: 'expiredAt', dataIndex: 'expiredAt', width: 180 },
      { title: 'updatedAt', dataIndex: 'updatedAt', width: 180 }
    ],
    []
  );

  return (
    <>
      <PageTitle title="OpenAPI 中心" description="管理客户端、凭证、授权范围、IP 白名单、幂等记录并查看访问日志。" />
      <Tabs
        items={[
          {
            key: 'clients',
            label: '客户端',
            children: (
              <section className="section">
                <ContextGate required={['tenantId', 'systemId']}>
                  <BaseTable
                    loading={loading}
                    data={clients}
                    columns={columns}
                    extra={
                      <CreateModal
                        title="创建 OpenAPI 客户端"
                        fields={[
                          { name: 'clientCode', label: 'clientCode', required: true },
                          { name: 'clientName', label: 'clientName', required: true },
                          { name: 'rateLimitPerMinute', label: 'rateLimitPerMinute', type: 'number' },
                          { name: 'expiredAt', label: 'expiredAt' }
                        ]}
                        onSubmit={async (values) => {
                          await api.apps.createClient({
                            clientCode: String(values.clientCode),
                            clientName: String(values.clientName),
                            rateLimitPerMinute: values.rateLimitPerMinute ? Number(values.rateLimitPerMinute) : undefined,
                            expiredAt: values.expiredAt ? String(values.expiredAt) : undefined
                          });
                          message.success('OpenAPI 客户端已创建');
                          await refresh();
                        }}
                      />
                    }
                  />
                </ContextGate>
              </section>
            )
          },
          {
            key: 'security',
            label: '凭证与授权',
            children: (
              <section className="section">
                <Space wrap>
                  <CreateModal
                    title="创建 OpenAPI 凭证"
                    fields={[
                      { name: 'clientId', label: 'clientId', type: 'number', required: true },
                      { name: 'accessKey', label: 'accessKey', required: true },
                      { name: 'secret', label: 'secret', required: true },
                      { name: 'signAlgorithm', label: 'signAlgorithm', required: true }
                    ]}
                    onSubmit={async (values) => {
                      await api.apps.createCredential({
                        clientId: Number(values.clientId),
                        accessKey: String(values.accessKey),
                        secret: String(values.secret),
                        signAlgorithm: String(values.signAlgorithm)
                      });
                      message.success('凭证已创建');
                    }}
                  />
                  <CreateModal
                    title="创建 OpenAPI scope"
                    buttonText="新增 scope"
                    fields={[
                      { name: 'clientId', label: 'clientId', type: 'number', required: true },
                      { name: 'appId', label: 'appId', type: 'number', required: true },
                      { name: 'moduleId', label: 'moduleId', type: 'number' },
                      { name: 'scopeCode', label: 'scopeCode', required: true },
                      { name: 'actions', label: 'actions', required: true }
                    ]}
                    onSubmit={async (values) => {
                      await api.apps.createScope({
                        clientId: Number(values.clientId),
                        appId: Number(values.appId),
                        moduleId: values.moduleId ? Number(values.moduleId) : undefined,
                        scopeCode: String(values.scopeCode),
                        actions: String(values.actions)
                      });
                      message.success('scope 已创建');
                    }}
                  />
                  <CreateModal
                    title="创建 IP 白名单"
                    buttonText="新增 IP"
                    fields={[
                      { name: 'clientId', label: 'clientId', type: 'number', required: true },
                      { name: 'ipValue', label: 'ipValue', required: true }
                    ]}
                    onSubmit={async (values) => {
                      await api.apps.createIpWhitelist({
                        clientId: Number(values.clientId),
                        ipValue: String(values.ipValue)
                      });
                      message.success('IP 白名单已创建');
                    }}
                  />
                  <CreateModal
                    title="创建幂等记录"
                    buttonText="新增幂等"
                    fields={[
                      { name: 'clientId', label: 'clientId', type: 'number', required: true },
                      { name: 'idempotentKey', label: 'idempotentKey', required: true },
                      { name: 'requestHash', label: 'requestHash', required: true },
                      { name: 'responseHash', label: 'responseHash' },
                      { name: 'expiredAt', label: 'expiredAt', required: true }
                    ]}
                    onSubmit={async (values) => {
                      await api.apps.createIdempotent({
                        clientId: Number(values.clientId),
                        idempotentKey: String(values.idempotentKey),
                        requestHash: String(values.requestHash),
                        responseHash: values.responseHash ? String(values.responseHash) : undefined,
                        expiredAt: String(values.expiredAt)
                      });
                      message.success('幂等记录已创建');
                    }}
                  />
                </Space>
              </section>
            )
          },
          {
            key: 'logs',
            label: '访问日志',
            children: (
              <section className="section">
                <BaseTable loading={loading} data={logs} columns={columns} />
              </section>
            )
          }
        ]}
      />
    </>
  );
}

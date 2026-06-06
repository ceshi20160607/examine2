import { App, Button, Space, Tabs } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { FlowActionType } from '../api/enums';
import { api } from '../api/sdk';
import type { FlowManageVO } from '../api/types';
import { BaseTable, ContextGate, CreateModal, JsonBlock, PageTitle, RowActions, StatusTag } from './shared';

export default function FlowWorkspace() {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [templates, setTemplates] = useState<FlowManageVO[]>([]);
  const [tasks, setTasks] = useState<FlowManageVO[]>([]);

  const refresh = async () => {
    setLoading(true);
    try {
      const [templateRows, taskRows] = await Promise.all([api.flows.templates(), api.flows.tasks()]);
      setTemplates(templateRows);
      setTasks(taskRows);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '流程数据加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
  }, []);

  const columns: ColumnsType<FlowManageVO> = useMemo(
    () => [
      { title: 'ID', dataIndex: 'id', width: 90 },
      { title: 'code', dataIndex: 'code', width: 170 },
      { title: 'name', dataIndex: 'name', width: 180 },
      { title: 'status', dataIndex: 'status', width: 120, render: (status) => <StatusTag status={status} /> },
      { title: 'tenantId', dataIndex: 'tenantId', width: 110 },
      { title: 'appId', dataIndex: 'appId', width: 90 },
      { title: 'moduleId', dataIndex: 'moduleId', width: 100 },
      { title: 'recordId', dataIndex: 'recordId', width: 100 },
      { title: 'templateId', dataIndex: 'templateId', width: 110 },
      { title: 'templateVersionId', dataIndex: 'templateVersionId', width: 150 },
      { title: 'assigneeId', dataIndex: 'assigneeId', width: 110 },
      { title: 'graphJson', dataIndex: 'graphJson', width: 280, render: (value) => <JsonBlock value={value} /> },
      { title: 'updatedAt', dataIndex: 'updatedAt', width: 180 }
    ],
    []
  );

  return (
    <>
      <PageTitle title="流程工作台" description="覆盖流程模板创建、版本发布、待办查询和任务处理。" />
      <Tabs
        items={[
          {
            key: 'templates',
            label: '流程模板',
            children: (
              <section className="section">
                <ContextGate required={['tenantId', 'appId', 'moduleId']}>
                  <BaseTable
                    loading={loading}
                    data={templates}
                    columns={columns}
                    extra={
                      <Space wrap>
                        <CreateModal
                          title="创建流程模板"
                          fields={[
                            { name: 'templateCode', label: 'templateCode', required: true },
                            { name: 'templateName', label: 'templateName', required: true }
                          ]}
                          onSubmit={async (values) => {
                            await api.flows.createTemplate({
                              templateCode: String(values.templateCode),
                              templateName: String(values.templateName)
                            });
                            message.success('流程模板已创建');
                            await refresh();
                          }}
                        />
                        <CreateModal
                          title="发布流程模板"
                          buttonText="发布模板"
                          fields={[
                            { name: 'templateId', label: 'templateId', type: 'number', required: true },
                            { name: 'versionNo', label: 'versionNo', required: true },
                            { name: 'graphJson', label: 'graphJson', type: 'textarea', required: true }
                          ]}
                          onSubmit={async (values) => {
                            await api.flows.publishTemplate({
                              templateId: Number(values.templateId),
                              versionNo: String(values.versionNo),
                              graphJson: String(values.graphJson)
                            });
                            message.success('流程模板已发布');
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
            key: 'tasks',
            label: '待办任务',
            children: (
              <section className="section">
                <BaseTable
                  loading={loading}
                  data={tasks}
                  columns={[
                    ...columns,
                    {
                      title: '处理',
                      fixed: 'right',
                      width: 260,
                      render: (_, record) => (
                        <RowActions>
                          <CreateModal
                            title={`处理任务 ${record.id}`}
                            buttonText="处理"
                            fields={[
                              {
                                name: 'actionType',
                                label: 'actionType',
                                type: 'select',
                                required: true,
                                options: Object.values(FlowActionType).map((value) => ({ value, label: value }))
                              },
                              { name: 'commentText', label: 'commentText', type: 'textarea' },
                              { name: 'transferTo', label: 'transferTo', type: 'number' }
                            ]}
                            onSubmit={async (values) => {
                              await api.flows.handleTask(record.id, {
                                actionType: String(values.actionType),
                                commentText: values.commentText ? String(values.commentText) : undefined,
                                transferTo: values.transferTo ? Number(values.transferTo) : undefined
                              });
                              message.success('任务已处理');
                              await refresh();
                            }}
                          />
                        </RowActions>
                      )
                    }
                  ]}
                />
              </section>
            )
          }
        ]}
      />
    </>
  );
}

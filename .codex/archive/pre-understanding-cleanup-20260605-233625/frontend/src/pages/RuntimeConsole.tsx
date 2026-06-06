import { App, Button, Descriptions, Form, Input, InputNumber, Modal, Space } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/sdk';
import type { Id, ModuleManageVO } from '../api/types';
import { useSessionStore } from '../store/context';
import { BaseTable, ContextGate, CreateModal, JsonBlock, PageTitle, RowActions, StatusTag } from './shared';

function parseJsonRecord(raw?: string): Record<string, unknown> {
  if (!raw?.trim()) {
    return {};
  }
  return JSON.parse(raw) as Record<string, unknown>;
}

export default function RuntimeConsole() {
  const { message, modal } = App.useApp();
  const moduleId = useSessionStore((state) => state.moduleId);
  const [records, setRecords] = useState<ModuleManageVO[]>([]);
  const [detail, setDetail] = useState<ModuleManageVO | null>(null);
  const [editing, setEditing] = useState<ModuleManageVO | null>(null);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

  const refresh = async () => {
    if (!moduleId) {
      setRecords([]);
      return;
    }
    setLoading(true);
    try {
      const rows = await api.modules.records();
      setRecords(rows);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '记录加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
  }, [moduleId]);

  const columns: ColumnsType<ModuleManageVO> = useMemo(
    () => [
      { title: 'ID', dataIndex: 'id', width: 90 },
      { title: 'recordNo/code', dataIndex: 'code', width: 180 },
      { title: 'name', dataIndex: 'name', width: 160 },
      { title: 'status', dataIndex: 'status', width: 120, render: (status) => <StatusTag status={status} /> },
      { title: 'tenantId', dataIndex: 'tenantId', width: 110 },
      { title: 'systemId', dataIndex: 'systemId', width: 110 },
      { title: 'appId', dataIndex: 'appId', width: 90 },
      { title: 'moduleId', dataIndex: 'moduleId', width: 100 },
      { title: 'values', dataIndex: 'values', width: 320, render: (value) => <JsonBlock value={value} /> },
      { title: 'updatedAt', dataIndex: 'updatedAt', width: 180 },
      {
        title: '操作',
        fixed: 'right',
        width: 260,
        render: (_, record) => (
          <RowActions>
            <Button
              size="small"
              onClick={async () => {
                const data = await api.modules.detail(record.id);
                setDetail(data);
              }}
            >
              详情
            </Button>
            <Button
              size="small"
              onClick={() => {
                setEditing(record);
                form.setFieldsValue({
                  recordNo: record.code,
                  ownerAccountId: undefined,
                  deptId: undefined,
                  valuesJson: JSON.stringify(record.values || {}, null, 2)
                });
              }}
            >
              编辑
            </Button>
            <Button
              size="small"
              danger
              onClick={() => {
                modal.confirm({
                  title: '确认删除运行态记录',
                  content: `记录 ID: ${record.id}`,
                  onOk: async () => {
                    await api.modules.deleteRecord(record.id);
                    message.success('记录已删除');
                    await refresh();
                  }
                });
              }}
            >
              删除
            </Button>
          </RowActions>
        )
      }
    ],
    [form, message, modal]
  );

  const submitUpdate = async (recordId: Id) => {
    const values = await form.validateFields();
    await api.modules.updateRecord(recordId, {
      recordNo: values.recordNo,
      ownerAccountId: values.ownerAccountId,
      deptId: values.deptId,
      values: parseJsonRecord(values.valuesJson)
    });
    message.success('记录已更新');
    setEditing(null);
    await refresh();
  };

  return (
    <>
      <PageTitle title="应用运行台" description="运行态记录使用后端声明的新增、详情、更新、删除、导出与流程发起接口。" />
      <section className="section">
        <ContextGate required={['moduleId']}>
          <BaseTable
            loading={loading}
            data={records}
            columns={columns}
            extra={
              <Space wrap>
                <CreateModal
                  title="新增运行态记录"
                  fields={[
                    { name: 'recordNo', label: 'recordNo', required: true },
                    { name: 'ownerAccountId', label: 'ownerAccountId', type: 'number' },
                    { name: 'deptId', label: 'deptId', type: 'number' },
                    { name: 'valuesJson', label: 'values', type: 'textarea', required: true, placeholder: '{\"customerName\":\"示例客户\"}' }
                  ]}
                  onSubmit={async (values) => {
                    await api.modules.createRecord({
                      recordNo: String(values.recordNo),
                      ownerAccountId: values.ownerAccountId ? Number(values.ownerAccountId) : undefined,
                      deptId: values.deptId ? Number(values.deptId) : undefined,
                      values: parseJsonRecord(String(values.valuesJson))
                    });
                    message.success('记录已新增');
                    await refresh();
                  }}
                />
                <CreateModal
                  title="创建导出任务"
                  buttonText="导出"
                  fields={[{ name: 'requestJson', label: 'requestJson', type: 'textarea' }]}
                  onSubmit={async (values) => {
                    await api.modules.createExportJob({ requestJson: values.requestJson ? String(values.requestJson) : undefined });
                    message.success('导出任务已创建');
                  }}
                />
                <CreateModal
                  title="发起流程"
                  buttonText="发起流程"
                  fields={[
                    { name: 'recordId', label: 'recordId', type: 'number', required: true },
                    { name: 'templateId', label: 'templateId', type: 'number', required: true },
                    { name: 'templateVersionId', label: 'templateVersionId', type: 'number', required: true },
                    { name: 'assigneeId', label: 'assigneeId', type: 'number', required: true },
                    { name: 'taskName', label: 'taskName', required: true }
                  ]}
                  onSubmit={async (values) => {
                    await api.flows.start({
                      recordId: Number(values.recordId),
                      templateId: Number(values.templateId),
                      templateVersionId: Number(values.templateVersionId),
                      assigneeId: Number(values.assigneeId),
                      taskName: String(values.taskName)
                    });
                    message.success('流程已发起');
                  }}
                />
              </Space>
            }
          />
        </ContextGate>
      </section>
      <Modal title="记录详情" open={!!detail} footer={null} onCancel={() => setDetail(null)} width={760}>
        {detail && (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="id">{detail.id}</Descriptions.Item>
            <Descriptions.Item label="recordNo/code">{detail.code}</Descriptions.Item>
            <Descriptions.Item label="status">
              <StatusTag status={detail.status} />
            </Descriptions.Item>
            <Descriptions.Item label="moduleId">{detail.moduleId}</Descriptions.Item>
            <Descriptions.Item label="values">
              <JsonBlock value={detail.values} />
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
      <Modal
        title="编辑运行态记录"
        open={!!editing}
        onCancel={() => setEditing(null)}
        onOk={() => editing && submitUpdate(editing.id)}
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="recordNo" label="recordNo" rules={[{ required: true, message: '请填写 recordNo' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="ownerAccountId" label="ownerAccountId">
            <InputNumber className="full-width" />
          </Form.Item>
          <Form.Item name="deptId" label="deptId">
            <InputNumber className="full-width" />
          </Form.Item>
          <Form.Item name="valuesJson" label="values" rules={[{ required: true, message: '请填写 values' }]}>
            <Input.TextArea rows={5} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}

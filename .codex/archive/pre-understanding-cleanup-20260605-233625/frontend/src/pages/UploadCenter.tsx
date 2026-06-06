import { Alert, App, Button, Space, Tabs } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { UploadJobType } from '../api/enums';
import { api } from '../api/sdk';
import type { UploadManageVO } from '../api/types';
import { BaseTable, ContextGate, CreateModal, PageTitle, RowActions, StatusTag } from './shared';

export default function UploadCenter() {
  const { message, modal } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [files, setFiles] = useState<UploadManageVO[]>([]);
  const [attachments, setAttachments] = useState<UploadManageVO[]>([]);

  const refresh = async () => {
    setLoading(true);
    try {
      const fileRows = await api.uploads.files();
      setFiles(fileRows);
      setAttachments([]);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '上传中心加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
  }, []);

  const columns: ColumnsType<UploadManageVO> = useMemo(
    () => [
      { title: 'ID', dataIndex: 'id', width: 90 },
      { title: 'fileId', dataIndex: 'fileId', width: 100 },
      { title: 'name', dataIndex: 'name', width: 220 },
      { title: 'type', dataIndex: 'type', width: 120 },
      { title: 'size', dataIndex: 'size', width: 120 },
      { title: 'status', dataIndex: 'status', width: 120, render: (status) => <StatusTag status={status} /> },
      { title: 'tenantId', dataIndex: 'tenantId', width: 110 },
      { title: 'moduleId', dataIndex: 'moduleId', width: 100 },
      { title: 'bizType', dataIndex: 'bizType', width: 120 },
      { title: 'bizId', dataIndex: 'bizId', width: 100 },
      { title: 'storagePath', dataIndex: 'storagePath', width: 260 },
      { title: 'failureReason', dataIndex: 'failureReason', width: 220 },
      { title: 'updatedAt', dataIndex: 'updatedAt', width: 180 }
    ],
    []
  );

  return (
    <>
      <PageTitle title="上传中心" description="登记文件元数据、维护附件引用并创建导入导出任务。" />
      <Tabs
        items={[
          {
            key: 'files',
            label: '文件元数据',
            children: (
              <section className="section">
                <ContextGate required={['tenantId']}>
                  <BaseTable
                    loading={loading}
                    data={files}
                    columns={[
                      ...columns,
                      {
                        title: '操作',
                        fixed: 'right',
                        render: (_, record) => (
                          <RowActions>
                            <Button
                              size="small"
                              danger
                              onClick={() =>
                                modal.confirm({
                                  title: '确认删除文件元数据',
                                  content: `文件 ID: ${record.id}`,
                                  onOk: async () => {
                                    await api.uploads.deleteFile(record.id);
                                    message.success('文件已逻辑删除');
                                    await refresh();
                                  }
                                })
                              }
                            >
                              删除
                            </Button>
                          </RowActions>
                        )
                      }
                    ]}
                    extra={
                      <CreateModal
                        title="登记文件元数据"
                        fields={[
                          { name: 'storageConfigId', label: 'storageConfigId', type: 'number', required: true },
                          { name: 'originalName', label: 'originalName', required: true },
                          { name: 'fileExt', label: 'fileExt', required: true },
                          { name: 'mimeType', label: 'mimeType', required: true },
                          { name: 'fileSize', label: 'fileSize', type: 'number', required: true },
                          { name: 'storagePath', label: 'storagePath', required: true },
                          { name: 'sha256', label: 'sha256' }
                        ]}
                        onSubmit={async (values) => {
                          const storageConfigId = Number(values.storageConfigId);
                          if (!Number.isFinite(storageConfigId)) {
                            message.error('storageConfigId is required');
                            return;
                          }
                          await api.uploads.createFile({
                            storageConfigId,
                            originalName: String(values.originalName),
                            fileExt: String(values.fileExt),
                            mimeType: String(values.mimeType),
                            fileSize: Number(values.fileSize),
                            storagePath: String(values.storagePath),
                            sha256: values.sha256 ? String(values.sha256) : undefined
                          });
                          message.success('文件元数据已登记');
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
            key: 'attachments',
            label: '附件引用',
            children: (
              <section className="section">
                <Alert
                  className="section-alert"
                  type="info"
                  showIcon
                  message="附件列表需要具体业务对象"
                  description="后端附件查询必须携带 bizType 和 bizId；上传中心没有当前业务对象，因此只提供创建引用入口，不发起附件列表请求。"
                />
                <BaseTable
                  loading={loading}
                  data={attachments}
                  columns={columns}
                  extra={
                    <CreateModal
                      title="创建附件引用"
                      fields={[
                        { name: 'fileId', label: 'fileId', type: 'number', required: true },
                        { name: 'bizType', label: 'bizType', required: true },
                        { name: 'bizId', label: 'bizId', type: 'number', required: true },
                        { name: 'fieldCode', label: 'fieldCode' }
                      ]}
                      onSubmit={async (values) => {
                        await api.uploads.createAttachment({
                          fileId: Number(values.fileId),
                          bizType: String(values.bizType),
                          bizId: Number(values.bizId),
                          fieldCode: values.fieldCode ? String(values.fieldCode) : undefined
                        });
                        message.success('附件引用已创建');
                        await refresh();
                      }}
                    />
                  }
                />
              </section>
            )
          },
          {
            key: 'jobs',
            label: '导入导出任务',
            children: (
              <section className="section">
                <ContextGate required={['tenantId', 'moduleId']}>
                  <Space wrap>
                    <CreateModal
                      title="创建上传任务"
                      buttonText="创建任务"
                      fields={[
                        {
                          name: 'jobType',
                          label: 'jobType',
                          type: 'select',
                          required: true,
                          options: Object.values(UploadJobType).map((value) => ({ value, label: value }))
                        },
                        { name: 'sourceFileId', label: 'sourceFileId', type: 'number' },
                        { name: 'requestJson', label: 'requestJson', type: 'textarea' }
                      ]}
                      onSubmit={async (values) => {
                        await api.uploads.createJob({
                          jobType: String(values.jobType),
                          sourceFileId: values.sourceFileId ? Number(values.sourceFileId) : undefined,
                          requestJson: values.requestJson ? String(values.requestJson) : undefined
                        });
                        message.success('任务已创建');
                      }}
                    />
                  </Space>
                </ContextGate>
              </section>
            )
          }
        ]}
      />
    </>
  );
}

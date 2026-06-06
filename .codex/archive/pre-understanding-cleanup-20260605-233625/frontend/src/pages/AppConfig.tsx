import { App, Button, Tabs } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { CommonStatus, DataScopeType, FieldType, PageType } from '../api/enums';
import { api } from '../api/sdk';
import type { AppManageVO, ByteFlag, ModuleFieldOptionBO, ModuleManageVO } from '../api/types';
import { useSessionStore } from '../store/context';
import { BaseTable, ContextGate, CreateModal, JsonBlock, PageTitle, RowActions, StatusTag } from './shared';

function parseOptions(raw?: string): ModuleFieldOptionBO[] {
  if (!raw?.trim()) {
    return [];
  }
  const parsed = JSON.parse(raw) as ModuleFieldOptionBO[];
  return Array.isArray(parsed) ? parsed : [];
}

function toByteFlag(value: unknown): ByteFlag {
  return value ? 1 : 0;
}

export default function AppConfig() {
  const { message } = App.useApp();
  const { tenantId, systemId, appId, moduleId } = useSessionStore();
  const [loading, setLoading] = useState(false);
  const [apps, setApps] = useState<AppManageVO[]>([]);
  const [models, setModels] = useState<ModuleManageVO[]>([]);
  const [fields, setFields] = useState<ModuleManageVO[]>([]);
  const [pages, setPages] = useState<ModuleManageVO[]>([]);

  const refresh = async () => {
    setLoading(true);
    try {
      const [appRows, modelRows, fieldRows, pageRows] = await Promise.all([
        api.apps.list(),
        api.modules.models(),
        moduleId ? api.modules.fields() : Promise.resolve([] as ModuleManageVO[]),
        moduleId ? api.modules.pages() : Promise.resolve([] as ModuleManageVO[])
      ]);
      setApps(appRows);
      setModels(modelRows);
      setFields(fieldRows);
      setPages(pageRows);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '应用配置加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
  }, [tenantId, systemId, appId, moduleId]);

  const appColumns: ColumnsType<AppManageVO> = useMemo(
    () => [
      { title: 'ID', dataIndex: 'id', width: 90 },
      { title: 'code', dataIndex: 'code', width: 180 },
      { title: 'name', dataIndex: 'name', width: 180 },
      { title: 'status', dataIndex: 'status', width: 120, render: (status) => <StatusTag status={status} /> },
      { title: 'tenantId', dataIndex: 'tenantId', width: 110 },
      { title: 'systemId', dataIndex: 'systemId', width: 110 },
      { title: 'versionNo', dataIndex: 'versionNo', width: 130 },
      { title: 'detail', dataIndex: 'detail', width: 260 },
      { title: 'updatedAt', dataIndex: 'updatedAt', width: 180 }
    ],
    []
  );

  const moduleColumns: ColumnsType<ModuleManageVO> = useMemo(
    () => [
      { title: 'ID', dataIndex: 'id', width: 90 },
      { title: 'code', dataIndex: 'code', width: 180 },
      { title: 'name', dataIndex: 'name', width: 180 },
      { title: 'type', dataIndex: 'type', width: 120 },
      { title: 'status', dataIndex: 'status', width: 120, render: (status) => <StatusTag status={status} /> },
      { title: 'tenantId', dataIndex: 'tenantId', width: 110 },
      { title: 'systemId', dataIndex: 'systemId', width: 110 },
      { title: 'appId', dataIndex: 'appId', width: 90 },
      { title: 'moduleId', dataIndex: 'moduleId', width: 100 },
      { title: 'configJson', dataIndex: 'configJson', width: 260, render: (value) => <JsonBlock value={value} /> },
      { title: 'updatedAt', dataIndex: 'updatedAt', width: 180 }
    ],
    []
  );

  return (
    <>
      <PageTitle title="应用配置中心" description="按应用、模块、字段、页面和发布版本组织配置态数据。" />
      <Tabs
        items={[
          {
            key: 'apps',
            label: '应用',
            children: (
              <section className="section">
                <ContextGate required={['tenantId', 'systemId']}>
                  <BaseTable
                    loading={loading}
                    data={apps}
                    columns={appColumns}
                    extra={
                      <CreateModal
                        title="创建应用"
                        fields={[
                          { name: 'appCode', label: 'appCode', required: true },
                          { name: 'appName', label: 'appName', required: true },
                          { name: 'visibleScope', label: 'visibleScope', required: true },
                          { name: 'description', label: 'description', type: 'textarea' }
                        ]}
                        onSubmit={async (values) => {
                          await api.apps.create({
                            appCode: String(values.appCode),
                            appName: String(values.appName),
                            visibleScope: String(values.visibleScope),
                            description: values.description ? String(values.description) : undefined
                          });
                          message.success('应用已创建');
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
            key: 'publish',
            label: '发布',
            children: (
              <section className="section">
                <CreateModal
                  title="发布应用版本"
                  buttonText="发布应用"
                  fields={[
                    { name: 'appId', label: 'appId', type: 'number', required: true },
                    { name: 'versionNo', label: 'versionNo', required: true },
                    { name: 'versionName', label: 'versionName', required: true },
                    { name: 'snapshotJson', label: 'snapshotJson', type: 'textarea', required: true }
                  ]}
                  onSubmit={async (values) => {
                    await api.apps.publish({
                      appId: Number(values.appId),
                      versionNo: String(values.versionNo),
                      versionName: String(values.versionName),
                      snapshotJson: String(values.snapshotJson)
                    });
                    message.success('应用版本已发布');
                    await refresh();
                  }}
                />
              </section>
            )
          },
          {
            key: 'models',
            label: '模块',
            children: (
              <section className="section">
                <ContextGate required={['tenantId', 'systemId', 'appId']}>
                  <BaseTable
                    loading={loading}
                    data={models}
                    columns={[
                      ...moduleColumns,
                      {
                        title: '操作',
                        fixed: 'right',
                        render: (_, record) => (
                          <RowActions>
                            <Button size="small" onClick={() => api.modules.setModelStatus(record.id, CommonStatus.Published).then(refresh)}>
                              发布
                            </Button>
                            <Button size="small" onClick={() => api.modules.setModelStatus(record.id, CommonStatus.Disabled).then(refresh)}>
                              停用
                            </Button>
                          </RowActions>
                        )
                      }
                    ]}
                    extra={
                      <CreateModal
                        title="创建模块模型"
                        fields={[
                          { name: 'moduleCode', label: 'moduleCode', required: true },
                          { name: 'moduleName', label: 'moduleName', required: true },
                          {
                            name: 'dataScopeType',
                            label: 'dataScopeType',
                            type: 'select',
                            required: true,
                            options: Object.values(DataScopeType).map((value) => ({ value, label: value }))
                          },
                          { name: 'flowEnabled', label: 'flowEnabled', type: 'switch' },
                          { name: 'importEnabled', label: 'importEnabled', type: 'switch' },
                          { name: 'exportEnabled', label: 'exportEnabled', type: 'switch' }
                        ]}
                        initialValues={{
                          flowEnabled: false,
                          importEnabled: false,
                          exportEnabled: true
                        }}
                        onSubmit={async (values) => {
                          await api.modules.createModel({
                            moduleCode: String(values.moduleCode),
                            moduleName: String(values.moduleName),
                            dataScopeType: String(values.dataScopeType),
                            flowEnabled: toByteFlag(values.flowEnabled),
                            importEnabled: toByteFlag(values.importEnabled),
                            exportEnabled: toByteFlag(values.exportEnabled)
                          });
                          message.success('模块模型已创建');
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
            key: 'fields',
            label: '字段',
            children: (
              <section className="section">
                <ContextGate required={['moduleId']}>
                  <BaseTable
                    loading={loading}
                    data={fields}
                    columns={moduleColumns}
                    extra={
                      <CreateModal
                        title="创建模块字段"
                        fields={[
                          { name: 'fieldCode', label: 'fieldCode', required: true },
                          { name: 'fieldName', label: 'fieldName', required: true },
                          {
                            name: 'fieldType',
                            label: 'fieldType',
                            type: 'select',
                            required: true,
                            options: Object.values(FieldType).map((value) => ({ value, label: value }))
                          },
                          { name: 'requiredFlag', label: 'requiredFlag', type: 'switch' },
                          { name: 'uniqueFlag', label: 'uniqueFlag', type: 'switch' },
                          { name: 'listVisible', label: 'listVisible', type: 'switch' },
                          { name: 'searchable', label: 'searchable', type: 'switch' },
                          { name: 'editable', label: 'editable', type: 'switch' },
                          { name: 'defaultValue', label: 'defaultValue' },
                          { name: 'validationJson', label: 'validationJson', type: 'textarea' },
                          { name: 'sortOrder', label: 'sortOrder', type: 'number' },
                          { name: 'optionsJson', label: 'options[]', type: 'textarea', placeholder: '[{\"optionValue\":\"A\",\"optionLabel\":\"选项A\"}]' }
                        ]}
                        initialValues={{
                          requiredFlag: false,
                          uniqueFlag: false,
                          listVisible: true,
                          searchable: true,
                          editable: true
                        }}
                        onSubmit={async (values) => {
                          await api.modules.createField({
                            fieldCode: String(values.fieldCode),
                            fieldName: String(values.fieldName),
                            fieldType: String(values.fieldType),
                            requiredFlag: toByteFlag(values.requiredFlag),
                            uniqueFlag: toByteFlag(values.uniqueFlag),
                            listVisible: toByteFlag(values.listVisible),
                            searchable: toByteFlag(values.searchable),
                            editable: toByteFlag(values.editable),
                            defaultValue: values.defaultValue ? String(values.defaultValue) : undefined,
                            validationJson: values.validationJson ? String(values.validationJson) : undefined,
                            sortOrder: values.sortOrder ? Number(values.sortOrder) : undefined,
                            options: parseOptions(values.optionsJson ? String(values.optionsJson) : '')
                          });
                          message.success('模块字段已创建');
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
            key: 'pages',
            label: '页面',
            children: (
              <section className="section">
                <ContextGate required={['moduleId']}>
                  <BaseTable
                    loading={loading}
                    data={pages}
                    columns={moduleColumns}
                    extra={
                      <CreateModal
                        title="创建模块页面"
                        fields={[
                          { name: 'pageCode', label: 'pageCode', required: true },
                          { name: 'pageName', label: 'pageName', required: true },
                          {
                            name: 'pageType',
                            label: 'pageType',
                            type: 'select',
                            required: true,
                            options: Object.values(PageType).map((value) => ({ value, label: value }))
                          },
                          { name: 'layoutJson', label: 'layoutJson', type: 'textarea', required: true },
                          { name: 'buttonJson', label: 'buttonJson', type: 'textarea' }
                        ]}
                        onSubmit={async (values) => {
                          await api.modules.createPage({
                            pageCode: String(values.pageCode),
                            pageName: String(values.pageName),
                            pageType: String(values.pageType),
                            layoutJson: String(values.layoutJson),
                            buttonJson: values.buttonJson ? String(values.buttonJson) : undefined
                          });
                          message.success('模块页面已创建');
                          await refresh();
                        }}
                      />
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

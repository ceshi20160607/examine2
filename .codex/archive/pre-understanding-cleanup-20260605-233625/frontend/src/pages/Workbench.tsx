import { Alert, List, Spin, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { api } from '../api/sdk';
import type { AppManageVO, FlowManageVO, ModuleManageVO, UploadManageVO } from '../api/types';
import { PageTitle, StatusTag } from './shared';

export default function Workbench() {
  const [apps, setApps] = useState<AppManageVO[]>([]);
  const [modules, setModules] = useState<ModuleManageVO[]>([]);
  const [tasks, setTasks] = useState<FlowManageVO[]>([]);
  const [files, setFiles] = useState<UploadManageVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([api.apps.list(), api.modules.models(), api.flows.tasks(), api.uploads.files()])
      .then(([appRows, moduleRows, taskRows, fileRows]) => {
        setApps(appRows);
        setModules(moduleRows);
        setTasks(taskRows);
        setFiles(fileRows);
      })
      .catch((requestError: unknown) => {
        setError(requestError instanceof Error ? requestError.message : '工作台加载失败');
      })
      .finally(() => setLoading(false));
  }, []);

  return (
    <>
      <PageTitle title="工作台" description="展示接口实时返回的应用、模块、待办和文件摘要。" />
      {error && <Alert className="context-alert" type="error" showIcon message={error} />}
      <Spin spinning={loading}>
        <div className="metric-grid">
          <div className="metric">
            <strong>{apps.length}</strong>
            <span>应用</span>
          </div>
          <div className="metric">
            <strong>{modules.length}</strong>
            <span>模块</span>
          </div>
          <div className="metric">
            <strong>{tasks.length}</strong>
            <span>待办/任务</span>
          </div>
          <div className="metric">
            <strong>{files.length}</strong>
            <span>文件元数据</span>
          </div>
        </div>
        <section className="section">
          <div className="section-title">
            <Typography.Title level={2}>最近应用</Typography.Title>
          </div>
          <List
            dataSource={apps.slice(0, 6)}
            locale={{ emptyText: '接口暂无应用数据' }}
            renderItem={(item) => (
              <List.Item>
                <List.Item.Meta title={item.name || item.code || `应用 ${item.id}`} description={`ID: ${item.id}`} />
                <StatusTag status={item.status} />
              </List.Item>
            )}
          />
        </section>
      </Spin>
    </>
  );
}

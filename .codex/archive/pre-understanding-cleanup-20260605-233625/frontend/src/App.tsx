import {
  AppstoreOutlined,
  CloudUploadOutlined,
  ControlOutlined,
  DashboardOutlined,
  DeploymentUnitOutlined,
  PartitionOutlined,
  SafetyCertificateOutlined
} from '@ant-design/icons';
import { Alert, App as AntApp, Button, Layout, Menu, Select, Space, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { api } from './api/sdk';
import type { AppManageVO, ModuleManageVO, PlatformManageVO } from './api/types';
import { useSessionStore } from './store/context';
import AppConfig from './pages/AppConfig';
import FlowWorkspace from './pages/FlowWorkspace';
import Login from './pages/Login';
import OpenApiCenter from './pages/OpenApiCenter';
import PlatformCenter from './pages/PlatformCenter';
import RuntimeConsole from './pages/RuntimeConsole';
import UploadCenter from './pages/UploadCenter';
import Workbench from './pages/Workbench';

const { Header, Content, Sider } = Layout;

const menus: MenuProps['items'] = [
  { key: '/workbench', icon: <DashboardOutlined />, label: '工作台' },
  { key: '/platform', icon: <ControlOutlined />, label: '平台中心' },
  { key: '/config', icon: <AppstoreOutlined />, label: '应用配置' },
  { key: '/runtime', icon: <PartitionOutlined />, label: '运行台' },
  { key: '/flows', icon: <DeploymentUnitOutlined />, label: '流程工作台' },
  { key: '/uploads', icon: <CloudUploadOutlined />, label: '上传中心' },
  { key: '/openapi', icon: <SafetyCertificateOutlined />, label: 'OpenAPI' }
];

function BackendShell() {
  const navigate = useNavigate();
  const location = useLocation();
  const session = useSessionStore();
  const [systems, setSystems] = useState<PlatformManageVO[]>([]);
  const [tenants, setTenants] = useState<PlatformManageVO[]>([]);
  const [apps, setApps] = useState<AppManageVO[]>([]);
  const [modules, setModules] = useState<ModuleManageVO[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    Promise.allSettled([api.platform.systems(), api.platform.tenants(), api.apps.list(), api.modules.models()])
      .then(([systemResult, tenantResult, appResult, moduleResult]) => {
        if (systemResult.status === 'fulfilled') setSystems(systemResult.value);
        if (tenantResult.status === 'fulfilled') setTenants(tenantResult.value);
        if (appResult.status === 'fulfilled') setApps(appResult.value);
        if (moduleResult.status === 'fulfilled') setModules(moduleResult.value);
      })
      .finally(() => setLoading(false));
  }, []);

  const selectedKey = useMemo(() => {
    const hit = menus?.find((item) => location.pathname.startsWith(String(item?.key)));
    return hit?.key ? [String(hit.key)] : ['/workbench'];
  }, [location.pathname]);

  const missingContext = !session.tenantId || !session.systemId;

  return (
    <Layout className="app-shell">
      <Sider breakpoint="lg" collapsedWidth={0} width={220} className="app-sider">
        <div className="brand">配置平台</div>
        <Menu
          mode="inline"
          theme="dark"
          selectedKeys={selectedKey}
          items={menus}
          onClick={({ key }) => navigate(String(key))}
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <Space wrap size={10}>
            <Select
              className="context-select"
              loading={loading}
              placeholder="选择系统"
              value={session.systemId}
              options={systems.map((item) => ({ value: item.id, label: `${item.name ?? item.code} #${item.id}` }))}
              onChange={(systemId) => session.setContext({ systemId })}
            />
            <Select
              className="context-select"
              loading={loading}
              placeholder="选择租户"
              value={session.tenantId}
              options={tenants.map((item) => ({ value: item.id, label: `${item.name ?? item.code} #${item.id}` }))}
              onChange={(tenantId) => session.setContext({ tenantId })}
            />
            <Select
              className="context-select"
              allowClear
              loading={loading}
              placeholder="选择应用"
              value={session.appId}
              options={apps.map((item) => ({ value: item.id, label: `${item.name ?? item.code} #${item.id}` }))}
              onChange={(appId) => session.setContext({ appId, moduleId: undefined })}
            />
            <Select
              className="context-select"
              allowClear
              loading={loading}
              placeholder="选择模块"
              value={session.moduleId}
              options={modules
                .filter((item) => !session.appId || item.appId === session.appId)
                .map((item) => ({ value: item.id, label: `${item.name ?? item.code} #${item.id}` }))}
              onChange={(moduleId) => session.setContext({ moduleId })}
            />
          </Space>
          <Space>
            <Typography.Text type="secondary">{session.displayName || session.username || `账号 ${session.accountId}`}</Typography.Text>
            <Button
              onClick={() => {
                session.clear();
                navigate('/login');
              }}
            >
              退出
            </Button>
          </Space>
        </Header>
        <Content className="app-content">
          {missingContext && (
            <Alert
              className="context-alert"
              type="warning"
              showIcon
              message="缺少租户或系统上下文"
              description="会写入 tenantId/systemId 的接口已由页面阻止，请先从接口返回的系统和租户列表中选择上下文。"
            />
          )}
          <Routes>
            <Route path="/workbench" element={<Workbench />} />
            <Route path="/platform" element={<PlatformCenter />} />
            <Route path="/config" element={<AppConfig />} />
            <Route path="/runtime" element={<RuntimeConsole />} />
            <Route path="/flows" element={<FlowWorkspace />} />
            <Route path="/uploads" element={<UploadCenter />} />
            <Route path="/openapi" element={<OpenApiCenter />} />
            <Route path="*" element={<Navigate to="/workbench" replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}

function ProtectedShell() {
  const accountId = useSessionStore((state) => state.accountId);
  if (!accountId) {
    return <Navigate to="/login" replace />;
  }
  return <BackendShell />;
}

export default function App() {
  return (
    <AntApp>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/*" element={<ProtectedShell />} />
      </Routes>
    </AntApp>
  );
}

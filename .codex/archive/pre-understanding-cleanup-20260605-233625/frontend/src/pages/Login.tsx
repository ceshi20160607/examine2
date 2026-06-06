import { Button, Form, Input, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/sdk';
import type { PlatformLoginDTO } from '../api/types';
import { useSessionStore } from '../store/context';

export default function Login() {
  const navigate = useNavigate();
  const setLogin = useSessionStore((state) => state.setLogin);

  return (
    <main className="login-page">
      <section className="login-panel">
        <h1>可配置业务系统平台</h1>
        <p>账号登录后进入实际管理工作台。</p>
        <Form<PlatformLoginDTO>
          layout="vertical"
          initialValues={{ username: '', password: '' }}
          onFinish={async (values) => {
            try {
              const account = await api.auth.login(values);
              setLogin({
                accountId: account.id,
                username: account.code,
                displayName: account.name,
                tenantId: account.tenantId,
                systemId: account.systemId
              });
              message.success('登录成功');
              navigate('/workbench', { replace: true });
            } catch (error) {
              const messageText = error instanceof Error ? error.message : '登录失败';
              message.error(messageText);
            }
          }}
        >
          <Form.Item name="username" label="username" rules={[{ required: true, message: '请填写 username' }]}>
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" label="password" rules={[{ required: true, message: '请填写 password' }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>
            登录
          </Button>
        </Form>
      </section>
    </main>
  );
}

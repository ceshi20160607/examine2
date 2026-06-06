import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type React from 'react';
import { useState } from 'react';
import { statusText } from '../api/enums';
import type { Id } from '../api/types';
import { useSessionStore } from '../store/context';

export function PageTitle({ title, description }: { title: string; description: string }) {
  return (
    <div className="page-title">
      <div>
        <Typography.Title level={1}>{title}</Typography.Title>
        <Typography.Paragraph>{description}</Typography.Paragraph>
      </div>
    </div>
  );
}

export function StatusTag({ status }: { status?: string }) {
  if (!status) {
    return <Tag>未返回</Tag>;
  }
  const color = status === 'ENABLED' || status === 'PUBLISHED' || status === 'DONE' ? 'green' : status === 'DISABLED' ? 'orange' : 'blue';
  return <Tag color={color}>{statusText[status] || status}</Tag>;
}

export function JsonBlock({ value }: { value?: unknown }) {
  if (value == null || value === '') {
    return <Typography.Text type="secondary">空</Typography.Text>;
  }
  const content = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
  return <pre className="json-preview">{content}</pre>;
}

export function ContextGate({
  required,
  children
}: {
  required: Array<'tenantId' | 'systemId' | 'appId' | 'moduleId' | 'accountId'>;
  children: React.ReactNode;
}) {
  const session = useSessionStore();
  const missing = required.filter((field) => !session[field]);
  if (missing.length > 0) {
    return (
      <Alert
        type="warning"
        showIcon
        message="上下文不足，已阻止请求"
        description={`请先选择 ${missing.join(', ')}，页面不会要求手工输入这些系统字段。`}
      />
    );
  }
  return <>{children}</>;
}

export interface FieldItem {
  name: string;
  label: string;
  required?: boolean;
  type?: 'text' | 'number' | 'password' | 'textarea' | 'switch' | 'select';
  options?: Array<{ label: string; value: string | number | boolean }>;
  placeholder?: string;
}

function renderField(field: FieldItem) {
  if (field.type === 'textarea') {
    return <Input.TextArea rows={4} placeholder={field.placeholder} />;
  }
  if (field.type === 'number') {
    return <InputNumber className="full-width" placeholder={field.placeholder} />;
  }
  if (field.type === 'password') {
    return <Input.Password placeholder={field.placeholder} />;
  }
  if (field.type === 'switch') {
    return <Switch />;
  }
  if (field.type === 'select') {
    return <Select placeholder={field.placeholder} options={field.options} />;
  }
  return <Input placeholder={field.placeholder} />;
}

type FormValues = Record<string, any>;

export function CreateModal({
  title,
  buttonText = '新增',
  fields,
  onSubmit,
  disabled,
  initialValues
}: {
  title: string;
  buttonText?: string;
  fields: FieldItem[];
  onSubmit: (values: FormValues) => Promise<void>;
  disabled?: boolean;
  initialValues?: FormValues;
}) {
  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<FormValues>();

  return (
    <>
      <Button type="primary" onClick={() => setOpen(true)} disabled={disabled}>
        {buttonText}
      </Button>
      <Modal
        title={title}
        open={open}
        destroyOnClose
        confirmLoading={submitting}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
      >
        <Form<FormValues>
          form={form}
          layout="vertical"
          preserve={false}
          initialValues={initialValues}
          onFinish={async (values) => {
            setSubmitting(true);
            try {
              await onSubmit(values);
              setOpen(false);
              form.resetFields();
            } finally {
              setSubmitting(false);
            }
          }}
        >
          {fields.map((field) => (
            <Form.Item
              key={field.name}
              name={field.name}
              label={field.label}
              valuePropName={field.type === 'switch' ? 'checked' : 'value'}
              rules={field.required ? [{ required: true, message: `请填写${field.label}` }] : undefined}
            >
              {renderField(field)}
            </Form.Item>
          ))}
        </Form>
      </Modal>
    </>
  );
}

export function BaseTable<T extends { id: Id }>({
  rowKey = 'id',
  loading,
  data,
  columns,
  extra
}: {
  rowKey?: string;
  loading: boolean;
  data: T[];
  columns: ColumnsType<T>;
  extra?: React.ReactNode;
}) {
  return (
    <>
      {extra && <div className="toolbar">{extra}</div>}
      <Table<T>
        rowKey={rowKey}
        loading={loading}
        dataSource={data}
        columns={columns}
        pagination={false}
        scroll={{ x: 'max-content' }}
      />
    </>
  );
}

export function RowActions({ children }: { children: React.ReactNode }) {
  return <Space size={6}>{children}</Space>;
}

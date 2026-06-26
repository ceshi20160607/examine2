import type {
  ActionContract,
  BusinessDetailView,
  BusinessRecordRow,
  DynamicListSchema,
  FieldDefinitionVO,
} from '../../../api/types';
import type { StatusTone } from '../../../shared/status';

export interface RuntimeModuleGroup {
  groupId: string;
  name: string;
  routePath: string;
  visible: boolean;
  disabledReason?: string;
}

export interface RuntimeModuleItem {
  groupId: string;
  moduleId: string;
  moduleCode: string;
  name: string;
  count: number;
  publishVersion: string;
  active?: boolean;
  disabledReason?: string;
}

export interface RuntimeRecordRow extends BusinessRecordRow {
  serialNo: string;
  favorite: boolean;
  model: string;
  statusLabel: string;
  statusTone: StatusTone;
  department: string;
  owner: string;
  buyDate: string;
  inspectionDue: string;
  lastMaintenance: string;
  traceId: string;
  auditLogId: string;
  editDisabledReason?: string;
  deleteDisabledReason?: string;
  transferDisabledReason?: string;
  detail: BusinessDetailView;
}

export const defaultRuntimeModuleId = '';

export const runtimeModuleGroups: RuntimeModuleGroup[] = [];

export const runtimeModules: RuntimeModuleItem[] = [];

export const runtimeFields: FieldDefinitionVO[] = [];

export const runtimeListSchema: DynamicListSchema = {
  moduleId: '',
  moduleCode: '',
  sceneId: 'default',
  columns: [],
  filters: [],
  sorters: [],
  page: { pageNo: 1, pageSize: 10, filters: [], sorts: [] },
  rowClickTarget: 'recordDetailDrawer',
  batchActions: [],
  toolbarActions: [],
  importExportConfig: {
    importEnabled: false,
    exportEnabled: false,
    exportAllEnabled: false,
    resultTaskRequired: true,
  },
  emptyState: { title: '暂无业务数据', actionCode: 'create' },
  permissionSnapshotId: '',
};

export const runtimeRows: RuntimeRecordRow[] = [];

export type { ActionContract };

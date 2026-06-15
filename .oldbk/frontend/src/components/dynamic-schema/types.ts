import type {
  ApiErrorDetail,
  AvailableAction,
  DynamicFieldType,
  DynamicFieldValue,
  EntityId,
  FieldDefinitionVO,
  FieldPermission,
  FileBindDTO,
  FilterOperator,
  FilterRule,
  JsonValue,
  SortDirection,
  SortRule,
} from "../../api";

export type DynamicSchemaRenderMode = "list" | "filter" | "form" | "detail" | "history";

export type DynamicSchemaComponentKind =
  | "text-input"
  | "number-input"
  | "money-input"
  | "date-picker"
  | "datetime-picker"
  | "select"
  | "multi-select"
  | "switch"
  | "member-picker"
  | "dept-picker"
  | "file-uploader"
  | "image-uploader"
  | "auto-number"
  | "relation-picker"
  | "sub-table"
  | "address-picker"
  | "tag-input"
  | "json-editor"
  | "readonly-text";

export interface DynamicFieldRendererDefinition {
  fieldType: DynamicFieldType;
  label: string;
  formComponent: DynamicSchemaComponentKind;
  filterComponent: DynamicSchemaComponentKind;
  detailComponent: DynamicSchemaComponentKind;
  historyComponent: DynamicSchemaComponentKind;
  sortable: boolean;
  filterable: boolean;
  writable: boolean;
  acceptsMultipleValues: boolean;
  defaultOperators: FilterOperator[];
}

export interface DynamicSchemaFieldRef {
  fieldCode: string;
  fieldId?: EntityId;
  label?: string;
  visible?: boolean;
  required?: boolean;
  width?: number;
  component?: string;
  defaultValue?: JsonValue;
  sortable?: boolean;
  filterable?: boolean;
}

export interface DynamicSchemaSorterRef {
  fieldCode: string;
  direction: SortDirection;
}

export interface DynamicListSchema {
  columns: DynamicSchemaFieldRef[];
  filters?: DynamicSchemaFieldRef[];
  sorters?: DynamicSchemaSorterRef[];
  fieldVisibility?: FieldVisibilityRule[];
  schemaVersion?: number;
}

export interface DynamicFormSection {
  sectionCode: string;
  title: string;
  fields: DynamicSchemaFieldRef[];
}

export interface DynamicFormSchema {
  formSections: DynamicFormSection[];
  fieldWritable?: FieldWritableRule[];
  schemaVersion?: number;
}

export interface DynamicDetailBlock {
  blockCode: string;
  title: string;
  fields: DynamicSchemaFieldRef[];
}

export interface DynamicDetailSchema {
  detailBlocks: DynamicDetailBlock[];
  fieldVisibility?: FieldVisibilityRule[];
  schemaVersion?: number;
}

export interface FieldVisibilityRule {
  fieldCode: string;
  visible: boolean;
  reason?: string;
}

export interface FieldWritableRule {
  fieldCode: string;
  writable: boolean;
  readonlyReason?: string;
}

export interface DynamicSchemaInput {
  moduleId: EntityId;
  moduleCode?: string;
  publishedVersionId?: EntityId;
  fieldDefinitions: FieldDefinitionVO[];
  listSchema?: JsonValue | DynamicListSchema;
  formSchema?: JsonValue | DynamicFormSchema;
  detailSchema?: JsonValue | DynamicDetailSchema;
  availableActions?: AvailableAction[];
  fieldPermissions?: FieldPermission[];
}

export interface FieldPermissionState {
  fieldCode: string;
  visible: boolean;
  writable: boolean;
  readonly: boolean;
  readonlyReason?: string;
}

export interface DynamicFieldError {
  fieldCode: string;
  code: "FIELD_REQUIRED_MISSING" | "FIELD_VALUE_TYPE_INVALID" | "PERM_FIELD_WRITE_DENIED" | string;
  message: string;
  requestId?: string;
  apiError?: ApiErrorDetail;
}

export interface DynamicFieldRenderModel {
  field: FieldDefinitionVO;
  ref: DynamicSchemaFieldRef;
  renderer: DynamicFieldRendererDefinition;
  mode: DynamicSchemaRenderMode;
  permission: FieldPermissionState;
  value: JsonValue | FileBindDTO[] | DynamicFieldValue[];
  displayValue: string;
  rawDisplayValue?: JsonValue;
  required: boolean;
  errors: DynamicFieldError[];
}

export interface DynamicSectionRenderModel {
  sectionCode: string;
  title: string;
  fields: DynamicFieldRenderModel[];
}

export interface DynamicBlockRenderModel {
  blockCode: string;
  title: string;
  fields: DynamicFieldRenderModel[];
}

export interface DynamicListRenderModel {
  columns: DynamicFieldRenderModel[];
  filters: DynamicFieldRenderModel[];
  sorters: SortRule[];
  empty: boolean;
}

export interface DynamicFormRenderModel {
  sections: DynamicSectionRenderModel[];
  errors: DynamicFieldError[];
  valid: boolean;
  values: DynamicFieldValue[];
}

export interface DynamicDetailRenderModel {
  blocks: DynamicBlockRenderModel[];
  empty: boolean;
}

export interface DynamicHistorySnapshotModel {
  title: string;
  changedAt?: string;
  changedBy?: string;
  fields: DynamicFieldRenderModel[];
  requestId?: string;
}

export interface DynamicQueryDraft {
  keyword?: string;
  filters?: Array<FilterRule<JsonValue>>;
  sorter?: SortRule[];
}

export interface DynamicSchemaValidationResult {
  valid: boolean;
  errors: DynamicFieldError[];
  values: DynamicFieldValue[];
}

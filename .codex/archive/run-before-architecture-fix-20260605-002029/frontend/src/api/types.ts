export type Status = 'DRAFT' | 'ENABLED' | 'DISABLED' | 'PUBLISHED' | string;

export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  pageNo: number;
  pageSize: number;
  total: number;
  records: T[];
}

export interface AnyRecord {
  id?: number;
  [key: string]: unknown;
}

export interface UserVO {
  id: number;
  account: string;
  realName?: string;
  mobile?: string;
  email?: string;
  status?: string;
  systemId?: number;
  tenantId?: number;
}

export interface AuthTokenVO {
  accessToken: string;
  tokenType?: string;
  resetRequired?: boolean;
  user?: UserVO;
}

export interface HealthVO {
  serviceStatus?: string;
  databaseStatus?: string;
  redisStatus?: string;
  storageStatus?: string;
  scriptVersionStatus?: string;
  [key: string]: unknown;
}

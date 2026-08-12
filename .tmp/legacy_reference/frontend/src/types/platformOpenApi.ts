export type PlatformOpenApiStatus = 'ACTIVE' | 'DISABLED'
export interface PlatformOpenApiApplication { id:string;serviceAccountId:string;appKey:string;name:string;status:PlatformOpenApiStatus;scopes:string[];ipAllowlist:string[];rateLimitPerMinute:number;credentialVersion:number;secretRef:string;version:number;createdAt:string;updatedAt:string }
export interface PlatformOpenApiPage { items:PlatformOpenApiApplication[];page:number;size:number;total:number }
export interface CreatePlatformOpenApiApplication { serviceAccountId:string;name:string;scopes:string[];ipAllowlist:string[];rateLimitPerMinute:number;secretRef:string }
export interface PlatformOpenApiCallLog { id:string;credentialVersion:number|null;routeTemplate:string;requestMethod:string;resultCategory:string;httpStatus:number;latencyMs:number;requestId:string;traceId:string;observedIp:string;createdAt:string }
export interface PlatformOpenApiCallLogPage { items:PlatformOpenApiCallLog[];page:number;size:number;total:number }

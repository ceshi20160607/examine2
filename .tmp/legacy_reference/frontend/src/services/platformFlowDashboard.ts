import { apiRequest } from '@/services/api'
import type { ConfigCheck,PlatformDashboard,PlatformDashboardRuntime,PlatformFlowDefinition,PlatformFlowInstance } from '@/types/platformFlowDashboard'
export const platformFlowApi={
  definitions:()=>apiRequest<PlatformFlowDefinition[]>('/api/v1/platform/flows/definitions'),
  instances:()=>apiRequest<PlatformFlowInstance[]>('/api/v1/platform/flows/instances'),
  start:(id:string,input:unknown)=>apiRequest<PlatformFlowInstance>(`/api/v1/platform/flows/definitions/${encodeURIComponent(id)}/instances`,{method:'POST',body:{input}}),
  adminList:()=>apiRequest<PlatformFlowDefinition[]>('/api/v1/platform/admin/flows/definitions'),
  create:(body:unknown)=>apiRequest<PlatformFlowDefinition>('/api/v1/platform/admin/flows/definitions',{method:'POST',body}),
  save:(id:string,body:unknown)=>apiRequest<PlatformFlowDefinition>(`/api/v1/platform/admin/flows/definitions/${encodeURIComponent(id)}/draft`,{method:'PUT',body}),
  check:(id:string)=>apiRequest<ConfigCheck>(`/api/v1/platform/admin/flows/definitions/${encodeURIComponent(id)}/draft:check`,{method:'POST'}),
  publish:(id:string,expectedVersion:number)=>apiRequest<unknown>(`/api/v1/platform/admin/flows/definitions/${encodeURIComponent(id)}/draft:publish`,{method:'POST',body:{expectedVersion}}),
  versions:(id:string)=>apiRequest<unknown[]>(`/api/v1/platform/admin/flows/definitions/${encodeURIComponent(id)}/versions`),
}
export const platformDashboardApi={
  runtime:()=>apiRequest<PlatformDashboardRuntime>('/api/v1/platform/dashboard'),
  list:()=>apiRequest<PlatformDashboard[]>('/api/v1/platform/admin/dashboards'),
  create:(body:unknown)=>apiRequest<PlatformDashboard>('/api/v1/platform/admin/dashboards',{method:'POST',body}),
  save:(id:string,body:unknown)=>apiRequest<PlatformDashboard>(`/api/v1/platform/admin/dashboards/${encodeURIComponent(id)}/draft`,{method:'PUT',body}),
  check:(id:string)=>apiRequest<ConfigCheck>(`/api/v1/platform/admin/dashboards/${encodeURIComponent(id)}/draft:check`,{method:'POST'}),
  publish:(id:string,expectedVersion:number)=>apiRequest<unknown>(`/api/v1/platform/admin/dashboards/${encodeURIComponent(id)}/draft:publish`,{method:'POST',body:{expectedVersion}}),
  versions:(id:string)=>apiRequest<unknown[]>(`/api/v1/platform/admin/dashboards/${encodeURIComponent(id)}/versions`),
  restore:(id:string,version:number,expectedVersion:number)=>apiRequest<PlatformDashboard>(`/api/v1/platform/admin/dashboards/${encodeURIComponent(id)}/versions/${version}:restore`,{method:'POST',body:{expectedVersion}}),
}

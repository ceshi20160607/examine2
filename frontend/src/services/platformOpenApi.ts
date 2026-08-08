import { apiRequest } from '@/services/api'
import type { CreatePlatformOpenApiApplication, PlatformOpenApiApplication, PlatformOpenApiCallLogPage, PlatformOpenApiPage } from '@/types/platformOpenApi'
const base='/api/v1/platform/admin/openapi/applications'
export const platformOpenApiApi={
  list:(page=1,size=20)=>apiRequest<PlatformOpenApiPage>(`${base}?page=${page}&size=${size}`),
  create:(body:CreatePlatformOpenApiApplication)=>apiRequest<PlatformOpenApiApplication>(base,{method:'POST',body}),
  rotate:(id:string,secretRef:string,version:number)=>apiRequest<PlatformOpenApiApplication>(`${base}/${encodeURIComponent(id)}:rotate-secret-ref`,{method:'POST',body:{secretRef,version}}),
  status:(id:string,command:'enable'|'disable',version:number,reason:string)=>apiRequest<PlatformOpenApiApplication>(`${base}/${encodeURIComponent(id)}:${command}`,{method:'POST',body:{version,reason}}),
  callLogs:(id:string,resultCategory='ALL',requestMethod='ALL',page=1,size=20)=>apiRequest<PlatformOpenApiCallLogPage>(`${base}/${encodeURIComponent(id)}/call-logs?${new URLSearchParams({resultCategory,requestMethod,page:String(page),size:String(size)}).toString()}`),
}

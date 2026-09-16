import { describe, expect, it } from 'vitest'
import { actionsAtLocation, actionAllowsRecord, selectionActionReason, supportsBatchAction } from './action-placement'
import { moduleAdminTarget, selectContextModule } from './module-navigation'
import type { ConfiguredAction, RuntimeRecord } from './types'
const action = (code: string, location: string, status = 'ACTIVE') => ({ code, location, status }) as ConfiguredAction
const record = (extra: Partial<RuntimeRecord> = {}) => ({ archived: false, deleted: false, shared: false, ownedByCurrentTenant: true, sharedActions: [], ...extra })
describe('published action placement and access', () => {
  it('moves a permitted published action between areas without exposing disabled, ungranted or unsupported actions', () => {
    const rows = [action('UPDATE','ROW'),action('DELETE','ROW'),action('IMPORT','ROW','DISABLED'),action('custom','ROW')]
    expect(actionsAtLocation(rows,'ROW',code=>code==='UPDATE').map(row=>row.code)).toEqual(['UPDATE'])
    rows[0]!.location='DETAIL_HEADER'
    expect(actionsAtLocation(rows,'ROW',code=>code==='UPDATE')).toEqual([])
    expect(actionsAtLocation(rows,'DETAIL_HEADER',()=>false)).toEqual([])
    expect(actionsAtLocation(rows,'DETAIL_HEADER',code=>code==='UPDATE')).toEqual([rows[0]])
  })
  it('keeps lifecycle restrictions, shared grants and conversion prerequisites across every placement', () => {
    expect(actionAllowsRecord('UPDATE',record())).toBe(true)
    expect(actionAllowsRecord('UPDATE',record({archived:true}))).toBe(false)
    expect(actionAllowsRecord('RESTORE',record({deleted:true}))).toBe(true)
    expect(actionAllowsRecord('DELETE',record({deleted:true}))).toBe(false)
    expect(actionAllowsRecord('UPDATE',record({shared:true,ownedByCurrentTenant:false}))).toBe(false)
    expect(actionAllowsRecord('UPDATE',record({shared:true,ownedByCurrentTenant:false,sharedActions:['UPDATE']}))).toBe(true)
    expect(actionAllowsRecord('SHARE',record({shared:true,sharedActions:['SHARE']}))).toBe(false)
    expect(actionAllowsRecord('CONVERT',record(),false)).toBe(false)
  })
  it('uses navigation for the default list entry and allows configuring a return-to-list button elsewhere', () => {
    const entry = action('LIST', 'MODULE_ENTRY')
    expect(actionsAtLocation([entry], 'MODULE_ENTRY', () => true)).toEqual([])
    entry.location = 'DETAIL_HEADER'
    expect(actionsAtLocation([entry], 'DETAIL_HEADER', () => true)).toEqual([entry])
    expect(actionsAtLocation([action('DETAIL', 'ROW')], 'ROW', () => true)).toEqual([])
  })

  it('places published manual actions while retaining permission, lifecycle and sharing limits', () => {
    const manual = { ...action('ACTION_REVIEW', 'BATCH'), actionType: 'CUSTOM' as const }
    expect(actionsAtLocation([manual], 'BATCH', () => true)).toEqual([manual])
    expect(actionsAtLocation([manual], 'BATCH', () => false)).toEqual([])
    expect(actionAllowsRecord(manual.code, record({ archived: true }))).toBe(false)
    expect(actionAllowsRecord(manual.code, record({ shared: true, sharedActions: ['UPDATE'] }))).toBe(false)
    expect(actionAllowsRecord(manual.code, record({ shared: true, sharedActions: [manual.code] }))).toBe(true)
  })
  it('requires an explicit single record for record actions; export retains multiple selection support', () => {
    expect(selectionActionReason('UPDATE',0)).toBeTruthy()
    expect(selectionActionReason('UPDATE',2)).toBeTruthy()
    expect(selectionActionReason('UPDATE',1)).toBe('')
    expect(selectionActionReason('EXPORT',2)).toBe('')
    expect(selectionActionReason('DELETE',0,true)).toBeTruthy()
    expect(selectionActionReason('DELETE',2,true)).toBe('')
    expect(supportsBatchAction({ ...action('DELETE','BATCH'), actionType:'BUILTIN' })).toBe(true)
    expect(supportsBatchAction({ ...action('DELETE','ROW'), actionType:'BUILTIN' })).toBe(false)
    expect(supportsBatchAction({ ...action('TRANSFER','BATCH'), actionType:'BUILTIN' })).toBe(false)
  })
})
describe('module configuration navigation', () => {
  it('retains module and task on Flow/application navigation and return without accepting a caller-provided URL', () => {
    expect(moduleAdminTarget(19,'inspection','flow','FLOW')).toEqual({path:'/systems/19/admin',query:{section:'flow',module:'inspection',moduleTask:'FLOW',flowSection:'bindings'}})
    expect(moduleAdminTarget(19,'inspection','templates','APPLICATION').query).toEqual({section:'templates',module:'inspection',moduleTask:'APPLICATION'})
  })
  it('does not silently switch an unavailable requested module to the first accessible module', () => {
    const rows=[{moduleCode:'customer'},{moduleCode:'inspection'}]
    expect(selectContextModule(rows,'inspection')).toBe(rows[1])
    expect(selectContextModule(rows,'unavailable')).toBeUndefined()
    expect(selectContextModule(rows,'')).toBe(rows[0])
  })
})

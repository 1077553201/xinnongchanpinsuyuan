export const rejectionStatuses: Record<string, string> = { OPEN: '待处置', RETURNING: '退回在途', QUARANTINED: '隔离保管', DISPOSED: '已报损处置' }
export const rejectionKinds: Record<string, string> = { RETURN_DISPATCH: '登记退回发运', QUARANTINE_RECEIVE: '登记隔离收货', DISPOSE: '登记实际报损', ANNOTATION: '追加更正说明（不改账）' }
export function rejectionActions(status: string): string[] {
  if (status === 'OPEN') return ['RETURN_DISPATCH', 'QUARANTINE_RECEIVE', 'ANNOTATION']
  if (status === 'RETURNING') return ['QUARANTINE_RECEIVE', 'ANNOTATION']
  if (status === 'QUARANTINED') return ['DISPOSE', 'ANNOTATION']
  return status === 'DISPOSED' ? ['ANNOTATION'] : []
}

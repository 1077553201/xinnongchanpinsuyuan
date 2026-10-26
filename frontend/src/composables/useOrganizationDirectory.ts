import { computed } from 'vue'
import { listOrganizationNames } from '@/api/production'
import { useAsyncResource } from './useAsyncResource'

/** Active entries are selectable; inactive names remain available for historical records. */
export function useOrganizationDirectory() {
  const directory = useAsyncResource(listOrganizationNames)
  return {
    ...directory,
    data: computed(() => directory.data.value === null ? null : directory.data.value.filter(row => row.status === 'ACTIVE')),
    names: computed(() => new Map((directory.data.value ?? []).map(row => [row.id, row.name]))),
    records: directory.data,
  }
}

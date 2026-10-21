<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import AppDialog from '@/components/AppDialog.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { cancelFarmTask, completeFarmTask, createFarmTask, getFarmExecution, listFarmTasks, listProductionPeople, TASK_TYPES } from '@/api/growing'
import type { FarmTask } from '@/api/growing'
import type { PlantingCycle } from '@/api/production'
import { formatDateTime } from '@/types/api'
import { eventTime } from '@/utils/items'
import { localDateTime, newOperationKey, operationError, uncertainOperation } from '@/utils/growing'
const props = defineProps<{ cycle: PlantingCycle; organizationId: number }>()
const auth = useAuth(), tasks = useAsyncResource(() => listFarmTasks(props.cycle.id)), people = useAsyncResource(listProductionPeople)
const status = ref(''), search = ref(''), mine = ref(false)
const labels = { PLANNED: '待执行', COMPLETED: '已完成', CANCELLED: '已取消' }
const rows = computed(() => (tasks.data.value ?? []).filter(task => (!status.value || task.status === status.value) && (!mine.value || task.assignedTo === auth.user.value?.id) && (!search.value || [task.title, task.taskCode, task.assigneeName].some(text => text.includes(search.value.trim())))))
const assignees = computed(() => (people.data.value ?? []).filter(person => person.organizationId === props.organizationId))
const selected = ref<FarmTask | null>(null), mode = ref<'create' | 'complete' | 'cancel' | 'detail' | null>(null)
const execution = useAsyncResource(() => getFarmExecution(selected.value!.id))
const busy = ref(false), uncertain = ref(false), error = ref(''), notice = ref('')
const today = () => localDateTime().slice(0, 10)
const draft = reactive({ taskCode: '', taskType: 'IRRIGATION', title: '', plannedOn: '', assignedTo: '', instructions: '', performedAt: '', inputName: '', quantity: '', unit: '', notes: '', evidenceReference: '', reason: '', requestKey: '' })
const inputRequired = computed(() => ['FERTILIZATION', 'PEST_CONTROL'].includes(selected.value?.taskType ?? ''))
const title = computed(() => ({ create: '安排农事任务', complete: '登记实际完成', cancel: '取消农事任务', detail: '农事记录详情' })[mode.value ?? 'detail'])
async function refresh() { await Promise.all([tasks.load(), people.load()]) }
onMounted(refresh)
function canComplete(task: FarmTask) { return auth.canWrite.value && (auth.isAdmin.value || task.assignedTo === auth.user.value?.id) }
async function open(value: NonNullable<typeof mode.value>, task?: FarmTask) {
  selected.value = task ?? null; mode.value = value; error.value = ''; uncertain.value = false
  Object.assign(draft, { taskCode: `TASK-${newOperationKey()}`, taskType: 'IRRIGATION', title: '', plannedOn: today(), assignedTo: assignees.value.some(person => person.id === auth.user.value?.id) ? String(auth.user.value!.id) : '', instructions: '', performedAt: localDateTime(), inputName: '', quantity: '', unit: '', notes: '', evidenceReference: '', reason: '', requestKey: newOperationKey() })
  if (value === 'detail' && task?.status === 'COMPLETED') await execution.load()
}
function close() { if (!busy.value) mode.value = null }
async function save() {
  if (busy.value || uncertain.value) return
  busy.value = true; error.value = ''
  try {
    if (mode.value === 'create') {
      await createFarmTask(props.cycle.id, { taskCode: draft.taskCode, taskType: draft.taskType, title: draft.title, plannedOn: draft.plannedOn, assignedTo: Number(draft.assignedTo), instructions: draft.instructions })
      notice.value = '任务已安排，尚未计为实际完成。'
    } else if (mode.value === 'complete' && selected.value) {
      await completeFarmTask(selected.value.id, { performedAt: eventTime(draft.performedAt), inputName: draft.inputName, quantity: draft.quantity === '' ? undefined : Number(draft.quantity), unit: draft.unit, notes: draft.notes, evidenceReference: draft.evidenceReference, requestKey: draft.requestKey })
      notice.value = '实际执行记录已保存，任务已完成；投入品及执行时间保留在本轮档案中。'
    } else if (selected.value) { await cancelFarmTask(selected.value.id, draft.reason); notice.value = '任务已取消，取消原因和历史安排保留。' }
    mode.value = null; await refresh()
  } catch (cause) { error.value = operationError(cause); uncertain.value = uncertainOperation(cause) }
  finally { busy.value = false }
}
</script>
<template>
  <GlassPanel padded="large" class="stack management">
    <div class="section-head"><h2 class="display">本轮农事作业</h2><div class="actions"><button class="action-button" :disabled="tasks.loading.value" @click="refresh">刷新农事</button><button v-if="auth.canWrite.value && cycle.status === 'ACTIVE'" class="action-button primary" :disabled="people.loading.value || !!people.error.value" @click="open('create')">安排农事任务</button></div></div>
    <p class="muted">待执行任务优先显示，最多 200 条。任务负责人或管理员可登记完成；管理员代录会同时保留实际执行人和录入人。</p>
    <p v-if="people.error.value" class="notice bad" role="alert">生产人员读取失败，请刷新后重试。</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <div class="filters"><label>查找任务<input v-model="search" type="search" class="field-input" placeholder="任务名称、编码、负责人"></label><label>状态<select v-model="status" class="field-input"><option value="">全部状态</option><option value="PLANNED">待执行</option><option value="COMPLETED">已完成</option><option value="CANCELLED">已取消</option></select></label><label class="mine"><span>任务范围</span><span><input v-model="mine" type="checkbox"> 只看分配给我</span></label></div>
    <ResourceState :loading="tasks.loading.value" :error="tasks.error.value" :empty="!rows.length" empty-text="暂无符合条件的农事任务，可以为本轮安排第一次作业。" @retry="tasks.load"><div class="table-scroll"><table class="data-table"><thead><tr><th>任务 / 类型</th><th>计划日期</th><th>负责人</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="task in rows" :key="task.id"><td><strong>{{ task.title }}</strong><p class="muted">{{ TASK_TYPES[task.taskType] }} · {{ task.taskCode }}</p></td><td>{{ task.plannedOn }}<p v-if="task.status === 'PLANNED' && task.plannedOn < today()" class="field-error">已过计划日期，尚未登记完成</p></td><td>{{ task.assigneeName }}</td><td>{{ labels[task.status] }}</td><td><div class="actions"><button class="text-button" @click="open('detail', task)">详情</button><button v-if="task.status === 'PLANNED' && cycle.status === 'ACTIVE' && canComplete(task)" class="text-button" @click="open('complete', task)">登记完成</button><button v-if="task.status === 'PLANNED' && cycle.status === 'ACTIVE' && auth.canWrite.value" class="text-button" @click="open('cancel', task)">取消任务</button></div></td></tr></tbody></table></div></ResourceState>
    <AppDialog :open="mode !== null" :title="title" :busy="busy" @close="close">
      <div v-if="mode === 'detail' && selected" class="management stack"><dl class="detail-grid"><div><dt>任务</dt><dd>{{ selected.title }}</dd></div><div><dt>类型与状态</dt><dd>{{ TASK_TYPES[selected.taskType] }} · {{ labels[selected.status] }}</dd></div><div><dt>计划日期</dt><dd>{{ selected.plannedOn }}</dd></div><div><dt>任务负责人</dt><dd>{{ selected.assigneeName }}</dd></div><div class="full"><dt>作业要求</dt><dd class="pre-wrap">{{ selected.instructions || '未填写' }}</dd></div><div v-if="selected.cancelReason" class="full"><dt>取消说明</dt><dd>{{ selected.cancelReason }}</dd></div></dl><ResourceState v-if="selected.status === 'COMPLETED'" :loading="execution.loading.value" :error="execution.error.value" :empty="!execution.data.value" empty-text="未取得对应执行记录，请刷新核对。" @retry="execution.load"><dl class="detail-grid"><div><dt>实际执行人</dt><dd>{{ execution.data.value?.performerName }}</dd></div><div><dt>实际时间</dt><dd>{{ formatDateTime(execution.data.value?.performedAt) }}</dd></div><div><dt>投入品与实际用量</dt><dd>{{ execution.data.value?.inputName || '未使用或未填写' }} · {{ execution.data.value?.quantity ?? '—' }} {{ execution.data.value?.unit }}</dd></div><div><dt>录入人</dt><dd>{{ execution.data.value?.recordedName }}{{ execution.data.value?.recordedBy !== execution.data.value?.performedBy ? '（代录）' : '' }}</dd></div><div class="full"><dt>实际作业说明</dt><dd class="pre-wrap">{{ execution.data.value?.notes }}</dd></div><div class="full"><dt>凭证引用</dt><dd class="pre-wrap">{{ execution.data.value?.evidenceReference || '未填写' }}</dd></div></dl></ResourceState></div>
      <form v-else class="management" @submit.prevent="save"><fieldset :disabled="busy || uncertain">
        <template v-if="mode === 'create'"><label class="form-field">任务编码<input v-model.trim="draft.taskCode" class="field-input" required pattern="[A-Za-z0-9_-]{1,64}" maxlength="64"></label><label class="form-field">农事类型<select v-model="draft.taskType" class="field-input"><option v-for="(label,key) in TASK_TYPES" :key="key" :value="key">{{ label }}</option></select></label><label class="form-field">任务名称<input v-model.trim="draft.title" class="field-input" required maxlength="128" placeholder="例如 东棚滴灌检查"></label><div class="form-grid"><label class="form-field">计划日期<input v-model="draft.plannedOn" class="field-input" type="date" :min="cycle.plantedOn" required></label><label class="form-field">任务负责人<select v-model="draft.assignedTo" class="field-input" required><option value="" disabled>选择同机构生产人员</option><option v-for="person in assignees" :key="person.id" :value="String(person.id)">{{ person.displayName }} · {{ person.username }}</option></select></label></div><label class="form-field">作业要求（选填）<textarea v-model.trim="draft.instructions" class="field-input" rows="3" maxlength="2000"></textarea></label><p class="notice">本操作只安排任务，实际作业后再登记完成。</p></template>
        <template v-else-if="mode === 'complete' && selected"><p class="notice">{{ selected.title }} · 实际执行人 {{ selected.assigneeName }}。{{ auth.user.value?.id !== selected.assignedTo ? '本次为管理员代录，将保留你的录入身份。' : '' }}</p><label class="form-field">实际执行时间<input v-model="draft.performedAt" class="field-input" type="datetime-local" :min="`${cycle.plantedOn}T00:00`" :max="localDateTime()" required></label><label class="form-field">投入品名称{{ inputRequired ? '（必填）' : '（选填）' }}<input v-model.trim="draft.inputName" class="field-input" :required="inputRequired" maxlength="128"></label><div class="form-grid"><label class="form-field">实际用量<input v-model="draft.quantity" class="field-input" type="number" min="0.000001" step="any" :required="inputRequired"></label><label class="form-field">用量单位<input v-model.trim="draft.unit" class="field-input" :required="inputRequired || draft.quantity !== ''" maxlength="32" placeholder="如 kg、L、袋"></label></div><label class="form-field">实际作业说明<textarea v-model.trim="draft.notes" class="field-input" rows="3" maxlength="2000" required placeholder="记录实际做了什么、范围及结果"></textarea></label><label class="form-field">凭证引用（选填）<input v-model.trim="draft.evidenceReference" class="field-input" maxlength="500" placeholder="照片档案编号或纸质凭证编号"><small class="muted">本批仅保存引用文字，尚未提供照片上传。</small></label></template>
        <label v-else class="form-field">取消原因<textarea v-model.trim="draft.reason" class="field-input" rows="3" required maxlength="1000"></textarea></label>
      </fieldset><p v-if="error" class="notice bad" role="alert">{{ error }}</p><div class="actions form-actions"><button class="action-button primary" type="submit" :disabled="busy || uncertain">{{ busy ? '正在保存…' : mode === 'complete' ? '确认实际完成' : mode === 'create' ? '确认安排任务' : '确认取消任务' }}</button><button class="action-button" type="button" :disabled="busy" @click="close">关闭</button></div></form>
    </AppDialog>
  </GlassPanel>
</template>
<style scoped>h2 { font-size: var(--fs-h2); }.pre-wrap { white-space: pre-wrap; }.form-actions { margin-top: 20px; }.mine input { accent-color: var(--field); }</style>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import AppDialog from '@/components/AppDialog.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { ORGANIZATION_TYPES, ORGANIZATION_STATUS, organizationsPage, organizationDetail, createOrganization, updateOrganization, changeOrganizationStatus, type OrganizationInput } from '@/api/organizations'
import { newOperationKey, operationError, uncertainOperation } from '@/utils/growing'
import { formatDateTime } from '@/types/api'

const auth = useAuth()
const search = ref(''), term = ref(''), status = ref('ALL'), page = ref(1), selected = ref(0)
const ledger = useAsyncResource(() => organizationsPage(term.value, status.value, page.value))
const detail = useAsyncResource(() => organizationDetail(selected.value))
const organization = computed(() => detail.data.value?.organization)
const mode = ref<'create' | 'edit' | 'ACTIVE' | 'DISABLED' | 'DELETED' | null>(null)
const busy = ref(false), uncertain = ref(false), error = ref(''), notice = ref(''), approved = ref(false)
const draft = reactive<OrganizationInput>({ orgCode: '', orgName: '', orgType: 'FARM', contactName: '', contactPhone: '', address: '' })
const title = computed(() => mode.value === 'create' ? '新建机构' : mode.value === 'edit' ? '编辑机构资料' : mode.value === 'DELETED' ? '删除空机构' : `${ORGANIZATION_STATUS[mode.value as keyof typeof ORGANIZATION_STATUS] ?? ''}机构`)
watch(selected, () => { if (selected.value) void detail.load() })
onMounted(() => { void ledger.load() })
async function refresh() { await ledger.load(); if (selected.value) await detail.load() }
function filter() { term.value = search.value.trim(); page.value = 1; void ledger.load() }
async function turn(value: number) { page.value = value; await ledger.load() }
function open(value: NonNullable<typeof mode.value>) {
  if (value !== 'create' && !organization.value) return
  error.value = ''; uncertain.value = false; approved.value = false
  const row = organization.value
  Object.assign(draft, value === 'create' ? { orgCode: `ORG-${newOperationKey()}`, orgName: '', orgType: 'FARM', contactName: '', contactPhone: '', address: '' } : { orgCode: row?.orgCode ?? '', orgName: row?.orgName ?? '', orgType: row?.orgType ?? 'FARM', contactName: row?.contactName ?? '', contactPhone: row?.contactPhone ?? '', address: row?.address ?? '' })
  mode.value = value
}
function close() { if (!busy.value) mode.value = null }
async function save() {
  if (busy.value || uncertain.value || !mode.value) return
  if (['ACTIVE', 'DISABLED', 'DELETED'].includes(mode.value) && !approved.value) return
  busy.value = true; error.value = ''
  try {
    const input: OrganizationInput = { orgCode: draft.orgCode.trim(), orgName: draft.orgName.trim(), orgType: draft.orgType, contactName: draft.contactName.trim(), contactPhone: draft.contactPhone.trim(), address: draft.address.trim() }
    const result = mode.value === 'create' ? await createOrganization(input) : mode.value === 'edit' ? await updateOrganization(selected.value, input) : await changeOrganizationStatus(selected.value, mode.value)
    selected.value = result.id
    notice.value = mode.value === 'create' ? '机构已创建，可以在人员、产品、地块、仓库和订单中选择；已打开的表单可刷新机构选项。' : '机构资料或状态已更新，原有关联记录保留。'
    mode.value = null; await refresh()
  } catch (cause) { error.value = operationError(cause); uncertain.value = uncertainOperation(cause) }
  finally { busy.value = false }
}
</script>

<template>
  <div class="management stack organizations-page">
    <GlassPanel padded="large" class="stack">
      <div class="section-head"><div><p class="eyebrow">经营主体与业务归属</p><h2 class="display">机构管理</h2></div><div class="actions"><button class="action-button" :disabled="ledger.loading.value || busy" @click="refresh">刷新机构</button><button class="action-button primary" :disabled="busy" @click="open('create')">新建机构</button></div></div>
      <p class="muted">统一维护农场、合作社、仓储和运输等主体。人员和业务按机构归属；机构类型用于档案分类，人员权限仍由账号角色决定。</p>
      <p v-if="notice" class="notice" role="status">{{ notice }}</p>
      <form class="filters" @submit.prevent="filter"><label>查找机构<input v-model="search" class="field-input" maxlength="128" placeholder="名称、编码、联系人或地址"></label><label>状态<select v-model="status" class="field-input" @change="filter"><option value="ALL">全部未删除机构</option><option v-for="(label,key) in ORGANIZATION_STATUS" :key="key" :value="key">{{ label }}</option></select></label><button class="action-button" :disabled="ledger.loading.value">查询</button></form>
      <ResourceState :loading="ledger.loading.value" :error="ledger.error.value" :empty="!ledger.data.value?.rows.length" empty-text="没有匹配机构，可调整筛选或新建机构。" @retry="ledger.load">
        <div class="table-scroll"><table class="data-table"><thead><tr><th>机构名称 / 编码</th><th>类型</th><th>联系人</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in ledger.data.value?.rows" :key="row.id"><td>{{ row.orgName }}<p class="mono">{{ row.orgCode }}</p></td><td>{{ ORGANIZATION_TYPES[row.orgType] || row.orgType }}</td><td>{{ row.contactName || '未填写' }}<p>{{ row.contactPhone }}</p></td><td>{{ ORGANIZATION_STATUS[row.status] }}<p v-if="row.id === auth.user.value?.organizationId" class="muted">当前账号所属机构</p></td><td><button class="text-button" :disabled="busy" @click="selected = row.id">查看资料与关联</button></td></tr></tbody></table></div>
      </ResourceState>
      <div v-if="ledger.data.value" class="section-head"><p class="muted">共 {{ ledger.data.value.total }} 个 · 第 {{ ledger.data.value.page }} 页</p><div class="actions"><button class="action-button" :disabled="ledger.loading.value || ledger.data.value.page <= 1" @click="turn(ledger.data.value!.page - 1)">上一页</button><button class="action-button" :disabled="ledger.loading.value || ledger.data.value.page * 20 >= ledger.data.value.total" @click="turn(ledger.data.value!.page + 1)">下一页</button></div></div>
    </GlassPanel>
    <GlassPanel v-if="selected" padded="large" class="stack">
      <ResourceState :loading="detail.loading.value" :error="detail.error.value" @retry="detail.load"><template v-if="organization">
        <div class="section-head"><div><p class="eyebrow">{{ ORGANIZATION_STATUS[organization.status] }}</p><h2 class="display">{{ organization.orgName }}</h2><p class="mono">{{ organization.orgCode }} · #{{ organization.id }}</p></div><div v-if="organization.status !== 'DELETED'" class="actions"><button class="action-button" :disabled="busy" @click="open('edit')">编辑资料</button><button v-if="organization.status === 'DISABLED'" class="action-button" :disabled="busy" @click="open('ACTIVE')">启用机构</button><button v-else class="action-button" :disabled="busy || organization.id === auth.user.value?.organizationId" @click="open('DISABLED')">停用机构</button><button class="action-button" :disabled="busy || !detail.data.value?.canDelete" @click="open('DELETED')">删除空机构</button></div></div>
        <dl class="detail-grid"><div><dt>类型</dt><dd>{{ ORGANIZATION_TYPES[organization.orgType] || organization.orgType }}</dd></div><div><dt>联系人 / 电话</dt><dd>{{ organization.contactName || '未填写' }} · {{ organization.contactPhone || '未填写' }}</dd></div><div><dt>地址</dt><dd class="pre-wrap">{{ organization.address || '未填写' }}</dd></div><div><dt>最近更新</dt><dd>{{ formatDateTime(organization.updateTime) }}</dd></div></dl>
        <h3 class="display">关联记录</h3><div class="dependency-grid"><div v-for="item in detail.data.value?.dependencies" :key="item.label" class="dependency"><span>{{ item.label }}</span><strong>{{ item.total }}</strong></div></div>
        <p class="notice">已有关联人员或业务的机构不能删除，可停用并保留历史。停用后，该机构账号不能登录，也不能被用于新建人员、产品、地块、仓库、订单或分配设备；管理员仍可核对并处理原有业务。</p>
        <p v-if="organization.id === auth.user.value?.organizationId" class="notice">当前账号所属机构不能停用或删除，避免失去管理入口。</p>
      </template></ResourceState>
    </GlassPanel>
    <AppDialog :open="mode !== null" :title="title" :busy="busy" @close="close"><form class="management stack" @submit.prevent="save"><fieldset class="stack" :disabled="busy || uncertain">
      <template v-if="mode === 'create' || mode === 'edit'">
        <label class="form-field">机构编码<input v-model.trim="draft.orgCode" class="field-input" maxlength="64" pattern="[A-Za-z0-9_-]{1,64}" required :readonly="mode === 'edit'"></label>
        <p class="muted">编码建立后保留，用于识别同一经营主体。</p>
        <label class="form-field">机构名称<input v-model.trim="draft.orgName" class="field-input" maxlength="128" required></label>
        <label class="form-field">机构类型<select v-model="draft.orgType" class="field-input" required><option v-for="(label,key) in ORGANIZATION_TYPES" :key="key" :value="key">{{ label }}</option><option v-if="draft.orgType && !ORGANIZATION_TYPES[draft.orgType]" :value="draft.orgType">{{ draft.orgType }}（原类型）</option></select></label>
        <div class="form-grid"><label class="form-field">联系人（选填）<input v-model.trim="draft.contactName" class="field-input" maxlength="64"></label><label class="form-field">联系电话（选填）<input v-model.trim="draft.contactPhone" class="field-input" maxlength="32"></label></div>
        <label class="form-field">地址（选填）<textarea v-model.trim="draft.address" class="field-input" maxlength="255" rows="3"></textarea></label>
      </template>
      <template v-else><p>{{ organization?.orgName }} · {{ organization?.orgCode }}</p><p v-if="mode === 'DISABLED'" class="notice wait">停用会使所属账号失去后台访问权限，并从新建业务的机构选项中移除；原有记录保留，之后可重新启用。</p><p v-else-if="mode === 'DELETED'" class="notice wait">仅无人员或业务关联的空机构可删除。删除后退出可选机构，原编码及管理留档保留，不能重新启用。</p><p v-else class="notice">启用后重新出现在业务选项中；所属已停用账号不会自动启用。</p><label class="approval"><input v-model="approved" type="checkbox" required>已核对机构及本次操作影响</label></template>
    </fieldset><p v-if="error" class="notice bad" role="alert">{{ error }}</p><div class="actions"><button class="action-button primary" :disabled="busy || uncertain">{{ busy ? '正在保存…' : '确认保存' }}</button><button type="button" class="action-button" :disabled="busy" @click="close">关闭</button></div></form></AppDialog>
  </div>
</template>

<style scoped>
.organizations-page { min-width: 0; } h2 { font-size: var(--fs-h2); } .mono, dd { overflow-wrap: anywhere; } .pre-wrap { white-space: pre-wrap; }
.dependency-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 10px; }
.dependency { display: flex; justify-content: space-between; gap: 12px; border: 1px solid var(--ink-16); border-radius: var(--radius-sm); padding: 12px; }
</style>

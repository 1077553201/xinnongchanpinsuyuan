<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import AppDialog from '@/components/AppDialog.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { useOrganizationDirectory } from '@/composables/useOrganizationDirectory'
import OrganizationHelp from '@/components/OrganizationHelp.vue'
import { ApiError, get, post } from '@/api/http'

interface Account { id: number; username: string; displayName: string; organizationId: number; status: string; roles: string[] }
const auth = useAuth()
const router = useRouter()
const accounts = useAsyncResource(() => get<Account[]>('/auth/accounts'))
const organizations = useOrganizationDirectory()
const names = computed(() => organizations.names.value)
const draft = reactive({ username: '', password: '', displayName: '', organizationId: '', role: 'MANAGER' })
const password = reactive({ currentPassword: '', newPassword: '', confirm: '' })
const mode = ref<'create' | 'password' | 'disable' | null>(null)
const selected = ref<Account | null>(null)
const busy = ref(false)
const uncertain = ref(false)
const error = ref('')
const notice = ref('')
const labels: Record<string, string> = { ADMIN: '管理员', MANAGER: '生产人员', VIEWER: '只读人员' }
const title = computed(() => mode.value === 'create' ? '创建账号' : mode.value === 'password' ? '修改本人密码' : '停用账号')
async function refresh() { if (auth.isAdmin.value) await Promise.all([accounts.load(), organizations.load()]) }
onMounted(refresh)
function open(value: NonNullable<typeof mode.value>, account?: Account) {
  mode.value = value; selected.value = account ?? null; error.value = ''; uncertain.value = false
  Object.assign(draft, { username: '', password: '', displayName: '', organizationId: '', role: 'MANAGER' })
  Object.assign(password, { currentPassword: '', newPassword: '', confirm: '' })
}
function close() { if (!busy.value) { mode.value = null; draft.password = ''; Object.assign(password, { currentPassword: '', newPassword: '', confirm: '' }) } }
async function save() {
  if (busy.value || uncertain.value) return
  if (mode.value === 'password' && password.newPassword !== password.confirm) { error.value = '两次新密码不一致。'; return }
  busy.value = true; error.value = ''
  try {
    if (mode.value === 'password') {
      await post('/auth/password', { currentPassword: password.currentPassword, newPassword: password.newPassword })
      auth.clear(); mode.value = null; await router.replace('/login'); return
    }
    if (mode.value === 'create') {
      await post('/auth/accounts', { ...draft, organizationId: Number(draft.organizationId) })
      notice.value = '账号已创建，请通过安全方式将登录资料交给使用人。'
    } else if (selected.value) {
      await post(`/auth/accounts/${selected.value.id}/disable`)
      notice.value = '账号已停用，已有会话将在下次请求时失效。'
    }
    draft.password = ''; mode.value = null; await refresh()
  } catch (cause) {
    if (cause instanceof ApiError && ['400', '403'].includes(cause.code)) error.value = cause.message
    else if (cause instanceof ApiError && cause.code === '409') error.value = '账号名称已存在，请核对后使用其他名称。'
    else { uncertain.value = true; error.value = '无法确认操作结果，请关闭窗口并核对账号状态；修改密码后可尝试重新登录。' }
  } finally { busy.value = false }
}
</script>

<template>
  <div class="management stack">
    <GlassPanel padded="large" class="stack">
      <div class="section-head"><div><p class="eyebrow">当前身份</p><h2 class="display">{{ auth.user.value?.displayName }}</h2></div><button class="action-button" @click="open('password')">修改本人密码</button></div>
      <p class="muted">{{ auth.user.value?.username }} · {{ labels[auth.user.value?.role ?? ''] }}。生产人员可维护本机构生产记录，只读人员可查看记录；系统配置与旧溯源模块由管理员维护。</p>
      <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    </GlassPanel>
    <GlassPanel v-if="auth.isAdmin.value" padded="large">
      <div class="section-head"><h2 class="display">人员账号</h2><OrganizationHelp :loading="organizations.loading.value" @refresh="organizations.load" /><div class="actions"><button class="action-button" :disabled="accounts.loading.value" @click="refresh">刷新</button><button class="action-button primary" :disabled="organizations.loading.value || !!organizations.error.value" @click="open('create')">创建账号</button></div></div>
      <ResourceState :loading="accounts.loading.value" :error="accounts.error.value" @retry="refresh">
        <div class="table-scroll"><table class="data-table"><thead><tr><th>人员 / 账号</th><th>所属机构</th><th>权限</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="account in accounts.data.value" :key="account.id"><td>{{ account.displayName }}<p class="mono muted">{{ account.username }}</p></td><td>{{ names.get(account.organizationId) ?? account.organizationId }}</td><td>{{ account.roles.map(role => labels[role] ?? role).join('、') }}</td><td>{{ account.status === 'ACTIVE' ? '可登录' : '已停用' }}</td><td><button v-if="account.status === 'ACTIVE' && account.id !== auth.user.value?.id" class="text-button" @click="open('disable', account)">停用</button><span v-else class="muted">{{ account.id === auth.user.value?.id ? '当前账号' : '—' }}</span></td></tr></tbody></table></div>
      </ResourceState>
    </GlassPanel>
    <AppDialog :open="mode !== null" :title="title" :busy="busy" @close="close">
      <form class="management" @submit.prevent="save">
        <fieldset :disabled="busy || uncertain">
          <template v-if="mode === 'create'">
            <label class="form-field">所属机构<select v-model="draft.organizationId" class="field-input" required><option value="" disabled>请选择</option><option v-for="org in organizations.data.value" :key="org.id" :value="String(org.id)">{{ org.name }} · {{ org.code || `#${org.id}` }}</option></select></label>
            <OrganizationHelp :loading="organizations.loading.value" @refresh="organizations.load" /><p v-if="organizations.error.value" class="notice bad">{{ organizations.error.value }}</p><p v-else-if="!organizations.loading.value && !organizations.data.value?.length" class="notice wait">尚无启用机构，请先创建或启用机构。</p><label class="form-field">使用人姓名<input v-model.trim="draft.displayName" class="field-input" required maxlength="64"></label>
            <label class="form-field">登录账号<input v-model.trim="draft.username" class="field-input" required pattern="[A-Za-z0-9_-]{3,64}" maxlength="64" autocomplete="off" placeholder="3–64 位字母、数字、短横线或下划线"></label>
            <label class="form-field">初始密码<input v-model="draft.password" class="field-input" type="password" required minlength="12" maxlength="128" autocomplete="new-password"></label>
            <label class="form-field">权限角色<select v-model="draft.role" class="field-input"><option value="MANAGER">生产人员 · 本机构生产读写</option><option value="VIEWER">只读人员 · 本机构查询</option><option value="ADMIN">管理员 · 全部机构与系统配置</option></select></label>
          </template>
          <template v-else-if="mode === 'password'">
            <p class="notice">修改后需要重新登录，其他已登录会话也会失效。</p>
            <label class="form-field">当前密码<input v-model="password.currentPassword" class="field-input" type="password" autocomplete="current-password" required maxlength="128"></label>
            <label class="form-field">新密码（至少 12 位）<input v-model="password.newPassword" class="field-input" type="password" autocomplete="new-password" required minlength="12" maxlength="128"></label>
            <label class="form-field">再次输入新密码<input v-model="password.confirm" class="field-input" type="password" autocomplete="new-password" required minlength="12" maxlength="128"></label>
          </template>
          <p v-else class="notice wait">确认停用 {{ selected?.displayName }}（{{ selected?.username }}）？此人将无法继续访问后台，历史操作记录保留。</p>
        </fieldset>
        <p v-if="error" class="notice bad" role="alert">{{ error }}</p>
        <div class="actions form-actions"><button class="action-button primary" type="submit" :disabled="busy || uncertain">{{ busy ? '正在处理…' : mode === 'create' ? '确认创建' : mode === 'password' ? '确认修改密码' : '确认停用' }}</button><button class="action-button" type="button" :disabled="busy" @click="close">关闭</button></div>
      </form>
    </AppDialog>
  </div>
</template>
<style scoped>h2 { margin-top: 8px; font-size: var(--fs-h2); }.form-actions { margin-top: 20px; }.table-scroll { margin-top: 20px; }</style>

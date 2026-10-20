<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import StatusChip from '@/components/StatusChip.vue'
import AppDialog from '@/components/AppDialog.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { bindNfc, disableNfc, listItems, listNfcTags } from '@/api/catalog'
import { ApiError } from '@/api/http'
import { batchSaveFeedback } from '@/utils/catalog'
import { formatDateTime, shortHash } from '@/types/api'
import type { NfcTagEntity } from '@/types/catalog'

const tags = useAsyncResource(listNfcTags), items = useAsyncResource(listItems)
const query = ref(''), status = ref(''), mode = ref<'bind' | 'disable' | null>(null)
const busy = ref(false), uncertain = ref(false), error = ref(''), notice = ref('')
const selectedTag = ref<NfcTagEntity | null>(null)
const draft = reactive({ itemId: '', nfcUid: '', tagHash: '', reason: '' })
const itemMap = computed(() => new Map((items.data.value ?? []).map(item => [item.id, item])))
const eligible = computed(() => (items.data.value ?? []).filter(item => item.status === 'ACTIVE' && item.nfcStatus === 'UNBOUND' && !(tags.data.value ?? []).some(tag => tag.itemId === item.id)))
const filtered = computed(() => (tags.data.value ?? []).filter(tag => (!status.value || tag.status === status.value) && (!query.value.trim() || [tag.uidHash, itemMap.value.get(tag.itemId ?? 0)?.traceCodePlain].some(value => value?.toLowerCase().includes(query.value.trim().toLowerCase())))))
async function refresh() { await Promise.all([tags.load(), items.load()]) }
onMounted(refresh)
function openBind() { Object.assign(draft, { itemId: '', nfcUid: '', tagHash: '', reason: '' }); error.value = ''; uncertain.value = false; mode.value = 'bind' }
function openDisable(tag: NfcTagEntity) { selectedTag.value = tag; draft.reason = ''; error.value = ''; uncertain.value = false; mode.value = 'disable' }
async function save() {
  if (busy.value || uncertain.value) return
  if (mode.value === 'bind' && (!eligible.value.some(item => item.id === Number(draft.itemId)) || !draft.nfcUid.trim())) { error.value = '请选择尚未绑定标签的有效单品，并填写标签 UID。'; return }
  if (mode.value === 'disable' && !draft.reason.trim()) { error.value = '请填写停用原因。'; return }
  busy.value = true; error.value = ''
  try {
    const result = mode.value === 'bind'
      ? await bindNfc({ itemId: Number(draft.itemId), nfcUid: draft.nfcUid.trim(), tagHash: draft.tagHash.trim() || undefined })
      : await disableNfc(selectedTag.value!.id, { reasonPayload: { reason: draft.reason.trim() } })
    notice.value = batchSaveFeedback(result.chainStatus).message
    mode.value = null; draft.nfcUid = ''; await refresh()
  } catch (cause) {
    if (cause instanceof ApiError && ['400', '409'].includes(cause.code)) error.value = '该标签或单品已绑定、已停用或状态不允许操作，请刷新后核对。'
    else { uncertain.value = true; error.value = '提交结果未确认，请关闭窗口并刷新标签列表核对，避免重复提交。' }
  } finally { busy.value = false }
}
</script>

<template>
  <div class="management stack">
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <GlassPanel class="stack">
      <div class="toolbar"><div><p class="eyebrow">标签关联</p><h2 class="display">NFC 与单品身份</h2></div><div class="actions"><button class="action-button" type="button" :disabled="tags.loading.value" @click="refresh">刷新标签</button><button class="action-button primary" type="button" :disabled="!eligible.length || !!items.error.value || !!tags.error.value" @click="openBind">绑定标签</button></div></div>
      <p class="muted tiny">当前提供 UID 与单品的一次绑定及停用；普通 UID 关联不代表物理防克隆，换绑和动态签名尚未提供。</p>
      <p v-if="items.error.value" class="notice bad">单品资料读取失败，暂时无法绑定，请刷新重试。</p>
      <form class="filters" role="search" @submit.prevent><label>搜索标签或单品<input v-model="query" class="field-input" type="search" placeholder="UID 摘要或溯源编号"></label><label>标签状态<select v-model="status" class="field-input"><option value="">全部状态</option><option value="ACTIVE">有效</option><option value="DISABLED">已停用</option></select></label></form>
      <ResourceState :loading="tags.loading.value" :error="tags.error.value" :empty="!filtered.length" empty-text="暂无符合条件的标签。先生成单品码，再绑定标签。" @retry="tags.load"><div class="table-scroll" tabindex="0" aria-label="NFC 标签列表，可横向滚动"><table class="data-table"><caption class="sr-only">最近 200 条 NFC 标签</caption><thead><tr><th scope="col">标签摘要</th><th scope="col">关联单品</th><th scope="col">状态</th><th scope="col">上链状态</th><th scope="col">绑定时间</th><th scope="col">操作</th></tr></thead><tbody><tr v-for="tag in filtered" :key="tag.id"><td class="mono" :title="tag.uidHash">{{ shortHash(tag.uidHash) }}</td><td>{{ itemMap.get(tag.itemId ?? 0)?.traceCodePlain || `单品 #${tag.itemId}` }}</td><td><StatusChip :tone="tag.status === 'ACTIVE' ? 'ok' : 'off'" :label="tag.status === 'ACTIVE' ? '有效' : tag.status === 'DISABLED' ? '已停用' : '状态待确认'" compact /></td><td><StatusChip :status="tag.chainStatus" compact /></td><td>{{ formatDateTime(tag.boundAt) }}</td><td><div class="actions"><RouterLink v-if="itemMap.get(tag.itemId ?? 0)?.traceCodePlain" class="text-button" :to="{ name: 'public-trace', params: { traceCode: itemMap.get(tag.itemId ?? 0)!.traceCodePlain! } }">公众查询</RouterLink><button class="text-button" type="button" :disabled="tag.status !== 'ACTIVE' || !['CONFIRMED','CHAIN_DISABLED'].includes(tag.chainStatus ?? '')" @click="openDisable(tag)">停用</button></div></td></tr></tbody></table></div></ResourceState>
      <p class="muted tiny">显示最近最多 200 条标签。原始 UID 不在列表中返回，只展示摘要。</p>
    </GlassPanel>
    <AppDialog :open="mode !== null" :title="mode === 'bind' ? '绑定 NFC 标签' : '确认停用标签'" :busy="busy" @close="mode = null"><form @submit.prevent="save"><fieldset :disabled="busy || uncertain"><template v-if="mode === 'bind'"><div class="form-field"><label class="field-label" for="nfc-item">关联单品 *</label><select id="nfc-item" v-model="draft.itemId" class="field-input" required><option value="">请选择单品</option><option v-for="item in eligible" :key="item.id" :value="String(item.id)">{{ item.traceCodePlain || `单品 #${item.id}` }}</option></select></div><div class="form-field"><label class="field-label" for="nfc-uid">标签 UID *</label><input id="nfc-uid" v-model="draft.nfcUid" class="field-input" required maxlength="128" autocomplete="off"></div><div class="form-field"><label class="field-label" for="nfc-hash">标签资料摘要（选填）</label><input id="nfc-hash" v-model="draft.tagHash" class="field-input" maxlength="64"></div></template><template v-else><p>将停用标签 #{{ selectedTag?.id }}，并提交链上状态变更。此操作没有恢复入口。</p><div class="form-field"><label class="field-label" for="nfc-reason">停用原因 *</label><textarea id="nfc-reason" v-model="draft.reason" class="field-input" required maxlength="1000" rows="3"></textarea></div></template></fieldset><button class="action-button primary" type="submit" :disabled="busy || uncertain">{{ busy ? '提交中…' : mode === 'bind' ? '确认绑定并上链' : '确认停用并上链' }}</button><p v-if="error" class="notice bad" role="alert">{{ error }}</p></form></AppDialog>
  </div>
</template>
<style scoped>h2 { font-size: var(--fs-h2); margin-top: 8px; }</style>

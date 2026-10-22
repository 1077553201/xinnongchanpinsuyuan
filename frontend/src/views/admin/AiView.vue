<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import ChatTrace from '@/components/ChatTrace.vue'
import AppDialog from '@/components/AppDialog.vue'
import AgentWorkbench from '@/components/AgentWorkbench.vue'
import ResourceState from '@/components/ResourceState.vue'
import { agentStatus } from '@/api/agent'
import { conversations, conversationDetail, newConversation, sendChat, type Conversation, type ChatTurn, type ChatEvidence } from '@/api/chat'
import { newOperationKey, operationError } from '@/utils/growing'
import { formatDateTime } from '@/types/api'
import { useAsyncResource } from '@/composables/useAsyncResource'

const route = useRoute(), status = useAsyncResource(agentStatus)
const page = ref(1), history = useAsyncResource(() => conversations(page.value)), current = ref<Conversation | null>(null), turns = ref<ChatTurn[]>([])
const input = ref(''), loading = ref(false), sending = ref(false), sentText = ref(''), error = ref(''), total = ref(0), olderLoading = ref(false)
const listElement = ref<HTMLElement>(), inputElement = ref<HTMLTextAreaElement>(), picker = ref(false)
const workbench = ref(false), reportId = ref<number>(), workHistory = ref(false), workBusy = ref(false)
const pending = computed(() => turns.value.find(t => t.status === 'PENDING'))
let poll: ReturnType<typeof setTimeout> | undefined, disposed = false, selection = 0
let lastAttempt: { id: number; text: string; key: string } | undefined
const starters = ['帮我看看现在有哪些地块在种植', '最近有哪些农事还没完成？', '帮我找一下仓库里有哪些现货', '我刚采收了一批，帮我记一下']
function evidence(turn: ChatTurn): ChatEvidence[] { try { return JSON.parse(turn.evidenceJson || '[]') } catch { return [] } }
function expired(turn: ChatTurn) { return Date.now() >= new Date(turn.leaseUntil.replace(' ', 'T') + (/[Z+]\d*$/.test(turn.leaseUntil) ? '' : '+08:00')).getTime() }
async function bottom() { await nextTick(); if (listElement.value) listElement.value.scrollTop = listElement.value.scrollHeight }
function schedule() {
  clearTimeout(poll)
  if (disposed || (!sending.value && (!pending.value || expired(pending.value)))) return
  poll = setTimeout(async () => { if (current.value) await reload(false); schedule() }, 1200)
}
async function reload(scroll = true) {
  if (!current.value) return
  const id = current.value.id, revision = selection
  const element = listElement.value
  const follow = scroll || !element || element.scrollHeight - element.scrollTop - element.clientHeight < 100
  try {
    const result = await conversationDetail(id)
    if (disposed || revision !== selection || current.value?.id !== id) return
    const before = turns.value.map(t => `${t.id}:${t.status}:${t.evidenceJson?.length}`).join(',')
    current.value = result.conversation; turns.value = result.turns; total.value = result.total
    if (follow && before !== turns.value.map(t => `${t.id}:${t.status}:${t.evidenceJson?.length}`).join(',')) await bottom()
  } catch (cause) { error.value = operationError(cause) }
}
async function open(item: Conversation) {
  if (sending.value) return
  selection++; clearTimeout(poll); current.value = item; turns.value = []; total.value = 0; error.value = ''; input.value = ''; picker.value = false; loading.value = true
  await reload(); loading.value = false; schedule()
}
function fresh() { if (sending.value) return; selection++; clearTimeout(poll); current.value = null; turns.value = []; total.value = 0; error.value = ''; input.value = ''; picker.value = false; lastAttempt = undefined; void nextTick(() => inputElement.value?.focus()) }
async function send(retry?: ChatTurn) {
  const text = retry?.userText ?? input.value.trim()
  if (!text || sending.value || (!retry && pending.value) || !status.data.value?.configured) return
  sending.value = true; error.value = ''; clearTimeout(poll); sentText.value = retry ? '' : text
  try {
    if (!current.value) { current.value = await newConversation(); await history.load() }
    const id = current.value.id
    const key = retry?.requestKey ?? (lastAttempt?.id === id && lastAttempt.text === text ? lastAttempt.key : newOperationKey())
    lastAttempt = { id, text, key }; if (!retry) input.value = ''; await bottom()
    schedule()
    await sendChat(id, text, key); lastAttempt = undefined
  } catch (cause) { error.value = operationError(cause) + ' 可刷新对话后重试原消息，业务不会因发送聊天而自动执行。' }
  finally { sending.value = false; sentText.value = ''; await reload(); if (lastAttempt && !retry && !turns.value.some(t => t.requestKey === lastAttempt?.key)) input.value = text; await history.load(); schedule(); await bottom() }
}
async function older() {
  if (!current.value || !turns.value.length || olderLoading.value) return
  olderLoading.value = true; const id = current.value.id, revision = selection, oldHeight = listElement.value?.scrollHeight ?? 0
  try { const result = await conversationDetail(id, turns.value[0]!.id); if (revision === selection) { turns.value = [...result.turns, ...turns.value]; await nextTick(); if (listElement.value) listElement.value.scrollTop += listElement.value.scrollHeight - oldHeight } }
  catch (cause) { error.value = operationError(cause) }
  finally { olderLoading.value = false }
}
function keydown(event: KeyboardEvent) { if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); void send() } }
function choose(text: string) { input.value = text; void nextTick(() => inputElement.value?.focus()) }
function showReport(id?: number) { reportId.value = id; workHistory.value = !id; workbench.value = true }
async function turnPage(next: number) { page.value = next; await history.load() }
onMounted(async () => { await Promise.all([status.load(), history.load()]); const id = Number(route.query.report); if (Number.isSafeInteger(id) && id > 0) showReport(id) })
onBeforeUnmount(() => { disposed = true; clearTimeout(poll); selection++ })
watch(() => sending.value, value => { if (!value) schedule() })
</script>

<template>
  <div class="assistant-workspace">
    <aside class="conversation-rail glass"><div class="rail-head"><p class="eyebrow">随时接着聊</p><button class="action-button primary" :disabled="sending" @click="fresh">＋ 新对话</button></div><ResourceState :loading="history.loading.value" :error="history.error.value" @retry="history.load"><nav class="conversation-list" aria-label="历史对话"><button v-for="item in history.data.value?.rows" :key="item.id" :class="{ active: current?.id === item.id }" :disabled="sending" @click="open(item)"><strong>{{ item.title }}</strong><span>{{ formatDateTime(item.updatedAt) }}</span></button><p v-if="!history.data.value?.rows.length" class="muted empty-history">对话会保存在这里。</p></nav></ResourceState><div class="history-pages"><button class="text-button" :disabled="sending || (history.data.value?.page ?? 1) <= 1" @click="turnPage((history.data.value?.page ?? 1) - 1)">上一页</button><button class="text-button" :disabled="sending || (history.data.value?.page ?? 1) * 20 >= (history.data.value?.total ?? 0)" @click="turnPage((history.data.value?.page ?? 1) + 1)">下一页</button></div><button class="history-action" :disabled="sending" @click="showReport()">操作单与执行记录 →</button></aside>
    <section class="chat-panel glass" aria-label="农场日常助手"><header class="chat-head"><div><p class="eyebrow">农场日常助手</p><h2>{{ current?.title || '今天想先处理什么？' }}</h2></div><div class="chat-tools"><button class="action-button mobile-history" :disabled="sending" @click="picker = true">对话记录</button><button class="action-button mobile-new" :disabled="sending" @click="fresh">新对话</button><button class="action-button" :disabled="sending || !current" @click="reload(); schedule()">刷新</button></div></header>
      <div ref="listElement" class="message-list" aria-live="polite" aria-relevant="additions"><ResourceState :loading="loading || status.loading.value" :error="status.error.value" @retry="status.load" /><div v-if="!current && !loading" class="welcome"><div class="assistant-mark" aria-hidden="true">田</div><h3>问一件事，或把刚才的情况告诉我。</h3><p>查地块、看库存、聊种植和日常安排。需要办理时，我们再一起核对。</p><div class="starter-list"><button v-for="text in starters" :key="text" @click="choose(text)">{{ text }} <span aria-hidden="true">↗</span></button></div></div>
        <button v-if="turns.length < total" class="older-button" :disabled="olderLoading" @click="older">{{ olderLoading ? '正在读取…' : '查看更早的消息' }}</button>
        <template v-for="turn in turns" :key="turn.id"><article class="message user-message"><p class="speaker">你 <time>{{ formatDateTime(turn.createdAt) }}</time></p><div class="user-bubble">{{ turn.userText }}</div></article><article class="message assistant-message"><p class="speaker">农场助理</p><ChatTrace :events="evidence(turn)" :active="turn.status === 'PENDING' && !expired(turn)" /><div v-if="turn.status === 'COMPLETE'" class="reply-text">{{ turn.replyText }}</div><p v-else-if="turn.status === 'PENDING'" class="muted">{{ expired(turn) ? '这条回复尚未完成，可以恢复原消息。' : '正在处理，执行步骤和查询结果会在上方持续更新。' }}</p><p v-else class="reply-error">{{ turn.errorMessage || '这条回复未生成，请重试原消息。' }}</p><button v-if="turn.reportId" class="proposal-button" @click="showReport(turn.reportId)"><span>操作单 #{{ turn.reportId }}</span><strong>核对事项与执行结果 →</strong><small>聊天不会自动办理，打开后可补齐、勾选并确认。</small></button><button v-if="turn.id === turns.at(-1)?.id && (turn.status === 'FAILED' || (turn.status === 'PENDING' && expired(turn)))" class="action-button" :disabled="sending" @click="send(turn)">{{ turn.status === 'FAILED' ? '重试这条回复' : '恢复原消息' }}</button></article></template>
        <template v-if="sending"><article v-if="sentText && !turns.some(t => t.requestKey === lastAttempt?.key)" class="message user-message"><p class="speaker">你</p><div class="user-bubble">{{ sentText }}</div></article><p v-if="!pending" class="thinking" role="status">正在连接助理，受理后会显示执行步骤…</p></template>
      </div>
      <footer class="composer"><p v-if="error" role="alert" class="composer-error">{{ error }}</p><p v-if="status.data.value && !status.data.value.configured" class="composer-error">模型尚未启用。请核对本地模型配置并重启后端，然后刷新。</p><form @submit.prevent="send()"><label class="sr-only" for="assistant-message">给农场助理发消息</label><textarea id="assistant-message" ref="inputElement" v-model="input" rows="2" maxlength="4000" :disabled="sending || !!pending || !status.data.value?.configured" placeholder="有什么想查的，或刚刚发生了什么？" @keydown="keydown"></textarea><button class="send-button" :disabled="sending || !!pending || !input.trim() || !status.data.value?.configured" aria-label="发送消息">{{ sending ? '回复中' : '发送 ↑' }}</button></form><div class="composer-hint"><span>Enter 发送 · Shift + Enter 换行</span><button class="text-button mobile-operations" @click="showReport()">操作单记录</button><span>办理业务前会请你核对确认</span></div></footer>
    </section>
    <AppDialog :open="picker" title="继续之前的对话" @close="picker = false"><div class="conversation-list"><button v-for="item in history.data.value?.rows" :key="item.id" @click="open(item)"><strong>{{ item.title }}</strong><span>{{ formatDateTime(item.updatedAt) }}</span></button><div class="history-pages"><button class="text-button" :disabled="(history.data.value?.page ?? 1) <= 1" @click="turnPage((history.data.value?.page ?? 1) - 1)">上一页</button><button class="text-button" :disabled="(history.data.value?.page ?? 1) * 20 >= (history.data.value?.total ?? 0)" @click="turnPage((history.data.value?.page ?? 1) + 1)">下一页</button></div></div></AppDialog>
    <AppDialog :open="workbench" title="操作单与执行记录" :busy="workBusy" @close="workbench = false"><AgentWorkbench v-if="workbench" :key="reportId ?? 'history'" :report-id="reportId" :history="workHistory" @busy="workBusy = $event" /></AppDialog>
  </div>
</template>

<style scoped>
.assistant-workspace { width: 100%; min-width: 0; display: grid; grid-template-columns: 220px minmax(0,1fr); gap: 16px; height: calc(100dvh - 152px); min-height: 520px; }.conversation-rail { min-width: 0; border-radius: var(--radius-lg); padding: 18px 12px; display: flex; flex-direction: column; gap: 16px; min-height: 0; }.rail-head { padding: 0 6px; display: grid; gap: 12px; }.primary { color: white; background: var(--field); }.conversation-list { grid-template-columns: minmax(0,1fr); display: grid; gap: 6px; overflow-y: auto; min-height: 0; }.conversation-list button { border: 0; width: 100%; overflow: hidden; display: grid; gap: 6px; text-align: left; padding: 12px; border-radius: 12px; color: var(--ink); background: transparent; min-width: 0; }.conversation-list strong { font-size: var(--fs-sm); font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.conversation-list span { font-size: var(--fs-xs); color: var(--slate); }.conversation-list button.active,.conversation-list button:hover { background: var(--field-tint); }.history-pages { display: flex; justify-content: space-between; gap: 12px; padding: 0 8px; }.history-action { border: 0; margin-top: auto; background: none; border-top: 1px solid var(--glass-hairline); padding: 16px 4px 0; color: var(--field); text-align: left; font-size: var(--fs-sm); }.empty-history { padding: 12px; font-size: var(--fs-sm); }.chat-panel { min-width: 0; min-height: 0; display: flex; flex-direction: column; border-radius: var(--radius-lg); overflow: hidden; }.chat-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 20px 26px; border-bottom: 1px solid var(--glass-hairline); }.chat-head > div:first-child { min-width: 0; }.chat-head h2 { font-family: var(--font-display); font-size: 1.12rem; margin-top: 6px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }.chat-tools { display: flex; gap: 8px; flex-shrink: 0; }.message-list { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 26px clamp(16px,4vw,60px); scroll-behavior: smooth; }.welcome { max-width: 650px; margin: auto; padding: 32px 0; }.assistant-mark { display: grid; place-items: center; width: 44px; height: 44px; border: 1px solid var(--field); border-radius: 14px; color: var(--field); font-family: var(--font-display); font-size: 26px; margin-bottom: 20px; }.welcome h3 { font-family: var(--font-display); font-size: clamp(1.12rem,2vw,1.5rem); font-weight: 500; line-height: 1.6; }.welcome p { color: var(--slate); font-size: var(--fs-sm); margin: 12px 0 28px; line-height: 1.8; }.starter-list { display: grid; gap: 8px; }.starter-list button { border: 1px solid var(--glass-stroke); border-radius: 12px; background: var(--glass-fill); text-align: left; display: flex; justify-content: space-between; gap: 16px; color: var(--field); padding: 14px 16px; min-height: 48px; align-items: center; font-size: var(--fs-sm); }.message { margin: 0 auto 24px; width: 100%; max-width: 980px; min-width: 0; }.speaker { font-size: var(--fs-xs); color: var(--slate); margin-bottom: 8px; }.speaker time { margin-left: 12px; opacity: .8; }.user-message { text-align: right; }.user-bubble { max-width: min(85%,760px); display: inline-block; text-align: left; padding: 14px 18px; border-radius: 18px 18px 4px 18px; background: var(--field-tint); color: var(--ink); white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; font-size: var(--fs-sm); }.reply-text { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.9; font-size: var(--fs-sm); }.assistant-message { padding-right: 0; }.source-list { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }.source-chip { border: 1px solid var(--glass-hairline); border-radius: 20px; color: var(--field); padding: 6px 10px; font-size: var(--fs-xs); }.source-failed,.reply-error { color: var(--seal); }.proposal-button { margin-top: 16px; display: grid; gap: 8px; background: var(--glass-fill-strong); border: 1px solid var(--field); border-left: 4px solid var(--field); border-radius: 12px; padding: 16px; width: 100%; text-align: left; color: var(--field); }.proposal-button span,.proposal-button small { font-size: var(--fs-xs); font-weight: 400; }.proposal-button small { color: var(--slate); line-height: 1.6; }.proposal-button strong { font-size: var(--fs-sm); font-weight: 500; }.composer { padding: 16px 26px 12px; background: var(--glass-fill); border-top: 1px solid var(--glass-hairline); }.composer form { display: flex; align-items: end; gap: 12px; }.composer textarea { width: 100%; min-width: 0; resize: vertical; max-height: 160px; border: 0; background: transparent; outline: 0; color: var(--ink); line-height: 1.7; font-size: var(--fs-sm); }.composer textarea:focus-visible { outline: 1px solid var(--field); outline-offset: 4px; border-radius: 6px; }.send-button { border: 0; flex-shrink: 0; min-height: 44px; padding: 10px 16px; border-radius: 14px; color: white; background: var(--field); font-size: var(--fs-sm); }.send-button:disabled { opacity: .5; cursor: not-allowed; }.composer-hint { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 6px 12px; font-size: 10px; color: var(--slate); margin-top: 10px; }.composer-error { color: var(--seal); font-size: var(--fs-sm); line-height: 1.7; margin-bottom: 12px; }.thinking { color: var(--slate); font-size: var(--fs-sm); margin: 12px 0; }.text-button,.older-button { color: var(--field); background: none; border: 0; font-size: var(--fs-xs); padding: 8px 0; }.older-button { display: block; margin: 0 auto 24px; }.mobile-history,.mobile-new,.mobile-operations { display: none; }.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
@media(max-width:1100px) { .assistant-workspace { grid-template-columns: 190px minmax(0,1fr); } }
@media(max-width:900px) { .assistant-workspace { display: flex; height: calc(100dvh - 126px); min-height: 420px; }.conversation-rail { display: none; }.chat-panel { width: 100%; }.chat-head { padding: 14px; flex-wrap: wrap; gap: 10px; }.chat-head > div:first-child { flex: 1 1 140px; }.chat-tools { flex-wrap: wrap; gap: 6px; }.chat-tools .action-button { padding: 8px 10px; font-size: var(--fs-xs); }.mobile-history,.mobile-new,.mobile-operations { display: inline-flex; }.message-list { padding: 18px 14px; }.welcome { padding: 16px 0; }.composer { padding: 12px 14px max(12px,env(safe-area-inset-bottom)); }.composer-hint > span:first-child { display: none; }.user-message { margin-left: auto; }.assistant-message { margin-right: auto; }.send-button { padding: 10px 12px; } }
.starter-list button:hover { background: var(--field-tint); border-color: var(--field); }.assistant-workspace button:focus-visible { outline: 2px solid var(--field); outline-offset: 3px; }
@media(prefers-reduced-motion:reduce) { .message-list { scroll-behavior: auto; } }
</style>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import { useAuth } from '@/composables/useAuth'
import { ApiError } from '@/api/http'
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref(route.query.unavailable ? '暂时无法连接服务，请稍后重试。' : '')
async function submit() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    await auth.login(username.value.trim(), password.value)
    password.value = ''
    const redirect = typeof route.query.redirect === 'string' && /^\/admin(?:[/?]|$)/.test(route.query.redirect) ? route.query.redirect : '/admin'
    await router.replace(redirect)
  } catch (cause) {
    error.value = cause instanceof ApiError && ['400', '401', '429'].includes(cause.code) ? cause.message : '暂时无法登录，请检查服务后重试。'
  } finally { busy.value = false }
}
</script>

<template>
  <div class="login">
    <GlassPanel strong glow="field" padded="large" class="card">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true">溯</span>
        <div>
          <h1 class="title display">农产管理与溯源</h1>
          <p class="eyebrow">AgriTrace Console</p>
        </div>
      </div>

      <p class="lede">
        登录后管理地块、种植与环境监测。消费者扫码查询无需登录。
      </p>

      <form class="fields" @submit.prevent="submit">
        <label class="field">
          <span class="field-label">账号</span>
          <input v-model.trim="username" class="field-input" type="text" name="username" autocomplete="username" required maxlength="64" :disabled="busy" />
        </label>
        <label class="field">
          <span class="field-label">密码</span>
          <input v-model="password" class="field-input" type="password" name="password" autocomplete="current-password" required maxlength="128" :disabled="busy" />
        </label>
        <p v-if="error" class="field-error" role="alert">{{ error }}</p>
        <button class="primary" type="submit" :disabled="busy">{{ busy ? '正在登录…' : '登录' }}</button>
      </form>

      <p class="note">
        账号由管理员分配。首次在本机使用时，请查看本地初始化凭据文件，登录后在“账号与权限”修改密码。
      </p>

      <div class="links">
        <ThemeSwitcher />
      </div>
    </GlassPanel>
  </div>
</template>

<style scoped>
.login {
  flex: 1 1 auto;
  display: grid;
  place-items: center;
  padding: var(--space-6) var(--space-4);
}

.card {
  width: min(420px, 100%);
  border-radius: var(--radius-xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: var(--field);
  color: var(--mist);
  font-family: var(--font-display);
  font-size: 1.25rem;
}

.title {
  font-size: 1.25rem;
  font-weight: 600;
}

.lede {
  font-size: var(--fs-sm);
  color: var(--ink-70);
}

.fields {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: var(--fs-xs);
  color: var(--slate);
}

.field-input {
  padding: 10px var(--space-3);
  border-radius: var(--radius-sm);
  border: 1px solid var(--glass-hairline);
  background: rgba(255, 255, 255, 0.55);
}

.field-input:disabled {
  color: var(--slate);
  cursor: not-allowed;
}

.primary {
  padding: 11px var(--space-4);
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--field);
  color: var(--mist);
  font-weight: 500;
  transition: opacity var(--dur-fast) var(--ease-out);
}

.primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.note {
  font-size: var(--fs-xs);
  color: var(--slate);
  line-height: 1.6;
}

.links {
  display: flex;
  gap: var(--space-4);
  flex-wrap: wrap;
  border-top: 1px solid var(--glass-hairline);
  padding-top: var(--space-3);
}

.link {
  font-size: var(--fs-sm);
  color: var(--field);
}
</style>

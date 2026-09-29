<script setup lang="ts">
import { RouterLink } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
</script>

<template>
  <div class="login">
    <GlassPanel strong glow="field" padded="large" class="card">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true">溯</span>
        <div>
          <h1 class="title display">农产品可信溯源</h1>
          <p class="eyebrow">AgriTrace Console</p>
        </div>
      </div>

      <p class="lede">
        后台需要账号登录后才能录入批次、生成单品码与绑定 NFC 标签。
      </p>

      <div class="fields">
        <label class="field">
          <span class="field-label">账号</span>
          <input class="field-input" type="text" placeholder="尚未开放" disabled />
        </label>
        <label class="field">
          <span class="field-label">密码</span>
          <input class="field-input" type="password" placeholder="尚未开放" disabled />
        </label>
      </div>

      <button class="primary" type="button" disabled>登录</button>

      <p class="note">
        后端目前还没有登录鉴权模块（无 JWT / Spring Security），登录页先做占位。
        接入鉴权后这里会换成真实的账号校验。
      </p>

      <div class="links">
        <RouterLink to="/admin" class="link">先进入后台预览</RouterLink>
        <RouterLink to="/t/DEMO-0001" class="link">查看公众查询页</RouterLink>
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

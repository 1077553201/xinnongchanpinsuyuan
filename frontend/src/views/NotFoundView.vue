<script setup lang="ts">
import { RouterLink } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
</script>

<template>
  <div class="nf">
    <GlassPanel strong padded="large" class="card">
      <p class="code mono">404</p>
      <h1 class="title display">没有这个页面</h1>
      <p class="lede">
        链接可能已经失效，或者地址输错了。扫码查询请确认二维码里的地址以
        <span class="mono">/t/</span> 开头。
      </p>
      <div class="links">
        <RouterLink to="/admin" class="link">返回后台</RouterLink>
        <RouterLink to="/t/DEMO-0001" class="link">查看公众查询页</RouterLink>
      </div>
    </GlassPanel>
  </div>
</template>

<style scoped>
.nf {
  flex: 1 1 auto;
  display: grid;
  place-items: center;
  padding: var(--space-6) var(--space-4);
}

.card {
  width: min(440px, 100%);
  border-radius: var(--radius-xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.code {
  font-size: 3rem;
  color: var(--field);
  opacity: 0.4;
  line-height: 1;
}

.title {
  font-size: var(--fs-title);
  font-weight: 600;
}

.lede {
  font-size: var(--fs-sm);
  color: var(--ink-70);
}

.links {
  display: flex;
  gap: var(--space-4);
  border-top: 1px solid var(--glass-hairline);
  padding-top: var(--space-3);
}

.link {
  font-size: var(--fs-sm);
  color: var(--field);
}
</style>

<script setup lang="ts">
/**
 * 柔光环境层：页面背景的晨光光斑与细微纹理。
 * 纯装饰，不承载内容，因此对辅助技术隐藏；动效在系统减弱动效偏好下自动停止。
 */
</script>

<template>
  <div class="ambient" aria-hidden="true">
    <span class="orb orb-a"></span>
    <span class="orb orb-b"></span>
    <span class="orb orb-c"></span>
    <span class="texture"></span>
  </div>
</template>

<style scoped>
.ambient {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(72px);
  will-change: transform;
}

/* 冷绿光斑：田间晨雾 */
.orb-a {
  width: 66vw;
  height: 66vw;
  min-width: 420px;
  min-height: 420px;
  top: -20vh;
  left: -18vw;
  background: radial-gradient(circle at 42% 42%, var(--glow-cool), transparent 66%);
  opacity: 0.85;
  animation: drift-a 38s var(--ease-out) infinite alternate;
}

/* 暖金光斑：斜射的晨光 */
.orb-b {
  width: 52vw;
  height: 52vw;
  min-width: 320px;
  min-height: 320px;
  top: 28vh;
  right: -16vw;
  background: radial-gradient(circle at 50% 50%, var(--glow-warm), transparent 64%);
  opacity: 0.55;
  animation: drift-b 46s var(--ease-out) infinite alternate;
}

/* 底部冷光：托住内容 */
.orb-c {
  width: 74vw;
  height: 46vw;
  bottom: -24vh;
  left: 6vw;
  background: radial-gradient(circle at 50% 50%, var(--glow-pale), transparent 62%);
  opacity: 0.6;
  animation: drift-c 54s var(--ease-out) infinite alternate;
}

/* 极细纹理，给玻璃一点材质感 */
.texture {
  position: absolute;
  inset: 0;
  opacity: 0.55;
  background-image: radial-gradient(rgba(14, 33, 27, 0.03) 1px, transparent 1px);
  background-size: 3px 3px;
}

@keyframes drift-a {
  to {
    transform: translate3d(6vw, 5vh, 0) scale(1.08);
  }
}

@keyframes drift-b {
  to {
    transform: translate3d(-5vw, 7vh, 0) scale(1.12);
  }
}

@keyframes drift-c {
  to {
    transform: translate3d(4vw, -4vh, 0) scale(1.06);
  }
}

@media (prefers-reduced-motion: reduce) {
  .orb {
    animation: none;
  }
}
</style>

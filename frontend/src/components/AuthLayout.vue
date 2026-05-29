<template>
  <div class="auth-layout">
    <div class="auth-bg" aria-hidden="true">
      <div class="auth-grid-motion" />
      <div class="scanlines" />
      <div class="orb orb-1" />
      <div class="orb orb-2" />
      <div class="orb orb-3" />
      <div class="mesh" />
      <div class="starfield">
        <span v-for="n in 42" :key="n" class="star" :style="starStyle(n)" />
      </div>
    </div>
    <div class="auth-inner">
      <div class="auth-brand">
        <div class="logo-mark">
          <span class="logo-core">RAG</span>
        </div>
        <h1 class="brand-title">{{ title }}</h1>
        <p class="brand-sub">{{ subtitle }}</p>
        <div class="brand-pills">
          <span class="pill">向量检索</span>
          <span class="pill">引用可追溯</span>
          <span class="pill">权限可控</span>
        </div>
      </div>
      <div class="auth-card">
        <slot />
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '企业知识库' },
  subtitle: { type: String, default: '基于 Spring AI 的检索增强生成' },
})

function starStyle(seed) {
  const x = ((seed * 73) % 100).toFixed(2)
  const y = ((seed * 41) % 100).toFixed(2)
  const d = (0.4 + ((seed * 17) % 10) / 12).toFixed(2)
  const delay = ((seed * 29) % 5).toFixed(2)
  return {
    left: `${x}%`,
    top: `${y}%`,
    animationDuration: `${d}s`,
    animationDelay: `${delay}s`,
  }
}
</script>

<style scoped>
.auth-layout {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 20px 64px;
  overflow: hidden;
}

.auth-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.auth-grid-motion {
  position: absolute;
  inset: -20%;
  background-image:
    linear-gradient(color-mix(in srgb, var(--app-accent) 22%, transparent) 1px, transparent 1px),
    linear-gradient(90deg, color-mix(in srgb, var(--app-accent-2) 18%, transparent) 1px, transparent 1px);
  background-size: 64px 64px;
  opacity: 0.12;
  transform: rotate(-8deg);
  animation: rag-grid-pan 28s linear infinite;
}

html:not(.dark) .auth-grid-motion {
  opacity: 0.18;
  background-image:
    linear-gradient(color-mix(in srgb, var(--app-accent) 12%, transparent) 1px, transparent 1px),
    linear-gradient(90deg, color-mix(in srgb, var(--app-accent-2) 10%, transparent) 1px, transparent 1px);
}

@keyframes rag-grid-pan {
  0% {
    background-position: 0 0;
  }
  100% {
    background-position: 640px 320px;
  }
}

.scanlines {
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent 3px,
    color-mix(in srgb, var(--app-text) 4%, transparent) 4px
  );
  opacity: 0.14;
  mix-blend-mode: soft-light;
  pointer-events: none;
}

.starfield {
  position: absolute;
  inset: 0;
}
.star {
  position: absolute;
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: color-mix(in srgb, #fff 55%, var(--app-accent));
  opacity: 0.45;
  animation-name: rag-star-twinkle;
  animation-timing-function: ease-in-out;
  animation-iteration-count: infinite;
}

html:not(.dark) .star {
  background: color-mix(in srgb, var(--app-accent) 40%, #0f172a);
}

@keyframes rag-star-twinkle {
  0%,
  100% {
    opacity: 0.25;
    transform: scale(1);
  }
  50% {
    opacity: 0.85;
    transform: scale(1.35);
  }
}

.mesh {
  position: absolute;
  inset: -40%;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(closest-side at 50% 40%, black, transparent 72%);
  opacity: 0.45;
  animation: rag-shimmer 18s linear infinite;
  background-position: 0 0;
}

html:not(.dark) .mesh {
  background-image:
    linear-gradient(rgba(15, 23, 42, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, 0.05) 1px, transparent 1px);
  opacity: 0.55;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(40px);
  opacity: 0.75;
  animation: rag-bg-drift 16s ease-in-out infinite alternate;
}
.orb-1 {
  width: 320px;
  height: 320px;
  left: 8%;
  top: 12%;
  background: var(--app-glow-a);
}
.orb-2 {
  width: 260px;
  height: 260px;
  right: 6%;
  top: 18%;
  background: var(--app-glow-b);
  animation-delay: -4s;
}
.orb-3 {
  width: 200px;
  height: 200px;
  left: 42%;
  bottom: 6%;
  background: var(--app-glow-c);
  animation-delay: -7s;
}

.auth-inner {
  position: relative;
  z-index: 1;
  width: min(1040px, 100%);
  display: grid;
  grid-template-columns: 1fr 420px;
  gap: clamp(24px, 4vw, 56px);
  align-items: center;
}

@media (max-width: 880px) {
  .auth-inner {
    grid-template-columns: 1fr;
  }
  .auth-brand {
    text-align: center;
  }
  .brand-pills {
    justify-content: center;
  }
}

.auth-brand {
  color: var(--app-text);
}

.logo-mark {
  width: 72px;
  height: 72px;
  border-radius: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--app-accent), var(--app-accent-2));
  box-shadow: 0 16px 50px color-mix(in srgb, var(--app-accent) 35%, transparent);
  margin-bottom: 20px;
  animation: rag-float 5s ease-in-out infinite;
}

.logo-core {
  font-weight: 800;
  font-size: 20px;
  letter-spacing: 0.12em;
  color: #fff;
}

.brand-title {
  margin: 0 0 10px;
  font-size: clamp(1.75rem, 3vw, 2.25rem);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.brand-sub {
  margin: 0 0 22px;
  font-size: 15px;
  color: var(--app-text-muted);
  max-width: 420px;
  line-height: 1.6;
}

.brand-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pill {
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--app-border);
  background: var(--app-surface);
  backdrop-filter: blur(10px);
  color: var(--app-text-muted);
}

.auth-card {
  padding: clamp(22px, 3vw, 30px);
  border-radius: 22px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  backdrop-filter: blur(20px) saturate(150%);
  -webkit-backdrop-filter: blur(20px) saturate(150%);
  box-shadow: var(--app-shadow);
}

.auth-card :deep(.form-title) {
  margin: 0 0 6px;
  font-size: 1.25rem;
  font-weight: 700;
}
.auth-card :deep(.form-desc) {
  margin: 0 0 22px;
  font-size: 13px;
  color: var(--app-text-muted);
}
.auth-card :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--app-text-muted);
}
.auth-card :deep(.submit-btn) {
  width: 100%;
  height: 44px;
  border-radius: 12px !important;
  font-weight: 600;
  letter-spacing: 0.06em;
}
.auth-card :deep(.foot-link) {
  margin-top: 16px;
  text-align: center;
  font-size: 14px;
  color: var(--app-text-muted);
}
</style>

<template>
  <Teleport to="body">
    <Transition name="tech-overlay-fade">
      <div
        v-if="visible"
        class="tech-overlay"
        role="status"
        aria-live="polite"
        :aria-label="message"
      >
        <div class="tech-overlay__backdrop" />
        <div class="tech-overlay__panel">
          <div class="tech-overlay__grid" aria-hidden="true" />
          <div class="tech-overlay__scan" aria-hidden="true" />
          <div class="tech-overlay__ring" aria-hidden="true">
            <span />
            <span />
          </div>
          <p class="tech-overlay__msg">{{ message }}</p>
          <div class="tech-overlay__bar" aria-hidden="true">
            <span class="tech-overlay__bar-fill" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  message: { type: String, default: '处理中…' },
})
</script>

<style scoped>
.tech-overlay {
  position: fixed;
  inset: 0;
  z-index: 20000;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: auto;
}

.tech-overlay__backdrop {
  position: absolute;
  inset: 0;
  background: color-mix(in srgb, var(--app-bg, #0a0e14) 72%, transparent);
  backdrop-filter: blur(10px);
}

.tech-overlay__panel {
  position: relative;
  z-index: 1;
  min-width: min(320px, 90vw);
  max-width: 420px;
  padding: 28px 32px 26px;
  border-radius: 16px;
  border: 1px solid color-mix(in srgb, var(--app-accent, #5b8cff) 35%, var(--app-border, #2a3340));
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--app-surface, #121820) 92%, var(--app-accent, #5b8cff) 8%),
    var(--app-surface-2, #0f1419)
  );
  box-shadow:
    0 0 0 1px color-mix(in srgb, var(--app-accent, #5b8cff) 12%, transparent),
    0 24px 64px color-mix(in srgb, #000 55%, transparent);
  overflow: hidden;
}

.tech-overlay__grid {
  position: absolute;
  inset: -40%;
  background-image:
    linear-gradient(color-mix(in srgb, var(--app-accent, #5b8cff) 14%, transparent) 1px, transparent 1px),
    linear-gradient(90deg, color-mix(in srgb, var(--app-accent, #5b8cff) 14%, transparent) 1px, transparent 1px);
  background-size: 28px 28px;
  opacity: 0.35;
  animation: tech-grid-drift 18s linear infinite;
  pointer-events: none;
}

.tech-overlay__scan {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    to bottom,
    transparent 0%,
    color-mix(in srgb, var(--app-accent, #5b8cff) 22%, transparent) 48%,
    transparent 100%
  );
  background-size: 100% 220%;
  animation: tech-scan 2.4s ease-in-out infinite;
  pointer-events: none;
  mix-blend-mode: screen;
  opacity: 0.55;
}

.tech-overlay__ring {
  position: relative;
  width: 48px;
  height: 48px;
  margin: 0 auto 16px;
}

.tech-overlay__ring span {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2px solid transparent;
  border-top-color: var(--app-accent, #5b8cff);
  border-right-color: color-mix(in srgb, var(--app-accent-2, #7cf0ff) 70%, transparent);
  animation: tech-spin 1.1s linear infinite;
}

.tech-overlay__ring span:last-child {
  inset: 6px;
  border-top-color: color-mix(in srgb, var(--app-accent-2, #7cf0ff) 85%, transparent);
  animation-direction: reverse;
  animation-duration: 1.4s;
}

.tech-overlay__msg {
  margin: 0 0 18px;
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--app-text, #e8eef7);
  text-shadow: 0 0 24px color-mix(in srgb, var(--app-accent, #5b8cff) 40%, transparent);
}

.tech-overlay__bar {
  height: 3px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--app-border, #2a3340) 80%, transparent);
  overflow: hidden;
}

.tech-overlay__bar-fill {
  display: block;
  height: 100%;
  width: 40%;
  border-radius: inherit;
  background: linear-gradient(
    90deg,
    transparent,
    var(--app-accent, #5b8cff),
    var(--app-accent-2, #7cf0ff),
    transparent
  );
  animation: tech-bar 1.5s ease-in-out infinite;
}

.tech-overlay-fade-enter-active,
.tech-overlay-fade-leave-active {
  transition: opacity 0.28s ease;
}

.tech-overlay-fade-enter-active .tech-overlay__panel,
.tech-overlay-fade-leave-active .tech-overlay__panel {
  transition:
    transform 0.32s cubic-bezier(0.22, 1, 0.36, 1),
    opacity 0.28s ease;
}

.tech-overlay-fade-enter-from,
.tech-overlay-fade-leave-to {
  opacity: 0;
}

.tech-overlay-fade-enter-from .tech-overlay__panel,
.tech-overlay-fade-leave-to .tech-overlay__panel {
  transform: scale(0.94) translateY(8px);
  opacity: 0;
}

@keyframes tech-scan {
  0%,
  100% {
    background-position: 0% 0%;
  }
  50% {
    background-position: 0% 100%;
  }
}

@keyframes tech-grid-drift {
  from {
    transform: translate(0, 0);
  }
  to {
    transform: translate(28px, 28px);
  }
}

@keyframes tech-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes tech-bar {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(280%);
  }
}
</style>

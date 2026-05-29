<template>
  <div class="theme-switcher">
    <el-dropdown trigger="click" @command="onCommand">
      <el-button>
        <span class="btn-inner">
          <el-icon class="ico"><Brush /></el-icon>
          <span class="lbl">主题</span>
          <span class="cur">{{ currentLabel }}</span>
        </span>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="t in themes"
            :key="t.id"
            :command="t.id"
            :class="{ 'is-active': t.id === currentId }"
          >
            <span class="item-row">
              <span>{{ t.label }}</span>
              <el-icon v-if="t.id === currentId"><Check /></el-icon>
            </span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Brush, Check } from '@element-plus/icons-vue'
import { useTheme } from '../composables/useTheme'

const { themes, currentId, setTheme } = useTheme()

const currentLabel = computed(() => themes.find((t) => t.id === currentId.value)?.label || '')

function onCommand(id) {
  setTheme(id)
}
</script>

<style scoped>
.btn-inner {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.ico {
  font-size: 18px;
}
.lbl {
  font-weight: 600;
  letter-spacing: 0.04em;
}
.cur {
  font-size: 12px;
  opacity: 0.85;
  padding: 2px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--app-accent) 22%, transparent);
  color: var(--app-text);
}
.item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 120px;
}
:deep(.el-dropdown-menu__item.is-active) {
  color: var(--el-color-primary);
  font-weight: 600;
}
</style>

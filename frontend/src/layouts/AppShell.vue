<template>
  <el-container class="app-shell">
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside glass">
      <div class="brand-row">
        <span v-if="!collapsed" class="brand-text">RAG 工作台</span>
        <el-button class="collapse-btn" text circle @click="collapsed = !collapsed">
          <el-icon><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
        </el-button>
      </div>
      <el-menu
        :collapse="collapsed"
        :default-active="activeMenu"
        class="side-menu"
        router
        background-color="transparent"
      >
        <el-menu-item index="/">
          <el-icon><Grid /></el-icon>
          <template #title>
            <span class="menu-title">知识库</span>
            <el-tag v-if="!collapsed" size="small" round effect="plain">{{ kbCount }}</el-tag>
          </template>
        </el-menu-item>
        <el-menu-item index="/documents">
          <el-icon><Files /></el-icon>
          <template #title>文档中心</template>
        </el-menu-item>
        <el-divider class="menu-divider" />
        <el-menu-item index="/model-settings">
          <el-icon><Cpu /></el-icon>
          <template #title>模型配置</template>
        </el-menu-item>
        <el-menu-item index="/prompt-templates">
          <el-icon><ChatLineRound /></el-icon>
          <template #title>Prompt 模板</template>
        </el-menu-item>
        <el-menu-item index="/system-settings">
          <el-icon><Setting /></el-icon>
          <template #title>系统设置</template>
        </el-menu-item>
        <el-menu-item index="/system-status">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>运行状态</template>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/admin/audit">
          <el-icon><Document /></el-icon>
          <template #title>审计日志</template>
        </el-menu-item>
        <el-divider class="menu-divider" />
        <el-menu-item index="/help-support">
          <el-icon><QuestionFilled /></el-icon>
          <template #title>帮助与支持</template>
        </el-menu-item>
        <el-menu-item index="/profile">
          <el-icon><User /></el-icon>
          <template #title>个人中心</template>
        </el-menu-item>
      </el-menu>
      <div class="aside-foot">
        <el-tag v-if="!collapsed && profile?.role === 'ADMIN'" size="small" type="warning" effect="dark" round
          >管理员</el-tag
        >
        <span v-if="!collapsed" class="who">{{ profile?.username }}</span>
        <el-button v-if="!collapsed" size="small" plain round @click="logout">退出登录</el-button>
      </div>
    </el-aside>
    <el-main class="shell-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Grid,
  Document,
  Fold,
  Expand,
  Files,
  Cpu,
  ChatLineRound,
  Setting,
  DataAnalysis,
  QuestionFilled,
  User,
} from '@element-plus/icons-vue'
import http, { getProfile, setToken } from '../api/http'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const profile = computed(() => getProfile())
const isAdmin = computed(() => profile.value?.role === 'ADMIN')
const kbCount = ref(0)

const activeMenu = computed(() => {
  if (route.path.startsWith('/admin/audit')) return '/admin/audit'
  if (route.path.startsWith('/documents')) return '/documents'
  if (route.path.startsWith('/model-settings')) return '/model-settings'
  if (route.path.startsWith('/prompt-templates')) return '/prompt-templates'
  if (route.path.startsWith('/system-settings')) return '/system-settings'
  if (route.path.startsWith('/system-status')) return '/system-status'
  if (route.path.startsWith('/help-support')) return '/help-support'
  if (route.path.startsWith('/profile')) return '/profile'
  if (route.path.startsWith('/kb/')) return '/'
  return route.path || '/'
})

async function loadKbCount() {
  try {
    const { data } = await http.get('/knowledge-bases')
    kbCount.value = Array.isArray(data) ? data.length : 0
  } catch {
    kbCount.value = 0
  }
}

function logout() {
  setToken(null)
  router.push('/login')
}

onMounted(loadKbCount)
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
}
.aside {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--app-border);
  transition: width 0.2s ease;
}
.brand-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 12px 12px;
  gap: 8px;
}
.brand-text {
  font-weight: 700;
  font-size: 15px;
  letter-spacing: 0.04em;
  color: var(--app-text);
}
.collapse-btn {
  flex-shrink: 0;
}
.side-menu {
  border-right: none;
  flex: 1;
}
.side-menu :deep(.el-menu-item) {
  border-radius: 12px;
  margin: 4px 8px;
}
.menu-title {
  margin-right: 6px;
}
.menu-divider {
  margin: 8px 14px;
}
.aside-foot {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  color: var(--app-text-muted);
}
.who {
  word-break: break-all;
}
.shell-main {
  padding: 0;
  min-width: 0;
}
</style>

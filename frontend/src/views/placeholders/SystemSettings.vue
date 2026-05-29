<template>
  <div class="shell">
    <el-card class="glass">
      <div class="head">
        <div>
          <h2>系统设置</h2>
          <p class="sub">外观、缓存与系统管理入口（部分功能需要管理员权限）。</p>
        </div>
      </div>

      <div class="grid">
        <el-card class="mini" shadow="never">
          <div class="mini-head">
            <div>
              <div class="mini-title">外观主题</div>
              <div class="mini-desc">切换主题并持久化到本地</div>
            </div>
          </div>

          <div class="kv">
            <div class="row">
              <span class="k">当前主题</span>
              <span class="v">
                <el-select v-model="themeId" placeholder="选择主题" style="width: 220px">
                  <el-option v-for="t in themes" :key="t.id" :label="t.label" :value="t.id" />
                </el-select>
              </span>
            </div>

            <div class="row">
              <span class="k">模式</span>
              <span class="v">
                <el-tag effect="plain">{{ currentTheme?.dark ? '深色' : '浅色' }}</el-tag>
              </span>
            </div>
          </div>

          <div class="actions">
            <el-button type="primary" @click="applyThemeNow">应用</el-button>
            <el-button @click="resetTheme">恢复默认</el-button>
          </div>
        </el-card>

        <el-card class="mini" shadow="never">
          <div class="mini-head">
            <div>
              <div class="mini-title">缓存与本地数据</div>
              <div class="mini-desc">用于解决“页面不更新/乱码”等问题</div>
            </div>
          </div>

          <div class="kv">
            <div class="row"><span class="k">Token</span><span class="v">{{ hasToken ? '已存在' : '无' }}</span></div>
            <div class="row"><span class="k">Profile</span><span class="v">{{ hasProfile ? '已存在' : '无' }}</span></div>
            <div class="row"><span class="k">主题</span><span class="v">{{ storedTheme || '-' }}</span></div>
          </div>

          <div class="actions">
            <el-button type="danger" plain @click="clearSiteData">清除本地数据</el-button>
            <el-button @click="reload">刷新页面</el-button>
          </div>

          <div class="tip">清除后会退出登录，需要重新登录。</div>
        </el-card>

        <el-card class="mini" shadow="never">
          <div class="mini-head">
            <div>
              <div class="mini-title">当前运行时模型</div>
              <div class="mini-desc">读取后端当前实际生效配置</div>
            </div>
            <el-tag v-if="isAdmin" size="small" type="success">LIVE</el-tag>
            <el-tag v-else size="small" type="info">管理员可见</el-tag>
          </div>

          <div v-if="isAdmin && runtimeSummary" class="kv compact">
            <div class="runtime-toolbar">
              <el-tag size="small" :type="runtimeSummary.chatBackend === 'openai' ? 'success' : 'warning'">
                {{ runtimeSummary.chatBackend }}
              </el-tag>
              <el-tag size="small" :type="runtimeSummary.retrievalMode === 'FTS-only' ? 'info' : 'success'">
                {{ runtimeSummary.retrievalMode }}
              </el-tag>
              <el-tag size="small" :type="runtimeSummary.imageCaptionEnabled ? 'success' : 'info'">
                图像 {{ runtimeSummary.imageCaptionEnabled ? 'ON' : 'OFF' }}
              </el-tag>
              <el-tag size="small" :type="runtimeSummary.videoCaptionEnabled ? 'success' : 'info'">
                视频 {{ runtimeSummary.videoCaptionEnabled ? 'ON' : 'OFF' }}
              </el-tag>
            </div>
            <div class="row"><span class="k">Chat</span><span class="v mono">{{ runtimeSummary.chatModel }}</span></div>
            <div class="row runtime-url-row">
              <span class="k">Base URL</span>
              <span class="v mono">{{ runtimeSummary.baseUrl }}</span>
              <el-button text size="small" @click="copyBaseUrl">复制</el-button>
            </div>
            <div class="row"><span class="k">Vision</span><span class="v mono">{{ runtimeSummary.openAiVisionModel }}</span></div>
            <div class="row"><span class="k">Provider</span><span class="v">{{ runtimeSummary.llmProvider }}</span></div>
          </div>

          <div v-else class="tip">{{ isAdmin ? '正在读取运行时配置…' : '登录管理员账号后可直接查看当前实际生效的模型与网关地址。' }}</div>

          <div class="actions">
            <el-button type="primary" plain :disabled="!isAdmin" @click="go('/system-status')">查看完整摘要</el-button>
            <el-button :disabled="!isAdmin" :loading="runtimeLoading" @click="loadRuntimeSummary">刷新摘要</el-button>
          </div>
        </el-card>

        <el-card class="mini full" shadow="never">
          <div class="mini-head">
            <div>
              <div class="mini-title">系统管理入口</div>
              <div class="mini-desc">管理员可查看系统状态、模型配置与工单处理</div>
            </div>
            <el-tag v-if="isAdmin" type="success" size="small">ADMIN</el-tag>
            <el-tag v-else type="warning" size="small">需要管理员</el-tag>
          </div>

          <div class="entry">
            <el-button type="primary" :disabled="!isAdmin" @click="go('/system-status')">运行状态</el-button>
            <el-button type="primary" :disabled="!isAdmin" @click="go('/model-settings')">模型配置</el-button>
            <el-button type="primary" :disabled="!isAdmin" @click="go('/prompt-templates')">Prompt 模板</el-button>
            <el-button @click="go('/help-support')">帮助与支持</el-button>
            <el-button @click="go('/profile')">个人中心</el-button>
          </div>

          <div class="note">
            非管理员也可以正常使用知识库与问答功能；系统级管理（状态/配置/工单处理）在后端做了权限加固。
          </div>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { THEME_KEY, themes, applyTheme, getStoredThemeId } from '../../composables/useTheme'
import http, { getProfile, getToken, setToken } from '../../api/http'

const router = useRouter()

const isAdmin = computed(() => (getProfile()?.role || '') === 'ADMIN')
const runtimeSummary = ref(null)
const runtimeLoading = ref(false)

const themeId = ref(getStoredThemeId())
const storedTheme = computed(() => {
  try {
    return localStorage.getItem(THEME_KEY)
  } catch {
    return null
  }
})

const currentTheme = computed(() => themes.find((t) => t.id === themeId.value) || themes[0])

const hasToken = computed(() => !!getToken())
const hasProfile = computed(() => {
  try {
    return !!localStorage.getItem('rag_profile')
  } catch {
    return false
  }
})

function go(path) {
  router.push(path)
}

function applyThemeNow() {
  applyTheme(themeId.value)
  ElMessage.success('主题已应用')
}

function resetTheme() {
  themeId.value = 'aurora'
  applyThemeNow()
}

function reload() {
  window.location.reload()
}

async function copyBaseUrl() {
  const url = runtimeSummary.value?.baseUrl
  if (!url) return
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('Base URL 已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

async function clearSiteData() {
  try {
    await ElMessageBox.confirm('确认清除本地数据并退出登录？', '提示', {
      type: 'warning',
      confirmButtonText: '清除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    localStorage.removeItem('rag_profile')
    localStorage.removeItem('rag_token')
    localStorage.removeItem(THEME_KEY)
  } catch {
    // ignore
  }

  setToken(null)
  ElMessage.success('已清除，本页即将刷新')
  setTimeout(() => window.location.reload(), 300)
}

async function loadRuntimeSummary() {
  if (!isAdmin.value) return
  runtimeLoading.value = true
  try {
    const { data } = await http.get('/system/runtime-summary')
    runtimeSummary.value = data
  } catch {
    runtimeSummary.value = null
  } finally {
    runtimeLoading.value = false
  }
}

onMounted(() => {
  loadRuntimeSummary()
})

watch(
  () => themeId.value,
  () => {
    // 预览效果：选择即应用
    applyTheme(themeId.value)
  }
)
</script>

<style scoped>
.shell {
  padding: 24px;
}
.glass {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
}

.head {
  margin-bottom: 12px;
}

h2 {
  margin: 0;
}

.sub {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.mini {
  background: var(--app-surface-2);
  border-radius: 16px;
  border: 1px solid var(--app-border);
}

.full {
  grid-column: 1 / -1;
}

.mini-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 12px 0;
}

.mini-title {
  font-weight: 700;
  margin-bottom: 4px;
}

.mini-desc {
  color: var(--app-text-muted);
  font-size: 12px;
}

.kv {
  padding: 12px;
}

.kv.compact .row {
  margin: 8px 0;
}

.runtime-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.runtime-url-row {
  align-items: flex-start;
}

.mono {
  font-family: 'Cascadia Code', 'JetBrains Mono', monospace;
  font-size: 12px;
}

.row {
  display: flex;
  gap: 12px;
  margin: 10px 0;
  align-items: center;
}

.k {
  width: 96px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.v {
  flex: 1;
  word-break: break-word;
}

.actions {
  padding: 0 12px 12px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.tip {
  padding: 0 12px 12px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.entry {
  padding: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.note {
  padding: 0 12px 12px;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .full {
    grid-column: auto;
  }
}
</style>
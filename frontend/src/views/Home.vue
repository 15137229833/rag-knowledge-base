<template>
  <div class="shell">
    <header class="top glass">
      <div class="top-left">
        <div class="badge-dot" />
        <div class="titles">
          <h1>知识库工作台</h1>
          <p>文档向量化 · 智能问答 · 协作与审计一体化</p>
        </div>
      </div>
      <div class="top-right">
        <el-button round type="danger" plain @click="logout">退出</el-button>
      </div>
    </header>

    <section class="hero">
      <div class="hero-text">
        <h2>从这里开始</h2>
        <p>创建独立知识空间，上传业务文档，即刻获得带引用引证的回答。</p>
      </div>
      <el-button type="primary" size="large" class="cta" round @click="dialogVisible = true">
        <el-icon class="cta-ico"><Plus /></el-icon>
        新建知识库
      </el-button>
    </section>

    <div class="toolbar glass">
      <el-input
        v-model="kbSearch"
        clearable
        placeholder="搜索名称 / 描述…"
        class="kb-search"
      />
      <el-select v-model="roleFilter" placeholder="按权限过滤" clearable class="role-filter">
        <el-option label="全部权限" value="" />
        <el-option label="所有者" value="OWNER" />
        <el-option label="可编辑" value="WRITE" />
        <el-option label="只读" value="READ" />
      </el-select>
      <span v-if="list.length" class="toolbar-hint">共 {{ list.length }} 个 · 当前显示 {{ filteredList.length }} 个</span>
    </div>

    <el-row :gutter="20" class="grid">
      <el-col v-for="(k, i) in filteredList" :key="k.id" :xs="24" :sm="12" :md="8">
        <div class="kb-tile glass-hover" :style="{ '--stagger': `${i * 55}ms` }" @click="$router.push('/kb/' + k.id)">
          <div class="tile-head">
            <span class="tile-icon">
              <el-icon><FolderOpened /></el-icon>
            </span>
            <el-tag size="small" round effect="plain">{{ roleLabel(k.role) }}</el-tag>
          </div>
          <h3 class="tile-title">{{ k.name }}</h3>
          <p class="tile-desc">{{ k.description || '暂无描述，可进入详情页补充。' }}</p>
          <div class="tile-stats">
            <span class="stat-chip">
              <el-icon><Document /></el-icon>
              {{ k.documentCount ?? 0 }} 文档
            </span>
            <el-tooltip :content="formatKbTime(k.lastActivityAt)" placement="top">
              <span class="stat-chip muted">最近动态</span>
            </el-tooltip>
          </div>
          <div class="tile-foot">
            <span class="hint">点击进入</span>
            <el-icon class="arrow"><Right /></el-icon>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-empty v-if="!list.length" description="暂无知识库，点击上方按钮创建一个" class="empty" />
    <el-empty
      v-else-if="!filteredList.length"
      description="没有匹配当前筛选条件的知识库"
      class="empty"
    />

    <el-dialog v-model="dialogVisible" title="新建知识库" width="480px" class="kb-dialog" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="例如：产品白皮书库" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="可选，便于成员理解用途" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createKb">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import http, { getToken, setToken } from '../api/http'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, FolderOpened, Right, Document } from '@element-plus/icons-vue'

const router = useRouter()
const list = ref([])
const kbSearch = ref('')
const roleFilter = ref('')
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({ name: '', description: '' })

const filteredList = computed(() => {
  let arr = list.value
  const q = kbSearch.value.trim().toLowerCase()
  if (q) {
    arr = arr.filter(
      (k) =>
        (k.name || '').toLowerCase().includes(q) || (k.description || '').toLowerCase().includes(q),
    )
  }
  if (roleFilter.value) {
    arr = arr.filter((k) => k.role === roleFilter.value)
  }
  return arr
})

function roleLabel(role) {
  if (role === 'OWNER') return '所有者'
  if (role === 'WRITE') return '可编辑'
  if (role === 'READ') return '只读'
  return role || '-'
}

function formatKbTime(iso) {
  if (!iso) return '暂无文档活动（例如尚未上传）'
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return String(iso)
  }
}

async function load() {
  const [{ data: kbs }, { data: user }] = await Promise.all([http.get('/knowledge-bases'), http.get('/users/me')])
  list.value = kbs
  setToken(getToken(), user)
}

async function createKb() {
  saving.value = true
  try {
    await http.post('/knowledge-bases', { name: form.name, description: form.description })
    ElMessage.success('已创建')
    dialogVisible.value = false
    form.name = ''
    form.description = ''
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

function logout() {
  setToken(null)
  router.push('/login')
}

onMounted(() => {
  load().catch((e) => ElMessage.error(e.message))
})
</script>

<style scoped>
.shell {
  max-width: 1180px;
  margin: 0 auto;
  padding: 28px 22px 48px;
  animation: rag-enter 0.6s ease-out both;
}

@keyframes rag-enter {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.glass {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  backdrop-filter: blur(18px);
  border-radius: 18px;
  box-shadow: var(--app-shadow);
}

.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 18px 20px;
  margin-bottom: 24px;
}

.top-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.badge-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--app-accent), var(--app-accent-2));
  box-shadow: 0 0 0 6px color-mix(in srgb, var(--app-accent) 25%, transparent);
}

.titles h1 {
  margin: 0;
  font-size: 1.35rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--app-text);
}

.titles p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--app-text-muted);
}

.top-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: 18px;
}

.kb-search {
  flex: 1;
  min-width: 200px;
  max-width: 360px;
}

.role-filter {
  width: 160px;
}

.toolbar-hint {
  font-size: 12px;
  color: var(--app-text-muted);
  margin-left: auto;
}

.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 22px;
}

.hero-text h2 {
  margin: 0 0 6px;
  font-size: 1.45rem;
  font-weight: 700;
  color: var(--app-text);
}

.hero-text p {
  margin: 0;
  max-width: 560px;
  color: var(--app-text-muted);
  line-height: 1.6;
  font-size: 14px;
}

.cta {
  font-weight: 600;
  letter-spacing: 0.04em;
  padding: 0 22px;
  height: 44px;
  border: none;
  background: linear-gradient(120deg, var(--app-accent), var(--app-accent-2)) !important;
  box-shadow: 0 14px 40px color-mix(in srgb, var(--app-accent) 35%, transparent);
}

.cta-ico {
  margin-right: 6px;
}

.grid {
  margin-top: 8px;
}

.kb-tile {
  position: relative;
  padding: 20px;
  border-radius: 20px;
  border: 1px solid var(--app-border);
  background: var(--app-surface);
  margin-bottom: 20px;
  cursor: pointer;
  min-height: 188px;
  display: flex;
  flex-direction: column;
  animation: tile-in 0.55s ease backwards;
  animation-delay: var(--stagger, 0ms);
  transition:
    transform 0.25s ease,
    box-shadow 0.25s ease,
    border-color 0.25s ease;
}

@keyframes tile-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.glass-hover:hover {
  transform: translateY(-4px);
  box-shadow: 0 22px 60px color-mix(in srgb, var(--app-accent) 18%, rgba(0, 0, 0, 0.25));
  border-color: color-mix(in srgb, var(--app-accent) 45%, var(--app-border));
}

.tile-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.tile-icon {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--app-accent) 18%, transparent);
  color: var(--app-accent);
  font-size: 20px;
}

.tile-title {
  margin: 0 0 8px;
  font-size: 1.05rem;
  font-weight: 650;
  color: var(--app-text);
}

.tile-desc {
  margin: 0;
  flex: 1;
  font-size: 13px;
  color: var(--app-text-muted);
  line-height: 1.55;
  min-height: 42px;
}

.tile-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0 4px;
}

.stat-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--app-border);
  background: color-mix(in srgb, var(--app-surface-2) 88%, transparent);
  color: var(--app-text);
}
.stat-chip.muted {
  color: var(--app-text-muted);
}

.tile-foot {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--app-text-muted);
}

.hint {
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-size: 11px;
}

.arrow {
  transition: transform 0.2s ease;
}

.kb-tile:hover .arrow {
  transform: translateX(4px);
  color: var(--app-accent);
}

.empty {
  margin-top: 32px;
}
</style>

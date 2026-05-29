<template>
  <div class="shell">
    <TechLoadingOverlay :visible="loading" message="正在同步跨库文档列表…" />
    <header class="top glass">
      <div class="titles">
        <h1>文档中心</h1>
        <p>跨知识库统一管理文档（批量操作 + 状态图 + 标签筛选联动）</p>
      </div>
      <el-button round @click="$router.push('/')">返回知识库</el-button>
    </header>

    <el-card class="glass panel" shadow="never">
      <div class="status-cards">
        <div class="sc"><span>全部</span><b>{{ rows.length }}</b></div>
        <div class="sc"><span>可用</span><b>{{ countReady }}</b></div>
        <div class="sc"><span>处理中</span><b>{{ countRunning }}</b></div>
        <div class="sc"><span>失败</span><b>{{ countFailed }}</b></div>
      </div>
      <div ref="chartRef" class="chart" />

      <div class="toolbar">
        <el-input v-model="q" clearable placeholder="按文件名搜索" class="f-item" />
        <el-select v-model="status" clearable placeholder="状态" class="f-item">
          <el-option label="可用" value="READY" />
          <el-option label="解析中" value="PROCESSING" />
          <el-option label="待处理" value="PENDING" />
          <el-option label="失败" value="FAILED" />
        </el-select>
        <el-select v-model="ext" clearable placeholder="类型" class="f-item">
          <el-option label="PDF" value=".pdf" />
          <el-option label="Word" value=".docx" />
          <el-option label="Markdown" value=".md" />
          <el-option label="文本" value=".txt" />
        </el-select>
        <el-select v-model="tag" clearable filterable placeholder="标签筛选" class="f-item">
          <el-option v-for="t in allTags" :key="t" :label="t" :value="t" />
        </el-select>
        <el-button type="primary" @click="load">刷新</el-button>
      </div>

      <div class="batch" v-if="selected.length">
        <el-button type="danger" size="small" @click="batchDelete">批量删除（{{ selected.length }}）</el-button>
        <el-button size="small" @click="batchSetTag">批量打标签</el-button>
      </div>

      <el-table :data="filtered" v-loading="loading" stripe @selection-change="selected = $event">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="knowledgeBaseName" label="知识库" width="180" />
        <el-table-column prop="filename" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column label="标签" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="(t, i) in row.tags || []" :key="i" size="small" effect="plain" style="margin-right: 4px">{{ t }}</el-tag>
            <el-button link size="small" @click="editTag(row)">编辑</el-button>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'READY' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contentType" label="类型" width="180" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push('/kb/' + row.knowledgeBaseId)">进入知识库</el-button>
            <el-button link v-if="canPreview(row)" @click="preview(row)">预览</el-button>
            <el-button link type="danger" @click="removeOne(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import TechLoadingOverlay from '../components/TechLoadingOverlay.vue'

const loading = ref(false)
const rows = ref([])
const selected = ref([])
const q = ref('')
const status = ref('')
const ext = ref('')
const tag = ref('')
const chartRef = ref(null)
let chart = null

const filtered = computed(() => {
  const kw = q.value.trim().toLowerCase()
  return rows.value.filter((r) => {
    if (kw && !(r.filename || '').toLowerCase().includes(kw)) return false
    if (status.value && r.status !== status.value) return false
    if (ext.value && !(r.filename || '').toLowerCase().endsWith(ext.value)) return false
    if (tag.value && !(r.tags || []).includes(tag.value)) return false
    return true
  })
})

const allTags = computed(() => {
  const set = new Set()
  rows.value.forEach((r) => (r.tags || []).forEach((t) => set.add(t)))
  return Array.from(set)
})
const countReady = computed(() => rows.value.filter((x) => x.status === 'READY').length)
const countRunning = computed(() => rows.value.filter((x) => x.status === 'PENDING' || x.status === 'PROCESSING').length)
const countFailed = computed(() => rows.value.filter((x) => x.status === 'FAILED').length)

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['60%', '82%'],
      label: { show: false },
      data: [
        { name: '可用', value: countReady.value },
        { name: '处理中', value: countRunning.value },
        { name: '失败', value: countFailed.value },
      ],
    }],
  })
}

function canPreview(row) {
  const n = (row.filename || '').toLowerCase()
  return n.endsWith('.pdf') || n.endsWith('.md') || n.endsWith('.txt') || n.endsWith('.markdown')
}

function preview(row) {
  window.open(`/api/v1/documents/${row.id}/file`, '_blank')
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/documents', {
      params: {
        keyword: q.value.trim() || undefined,
        status: status.value || undefined,
        ext: ext.value || undefined,
        tag: tag.value || undefined,
      },
    })
    rows.value = data || []
    await nextTick()
    renderChart()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function removeOne(row) {
  await ElMessageBox.confirm(`确定删除 ${row.filename}？`, '提示')
  await http.delete(`/documents/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

async function batchDelete() {
  await ElMessageBox.confirm(`确定批量删除 ${selected.value.length} 个文档？`, '提示')
  await http.post('/documents/batch-delete', { ids: selected.value.map((x) => x.id) })
  ElMessage.success('批量删除成功')
  selected.value = []
  await load()
}

async function editTag(row) {
  const old = (row.tags || []).join(', ')
  const txt = await ElMessageBox.prompt('逗号分隔标签', '编辑标签', { inputValue: old })
    .then((r) => r.value || '')
    .catch(() => null)
  if (txt == null) return
  const tags = txt.split(',').map((x) => x.trim()).filter(Boolean)
  await http.patch(`/documents/${row.id}/tags`, { tags })
  ElMessage.success('标签已更新')
  await load()
}

async function batchSetTag() {
  const txt = await ElMessageBox.prompt('为选中文档设置统一标签（逗号分隔）', '批量打标签')
    .then((r) => r.value || '')
    .catch(() => null)
  if (txt == null) return
  const tags = txt.split(',').map((x) => x.trim()).filter(Boolean)
  for (const d of selected.value) {
    await http.patch(`/documents/${d.id}/tags`, { tags })
  }
  ElMessage.success('批量标签更新完成')
  await load()
}

onMounted(load)
</script>

<style scoped>
.shell { max-width: 1360px; margin: 0 auto; padding: 24px 20px 36px; }
.glass { background: var(--app-surface); border: 1px solid var(--app-border); border-radius: 16px; }
.top { display:flex; align-items:center; justify-content:space-between; gap: 12px; padding: 16px 18px; margin-bottom: 16px; }
.titles h1 { margin:0; font-size: 1.25rem; color: var(--app-text); }
.titles p { margin:4px 0 0; color: var(--app-text-muted); font-size: 13px; }
.status-cards { display:grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 12px; }
.sc { border: 1px solid var(--app-border); border-radius: 12px; background: var(--app-surface-2); padding: 10px; }
.sc span { font-size: 12px; color: var(--app-text-muted); display: block; }
.sc b { font-size: 20px; color: var(--app-text); }
.chart { height: 180px; margin-bottom: 8px; }
.toolbar { display:flex; flex-wrap:wrap; gap: 10px; margin-bottom: 12px; }
.f-item { width: 220px; }
.batch { margin-bottom: 8px; display: flex; gap: 8px; }
</style>
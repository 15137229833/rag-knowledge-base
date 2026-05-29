<template>
  <div class="shell">
    <header class="top glass">
      <div class="titles">
        <h1>知识图谱</h1>
        <p v-if="kb">{{ kb.name }} · 文档共现（基于问答引用）</p>
        <p v-else>加载中…</p>
      </div>
      <div class="actions">
        <el-input
          v-model="searchQ"
          clearable
          size="small"
          placeholder="按文档名筛选子图"
          class="search"
          @keyup.enter="applySearch"
        />
        <el-button size="small" round @click="applySearch">筛选</el-button>
        <el-button size="small" round @click="loadFull">全图</el-button>
        <el-button round @click="reload" :loading="loading">刷新</el-button>
        <el-button round type="primary" @click="goKb">返回知识库</el-button>
      </div>
    </header>

    <el-card class="glass panel" shadow="never" v-loading="loading">
      <div ref="chartRef" class="chart" />
      <p class="hint">
        边表示同一轮回答中多个文档被同时引用；可在个人中心调整问答偏好后继续使用智能问答以积累引用关系。
      </p>
      <el-empty v-if="!loading && !graphNodes.length" description="暂无共现边（需要多条带多文档引用的问答记录）" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const route = useRoute()
const router = useRouter()
const kbId = computed(() => route.params.id)

const loading = ref(false)
const kb = ref(null)
const searchQ = ref('')
const graphNodes = ref([])
const graphLinks = ref([])
const chartRef = ref(null)
let chart = null

function goKb() {
  router.push(`/kb/${kbId.value}`)
}

async function loadKb() {
  const { data } = await http.get(`/knowledge-bases/${kbId.value}`)
  kb.value = data
}

function mapGraph(visual) {
  const nodes = visual?.nodes || []
  const edges = visual?.edges || []
  const degrees = {}
  edges.forEach((e) => {
    degrees[e.fromEntityId] = (degrees[e.fromEntityId] || 0) + 1
    degrees[e.toEntityId] = (degrees[e.toEntityId] || 0) + 1
  })
  graphNodes.value = nodes.map((n) => ({
    id: n.id,
    name: n.name || n.id,
    symbolSize: 22 + Math.min(36, (degrees[n.id] || 0) * 5),
    category: 0,
    value: degrees[n.id] || 0,
  }))
  graphLinks.value = edges.map((e) => ({
    source: e.fromEntityId,
    target: e.toEntityId,
    value: e.properties?.weight ?? 1,
    lineStyle: { width: 1 + Math.min(4, (e.properties?.weight || 1) / 2) },
  }))
}

async function loadFull() {
  searchQ.value = ''
  const { data } = await http.get(`/kb/${kbId.value}/graph/visualization`)
  mapGraph(data)
  await nextTick()
  render()
}

async function applySearch() {
  const q = searchQ.value.trim()
  if (!q) {
    await loadFull()
    return
  }
  const { data } = await http.get(`/kb/${kbId.value}/graph/search`, { params: { q } })
  const path = data || {}
  mapGraph({ nodes: path.entities || [], edges: path.relations || [] })
  await nextTick()
  render()
}

function render() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  if (!graphNodes.value.length) {
    chart.clear()
    return
  }
  chart.setOption({
    tooltip: {},
    legend: { data: ['文档'], bottom: 0 },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        label: { show: true, position: 'right', fontSize: 11 },
        force: {
          repulsion: 220,
          edgeLength: [60, 140],
          gravity: 0.08,
        },
        categories: [{ name: '文档' }],
        data: graphNodes.value,
        links: graphLinks.value,
        lineStyle: { color: 'source', curveness: 0.12, opacity: 0.65 },
        emphasis: { focus: 'adjacency', lineStyle: { width: 3 } },
      },
    ],
  })
}

function onResize() {
  chart?.resize()
}

async function reload() {
  loading.value = true
  try {
    await loadKb()
    if (searchQ.value.trim()) await applySearch()
    else await loadFull()
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

watch(kbId, () => reload())

onMounted(() => {
  window.addEventListener('resize', onResize)
  reload()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.shell {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}
.glass {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
}
.top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  border-radius: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.titles h1 {
  margin: 0;
  font-size: 22px;
}
.titles p {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
}
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.search {
  width: 200px;
}
.panel {
  border-radius: 16px;
}
.chart {
  height: min(72vh, 640px);
  width: 100%;
}
.hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--app-text-muted);
  line-height: 1.5;
}
</style>

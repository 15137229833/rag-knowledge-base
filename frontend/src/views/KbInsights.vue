<template>
  <div class="shell">
    <header class="top glass">
      <div class="titles">
        <h1>数据洞察</h1>
        <p v-if="kb">{{ kb.name }} · 概览与引用热度</p>
        <p v-else>加载中…</p>
      </div>
      <div class="actions">
        <el-button round @click="reload" :loading="loading">刷新</el-button>
        <el-button round type="primary" @click="goKb">返回知识库</el-button>
      </div>
    </header>

    <el-row :gutter="12" class="row">
      <el-col :xs="24" :md="6" v-for="c in overviewCards" :key="c.key">
        <el-card class="stat glass" shadow="never">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-val">{{ c.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="glass panel" shadow="never" v-loading="loading">
      <template #header>
        <span>热门文档（按问答引用次数）</span>
      </template>
      <div ref="hotChartRef" class="chart" />
      <el-table v-if="hotDocs.length" :data="hotDocs" size="small" stripe class="mt">
        <el-table-column prop="fileName" label="文件" min-width="220" show-overflow-tooltip />
        <el-table-column prop="referenceCount" label="引用次数" width="120" />
      </el-table>
      <el-empty v-else description="暂无引用数据（需先有带引用的问答记录）" />
    </el-card>

    <el-card class="glass panel" shadow="never" v-loading="loading">
      <template #header>
        <div class="panel-head">
          <span>成员活跃度（按问答条数）</span>
          <div class="range">
            <el-date-picker
              v-model="activityRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始"
              end-placeholder="结束"
              value-format="YYYY-MM-DD"
              size="small"
              @change="loadActivity"
            />
          </div>
        </div>
      </template>
      <el-table :data="userActivity" size="small" stripe>
        <el-table-column label="用户 ID" min-width="280">
          <template #default="{ row }">
            <code class="uid">{{ row.userId }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="questionCount" label="提问数" width="120" />
        <el-table-column prop="activeDays" label="活跃天（简化）" width="140" />
      </el-table>
      <el-empty v-if="!userActivity.length && !loading" description="该时段内无问答记录" />
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
const overview = ref(null)
const hotDocs = ref([])
const userActivity = ref([])
const hotChartRef = ref(null)
let hotChart = null

const end = new Date()
const start = new Date()
start.setDate(start.getDate() - 29)
function pad(d) {
  const x = new Date(d)
  const y = x.getFullYear()
  const m = String(x.getMonth() + 1).padStart(2, '0')
  const day = String(x.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}
const activityRange = ref([pad(start), pad(end)])

const overviewCards = computed(() => {
  const o = overview.value
  if (!o) {
    return [
      { key: 'd', label: '文档总数', value: '—' },
      { key: 'r', label: '可用文档', value: '—' },
      { key: 'q', label: '累计提问', value: '—' },
      { key: 'w', label: '近 7 日提问', value: '—' },
    ]
  }
  return [
    { key: 'd', label: '文档总数', value: o.totalDocuments },
    { key: 'r', label: '可用文档', value: o.ingestedDocuments },
    { key: 'q', label: '累计提问', value: o.totalQuestions },
    { key: 'w', label: '近 7 日提问', value: o.recentQuestions },
  ]
})

function goKb() {
  router.push(`/kb/${kbId.value}`)
}

async function loadKb() {
  const { data } = await http.get(`/knowledge-bases/${kbId.value}`)
  kb.value = data
}

async function loadOverview() {
  const { data } = await http.get(`/kb/${kbId.value}/analytics/overview`)
  overview.value = data
}

async function loadHot() {
  const { data } = await http.get(`/kb/${kbId.value}/analytics/hot-documents`, { params: { limit: 15 } })
  hotDocs.value = data || []
  await nextTick()
  renderHotChart()
}

async function loadActivity() {
  const range = activityRange.value
  if (!range || range.length !== 2) return
  const [s, e] = range
  const { data } = await http.get(`/kb/${kbId.value}/analytics/user-activity`, {
    params: { start: s, end: e },
  })
  userActivity.value = data || []
}

function renderHotChart() {
  if (!hotChartRef.value) return
  if (!hotChart) hotChart = echarts.init(hotChartRef.value)
  const rows = hotDocs.value.slice(0, 12)
  hotChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '12%', right: '4%', bottom: '18%', top: '8%' },
    xAxis: {
      type: 'category',
      data: rows.map((r) => (r.fileName || r.title || '').slice(0, 14) + ((r.fileName || '').length > 14 ? '…' : '')),
      axisLabel: { rotate: 35, fontSize: 10 },
    },
    yAxis: { type: 'value', name: '引用' },
    series: [{ type: 'bar', data: rows.map((r) => r.referenceCount), itemStyle: { borderRadius: [4, 4, 0, 0] } }],
  })
}

async function reload() {
  loading.value = true
  try {
    await loadKb()
    await Promise.all([loadOverview(), loadHot(), loadActivity()])
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

watch(kbId, () => reload())

function onResize() {
  hotChart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', onResize)
  reload()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  hotChart?.dispose()
  hotChart = null
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
  flex-shrink: 0;
}
.row {
  margin-bottom: 12px;
}
.stat {
  border-radius: 14px;
  margin-bottom: 12px;
}
.stat-label {
  font-size: 12px;
  color: var(--app-text-muted);
}
.stat-val {
  font-size: 24px;
  font-weight: 700;
  margin-top: 6px;
}
.panel {
  border-radius: 16px;
  margin-bottom: 16px;
}
.chart {
  height: 260px;
  width: 100%;
}
.mt {
  margin-top: 12px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.uid {
  font-size: 12px;
  word-break: break-all;
}
</style>

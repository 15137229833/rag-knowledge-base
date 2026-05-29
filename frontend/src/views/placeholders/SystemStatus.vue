<template>
  <div class="shell">
    <TechLoadingOverlay :visible="loading" message="正在同步系统运行状态…" />

    <el-card class="glass">
      <div class="head">
        <div>
          <h2>系统运行状态</h2>
          <p class="sub">健康检查 + 24 小时调用统计（管理员）。</p>
        </div>
        <el-button v-if="isAdmin" :loading="loading" type="primary" @click="load">刷新</el-button>
      </div>

      <div v-if="!isAdmin" class="perm-block">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="需要管理员权限"
          description="当前账号不具备查看系统运行状态的权限（后端接口已加固）。"
        />
        <div class="perm-actions">
          <el-tag size="small" type="info">当前角色：{{ profileRole }}</el-tag>
          <el-button type="primary" @click="go('/profile')">查看个人中心</el-button>
        </div>
      </div>

      <div v-else>
        <div v-if="errorMessage" class="error-wrap">
          <el-alert type="error" :closable="false" show-icon :title="errorMessage" />
        </div>

        <div class="cards" v-if="data">
          <div class="c"><span>用户数</span><b>{{ data.totalUsers }}</b></div>
          <div class="c"><span>知识库</span><b>{{ data.totalKnowledgeBases }}</b></div>
          <div class="c"><span>文档数</span><b>{{ data.totalDocuments }}</b></div>
          <div class="c"><span>Prompt 模板</span><b>{{ data.totalPrompts }}</b></div>
          <div class="c"><span>24h Chat</span><b>{{ data.chatCalls24h }}</b></div>
          <div class="c"><span>24h 上传</span><b>{{ data.uploadCalls24h }}</b></div>
          <div class="c"><span>24h 错误</span><b>{{ data.errorCalls24h }}</b></div>
        </div>

        <div v-if="runtimeSummary" class="runtime-block">
          <div class="runtime-head">
            <h3>当前运行时模型</h3>
            <p class="runtime-desc">直接读取后端当前生效配置，避免被本地文件或环境变量覆盖关系误导。</p>
          </div>
          <div class="runtime-grid">
            <div class="runtime-item"><span>LLM Provider</span><b>{{ runtimeSummary.llmProvider }}</b></div>
            <div class="runtime-item"><span>Chat Backend</span><b>{{ runtimeSummary.chatBackend }}</b></div>
            <div class="runtime-item"><span>Chat Model</span><b>{{ runtimeSummary.chatModel }}</b></div>
            <div class="runtime-item wide"><span>Base URL</span><b>{{ runtimeSummary.baseUrl }}</b></div>
            <div class="runtime-item"><span>Vision Provider</span><b>{{ runtimeSummary.visionProvider }}</b></div>
            <div class="runtime-item"><span>Vision Model</span><b>{{ runtimeSummary.openAiVisionModel }}</b></div>
            <div class="runtime-item"><span>Retrieval</span><b>{{ runtimeSummary.retrievalMode }}</b></div>
            <div class="runtime-item"><span>Chunk Table</span><b>{{ runtimeSummary.chunkTable }}</b></div>
          </div>
        </div>

        <div v-if="data" class="viz-block">
          <div class="viz-head">
            <h3>向量空间示意（2D）</h3>
            <p class="viz-desc">
              基于统计指标生成的<strong>模拟</strong>散点分布，用于展示「知识点簇」与密度观感，并非真实向量降维结果。
            </p>
          </div>
          <div ref="vectorScatterRef" class="vector-scatter" />
        </div>

        <el-table v-if="data" :data="data.services || []" stripe style="margin-top: 14px">
          <el-table-column prop="name" label="服务" width="190" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.up ? 'success' : 'danger'">{{ row.up ? 'UP' : 'DOWN' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="详情" />
        </el-table>

        <div class="meta" v-if="data">
          <el-tag round effect="plain">服务器时间：{{ formatInstant(data.serverTime) }}</el-tag>
          <el-tag round effect="plain">运行时长：{{ data.uptimeSeconds }} 秒</el-tag>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import http, { getProfile } from '../../api/http'
import TechLoadingOverlay from '../../components/TechLoadingOverlay.vue'

const router = useRouter()

const loading = ref(false)
const data = ref(null)
const runtimeSummary = ref(null)
const errorMessage = ref('')
const vectorScatterRef = ref(null)
let scatterChart = null

const profileRole = computed(() => getProfile()?.role || 'UNKNOWN')
const isAdmin = computed(() => profileRole.value === 'ADMIN')

function go(path) {
  router.push(path)
}

function formatInstant(v) {
  if (!v) return ''
  try {
    return new Date(v).toLocaleString()
  } catch {
    return String(v)
  }
}

function mulberry32(seed) {
  return function rand() {
    let t = (seed += 0x6d2b79f5)
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function buildScatterData(d) {
  const u = d.totalUsers | 0
  const kb = d.totalKnowledgeBases | 0
  const doc = d.totalDocuments | 0
  const chat = d.chatCalls24h | 0
  let seed = (u + 1) * 1009 + (kb + 1) * 7919 + (doc + 1) * 104729 + (chat + 1) * 5657
  const rnd = mulberry32(seed >>> 0)

  const clusterCount = 3 + (seed % 4)
  const centers = []
  for (let c = 0; c < clusterCount; c++) {
    centers.push({
      x: (rnd() - 0.5) * 16,
      y: (rnd() - 0.5) * 12,
      spread: 1.2 + rnd() * 2.8,
      label: `簇 ${c + 1}`,
    })
  }

  const n = Math.min(260, Math.max(56, doc * 5 + kb * 14 + u * 3 + Math.min(chat, 80)))
  const points = []
  for (let i = 0; i < n; i++) {
    const cen = centers[(seed + i) % clusterCount]
    const gx = cen.x + (rnd() + rnd() + rnd() - 1.5) * cen.spread
    const gy = cen.y + (rnd() + rnd() + rnd() - 1.5) * cen.spread * 0.85
    const dist = Math.hypot(gx - cen.x, gy - cen.y)
    points.push([gx, gy, dist, cen.label])
  }
  return { points, centers }
}

function renderScatter() {
  if (!vectorScatterRef.value || !data.value) {
    return
  }
  const { points, centers } = buildScatterData(data.value)
  if (!scatterChart) {
    scatterChart = echarts.init(vectorScatterRef.value)
  }
  const root = typeof document !== 'undefined' ? document.documentElement : null
  const isDark = root?.classList.contains('dark')
  const text = isDark ? '#c8d4e6' : '#3d4a5c'
  const line = isDark ? 'rgba(148,163,184,0.25)' : 'rgba(100,116,139,0.35)'
  const tooltipBg = isDark ? 'rgba(22, 26, 42, 0.94)' : 'rgba(248, 250, 252, 0.96)'
  const tooltipBorder = isDark ? 'rgba(129, 140, 248, 0.55)' : 'rgba(99, 102, 241, 0.38)'
  const tooltipText = isDark ? '#e8eef8' : '#1e293b'
  const tooltipMuted = isDark ? '#94a3b8' : '#64748b'

  scatterChart.setOption({
    animationDuration: 900,
    grid: { left: 48, right: 18, top: 28, bottom: 42 },
    tooltip: {
      trigger: 'item',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      borderWidth: 1,
      padding: [10, 14],
      textStyle: {
        color: tooltipText,
        fontSize: 12,
        lineHeight: 18,
      },
      extraCssText: isDark
        ? `border-radius:10px;box-shadow:0 10px 28px rgba(0,0,0,0.45),0 0 0 1px rgba(99,102,241,0.12) inset;backdrop-filter:blur(10px);`
        : `border-radius:10px;box-shadow:0 8px 24px rgba(15,23,42,0.12);`,
      formatter(p) {
        const v = p.value
        if (!Array.isArray(v)) return ''
        const title = p.seriesName === '簇心' ? '簇心' : '模拟向量点'
        const sub =
          p.seriesName === '簇心'
            ? `<span style="color:${tooltipMuted};font-size:11px">${v[3] || ''}</span>`
            : `<span style="color:${tooltipMuted};font-size:11px">距簇心 ${v[2].toFixed(2)}</span>`
        return `<div style="font-weight:600;margin-bottom:4px;color:${tooltipText}">${title}</div>
<div style="color:${tooltipMuted};font-size:11px;margin-bottom:6px">x: ${v[0].toFixed(2)}　y: ${v[1].toFixed(2)}</div>
${sub}`
      },
    },
    xAxis: {
      name: '投影维 1',
      nameTextStyle: { color: text, fontSize: 11 },
      axisLabel: { color: text, fontSize: 10 },
      axisLine: { lineStyle: { color: line } },
      splitLine: { lineStyle: { color: line, type: 'dashed' } },
    },
    yAxis: {
      name: '投影维 2',
      nameTextStyle: { color: text, fontSize: 11 },
      axisLabel: { color: text, fontSize: 10 },
      axisLine: { lineStyle: { color: line } },
      splitLine: { lineStyle: { color: line, type: 'dashed' } },
    },
    visualMap: {
      show: true,
      dimension: 2,
      min: 0,
      max: 6,
      calculable: false,
      inRange: {
        color: ['#38bdf8', '#6366f1', '#a78bfa', '#f472b6'],
      },
      textStyle: { color: text, fontSize: 10 },
      bottom: 4,
      left: 'center',
      orient: 'horizontal',
      itemWidth: 14,
      itemHeight: 100,
      text: ['高密度', '低密度'],
    },
    series: [
      {
        name: '知识点（模拟）',
        type: 'scatter',
        symbolSize(val) {
          const d = val[2]
          return Math.max(5, Math.min(22, 18 - d * 1.8))
        },
        itemStyle: {
          opacity: 0.78,
          shadowBlur: 10,
          shadowColor: 'rgba(99,102,241,0.35)',
        },
        data: points,
      },
      {
        name: '簇心',
        type: 'scatter',
        symbol: 'diamond',
        symbolSize: 14,
        itemStyle: { color: '#fbbf24', borderColor: '#fff', borderWidth: 1 },
        data: centers.map((c) => [c.x, c.y, 0, c.label]),
        z: 10,
      },
    ],
  })
}

async function load() {
  if (!isAdmin.value) return

  loading.value = true
  errorMessage.value = ''
  try {
    const [statusRes, runtimeRes] = await Promise.all([
      http.get('/system/status'),
      http.get('/system/runtime-summary'),
    ])
    data.value = statusRes.data
    runtimeSummary.value = runtimeRes.data
    await nextTick()
    renderScatter()
  } catch (e) {
    const msg = e?.message || '加载系统状态失败'
    errorMessage.value = msg
    ElMessage.error(msg)
    data.value = null
    runtimeSummary.value = null
    if (scatterChart) {
      scatterChart.clear()
    }
  } finally {
    loading.value = false
  }
}

function onResize() {
  scatterChart?.resize()
}

onMounted(() => {
  if (isAdmin.value) load()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  if (scatterChart) {
    scatterChart.dispose()
    scatterChart = null
  }
})
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
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

h2 {
  margin: 0 0 6px;
}

.sub {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.c {
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 10px;
  background: var(--app-surface-2);
}

.c span {
  color: var(--app-text-muted);
  font-size: 12px;
  display: block;
}

.c b {
  margin-top: 6px;
  display: block;
  font-size: 20px;
  color: var(--app-text);
}

.viz-block {
  margin-top: 18px;
  padding: 14px 14px 8px;
  border-radius: 14px;
  border: 1px solid var(--app-border);
  background: linear-gradient(
    165deg,
    color-mix(in srgb, var(--app-surface-2) 88%, var(--app-accent) 6%),
    var(--app-surface-2)
  );
}

.runtime-block {
  margin-top: 18px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid var(--app-border);
  background:
    radial-gradient(circle at top right, color-mix(in srgb, var(--app-accent) 20%, transparent), transparent 42%),
    linear-gradient(155deg, color-mix(in srgb, var(--app-surface-2) 90%, var(--app-accent) 5%), var(--app-surface));
}

.runtime-head h3 {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 650;
}

.runtime-desc {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--app-text-muted);
  line-height: 1.5;
}

.runtime-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.runtime-item {
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 10px 12px;
  background: color-mix(in srgb, var(--app-surface-2) 88%, transparent);
}

.runtime-item.wide {
  grid-column: 1 / -1;
}

.runtime-item span {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.runtime-item b {
  font-size: 15px;
  color: var(--app-text);
  word-break: break-all;
}

.viz-head h3 {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 650;
}

.viz-desc {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--app-text-muted);
  line-height: 1.5;
}

.vector-scatter {
  width: 100%;
  height: 320px;
}

.meta {
  margin-top: 14px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.perm-block {
  padding: 12px 4px;
}

.perm-actions {
  margin-top: 10px;
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.error-wrap {
  margin: 10px 0;
}

@media (max-width: 900px) {
  .cards {
    grid-template-columns: 1fr 1fr;
  }

  .runtime-grid {
    grid-template-columns: 1fr;
  }

  .vector-scatter {
    height: 260px;
  }
}
</style>

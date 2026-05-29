<template>
  <div class="shell">
    <header class="top glass">
      <el-button round @click="$router.push('/')">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <div class="titles">
        <h1>审计日志</h1>
        <p>全站关键操作留痕 · 仅管理员可访问</p>
      </div>
      <span class="spacer" />
    </header>

    <el-card class="panel" shadow="never">
      <div class="filters">
        <el-select v-model="action" clearable placeholder="操作类型" class="f-item" style="width: 200px">
          <el-option label="REGISTER" value="REGISTER" />
          <el-option label="LOGIN" value="LOGIN" />
          <el-option label="KB_CREATE" value="KB_CREATE" />
          <el-option label="KB_UPDATE" value="KB_UPDATE" />
          <el-option label="KB_DELETE" value="KB_DELETE" />
          <el-option label="DOC_UPLOAD" value="DOC_UPLOAD" />
          <el-option label="DOC_REINDEX" value="DOC_REINDEX" />
          <el-option label="DOC_DELETE" value="DOC_DELETE" />
          <el-option label="CHAT" value="CHAT" />
        </el-select>
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          class="f-item"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss.SSS[Z]"
        />
        <el-button type="primary" round @click="search">筛选</el-button>
        <el-button round @click="reset">重置</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe class="audit-table">
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column prop="action" label="操作" width="140" />

        <el-table-column prop="resourceType" label="资源类型" width="120" />

        <el-table-column prop="resourceId" label="资源ID" min-width="120" show-overflow-tooltip />

        <el-table-column prop="detail" label="详情" min-width="160" show-overflow-tooltip />

      </el-table>

      <div class="pager">

        <el-pagination

          v-model:current-page="page"

          v-model:page-size="size"

          :total="total"

          :page-sizes="[10, 20, 50]"

          layout="total, sizes, prev, pager, next"

          @current-change="load"

          @size-change="onSizeChange"

        />

      </div>

    </el-card>

  </div>

</template>



<script setup>

import { onMounted, ref } from 'vue'

import http from '../api/http'

import { ElMessage } from 'element-plus'

import { ArrowLeft } from '@element-plus/icons-vue'



const rows = ref([])

const loading = ref(false)

const page = ref(1)

const size = ref(20)

const total = ref(0)

const action = ref('')

const timeRange = ref([])



function onSizeChange() {

  page.value = 1

  load()

}



async function load() {

  loading.value = true

  try {

    const from = timeRange.value?.[0] || undefined

    const to = timeRange.value?.[1] || undefined

    const { data } = await http.get('/admin/audit-logs', {

      params: {

        page: page.value - 1,

        size: size.value,

        action: action.value || undefined,

        from,

        to,

      },

    })

    rows.value = (data.content || []).map((r) => ({

      ...r,

      createdAt: r.createdAt ? new Date(r.createdAt).toLocaleString() : '',

    }))

    total.value = data.totalElements ?? 0

  } catch (e) {

    ElMessage.error(e.message)

  } finally {

    loading.value = false

  }

}



function search() {

  page.value = 1

  load()

}



function reset() {

  page.value = 1

  action.value = ''

  timeRange.value = []

  load()

}



onMounted(load)

</script>



<style scoped>

.shell {

  max-width: 1260px;

  margin: 0 auto;

  padding: 24px 20px 48px;

  animation: rag-audit-in 0.55s ease both;

}



@keyframes rag-audit-in {

  from {

    opacity: 0;

    transform: translateY(10px);

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

  display: grid;

  grid-template-columns: auto 1fr auto;

  align-items: center;

  gap: 16px;

  padding: 16px 18px;

  margin-bottom: 20px;

}



.titles h1 {

  margin: 0;

  font-size: 1.35rem;

  font-weight: 700;

  color: var(--app-text);

}



.titles p {

  margin: 4px 0 0;

  font-size: 13px;

  color: var(--app-text-muted);

}



.spacer {

  width: 72px;

}



.panel {

  border-radius: 18px !important;

}



.filters {

  margin-bottom: 16px;

  display: flex;

  gap: 10px;

  align-items: center;

  flex-wrap: wrap;

}



.f-item {

  min-width: 0;

}



.pager {

  margin-top: 16px;

  justify-content: flex-end;

  display: flex;

}



.audit-table {

  border-radius: 12px;

  overflow: hidden;

}

</style>



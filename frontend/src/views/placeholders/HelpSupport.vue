<template>
  <div class="shell">
    <el-card class="glass">
      <div class="head">
        <div>
          <h2>帮助与支持</h2>
          <p class="sub">FAQ + 工单反馈（优先级、附件、时间线、管理员可处理）。</p>
        </div>
      </div>

      <el-tabs v-model="active">
        <el-tab-pane label="常见问题" name="faq">
          <el-collapse accordion>
            <el-collapse-item title="什么是 RAG？" name="1">
              <div class="p">先检索知识库片段，再把片段作为上下文交给大模型生成答案。</div>
            </el-collapse-item>
            <el-collapse-item title="为什么会出现 403？" name="2">
              <div class="p">403 通常是权限不足；系统级接口（运行状态/模型配置/工单管理）需要 `ADMIN` 权限。</div>
            </el-collapse-item>
            <el-collapse-item title="如何提交有效问题？" name="3">
              <div class="p">请尽量提供：操作步骤、截图/日志、以及你遇到的报错文本。</div>
            </el-collapse-item>
          </el-collapse>
        </el-tab-pane>

        <el-tab-pane label="提交工单" name="feedback">
          <div class="form">
            <el-form label-position="top">
              <el-form-item label="主题（可选）">
                <el-input v-model="topic" placeholder="例如：导入失败 / 模型无响应 / 页面乱码" />
              </el-form-item>

              <el-form-item label="优先级">
                <el-select v-model="priority" placeholder="请选择优先级" style="width: 220px">
                  <el-option label="低" value="LOW" />
                  <el-option label="普通" value="NORMAL" />
                  <el-option label="高" value="HIGH" />
                  <el-option label="紧急" value="URGENT" />
                </el-select>
              </el-form-item>

              <el-form-item label="问题描述（必填）">
                <el-input v-model="content" type="textarea" :rows="6" placeholder="请描述问题（包含报错信息/操作步骤更好）" />
              </el-form-item>

              <el-form-item label="联系方式（选填）">
                <el-input v-model="contact" placeholder="例如：邮箱/QQ/手机号（可不填）" />
              </el-form-item>

              <el-form-item label="附件（可选）">
                <el-upload
                  drag
                  multiple
                  :auto-upload="false"
                  :file-list="uploadFiles"
                  :on-change="onFilesChange"
                  :limit="5"
                >
                  <div class="el-upload__text">把截图/日志拖拽到这里，最多 5 个文件</div>
                </el-upload>
                <div class="upload-hint">提示：文件会被存到 MinIO（`support/*`），管理员可下载查看。</div>
              </el-form-item>

              <el-form-item>
                <div class="actions">
                  <el-button type="primary" :loading="submitting" @click="submit">提交工单</el-button>
                  <el-button :disabled="submitting" @click="clear">清空</el-button>
                </div>
              </el-form-item>
            </el-form>

            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="说明"
              description="提交后会写入数据库。你可以在“我的工单”里查看处理进度与时间线。"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="我的工单" name="my">
          <div class="toolbar">
            <el-button :loading="loadingMy" @click="loadMy">刷新</el-button>
          </div>

          <el-table :data="myList" stripe v-loading="loadingMy">
            <el-table-column prop="priority" label="优先级" width="120">
              <template #default="{ row }">
                <el-tag :type="priorityType(row.priority)">{{ priorityLabel(row.priority) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="topic" label="主题" min-width="160" />
            <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
            <el-table-column label="更新时间" width="180">
              <template #default="{ row }">{{ formatInstant(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="openDetail(row.id, false)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              background
              layout="prev, pager, next"
              :page-size="myPage.size"
              :total="myPage.total"
              :current-page="myPage.page + 1"
              @current-change="(p) => { myPage.page = p - 1; loadMy() }"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="isAdmin" label="工单管理" name="admin">
          <div class="admin-tools">
            <el-input v-model="adminKeyword" placeholder="搜索：主题/内容/联系方式" style="max-width: 320px" />
            <el-select v-model="adminStatus" placeholder="状态" style="width: 160px">
              <el-option label="全部" value="" />
              <el-option label="待处理" value="OPEN" />
              <el-option label="处理中" value="IN_PROGRESS" />
              <el-option label="已解决" value="RESOLVED" />
              <el-option label="已关闭" value="CLOSED" />
            </el-select>
            <el-button type="primary" :loading="loadingAdmin" @click="loadAdmin">查询</el-button>
          </div>

          <el-table :data="adminList" stripe v-loading="loadingAdmin">
            <el-table-column prop="priority" label="优先级" width="120">
              <template #default="{ row }">
                <el-tag :type="priorityType(row.priority)">{{ priorityLabel(row.priority) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="topic" label="主题" min-width="160" />
            <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
            <el-table-column prop="contact" label="联系方式" min-width="160" show-overflow-tooltip />
            <el-table-column label="更新时间" width="180">
              <template #default="{ row }">{{ formatInstant(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="openDetail(row.id, true)">详情/处理</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              background
              layout="prev, pager, next"
              :page-size="adminPage.size"
              :total="adminPage.total"
              :current-page="adminPage.page + 1"
              @current-change="(p) => { adminPage.page = p - 1; loadAdmin() }"
            />
          </div>
        </el-tab-pane>
      </el-tabs>

      <el-dialog
        v-model="detailVisible"
        :title="detailTitle"
        width="860px"
        :before-close="onBeforeCloseDetail"
      >
        <div v-if="detailLoading" class="loading-wrap">
          <el-skeleton :rows="10" animated />
        </div>

        <div v-else>
          <div class="detail-top">
            <el-tag :type="priorityType(detailTicket.priority)">{{ priorityLabel(detailTicket.priority) }}</el-tag>
            <el-tag :type="statusType(detailTicket.status)">{{ statusLabel(detailTicket.status) }}</el-tag>
            <span class="mono">ID: {{ detailTicket.id }}</span>
          </div>

          <div class="detail-topic">{{ detailTicket.topic || '-' }}</div>
          <div class="detail-content">{{ detailTicket.content }}</div>

          <div class="detail-meta">
            <div><span class="k">提交人</span><span class="v">{{ detailTicket.createdBy }}</span></div>
            <div><span class="k">联系人</span><span class="v">{{ detailTicket.contact || '-' }}</span></div>
            <div><span class="k">创建时间</span><span class="v">{{ formatInstant(detailTicket.createdAt) }}</span></div>
            <div><span class="k">更新时间</span><span class="v">{{ formatInstant(detailTicket.updatedAt) }}</span></div>
          </div>

          <el-tabs v-model="detailTab">
            <el-tab-pane label="处理" name="handle">
              <el-alert
                v-if="!canUpload"
                type="warning"
                :closable="false"
                show-icon
                title="上传权限受限"
                description="该工单不是你的（且你不是管理员），无法上传附件。"
              />

              <el-form label-width="70px" class="form-inline">
                <el-form-item label="状态">
                  <el-select v-model="handleModel.status" :disabled="!isAdmin">
                    <el-option label="待处理" value="OPEN" />
                    <el-option label="处理中" value="IN_PROGRESS" />
                    <el-option label="已解决" value="RESOLVED" />
                    <el-option label="已关闭" value="CLOSED" />
                  </el-select>
                </el-form-item>

                <el-form-item label="备注">
                  <el-input
                    v-model="handleModel.adminNote"
                    type="textarea"
                    :rows="4"
                    placeholder="管理员处理说明（可为空）"
                    :disabled="!isAdmin"
                  />
                </el-form-item>
              </el-form>

              <div class="actions">
                <el-button type="primary" :loading="saving" :disabled="!isAdmin" @click="saveHandle">保存处理</el-button>
              </div>

              <el-divider />

              <div class="upload-block">
                <div class="upload-title">上传附件（可选）</div>
                <el-upload
                  drag
                  multiple
                  :auto-upload="false"
                  :file-list="detailUploadFiles"
                  :on-change="onDetailFilesChange"
                  :limit="5"
                  :disabled="!canUpload"
                >
                  <div class="el-upload__text">拖拽文件到这里</div>
                </el-upload>

                <div class="upload-actions">
                  <el-button
                    type="primary"
                    :loading="uploading"
                    :disabled="!canUpload || detailUploadFiles.length === 0"
                    @click="uploadDetailAttachments"
                  >
                    上传附件
                  </el-button>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="时间线" name="timeline">
              <el-timeline>
                <el-timeline-item
                  v-for="ev in detailEvents"
                  :key="ev.id"
                  :timestamp="formatInstant(ev.createdAt)"
                  placement="top"
                >
                  <div class="ev-type">{{ ev.eventType }}</div>
                  <div class="ev-msg">{{ ev.message || '-' }}</div>
                </el-timeline-item>
              </el-timeline>
            </el-tab-pane>

            <el-tab-pane label="附件" name="attachments">
              <div class="att-list" v-if="detailTicket.attachments && detailTicket.attachments.length > 0">
                <div class="att" v-for="a in detailTicket.attachments" :key="a.id">
                  <div class="att-left">
                    <div class="att-name">{{ a.filename }}</div>
                    <div class="att-meta">{{ a.contentType || 'application/octet-stream' }} · {{ humanBytes(a.sizeBytes) }}</div>
                  </div>
                  <div class="att-right">
                    <el-button size="small" @click="downloadAttachment(detailTicket.id, a.id, a.filename)">下载</el-button>
                  </div>
                </div>
              </div>

              <div v-else class="empty">
                <div class="empty-title">暂无附件</div>
                <div class="empty-sub">你可以在“处理”页上传文件</div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>

        <template #footer>
          <el-button @click="detailVisible = false">关闭</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http, { getProfile } from '../../api/http'

const active = ref('faq')

const isAdmin = computed(() => (getProfile()?.role || '') === 'ADMIN')
const myId = computed(() => getProfile()?.id || '')

// submit
const topic = ref('')
const content = ref('')
const contact = ref('')
const priority = ref('NORMAL')
const submitting = ref(false)
const uploadFiles = ref([])

// list
const myList = ref([])
const loadingMy = ref(false)
const myPage = reactive({ page: 0, size: 10, total: 0 })

const adminList = ref([])
const loadingAdmin = ref(false)
const adminKeyword = ref('')
const adminStatus = ref('')
const adminPage = reactive({ page: 0, size: 10, total: 0 })

// detail
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailTab = ref('handle')
const detailEvents = ref([])
const detailUploadFiles = ref([])
const uploading = ref(false)

const detailTicket = reactive({
  id: '',
  createdBy: '',
  topic: '',
  content: '',
  contact: '',
  status: 'OPEN',
  priority: 'NORMAL',
  adminNote: '',
  attachments: [],
  createdAt: null,
  updatedAt: null,
})

const handleModel = reactive({ status: 'IN_PROGRESS', adminNote: '' })
const saving = ref(false)

const detailTitle = computed(() => (isAdmin.value ? '工单详情/处理（管理员）' : '工单详情'))
const canUpload = computed(() => {
  if (!detailTicket.id) return false
  if (isAdmin.value) return true
  return detailTicket.createdBy && String(detailTicket.createdBy) === String(myId.value)
})

function clear() {
  topic.value = ''
  content.value = ''
  contact.value = ''
  priority.value = 'NORMAL'
  uploadFiles.value = []
}

function onFilesChange(_file, fileList) {
  uploadFiles.value = fileList
}

function onDetailFilesChange(_file, fileList) {
  detailUploadFiles.value = fileList
}

function formatInstant(v) {
  if (!v) return ''
  try {
    return new Date(v).toLocaleString()
  } catch {
    return String(v)
  }
}

function statusType(s) {
  if (s === 'OPEN') return 'warning'
  if (s === 'IN_PROGRESS') return ''
  if (s === 'RESOLVED') return 'success'
  if (s === 'CLOSED') return 'info'
  return 'info'
}

function statusLabel(s) {
  if (s === 'OPEN') return '待处理'
  if (s === 'IN_PROGRESS') return '处理中'
  if (s === 'RESOLVED') return '已解决'
  if (s === 'CLOSED') return '已关闭'
  return s || '-'
}

function priorityType(p) {
  if (p === 'URGENT') return 'danger'
  if (p === 'HIGH') return 'warning'
  if (p === 'NORMAL') return 'info'
  if (p === 'LOW') return 'success'
  return 'info'
}

function priorityLabel(p) {
  if (p === 'URGENT') return '紧急'
  if (p === 'HIGH') return '高'
  if (p === 'NORMAL') return '普通'
  if (p === 'LOW') return '低'
  return p || '-'
}

function humanBytes(bytes) {
  if (bytes === null || bytes === undefined) return '-'
  const n = Number(bytes)
  if (!Number.isFinite(n)) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let idx = 0
  let v = n
  while (v >= 1024 && idx < units.length - 1) {
    v /= 1024
    idx++
  }
  return `${v.toFixed(v >= 10 ? 0 : 1)} ${units[idx]}`
}

async function uploadAttachment(ticketId, file) {
  const fd = new FormData()
  fd.append('file', file)
  await http.post(`/support/tickets/${ticketId}/attachments`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

async function submit() {
  if (!content.value.trim()) {
    ElMessage.warning('请先填写问题描述')
    return
  }
  submitting.value = true
  try {
    const res = await http.post('/support/tickets', {
      topic: topic.value.trim() || null,
      content: content.value.trim(),
      contact: contact.value.trim() || null,
      priority: priority.value,
    })

    const ticket = res.data

    if (uploadFiles.value && uploadFiles.value.length > 0) {
      for (const f of uploadFiles.value) {
        if (!f?.raw) continue
        await uploadAttachment(ticket.id, f.raw)
      }
    }

    ElMessage.success('已提交工单')
    clear()
    active.value = 'my'
    await loadMy()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function loadMy() {
  loadingMy.value = true
  try {
    const { data } = await http.get('/support/my-tickets', {
      params: { page: myPage.page, size: myPage.size },
    })
    myList.value = data?.content || []
    myPage.total = data?.totalElements || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loadingMy.value = false
  }
}

async function loadAdmin() {
  loadingAdmin.value = true
  try {
    const { data } = await http.get('/support/tickets', {
      params: {
        page: adminPage.page,
        size: adminPage.size,
        status: adminStatus.value || null,
        keyword: adminKeyword.value || null,
      },
    })
    adminList.value = data?.content || []
    adminPage.total = data?.totalElements || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loadingAdmin.value = false
  }
}

async function openDetail(ticketId, isAdminView) {
  detailVisible.value = true
  detailLoading.value = true
  detailTab.value = 'handle'

  try {
    const res = isAdminView ? await http.get(`/support/tickets/${ticketId}`) : await http.get(`/support/my-tickets/${ticketId}`)
    const detail = res.data

    Object.assign(detailTicket, detail.ticket)
    detailEvents.value = detail.events || []

    handleModel.status = detailTicket.status || 'IN_PROGRESS'
    handleModel.adminNote = detailTicket.adminNote || ''

    detailUploadFiles.value = []
  } catch (e) {
    ElMessage.error(e.message)
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function saveHandle() {
  if (!detailTicket.id) return
  saving.value = true
  try {
    await http.put(`/support/tickets/${detailTicket.id}`, {
      status: handleModel.status,
      adminNote: handleModel.adminNote || null,
    })

    ElMessage.success('处理已保存')

    await openDetail(detailTicket.id, true)
    await loadMy()
    if (isAdmin.value) await loadAdmin()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function uploadDetailAttachments() {
  if (!detailTicket.id || detailUploadFiles.value.length === 0) return

  uploading.value = true
  try {
    for (const f of detailUploadFiles.value) {
      if (!f?.raw) continue
      await uploadAttachment(detailTicket.id, f.raw)
    }

    ElMessage.success('附件已上传')
    detailUploadFiles.value = []
    await openDetail(detailTicket.id, isAdmin.value)
    await loadMy()
    if (isAdmin.value) await loadAdmin()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    uploading.value = false
  }
}

function onBeforeCloseDetail(done) {
  detailVisible.value = false
  if (typeof done === 'function') done()
}

async function downloadAttachment(ticketId, attachmentId, filename) {
  try {
    const { data } = await http.get(`/support/tickets/${ticketId}/attachments/${attachmentId}`, {
      responseType: 'blob',
    })

    const url = URL.createObjectURL(data)
    const a = document.createElement('a')
    a.href = url
    a.download = filename || 'attachment'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(() => {
  loadMy()
  if (isAdmin.value) loadAdmin()
})
</script>

<style scoped>
.shell { padding: 24px; }
.glass { background: var(--app-surface); border: 1px solid var(--app-border); }
.head { margin-bottom: 12px; }

h2 { margin: 0; }
.sub { margin: 6px 0 0; color: var(--app-text-muted); font-size: 13px; }
.p { line-height: 1.8; color: var(--app-text); }
.form { padding: 6px 4px; }
.mb { margin-bottom: 12px; }
.actions { display: flex; gap: 10px; align-items: center; }
.toolbar { display: flex; justify-content: flex-start; align-items: center; margin: 6px 0 12px; }
.admin-tools { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin: 6px 0 12px; }

.pager { margin-top: 12px; display: flex; justify-content: flex-end; }

.loading-wrap { padding: 8px 0; }

.detail-top { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.detail-topic { margin-top: 8px; font-weight: 700; font-size: 16px; }
.detail-content { margin-top: 10px; white-space: pre-wrap; line-height: 1.7; color: var(--app-text); }
.detail-meta { margin-top: 12px; display: grid; grid-template-columns: 1fr 1fr; gap: 10px 20px; color: var(--app-text-muted); font-size: 12px; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace; font-size: 12px; color: var(--app-text-muted); }
.k { display: inline-block; width: 70px; }
.v { color: var(--app-text); }

.upload-hint { margin-top: 8px; color: var(--app-text-muted); font-size: 12px; }
.upload-block { margin-top: 10px; }
.upload-title { font-weight: 700; margin-bottom: 8px; }
.upload-actions { margin-top: 10px; display: flex; justify-content: flex-start; }

.ev-type { font-weight: 700; margin-bottom: 4px; }
.ev-msg { color: var(--app-text); white-space: pre-wrap; }

.att-list { display: flex; flex-direction: column; gap: 10px; }
.att { display: flex; justify-content: space-between; align-items: center; border: 1px solid var(--app-border); border-radius: 12px; padding: 10px; background: rgba(255, 255, 255, 0.02); }
.att-name { font-weight: 700; word-break: break-word; }
.att-meta { margin-top: 4px; color: var(--app-text-muted); font-size: 12px; }
.att-right { flex-shrink: 0; }

.empty { padding: 12px 4px; }
.empty-title { font-weight: 700; margin-bottom: 6px; }
.empty-sub { color: var(--app-text-muted); font-size: 12px; }
</style>
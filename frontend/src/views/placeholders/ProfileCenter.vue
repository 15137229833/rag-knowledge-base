<template>
  <div class="shell">
    <el-card class="glass">
      <div class="head">
        <div>
          <h2>个人中心</h2>
          <p class="sub">账户信息、问答偏好、API 令牌与安全操作</p>
        </div>
      </div>

      <div class="grid">
        <el-card class="mini" shadow="never">
          <div class="title">账号信息</div>
          <div class="kv">
            <div class="row"><span class="k">用户名</span><span class="v">{{ profile.username || '-' }}</span></div>
            <div class="row">
              <span class="k">角色</span>
              <span class="v">
                <el-tag :type="profile.role === 'ADMIN' ? 'warning' : 'info'">
                  {{ profile.role || '-' }}
                </el-tag>
              </span>
            </div>
            <div class="row"><span class="k">User ID</span><span class="v">{{ profile.id || '-' }}</span></div>
          </div>

          <div class="small">说明：点击“刷新”会从后端同步最新用户信息（`/api/v1/users/me`）。</div>

          <div class="actions">
            <el-button type="primary" :loading="loading" @click="refresh">刷新</el-button>
          </div>
        </el-card>

        <el-card class="mini" shadow="never">
          <div class="title">修改密码</div>

          <el-form label-width="86px" class="form">
            <el-form-item label="旧密码">
              <el-input v-model="pwd.oldPassword" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="pwd.newPassword" type="password" show-password autocomplete="new-password" />
              <div class="hint">建议不少于 8 位，包含字母与数字。</div>
            </el-form-item>
            <el-form-item label="确认">
              <el-input v-model="pwd.newPassword2" type="password" show-password autocomplete="new-password" />
            </el-form-item>

            <div class="actions">
              <el-button type="primary" :loading="changing" @click="changePassword">更新密码</el-button>
              <el-button :disabled="changing" @click="clearPwd">清空</el-button>
            </div>

            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="说明"
              description="更新成功后不会自动退出；如果你希望更安全，我也可以改成更新后强制重新登录。"
            />
          </el-form>
        </el-card>

        <el-card class="mini full" shadow="never">
          <div class="title">安全</div>
          <div class="small">
            退出后会清除本地 `rag_token`，需要重新登录。
          </div>

          <div class="actions">
            <el-button type="danger" :loading="loggingOut" @click="logout">退出登录</el-button>
            <el-button plain :loading="clearing" @click="clearProfileOnly">清理本地个人缓存</el-button>
          </div>

          <div class="small muted">仅清除浏览器里的 `rag_profile`，不影响后端数据。</div>
        </el-card>

        <el-card class="mini full" shadow="never" v-loading="prefLoading">
          <div class="title">问答偏好</div>
          <p class="small">未在提问时指定「回答风格」时，将按此处设置映射为简洁 / 详细 / 平衡模式。</p>
          <el-form label-width="120px" class="form pref-form">
            <el-form-item label="回答风格">
              <el-select v-model="pref.answerStyle" placeholder="concise / detailed / conversational" style="width: 220px">
                <el-option label="简洁 concise" value="concise" />
                <el-option label="详细 detailed" value="detailed" />
                <el-option label="对话式 conversational" value="conversational" />
              </el-select>
            </el-form-item>
            <el-form-item label="界面语言">
              <el-select v-model="pref.language" style="width: 140px">
                <el-option label="中文 zh" value="zh" />
                <el-option label="English en" value="en" />
              </el-select>
            </el-form-item>
            <el-form-item label="专业程度">
              <el-select v-model="pref.expertiseLevel" style="width: 200px">
                <el-option label="入门 beginner" value="beginner" />
                <el-option label="中级 intermediate" value="intermediate" />
                <el-option label="进阶 advanced" value="advanced" />
                <el-option label="专家 expert" value="expert" />
              </el-select>
            </el-form-item>
            <el-form-item label="语气">
              <el-select v-model="pref.tone" style="width: 180px">
                <el-option label="专业 professional" value="professional" />
                <el-option label="友好 friendly" value="friendly" />
                <el-option label="正式 formal" value="formal" />
                <el-option label="轻松 casual" value="casual" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标字数">
              <el-input-number v-model="pref.preferredResponseLength" :min="120" :max="8000" :step="50" />
            </el-form-item>
            <el-form-item label="包含示例">
              <el-switch v-model="pref.includeExamples" />
            </el-form-item>
            <el-form-item label="强调引用">
              <el-switch v-model="pref.includeReferences" />
            </el-form-item>
            <el-form-item label="关注主题">
              <el-select
                v-model="pref.favoriteTopics"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="输入后回车添加标签"
                style="width: 100%; max-width: 520px"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingPref" @click="savePreference">保存偏好</el-button>
              <el-button :disabled="prefLoading" @click="loadPreference">重新加载</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="mini full" shadow="never" v-loading="tokenLoading">
          <div class="title">API 访问令牌</div>
          <p class="small">用于开放平台集成；列表中的令牌已脱敏，仅在创建成功时展示一次明文。</p>
          <div class="actions" style="padding-top: 0">
            <el-button type="primary" @click="openTokenDialog">新建令牌</el-button>
            <el-button @click="loadTokens">刷新列表</el-button>
          </div>
          <el-table :data="tokens" size="small" stripe style="margin: 0 12px 12px">
            <el-table-column prop="name" label="名称" width="140" />
            <el-table-column prop="tokenMasked" label="令牌" min-width="200" show-overflow-tooltip />
            <el-table-column prop="appName" label="应用" width="140" show-overflow-tooltip />
            <el-table-column label="状态" width="88">
              <template #default="{ row }">
                <el-tag size="small" :type="row.active ? 'success' : 'info'">{{ row.active ? '有效' : '已撤销' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="expiresAt" label="过期" width="170" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" size="small" :disabled="!row.active" @click="revokeTokenRow(row)">
                  撤销
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>

      <el-dialog v-model="tokenDialogVisible" title="新建 API 令牌" width="440px" destroy-on-close @closed="resetTokenForm">
        <el-form label-width="100px">
          <el-form-item label="名称" required>
            <el-input v-model="tokenForm.name" placeholder="便于识别的名称" />
          </el-form-item>
          <el-form-item label="应用名">
            <el-input v-model="tokenForm.appName" placeholder="可选" />
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="tokenForm.appDescription" type="textarea" rows="2" placeholder="可选" />
          </el-form-item>
          <el-form-item label="有效天数">
            <el-input-number v-model="tokenForm.expiryDays" :min="1" :max="3650" placeholder="空表示不过期" />
            <span class="hint">留空表示不设过期时间</span>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="tokenDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="creatingToken" @click="submitTokenCreate">创建</el-button>
        </template>
      </el-dialog>

      <el-divider />

      <div class="help">
        <div class="help-title">小提示</div>
        <div class="help-text">如果你发现“系统设置 / 运行状态 / 模型配置”看不到内容，通常是账号不是 `ADMIN`。</div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http, { getProfile, setToken } from '../../api/http'

const router = useRouter()

const loading = ref(false)
const changing = ref(false)
const loggingOut = ref(false)
const clearing = ref(false)

const profile = ref(getProfile() || { id: '', username: '', role: '' })

const pwd = reactive({
  oldPassword: '',
  newPassword: '',
  newPassword2: '',
})

const prefLoading = ref(false)
const savingPref = ref(false)
const pref = ref({
  answerStyle: 'concise',
  language: 'zh',
  expertiseLevel: 'intermediate',
  tone: 'professional',
  preferredResponseLength: 500,
  includeExamples: true,
  includeReferences: true,
  favoriteTopics: [],
})

const tokenLoading = ref(false)
const tokens = ref([])
const tokenDialogVisible = ref(false)
const creatingToken = ref(false)
const tokenForm = reactive({
  name: '',
  appName: '',
  appDescription: '',
  expiryDays: null,
})

function clearPwd() {
  pwd.oldPassword = ''
  pwd.newPassword = ''
  pwd.newPassword2 = ''
}

async function refresh() {
  loading.value = true
  try {
    const res = await http.get('/users/me')
    profile.value = res.data || profile.value
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function changePassword() {
  if (!pwd.oldPassword || !pwd.newPassword) {
    ElMessage.warning('请填写旧密码和新密码')
    return
  }
  if (pwd.newPassword !== pwd.newPassword2) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  if (pwd.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }

  changing.value = true
  try {
    await http.post('/users/me/change-password', {
      oldPassword: pwd.oldPassword,
      newPassword: pwd.newPassword,
    })
    ElMessage.success('密码已更新')
    clearPwd()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    changing.value = false
  }
}

function logout() {
  loggingOut.value = true
  try {
    setToken(null)
    router.push('/login')
  } finally {
    loggingOut.value = false
  }
}

function clearProfileOnly() {
  clearing.value = true
  try {
    localStorage.removeItem('rag_profile')
    profile.value = getProfile() || { id: '', username: '', role: '' }
    ElMessage.success('已清理本地个人缓存')
  } catch {
    ElMessage.error('清理失败，请检查浏览器存储权限')
  } finally {
    clearing.value = false
  }
}

async function loadPreference() {
  prefLoading.value = true
  try {
    const { data } = await http.get('/user/preferences')
    pref.value = {
      ...data,
      favoriteTopics: Array.isArray(data.favoriteTopics) ? [...data.favoriteTopics] : [],
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    prefLoading.value = false
  }
}

async function savePreference() {
  savingPref.value = true
  try {
    const body = {
      ...pref.value,
      favoriteTopics: pref.value.favoriteTopics || [],
    }
    const { data } = await http.put('/user/preferences', body)
    pref.value = {
      ...data,
      favoriteTopics: Array.isArray(data.favoriteTopics) ? [...data.favoriteTopics] : [],
    }
    ElMessage.success('偏好已保存')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    savingPref.value = false
  }
}

async function loadTokens() {
  tokenLoading.value = true
  try {
    const { data } = await http.get('/user/api-tokens')
    tokens.value = data || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    tokenLoading.value = false
  }
}

function openTokenDialog() {
  resetTokenForm()
  tokenDialogVisible.value = true
}

function resetTokenForm() {
  tokenForm.name = ''
  tokenForm.appName = ''
  tokenForm.appDescription = ''
  tokenForm.expiryDays = null
}

async function submitTokenCreate() {
  if (!tokenForm.name.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  creatingToken.value = true
  try {
    const payload = {
      name: tokenForm.name.trim(),
      appName: tokenForm.appName.trim() || undefined,
      appDescription: tokenForm.appDescription.trim() || undefined,
      expiryDays: tokenForm.expiryDays || undefined,
    }
    const { data } = await http.post('/user/api-tokens', payload)
    tokenDialogVisible.value = false
    await loadTokens()
    await ElMessageBox.alert(
      `请立即复制保存，关闭后无法再次查看完整令牌：\n\n${data.token}`,
      '令牌已创建',
      { confirmButtonText: '我已保存' },
    )
    try {
      await navigator.clipboard.writeText(data.token)
      ElMessage.success('已复制到剪贴板')
    } catch {
      /* 忽略剪贴板失败 */
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    creatingToken.value = false
  }
}

async function revokeTokenRow(row) {
  try {
    await ElMessageBox.confirm(`确定撤销「${row.name}」？`, '撤销令牌', { type: 'warning' })
    await http.post(`/user/api-tokens/${row.id}/revoke`)
    ElMessage.success('已撤销')
    await loadTokens()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

async function boot() {
  await refresh()
  await loadPreference()
  await loadTokens()
}
boot()
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

.title {
  font-weight: 700;
  padding: 12px 12px 0;
}

.kv {
  padding: 12px;
}

.row {
  display: flex;
  gap: 12px;
  margin: 10px 0;
  align-items: baseline;
}

.k {
  width: 84px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.v {
  word-break: break-word;
  font-size: 13px;
}

.small {
  padding: 0 12px 12px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.muted {
  opacity: 0.9;
}

.actions {
  padding: 0 12px 12px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.form {
  padding: 10px 12px 12px;
}

.pref-form {
  max-width: 640px;
}

.hint {
  margin-top: 6px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.help {
  padding: 6px 2px;
}

.help-title {
  font-weight: 700;
  margin-bottom: 6px;
}

.help-text {
  color: var(--app-text-muted);
  font-size: 13px;
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
<template>
  <div class="shell">
    <TechLoadingOverlay
      :visible="pageLoading || saving"
      :message="saving ? '正在写入模型与向量库配置…' : '正在拉取模型配置…'"
    />
    <el-card class="glass">
      <h2>模型配置</h2>
      <p class="sub">配置 LLM、Embedding、API 与默认推理参数（管理员可写）。</p>

      <el-form label-position="top" :model="form" class="form">
        <div class="grid">
          <el-form-item label="提供商">
            <el-select v-model="form.provider">
              <el-option label="OLLAMA" value="OLLAMA" />
              <el-option label="OPENAI" value="OPENAI" />
              <el-option label="AZURE_OPENAI" value="AZURE_OPENAI" />
            </el-select>
          </el-form-item>
          <el-form-item label="Chat 模型">
            <el-input v-model="form.chatModel" placeholder="例如 llama3.2 / gpt-4o-mini" />
          </el-form-item>
          <el-form-item label="Embedding 模型">
            <el-input v-model="form.embeddingModel" placeholder="例如 nomic-embed-text / text-embedding-3-large" />
          </el-form-item>
          <el-form-item label="API Base URL">
            <el-input v-model="form.apiBaseUrl" placeholder="例如 http://localhost:11434" />
          </el-form-item>
          <el-form-item label="API Key（可空）">
            <el-input v-model="form.apiKey" type="password" show-password placeholder="留空表示不更新密钥" />
          </el-form-item>
          <el-form-item label="向量库类型">
            <el-select v-model="form.vectorDbType">
              <el-option label="PGVECTOR" value="PGVECTOR" />
              <el-option label="MILVUS" value="MILVUS" />
              <el-option label="ELASTIC" value="ELASTIC" />
            </el-select>
          </el-form-item>
          <el-form-item label="向量库地址">
            <el-input v-model="form.vectorDbEndpoint" placeholder="例如 postgresql://localhost:5432/ragkb" />
          </el-form-item>
          <el-form-item label="默认 Temperature">
            <el-slider v-model="form.defaultTemperature" :min="0" :max="2" :step="0.05" show-input />
          </el-form-item>
          <el-form-item label="默认 Top P">
            <el-slider v-model="form.defaultTopP" :min="0.1" :max="1" :step="0.05" show-input />
          </el-form-item>
          <el-form-item label="默认 Top K">
            <el-slider v-model="form.defaultTopK" :min="1" :max="200" :step="1" show-input />
          </el-form-item>
        </div>
      </el-form>

      <div class="meta" v-if="meta">
        <el-tag round effect="plain">最近更新：{{ meta.updatedAt || '-' }}</el-tag>
        <el-tag round effect="plain">操作者：{{ meta.updatedBy || '-' }}</el-tag>
        <el-tag round effect="plain" type="info">Key 掩码：{{ meta.apiKeyMasked || '(空)' }}</el-tag>
      </div>
      <div class="actions">
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'
import TechLoadingOverlay from '../../components/TechLoadingOverlay.vue'

const pageLoading = ref(false)
const saving = ref(false)
const meta = ref(null)
const form = reactive({
  provider: 'OLLAMA',
  chatModel: '',
  embeddingModel: '',
  apiBaseUrl: '',
  apiKey: '',
  defaultTemperature: 0.7,
  defaultTopP: 0.9,
  defaultTopK: 40,
  vectorDbType: 'PGVECTOR',
  vectorDbEndpoint: '',
})

async function load() {
  pageLoading.value = true
  try {
    const { data } = await http.get('/system/model-settings')
    form.provider = data.provider || 'OLLAMA'
    form.chatModel = data.chatModel || ''
    form.embeddingModel = data.embeddingModel || ''
    form.apiBaseUrl = data.apiBaseUrl || ''
    form.apiKey = ''
    form.defaultTemperature = Number(data.defaultTemperature ?? 0.7)
    form.defaultTopP = Number(data.defaultTopP ?? 0.9)
    form.defaultTopK = Number(data.defaultTopK ?? 40)
    form.vectorDbType = data.vectorDbType || 'PGVECTOR'
    form.vectorDbEndpoint = data.vectorDbEndpoint || ''
    meta.value = data
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    pageLoading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const payload = { ...form }
    if (!payload.apiKey) delete payload.apiKey
    const { data } = await http.put('/system/model-settings', payload)
    meta.value = data
    form.apiKey = ''
    ElMessage.success('模型配置已保存')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.shell { padding: 24px; }
.glass { background: var(--app-surface); border: 1px solid var(--app-border); }
h2 { margin: 0 0 6px; }
.sub { color: var(--app-text-muted); margin: 0 0 12px; }
.grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.meta { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.actions { margin-top: 12px; display: flex; gap: 8px; justify-content: flex-end; }
@media (max-width: 980px) { .grid { grid-template-columns: 1fr; } }
</style>
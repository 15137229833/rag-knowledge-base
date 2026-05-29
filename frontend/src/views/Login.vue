<template>
  <AuthLayout title="欢迎回来" subtitle="登录后即可管理知识库、上传文档并开启 RAG 智能问答。">
    <h2 class="form-title">登录</h2>
    <p class="form-desc">使用已注册账号进入工作台</p>
    <el-form ref="formRef" :model="{ username, password }" :rules="rules" label-position="top" @submit.prevent="onSubmit">
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="username"
          size="large"
          autocomplete="username"
          placeholder="请输入用户名"
          clearable
        />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="password"
          type="password"
          size="large"
          show-password
          autocomplete="current-password"
          placeholder="请输入密码"
          @keyup.enter="onSubmit"
        />
      </el-form-item>
      <el-alert
        v-if="serverHint"
        class="login-err-alert"
        type="error"
        :closable="false"
        :title="serverHint"
        show-icon
      />
      <el-button
        class="submit-btn"
        type="primary"
        native-type="submit"
        :loading="loading"
        @click="onSubmit"
      >
        进入系统
      </el-button>
      <div class="oauth-row">
        <span class="oauth-label">第三方登录（演示占位）</span>
        <div class="oauth-btns">
          <el-button disabled size="small" round>微信</el-button>
          <el-button disabled size="small" round>企业 SSO</el-button>
        </div>
        <p class="oauth-hint">毕业设计演示环境未接入 OAuth，可留作后续扩展接口。</p>
      </div>
      <div class="foot-link">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </div>
    </el-form>
  </AuthLayout>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import http, { setToken } from '../api/http'
import { ElMessage } from 'element-plus'
import AuthLayout from '../components/AuthLayout.vue'

const router = useRouter()
const route = useRoute()
const username = ref('')
const password = ref('')
const loading = ref(false)
const serverHint = ref('')
const formRef = ref(null)

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function mapLoginError(e) {
  const msg = e?.message || '登录失败'
  serverHint.value = msg
  if (msg.includes('网络') || msg.includes('后端')) {
    return '无法连接后端：请确认 Spring Boot 已在 8080 启动，且前端代理正常。'
  }
  if (msg.includes('401') || msg.toLowerCase().includes('unauthorized') || msg.includes('用户名或密码')) {
    return '用户名或密码不正确，或账号已被禁用。'
  }
  if (msg.includes('403')) {
    return '请求被拒绝（403）：多为跨域或浏览器缓存了失效会话，可尝试清除本站 localStorage 后重试。'
  }
  return msg
}

async function onSubmit() {
  serverHint.value = ''
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const { data } = await http.post('/auth/login', {
      username: username.value,
      password: password.value,
    })
    setToken(data.token, data.user)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/')
  } catch (e) {
    const friendly = mapLoginError(e)
    serverHint.value = friendly
    ElMessage.error(friendly)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-err-alert {
  margin-bottom: 12px;
}
.oauth-row {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed var(--app-border);
}
.oauth-label {
  display: block;
  font-size: 12px;
  color: var(--app-text-muted);
  margin-bottom: 8px;
}
.oauth-btns {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.oauth-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--app-text-muted);
  line-height: 1.5;
}
</style>

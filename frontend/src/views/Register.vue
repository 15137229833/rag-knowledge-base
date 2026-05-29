<template>
  <AuthLayout title="创建账号" subtitle="一次注册，统一管理多个知识库；支持成员协作与权限划分。">
    <h2 class="form-title">注册</h2>
    <p class="form-desc">填写信息即可完成开通</p>
    <el-form ref="formRef" :model="{ username, password, email }" :rules="rules" label-position="top" @submit.prevent="onSubmit">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="username" size="large" autocomplete="username" placeholder="3–32 位字母数字" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="password"
          type="password"
          size="large"
          show-password
          autocomplete="new-password"
          placeholder="建议 8 位以上，含大小写与数字"
        />
        <div class="pwd-meter">
          <el-progress
            :percentage="pwdScore.percent"
            :color="pwdScore.color"
            :stroke-width="6"
            :show-text="false"
            style="margin-top: 8px"
          />
          <span class="pwd-hint">{{ pwdScore.label }}</span>
        </div>
      </el-form-item>
      <el-form-item label="邮箱（可选）" prop="email">
        <el-input v-model="email" type="email" size="large" placeholder="用于找回或通知（可空）" />
      </el-form-item>
      <el-alert v-if="serverHint" type="error" :closable="false" :title="serverHint" class="reg-alert" show-icon />
      <el-button class="submit-btn" type="primary" native-type="submit" :loading="loading" @click="onSubmit">
        注册并前往登录
      </el-button>
      <div class="oauth-row">
        <span class="oauth-label">第三方注册（演示占位）</span>
        <div class="oauth-btns">
          <el-button disabled size="small" round>企业目录</el-button>
        </div>
      </div>
      <div class="foot-link">
        已有账号？
        <router-link to="/login">返回登录</router-link>
      </div>
    </el-form>
  </AuthLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { ElMessage } from 'element-plus'
import AuthLayout from '../components/AuthLayout.vue'

const router = useRouter()
const username = ref('')
const password = ref('')
const email = ref('')
const loading = ref(false)
const serverHint = ref('')
const formRef = ref(null)

const pwdScore = computed(() => {
  const p = password.value || ''
  let score = 0
  if (p.length >= 8) score++
  if (p.length >= 12) score++
  if (/[a-z]/.test(p) && /[A-Z]/.test(p)) score++
  if (/\d/.test(p)) score++
  if (/[^a-zA-Z0-9]/.test(p)) score++
  const percent = Math.min(100, Math.round((score / 5) * 100))
  if (percent < 40) return { percent, color: '#f56c6c', label: '较弱：建议加长并混合字符类型' }
  if (percent < 70) return { percent, color: '#e6a23c', label: '中等：可再增加特殊符号' }
  return { percent, color: '#67c23a', label: '较强' }
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度 3–32', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6–64', trigger: 'blur' },
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
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
    await http.post('/auth/register', {
      username: username.value,
      password: password.value,
      email: email.value || undefined,
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    const msg = e?.message || '注册失败'
    serverHint.value = msg
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.pwd-meter {
  width: 100%;
}
.pwd-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--app-text-muted);
}
.reg-alert {
  margin-bottom: 10px;
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
</style>

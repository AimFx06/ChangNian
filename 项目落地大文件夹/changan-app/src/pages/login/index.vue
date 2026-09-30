<script setup lang="ts">
import { reactive } from 'vue'
import { request, saveSession } from '@/utils/request'
import { getApiHost, saveApiHost } from '@/utils/config'

interface AuthResult {
  token: string
  user: {
    role: 'CHILD' | 'ELDER'
    displayName: string
  }
}

const form = reactive({
  username: '',
  password: ''
})

const serverForm = reactive({
  apiHost: getApiHost()
})

function routeByRole(role: string) {
  uni.reLaunch({ url: role === 'ELDER' ? '/pages/elder/home' : '/pages/child/status' })
}

function goRegister() {
  uni.navigateTo({ url: '/pages/register/index' })
}

function saveServer() {
  serverForm.apiHost = saveApiHost(serverForm.apiHost)
  uni.showToast({ title: '服务器已保存', icon: 'success' })
}

async function login(username = form.username, password = form.password) {
  try {
    const result = await request<AuthResult>({
      url: '/auth/login',
      method: 'POST',
      data: { username, password }
    })
    saveSession(result.token, result.user)
    routeByRole(result.user.role)
  } catch (error: any) {
    uni.showToast({ title: error.message || '登录失败', icon: 'none' })
  }
}
</script>

<template>
  <view class="login-page">
    <view class="brand">
      <view class="brand-title">常念</view>
    </view>

    <view class="panel">
      <input v-model="form.username" class="input" placeholder="用户名" />
      <input v-model="form.password" class="input gap" password placeholder="密码" />
      <button class="primary-button submit" @tap="login()">登录</button>

    </view>

    <view class="server-panel">
      <view class="server-title">服务器</view>
      <view class="server-row">
        <input v-model="serverForm.apiHost" class="server-input" placeholder="http://服务器IP:8080" />
        <button class="server-button" @tap="saveServer">保存</button>
      </view>
    </view>

    <view class="register-link" @tap="goRegister">
      没有账号，注册一个
    </view>
  </view>
</template>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  box-sizing: border-box;
  padding: 96rpx 36rpx 40rpx;
  background: #f5f5f7;
}

.brand {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 58rpx;
}

.brand-title {
  font-size: 54rpx;
  font-weight: 900;
  color: #111113;
  text-align: center;
}

.panel {
  padding: 30rpx;
  border-radius: 22rpx;
  background: #fff;
  box-shadow: 0 12rpx 32rpx rgba(0, 0, 0, 0.06);
}

.gap {
  margin-top: 18rpx;
}

.submit {
  margin-top: 28rpx;
}

.register-link {
  margin-top: 28rpx;
  text-align: center;
  font-size: 28rpx;
  color: #0a84ff;
  font-weight: 700;
}

.server-panel {
  margin-top: 22rpx;
  padding: 24rpx 30rpx;
  border-radius: 22rpx;
  background: #fff;
  box-shadow: 0 12rpx 32rpx rgba(0, 0, 0, 0.04);
}

.server-title {
  margin-bottom: 14rpx;
  color: #1c1c1e;
  font-size: 26rpx;
  line-height: 1.2;
  font-weight: 800;
}

.server-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.server-input {
  flex: 1;
  min-width: 0;
  height: 76rpx;
  box-sizing: border-box;
  padding: 0 22rpx;
  border-radius: 18rpx;
  background: #f5f5f7;
  color: #1c1c1e;
  font-size: 24rpx;
}

.server-button {
  flex-shrink: 0;
  width: 112rpx;
  height: 76rpx;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 18rpx;
  background: #1c1c1e;
  color: #fff;
  font-size: 24rpx;
  line-height: 76rpx;
  font-weight: 800;
}

.server-button::after {
  border: 0;
}
</style>

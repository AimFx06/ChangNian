<script setup lang="ts">
import { reactive } from 'vue'
import { request, saveSession } from '@/utils/request'

interface AuthResult {
  token: string
  user: {
    role: 'CHILD' | 'ELDER'
    displayName: string
    gender?: string
    inviteCode: string
  }
}

const genderOptions = ['女', '男'] as const
const form = reactive({
  username: '',
  password: '',
  displayName: '',
  gender: '',
  phone: '',
  role: 'CHILD',
  familyCode: ''
})

function routeByRole(role: string) {
  uni.reLaunch({ url: role === 'ELDER' ? '/pages/elder/home' : '/pages/child/status' })
}

function selectGender(value: '女' | '男') {
  form.gender = value
}

async function register() {
  try {
    const result = await request<AuthResult>({
      url: '/auth/register',
      method: 'POST',
      data: form
    })
    saveSession(result.token, result.user)
    uni.showModal({
      title: '注册成功',
      content: result.user.role === 'CHILD' ? `家庭邀请码：${result.user.inviteCode}` : '已加入家庭',
      showCancel: false,
      success: () => routeByRole(result.user.role)
    })
  } catch (error: any) {
    uni.showToast({ title: error.message || '注册失败', icon: 'none' })
  }
}
</script>

<template>
  <view class="page">
    <view class="page-title">注册常念</view>
    <view class="page-sub">子女创建家庭，老人用邀请码加入。</view>

    <view class="role-switch">
      <view class="role" :class="{ active: form.role === 'CHILD' }" @tap="form.role = 'CHILD'">子女账号</view>
      <view class="role elder" :class="{ active: form.role === 'ELDER' }" @tap="form.role = 'ELDER'">老人账号</view>
    </view>

    <view class="form card">
      <input v-model="form.username" class="input" placeholder="用户名" />
      <input v-model="form.password" class="input" password placeholder="密码" />
      <input v-model="form.displayName" class="input" placeholder="姓名" />
      <view class="gender-field">
        <view class="gender-label">性别</view>
        <view class="gender-options">
          <view
            v-for="option in genderOptions"
            :key="option"
            class="gender-option"
            :class="{ active: form.gender === option }"
            @tap="selectGender(option)"
          >
            {{ option }}
          </view>
        </view>
      </view>
      <input v-model="form.phone" class="input" placeholder="手机号" />
      <input
        v-if="form.role === 'ELDER'"
        v-model="form.familyCode"
        class="input"
        placeholder="家庭邀请码"
      />
      <button class="primary-button" @tap="register">完成注册</button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.role-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-top: 26rpx;
}

.role {
  height: 84rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  color: #6e6e73;
  font-size: 28rpx;
  font-weight: 800;
}

.role.active {
  background: #111113;
  color: #fff;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.gender-field {
  min-height: 88rpx;
  box-sizing: border-box;
  padding: 12rpx 24rpx;
  border-radius: 14rpx;
  border: 1rpx solid #e5e5ea;
  background: #fff;
  display: grid;
  grid-template-columns: 88rpx 1fr;
  align-items: center;
  gap: 16rpx;
}

.gender-label {
  color: #808080;
  font-size: 28rpx;
  font-weight: 700;
}

.gender-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12rpx;
}

.gender-option {
  height: 60rpx;
  border-radius: 12rpx;
  background: #f2f2f7;
  color: #1d1d1f;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 800;
}

.gender-option.active {
  background: #111113;
  color: #fff;
}
</style>

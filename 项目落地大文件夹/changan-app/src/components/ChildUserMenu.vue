<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { clearSession, getUser, request, saveSession } from '@/utils/request'
import { closeSocket } from '@/utils/socket'

const menuOpen = ref(false)
const saving = ref(false)
const user = ref<any>(getUser() || {})
const genderOptions = ['女', '男'] as const
const form = reactive({
  displayName: user.value?.displayName || '',
  phone: user.value?.phone || '',
  gender: normalizeGender(user.value?.gender)
})

const avatarText = computed(() => {
  const name = String(form.displayName || user.value?.displayName || user.value?.username || '').trim()
  return name ? name.slice(-1) : '我'
})

const familyInviteCode = computed(() => {
  const code = String(user.value?.inviteCode || '').trim()
  if (code === 'CHANGNIAN-DEMO') return 'VIVO-01'
  return code || '暂无'
})

function toggleMenu() {
  form.displayName = user.value?.displayName || ''
  form.phone = user.value?.phone || ''
  form.gender = normalizeGender(user.value?.gender)
  menuOpen.value = !menuOpen.value
}

function closeMenu() {
  menuOpen.value = false
}

async function saveProfile() {
  const displayName = form.displayName.trim()
  if (!displayName) {
    uni.showToast({ title: '请填写姓名', icon: 'none' })
    return
  }
  if (saving.value) return
  saving.value = true
  try {
    const result = await request<{ token: string; user: any }>({
      url: '/auth/me',
      method: 'PUT',
      data: {
        displayName,
        phone: form.phone.trim(),
        gender: form.gender
      }
    })
    user.value = result.user
    form.displayName = result.user.displayName || ''
    form.phone = result.user.phone || ''
    form.gender = normalizeGender(result.user.gender)
    saveSession(result.token, result.user)
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error.message || '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

function normalizeGender(value: unknown) {
  return value === '男' || value === '女' ? value : ''
}

function selectGender(value: '男' | '女') {
  form.gender = value
}

function logout() {
  closeMenu()
  clearSession()
  closeSocket()
  uni.reLaunch({ url: '/pages/login/index' })
}
</script>

<template>
  <view class="user-menu">
    <view v-if="menuOpen" class="menu-scrim" @tap="closeMenu"></view>
    <view class="avatar" @tap.stop="toggleMenu">{{ avatarText }}</view>
    <view v-if="menuOpen" class="profile-menu" @tap.stop>
      <view class="profile-head">
        <view class="profile-avatar">{{ avatarText }}</view>
        <view class="profile-title">
          <view class="profile-name">{{ form.displayName || '未填写姓名' }}</view>
          <view class="profile-role">个人信息</view>
        </view>
      </view>

      <view class="field">
        <view class="field-label">姓名</view>
        <input v-model="form.displayName" class="field-input" placeholder="填写姓名" />
      </view>
      <view class="field">
        <view class="field-label">电话</view>
        <input v-model="form.phone" class="field-input" type="number" placeholder="填写手机号" />
      </view>
      <view class="field">
        <view class="field-label">性别</view>
        <view class="gender-options">
          <view
            v-for="option in genderOptions"
            :key="option"
            class="gender-option"
            :class="{ active: form.gender === option }"
            @tap.stop="selectGender(option)"
          >
            {{ option }}
          </view>
        </view>
      </view>
      <view class="field">
        <view class="field-label">家庭邀请码</view>
        <view class="field-value invite-code">{{ familyInviteCode }}</view>
      </view>

      <view class="profile-actions">
        <button class="save-option" :disabled="saving" @tap.stop="saveProfile">{{ saving ? '保存中' : '保存' }}</button>
        <button class="logout-option" @tap.stop="logout">退出登录</button>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.user-menu {
  position: relative;
  z-index: 70;
  flex-shrink: 0;
}

.avatar {
  position: relative;
  z-index: 72;
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fa114f;
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  box-shadow: 0 0 0 4rpx #fff, 0 0 0 6rpx rgba(250, 17, 79, 0.15);
}

.menu-scrim {
  position: fixed;
  inset: 0;
  z-index: 68;
}

.profile-menu {
  position: absolute;
  z-index: 73;
  top: 88rpx;
  right: 0;
  width: 430rpx;
  box-sizing: border-box;
  padding: 22rpx;
  border-radius: 26rpx;
  background: #fff;
  box-shadow: 0 14rpx 42rpx rgba(0, 0, 0, 0.16);
}

.profile-head {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-bottom: 20rpx;
}

.profile-avatar {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fa114f;
  color: #fff;
  font-size: 30rpx;
  font-weight: 800;
}

.profile-title {
  min-width: 0;
  flex: 1;
}

.profile-name {
  color: #1c1c1e;
  font-size: 30rpx;
  line-height: 1.25;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-role {
  margin-top: 4rpx;
  color: #8e8e93;
  font-size: 22rpx;
  line-height: 1.2;
  font-weight: 600;
}

.field {
  position: relative;
  z-index: 1;
  margin-top: 14rpx;
}

.field-label {
  margin-bottom: 8rpx;
  color: #8e8e93;
  font-size: 22rpx;
  font-weight: 700;
}

.field-input {
  box-sizing: border-box;
  width: 100%;
  height: 72rpx;
  padding: 0 20rpx;
  border-radius: 18rpx;
  background: #f2f2f7;
  color: #1c1c1e;
  font-size: 26rpx;
  font-weight: 700;
}

.field-value {
  box-sizing: border-box;
  width: 100%;
  min-height: 72rpx;
  padding: 16rpx 20rpx;
  border-radius: 18rpx;
  background: #f2f2f7;
  color: #1c1c1e;
  display: flex;
  align-items: center;
  font-size: 24rpx;
  line-height: 1.3;
  font-weight: 800;
  word-break: break-all;
}

.invite-code {
  letter-spacing: 1rpx;
}

.gender-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8rpx;
  box-sizing: border-box;
  width: 100%;
  height: 72rpx;
  padding: 6rpx;
  border-radius: 18rpx;
  background: #f2f2f7;
}

.gender-option {
  height: 60rpx;
  border-radius: 14rpx;
  background: transparent;
  color: #8e8e93;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 800;
}

.gender-option.active {
  background: #fff;
  color: #1c1c1e;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.06);
}

.profile-actions {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14rpx;
  margin-top: 20rpx;
}

.save-option,
.logout-option {
  height: 68rpx;
  margin: 0;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 800;
  line-height: 68rpx;
}

.save-option::after,
.logout-option::after {
  border: 0;
}

.save-option {
  background: #0a84ff;
  color: #fff;
}

.logout-option {
  background: rgba(250, 17, 79, 0.08);
  color: #fa114f;
}

.logout-option:active {
  background: rgba(250, 17, 79, 0.14);
}
</style>

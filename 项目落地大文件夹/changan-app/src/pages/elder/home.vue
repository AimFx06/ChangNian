<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { clearSession, getUser, request } from '@/utils/request'
import { addSocketListener, closeSocket, ensureSocket } from '@/utils/socket'

const home = ref<any>({ pendingRequests: [], medications: [] })
const responseText = reactive<Record<string, string>>({})
const activeRequest = ref<any>(null)
const directTalkOpen = ref(false)
const directResponseText = ref('')
const countdown = ref(0)
const playingRequestId = ref<string | number | null>(null)
let sosTimer: ReturnType<typeof setInterval> | null = null
let reminderTimer: ReturnType<typeof setTimeout> | null = null
let removeListener: null | (() => void) = null

const pendingRequests = computed(() => home.value?.pendingRequests || [])
const medications = computed(() => home.value?.medications || [])
const currentMeds = computed(() => medications.value.slice(0, Math.min(2, medications.value.length)))
const upcomingMeds = computed(() => medications.value.slice(currentMeds.value.length, currentMeds.value.length + 2))
const firstRequest = computed(() => pendingRequests.value[0])

const childProfile = computed(() => firstRequest.value?.child || home.value?.child || null)
const childName = computed(() => {
  const profile = childProfile.value
  return profile?.displayName || profile?.username || '孩子'
})
const childAvatarText = computed(() => {
  const name = childName.value.trim()
  return name ? name.slice(-1) : '孩'
})
const childRelation = computed(() => {
  const gender = childProfile.value?.gender
  if (gender === '女') return '闺女'
  if (gender === '男') return '儿子'
  return '孩子'
})

const messagePreview = computed(() => {
  const item = firstRequest.value
  if (item?.request?.id) return requestDisplayText(item)
  return '今天没有新的话筒，想孩子了也可以先说一句。'
})

function topicPromptText(item?: any) {
  const title = item?.topic?.title?.trim?.() || ''
  const content = item?.topic?.content?.trim?.() || ''
  if (title && content) return `${title}，${content}`
  return content || title
}

function requestDisplayText(item?: any) {
  const prompt = item?.request?.promptText?.trim?.() || ''
  const topicText = topicPromptText(item)
  if (prompt && !isTopicExplanationPrompt(prompt, item)) return normalizeElderPronouns(prompt)
  if (topicText) return elderFriendlyTopicText(item)
  return prompt || '想听你说说这件事。'
}

function isTopicExplanationPrompt(prompt: string, item?: any) {
  if (prompt.includes('挺惦记你这件事')) return true
  const topicText = topicPromptText(item)
  const title = item?.topic?.title?.trim?.() || ''
  const content = item?.topic?.content?.trim?.() || ''
  const contentHead = content.slice(0, 18)
  if (topicText && prompt === topicText) return true
  if (title && prompt.includes(title) && prompt.length > 45) return true
  return Boolean(contentHead && prompt.includes(contentHead))
}

function elderFriendlyTopicText(item?: any) {
  const title = item?.topic?.title?.trim?.() || ''
  const content = item?.topic?.content?.trim?.() || ''
  const child = childName.value
  const source = `${title}，${content}`
  if (source.includes('晚上好')) return `${child}想接着和你聊聊：晚上在忙什么呀？`
  if (source.includes('睡') || source.includes('精神')) return `${child}想问问你：最近睡得怎么样，精神还好吗？`
  if (source.includes('药')) return `${child}想问问你：今天吃药和身体感觉怎么样？`
  if (source.includes('晚饭') || source.includes('吃饭') || source.includes('买菜')) return `${child}想听你说说：今天吃了什么，过得怎么样？`
  if (title) return `${child}想听你说说：${normalizeElderPronouns(title)}。`
  return `${child}想听你说说今天过得怎么样。`
}

function normalizeElderPronouns(text: string) {
  return text
    .replace(/她看了吗/g, '你看了吗')
    .replace(/他看了吗/g, '你看了吗')
    .replace(/她看了没/g, '你看了没')
    .replace(/他看了没/g, '你看了没')
    .replace(/她想不想/g, '你想不想')
    .replace(/他想不想/g, '你想不想')
    .replace(/问问她/g, '问问你')
    .replace(/问问他/g, '问问你')
    .replace(/妈妈最近/g, '你最近')
    .replace(/爸爸最近/g, '你最近')
    .replace(/妈妈这几天/g, '你这几天')
    .replace(/爸爸这几天/g, '你这几天')
}

async function load() {
  try {
    home.value = await request({ url: '/home/elder' })
    planNextReminder()
  } catch (error: any) {
    uni.showToast({ title: error.message || '加载失败', icon: 'none' })
  }
}

function openTalk(item?: any) {
  if (item?.request?.id) {
    activeRequest.value = item
    return
  }
  activeRequest.value = null
  directTalkOpen.value = true
}

function stopPromptPlayback() {
  if (typeof window !== 'undefined' && window.speechSynthesis) {
    window.speechSynthesis.cancel()
  }
  playingRequestId.value = null
}

function playPrompt(item?: any) {
  if (!item?.request?.id) {
    uni.showModal({
      title: '现在没有新话筒',
      showCancel: false
    })
    return
  }

  const text = requestDisplayText(item)
  const requestId = item.request.id
  if (playingRequestId.value === requestId) {
    stopPromptPlayback()
    return
  }

  const speechWindow = typeof window === 'undefined' ? null : (window as any)
  if (speechWindow?.speechSynthesis && speechWindow?.SpeechSynthesisUtterance) {
    stopPromptPlayback()
    const utterance = new speechWindow.SpeechSynthesisUtterance(text)
    utterance.lang = 'zh-CN'
    utterance.rate = 0.9
    utterance.onend = () => {
      if (playingRequestId.value === requestId) playingRequestId.value = null
    }
    utterance.onerror = () => {
      if (playingRequestId.value === requestId) playingRequestId.value = null
      uni.showToast({ title: '播放失败', icon: 'none' })
    }
    playingRequestId.value = requestId
    speechWindow.speechSynthesis.speak(utterance)
    return
  }

  uni.showModal({
    title: `${childRelation.value}说`,
    content: text,
    confirmText: '听完了',
    showCancel: false
  })
}

function closeTalk() {
  activeRequest.value = null
  directTalkOpen.value = false
}

async function respond(item: any) {
  const text = responseText[item.request.id]?.trim()
  if (!text) {
    uni.showToast({ title: '先写一句回应', icon: 'none' })
    return
  }
  try {
    await request({
      url: `/elder/messages/${item.request.id}/responses`,
      method: 'POST',
      data: { content: text }
    })
    home.value.pendingRequests = home.value.pendingRequests.filter((current: any) => current.request.id !== item.request.id)
    responseText[item.request.id] = ''
    activeRequest.value = null
    uni.showToast({ title: '已回应', icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error.message || '回应失败', icon: 'none' })
  }
}

async function sendDirectTalk() {
  const text = directResponseText.value.trim()
  if (!text) {
    uni.showToast({ title: '先写一句想说的话', icon: 'none' })
    return
  }
  try {
    await request({
      url: '/elder/messages/responses',
      method: 'POST',
      data: { content: text }
    })
    directResponseText.value = ''
    directTalkOpen.value = false
    uni.showToast({ title: `已发给${childRelation.value}`, icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error.message || '发送失败', icon: 'none' })
  }
}

async function recordMedication(id: number, action: 'taken' | 'later') {
  try {
    await request({
      url: `/medications/${id}/${action}`,
      method: 'POST'
    })
    uni.showToast({ title: action === 'taken' ? '已记录吃药' : '已记录待会吃', icon: 'success' })
    planNextReminder()
  } catch (error: any) {
    uni.showToast({ title: error.message || '记录失败', icon: 'none' })
  }
}

async function recordCurrentMedication(action: 'taken' | 'later') {
  if (!currentMeds.value.length) {
    uni.showToast({ title: '今天暂无用药提醒', icon: 'none' })
    return
  }

  for (const med of currentMeds.value) {
    await recordMedication(med.id, action)
  }
}

function planNextReminder() {
  if (reminderTimer) {
    clearTimeout(reminderTimer)
  }
  const meds = medications.value
  if (!meds.length) {
    return
  }
  const now = new Date()
  let nextMed: any = null
  let nextTime = Number.MAX_SAFE_INTEGER
  meds.forEach((med: any) => {
    const [hour, minute] = String(med.remindTime).split(':').map(Number)
    const target = new Date()
    target.setHours(hour || 0, minute || 0, 0, 0)
    if (target.getTime() <= now.getTime()) {
      target.setDate(target.getDate() + 1)
    }
    if (target.getTime() < nextTime) {
      nextTime = target.getTime()
      nextMed = med
    }
  })
  const delay = Math.max(1000, nextTime - now.getTime())
  reminderTimer = setTimeout(() => {
    uni.showModal({
      title: '该吃药了',
      content: `${nextMed.medicineName} · ${nextMed.dosage}`,
      cancelText: '待会吃',
      confirmText: '吃了',
      success: (res) => {
        recordMedication(nextMed.id, res.confirm ? 'taken' : 'later')
      }
    })
  }, delay)
}

function showMedical(action: string) {
  uni.showModal({
    title: action,
    content: '第一版先保留看病助手入口。后续会接入录音、拍处方单、拍化验单和上传归档。',
    showCancel: false
  })
}

function startSOS() {
  if (countdown.value > 0) return
  countdown.value = 3
  sosTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      cancelTimerOnly()
      triggerSOS()
    }
  }, 1000)
}

function cancelTimerOnly() {
  if (sosTimer) {
    clearInterval(sosTimer)
    sosTimer = null
  }
  countdown.value = 0
}

async function triggerSOS() {
  try {
    await request({
      url: '/emergencies',
      method: 'POST',
      data: {
        locationText: '老人端手机当前位置待接入',
        note: '老人端点击 SOS'
      }
    })
    uni.showModal({
      title: '已通知家人',
      content: 'SOS 记录已发送到子女端。',
      showCancel: false
    })
  } catch (error: any) {
    uni.showToast({ title: error.message || 'SOS 发送失败', icon: 'none' })
  }
}

function logout() {
  removeListener?.()
  if (reminderTimer) {
    clearTimeout(reminderTimer)
    reminderTimer = null
  }
  cancelTimerOnly()
  stopPromptPlayback()
  activeRequest.value = null
  directTalkOpen.value = false
  closeSocket()
  clearSession()
  uni.reLaunch({ url: '/pages/login/index' })
}

onMounted(() => {
  load()
  ensureSocket()
  removeListener = addSocketListener((message) => {
    if (message.type === 'MIC_REQUEST_CREATED') {
      const user = getUser<any>()
      const elderId = message.payload?.request?.elderId
      if (user?.id && elderId && String(user.id) !== String(elderId)) {
        return
      }
      if (!home.value.pendingRequests) home.value.pendingRequests = []
      home.value.pendingRequests.unshift(message.payload)
      uni.showToast({ title: '收到新的话题邀请', icon: 'none' })
    }
  })
})

onUnmounted(() => {
  removeListener?.()
  if (reminderTimer) {
    clearTimeout(reminderTimer)
  }
  stopPromptPlayback()
  cancelTimerOnly()
})
</script>

<template>
  <view class="elder-page">
    <button class="sos-bar" @tap="startSOS">
      <text class="sos-text">SOS</text>
    </button>

    <view class="content">
      <view class="msg-card">
        <view class="msg-header">
          <view class="msg-avatar">{{ childAvatarText }}</view>
          <view class="msg-from">
            <view class="msg-name">{{ childName }}</view>
            <view class="msg-relation">{{ childRelation }}</view>
          </view>
        </view>
        <view class="msg-preview">{{ messagePreview }}</view>
        <view v-if="firstRequest?.topic" class="topic-line">{{ firstRequest.topic.category }} · {{ normalizeElderPronouns(firstRequest.topic.title || '') }}</view>
        <view class="msg-actions">
          <button
            class="play-btn"
            :class="{ active: playingRequestId === firstRequest?.request?.id }"
            @tap="playPrompt(firstRequest)"
          >
            <view class="play-icon">
              <view></view>
              <view></view>
              <view></view>
              <view></view>
              <view></view>
            </view>
            <text>{{ playingRequestId === firstRequest?.request?.id ? '正在听' : '点开听' }}</text>
          </button>
          <button class="talk-btn" @tap="openTalk(firstRequest)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="9" y="3" width="6" height="12" rx="3" />
              <path d="M5 11a7 7 0 0 0 14 0" />
              <line x1="12" y1="18" x2="12" y2="22" />
            </svg>
            <text>跟{{ childRelation }}说一句</text>
          </button>
        </view>
      </view>

      <view class="pill-card">
        <view class="pill-now" :class="{ empty: !currentMeds.length }">
          <view v-if="currentMeds.length" class="pill-now-top">
            <view class="pill-now-label">现 在 要 吃</view>
            <view class="pill-now-time">{{ currentMeds[0]?.remindTime || '中午 12:00' }}</view>
          </view>
          <view class="pill-now-list">
            <view v-for="med in currentMeds" :key="med.id" class="pill-now-item">
              <view class="pill-now-name">{{ med.medicineName }}</view>
              <view class="pill-now-dose">{{ med.dosage }}</view>
            </view>
            <view v-if="!currentMeds.length" class="pill-now-item">
              <view class="pill-now-name">今天暂无用药提醒</view>
              <view class="pill-now-dose">安心</view>
            </view>
          </view>
          <view v-if="currentMeds.length" class="pill-actions">
            <button class="pill-btn-primary" @tap="recordCurrentMedication('taken')">现 在 吃</button>
            <button class="pill-btn-secondary" @tap="recordCurrentMedication('later')">待 会 吃</button>
          </view>
        </view>

        <view class="pill-upcoming">
          <view class="pill-upcoming-label">今 天 后 面 还 要 吃</view>
          <view v-for="med in upcomingMeds" :key="med.id" class="pill-upcoming-item">
            <view class="pill-upcoming-time">{{ med.remindTime }}</view>
            <view class="pill-upcoming-name">{{ med.medicineName }}</view>
            <view class="pill-upcoming-dose">{{ med.dosage }} · {{ med.note || '按时吃' }}</view>
          </view>
          <view v-if="!upcomingMeds.length" class="pill-upcoming-item">
            <view class="pill-upcoming-time">--:--</view>
            <view class="pill-upcoming-name">后面暂无安排</view>
          </view>
        </view>
      </view>

      <view class="hospital-card">
        <view class="hospital-header">
          <view class="hospital-cross"></view>
          <view class="hospital-title-wrap">
            <view class="hospital-title">看 病 助 手</view>
            <view class="hospital-sub">MEDICAL ASSISTANT</view>
          </view>
          <view class="hospital-tag">在医院用</view>
        </view>
        <view class="hospital-body">
          <view class="hospital-item" @tap="showMedical('录下医生说的话')">
            <view class="hospital-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="#0A84FF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="9" y="3" width="6" height="12" rx="3" />
                <path d="M5 11a7 7 0 0 0 14 0" />
                <line x1="12" y1="18" x2="12" y2="22" />
              </svg>
            </view>
            <view class="hospital-item-text">录医嘱</view>
          </view>
          <view class="hospital-item" @tap="showMedical('拍处方单')">
            <view class="hospital-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="#0A84FF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <polyline points="14 2 14 8 20 8" />
                <line x1="9" y1="13" x2="15" y2="13" />
                <line x1="9" y1="17" x2="13" y2="17" />
              </svg>
            </view>
            <view class="hospital-item-text">拍处方</view>
          </view>
          <view class="hospital-item" @tap="showMedical('拍化验单')">
            <view class="hospital-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="#0A84FF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" />
                <circle cx="12" cy="13" r="4" />
              </svg>
            </view>
            <view class="hospital-item-text">拍化验</view>
          </view>
        </view>
      </view>
    </view>

    <view class="logout-footer">
      <button class="logout-button" @tap="logout">退出登录</button>
    </view>

    <view v-if="activeRequest || directTalkOpen" class="talk-mask">
      <view class="talk-card">
        <view class="talk-title">跟{{ childRelation }}说一句</view>
        <view class="talk-prompt">
          {{ activeRequest ? requestDisplayText(activeRequest) : `想跟${childRelation}说什么？` }}
        </view>
        <textarea
          v-if="activeRequest"
          v-model="responseText[activeRequest.request.id]"
          class="elder-textarea"
          :placeholder="`在这里写几句，点下面按钮发给${childRelation}`"
        ></textarea>
        <textarea
          v-else
          v-model="directResponseText"
          class="elder-textarea"
          :placeholder="`在这里写几句，点下面按钮发给${childRelation}`"
        ></textarea>
        <view class="talk-actions">
          <button class="talk-cancel" @tap="closeTalk">取消</button>
          <button v-if="activeRequest" class="talk-send" @tap="respond(activeRequest)">发给{{ childRelation }}</button>
          <button v-else class="talk-send" @tap="sendDirectTalk">发给{{ childRelation }}</button>
        </view>
      </view>
    </view>

    <view v-if="countdown > 0" class="countdown-mask">
      <view class="countdown-card">
        <view class="count-num">{{ countdown }}</view>
        <view class="count-text">即将通知家人</view>
        <button class="cancel-button" @tap="cancelTimerOnly">取消</button>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.elder-page {
  height: 100vh;
  box-sizing: border-box;
  padding: 0;
  background: #f5f5f7;
  color: #1c1c1e;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sos-bar {
  flex-shrink: 0;
  height: 96rpx;
  margin: 16rpx 32rpx 20rpx;
  padding: 0;
  border: 0;
  border-radius: 36rpx;
  background: linear-gradient(135deg, #ff3b30 0%, #d70015 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 36rpx rgba(255, 59, 48, 0.32);
}

.sos-bar::after,
button::after {
  border: 0;
}

.sos-text {
  padding-left: 20rpx;
  color: #fff;
  font-size: 52rpx;
  line-height: 1;
  font-weight: 900;
  letter-spacing: 20rpx;
  font-style: italic;
  text-shadow: 0 4rpx 8rpx rgba(0, 0, 0, 0.2);
}

.content {
  flex: 1;
  min-height: 0;
  padding: 0 32rpx 18rpx;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
  overflow: hidden;
}

.msg-card {
  flex-shrink: 0;
  box-sizing: border-box;
  padding: 26rpx 32rpx 24rpx;
  border-radius: 44rpx;
  border: 4rpx solid #fa114f;
  background: linear-gradient(135deg, #fff5f7 0%, #ffe8ec 100%);
}

.msg-header {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 12rpx;
}

.msg-avatar {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fa114f;
  color: #fff;
  font-size: 34rpx;
  font-weight: 800;
  box-shadow: 0 8rpx 20rpx rgba(250, 17, 79, 0.25);
}

.msg-from {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.msg-name {
  font-size: 34rpx;
  line-height: 1.2;
  font-weight: 800;
  color: #1c1c1e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-relation {
  color: #8e8e93;
  font-size: 24rpx;
  line-height: 1.2;
  font-weight: 800;
}

.msg-preview {
  margin-bottom: 14rpx;
  color: #1c1c1e;
  font-size: 30rpx;
  line-height: 1.42;
  font-weight: 600;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.topic-line {
  margin: -2rpx 0 14rpx;
  color: #fa114f;
  font-size: 24rpx;
  font-weight: 800;
}

.msg-actions {
  display: flex;
  gap: 16rpx;
}

.play-btn,
.talk-btn {
  height: 96rpx;
  margin: 0;
  border-radius: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  font-size: 30rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
}

.play-btn {
  flex: 0 0 42%;
  background: #fa114f;
  color: #fff;
  box-shadow: 0 8rpx 28rpx rgba(250, 17, 79, 0.3);
}

.talk-btn {
  flex: 1;
  background: #fff;
  border: 5rpx solid #fa114f;
  color: #fa114f;
}

.talk-btn svg {
  width: 40rpx;
  height: 40rpx;
}

.play-icon {
  display: flex;
  align-items: center;
  gap: 5rpx;
  height: 40rpx;
}

.play-icon view {
  width: 6rpx;
  border-radius: 999rpx;
  background: #fff;
}

.play-icon view:nth-child(1) {
  height: 15rpx;
}

.play-icon view:nth-child(2) {
  height: 27rpx;
}

.play-icon view:nth-child(3) {
  height: 40rpx;
}

.play-icon view:nth-child(4) {
  height: 24rpx;
}

.play-icon view:nth-child(5) {
  height: 15rpx;
}

.pill-card,
.hospital-card {
  flex-shrink: 0;
  border-radius: 44rpx;
  border: 3rpx solid #e5e5ea;
  background: #fff;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.pill-now {
  padding: 22rpx 30rpx;
  border-bottom: 2rpx solid #ffd5db;
  background: linear-gradient(135deg, #fff5f7 0%, #ffedf1 100%);
}

.pill-now.empty {
  padding: 28rpx 30rpx;
}

.pill-now.empty .pill-now-list {
  margin-bottom: 0;
}

.pill-now-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14rpx;
}

.pill-now-label {
  display: flex;
  align-items: center;
  gap: 12rpx;
  color: #fa114f;
  font-size: 24rpx;
  font-weight: 800;
  letter-spacing: 3rpx;
}

.pill-now-label::before {
  content: "";
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #fa114f;
  box-shadow: 0 0 0 6rpx rgba(250, 17, 79, 0.2);
}

.pill-now-time {
  color: #1c1c1e;
  font-size: 30rpx;
  font-weight: 900;
  letter-spacing: 2rpx;
}

.pill-now-list {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  margin-bottom: 18rpx;
}

.pill-now-item {
  display: flex;
  align-items: center;
  padding: 6rpx 0;
}

.pill-now-name {
  flex: 1;
  min-width: 0;
  font-size: 34rpx;
  line-height: 1.25;
  font-weight: 800;
}

.pill-now-dose {
  flex-shrink: 0;
  padding: 6rpx 20rpx;
  border-radius: 16rpx;
  border: 2rpx solid #ffd5db;
  background: #fff;
  color: #fa114f;
  font-size: 28rpx;
  font-weight: 800;
}

.pill-actions {
  display: flex;
  gap: 16rpx;
}

.pill-btn-primary,
.pill-btn-secondary {
  flex: 1;
  height: 88rpx;
  margin: 0;
  border-radius: 24rpx;
  font-size: 30rpx;
  font-weight: 800;
  letter-spacing: 4rpx;
}

.pill-btn-primary {
  background: #30d158;
  color: #fff;
  box-shadow: 0 8rpx 24rpx rgba(48, 209, 88, 0.3);
}

.pill-btn-secondary {
  border: 4rpx solid #d1d1d6;
  background: #fff;
  color: #1c1c1e;
}

.pill-upcoming {
  padding: 16rpx 30rpx 18rpx;
}

.pill-upcoming-label {
  margin-bottom: 8rpx;
  color: #8e8e93;
  font-size: 22rpx;
  font-weight: 800;
  letter-spacing: 3rpx;
}

.pill-upcoming-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 9rpx 0;
}

.pill-upcoming-item + .pill-upcoming-item {
  border-top: 2rpx dashed #e5e5ea;
}

.pill-upcoming-time {
  flex-shrink: 0;
  padding: 6rpx 16rpx;
  border-radius: 12rpx;
  background: #f2f2f7;
  color: #1c1c1e;
  font-size: 24rpx;
  font-weight: 800;
}

.pill-upcoming-name {
  flex: 1;
  min-width: 0;
  color: #1c1c1e;
  font-size: 26rpx;
  font-weight: 800;
}

.pill-upcoming-dose {
  flex-shrink: 0;
  color: #8e8e93;
  font-size: 22rpx;
  font-weight: 600;
}

.hospital-header {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 16rpx 30rpx;
  border-bottom: 3rpx solid #e5e5ea;
  background: linear-gradient(135deg, #e8f4fd 0%, #d6eafd 100%);
}

.hospital-cross {
  position: relative;
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
  background: #fff;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.08);
}

.hospital-cross::before,
.hospital-cross::after {
  content: "";
  position: absolute;
  left: 50%;
  top: 50%;
  border-radius: 4rpx;
  background: #0a84ff;
  transform: translate(-50%, -50%);
}

.hospital-cross::before {
  width: 32rpx;
  height: 8rpx;
}

.hospital-cross::after {
  width: 8rpx;
  height: 32rpx;
}

.hospital-title-wrap {
  flex: 1;
  min-width: 0;
}

.hospital-title {
  font-size: 30rpx;
  line-height: 1.2;
  font-weight: 800;
  letter-spacing: 2rpx;
}

.hospital-sub {
  margin-top: 2rpx;
  color: #8e8e93;
  font-size: 20rpx;
  letter-spacing: 1rpx;
}

.hospital-tag {
  flex-shrink: 0;
  padding: 6rpx 16rpx;
  border-radius: 12rpx;
  background: #0a84ff;
  color: #fff;
  font-size: 20rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
}

.hospital-body {
  display: flex;
  padding: 10rpx 8rpx;
}

.hospital-item {
  flex: 1;
  position: relative;
  padding: 8rpx 4rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
}

.hospital-item + .hospital-item::before {
  content: "";
  position: absolute;
  left: 0;
  top: 20%;
  width: 2rpx;
  height: 60%;
  background: #e5e5ea;
}

.hospital-icon {
  width: 66rpx;
  height: 66rpx;
  border-radius: 22rpx;
  border: 3rpx solid #d6eafd;
  background: #f2f8ff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.hospital-icon svg {
  width: 40rpx;
  height: 40rpx;
}

.hospital-item-text {
  font-size: 26rpx;
  font-weight: 800;
}

.logout-footer {
  flex-shrink: 0;
  padding: 0 32rpx 24rpx;
}

.logout-button {
  height: 76rpx;
  margin: 0;
  border-radius: 24rpx;
  border: 3rpx solid #d1d1d6;
  background: #fff;
  color: #8e8e93;
  font-size: 26rpx;
  font-weight: 800;
}

.logout-button::after {
  border: 0;
}

.talk-mask,
.countdown-mask {
  position: fixed;
  inset: 0;
  z-index: 99;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
  box-sizing: border-box;
  background: rgba(0, 0, 0, 0.45);
}

.talk-card,
.countdown-card {
  width: 100%;
  box-sizing: border-box;
  padding: 42rpx;
  border-radius: 32rpx;
  background: #fff;
}

.talk-title {
  font-size: 40rpx;
  font-weight: 900;
}

.talk-prompt {
  margin-top: 16rpx;
  font-size: 30rpx;
  line-height: 1.45;
  color: #3c3c43;
}

.elder-textarea {
  width: 100%;
  min-height: 220rpx;
  box-sizing: border-box;
  margin-top: 24rpx;
  padding: 24rpx;
  border-radius: 22rpx;
  border: 3rpx solid #e5e5ea;
  background: #fbfbfd;
  font-size: 32rpx;
  line-height: 1.5;
}

.talk-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
  margin-top: 24rpx;
}

.talk-cancel,
.talk-send,
.cancel-button {
  height: 96rpx;
  margin: 0;
  border-radius: 20rpx;
  font-size: 32rpx;
  font-weight: 900;
}

.talk-cancel,
.cancel-button {
  background: #f2f2f7;
  color: #111113;
}

.talk-send {
  background: #fa114f;
  color: #fff;
}

.countdown-card {
  text-align: center;
}

.count-num {
  color: #b00020;
  font-size: 120rpx;
  line-height: 1;
  font-weight: 900;
}

.count-text {
  margin-top: 12rpx;
  font-size: 34rpx;
  font-weight: 900;
}

.cancel-button {
  width: 100%;
  margin-top: 30rpx;
}
</style>

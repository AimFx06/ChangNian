<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import ChildTabBar from '@/components/ChildTabBar.vue'
import ChildFamilySwitcher from '@/components/ChildFamilySwitcher.vue'
import ChildUserMenu from '@/components/ChildUserMenu.vue'
import EmergencyBanner from '@/components/EmergencyBanner.vue'
import { request } from '@/utils/request'
import { getFileBase } from '@/utils/config'
import { addSocketListener, ensureSocket } from '@/utils/socket'
import { useFamilyMember } from '@/utils/familyMember'
import { acknowledgeEmergenciesLocally, confirmEmergency, getVisibleEmergency } from '@/utils/emergency'

const home = ref<any>(null)
const topicIndex = ref(0)
const aiLoading = ref(false)
const audioDurations = reactive<Record<string, number | null>>({})
const playingAudioKey = ref<string | null>(null)
let removeListener: null | (() => void) = null
let activeAudio: HTMLAudioElement | null = null
const { familyName, familyUsername } = useFamilyMember()

type ElderAccount = {
  id: number
  username: string
  displayName: string
  phone?: string
}

type AiTopicRefreshResult = {
  topics: any[]
  mocked: boolean
  deepSeekConfigured?: boolean
  weeklyReport?: any
  dailyStatuses?: any[]
  memories?: any[]
}

const topics = computed(() => {
  const list = home.value?.topicCards || []
  const elderId = selectedElder.value?.id
  return elderId ? list.filter((item: any) => item?.elderId === elderId) : list
})

const selectedElder = computed<ElderAccount | null>(() => {
  const elders = (home.value?.elders || []) as ElderAccount[]
  return elders.find((elder) => elder.username === familyUsername.value) || null
})

const allResponses = computed(() => {
  const list = home.value?.responses || []
  const elderId = selectedElder.value?.id
  return elderId ? list.filter((item: any) => item?.response?.elderId === elderId) : list
})

const responses = computed(() => allResponses.value.filter((item: any) => isToday(item?.response?.createdAt)))

const latestEmergency = computed(() => getVisibleEmergency(home.value?.emergencies))

async function load() {
  try {
    home.value = await request({ url: '/home/child' })
  } catch (error: any) {
    uni.showToast({ title: error.message || '加载失败', icon: 'none' })
  }
}

async function refreshAiTopics() {
  if (aiLoading.value) return
  aiLoading.value = true
  try {
    const result = await request<AiTopicRefreshResult>({
      url: '/ai/insights/refresh',
      method: 'POST',
      data: { elderUsername: familyUsername.value }
    })
    if (!home.value) home.value = {}
    if (result.topics) home.value.topicCards = result.topics
    if (result.weeklyReport) {
      const reports = (home.value.weeklyReports || []).filter((item: any) => item?.elderId !== selectedElder.value?.id)
      home.value.weeklyReports = [result.weeklyReport, ...reports]
    }
    if (result.dailyStatuses) {
      const elderId = selectedElder.value?.id
      const others = (home.value.dailyStatuses || []).filter((item: any) => !elderId || item?.elderId !== elderId)
      home.value.dailyStatuses = [...result.dailyStatuses, ...others]
    }
    if (result.memories) {
      const elderId = selectedElder.value?.id
      const others = (home.value.memories || []).filter((item: any) => !elderId || item?.elderId !== elderId)
      home.value.memories = [...result.memories, ...others]
    }
    await load()
    topicIndex.value = 0
    uni.showToast({
      title: result.mocked ? '未配置Key，已用本地示例' : 'AI数据已更新',
      icon: result.mocked ? 'none' : 'success'
    })
  } catch (error: any) {
    uni.showToast({ title: error.message || '生成失败', icon: 'none' })
  } finally {
    aiLoading.value = false
  }
}

async function passMic(topic: any) {
  if (String(topic.id).startsWith('fallback')) {
    uni.showToast({ title: '数据加载后可递话筒', icon: 'none' })
    return
  }

  try {
    await request({
      url: `/topics/${topic.id}/microphone-requests`,
      method: 'POST',
      data: { elderUsername: familyUsername.value }
    })
    uni.showToast({ title: `已递给${familyName.value}`, icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error.message || '发送失败', icon: 'none' })
  }
}

function selfSay() {
  uni.showModal({
    title: '我自己说一句',
    content: '第一版先保留入口。后续可接入录音或文字消息，仍由家人亲自表达。',
    showCancel: false
  })
}

async function dismissEmergency() {
  const event = latestEmergency.value
  if (!event) return
  if (home.value?.emergencies) {
    acknowledgeEmergenciesLocally(home.value.emergencies)
    home.value.emergencies = []
  }
  await confirmEmergency(event)
}

function onTopicChange(event: any) {
  topicIndex.value = event.detail.current || 0
}

function selectTopic(index: number) {
  topicIndex.value = index
}

function formatSendTime(value?: string) {
  if (!value) return '刚刚'
  const date = parseLocalDate(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ')
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function parseLocalDate(value?: string) {
  if (!value) return new Date(Number.NaN)
  const text = String(value).replace('T', ' ').trim()
  const match = text.match(/^(\d{4})-(\d{2})-(\d{2})(?:\s+(\d{2}):(\d{2})(?::(\d{2}))?)?/)
  if (!match) return new Date(value)
  return new Date(
    Number(match[1]),
    Number(match[2]) - 1,
    Number(match[3]),
    Number(match[4] || 0),
    Number(match[5] || 0),
    Number(match[6] || 0)
  )
}

function isToday(value?: string) {
  const date = parseLocalDate(value)
  if (Number.isNaN(date.getTime())) return false
  const today = new Date()
  return date.getFullYear() === today.getFullYear() && date.getMonth() === today.getMonth() && date.getDate() === today.getDate()
}

function openDetail(payload: Record<string, unknown>) {
  uni.setStorageSync('childDetail', payload)
  uni.navigateTo({ url: '/pages/child/detail' })
}

function openResponseDetail(item: any) {
  const response = item?.response || {}
  const title = response.sourceType === 'MICROPHONE' ? '回应了你的递话筒' : '主动留言'
  openDetail({
    backText: '话题',
    title,
    tag: familyName.value,
    meta: formatSendTime(response.createdAt),
    content: response.content || '这条回应包含一段录音。',
    sourceTitle: item?.topic?.title || '',
    sourceText: item?.topic?.content || item?.topic?.sourceText || ''
  })
}

function hasAudio(item: any) {
  return Boolean(item?.response?.audioUrl)
}

function responseAudioKey(item: any) {
  return String(item?.response?.id || item?.response?.audioUrl || '')
}

function audioSrc(item: any) {
  const raw = item?.response?.audioUrl || ''
  if (!raw) return ''
  if (/^(https?:|data:|blob:)/i.test(raw)) return raw
  return `${getFileBase()}${raw.startsWith('/') ? raw : `/${raw}`}`
}

function formatAudioDuration(value?: number | null) {
  if (!value || !Number.isFinite(value) || value <= 0) return '--:--'
  const total = Math.max(1, Math.round(value))
  const minutes = Math.floor(total / 60)
  const seconds = String(total % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
}

function audioDuration(item: any) {
  return formatAudioDuration(audioDurations[responseAudioKey(item)])
}

function loadAudioDuration(item: any) {
  if (!hasAudio(item)) return
  const key = responseAudioKey(item)
  if (!key || key in audioDurations) return
  audioDurations[key] = null
  if (typeof Audio === 'undefined') return

  const audio = new Audio()
  audio.preload = 'metadata'
  audio.onloadedmetadata = () => {
    audioDurations[key] = Number.isFinite(audio.duration) ? audio.duration : null
  }
  audio.onerror = () => {
    audioDurations[key] = null
  }
  audio.src = audioSrc(item)
}

function stopAudio() {
  if (activeAudio) {
    activeAudio.pause()
    activeAudio = null
  }
  playingAudioKey.value = null
}

async function playAudio(item: any) {
  const src = audioSrc(item)
  const key = responseAudioKey(item)
  if (!src || !key) return
  if (playingAudioKey.value === key) {
    stopAudio()
    return
  }
  stopAudio()
  if (typeof Audio === 'undefined') {
    uni.showToast({ title: '当前环境暂不支持播放', icon: 'none' })
    return
  }

  const audio = new Audio(src)
  activeAudio = audio
  playingAudioKey.value = key
  audio.onended = stopAudio
  audio.onerror = () => {
    stopAudio()
    uni.showToast({ title: '录音播放失败', icon: 'none' })
  }
  try {
    await audio.play()
  } catch {
    stopAudio()
    uni.showToast({ title: '录音播放失败', icon: 'none' })
  }
}

function waveHeights(index: number) {
  const waves = [
    [8, 14, 20, 12, 18, 10, 16, 6, 14, 18, 8, 12, 16, 10, 14, 6],
    [10, 16, 8, 14, 20, 12, 6, 18, 10, 14, 8, 16, 12, 18, 6, 10],
    [8, 12, 18, 10, 14, 20, 8, 16, 12, 6, 14, 18, 10, 12, 16, 8]
  ]
  return waves[index % waves.length]
}

watch(
  responses,
  (list) => {
    list.forEach(loadAudioDuration)
  },
  { immediate: true }
)

onMounted(() => {
  load()
  ensureSocket()
  removeListener = addSocketListener((message) => {
    if (message.type === 'ELDER_RESPONSE_CREATED') {
      if (!home.value) home.value = {}
      if (!home.value.responses) home.value.responses = []
      home.value.responses.unshift(message.payload)
      uni.showToast({ title: '收到老人回应', icon: 'none' })
    }
    if (message.type === 'EMERGENCY_CREATED') {
      if (!home.value) home.value = {}
      if (!home.value.emergencies) home.value.emergencies = []
      home.value.emergencies.unshift(message.payload)
    }
  })
})

onUnmounted(() => {
  removeListener?.()
  stopAudio()
})
</script>

<template>
  <view class="topic-page">
    <view class="nav-bar">
      <ChildFamilySwitcher />
      <ChildUserMenu />
    </view>

    <EmergencyBanner :event="latestEmergency" @close="dismissEmergency" />

    <view class="content">
      <view class="topics-section">
        <view class="section-header">
          <view class="section-title">今天可以聊什么</view>
          <button class="ai-button" :disabled="aiLoading" @tap="refreshAiTopics">
            <svg viewBox="0 0 16 16" fill="currentColor">
              <path d="M8 1.5l1.7 4.1 4.3.4-3.3 2.8 1 4.2L8 10.8 4.3 13l1-4.2L2 6l4.3-.4L8 1.5z" />
            </svg>
            <text>{{ aiLoading ? '生成中' : 'AI卡片' }}</text>
          </button>
        </view>

        <swiper v-if="topics.length" class="card-swiper" :current="topicIndex" :duration="260" @change="onTopicChange">
          <swiper-item v-for="topic in topics" :key="topic.id" class="topic-slide">
            <view class="topic-card">
              <view class="topic-tag">
                <svg viewBox="0 0 16 16" fill="currentColor">
                  <path d="M8 14s-6-3.5-6-8a3.5 3.5 0 016-2.5A3.5 3.5 0 0114 6c0 4.5-6 8-6 8z" />
                </svg>
                <text>{{ topic.category }}</text>
              </view>
              <view class="topic-text">
                <text class="hl">{{ topic.title }}</text>
                <text>，{{ topic.content }}</text>
              </view>
              <view class="card-actions">
                <button class="btn btn-primary" @tap="selfSay">
                  <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="6" y="2" width="4" height="8" rx="2" />
                    <path d="M3 8a5 5 0 0010 0M8 13v2" />
                  </svg>
                  <text>我自己说</text>
                </button>
                <button class="btn btn-secondary" @tap="passMic(topic)">递话筒</button>
              </view>
            </view>
          </swiper-item>
        </swiper>
        <view v-else class="empty-panel topic-empty">
          <view class="empty-title">暂无话题卡</view>
          <view class="empty-text">历史数据已清空，点击 AI卡片 生成新的话题。</view>
        </view>

        <view v-if="topics.length > 1" class="dots">
          <view
            v-for="(_, index) in topics"
            :key="index"
            class="dot-button"
            @tap="selectTopic(index)"
          >
            <view class="dot" :class="{ active: topicIndex === index }"></view>
          </view>
        </view>
      </view>

      <view class="replies-section">
        <view class="section-header replies-head">
          <view class="section-title">{{ familyName }}刚刚说了什么</view>
        </view>

        <scroll-view scroll-y class="replies-scroll">
          <view
            v-for="(item, index) in responses"
            :key="item.response.id"
            class="reply-card"
            :class="{ 'text-only': !hasAudio(item) }"
            hover-class="reply-card-active"
            @tap="openResponseDetail(item)"
          >
            <view class="reply-from" :class="{ self: item.response.sourceType !== 'MICROPHONE', replied: item.response.sourceType === 'MICROPHONE' }">
              <text class="name">{{ familyName }}</text>
              <text class="dot-sep">·</text>
              <text class="flag">{{ item.response.sourceType === 'MICROPHONE' ? '回应了你的递话筒' : '主动留言' }}</text>
            </view>

            <view v-if="hasAudio(item)" class="reply-main">
              <button class="play-btn" :class="{ active: playingAudioKey === responseAudioKey(item) }" aria-label="播放" @tap.stop="playAudio(item)">
                <svg viewBox="0 0 16 16" fill="currentColor">
                  <path d="M4 3l9 5-9 5V3z" />
                </svg>
              </button>
              <view class="wave">
                <view
                  v-for="(height, waveIndex) in waveHeights(index)"
                  :key="waveIndex"
                  class="bar"
                  :class="{ active: waveIndex < 5 }"
                  :style="{ height: `${height * 2}rpx` }"
                ></view>
              </view>
              <view class="reply-meta">
                <view class="dur">{{ audioDuration(item) }}</view>
                <view class="time">{{ formatSendTime(item.response.createdAt) }}</view>
              </view>
            </view>

            <view class="reply-text">{{ item.response.content || '这条回应包含一段录音。' }}</view>
            <view v-if="!hasAudio(item)" class="reply-send-time">{{ formatSendTime(item.response.createdAt) }}</view>

            <view v-if="item.topic" class="reply-source">
              <text class="source-tag">{{ item.topic.category }}</text>
              <text class="source-text">{{ item.topic.title || item.topic.content }}</text>
              <text class="source-arrow">›</text>
            </view>
          </view>
          <view v-if="!responses.length" class="empty-panel replies-empty">
            <view class="empty-title">暂无回应</view>
            <view class="empty-text">{{ familyName }}今天新的回应会显示在这里。</view>
          </view>
        </scroll-view>
      </view>
    </view>

    <ChildTabBar active="topics" />
  </view>
</template>

<style scoped lang="scss">
.topic-page {
  height: 100vh;
  box-sizing: border-box;
  padding: 36rpx 0 166rpx;
  background: #ececf0;
  color: #1c1c1e;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.nav-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14rpx 40rpx 12rpx;
}

.content {
  flex: 1;
  min-height: 0;
  padding: 20rpx 0 0;
  display: flex;
  flex-direction: column;
  gap: 22rpx;
  overflow: hidden;
}

.topics-section,
.replies-section {
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.topics-section {
  flex: 1.32;
}

.replies-section {
  flex: 1;
  padding: 0 32rpx;
}

.section-header {
  flex-shrink: 0;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 40rpx 20rpx;
}

.replies-head {
  padding: 0 8rpx 20rpx;
}

.section-title {
  font-size: 28rpx;
  line-height: 1.2;
  font-weight: 800;
  color: #1c1c1e;
}

.ai-button {
  height: 52rpx;
  margin: 0;
  padding: 0 18rpx;
  border: 0;
  border-radius: 16rpx;
  background: #1c1c1e;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: 22rpx;
  line-height: 52rpx;
  font-weight: 800;
}

.ai-button::after {
  border: 0;
}

.ai-button[disabled] {
  opacity: 0.62;
}

.ai-button svg {
  width: 22rpx;
  height: 22rpx;
}

.empty-panel {
  flex: 1;
  box-sizing: border-box;
  padding: 36rpx 40rpx;
  border-radius: 32rpx;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
}

.topic-empty {
  margin: 4rpx 32rpx 8rpx;
}

.replies-empty {
  min-height: 180rpx;
}

.empty-title {
  margin-bottom: 12rpx;
  color: #1c1c1e;
  font-size: 30rpx;
  line-height: 1.25;
  font-weight: 800;
}

.empty-text {
  color: #8e8e93;
  font-size: 26rpx;
  line-height: 1.45;
  font-weight: 500;
}

.card-swiper {
  flex: 1;
  min-height: 0;
  height: 100%;
  width: 100%;
}

.replies-scroll::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

:deep(.uni-scroll-view) {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

:deep(.uni-scroll-view::-webkit-scrollbar) {
  display: none;
  width: 0;
  height: 0;
}

.topic-slide {
  height: 100%;
  padding: 4rpx 32rpx 8rpx;
  box-sizing: border-box;
}

.topic-card {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  padding: 28rpx 36rpx 26rpx;
  border-radius: 32rpx;
  background: #fff;
  white-space: normal;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.topic-tag {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  background: rgba(255, 149, 0, 0.12);
  color: #ff9500;
  font-size: 22rpx;
  line-height: 1.2;
  font-weight: 800;
}

.topic-tag svg {
  width: 22rpx;
  height: 22rpx;
}

.topic-text {
  flex: 1;
  min-height: 0;
  margin-top: 18rpx;
  font-size: 28rpx;
  line-height: 1.48;
  color: #1c1c1e;
  overflow: visible;
}

.hl {
  font-weight: 800;
}

.card-actions {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-top: 18rpx;
}

.btn {
  height: 72rpx;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  font-size: 26rpx;
  line-height: 72rpx;
  font-weight: 700;
}

.btn::after {
  border: 0;
}

.btn svg {
  width: 24rpx;
  height: 24rpx;
}

.btn-primary {
  background: #ff9500;
  color: #fff;
}

.btn-secondary {
  background: #fff;
  color: #fa114f;
  border: 1rpx solid rgba(250, 17, 79, 0.35);
}

.dots {
  flex-shrink: 0;
  height: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 10rpx;
}

.dot-button {
  height: 28rpx;
  min-width: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: #d1d1d6;
  transition: width 0.2s ease, background 0.2s ease;
}

.dot.active {
  width: 32rpx;
  border-radius: 999rpx;
  background: #ff9500;
}

.replies-scroll {
  flex: 1;
  min-height: 0;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.reply-card {
  box-sizing: border-box;
  padding: 20rpx 28rpx 24rpx;
  margin-bottom: 16rpx;
  border-radius: 28rpx;
  background: #fff;
}

.reply-card-active {
  background: #f8f8fa;
}

.reply-card:last-child {
  margin-bottom: 0;
}

.reply-from {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
  font-size: 22rpx;
  line-height: 1.2;
  font-weight: 700;
  color: #8e8e93;
}

.reply-from .name {
  color: #1c1c1e;
}

.dot-sep {
  color: #c7c7cc;
}

.reply-from.self .flag {
  color: #fa114f;
}

.reply-from.self .flag::before {
  content: "";
  display: inline-block;
  width: 10rpx;
  height: 10rpx;
  margin-right: 6rpx;
  border-radius: 50%;
  background: #fa114f;
  vertical-align: middle;
}

.reply-from.replied .flag {
  color: #ff9500;
}

.reply-main {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.play-btn {
  flex-shrink: 0;
  width: 72rpx;
  height: 72rpx;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: #ff9500;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.play-btn::after {
  border: 0;
}

.play-btn svg {
  width: 28rpx;
  height: 28rpx;
}

.wave {
  flex: 1;
  min-width: 0;
  height: 44rpx;
  display: flex;
  align-items: center;
  gap: 4rpx;
  overflow: hidden;
}

.bar {
  width: 4rpx;
  border-radius: 999rpx;
  background: #c7c7cc;
}

.bar.active {
  background: #ff9500;
}

.reply-meta {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4rpx;
}

.dur {
  font-size: 26rpx;
  line-height: 1.2;
  font-weight: 700;
  color: #1c1c1e;
}

.time {
  font-size: 22rpx;
  color: #8e8e93;
}

.reply-text {
  margin-top: 14rpx;
  font-size: 24rpx;
  line-height: 1.45;
  color: #3c3c43;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}

.reply-send-time {
  margin-top: 8rpx;
  color: #8e8e93;
  font-size: 22rpx;
  line-height: 1.25;
  font-weight: 600;
}

.reply-source {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-top: 18rpx;
  padding: 16rpx 20rpx;
  border-radius: 20rpx;
  background: #f8f8fa;
}

.source-tag {
  flex-shrink: 0;
  padding: 6rpx 16rpx;
  border-radius: 12rpx;
  background: rgba(255, 149, 0, 0.12);
  color: #ff9500;
  font-size: 20rpx;
  font-weight: 800;
}

.source-text {
  flex: 1;
  min-width: 0;
  font-size: 24rpx;
  color: #3c3c43;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-arrow {
  flex-shrink: 0;
  color: #8e8e93;
  font-size: 32rpx;
  line-height: 1;
}
</style>

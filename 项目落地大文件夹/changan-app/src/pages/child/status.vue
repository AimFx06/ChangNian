<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import ChildTabBar from '@/components/ChildTabBar.vue'
import ChildFamilySwitcher from '@/components/ChildFamilySwitcher.vue'
import ChildUserMenu from '@/components/ChildUserMenu.vue'
import EmergencyBanner from '@/components/EmergencyBanner.vue'
import { request } from '@/utils/request'
import { addSocketListener, ensureSocket } from '@/utils/socket'
import { acknowledgeEmergenciesLocally, confirmEmergency, getVisibleEmergency } from '@/utils/emergency'
import { useFamilyMember } from '@/utils/familyMember'

const home = ref<any>(null)
const loading = ref(false)
let removeListener: null | (() => void) = null
const { familyUsername } = useFamilyMember()

type ElderAccount = {
  id: number
  username: string
}

const latestEmergency = computed(() => getVisibleEmergency(home.value?.emergencies))
const selectedElder = computed<ElderAccount | null>(() => {
  const elders = (home.value?.elders || []) as ElderAccount[]
  return elders.find((elder) => elder.username === familyUsername.value) || null
})
const weeklyReport = computed(() => {
  const reports = home.value?.weeklyReports || []
  const elderId = selectedElder.value?.id
  return elderId ? reports.find((item: any) => item?.elderId === elderId) || null : home.value?.weeklyReport || null
})
const recentStatuses = computed(() => {
  const list = home.value?.dailyStatuses || []
  const elderId = selectedElder.value?.id
  return (elderId ? list.filter((item: any) => item?.elderId === elderId) : list).slice(0, 3)
})
const weeklySummary = computed(() => {
  return weeklyReport.value?.summary || '还没有形成本周周报。等有新的近况、话题或回应后，这里会自动整理成一段给你看的家庭周报。'
})
const weekRangeText = computed(() => {
  const today = new Date()
  const day = today.getDay()
  const mondayOffset = day === 0 ? -6 : 1 - day
  const monday = new Date(today)
  monday.setDate(today.getDate() + mondayOffset)
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  return `${formatMonthDay(monday)} — ${formatMonthDay(sunday)}`
})

function dayText(index: number) {
  if (index === 0) return '今天'
  if (index === 1) return '昨天'
  if (index === 2) return '前天'
  return formatMonthDay(dateByIndex(index))
}

function weekdayText(index: number) {
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return weekdays[dateByIndex(index).getDay()] || ''
}

function dateByIndex(index: number) {
  const date = new Date()
  date.setDate(date.getDate() - index)
  return date
}

function formatMonthDay(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}.${day}`
}

function openSevenDays() {
  uni.navigateTo({ url: '/pages/child/status-seven' })
}

function statusMeta(item: any, index: number) {
  const displayDate = formatMonthDay(dateByIndex(index)).replace('.', '/')
  return [dayText(index), weekdayText(index), displayDate].filter(Boolean).join(' · ')
}

function openStatusDetail(item: any, index: number) {
  if (!item) return
  uni.setStorageSync('childDetail', {
    backText: '近况',
    title: item.title || `${dayText(index)}的近况`,
    tag: '日常近况',
    meta: statusMeta(item, index),
    content: item.content || '暂无近况内容。'
  })
  uni.navigateTo({ url: '/pages/child/detail' })
}

async function load() {
  loading.value = true
  try {
    home.value = await request({ url: '/home/child' })
  } catch (error: any) {
    uni.showToast({ title: error.message || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
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

onMounted(() => {
  load()
  ensureSocket()
  removeListener = addSocketListener((message) => {
    if (message.type === 'EMERGENCY_CREATED') {
      if (!home.value) home.value = {}
      if (!home.value.emergencies) home.value.emergencies = []
      home.value.emergencies.unshift(message.payload)
      uni.showToast({ title: '老人端触发了 SOS', icon: 'none' })
    }
  })
})

onUnmounted(() => {
  removeListener?.()
})
</script>

<template>
  <view class="status-page">
    <view class="nav-bar">
      <ChildFamilySwitcher />
      <ChildUserMenu />
    </view>

    <EmergencyBanner :event="latestEmergency" @close="dismissEmergency" />

    <view class="content">
      <view class="weekly-card" :class="{ empty: !weeklyReport }">
        <view class="card-head">
          <view class="card-title">周报</view>
          <view class="card-meta">{{ weekRangeText }}</view>
        </view>
        <view class="card-body">{{ weeklySummary }}</view>
      </view>

      <view class="recent-block">
        <view class="section-header">
          <view class="section-title">最近三天</view>
          <view class="section-more" hover-class="section-more-active" @click="openSevenDays">
            查看七天
          </view>
        </view>

        <view class="timeline-card" :class="{ empty: !recentStatuses.length }">
          <view
            v-for="(item, index) in recentStatuses"
            :key="item.id"
            class="timeline-row"
            :class="{ today: index === 0 }"
            hover-class="timeline-row-active"
            @tap="openStatusDetail(item, index)"
          >
            <view class="timeline-day">
              <view class="d1">{{ dayText(index) }}</view>
              <view class="d2">{{ weekdayText(index) }}</view>
            </view>
            <view class="timeline-text">{{ item.content }}</view>
          </view>
          <view v-if="!recentStatuses.length" class="empty-timeline">
            <view class="empty-title">暂无近况记录</view>
            <view class="empty-text">有新的日常记录后，会先显示最近三天。</view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="loading" class="loading">加载中...</view>
    <ChildTabBar active="status" />
  </view>
</template>

<style scoped lang="scss">
.status-page {
  height: 100vh;
  box-sizing: border-box;
  padding: 36rpx 32rpx 186rpx;
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
  padding: 14rpx 8rpx 12rpx;
  gap: 18rpx;
}

.content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
  margin-top: 24rpx;
}

.weekly-card {
  flex-shrink: 0;
  min-height: 300rpx;
  box-sizing: border-box;
  padding: 40rpx 44rpx;
  border-radius: 36rpx;
  background: #fff;
  display: flex;
  flex-direction: column;
}

.weekly-card.empty {
  background: #fff;
}

.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 28rpx;
}

.card-title {
  font-size: 44rpx;
  line-height: 1.2;
  font-weight: 700;
  color: #1c1c1e;
}

.card-meta {
  font-size: 26rpx;
  color: #8e8e93;
  font-weight: 500;
}

.card-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  font-size: 30rpx;
  line-height: 1.72;
  color: #1c1c1e;
}

.weekly-card.empty .card-body {
  color: #8e8e93;
}

.recent-block {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 8rpx 20rpx;
}

.section-title,
.section-more {
  font-size: 28rpx;
  line-height: 1.2;
  font-weight: 700;
  color: #1c1c1e;
}

.section-more {
  padding: 8rpx 0 8rpx 20rpx;
  color: #1c1c1e;
}

.section-more-active {
  opacity: 0.62;
}

.timeline-card {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border-radius: 32rpx;
  background: #fff;
  display: flex;
  flex-direction: column;
}

.timeline-card.empty {
  min-height: 260rpx;
}

.empty-timeline {
  flex: 1;
  min-height: 220rpx;
  box-sizing: border-box;
  padding: 36rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.empty-title {
  margin-bottom: 10rpx;
  color: #1c1c1e;
  font-size: 28rpx;
  line-height: 1.25;
  font-weight: 800;
}

.empty-text {
  color: #8e8e93;
  font-size: 26rpx;
  line-height: 1.45;
  font-weight: 500;
}

.timeline-row {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: flex-start;
  gap: 28rpx;
  padding: 22rpx 36rpx;
  box-sizing: border-box;
  overflow: hidden;
}

.timeline-row-active {
  background: #f8f8fa;
}

.timeline-row + .timeline-row::before {
  content: "";
  position: absolute;
  left: 124rpx;
  right: 0;
  top: 0;
  height: 1rpx;
  background: #e5e5ea;
}

.timeline-day {
  width: 88rpx;
  flex-shrink: 0;
  padding-top: 4rpx;
}

.d1 {
  font-size: 26rpx;
  line-height: 1.2;
  font-weight: 600;
  color: #1c1c1e;
}

.d2 {
  margin-top: 2rpx;
  font-size: 22rpx;
  color: #8e8e93;
}

.today .d1 {
  color: #30d158;
}

.timeline-text {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  line-height: 1.5;
  max-height: 84rpx;
  color: #3c3c43;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.loading {
  margin-top: 24rpx;
  color: #8e8e93;
  text-align: center;
}
</style>

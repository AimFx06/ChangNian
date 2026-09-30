<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import ChildTabBar from '@/components/ChildTabBar.vue'
import ChildFamilySwitcher from '@/components/ChildFamilySwitcher.vue'
import ChildUserMenu from '@/components/ChildUserMenu.vue'
import EmergencyBanner from '@/components/EmergencyBanner.vue'
import { request } from '@/utils/request'
import { addSocketListener, ensureSocket } from '@/utils/socket'
import { useFamilyMember } from '@/utils/familyMember'
import { acknowledgeEmergenciesLocally, confirmEmergency, getVisibleEmergency } from '@/utils/emergency'

type TimelineItem = {
  kind: string
  title: string
  desc: string
  date: string
  sortTime: number
}

type MonthGroup = {
  key: string
  title: string
  count: number
  tag: '本月' | '上月' | ''
  items: TimelineItem[]
}

function monthKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function parseDate(value?: string) {
  if (!value) return null
  const text = String(value).replace('T', ' ').trim()
  const match = text.match(/^(\d{4})-(\d{2})-(\d{2})(?:\s+(\d{2}):(\d{2})(?::(\d{2}))?)?/)
  if (match) {
    return new Date(
      Number(match[1]),
      Number(match[2]) - 1,
      Number(match[3]),
      Number(match[4] || 0),
      Number(match[5] || 0),
      Number(match[6] || 0)
    )
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function pad(num: number) {
  return String(num).padStart(2, '0')
}

function monthTitle(date: Date) {
  return `${date.getFullYear()}年${date.getMonth() + 1}月`
}

function timelineDate(date: Date, withTime = true) {
  const day = `${pad(date.getMonth() + 1)}.${pad(date.getDate())}`
  if (!withTime) return day
  return `${day} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const home = ref<any>(null)
const viewMode = ref<'memory' | 'data'>('memory')
const storyIndex = ref(0)
const calendarToday = ref(new Date())
const expandedMonth = ref(monthKey(calendarToday.value))
let removeListener: null | (() => void) = null
let calendarTimer: ReturnType<typeof setInterval> | null = null
const { familyName, familyUsername } = useFamilyMember()

type ElderAccount = {
  id: number
  username: string
  displayName: string
  phone?: string
}

const latestEmergency = computed(() => getVisibleEmergency(home.value?.emergencies))

const selectedElder = computed<ElderAccount | null>(() => {
  const elders = (home.value?.elders || []) as ElderAccount[]
  return elders.find((elder) => elder.username === familyUsername.value) || null
})

const memories = computed(() => {
  const list = home.value?.memories || []
  const elderId = selectedElder.value?.id
  return list.filter((item: any) => (item?.title || item?.content) && (!elderId || item?.elderId === elderId))
})

const responses = computed(() => {
  const list = home.value?.responses || []
  const elderId = selectedElder.value?.id
  return elderId ? list.filter((item: any) => item?.response?.elderId === elderId) : list
})

const meds = computed(() => {
  const list = home.value?.medications || []
  const elderId = selectedElder.value?.id
  return elderId ? list.filter((item: any) => item?.elderId === elderId) : list
})

const medicationRecords = computed(() => {
  const list = home.value?.medicationRecords || []
  const elderId = selectedElder.value?.id
  return elderId ? list.filter((item: any) => item?.elderId === elderId) : list
})

function isMissedMedication(record: any) {
  return ['MISSED', 'MISS', 'SKIPPED'].includes(String(record?.status || '').toUpperCase())
}

const medicationMissCount = computed(() => medicationRecords.value.filter(isMissedMedication).length)

const medicationTakenCount = computed(() => medicationRecords.value.filter((record: any) => !isMissedMedication(record)).length)

const medicationAdherence = computed(() => {
  const total = medicationRecords.value.length
  if (!total) return null
  return Math.round((medicationTakenCount.value / total) * 100)
})

const medicationDots = computed(() => {
  const records = medicationRecords.value.slice(0, 14)
  return Array.from({ length: 14 }, (_, index) => {
    const record = records[index]
    return {
      empty: !meds.value.length || !record,
      miss: !!record && isMissedMedication(record)
    }
  })
})

const medicalRecords = computed(() => {
  const list = home.value?.medicalRecords || []
  const elderId = selectedElder.value?.id
  return elderId ? list.filter((item: any) => item?.elderId === elderId) : list
})

const latestMedicalRecord = computed(() => medicalRecords.value[0] || null)

const nextVisitDate = computed(() => {
  const next = medicalRecords.value.find((item: any) => item?.recordType === '复查提醒')
  return parseDate(next?.createdAt)
})

const recentVisitRecord = computed(() => {
  return medicalRecords.value.find((item: any) => item?.recordType !== '复查提醒') || latestMedicalRecord.value
})

function formatTime(value?: string) {
  if (!value) return ''
  return String(value).slice(0, 5)
}

function belongsToCurrentMember(text: string) {
  const name = familyName.value
  if (!text) return true
  if (text.includes(name)) return true
  const otherName = name === '妈妈' ? '爸爸' : '妈妈'
  return !text.includes(otherName)
}

function monthTag(key: string): MonthGroup['tag'] {
  const current = monthKey(calendarToday.value)
  const lastMonth = new Date(calendarToday.value)
  lastMonth.setMonth(lastMonth.getMonth() - 1)
  if (key === current) return '本月'
  if (key === monthKey(lastMonth)) return '上月'
  return ''
}

const monthGroups = computed<MonthGroup[]>(() => {
  const groups = new Map<string, MonthGroup>()

  function addItem(date: Date | null, item: Omit<TimelineItem, 'date' | 'sortTime'>, withTime = true) {
    if (!date) return
    const key = monthKey(date)
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        title: monthTitle(date),
        count: 0,
        tag: monthTag(key),
        items: []
      })
    }
    const group = groups.get(key)
    if (!group) return
    group.items.push({
      ...item,
      date: timelineDate(date, withTime),
      sortTime: date.getTime()
    })
    group.count = group.items.length
  }

  ;(home.value?.dailyStatuses || []).forEach((status: any) => {
    if (selectedElder.value?.id && status?.elderId !== selectedElder.value.id) return
    const title = status?.title || '日常近况'
    const desc = status?.content || ''
    if (!belongsToCurrentMember(`${title} ${desc}`)) return
    addItem(parseDate(status?.statusDate), { kind: '近况', title, desc }, false)
  })

  ;(home.value?.topicCards || []).forEach((topic: any) => {
    if (selectedElder.value?.id && topic?.elderId !== selectedElder.value.id) return
    const title = topic?.title || topic?.category || '话题卡'
    const desc = topic?.content || topic?.sourceText || ''
    if (!belongsToCurrentMember(`${title} ${desc} ${topic?.sourceText || ''}`)) return
    addItem(parseDate(topic?.createdAt), { kind: '话题', title, desc })
  })

  ;(responses.value || []).forEach((entry: any) => {
    const response = entry?.response || {}
    const topic = entry?.topic
    const title = topic?.title || (response.sourceType === 'MICROPHONE' ? '回应了递话筒' : '主动留言')
    const desc = response.content || '这条回应包含一段录音。'
    addItem(parseDate(response.createdAt), {
      kind: response.sourceType === 'MICROPHONE' ? '回应' : '留言',
      title,
      desc
    })
  })

  if (!groups.size) {
    memories.value.forEach((memory: any) => {
      const title = memory?.title || '回忆'
      const desc = memory?.content || ''
      addItem(parseDate(memory?.timeText), { kind: '回忆', title, desc })
    })
  }

  return Array.from(groups.values())
    .map((group) => ({
      ...group,
      items: group.items.sort((a, b) => b.sortTime - a.sortTime).slice(0, 20),
      count: group.items.length
    }))
    .sort((a, b) => b.key.localeCompare(a.key))
})

async function load() {
  try {
    home.value = await request({ url: '/home/child' })
  } catch (error: any) {
    uni.showToast({ title: error.message || '加载失败', icon: 'none' })
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

function onStoryChange(event: any) {
  storyIndex.value = event.detail.current || 0
}

function selectStory(index: number) {
  storyIndex.value = index
}

function storyCount() {
  return 1
}

function storyTime(memory: any) {
  if (!memory?.timeText) return '最近刚刚'
  return String(memory.timeText).replace('T', ' ')
}

function openAllMemories() {
  uni.navigateTo({ url: '/pages/child/family-memories' })
}

function toggleMonth(key: string) {
  expandedMonth.value = expandedMonth.value === key ? '' : key
}

function openTimelineDetail(item: TimelineItem) {
  uni.setStorageSync('childDetail', {
    backText: '家',
    title: item.title,
    tag: item.kind,
    meta: item.date,
    content: item.desc || '暂无内容。'
  })
  uni.navigateTo({ url: '/pages/child/detail' })
}

function syncCalendarDate() {
  calendarToday.value = new Date()
}

onMounted(() => {
  syncCalendarDate()
  calendarTimer = setInterval(syncCalendarDate, 60 * 1000)
  load()
  ensureSocket()
  removeListener = addSocketListener((message) => {
    if (message.type === 'ELDER_RESPONSE_CREATED' || message.type === 'MEDICATION_RECORD_CREATED') {
      load()
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
  if (calendarTimer) clearInterval(calendarTimer)
})
</script>

<template>
  <view class="family-page">
    <view class="nav-bar">
      <ChildFamilySwitcher />
      <ChildUserMenu />
    </view>

    <EmergencyBanner :event="latestEmergency" @close="dismissEmergency" />

    <view class="view-toggle">
      <view class="seg" :class="{ active: viewMode === 'memory' }" @tap="viewMode = 'memory'">回忆</view>
      <view class="seg" :class="{ active: viewMode === 'data' }" @tap="viewMode = 'data'">数据</view>
    </view>

    <view class="content">
      <view v-if="viewMode === 'memory'" class="memory-content" :class="{ empty: !memories.length && !monthGroups.length }">
        <view class="stories" :class="{ empty: !memories.length }">
          <view class="section-header">
            <view class="section-title">{{ familyName }}说起过的事</view>
            <view class="section-more" hover-class="section-more-active" @tap="openAllMemories">全部</view>
          </view>

          <swiper v-if="memories.length" class="story-swiper" :current="storyIndex" :duration="260" @change="onStoryChange">
            <swiper-item v-for="(memory, index) in memories" :key="`${memory.title}-${index}`" class="story-slide">
              <view class="story-card">
                <view class="story-theme">{{ memory.title }}</view>
                <view class="story-quote">{{ memory.content }}</view>
                <view class="story-meta">
                  <text class="count">{{ storyCount(index) }} 次提到</text>
                  <text>·</text>
                  <text>{{ storyTime(memory) }}</text>
                </view>
              </view>
            </swiper-item>
          </swiper>
          <view v-else class="empty-card">
            <view class="empty-title">暂无回忆记录</view>
            <view class="empty-text">历史数据已清空，新的回应会在这里出现。</view>
          </view>

          <view v-if="memories.length > 1" class="story-dots">
            <view
              v-for="(_, index) in memories"
              :key="index"
              class="dot-button"
              @tap="selectStory(index)"
            >
              <view class="dot" :class="{ active: storyIndex === index }"></view>
            </view>
          </view>
        </view>

        <view class="timeline" :class="{ empty: !monthGroups.length }">
          <view class="section-header timeline-head">
            <view class="section-title">你们一起走过的日子</view>
          </view>

          <scroll-view v-if="monthGroups.length" scroll-y class="timeline-scroll">
            <view
              v-for="month in monthGroups"
              :key="month.key"
              class="month-group"
              :class="{ expanded: expandedMonth === month.key }"
            >
              <view class="month-header" @tap="toggleMonth(month.key)">
                <view class="month-title">
                  <text>{{ month.title }}</text>
                  <text v-if="month.tag" class="cur">{{ month.tag }}</text>
                </view>
                <view class="month-meta">
                  <text>{{ month.count }} 条</text>
                  <view class="month-chev"></view>
                </view>
              </view>

                  <view v-if="expandedMonth === month.key" class="month-body">
                    <view
                      v-for="item in month.items"
                      :key="`${item.kind}-${item.date}`"
                      class="tl-item"
                      hover-class="tl-item-active"
                      @tap="openTimelineDetail(item)"
                    >
                      <view class="tl-body">
                        <view class="tl-kind">{{ item.kind }}</view>
                        <view class="tl-title">{{ item.title }}</view>
                    <view class="tl-desc">{{ item.desc }}</view>
                  </view>
                  <view class="tl-date">{{ item.date }}</view>
                </view>
              </view>
            </view>
          </scroll-view>
          <view v-else class="empty-card timeline-empty">
            <view class="empty-title">暂无共同记录</view>
            <view class="empty-text">新的近况、话题和回应会按月份汇总到这里。</view>
          </view>
        </view>
      </view>

      <scroll-view v-else scroll-y class="data-scroll">
        <view class="cube-grid">
          <view class="cube">
            <view class="cube-head">
              <view class="cube-ic">
                <svg viewBox="0 0 16 16" fill="currentColor">
                  <path d="M8 14s-6-3.5-6-8a3.5 3.5 0 016-2.5A3.5 3.5 0 0114 6c0 4.5-6 8-6 8z" />
                </svg>
              </view>
              <view class="cube-title">健康速览</view>
            </view>
            <view class="cube-body">
              <view class="hc-main">
                {{ medicationAdherence === null ? '--' : medicationAdherence }}<text class="unit">{{ medicationAdherence === null ? '' : '%' }}</text>
              </view>
              <view class="hc-label">用药依从率</view>
              <view class="hc-sub">
                <view>
                  <view class="k">已记录</view>
                  <view class="v">{{ medicationRecords.length }} 次</view>
                </view>
                <view>
                  <view class="k">按时/稍后</view>
                  <view class="v">{{ medicationTakenCount }} 次</view>
                </view>
                <view>
                  <view class="k">漏服</view>
                  <view class="v">{{ medicationMissCount }} 次</view>
                </view>
              </view>
            </view>
          </view>

          <view class="cube">
            <view class="cube-head">
              <view class="cube-ic">
                <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="2" y="6" width="12" height="5" rx="2.5" transform="rotate(-30 8 8.5)" />
                </svg>
              </view>
              <view class="cube-title">用药管理</view>
            </view>
            <view class="cube-body">
              <view class="pill-big">{{ meds.length }}<text class="unit">种</text></view>
              <view class="pill-label">在服药物</view>
              <view class="pill-mini">
                <view
                  v-for="(dot, index) in medicationDots"
                  :key="index"
                  class="pill-dot"
                  :class="{ empty: dot.empty, miss: dot.miss }"
                ></view>
              </view>
              <view v-if="meds.length" class="pill-list">
                <view v-for="med in meds.slice(0, 2)" :key="med.id" class="pill-row">
                  <text>{{ med.medicineName }}</text>
                  <text>{{ formatTime(med.remindTime) }}</text>
                </view>
              </view>
              <view class="pill-foot">
                <text>{{ meds.length ? '本月' : '暂无用药' }}</text>
                <text class="hl">{{ meds.length ? `漏服 ${medicationMissCount} 次` : '待添加' }}</text>
              </view>
            </view>
          </view>

          <view class="cube">
            <view class="cube-head">
              <view class="cube-ic">
                <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M3 4h10v9a1 1 0 01-1 1H4a1 1 0 01-1-1V4zM6 2h4v2" />
                  <path d="M8 7v4M6 9h4" />
                </svg>
              </view>
              <view class="cube-title">就诊记录</view>
            </view>
            <view class="cube-body">
              <view class="visit-next">
                <view class="visit-day">{{ nextVisitDate ? pad(nextVisitDate.getDate()) : '--' }}</view>
                <view class="visit-month">{{ nextVisitDate ? `${nextVisitDate.getMonth() + 1}月` : '--' }}</view>
              </view>
              <view class="visit-label">下次复查</view>
              <view class="visit-name">{{ recentVisitRecord?.title || '暂无记录' }}</view>
              <view class="visit-sub">{{ recentVisitRecord?.recordType || '添加后会在这里显示' }}</view>
            </view>
          </view>

          <view class="cube">
            <view class="cube-head">
              <view class="cube-ic">
                <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M2 12l4-5 3 3 5-6" />
                </svg>
              </view>
              <view class="cube-title">更多数据</view>
            </view>
            <view class="more-cube-body">
              <view class="more-tile">
                <view class="mi">
                  <svg viewBox="0 0 18 18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M3 14l3-4 3 2 5-7" />
                  </svg>
                </view>
                <view class="ml">步数</view>
              </view>
              <view class="more-tile">
                <view class="mi">
                  <svg viewBox="0 0 18 18" fill="currentColor">
                    <path d="M9 16s-6-3.8-6-8.5A3.5 3.5 0 019 5a3.5 3.5 0 016 2.5C15 12.2 9 16 9 16z" />
                  </svg>
                </view>
                <view class="ml">心率</view>
              </view>
              <view class="more-tile">
                <view class="mi">
                  <svg viewBox="0 0 18 18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M14 10a5 5 0 11-6-7 4 4 0 006 7z" />
                  </svg>
                </view>
                <view class="ml">睡眠</view>
              </view>
              <view class="more-tile">
                <view class="mi">
                  <svg viewBox="0 0 18 18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="9" cy="9" r="6" />
                    <path d="M9 5v4l3 2" />
                  </svg>
                </view>
                <view class="ml">趋势</view>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <ChildTabBar active="family" />
  </view>
</template>

<style scoped lang="scss">
.family-page {
  height: 100vh;
  box-sizing: border-box;
  padding: 36rpx 0 198rpx;
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

.view-toggle {
  flex-shrink: 0;
  display: flex;
  margin: 16rpx 32rpx 28rpx;
  padding: 6rpx;
  border-radius: 20rpx;
  background: #e3e3e8;
}

.seg {
  flex: 1;
  height: 68rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8e8e93;
  font-size: 28rpx;
  line-height: 1;
  font-weight: 800;
}

.seg.active {
  background: #fff;
  color: #1c1c1e;
  box-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.04), 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
}

.content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.memory-content {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}

.memory-content.empty {
  gap: 26rpx;
}

.section-header {
  flex-shrink: 0;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 40rpx 20rpx;
}

.timeline-head {
  padding: 0 8rpx 20rpx;
}

.section-title {
  font-size: 28rpx;
  line-height: 1.2;
  font-weight: 800;
  color: #1c1c1e;
}

.section-more {
  padding: 8rpx 0 8rpx 20rpx;
  font-size: 28rpx;
  color: #1c1c1e;
  font-weight: 800;
}

.section-more-active {
  opacity: 0.62;
}

.stories,
.timeline {
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.stories {
  flex: 1;
}

.stories.empty {
  flex: 0 0 auto;
}

.story-swiper {
  flex: 1;
  min-height: 0;
  height: 100%;
  width: 100%;
}

.timeline-scroll::-webkit-scrollbar,
.data-scroll::-webkit-scrollbar {
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

.story-slide {
  height: 100%;
  padding: 4rpx 32rpx 8rpx;
  box-sizing: border-box;
}

.story-card {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  padding: 36rpx 40rpx;
  border-radius: 32rpx;
  background: #fff;
  white-space: normal;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.empty-card {
  flex: 1;
  margin: 4rpx 32rpx 8rpx;
  box-sizing: border-box;
  padding: 36rpx 40rpx;
  border-radius: 32rpx;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
}

.stories.empty .empty-card {
  flex: none;
  min-height: 520rpx;
  margin-bottom: 0;
}

.timeline-empty {
  margin: 0;
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

.story-theme {
  margin-bottom: 24rpx;
  font-size: 34rpx;
  line-height: 1.3;
  font-weight: 800;
  color: #1c1c1e;
}

.story-quote {
  flex: 1;
  min-height: 0;
  position: relative;
  padding-left: 32rpx;
  font-size: 28rpx;
  line-height: 1.65;
  color: #3c3c43;
  overflow: hidden;
}

.story-quote::before {
  content: "\201C";
  position: absolute;
  left: -4rpx;
  top: -18rpx;
  font-size: 64rpx;
  line-height: 1;
  color: #0a84ff;
  opacity: 0.5;
  font-family: Georgia, serif;
}

.story-meta {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 24rpx;
  font-size: 24rpx;
  color: #8e8e93;
  font-weight: 500;
}

.count {
  color: #0a84ff;
  font-weight: 800;
}

.story-dots {
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
  background: #0a84ff;
}

.timeline {
  flex: 1.618;
  padding: 0 32rpx;
}

.timeline.empty {
  flex: 0 0 auto;
  min-height: 0;
}

.timeline.empty .timeline-empty {
  flex: none;
  min-height: 340rpx;
  margin-bottom: 0;
}

.timeline-scroll {
  flex: 1;
  min-height: 0;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.month-group {
  margin-bottom: 20rpx;
}

.month-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 28rpx;
  border-radius: 24rpx;
  background: #fff;
}

.month-group.expanded .month-header {
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
}

.month-title {
  display: flex;
  align-items: center;
  min-width: 0;
  font-size: 28rpx;
  font-weight: 800;
  color: #1c1c1e;
}

.cur {
  flex-shrink: 0;
  margin-left: 16rpx;
  padding: 4rpx 14rpx;
  border-radius: 10rpx;
  background: rgba(10, 132, 255, 0.12);
  color: #0a84ff;
  font-size: 20rpx;
  font-weight: 800;
}

.month-meta {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 24rpx;
  color: #8e8e93;
}

.month-chev {
  width: 16rpx;
  height: 16rpx;
  margin-top: -4rpx;
  border-right: 3rpx solid #8e8e93;
  border-bottom: 3rpx solid #8e8e93;
  transform: rotate(45deg);
  transition: transform 0.2s ease;
}

.month-group.expanded .month-chev {
  transform: rotate(225deg);
  margin-top: 4rpx;
}

.month-body {
  padding: 8rpx 28rpx 20rpx;
  border-radius: 0 0 24rpx 24rpx;
  background: #fff;
}

.tl-item {
  display: flex;
  align-items: flex-start;
  gap: 24rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f0f3;
}

.tl-item-active {
  opacity: 0.68;
}

.tl-item:last-child {
  border-bottom: 0;
}

.tl-body {
  flex: 1;
  min-width: 0;
}

.tl-kind {
  margin-bottom: 6rpx;
  font-size: 20rpx;
  line-height: 1.2;
  font-weight: 800;
  color: #0a84ff;
}

.tl-title {
  margin-bottom: 4rpx;
  font-size: 26rpx;
  line-height: 1.4;
  font-weight: 800;
  color: #1c1c1e;
}

.tl-desc {
  font-size: 24rpx;
  line-height: 1.4;
  color: #8e8e93;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tl-date {
  flex-shrink: 0;
  padding-top: 28rpx;
  font-size: 22rpx;
  color: #c7c7cc;
  font-weight: 600;
}

.data-scroll {
  height: 100%;
  box-sizing: border-box;
  padding: 0 32rpx 24rpx;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.cube-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
}

.cube {
  aspect-ratio: 1;
  box-sizing: border-box;
  padding: 28rpx;
  border-radius: 32rpx;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.cube-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 20rpx;
}

.cube-ic {
  width: 44rpx;
  height: 44rpx;
  border-radius: 12rpx;
  background: rgba(10, 132, 255, 0.12);
  color: #0a84ff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cube-ic svg {
  width: 26rpx;
  height: 26rpx;
}

.cube-title {
  flex: 1;
  min-width: 0;
  font-size: 26rpx;
  line-height: 1.2;
  font-weight: 800;
  color: #1c1c1e;
}

.cube-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.hc-main,
.pill-big,
.visit-day {
  font-size: 64rpx;
  line-height: 1;
  font-weight: 800;
  color: #1c1c1e;
}

.unit {
  margin-left: 4rpx;
  font-size: 28rpx;
  font-weight: 500;
  color: #8e8e93;
}

.hc-label,
.pill-label,
.visit-label {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #8e8e93;
}

.hc-sub {
  display: flex;
  justify-content: space-between;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #f0f0f3;
}

.k {
  font-size: 20rpx;
  color: #8e8e93;
}

.v {
  margin-top: 2rpx;
  font-size: 24rpx;
  font-weight: 800;
  color: #1c1c1e;
}

.pill-mini {
  display: flex;
  gap: 8rpx;
  margin-top: 24rpx;
}

.pill-dot {
  flex: 1;
  height: 10rpx;
  border-radius: 999rpx;
  background: #34c759;
}

.pill-dot.miss {
  background: #fa114f;
}

.pill-dot.empty {
  background: #f0f0f3;
}

.pill-list {
  margin-top: 16rpx;
}

.pill-row {
  display: flex;
  justify-content: space-between;
  gap: 12rpx;
  margin-top: 8rpx;
  color: #3c3c43;
  font-size: 20rpx;
  line-height: 1.2;
  font-weight: 700;
}

.pill-row text:first-child {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pill-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #8e8e93;
}

.pill-foot .hl {
  color: #fa114f;
  font-weight: 800;
}

.visit-next {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}

.visit-month {
  font-size: 28rpx;
  font-weight: 800;
  color: #0a84ff;
}

.visit-name {
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #f0f0f3;
  font-size: 24rpx;
  line-height: 1.4;
  font-weight: 800;
  color: #1c1c1e;
}

.visit-sub {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #8e8e93;
}

.more-cube-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12rpx;
}

.more-tile {
  border-radius: 16rpx;
  background: #f8f8fa;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
}

.mi {
  color: #0a84ff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mi svg {
  width: 32rpx;
  height: 32rpx;
  display: block;
}

.ml {
  font-size: 20rpx;
  font-weight: 800;
  color: #1c1c1e;
}
</style>

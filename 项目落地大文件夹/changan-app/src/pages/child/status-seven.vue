<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { request } from '@/utils/request'
import { useFamilyMember } from '@/utils/familyMember'

const home = ref<any>(null)
const loading = ref(false)
const { familyUsername } = useFamilyMember()

const selectedElder = computed(() => {
  const elders = home.value?.elders || []
  return elders.find((elder: any) => elder.username === familyUsername.value) || null
})
const statuses = computed(() => {
  const list = home.value?.dailyStatuses || []
  const elderId = selectedElder.value?.id
  return (elderId ? list.filter((item: any) => item?.elderId === elderId) : list).slice(0, 7)
})
const sevenDayRows = computed(() =>
  Array.from({ length: 7 }, (_, index) => {
    const item = statuses.value[index]
    const date = dateByIndex(index)
    return {
      id: item?.id || `empty-${index}`,
      day: dayText(index),
      weekday: weekdayText(date),
      date: formatDate(date),
      fullDate: formatFullDate(date),
      content: item?.content || '暂无新的近况记录。',
      title: item?.title || `${dayText(index)}的近况`,
      empty: !item
    }
  })
)

function dayText(index: number) {
  if (index === 0) return '今天'
  if (index === 1) return '昨天'
  if (index === 2) return '前天'
  return `${index + 1}天前`
}

function weekdayText(date: Date) {
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return weekdays[date.getDay()] || ''
}

function dateByIndex(index: number) {
  const date = new Date()
  date.setDate(date.getDate() - index)
  return date
}

function formatDate(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}/${day}`
}

function formatFullDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.reLaunch({ url: '/pages/child/status' })
}

function openDayDetail(item: any) {
  if (!item || item.empty) return
  uni.setStorageSync('childDetail', {
    backText: '近况',
    title: item.title,
    tag: '日常近况',
    meta: [item.day, item.weekday, item.fullDate].filter(Boolean).join(' · '),
    content: item.content
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

onMounted(() => {
  load()
})
</script>

<template>
  <view class="seven-page">
    <view class="nav-bar">
      <view class="back-area" @tap="goBack">
        <view class="back-icon"></view>
        <text>近况</text>
      </view>
    </view>

    <scroll-view scroll-y class="day-scroll">
      <view class="day-card">
        <view
          v-for="(item, index) in sevenDayRows"
          :key="item.id"
          class="day-row"
          :class="{ today: index === 0, empty: item.empty }"
          hover-class="day-row-active"
          @tap="openDayDetail(item)"
        >
          <view class="day-side">
            <view class="day-name">{{ item.day }}</view>
            <view class="weekday">{{ item.weekday }}</view>
          </view>
          <view class="day-main">
            <view class="date-text">{{ item.date }}</view>
            <view class="content-text">{{ item.content }}</view>
          </view>
        </view>
      </view>
    </scroll-view>

    <view v-if="loading" class="loading">加载中...</view>
  </view>
</template>

<style scoped lang="scss">
.seven-page {
  height: 100vh;
  box-sizing: border-box;
  padding: 36rpx 32rpx 32rpx;
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
  padding: 14rpx 8rpx 12rpx;
}

.back-area {
  justify-self: start;
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
  min-height: 56rpx;
  padding-right: 20rpx;
  font-size: 28rpx;
  line-height: 1.2;
  font-weight: 700;
  color: #1c1c1e;
}

.back-icon {
  width: 18rpx;
  height: 18rpx;
  border-left: 4rpx solid currentColor;
  border-bottom: 4rpx solid currentColor;
  transform: rotate(45deg);
}

.day-scroll {
  flex: 1;
  min-height: 0;
  margin-top: 16rpx;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.day-scroll::-webkit-scrollbar {
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

.day-card {
  border-radius: 32rpx;
  background: #fff;
  overflow: hidden;
}

.day-row {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 28rpx;
  min-height: 132rpx;
  padding: 26rpx 32rpx;
  box-sizing: border-box;
  overflow: hidden;
}

.day-row-active {
  background: #f8f8fa;
}

.day-row + .day-row::before {
  content: "";
  position: absolute;
  left: 136rpx;
  right: 0;
  top: 0;
  height: 1rpx;
  background: #e5e5ea;
}

.day-side {
  width: 84rpx;
  flex-shrink: 0;
}

.day-name {
  font-size: 28rpx;
  line-height: 1.2;
  font-weight: 800;
  color: #1c1c1e;
}

.weekday {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #8e8e93;
}

.today .day-name {
  color: #30d158;
}

.day-main {
  flex: 1;
  min-width: 0;
}

.date-text {
  margin-bottom: 8rpx;
  font-size: 22rpx;
  line-height: 1.2;
  font-weight: 700;
  color: #8e8e93;
}

.content-text {
  font-size: 28rpx;
  line-height: 1.5;
  color: #3c3c43;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.empty .content-text {
  color: #8e8e93;
}

.loading {
  flex-shrink: 0;
  margin-top: 20rpx;
  color: #8e8e93;
  text-align: center;
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { request } from '@/utils/request'
import { useFamilyMember } from '@/utils/familyMember'

const home = ref<any>(null)
const loading = ref(false)
const { familyName, familyUsername } = useFamilyMember()

type ElderAccount = {
  id: number
  username: string
  displayName: string
  phone?: string
}

const selectedElder = computed<ElderAccount | null>(() => {
  const elders = (home.value?.elders || []) as ElderAccount[]
  return elders.find((elder) => elder.username === familyUsername.value) || null
})

const memories = computed(() => {
  const list = home.value?.memories || []
  const elderId = selectedElder.value?.id
  return list.filter((item: any) => (item?.title || item?.content) && (!elderId || item?.elderId === elderId))
})

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

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.reLaunch({ url: '/pages/child/family' })
}

function storyTime(memory: any) {
  if (!memory?.timeText) return '最近刚刚'
  return String(memory.timeText).replace('T', ' ')
}

onMounted(() => {
  load()
})
</script>

<template>
  <view class="memories-page">
    <view class="nav-bar">
      <view class="back-area" @tap="goBack">
        <view class="back-icon"></view>
        <text>家</text>
      </view>
      <view class="nav-title">全部说过的</view>
    </view>

    <view class="summary-line">{{ familyName }} · {{ memories.length }} 则</view>

    <scroll-view scroll-y class="memory-scroll">
      <view v-if="memories.length" class="memory-list">
        <view v-for="(memory, index) in memories" :key="`${memory.title}-${index}`" class="memory-row">
          <view class="memory-title">{{ memory.title || '日常回应' }}</view>
          <view class="memory-content">{{ memory.content }}</view>
          <view class="memory-meta">
            <text>1 次提到</text>
            <text>·</text>
            <text>{{ storyTime(memory) }}</text>
          </view>
        </view>
      </view>

      <view v-else class="empty-card">
        <view class="empty-title">暂无说过的内容</view>
        <view class="empty-text">新的回应和回忆会按时间汇总到这里。</view>
      </view>
    </scroll-view>

    <view v-if="loading" class="loading">加载中...</view>
  </view>
</template>

<style scoped lang="scss">
.memories-page {
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
  display: grid;
  grid-template-columns: 120rpx 1fr 120rpx;
  align-items: center;
  padding: 14rpx 0 12rpx;
}

.back-area {
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
  min-height: 56rpx;
  font-size: 28rpx;
  line-height: 1.2;
  font-weight: 800;
  color: #1c1c1e;
}

.back-icon {
  width: 18rpx;
  height: 18rpx;
  border-left: 4rpx solid currentColor;
  border-bottom: 4rpx solid currentColor;
  transform: rotate(45deg);
}

.nav-title {
  text-align: center;
  font-size: 30rpx;
  line-height: 1.2;
  font-weight: 900;
}

.summary-line {
  flex-shrink: 0;
  padding: 20rpx 0 24rpx;
  color: #8e8e93;
  font-size: 26rpx;
  font-weight: 700;
}

.memory-scroll {
  flex: 1;
  min-height: 0;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.memory-scroll::-webkit-scrollbar {
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

.memory-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.memory-row,
.empty-card {
  box-sizing: border-box;
  padding: 30rpx 34rpx;
  border-radius: 28rpx;
  background: #fff;
}

.memory-title {
  margin-bottom: 16rpx;
  font-size: 30rpx;
  line-height: 1.25;
  font-weight: 900;
}

.memory-content {
  color: #3c3c43;
  font-size: 28rpx;
  line-height: 1.55;
}

.memory-meta {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-top: 20rpx;
  color: #8e8e93;
  font-size: 24rpx;
  font-weight: 600;
}

.empty-card {
  min-height: 320rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
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

.loading {
  flex-shrink: 0;
  margin-top: 20rpx;
  color: #8e8e93;
  text-align: center;
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type DetailPayload = {
  backText?: string
  title?: string
  tag?: string
  meta?: string
  content?: string
  sourceTitle?: string
  sourceText?: string
}

const detail = ref<DetailPayload>({})

const title = computed(() => detail.value.title || '内容详情')
const content = computed(() => detail.value.content || '暂无内容。')
const backText = computed(() => detail.value.backText || '返回')

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.reLaunch({ url: '/pages/child/status' })
}

onMounted(() => {
  detail.value = uni.getStorageSync('childDetail') || {}
})
</script>

<template>
  <view class="detail-page">
    <view class="nav-bar">
      <view class="back-area" @tap="goBack">
        <view class="back-icon"></view>
        <text>{{ backText }}</text>
      </view>
    </view>

    <scroll-view scroll-y class="detail-scroll">
      <view class="detail-card">
        <view v-if="detail.tag" class="detail-tag">{{ detail.tag }}</view>
        <view class="detail-title">{{ title }}</view>
        <view v-if="detail.meta" class="detail-meta">{{ detail.meta }}</view>
        <view class="detail-content">{{ content }}</view>
      </view>

      <view v-if="detail.sourceTitle || detail.sourceText" class="source-card">
        <view class="source-label">关联话题</view>
        <view v-if="detail.sourceTitle" class="source-title">{{ detail.sourceTitle }}</view>
        <view v-if="detail.sourceText" class="source-text">{{ detail.sourceText }}</view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped lang="scss">
.detail-page {
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
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
  min-height: 56rpx;
  padding-right: 20rpx;
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

.detail-scroll {
  flex: 1;
  min-height: 0;
  margin-top: 18rpx;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.detail-scroll::-webkit-scrollbar {
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

.detail-card,
.source-card {
  box-sizing: border-box;
  padding: 36rpx 38rpx;
  border-radius: 30rpx;
  background: #fff;
}

.detail-tag {
  display: inline-flex;
  align-items: center;
  min-height: 38rpx;
  padding: 0 18rpx;
  margin-bottom: 18rpx;
  border-radius: 999rpx;
  background: rgba(0, 122, 255, 0.1);
  color: #007aff;
  font-size: 22rpx;
  line-height: 1.2;
  font-weight: 800;
}

.detail-title {
  font-size: 40rpx;
  line-height: 1.28;
  font-weight: 900;
  color: #1c1c1e;
}

.detail-meta {
  margin-top: 18rpx;
  color: #8e8e93;
  font-size: 24rpx;
  line-height: 1.35;
  font-weight: 700;
}

.detail-content {
  margin-top: 30rpx;
  color: #2c2c2e;
  font-size: 32rpx;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.source-card {
  margin-top: 22rpx;
}

.source-label {
  margin-bottom: 14rpx;
  color: #8e8e93;
  font-size: 22rpx;
  line-height: 1.2;
  font-weight: 800;
}

.source-title {
  color: #1c1c1e;
  font-size: 28rpx;
  line-height: 1.35;
  font-weight: 900;
}

.source-text {
  margin-top: 10rpx;
  color: #3c3c43;
  font-size: 26rpx;
  line-height: 1.55;
}
</style>

<script setup lang="ts">
const props = defineProps<{
  active: 'status' | 'topics' | 'family'
}>()

const tabs = [
  { key: 'status', label: '近况', url: '/pages/child/status', color: '#30D158' },
  { key: 'topics', label: '话题', url: '/pages/child/topics', color: '#FF9500' },
  { key: 'family', label: '家', url: '/pages/child/family', color: '#0A84FF' }
] as const

function go(url: string) {
  uni.reLaunch({ url })
}
</script>

<template>
  <view class="tab-bar">
    <view
      v-for="tab in tabs"
      :key="tab.key"
      class="tab"
      :class="{ active: props.active === tab.key }"
      :style="{ color: props.active === tab.key ? tab.color : '#8E8E93' }"
      @tap="go(tab.url)"
    >
      <view class="icon">
        <svg v-if="tab.key === 'status'" class="tab-svg" viewBox="0 0 28 28" fill="currentColor">
          <path d="M14 3.5L3 12.5V24a1.5 1.5 0 001.5 1.5h6v-8h7v8h6A1.5 1.5 0 0024 24V12.5L14 3.5z" />
        </svg>
        <svg v-else-if="tab.key === 'topics'" class="tab-svg" viewBox="0 0 28 28" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M6 10c0-2.2 1.8-4 4-4h8c2.2 0 4 1.8 4 4v5c0 2.2-1.8 4-4 4h-5l-5 4v-4c-1.2 0-2-.8-2-2v-7z" />
        </svg>
        <svg v-else class="tab-svg" viewBox="0 0 28 28" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M4 13L14 4l10 9v10a1.5 1.5 0 01-1.5 1.5h-17A1.5 1.5 0 014 23V13z" />
          <path d="M10 24v-6h8v6" />
        </svg>
      </view>
      <text>{{ tab.label }}</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.tab-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  box-sizing: border-box;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  height: 166rpx;
  padding: 14rpx 0 54rpx;
  border-top: 1rpx solid #e5e5ea;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: saturate(180%) blur(30px);
}

.tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
  font-size: 20rpx;
  font-weight: 600;
  color: #8e8e93;
}

.icon {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tab-svg {
  width: 52rpx;
  height: 52rpx;
  display: block;
}
</style>

<script setup lang="ts">
defineProps<{
  event?: any
}>()

const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <view v-if="event" class="sos-layer">
    <view class="banner">
      <view class="icon">SOS</view>
      <view class="content">
        <view class="title">老人端 SOS 已触发</view>
        <view class="desc">{{ event.locationText || '暂未获取位置' }}</view>
        <view class="note">{{ event.note || '请尽快联系家人确认情况' }}</view>
      </view>
      <button class="status" @tap.stop="emit('close')">已确认</button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.sos-layer {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
  box-sizing: border-box;
  pointer-events: none;
}

.banner {
  width: 100%;
  max-width: 620rpx;
  box-sizing: border-box;
  padding: 34rpx 32rpx 30rpx;
  border-radius: 28rpx;
  background: rgba(255, 241, 240, 0.96);
  border: 1rpx solid rgba(215, 0, 21, 0.18);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
  box-shadow: 0 28rpx 80rpx rgba(176, 0, 32, 0.22);
  backdrop-filter: blur(20px);
  pointer-events: auto;
}

.icon {
  width: 92rpx;
  height: 92rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #d70015;
  color: #fff;
  font-size: 24rpx;
  line-height: 1;
  font-weight: 900;
  box-shadow: 0 12rpx 28rpx rgba(215, 0, 21, 0.28);
}

.content {
  width: 100%;
  text-align: center;
}

.title {
  font-size: 32rpx;
  line-height: 1.25;
  font-weight: 900;
  color: #b00020;
}

.desc {
  margin-top: 14rpx;
  font-size: 24rpx;
  color: #7a1f1f;
  line-height: 1.45;
}

.note {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #7a1f1f;
  line-height: 1.45;
}

.status {
  width: 220rpx;
  height: 68rpx;
  line-height: 68rpx;
  margin: 0;
  padding: 0;
  border-radius: 999rpx;
  background: #d70015;
  color: #fff;
  font-size: 26rpx;
  font-weight: 800;
  border: 0;
}

.status::after {
  border: 0;
}
</style>

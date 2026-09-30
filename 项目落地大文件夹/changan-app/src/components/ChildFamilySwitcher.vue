<script setup lang="ts">
import { ref } from 'vue'
import { FamilyMember, useFamilyMember } from '@/utils/familyMember'

const menuOpen = ref(false)
const { familyName, familyOptions, selectFamilyMember } = useFamilyMember()

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function closeMenu() {
  menuOpen.value = false
}

function chooseMember(value: FamilyMember) {
  selectFamilyMember(value)
  closeMenu()
}
</script>

<template>
  <view class="family-switcher">
    <text class="family-name">{{ familyName }}</text>
    <view class="chevron-button" :class="{ open: menuOpen }" @tap.stop="toggleMenu">
      <view class="chevron"></view>
    </view>

    <view v-if="menuOpen" class="switcher-scrim" @tap="closeMenu"></view>
    <view v-if="menuOpen" class="member-menu">
      <view
        v-for="option in familyOptions"
        :key="option"
        class="member-option"
        :class="{ active: option === familyName }"
        @tap.stop="chooseMember(option)"
      >
        <text>{{ option }}</text>
        <text v-if="option === familyName" class="check">✓</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.family-switcher {
  position: relative;
  z-index: 50;
  display: flex;
  align-items: center;
  gap: 12rpx;
  min-height: 72rpx;
}

.family-name {
  font-size: 40rpx;
  line-height: 1;
  font-weight: 700;
  color: #1c1c1e;
}

.chevron-button {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chevron {
  width: 18rpx;
  height: 18rpx;
  margin-top: -4rpx;
  border-right: 4rpx solid #8e8e93;
  border-bottom: 4rpx solid #8e8e93;
  transform: rotate(45deg);
  transition: transform 0.18s ease, margin-top 0.18s ease;
}

.chevron-button.open .chevron {
  margin-top: 6rpx;
  transform: rotate(225deg);
}

.switcher-scrim {
  position: fixed;
  inset: 0;
  z-index: 48;
}

.member-menu {
  position: absolute;
  left: 0;
  top: 72rpx;
  z-index: 52;
  width: 180rpx;
  box-sizing: border-box;
  padding: 10rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.96);
  border: 1rpx solid #e5e5ea;
  box-shadow: 0 18rpx 42rpx rgba(0, 0, 0, 0.12);
  backdrop-filter: blur(20px);
}

.member-option {
  height: 64rpx;
  box-sizing: border-box;
  padding: 0 18rpx;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #1c1c1e;
  font-size: 28rpx;
  font-weight: 700;
}

.member-option.active {
  background: #f2f2f7;
  color: #0a84ff;
}

.check {
  font-size: 24rpx;
  font-weight: 900;
}
</style>

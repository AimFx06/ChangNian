import { computed, ref } from 'vue'

export type FamilyMember = '妈妈' | '爸爸'

const STORAGE_KEY = 'childSelectedFamilyMember'
const familyOptions: FamilyMember[] = ['妈妈', '爸爸']
const familyUsernames: Record<FamilyMember, 'mother' | 'father'> = {
  妈妈: 'mother',
  爸爸: 'father'
}
const familyName = ref<FamilyMember>('妈妈')
const familyUsername = computed(() => familyMemberToUsername(familyName.value))
let initialized = false

function isFamilyMember(value: unknown): value is FamilyMember {
  return value === '妈妈' || value === '爸爸'
}

export function familyMemberToUsername(value: FamilyMember) {
  return familyUsernames[value]
}

function initFamilyMember() {
  if (initialized) return
  initialized = true
  const saved = readFamilyMember()
  if (isFamilyMember(saved)) {
    familyName.value = saved
  }
}

function readFamilyMember() {
  const uniValue = uni.getStorageSync(STORAGE_KEY)
  if (isFamilyMember(uniValue)) return uniValue

  if (typeof window !== 'undefined') {
    return window.localStorage.getItem(STORAGE_KEY)
  }

  return null
}

function saveFamilyMember(value: FamilyMember) {
  uni.setStorageSync(STORAGE_KEY, value)
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(STORAGE_KEY, value)
  }
}

export function useFamilyMember() {
  initFamilyMember()

  function selectFamilyMember(value: FamilyMember) {
    familyName.value = value
    saveFamilyMember(value)
  }

  function toggleFamilyMember() {
    selectFamilyMember(familyName.value === '妈妈' ? '爸爸' : '妈妈')
  }

  return {
    familyName,
    familyUsername,
    familyOptions,
    selectFamilyMember,
    toggleFamilyMember
  }
}

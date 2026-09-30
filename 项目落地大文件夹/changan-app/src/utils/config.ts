function defaultApiHost() {
  if (typeof window !== 'undefined') {
    const host = window.location.hostname
    if (host && host !== 'localhost' && host !== '127.0.0.1') {
      return `http://${host}:8080`
    }
  }
  return 'http://127.0.0.1:8080'
}

const API_HOST_KEY = 'CHANGNIAN_API_HOST'

function normalizeApiHost(value: string) {
  const trimmed = value.trim().replace(/\/+$/, '')
  if (!trimmed) return ''
  return /^https?:\/\//i.test(trimmed) ? trimmed : `http://${trimmed}`
}

export function getApiHost() {
  const saved = uni.getStorageSync(API_HOST_KEY)
  return normalizeApiHost(saved || import.meta.env.VITE_API_HOST || defaultApiHost())
}

export function saveApiHost(value: string) {
  const host = normalizeApiHost(value)
  if (!host) {
    uni.removeStorageSync(API_HOST_KEY)
    return defaultApiHost()
  }
  uni.setStorageSync(API_HOST_KEY, host)
  return host
}

export function getApiBase() {
  return `${getApiHost()}/api`
}

export function getWsBase() {
  return `${getApiHost().replace(/^http/, 'ws')}/ws`
}

export function getFileBase() {
  return getApiHost()
}

export const API_BASE = getApiBase()
export const WS_BASE = getWsBase()
export const FILE_BASE = getFileBase()

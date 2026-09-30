import { request } from './request'

type EmergencyEvent = {
  id?: number | string
  status?: string
}

const ACKNOWLEDGED_KEY = 'acknowledgedEmergencyIds'

function emergencyId(event?: EmergencyEvent | null) {
  if (!event || event.id == null) return ''
  return String(event.id)
}

function readAcknowledgedIds() {
  const value = uni.getStorageSync(ACKNOWLEDGED_KEY)
  if (Array.isArray(value)) {
    return value.map(String)
  }
  if (typeof value !== 'string' || !value) {
    return []
  }
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return []
  }
}

function writeAcknowledgedIds(ids: string[]) {
  uni.setStorageSync(ACKNOWLEDGED_KEY, Array.from(new Set(ids)).slice(-50))
}

export function acknowledgeEmergencyLocally(event?: EmergencyEvent | null) {
  const id = emergencyId(event)
  if (!id) return
  const ids = readAcknowledgedIds()
  if (!ids.includes(id)) {
    writeAcknowledgedIds([...ids, id])
  }
}

export function acknowledgeEmergenciesLocally(events?: EmergencyEvent[]) {
  const ids = readAcknowledgedIds()
  const nextIds = [...ids]
  ;(events || []).forEach((event) => {
    const id = emergencyId(event)
    if (id && !nextIds.includes(id)) {
      nextIds.push(id)
    }
  })
  writeAcknowledgedIds(nextIds)
}

export function isEmergencyAcknowledged(event?: EmergencyEvent | null) {
  if (!event) return true
  if (event.status && event.status !== 'ACTIVE') return true
  const id = emergencyId(event)
  return !!id && readAcknowledgedIds().includes(id)
}

export function getVisibleEmergency(events?: EmergencyEvent[]) {
  return (events || []).find((event) => !isEmergencyAcknowledged(event)) || null
}

export async function confirmEmergency(event?: EmergencyEvent | null) {
  const id = emergencyId(event)
  acknowledgeEmergencyLocally(event)
  if (!id) return
  try {
    await request({
      url: `/emergencies/${id}/confirm`,
      method: 'POST'
    })
  } catch (error) {
    console.warn('confirm emergency failed', error)
  }
}

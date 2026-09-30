import { getWsBase } from './config'
import { getToken } from './request'

export interface SocketMessage<T = any> {
  type: string
  payload: T
}

type Listener = (message: SocketMessage) => void

let opened = false
let connecting = false
const listeners = new Set<Listener>()

export function addSocketListener(listener: Listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function ensureSocket() {
  const token = getToken()
  if (!token || opened || connecting) {
    return
  }
  connecting = true
  uni.connectSocket({
    url: `${getWsBase()}?token=${encodeURIComponent(token)}`
  })
  uni.onSocketOpen(() => {
    opened = true
    connecting = false
  })
  uni.onSocketClose(() => {
    opened = false
    connecting = false
  })
  uni.onSocketError(() => {
    opened = false
    connecting = false
  })
  uni.onSocketMessage((event) => {
    try {
      const message = JSON.parse(String(event.data)) as SocketMessage
      listeners.forEach((listener) => listener(message))
    } catch (error) {
      console.warn('socket message parse failed', error)
    }
  })
}

export function closeSocket() {
  if (!opened && !connecting) {
    return
  }
  uni.closeSocket()
  opened = false
  connecting = false
}

import { getApiBase } from './config'

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

interface RequestOptions {
  url: string
  method?: Method
  data?: unknown
}

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export function getToken(): string {
  return uni.getStorageSync('token') || ''
}

export function getUser<T = any>(): T | null {
  const user = uni.getStorageSync('user')
  return user || null
}

export function saveSession(token: string, user: unknown) {
  uni.setStorageSync('token', token)
  uni.setStorageSync('user', user)
}

export function clearSession() {
  uni.removeStorageSync('token')
  uni.removeStorageSync('user')
}

export function request<T>(options: RequestOptions): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = getToken()
    uni.request({
      url: `${getApiBase()}${options.url}`,
      method: options.method || 'GET',
      data: options.data as any,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success: (res) => {
        const body = res.data as ApiResponse<T>
        if (res.statusCode === 401) {
          clearSession()
          uni.reLaunch({ url: '/pages/login/index' })
          reject(new Error('请先登录'))
          return
        }
        if (!body || body.success !== true) {
          reject(new Error(body?.message || '请求失败'))
          return
        }
        resolve(body.data)
      },
      fail: () => reject(new Error('无法连接后端服务'))
    })
  })
}

export function uploadFile(path: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const token = getToken()
    uni.uploadFile({
      url: `${getApiBase()}/files/upload`,
      filePath: path,
      name: 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        const body = JSON.parse(res.data) as ApiResponse<{ url: string }>
        if (!body.success) {
          reject(new Error(body.message))
          return
        }
        resolve(body.data.url)
      },
      fail: () => reject(new Error('上传失败'))
    })
  })
}

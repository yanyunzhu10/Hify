import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

const instance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

instance.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      // PageResult：保留分页元数据，映射为前端 PageResult 形态
      if (res.total !== undefined && res.page !== undefined) {
        return { records: res.data ?? [], total: res.total, page: res.page, size: res.size }
      }
      return res.data
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    const msg = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

export const get = <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
  instance.get(url, config)

export const post = <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
  instance.post(url, data, config)

export const put = <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
  instance.put(url, data, config)

export const del = <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
  instance.delete(url, config)

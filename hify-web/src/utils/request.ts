import axios, { type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { code, message, data } = response.data
    if (code === 200) {
      return data
    }
    ElMessage.error(message || '请求失败')
    return Promise.reject(new Error(message || '请求失败'))
  },
  (error) => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export const get = <T = any>(url: string, params?: any): Promise<T> => {
  return request.get(url, { params }) as Promise<T>
}

export const post = <T = any>(url: string, data?: any): Promise<T> => {
  return request.post(url, data) as Promise<T>
}

export const put = <T = any>(url: string, data?: any): Promise<T> => {
  return request.put(url, data) as Promise<T>
}

export const del = <T = any>(url: string, params?: any): Promise<T> => {
  return request.delete(url, { params }) as Promise<T>
}

export const patch = <T = any>(url: string, data?: any): Promise<T> => {
  return request.patch(url, data) as Promise<T>
}

export default request

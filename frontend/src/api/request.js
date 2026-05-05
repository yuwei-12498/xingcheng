import axios from 'axios'
import { ElMessage } from 'element-plus'
import { API_BASE_URL } from './baseUrl'

const service = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  withCredentials: true
})

function redirectToLogin() {
  if (window.location.pathname === '/auth') {
    return
  }

  const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
  const nextUrl = redirect && redirect !== '/auth'
    ? `/auth?redirect=${encodeURIComponent(redirect)}`
    : '/auth'
  window.location.assign(nextUrl)
}

service.interceptors.request.use(config => {
  const token = localStorage.getItem('jwt_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

service.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API Error: ', error)
    const skipErrorMessage = Boolean(error.config && error.config.skipErrorMessage)
    const skipAuthRedirect = Boolean(error.config && error.config.skipAuthRedirect)
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '\u670D\u52A1\u5F02\u5E38'

    error.code = status || error.code

    if (status === 401) {
      localStorage.removeItem('jwt_token')
      if (!skipAuthRedirect) {
        redirectToLogin()
      }
    }

    if (!skipErrorMessage && status !== 401) {
      if (status) {
        ElMessage.error(message)
      } else {
        ElMessage.error('\u7F51\u7EDC\u8FDE\u63A5\u5F02\u5E38\u6216\u540E\u7AEF\u670D\u52A1\u672A\u542F\u52A8')
      }
    }

    return Promise.reject(error)
  }
)

export default service

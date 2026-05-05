import request from './request'

export function reqRegister(data) {
  return request({
    url: '/users',
    method: 'post',
    data
  })
}

export function reqSendRegisterCode(email) {
  return request({
    url: '/users/send-code',
    method: 'post',
    data: { email }
  })
}

export function reqSendPasswordResetCode(email) {
  return request({
    url: '/auth/password/send-code',
    method: 'post',
    data: { email }
  })
}

export function reqResetPassword(data) {
  return request({
    url: '/auth/password/reset',
    method: 'post',
    data
  })
}

export function reqLogin(data) {
  return request({
    url: '/sessions',
    method: 'post',
    data
  })
}

export function reqLogout() {
  return request({
    url: '/sessions/current',
    method: 'delete'
  })
}

export function reqCurrentUser() {
  return request({
    url: '/users/me',
    method: 'get',
    skipErrorMessage: true,
    skipAuthRedirect: true
  })
}

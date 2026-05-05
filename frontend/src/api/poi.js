import request from './request'

export function reqGetPoiList() {
  return request({
    url: '/pois',
    method: 'get'
  })
}

export function reqGetPoiDetail(id, tripDate) {
  return request({
    url: `/pois/${id}`,
    method: 'get',
    params: tripDate ? { tripDate } : {}
  })
}

export function reqSearchPoi(keyword, city, limit = 8) {
  return request({
    url: '/pois/search',
    method: 'get',
    params: { keyword, city, limit }
  })
}

export function reqListCustomPois() {
  return request({
    url: '/custom-pois',
    method: 'get',
    skipErrorMessage: true
  })
}

import request from './request'

export function reqAdminCommunityPosts(params = {}) {
  return request({
    url: '/admin/community/posts',
    method: 'get',
    params
  })
}

export function reqAdminCommunityPin(id, pinned) {
  return request({
    url: `/admin/community/posts/${id}/pin`,
    method: 'patch',
    params: { pinned }
  })
}

export function reqAdminCommunityDelete(id) {
  return request({
    url: `/admin/community/posts/${id}`,
    method: 'delete'
  })
}
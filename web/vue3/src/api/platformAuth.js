import { httpGet, httpPost } from './http'

export function login(username, password) {
  return httpPost('/v1/platform/auth/login', { username, password })
}

export function me() {
  return httpGet('/v1/platform/auth/me')
}

export function register(username, password) {
  return httpPost('/v1/platform/auth/register', { username, password })
}

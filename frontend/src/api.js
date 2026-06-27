// Centralized auth + fetch wrapper.
// - token() reads current access token from localStorage
// - hdr() builds the Authorization header
// - apiFetch() wraps fetch; on 401 it transparently refreshes the access token
//   (using the stored refresh token) and retries the request once.
//   If refresh fails, it clears the session and redirects to /login.

export function token() {
  return localStorage.getItem('fin_token') || ''
}

export function refreshToken() {
  return localStorage.getItem('fin_refresh') || ''
}

export function hdr() {
  return { Authorization: 'Bearer ' + token() }
}

let refreshing = null

async function doRefresh() {
  if (!refreshing) {
    refreshing = (async () => {
      const rt = refreshToken()
      const r = await fetch('/api/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: rt })
      })
      if (!r.ok) throw new Error('refresh failed')
      const d = await r.json()
      localStorage.setItem('fin_token', d.token)
      if (d.refreshToken) localStorage.setItem('fin_refresh', d.refreshToken)
      return d.token
    })().finally(() => { refreshing = null })
  }
  return refreshing
}

export function logout() {
  localStorage.removeItem('fin_user')
  localStorage.removeItem('fin_token')
  localStorage.removeItem('fin_refresh')
  if (location.pathname !== '/login') location.href = '/login'
}

export async function apiFetch(input, init) {
  let res = await fetch(input, init)
  if (res.status !== 401 && res.status !== 403) return res

  if (!refreshToken()) { logout(); return res }
  try {
    await doRefresh()
  } catch (e) {
    logout()
    return res
  }

  const oldHeaders = new Headers((init && init.headers) || {})
  oldHeaders.set('Authorization', 'Bearer ' + token())
  const newInit = Object.assign({}, init, { headers: oldHeaders })
  return fetch(input, newInit)
}

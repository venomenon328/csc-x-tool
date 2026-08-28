let csrfToken: string | null = null
let csrfHeaderName = 'X-XSRF-TOKEN'
let csrfInitialization: Promise<void> | null = null

export function initializeCsrfProtection(): Promise<void> {
  if (import.meta.env.MODE === 'test') return Promise.resolve()
  if (csrfInitialization) return csrfInitialization
  csrfInitialization = fetch('/api/system/csrf', { credentials: 'same-origin' })
    .then(async (response) => {
      if (!response.ok) throw new Error('CSRF-Initialisierung fehlgeschlagen')
      const responseBody = await response.json() as { headerName: string, token: string }
      csrfHeaderName = responseBody.headerName
      csrfToken = responseBody.token
    })
    .catch(() => {
      csrfInitialization = null
      csrfToken = null
    })
  return csrfInitialization
}

export async function apiFetch(path: string, init?: RequestInit): Promise<Response> {
  const method = (init?.method ?? 'GET').toUpperCase()
  const requiresCsrf = ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)
  if (requiresCsrf && csrfInitialization) await csrfInitialization

  if (!requiresCsrf || !csrfToken) return init ? fetch(path, init) : fetch(path)
  const headers = new Headers(init?.headers)
  headers.set(csrfHeaderName, csrfToken)
  return fetch(path, { ...init, headers, credentials: 'same-origin' })
}

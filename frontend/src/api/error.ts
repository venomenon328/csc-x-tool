export type ApiError = {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
}

export async function readApiError(response: Response): Promise<ApiError> {
  const fallback: ApiError = {
    timestamp: new Date().toISOString(),
    status: response.status,
    code: 'HTTP_ERROR',
    message: 'Die Anfrage konnte nicht verarbeitet werden.',
    path: new URL(response.url || window.location.href).pathname,
  }

  try {
    return { ...fallback, ...(await response.json() as Partial<ApiError>) }
  } catch {
    return fallback
  }
}

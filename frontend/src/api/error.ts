export type ApiError = {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
}

export async function readApiError(response: Response): Promise<ApiError> {
  const fallback: ApiError = {
    timestamp: new Date().toISOString(),
    status: response.status,
    error: response.statusText || 'Unbekannter Fehler',
    message: 'Die Anfrage konnte nicht verarbeitet werden.',
    path: new URL(response.url).pathname,
  }

  try {
    return { ...fallback, ...(await response.json() as Partial<ApiError>) }
  } catch {
    return fallback
  }
}

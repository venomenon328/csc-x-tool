import { readApiError, type ApiError } from '../../api/error'
import { apiFetch } from '../../api/request'

export type BackupReason = 'STARTUP' | 'PRE_MIGRATION' | 'MANUAL' | 'PRE_RESTORE'
export type BackupSummary = { id: string, createdAt: string, applicationVersion: string, schemaVersion: number, reason: BackupReason, sizeBytes: number }
export type BackupOverview = {
  databaseLocation: string, automaticBackupsLocation: string, manualBackupsLocation: string, exportsLocation: string,
  lastBackup: BackupSummary | null, automaticBackups: BackupSummary[], manualBackups: BackupSummary[]
}
export type RestorePreview = {
  token: string, sourceType: string, sourceName: string, createdAt: string, applicationVersion: string,
  schemaVersion: number, compatible: boolean,
  counts: { mottoShows: number, candidates: number, participants: number, contestEntries: number, ballotSnapshots: number, legacyReceivedScores: number }
}
export type RestoreResult = { message: string, safetyBackup: BackupSummary }

export class DataApiError extends Error {
  constructor(readonly apiError: ApiError) { super(apiError.message) }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok) throw new DataApiError(await readApiError(response))
  return response.json() as Promise<T>
}

export function fetchDataOverview(): Promise<BackupOverview> { return request('/api/data') }
export function createManualBackup(): Promise<BackupSummary> { return request('/api/data/backups', { method: 'POST' }) }
export function previewBackup(id: string): Promise<RestorePreview> { return request(`/api/data/restore/preview/backups/${encodeURIComponent(id)}`, { method: 'POST' }) }
export function confirmRestore(token: string): Promise<RestoreResult> {
  return request('/api/data/restore', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }) })
}
export function previewUpload(file: File): Promise<RestorePreview> {
  const body = new FormData()
  body.append('file', file)
  return request('/api/data/restore/preview/upload', { method: 'POST', body })
}

import { Alert } from '@mui/material'
import type { ApiError } from '../api/error'

type ApiErrorNoticeProps = {
  error: ApiError
}

export function ApiErrorNotice({ error }: ApiErrorNoticeProps) {
  return <Alert severity="error">{error.message}</Alert>
}

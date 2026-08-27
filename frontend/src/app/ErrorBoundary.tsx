import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Alert, Button, Stack, Typography } from '@mui/material'

type ErrorBoundaryProps = {
  children: ReactNode
}

type ErrorBoundaryState = {
  error: Error | null
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  public state: ErrorBoundaryState = { error: null }

  public static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  public componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('Unbehandelter Fehler in der Oberfläche.', error, info)
  }

  private reload = (): void => {
    window.location.reload()
  }

  public render(): ReactNode {
    if (this.state.error !== null) {
      return (
        <Stack spacing={2} sx={{ alignItems: 'flex-start', m: 'auto', maxWidth: 560, p: 4 }}>
          <Typography component="h1" variant="h5">Oberfläche konnte nicht geladen werden</Typography>
          <Alert severity="error">{this.state.error.message}</Alert>
          <Button onClick={this.reload} variant="contained">Neu laden</Button>
        </Stack>
      )
    }

    return this.props.children
  }
}

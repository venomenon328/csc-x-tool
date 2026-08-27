import { Paper, Typography } from '@mui/material'
import { useState, type ClipboardEvent } from 'react'

/** Reads only the formats carried by one user initiated paste event. Nothing is retained here. */
export function ClipboardImportArea({ onPasteData, disabled = false }: {
  onPasteData: (html: string, text: string) => Promise<void>
  disabled?: boolean
}) {
  const [processing, setProcessing] = useState(false)

  async function handlePaste(event: ClipboardEvent<HTMLDivElement>) {
    event.preventDefault()
    if (processing || disabled) return
    const html = event.clipboardData?.getData('text/html') ?? ''
    const text = event.clipboardData?.getData('text/plain') ?? ''
    setProcessing(true)
    try {
      await onPasteData(html, text)
    } finally {
      setProcessing(false)
    }
  }

  return (
    <Paper
      aria-busy={processing}
      aria-label="CSC-Beitragsblock einfügen"
      onPaste={(event) => void handlePaste(event)}
      role="button"
      tabIndex={disabled ? -1 : 0}
      sx={{ border: 1, borderColor: 'primary.main', cursor: disabled ? 'default' : 'paste', outlineOffset: 3, p: 3 }}
    >
      <Typography component="h2" variant="h6">CSC-Beitragsblock importieren</Typography>
      <Typography color="text.secondary" sx={{ mt: 1 }}>
        CSC-Beitragsblock kopieren, hier klicken und Strg+V drücken.
      </Typography>
      <Typography color="text.secondary" variant="body2" sx={{ mt: 1 }}>
        {processing ? 'Zwischenablage wird geprüft …' : 'Linktexte und Linkziele werden nur für die Vorschau ausgewertet.'}
      </Typography>
    </Paper>
  )
}

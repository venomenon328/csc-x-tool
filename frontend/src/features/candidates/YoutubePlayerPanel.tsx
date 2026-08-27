import { Alert, Box, Button, Link, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import type { Candidate } from './api'

function videoIdFromYoutubeUrl(youtubeUrl: string): string | null {
  try {
    const videoId = new URL(youtubeUrl).searchParams.get('v')
    return videoId !== null && /^[A-Za-z0-9_-]{11}$/.test(videoId) ? videoId : null
  } catch {
    return null
  }
}

/** A single reusable player surface; rows deliberately never mount their own iframe. */
export function YoutubePlayerPanel({ candidate }: { candidate: Candidate | null }) {
  const [embedFailed, setEmbedFailed] = useState(false)

  if (candidate === null) {
    return (
      <Alert severity="info">Wähle einen Kandidaten aus, um ihn hier anzuhören.</Alert>
    )
  }

  const videoId = videoIdFromYoutubeUrl(candidate.youtubeUrl)
  const embedUrl = videoId === null ? null : `https://www.youtube-nocookie.com/embed/${videoId}`

  return (
    <Stack component="section" spacing={1.5} aria-label="YouTube-Player" sx={{ minWidth: 0 }}>
      <Box>
        <Typography component="h2" variant="h6">{candidate.artist} – {candidate.title}</Typography>
        <Typography color="text.secondary" variant="body2">Aktuell ausgewählter Kandidat</Typography>
      </Box>
      {embedUrl !== null && !embedFailed && (
        <Box sx={{ aspectRatio: '16 / 9', maxWidth: 760, width: '100%' }}>
          <Box
            component="iframe"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            allowFullScreen
            onError={() => setEmbedFailed(true)}
            referrerPolicy="strict-origin-when-cross-origin"
            src={embedUrl}
            sx={{ border: 0, height: '100%', width: '100%' }}
            title={`YouTube: ${candidate.artist} – ${candidate.title}`}
          />
        </Box>
      )}
      {(embedUrl === null || embedFailed) && (
        <Alert severity="warning">
          Der eingebettete Player konnte lokal nicht geladen werden. Der externe Link bleibt verfügbar.
        </Alert>
      )}
      <Link href={candidate.youtubeUrl} rel="noreferrer" target="_blank">
        <Button component="span" variant="outlined">Auf YouTube öffnen</Button>
      </Link>
    </Stack>
  )
}

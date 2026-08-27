import { Alert, Box, Button, Link, Stack, Typography } from '@mui/material'
import { useEffect, useRef, useState } from 'react'
import type { PlayableSong } from './PlayableSong'

function videoIdFromYoutubeUrl(youtubeUrl: string): string | null {
  try {
    const videoId = new URL(youtubeUrl).searchParams.get('v')
    return videoId !== null && /^[A-Za-z0-9_-]{11}$/.test(videoId) ? videoId : null
  } catch {
    return null
  }
}

function startSecondsFromYoutubeUrl(youtubeUrl: string): number | null {
  try {
    const url = new URL(youtubeUrl)
    const start = url.searchParams.get('t') ?? url.searchParams.get('start')
    if (start === null || start === '') return null
    if (/^\d+$/.test(start)) return Number(start)
    const match = /^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$/.exec(start)
    if (match === null || match.slice(1).every((part) => part === undefined)) return null
    return Number(match[1] ?? 0) * 3600 + Number(match[2] ?? 0) * 60 + Number(match[3] ?? 0)
  } catch {
    return null
  }
}

/** A single reusable player surface; rows deliberately never mount their own iframe. */
export function YoutubePlayerPanel({ song, emptyMessage, contextLabel }: {
  song: PlayableSong | null
  emptyMessage: string
  contextLabel: string
}) {
  const [failedSongId, setFailedSongId] = useState<number | null>(null)
  const iframeRef = useRef<HTMLIFrameElement | null>(null)
  const videoId = song === null ? null : videoIdFromYoutubeUrl(song.youtubeUrl)
  const startSeconds = song === null ? null : startSecondsFromYoutubeUrl(song.youtubeUrl)
  const embedUrl = videoId === null ? null : `https://www.youtube-nocookie.com/embed/${videoId}${startSeconds === null ? '' : `?start=${startSeconds}`}`
  const embedFailed = song !== null && failedSongId === song.id

  useEffect(() => {
    const iframe = iframeRef.current
    if (iframe === null || song === null || embedUrl === null || embedFailed) return
    const handleError = () => setFailedSongId(song.id)
    iframe.addEventListener('error', handleError)
    return () => iframe.removeEventListener('error', handleError)
  }, [song, embedFailed, embedUrl])

  if (song === null) {
    return <Alert severity="info">{emptyMessage}</Alert>
  }

  return (
    <Stack component="section" spacing={1.5} aria-label="YouTube-Player" sx={{ minWidth: 0 }}>
      <Box>
        <Typography component="h2" variant="h6">{song.artist} – {song.title}</Typography>
        <Typography color="text.secondary" variant="body2">{contextLabel}</Typography>
      </Box>
      {embedUrl !== null && !embedFailed && (
        <Box sx={{ aspectRatio: '16 / 9', maxWidth: 760, width: '100%' }}>
          <Box
            component="iframe"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            allowFullScreen
            ref={iframeRef}
            referrerPolicy="strict-origin-when-cross-origin"
            src={embedUrl}
            sx={{ border: 0, height: '100%', width: '100%' }}
            title={`YouTube: ${song.artist} – ${song.title}`}
          />
        </Box>
      )}
      {(embedUrl === null || embedFailed) && (
        <Alert severity="warning">
          Der eingebettete Player konnte lokal nicht geladen werden. Der externe Link bleibt verfügbar.
        </Alert>
      )}
      <Link href={song.youtubeUrl} rel="noreferrer" target="_blank">
        <Button component="span" variant="outlined">Auf YouTube öffnen</Button>
      </Link>
    </Stack>
  )
}

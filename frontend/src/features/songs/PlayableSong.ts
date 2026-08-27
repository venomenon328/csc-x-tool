/** The deliberately small contract shared by candidates and contest entries. */
export type PlayableSong = {
  id: number
  artist: string
  title: string
  youtubeUrl: string
}

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { fetchContests, type Contest } from './api'

type ContestContextValue = {
  contests: Contest[]
  selectedContestId: number | null
  selectedContest: Contest | null
  loading: boolean
  refresh: () => Promise<void>
  selectContest: (contestId: number) => void
}

const ContestContext = createContext<ContestContextValue>({
  contests: [], selectedContestId: null, selectedContest: null, loading: true,
  refresh: async () => {}, selectContest: () => {},
})

export function ContestProvider({ children }: { children: React.ReactNode }) {
  const [contests, setContests] = useState<Contest[]>([])
  const [selectedContestId, setSelectedContestId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const refresh = useCallback(() => fetchContests().then((loaded) => {
    setContests(loaded)
    setSelectedContestId((selected) => selected !== null && loaded.some((contest) => contest.id === selected)
      ? selected : loaded.find((contest) => contest.current)?.id ?? loaded[0]?.id ?? null)
    setLoading(false)
  }), [])
  useEffect(() => {
    let disposed = false
    void fetchContests().then((loaded) => {
      if (disposed) return
      setContests(loaded)
      setSelectedContestId((selected) => selected !== null && loaded.some((contest) => contest.id === selected)
        ? selected : loaded.find((contest) => contest.current)?.id ?? loaded[0]?.id ?? null)
      setLoading(false)
    }).catch(() => { if (!disposed) setLoading(false) })
    return () => { disposed = true }
  }, [])
  const value = useMemo(() => ({
    contests,
    selectedContestId,
    selectedContest: contests.find((contest) => contest.id === selectedContestId) ?? null,
    loading,
    refresh,
    selectContest: setSelectedContestId,
  }), [contests, loading, refresh, selectedContestId])
  return <ContestContext.Provider value={value}>{children}</ContestContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useContest() { return useContext(ContestContext) }

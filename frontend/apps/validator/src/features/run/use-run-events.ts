import { useEffect, useReducer, useState } from "react"

import { eventsUrl } from "@/api/validator-api"

import {
  initialRunState,
  reduceRun,
  RUN_EVENTS,
  type RunEvent,
} from "./run-state"

export function useRunEvents(id: string) {
  const [state, dispatch] = useReducer(reduceRun, initialRunState)
  const [interrupted, setInterrupted] = useState(false)

  useEffect(() => {
    const source = new EventSource(eventsUrl(id))
    const listeners = RUN_EVENTS.map((name) => {
      const listener = (message: MessageEvent<string>) => {
        setInterrupted(false)
        dispatch({ name, data: JSON.parse(message.data) } as RunEvent)
        if (name === "summary" || name === "failed") source.close()
      }
      source.addEventListener(name, listener)
      return [name, listener] as const
    })
    source.onerror = () => {
      if (source.readyState === EventSource.CLOSED) setInterrupted(true)
    }
    return () => {
      listeners.forEach(([name, listener]) =>
        source.removeEventListener(name, listener)
      )
      source.close()
    }
  }, [id])

  return { state, interrupted }
}

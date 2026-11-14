"use client";

import { useEffect, useRef } from "react";

export interface RealtimeEvent {
  type: string;
  id?: string;
  collectionId?: string | null;
}

let source: EventSource | null = null;
const listeners = new Set<(event: RealtimeEvent) => void>();

function ensureSource() {
  if (typeof window === "undefined" || source) return;
  source = new EventSource("/api/events");
  source.onmessage = (event) => {
    try {
      const parsed = JSON.parse(event.data) as RealtimeEvent;
      listeners.forEach((listener) => listener(parsed));
      window.dispatchEvent(new CustomEvent("mm:realtime", { detail: parsed }));
    } catch {
      // ignore malformed frames
    }
  };
}

/**
 * Close the shared stream. Called on logout/session loss so the next account
 * does not keep reading the previous account's events.
 */
export function resetRealtime() {
  source?.close();
  source = null;
}

/**
 * One shared SSE connection for the whole app. The handler is kept in a ref so
 * a changing callback never reconnects the stream.
 */
export function useRealtime(handler: (event: RealtimeEvent) => void) {
  const handlerRef = useRef(handler);

  useEffect(() => {
    handlerRef.current = handler;
  }, [handler]);

  useEffect(() => {
    ensureSource();
    const listener = (event: RealtimeEvent) => handlerRef.current(event);
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  }, []);
}

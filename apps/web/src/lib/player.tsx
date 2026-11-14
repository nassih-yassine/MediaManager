"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import type { Item } from "@/lib/types";

export type RepeatMode = "off" | "all" | "one";

const STORAGE_KEY = "mm.player.v1";

interface Persisted {
  queue: Item[];
  index: number;
  volume: number;
  repeat: RepeatMode;
  shuffle: boolean;
}

interface PlayerApi {
  queue: Item[];
  index: number;
  current: Item | null;
  playing: boolean;
  progress: number;
  duration: number;
  volume: number;
  muted: boolean;
  repeat: RepeatMode;
  shuffle: boolean;
  playNow: (items: Item[], startAt?: number) => void;
  playItem: (item: Item) => void;
  playNext: (item: Item) => void;
  addToQueue: (items: Item[]) => void;
  removeAt: (index: number) => void;
  move: (from: number, to: number) => void;
  clearQueue: () => void;
  toggle: () => void;
  next: () => void;
  prev: () => void;
  seek: (seconds: number) => void;
  jumpTo: (index: number) => void;
  setVolume: (value: number) => void;
  toggleMute: () => void;
  cycleRepeat: () => void;
  toggleShuffle: () => void;
  removeItem: (id: string) => void;
  videoRef: React.RefObject<HTMLVideoElement | null>;
}

const PlayerContext = createContext<PlayerApi | null>(null);

export function usePlayer(): PlayerApi {
  const api = useContext(PlayerContext);
  if (!api) throw new Error("usePlayer must be used inside PlayerProvider");
  return api;
}

export function PlayerProvider({ children }: { children: React.ReactNode }) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const wantPlayRef = useRef(false);

  const [queue, setQueue] = useState<Item[]>([]);
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolumeState] = useState(0.85);
  const [muted, setMuted] = useState(false);
  const [repeat, setRepeat] = useState<RepeatMode>("off");
  const [shuffle, setShuffle] = useState(false);

  const current = queue[index] ?? null;

  // Restore the last session (paused: browsers block autoplay on load).
  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const saved = JSON.parse(raw) as Partial<Persisted>;
      if (!Array.isArray(saved.queue) || saved.queue.length === 0) return;
      setQueue(saved.queue);
      setIndex(
        Math.min(
          Math.max(0, Number(saved.index) || 0),
          saved.queue.length - 1,
        ),
      );
      if (typeof saved.volume === "number") setVolumeState(saved.volume);
      if (saved.repeat === "all" || saved.repeat === "one") setRepeat(saved.repeat);
      setShuffle(Boolean(saved.shuffle));
    } catch {
      localStorage.removeItem(STORAGE_KEY);
    }
  }, []);

  useEffect(() => {
    if (queue.length === 0) {
      localStorage.removeItem(STORAGE_KEY);
      return;
    }
    const payload: Persisted = { queue, index, volume, repeat, shuffle };
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
    } catch {
      // quota exceeded: keep playing, persistence is best-effort
    }
  }, [queue, index, volume, repeat, shuffle]);

  const play = useCallback(() => {
    const el = videoRef.current;
    if (!el || !current) return;
    wantPlayRef.current = true;
    void el.play().catch(() => setPlaying(false));
  }, [current]);

  const pause = useCallback(() => {
    wantPlayRef.current = false;
    videoRef.current?.pause();
    setPlaying(false);
  }, []);

  const next = useCallback(() => {
    if (queue.length === 0) return;
    if (index + 1 < queue.length) {
      wantPlayRef.current = true;
      setIndex(index + 1);
    } else if (repeat === "all") {
      wantPlayRef.current = true;
      setIndex(0);
    } else {
      wantPlayRef.current = false;
      setPlaying(false);
    }
  }, [index, queue.length, repeat]);

  const prev = useCallback(() => {
    const el = videoRef.current;
    if (!el) return;
    if (el.currentTime > 3) {
      el.currentTime = 0;
      return;
    }
    if (index > 0) {
      wantPlayRef.current = true;
      setIndex(index - 1);
    } else if (repeat === "all" && queue.length > 0) {
      wantPlayRef.current = true;
      setIndex(queue.length - 1);
    }
  }, [index, queue.length, repeat]);

  const handleEnded = useCallback(() => {
    if (repeat === "one") {
      const el = videoRef.current;
      if (el) {
        el.currentTime = 0;
        void el.play().catch(() => {});
      }
      return;
    }
    next();
  }, [next, repeat]);

  // Load a new source and play it if the change came from a play action.
  useEffect(() => {
    const el = videoRef.current;
    if (!el || !current) return;
    setProgress(0);
    setDuration(0);
    if (wantPlayRef.current) {
      void el.play().catch(() => setPlaying(false));
    }
  }, [current?.id, current]);

  // Clearing the queue must actually stop the element; removing the src prop
  // alone can leave audio playing.
  useEffect(() => {
    if (current) return;
    const el = videoRef.current;
    if (!el) return;
    el.pause();
    el.removeAttribute("src");
    el.load();
    setPlaying(false);
    setProgress(0);
    setDuration(0);
  }, [current]);

  useEffect(() => {
    const el = videoRef.current;
    if (!el) return;
    el.volume = volume;
    el.muted = muted;
  }, [volume, muted]);

  useEffect(() => {
    if (!("mediaSession" in navigator)) return;
    const session = navigator.mediaSession;
    if (!current) {
      session.metadata = null;
      session.playbackState = "none";
      return;
    }
    if (typeof MediaMetadata !== "undefined") {
      session.metadata = new MediaMetadata({
        title: current.title,
        artist: current.kind,
        album: "MediaManager",
        artwork: current.thumbUrl
          ? [{ src: current.thumbUrl, sizes: "512x512", type: "image/jpeg" }]
          : [],
      });
    }
    session.playbackState = playing ? "playing" : "paused";
    session.setActionHandler("play", play);
    session.setActionHandler("pause", pause);
    session.setActionHandler("nexttrack", next);
    session.setActionHandler("previoustrack", prev);
    session.setActionHandler("seekto", (details) => {
      if (details.seekTime != null && videoRef.current) {
        videoRef.current.currentTime = details.seekTime;
      }
    });
  }, [current, playing, play, pause, next, prev]);

  const playNow = useCallback(
    (items: Item[], startAt = 0) => {
      if (items.length === 0) return;
      const safeIndex = Math.min(Math.max(0, startAt), items.length - 1);
      wantPlayRef.current = true;
      setPlaying(true);
      setQueue(items);
      setIndex(safeIndex);
      const el = videoRef.current;
      if (el && current?.id === items[safeIndex]?.id && el.paused) {
        void el.play().catch(() => {});
      }
    },
    [current],
  );

  const playItem = useCallback((item: Item) => playNow([item], 0), [playNow]);

  const playNext = useCallback((item: Item) => {
    setQueue((prev) => {
      const nextQueue = [...prev];
      nextQueue.splice(index + 1, 0, item);
      return nextQueue;
    });
  }, [index]);

  const addToQueue = useCallback((items: Item[]) => {
    setQueue((prev) => [...prev, ...items]);
  }, []);

  const removeAt = useCallback(
    (at: number) => {
      setQueue((prev) => {
        const nextQueue = prev.filter((_, i) => i !== at);
        if (nextQueue.length === 0) {
          setIndex(0);
          setPlaying(false);
          wantPlayRef.current = false;
          return nextQueue;
        }
        setIndex((currentIndex) => {
          if (at < currentIndex) return currentIndex - 1;
          if (at === currentIndex) return Math.min(currentIndex, nextQueue.length - 1);
          return currentIndex;
        });
        return nextQueue;
      });
    },
    [],
  );

  const move = useCallback(
    (from: number, to: number) => {
      if (from === to) return;
      setQueue((prev) => {
        if (from < 0 || from >= prev.length || to < 0 || to >= prev.length) return prev;
        const nextQueue = [...prev];
        const [moved] = nextQueue.splice(from, 1);
        nextQueue.splice(to, 0, moved);
        setIndex((currentIndex) => {
          if (from === currentIndex) return to;
          if (from < currentIndex && to >= currentIndex) return currentIndex - 1;
          if (from > currentIndex && to <= currentIndex) return currentIndex + 1;
          return currentIndex;
        });
        return nextQueue;
      });
    },
    [],
  );

  const clearQueue = useCallback(() => {
    wantPlayRef.current = false;
    setQueue([]);
    setIndex(0);
    setPlaying(false);
    setProgress(0);
    setDuration(0);
  }, []);

  const removeItem = useCallback(
    (id: string) => {
      setQueue((prev) => {
        const at = prev.findIndex((i) => i.id === id);
        if (at === -1) return prev;
        const nextQueue = prev.filter((i) => i.id !== id);
        if (nextQueue.length === 0) {
          setIndex(0);
          setPlaying(false);
          wantPlayRef.current = false;
          return nextQueue;
        }
        setIndex((currentIndex) => {
          if (at < currentIndex) return currentIndex - 1;
          if (at === currentIndex) return Math.min(currentIndex, nextQueue.length - 1);
          return currentIndex;
        });
        return nextQueue;
      });
    },
    [],
  );

  const toggle = useCallback(() => {
    const el = videoRef.current;
    if (!el || !current) return;
    if (el.paused) play();
    else pause();
  }, [current, pause, play]);

  const seek = useCallback((seconds: number) => {
    const el = videoRef.current;
    if (!el) return;
    el.currentTime = seconds;
    setProgress(seconds);
  }, []);

  const jumpTo = useCallback(
    (target: number) => {
      if (target < 0 || target >= queue.length) return;
      wantPlayRef.current = true;
      setPlaying(true);
      setIndex(target);
    },
    [queue.length],
  );

  const setVolume = useCallback((value: number) => {
    const clamped = Math.min(1, Math.max(0, value));
    setVolumeState(clamped);
    if (clamped > 0) setMuted(false);
  }, []);

  const toggleMute = useCallback(() => setMuted((m) => !m), []);

  const cycleRepeat = useCallback(() => {
    setRepeat((mode) => (mode === "off" ? "all" : mode === "all" ? "one" : "off"));
  }, []);

  const toggleShuffle = useCallback(() => {
    setShuffle((on) => {
      const nextOn = !on;
      if (nextOn) {
        setQueue((prev) => {
          if (prev.length < 3) return prev;
          const head = prev.slice(0, index + 1);
          const rest = prev.slice(index + 1);
          for (let i = rest.length - 1; i > 0; i -= 1) {
            const j = Math.floor(Math.random() * (i + 1));
            [rest[i], rest[j]] = [rest[j], rest[i]];
          }
          return [...head, ...rest];
        });
      }
      return nextOn;
    });
  }, [index]);

  const api = useMemo<PlayerApi>(
    () => ({
      queue,
      index,
      current,
      playing,
      progress,
      duration,
      volume,
      muted,
      repeat,
      shuffle,
      playNow,
      playItem,
      playNext,
      addToQueue,
      removeAt,
      move,
      clearQueue,
      toggle,
      next,
      prev,
      seek,
      setVolume,
      toggleMute,
      cycleRepeat,
      toggleShuffle,
      removeItem,
      jumpTo,
      videoRef,
    }),
    [
      queue,
      index,
      current,
      playing,
      progress,
      duration,
      volume,
      muted,
      repeat,
      shuffle,
      playNow,
      playItem,
      playNext,
      addToQueue,
      removeAt,
      move,
      clearQueue,
      toggle,
      next,
      prev,
      seek,
      setVolume,
      toggleMute,
      cycleRepeat,
      toggleShuffle,
      removeItem,
      jumpTo,
    ],
  );

  return (
    <PlayerContext.Provider value={api}>
      {children}
      {/* One media element for the whole app. It stays mounted while audio
          plays; the container is only visible for video. */}
      <div
        className={
          current?.kind === "video"
            ? "fixed right-3 bottom-[88px] z-40 w-[min(420px,calc(100vw-1.5rem))] overflow-hidden rounded-xl border border-line bg-black shadow-xl"
            : "hidden"
        }
      >
        <video
          ref={videoRef}
          src={current?.fileUrl}
          playsInline
          preload="metadata"
          className="aspect-video w-full bg-black"
          onTimeUpdate={(event) => setProgress(event.currentTarget.currentTime)}
          onDurationChange={(event) =>
            setDuration(
              Number.isFinite(event.currentTarget.duration)
                ? event.currentTarget.duration
                : 0,
            )
          }
          onPlay={() => setPlaying(true)}
          onPause={() => setPlaying(false)}
          onEnded={handleEnded}
          onError={() => setPlaying(false)}
          onDoubleClick={(event) => {
            const el = event.currentTarget;
            if (document.fullscreenElement) void document.exitFullscreen();
            else void el.requestFullscreen?.();
          }}
        />
      </div>
    </PlayerContext.Provider>
  );
}

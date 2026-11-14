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
import {
  appendUnique,
  dedupeById,
  indexAfterRemoval,
  indexAfterRemovingAll,
  insertNextUnique,
  isPlayable,
  moveItem,
  nextIndex,
  prevIndex,
  removeAt as removeAtUtil,
  resumeTarget,
} from "@/lib/player-utils";
import { api as apiRequest } from "@/lib/api";
import { useToast } from "@/components/ui/Toast";
import { useRealtime } from "@/lib/realtime";

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
  const positionsRef = useRef(new Map<string, number>());
  const pendingResume = useRef<number | null>(null);
  const lastSaveWall = useRef(0);
  const currentRef = useRef<Item | null>(null);

  const [queue, setQueue] = useState<Item[]>([]);
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolumeState] = useState(0.85);
  const [muted, setMuted] = useState(false);
  const [repeat, setRepeat] = useState<RepeatMode>("off");
  const [shuffle, setShuffle] = useState(false);
  const [mediaVersion, setMediaVersion] = useState(0);
  const restoringRef = useRef(false);
  const restoreAttempted = useRef<Set<string>>(new Set());
  const toast = useToast();

  const current = queue[index] ?? null;

  useEffect(() => {
    currentRef.current = current;
  }, [current]);

  const savePosition = useCallback((id: string, positionMs: number) => {
    positionsRef.current.set(id, positionMs);
    void apiRequest(`/api/items/${id}/progress`, {
      method: "POST",
      body: JSON.stringify({ positionMs: Math.round(positionMs) }),
    }).catch(() => {});
  }, []);

  // Best-effort save when the tab closes; keepalive lets the request finish.
  useEffect(() => {
    function onHide() {
      const el = videoRef.current;
      const item = currentRef.current;
      if (!el || !item || el.currentTime <= 1) return;
      void fetch(`/api/items/${item.id}/progress`, {
        method: "POST",
        keepalive: true,
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ positionMs: Math.round(el.currentTime * 1000) }),
      }).catch(() => {});
    }
    window.addEventListener("beforeunload", onHide);
    return () => window.removeEventListener("beforeunload", onHide);
  }, []);

  // Restore the last session (paused: browsers block autoplay on load).
  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const saved = JSON.parse(raw) as Partial<Persisted>;
      if (!Array.isArray(saved.queue) || saved.queue.length === 0) return;
      const restored = dedupeById(saved.queue as Item[]);
      if (restored.length === 0) return;
      setQueue(restored);
      setIndex(
        Math.min(
          Math.max(0, Number(saved.index) || 0),
          restored.length - 1,
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
    const el = videoRef.current;
    if (el) {
      el.pause();
      if (current && el.currentTime > 0) {
        savePosition(current.id, el.currentTime * 1000);
      }
    }
    setPlaying(false);
  }, [current, savePosition]);

  const next = useCallback(() => {
    const target = nextIndex(index, queue.length, repeat);
    if (target == null) {
      wantPlayRef.current = false;
      setPlaying(false);
      return;
    }
    wantPlayRef.current = true;
    setIndex(target);
  }, [index, queue.length, repeat]);

  const prev = useCallback(() => {
    const el = videoRef.current;
    if (!el) return;
    if (el.currentTime > 3) {
      el.currentTime = 0;
      return;
    }
    const target = prevIndex(index, queue.length, repeat);
    if (target == null) return;
    wantPlayRef.current = true;
    setIndex(target);
  }, [index, queue.length, repeat]);

  const handleEnded = useCallback(() => {
    if (current) savePosition(current.id, 0);
    if (repeat === "one") {
      const el = videoRef.current;
      if (el) {
        el.currentTime = 0;
        void el.play().catch(() => {});
      }
      return;
    }
    next();
  }, [next, repeat, current, savePosition]);

  // Load a new source and play it if the change came from a play action.
  useEffect(() => {
    const el = videoRef.current;
    if (!el || !current) return;
    const known =
      positionsRef.current.get(current.id) ?? current.positionMs ?? 0;
    pendingResume.current = resumeTarget(known, current.durationMs);
    lastSaveWall.current = 0;
    setProgress(0);
    setDuration(0);
    if (wantPlayRef.current) {
      void el.play().catch(() => setPlaying(false));
    }
  }, [current?.id, current, mediaVersion]);

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

  const restoreCurrent = useCallback(
    async (item: Item) => {
      if (restoringRef.current) return;
      restoringRef.current = true;
      try {
        const started = await apiRequest<{
          status: string;
          job?: { id: string };
        }>(`/api/items/${item.id}/restore`, { method: "POST" });
        if (started.status === "ready") return;
        const jobId = started.job?.id;
        if (!jobId) throw new Error("Could not start the restore");
        toast("Restoring media…", "info");
        for (let attempt = 0; attempt < 150; attempt += 1) {
          await new Promise((resolve) => setTimeout(resolve, 2000));
          const job = await apiRequest<{ status: string }>(`/api/jobs/${jobId}`);
          if (job.status === "failed") throw new Error("Restore failed");
          if (job.status === "cancelled") return;
          if (job.status === "done") break;
        }
        const fresh = await apiRequest<Item>(`/api/items/${item.id}`);
        setQueue((current) =>
          current.map((entry) => (entry.id === item.id ? fresh : entry)),
        );
        setMediaVersion((value) => value + 1);
        wantPlayRef.current = true;
        toast("Media restored", "success");
      } catch (err) {
        toast((err as Error).message, "error");
      } finally {
        restoringRef.current = false;
      }
    },
    [toast],
  );

  const handleMediaError = useCallback(() => {
    setPlaying(false);
    // The queue may predate an eviction, so ask the server even when the
    // cached item has no evictedAt. One attempt per item per session.
    if (!current?.sourceUrl || restoreAttempted.current.has(current.id)) return;
    restoreAttempted.current.add(current.id);
    void restoreCurrent(current);
  }, [current, restoreCurrent]);

  const playNow = useCallback(
    (items: Item[], startAt = 0) => {
      const playable = items.filter(isPlayable);
      if (playable.length === 0) return;
      const before = items.slice(0, startAt).filter(isPlayable).length;
      const safeIndex = Math.min(Math.max(0, before), playable.length - 1);
      wantPlayRef.current = true;
      setPlaying(true);
      setQueue(playable);
      setIndex(safeIndex);
      const el = videoRef.current;
      if (el && current?.id === playable[safeIndex]?.id && el.paused) {
        void el.play().catch(() => {});
      }
    },
    [current],
  );

  const playItem = useCallback((item: Item) => playNow([item], 0), [playNow]);

  const playNext = useCallback(
    (item: Item) => {
      if (!isPlayable(item)) return;
      if (queue.length === 0) {
        playNow([item], 0);
        return;
      }
      const result = insertNextUnique(queue, index, item);
      if (result.queue === queue) return;
      setQueue(result.queue);
      setIndex(result.index);
    },
    [index, queue, playNow],
  );

  const addToQueue = useCallback(
    (items: Item[]) => {
      const playable = items.filter(isPlayable);
      if (playable.length === 0) return;
      if (queue.length === 0) {
        playNow(playable, 0);
        return;
      }
      const next = appendUnique(queue, playable);
      if (next.length === queue.length) return;
      setQueue(next);
    },
    [queue, playNow],
  );

  const removeAt = useCallback(
    (at: number) => {
      if (at < 0 || at >= queue.length) return;
      const nextQueue = removeAtUtil(queue, at);
      const nextIndexValue = indexAfterRemoval(
        index,
        at,
        queue.length,
        nextQueue.length,
      );
      if (nextQueue.length === 0) {
        wantPlayRef.current = false;
        setPlaying(false);
      }
      setQueue(nextQueue);
      setIndex(nextIndexValue);
    },
    [queue, index],
  );

  const move = useCallback(
    (from: number, to: number) => {
      const nextQueue = moveItem(queue, from, to);
      if (nextQueue === queue) return;
      let nextIndexValue = index;
      if (from === index) nextIndexValue = to;
      else if (from < index && to >= index) nextIndexValue = index - 1;
      else if (from > index && to <= index) nextIndexValue = index + 1;
      setQueue(nextQueue);
      setIndex(nextIndexValue);
    },
    [queue, index],
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
      if (!queue.some((item) => item.id === id)) return;
      const nextQueue = queue.filter((item) => item.id !== id);
      if (nextQueue.length === 0) {
        wantPlayRef.current = false;
        setPlaying(false);
        setQueue([]);
        setIndex(0);
        return;
      }
      const nextIndexValue = indexAfterRemovingAll(queue, index, id);
      setQueue(nextQueue);
      setIndex(nextIndexValue);
    },
    [queue, index],
  );

  // Deleting an item anywhere (another tab, another view) prunes the queue.
  useRealtime((event) => {
    if (event.type === "item.deleted" && event.id) removeItem(event.id);
  });

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
          src={
            current
              ? `${current.fileUrl}${mediaVersion ? `?v=${mediaVersion}` : ""}`
              : undefined
          }
          playsInline
          preload="metadata"
          className="aspect-video w-full bg-black"
          onTimeUpdate={(event) => {
            const el = event.currentTarget;
            setProgress(el.currentTime);
            if (!current) return;
            const now = Date.now();
            if (now - lastSaveWall.current > 8000 && el.currentTime > 1) {
              lastSaveWall.current = now;
              savePosition(current.id, el.currentTime * 1000);
            }
          }}
          onLoadedMetadata={(event) => {
            const target = pendingResume.current;
            if (target == null) return;
            const el = event.currentTarget;
            if (target < el.duration - 1) el.currentTime = target;
            pendingResume.current = null;
          }}
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
          onError={handleMediaError}
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

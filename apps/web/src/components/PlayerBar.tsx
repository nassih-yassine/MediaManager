/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { useToast } from "@/components/ui/Toast";
import { BarSlider } from "@/components/ui/BarSlider";
import {
  CheckIcon,
  CloseIcon,
  MuteIcon,
  NextIcon,
  PauseIcon,
  PlayIcon,
  PrevIcon,
  QueueIcon,
  RepeatIcon,
  RepeatOneIcon,
  ShuffleIcon,
  VolumeIcon,
  TrashIcon,
} from "@/components/icons";
import { usePlayer } from "@/lib/player";
import { kindLabel } from "@/lib/kind";
import type { Item } from "@/lib/types";

function clock(seconds: number): string {
  const total = Math.max(0, Math.floor(seconds || 0));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return h > 0
    ? `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`
    : `${m}:${String(s).padStart(2, "0")}`;
}

function Equalizer() {
  return (
    <span className="inline-flex h-3 items-end gap-[2px]" aria-hidden>
      {[0, 1, 2].map((bar) => (
        <span
          key={bar}
          className="eq-bar w-[2px] rounded-full bg-accent"
          style={{ height: "100%", animationDelay: `${bar * 120}ms` }}
        />
      ))}
    </span>
  );
}

function QueueRow({
  item,
  at,
  active,
  onDrop,
  onRemove,
}: {
  item: Item;
  at: number;
  active: boolean;
  onDrop: (from: number, to: number) => void;
  onRemove: (at: number) => void;
}) {
  const player = usePlayer();
  const [dragging, setDragging] = useState(false);

  return (
    <li
      draggable
      onDragStart={(event) => {
        event.dataTransfer.effectAllowed = "move";
        event.dataTransfer.setData("text/plain", String(at));
        setDragging(true);
      }}
      onDragEnd={() => setDragging(false)}
      onDragOver={(event) => event.preventDefault()}
      onDrop={(event) => {
        event.preventDefault();
        const from = Number(event.dataTransfer.getData("text/plain"));
        if (Number.isInteger(from) && from !== at) onDrop(from, at);
      }}
      className={`group flex items-center gap-2.5 rounded-lg border px-2 py-1.5 transition-colors ${
        active
          ? "border-accent/40 bg-panel-2"
          : "border-transparent hover:bg-panel-2"
      } ${dragging ? "opacity-40" : ""}`}
    >
      <button
        type="button"
        onClick={() => player.jumpTo(at)}
        className="min-w-0 flex-1 text-left"
      >
        <span className="flex items-center gap-2.5">
          <span className="h-9 w-9 shrink-0 overflow-hidden rounded-md bg-canvas">
            {item.thumbUrl ? (
              <img src={item.thumbUrl} alt="" className="h-full w-full object-cover" />
            ) : (
              <span className="flex h-full items-center justify-center text-[10px] text-muted uppercase">
                {item.kind.slice(0, 1)}
              </span>
            )}
          </span>
          <span className="min-w-0">
            <span className="block truncate text-[13px] leading-tight">
              {item.title}
            </span>
            <span className="mt-0.5 block font-mono text-[10px] text-muted">
              {kindLabel(item.kind)}
            </span>
          </span>
        </span>
      </button>
      {active ? <CheckIcon className="h-3.5 w-3.5 shrink-0 text-accent" /> : null}
      <button
        type="button"
        onClick={() => onRemove(at)}
        aria-label={`Remove ${item.title} from queue`}
        className="shrink-0 rounded p-1 text-muted/60 transition-colors hover:text-danger"
      >
        <CloseIcon className="h-3.5 w-3.5" />
      </button>
    </li>
  );
}

export function PlayerBar() {
  const player = usePlayer();
  const toast = useToast();
  const [queueOpen, setQueueOpen] = useState(false);

  // Space toggles, arrows seek, N/P skip, M mutes, Q opens the queue.
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null;
      if (
        target &&
        (target.tagName === "INPUT" ||
          target.tagName === "TEXTAREA" ||
          target.tagName === "SELECT" ||
          target.isContentEditable)
      ) {
        return;
      }
      if (document.querySelector('[role="dialog"]')) return;

      switch (event.key) {
        case " ":
          event.preventDefault();
          player.toggle();
          break;
        case "ArrowRight":
          event.preventDefault();
          player.seek(player.progress + 5);
          break;
        case "ArrowLeft":
          event.preventDefault();
          player.seek(Math.max(0, player.progress - 5));
          break;
        case "n":
        case "N":
          player.next();
          break;
        case "p":
        case "P":
          player.prev();
          break;
        case "m":
        case "M":
          player.toggleMute();
          break;
        case "q":
        case "Q":
          setQueueOpen((value) => !value);
          break;
        default:
          break;
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [player]);

  const current = player.current;
  if (!current) return null;

  const nextTrack = player.queue[player.index + 1] ?? null;
  const progress = Math.min(player.progress, player.duration || 0);

  return (
    <>
      {queueOpen ? (
        <div className="animate-pop-in fixed right-2 bottom-[96px] z-50 flex max-h-[58vh] w-[min(400px,calc(100vw-1rem))] flex-col overflow-hidden rounded-2xl border border-line bg-panel shadow-2xl">
          <div className="flex items-center justify-between border-b border-line px-4 py-3">
            <div>
              <p className="text-[10px] font-semibold tracking-[0.16em] text-muted uppercase">
                Up next
              </p>
              <p className="mt-0.5 font-mono text-[11px] text-muted">
                {player.queue.length} in queue
              </p>
            </div>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => {
                  player.clearQueue();
                  setQueueOpen(false);
                  toast("Queue cleared", "info");
                }}
                className="flex items-center gap-1.5 rounded-md px-2 py-1 text-xs text-muted hover:text-danger"
              >
                <TrashIcon className="h-3 w-3" />
                Clear queue
              </button>
              <button
                type="button"
                onClick={() => setQueueOpen(false)}
                aria-label="Close queue"
                className="rounded-md p-1 text-muted hover:text-ink"
              >
                <CloseIcon />
              </button>
            </div>
          </div>
          <ul className="min-h-0 flex-1 space-y-0.5 overflow-y-auto p-2">
            {player.queue.length === 0 ? (
              <li className="px-2 py-8 text-center text-sm text-muted">
                Nothing queued.
              </li>
            ) : (
              player.queue.map((item, at) => (
                <QueueRow
                  key={`${item.id}-${at}`}
                  item={item}
                  at={at}
                  active={at === player.index}
                  onDrop={(from, to) => player.move(from, to)}
                  onRemove={player.removeAt}
                />
              ))
            )}
          </ul>
          <p className="border-t border-line px-4 py-2 text-[11px] text-muted">
            Drag to reorder · {nextTrack ? `Next: ${nextTrack.title}` : "End of queue"}
          </p>
        </div>
      ) : null}

      <div className="fixed inset-x-0 bottom-0 z-50 border-t border-line bg-panel/90 backdrop-blur-xl">
        <div className="mx-auto max-w-[1600px] px-3 pt-2 md:px-6">
          <div className="flex items-center gap-2.5">
            <span className="w-10 shrink-0 text-right font-mono text-[10px] text-muted tabular-nums">
              {clock(progress)}
            </span>
            <BarSlider
              value={progress}
              max={player.duration || 0}
              step={0.1}
              onChange={player.seek}
              ariaLabel="Seek"
              className="flex-1"
            />
            <span className="w-10 shrink-0 font-mono text-[10px] text-muted tabular-nums">
              {clock(player.duration)}
            </span>
          </div>

          <div className="grid grid-cols-[1fr_auto] items-center gap-3 py-2 sm:grid-cols-[1fr_auto_1fr]">
            <div className="flex min-w-0 items-center gap-3">
              <div className="h-12 w-12 shrink-0 overflow-hidden rounded-lg border border-line bg-panel-2">
                {current.thumbUrl ? (
                  <img
                    src={current.thumbUrl}
                    alt=""
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <span className="flex h-full items-center justify-center text-sm text-muted uppercase">
                    {current.kind.slice(0, 1)}
                  </span>
                )}
              </div>
              <div className="min-w-0">
                <Link
                  href={
                    current.kind === "music" || current.kind === "video"
                      ? `/watch/${current.id}`
                      : `/read/${current.id}`
                  }
                  className="block truncate text-sm font-medium hover:underline"
                >
                  {current.title}
                </Link>
                <p className="mt-0.5 flex items-center gap-1.5 truncate font-mono text-[10px] text-muted">
                  {player.playing ? <Equalizer /> : null}
                  {player.playing ? "Playing" : "Paused"} · {kindLabel(current.kind)}
                </p>
              </div>
            </div>

            <div className="order-3 col-span-2 flex items-center justify-center gap-0.5 sm:order-none sm:col-span-1">
              <button
                type="button"
                onClick={player.toggleShuffle}
                aria-label="Shuffle"
                aria-pressed={player.shuffle}
                className={`rounded-lg p-2 transition-colors ${
                  player.shuffle ? "text-accent" : "text-muted hover:text-ink"
                }`}
              >
                <ShuffleIcon />
              </button>
              <button
                type="button"
                onClick={player.prev}
                aria-label="Previous"
                className="rounded-lg p-2 text-muted transition-colors hover:text-ink"
              >
                <PrevIcon />
              </button>
              <button
                type="button"
                onClick={player.toggle}
                aria-label={player.playing ? "Pause" : "Play"}
                className="mx-1 flex h-11 w-11 items-center justify-center rounded-full bg-accent text-accent-ink shadow-lg transition-transform hover:scale-105"
              >
                {player.playing ? (
                  <PauseIcon className="h-4 w-4" />
                ) : (
                  <PlayIcon className="ml-0.5 h-4 w-4" />
                )}
              </button>
              <button
                type="button"
                onClick={player.next}
                aria-label="Next"
                className="rounded-lg p-2 text-muted transition-colors hover:text-ink"
              >
                <NextIcon />
              </button>
              <button
                type="button"
                onClick={player.cycleRepeat}
                aria-label={`Repeat: ${player.repeat}`}
                className={`hidden rounded-lg p-2 transition-colors sm:block ${
                  player.repeat === "off" ? "text-muted hover:text-ink" : "text-accent"
                }`}
              >
                {player.repeat === "one" ? <RepeatOneIcon /> : <RepeatIcon />}
              </button>
            </div>

            <div className="flex items-center justify-end gap-1">
              <button
                type="button"
                onClick={player.toggleMute}
                aria-label={player.muted ? "Unmute" : "Mute"}
                className="rounded-lg p-2 text-muted transition-colors hover:text-ink"
              >
                {player.muted || player.volume === 0 ? <MuteIcon /> : <VolumeIcon />}
              </button>
              <BarSlider
                value={player.muted ? 0 : player.volume}
                max={1}
                step={0.01}
                onChange={player.setVolume}
                ariaLabel="Volume"
                className="hidden w-20 lg:flex"
              />
              <button
                type="button"
                onClick={() => setQueueOpen((value) => !value)}
                aria-label="Queue"
                aria-pressed={queueOpen}
                className={`relative rounded-lg p-2 transition-colors ${
                  queueOpen ? "text-accent" : "text-muted hover:text-ink"
                }`}
              >
                <QueueIcon />
                {player.queue.length > 0 ? (
                  <span className="absolute -top-0.5 -right-0.5 rounded-full bg-accent px-1 font-mono text-[9px] leading-4 font-semibold text-accent-ink tabular-nums">
                    {player.queue.length}
                  </span>
                ) : null}
              </button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

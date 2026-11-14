/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useState } from "react";

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
} from "@/components/icons";
import { usePlayer } from "@/lib/player";
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
      className={`group flex items-center gap-2.5 rounded-lg border px-2 py-1.5 ${
        active
          ? "border-accent/50 bg-panel-2"
          : "border-transparent hover:bg-panel-2"
      } ${dragging ? "opacity-50" : ""}`}
    >
      <button
        type="button"
        onClick={() => player.jumpTo(at)}
        className="min-w-0 flex-1 text-left"
      >
        <span className="flex items-center gap-2.5">
          <span className="h-8 w-8 shrink-0 overflow-hidden rounded bg-canvas">
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
            <span className="mt-0.5 block text-[11px] text-muted">{item.kind}</span>
          </span>
        </span>
      </button>
      {active ? <CheckIcon className="h-3.5 w-3.5 shrink-0 text-accent" /> : null}
      <button
        type="button"
        onClick={() => onRemove(at)}
        aria-label={`Remove ${item.title} from queue`}
        className="shrink-0 rounded p-1 text-muted opacity-0 transition-opacity group-hover:opacity-100 hover:text-danger"
      >
        <CloseIcon className="h-3.5 w-3.5" />
      </button>
    </li>
  );
}

export function PlayerBar() {
  const player = usePlayer();
  const [queueOpen, setQueueOpen] = useState(false);

  const current = player.current;
  if (!current) return null;

  const nextTrack = player.queue[player.index + 1] ?? null;

  return (
    <>
      {queueOpen ? (
        <div className="fixed right-0 bottom-[72px] z-50 flex max-h-[58vh] w-[min(400px,100vw)] flex-col border-t border-l border-line bg-panel shadow-2xl">
          <div className="flex items-center justify-between border-b border-line px-3 py-2.5">
            <p className="text-xs font-medium tracking-wide text-muted uppercase">
              Queue · {player.queue.length}
            </p>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={player.clearQueue}
                className="rounded px-2 py-1 text-xs text-muted hover:text-danger"
              >
                Clear
              </button>
              <button
                type="button"
                onClick={() => setQueueOpen(false)}
                aria-label="Close queue"
                className="rounded p-1 text-muted hover:text-ink"
              >
                <CloseIcon />
              </button>
            </div>
          </div>
          <ul className="min-h-0 flex-1 space-y-1 overflow-y-auto p-2">
            {player.queue.length === 0 ? (
              <li className="px-2 py-6 text-center text-sm text-muted">
                Queue is empty.
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
          <p className="border-t border-line px-3 py-2 text-[11px] text-muted">
            Drag rows to reorder · {nextTrack ? `Next: ${nextTrack.title}` : "End of queue"}
          </p>
        </div>
      ) : null}

      <div className="fixed inset-x-0 bottom-0 z-50 border-t border-line bg-panel/95 backdrop-blur">
        <input
          type="range"
          min={0}
          max={player.duration || 0}
          step={0.1}
          value={Math.min(player.progress, player.duration || 0)}
          onChange={(event) => player.seek(Number(event.target.value))}
          aria-label="Seek"
          className="block h-1.5 w-full cursor-pointer"
        />
        <div className="mx-auto grid max-w-[1560px] grid-cols-[1fr_auto] items-center gap-3 px-3 py-2.5 sm:grid-cols-[1fr_auto_1fr] md:px-6">
          {/* Now playing */}
          <div className="flex min-w-0 items-center gap-3">
            <div className="h-11 w-11 shrink-0 overflow-hidden rounded-lg border border-line bg-panel-2">
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
              <p className="mt-0.5 truncate text-xs text-muted">
                {player.playing ? "Playing" : "Paused"} · {current.kind}
              </p>
            </div>
          </div>

          {/* Transport */}
          <div className="order-3 col-span-2 flex items-center justify-center gap-1 sm:order-none sm:col-span-1">
            <button
              type="button"
              onClick={player.toggleShuffle}
              aria-label="Shuffle"
              aria-pressed={player.shuffle}
              className={`rounded-md p-2 transition-colors ${
                player.shuffle ? "text-accent" : "text-muted hover:text-ink"
              }`}
            >
              <ShuffleIcon />
            </button>
            <button
              type="button"
              onClick={player.prev}
              aria-label="Previous"
              className="rounded-md p-2 text-muted transition-colors hover:text-ink"
            >
              <PrevIcon />
            </button>
            <button
              type="button"
              onClick={player.toggle}
              aria-label={player.playing ? "Pause" : "Play"}
              className="mx-1 flex h-10 w-10 items-center justify-center rounded-full bg-accent text-accent-ink transition-opacity hover:opacity-90"
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
              className="rounded-md p-2 text-muted transition-colors hover:text-ink"
            >
              <NextIcon />
            </button>
            <button
              type="button"
              onClick={player.cycleRepeat}
              aria-label={`Repeat: ${player.repeat}`}
              className={`hidden rounded-md p-2 transition-colors sm:block ${
                player.repeat === "off" ? "text-muted hover:text-ink" : "text-accent"
              }`}
            >
              {player.repeat === "one" ? <RepeatOneIcon /> : <RepeatIcon />}
            </button>
            <span className="ml-2 hidden text-[11px] text-muted tabular-nums md:inline">
              {clock(player.progress)} / {clock(player.duration)}
            </span>
          </div>

          {/* Output */}
          <div className="flex items-center justify-end gap-1">
            <button
              type="button"
              onClick={player.toggleMute}
              aria-label={player.muted ? "Unmute" : "Mute"}
              className="rounded-md p-2 text-muted transition-colors hover:text-ink"
            >
              {player.muted || player.volume === 0 ? <MuteIcon /> : <VolumeIcon />}
            </button>
            <input
              type="range"
              min={0}
              max={1}
              step={0.01}
              value={player.muted ? 0 : player.volume}
              onChange={(event) => player.setVolume(Number(event.target.value))}
              aria-label="Volume"
              className="hidden h-1 w-20 cursor-pointer lg:block"
            />
            <button
              type="button"
              onClick={() => setQueueOpen((value) => !value)}
              aria-label="Queue"
              aria-pressed={queueOpen}
              className={`relative rounded-md p-2 transition-colors ${
                queueOpen ? "text-accent" : "text-muted hover:text-ink"
              }`}
            >
              <QueueIcon />
              {player.queue.length > 0 ? (
                <span className="absolute -top-0.5 -right-0.5 rounded-full bg-accent px-1 text-[10px] leading-4 font-semibold text-accent-ink tabular-nums">
                  {player.queue.length}
                </span>
              ) : null}
            </button>
          </div>
        </div>
      </div>
    </>
  );
}

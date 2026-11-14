/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";

import { DeleteButton } from "@/components/DeleteButton";
import { ItemActions } from "@/components/ItemActions";
import { PauseIcon, PlayIcon } from "@/components/icons";
import { api, formatBytes, formatDate, formatDuration } from "@/lib/api";
import { usePlayer } from "@/lib/player";
import type { Item } from "@/lib/types";

export function WatchView() {
  const { id } = useParams<{ id: string }>();
  const player = usePlayer();
  const [item, setItem] = useState<Item | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    api<Item>(`/api/items/${id}`)
      .then(setItem)
      .catch((err: Error) => setError(err.message));
  }, [id]);

  if (error) {
    return (
      <div className="max-w-xl">
        <p className="rounded-xl border border-danger/40 bg-panel px-4 py-3 text-sm text-danger">
          {error}
        </p>
        <Link href="/" className="mt-4 inline-block text-sm text-muted hover:text-ink">
          ← Back to library
        </Link>
      </div>
    );
  }

  if (!item) {
    return (
      <div className="max-w-4xl animate-pulse">
        <div className="h-4 w-24 rounded bg-panel-2" />
        <div className="mt-4 h-7 w-2/3 rounded bg-panel-2" />
        <div className="mt-6 aspect-video w-full rounded-2xl bg-panel-2" />
      </div>
    );
  }

  const isMusic = item.kind === "music";
  const isCurrent = player.current?.id === item.id;
  const duration = formatDuration(item.durationMs);

  return (
    <div className="max-w-4xl">
      <Link href="/" className="text-sm text-muted transition-colors hover:text-ink">
        ← Library
      </Link>

      <h1 className="mt-3 text-2xl leading-tight font-semibold tracking-tight">
        {item.title}
      </h1>
      <p className="mt-1.5 text-sm text-muted">
        {item.kind} · {formatBytes(item.bytes)}
        {duration ? ` · ${duration}` : ""} · {formatDate(item.createdAt)}
      </p>

      <div
        className={`relative mt-6 overflow-hidden rounded-2xl border border-line bg-black ${
          isMusic ? "mx-auto aspect-square w-full max-w-sm" : "aspect-video w-full"
        }`}
      >
        {item.thumbUrl ? (
          <img
            src={item.thumbUrl}
            alt=""
            className="h-full w-full object-cover"
          />
        ) : (
          <div className="flex h-full items-center justify-center text-5xl text-line-strong">
            {isMusic ? "♪" : "▶"}
          </div>
        )}
        <div className="absolute inset-0 bg-black/25" />
        <button
          type="button"
          onClick={() => {
            if (isCurrent) player.toggle();
            else player.playItem(item);
          }}
          aria-label={isCurrent && player.playing ? "Pause" : "Play"}
          className="absolute inset-0 flex items-center justify-center"
        >
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-accent text-accent-ink shadow-xl transition-transform hover:scale-105">
            {isCurrent && player.playing ? (
              <PauseIcon className="h-6 w-6" />
            ) : (
              <PlayIcon className="ml-1 h-6 w-6" />
            )}
          </span>
        </button>
      </div>
      <p className="mt-2 text-center text-xs text-muted">
        Plays in the bottom bar. Double-click the floating video for fullscreen.
      </p>

      <div className="mt-6 border-t border-line pt-5">
        <ItemActions item={item} />
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <DeleteButton id={item.id} />
          {item.sourceUrl ? (
            <a
              href={item.sourceUrl}
              target="_blank"
              rel="noreferrer"
              className="rounded-lg border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
            >
              Source
            </a>
          ) : null}
          <a
            href={item.fileUrl}
            className="rounded-lg border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
          >
            Open file
          </a>
        </div>
      </div>
    </div>
  );
}

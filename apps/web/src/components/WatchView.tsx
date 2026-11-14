/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";

import { DeleteButton } from "@/components/DeleteButton";
import { ItemActions } from "@/components/ItemActions";
import { PauseIcon, PlayIcon } from "@/components/icons";
import { useToast } from "@/components/ui/Toast";
import { api, formatBytes, formatDate, formatDuration } from "@/lib/api";
import { kindLabel } from "@/lib/kind";
import { usePlayer } from "@/lib/player";
import type { Item } from "@/lib/types";

export function WatchView() {
  const { id } = useParams<{ id: string }>();
  const player = usePlayer();
  const toast = useToast();
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
        <div className="mt-4 h-9 w-2/3 rounded bg-panel-2" />
        <div className="mt-6 aspect-video w-full rounded-2xl bg-panel-2" />
      </div>
    );
  }

  const isMusic = item.kind === "music";
  const isCurrent = player.current?.id === item.id;
  const duration = formatDuration(item.durationMs);

  return (
    <div className="max-w-5xl">
      <Link href="/" className="text-sm text-muted transition-colors hover:text-ink">
        ← Library
      </Link>

      <section className="relative mt-4 overflow-hidden rounded-3xl border border-line bg-panel">
        {item.thumbUrl ? (
          <img
            src={item.thumbUrl}
            alt=""
            aria-hidden
            className="absolute inset-0 h-full w-full scale-125 object-cover opacity-20 blur-3xl"
          />
        ) : null}
        <div className="relative grid gap-6 p-5 sm:grid-cols-[240px_minmax(0,1fr)] sm:p-7">
          <div
            className={`relative overflow-hidden rounded-2xl border border-line bg-black shadow-2xl ${
              isMusic ? "aspect-square" : "aspect-video sm:mt-4"
            }`}
          >
            {item.thumbUrl ? (
              <img src={item.thumbUrl} alt="" className="h-full w-full object-cover" />
            ) : (
              <div className="flex h-full items-center justify-center text-5xl text-line-strong">
                {isMusic ? "♪" : "▶"}
              </div>
            )}
            <button
              type="button"
              onClick={() => {
                if (isCurrent) player.toggle();
                else player.playItem(item);
              }}
              aria-label={isCurrent && player.playing ? "Pause" : "Play"}
              className="absolute inset-0 flex items-center justify-center bg-black/20 transition-colors hover:bg-black/30"
            >
              <span className="flex h-14 w-14 items-center justify-center rounded-full bg-accent text-accent-ink shadow-xl transition-transform hover:scale-105">
                {isCurrent && player.playing ? (
                  <PauseIcon className="h-5 w-5" />
                ) : (
                  <PlayIcon className="ml-0.5 h-5 w-5" />
                )}
              </span>
            </button>
          </div>

          <div className="flex min-w-0 flex-col justify-end">
            <p className="text-[10px] font-semibold tracking-[0.18em] text-muted uppercase">
              {kindLabel(item.kind)}
            </p>
            <h1 className="mt-1.5 text-3xl leading-[1.1] font-semibold tracking-tight sm:text-[2.6rem]">
              {item.title}
            </h1>
            <div className="mt-3 flex flex-wrap gap-1.5">
              <span className="rounded-full border border-line px-2.5 py-1 font-mono text-[11px] text-muted">
                {formatBytes(item.bytes)}
              </span>
              {duration ? (
                <span className="rounded-full border border-line px-2.5 py-1 font-mono text-[11px] text-muted">
                  {duration}
                </span>
              ) : null}
              <span className="rounded-full border border-line px-2.5 py-1 font-mono text-[11px] text-muted">
                {formatDate(item.createdAt)}
              </span>
            </div>

            <div className="mt-5">
              <ItemActions item={item} />
            </div>
            <p className="mt-3 text-[11px] text-muted">
              {item.kind === "video"
                ? "Video opens in the floating panel. Double-click it for fullscreen."
                : "Audio keeps playing while you browse. Use the bar below."}
            </p>
          </div>
        </div>
      </section>

      <div className="mt-5 flex flex-wrap items-center gap-2">
        <DeleteButton id={item.id} />
        {item.sourceUrl ? (
          <a
            href={item.sourceUrl}
            target="_blank"
            rel="noreferrer"
            className="rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
          >
            Source
          </a>
        ) : null}
        <a
          href={item.fileUrl}
          className="rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
        >
          Open file
        </a>
        <button
          type="button"
          onClick={() => {
            void navigator.clipboard?.writeText(item.fileUrl);
            toast("File link copied", "success");
          }}
          className="rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
        >
          Copy link
        </button>
      </div>
    </div>
  );
}

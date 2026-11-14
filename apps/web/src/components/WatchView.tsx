/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";

import { DeleteButton } from "@/components/DeleteButton";
import { api, formatBytes, formatDate, formatDuration } from "@/lib/api";
import type { Item } from "@/lib/types";

export function WatchView() {
  const { id } = useParams<{ id: string }>();
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

      {isMusic ? (
        <div className="mt-6 flex flex-col gap-6 rounded-2xl border border-line bg-panel p-5 sm:flex-row sm:items-center sm:p-6">
          <div className="h-44 w-44 shrink-0 overflow-hidden rounded-xl border border-line bg-panel-2">
            {item.thumbUrl ? (
              <img
                src={item.thumbUrl}
                alt=""
                className="h-full w-full object-cover"
              />
            ) : (
              <div className="flex h-full items-center justify-center text-4xl text-line-strong">
                ♪
              </div>
            )}
          </div>
          <div className="min-w-0 flex-1">
            <audio src={item.fileUrl} controls className="w-full" />
            <p className="mt-3 text-xs leading-relaxed text-muted">
              The cover is embedded in the file too, so it shows up in other
              players.
            </p>
          </div>
        </div>
      ) : (
        <video
          src={item.fileUrl}
          poster={item.thumbUrl ?? undefined}
          controls
          className="mt-6 aspect-video w-full rounded-2xl border border-line bg-black"
        />
      )}

      <div className="mt-6 flex flex-wrap items-center gap-2 border-t border-line pt-5">
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
  );
}

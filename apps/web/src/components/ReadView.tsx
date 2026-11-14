/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";

import { DeleteButton } from "@/components/DeleteButton";
import { api, formatBytes, formatDate } from "@/lib/api";
import type { Item } from "@/lib/types";

export function ReadView() {
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
      <div className="max-w-3xl animate-pulse">
        <div className="h-4 w-24 rounded bg-panel-2" />
        <div className="mt-4 h-7 w-1/2 rounded bg-panel-2" />
        <div className="mt-6 h-[60vh] rounded-2xl bg-panel-2" />
      </div>
    );
  }

  const isPicture = item.kind === "picture" || item.mime.startsWith("image/");
  const isPdf = item.mime === "application/pdf";

  return (
    <div className="max-w-4xl">
      <Link href="/" className="text-sm text-muted transition-colors hover:text-ink">
        ← Library
      </Link>

      <h1 className="mt-3 text-2xl leading-tight font-semibold tracking-tight">
        {item.title}
      </h1>
      <p className="mt-1.5 text-sm text-muted">
        {item.kind} · {formatBytes(item.bytes)} · {formatDate(item.createdAt)}
      </p>

      <div className="mt-6 overflow-hidden rounded-2xl border border-line bg-panel">
        {isPicture ? (
          <img
            src={item.fileUrl}
            alt={item.title}
            className="mx-auto max-h-[75vh] w-auto max-w-full object-contain"
          />
        ) : isPdf ? (
          <iframe
            src={item.fileUrl}
            title={item.title}
            className="h-[75vh] w-full"
          />
        ) : (
          <div className="flex flex-col items-center gap-3 px-6 py-16 text-center">
            <p className="text-sm text-muted">
              This format can&apos;t be previewed here.
            </p>
            <a
              href={item.fileUrl}
              className="rounded-lg border border-line px-3.5 py-2 text-sm text-ink"
            >
              Download file
            </a>
          </div>
        )}
      </div>

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

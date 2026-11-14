/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { DeleteButton } from "@/components/DeleteButton";
import { ItemActions } from "@/components/ItemActions";
import { OpenIcon } from "@/components/icons";
import { useToast } from "@/components/ui/Toast";
import { api, formatBytes, formatDate } from "@/lib/api";
import { kindLabel } from "@/lib/kind";
import type { Item } from "@/lib/types";

export function ReadView() {
  const { id } = useParams<{ id: string }>();
  const toast = useToast();
  const [item, setItem] = useState<Item | null>(null);
  const [error, setError] = useState<string | null>(null);
  const autoOpened = useRef(false);

  useEffect(() => {
    if (!id) return;
    autoOpened.current = false;
    api<Item>(`/api/items/${id}`)
      .then(setItem)
      .catch((err: Error) => setError(err.message));
  }, [id]);

  const openNative = useCallback(async () => {
    if (!item) return;
    try {
      await api(`/api/items/${item.id}/open`, { method: "POST" });
      toast("Opened in your default app", "success");
    } catch (err) {
      toast((err as Error).message, "error");
    }
  }, [item, toast]);

  // Books belong in the native reader; launch it once when the page opens.
  useEffect(() => {
    if (!item || item.kind !== "book" || autoOpened.current) return;
    autoOpened.current = true;
    void openNative();
  }, [item, openNative]);

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
        <div className="mt-4 h-9 w-1/2 rounded bg-panel-2" />
        <div className="mt-6 h-[55vh] rounded-2xl bg-panel-2" />
      </div>
    );
  }

  const isBook = item.kind === "book";
  const isPicture = item.kind === "picture" || item.mime.startsWith("image/");

  return (
    <div className="max-w-4xl">
      <Link href="/" className="text-sm text-muted transition-colors hover:text-ink">
        ← Library
      </Link>

      <p className="mt-4 text-[10px] font-semibold tracking-[0.18em] text-muted uppercase">
        {kindLabel(item.kind)}
      </p>
      <h1 className="mt-1 text-3xl leading-tight font-semibold tracking-tight sm:text-4xl">
        {item.title}
      </h1>
      <div className="mt-3 flex flex-wrap gap-1.5">
        <span className="rounded-full border border-line px-2.5 py-1 font-mono text-[11px] text-muted">
          {formatBytes(item.bytes)}
        </span>
        <span className="rounded-full border border-line px-2.5 py-1 font-mono text-[11px] text-muted">
          {formatDate(item.createdAt)}
        </span>
        <span className="rounded-full border border-line px-2.5 py-1 font-mono text-[11px] text-muted uppercase">
          {item.mime.split("/").pop()}
        </span>
      </div>

      {isBook ? (
        <div className="mt-6 flex flex-col gap-6 sm:flex-row sm:items-start">
          <div className="mx-auto w-full max-w-[260px] shrink-0 overflow-hidden rounded-2xl border border-line bg-panel-2 shadow-xl sm:mx-0">
            {item.thumbUrl ? (
              <img
                src={item.thumbUrl}
                alt={`Cover of ${item.title}`}
                className="aspect-[3/4] w-full object-cover"
              />
            ) : (
              <div className="flex aspect-[3/4] items-center justify-center text-5xl text-line-strong">
                ▤
              </div>
            )}
          </div>

          <div className="min-w-0 flex-1">
            <div className="rounded-2xl border border-line bg-panel p-5">
              <h2 className="text-lg font-semibold tracking-tight">
                Read it in your own reader
              </h2>
              <p className="mt-1.5 text-sm leading-relaxed text-muted">
                Books open in your default PDF or EPUB app. The cover is the
                file&apos;s first page; nothing is rendered in the browser.
              </p>
              <div className="mt-4 flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={openNative}
                  className="flex items-center gap-2 rounded-full bg-accent px-4 py-2 text-sm font-semibold text-accent-ink transition-opacity hover:opacity-90"
                >
                  <OpenIcon className="h-4 w-4" />
                  Open in app
                </button>
                <a
                  href={item.fileUrl}
                  className="rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
                >
                  Save a copy
                </a>
              </div>
            </div>

            <div className="mt-4">
              <ItemActions item={item} />
            </div>
            <div className="mt-3">
              <DeleteButton id={item.id} />
            </div>
          </div>
        </div>
      ) : null}

      {isPicture ? (
        <>
          <div className="mt-6 overflow-hidden rounded-2xl border border-line bg-panel">
            <img
              src={item.fileUrl}
              alt={item.title}
              className="mx-auto max-h-[75vh] w-auto max-w-full object-contain"
            />
          </div>
          <div className="mt-5 flex flex-wrap items-center gap-2">
            <ItemActions item={item} />
          </div>
          <div className="mt-3 flex flex-wrap items-center gap-2">
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
          </div>
        </>
      ) : null}

      {!isBook && !isPicture ? (
        <>
          <div className="mt-6 flex flex-col items-center gap-3 rounded-2xl border border-line bg-panel px-6 py-20 text-center">
            <p className="text-sm text-muted">
              This format cannot be previewed in the browser.
            </p>
            <a
              href={item.fileUrl}
              className="rounded-full border border-line px-4 py-2 text-sm text-ink"
            >
              Download file
            </a>
          </div>
          <div className="mt-5">
            <ItemActions item={item} />
          </div>
          <div className="mt-3">
            <DeleteButton id={item.id} />
          </div>
        </>
      ) : null}
    </div>
  );
}

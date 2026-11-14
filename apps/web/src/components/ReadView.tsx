/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { DeleteButton } from "@/components/DeleteButton";
import { ItemActions } from "@/components/ItemActions";
import { OpenIcon } from "@/components/icons";
import { api, formatBytes, formatDate } from "@/lib/api";
import type { Item } from "@/lib/types";

export function ReadView() {
  const { id } = useParams<{ id: string }>();
  const [item, setItem] = useState<Item | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
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
      setNotice("Opened in your default app");
    } catch (err) {
      setNotice((err as Error).message);
    }
  }, [item]);

  // Books belong in the native reader; launch it once when the page opens.
  useEffect(() => {
    if (!item || item.kind !== "book" || autoOpened.current) return;
    autoOpened.current = true;
    void openNative();
  }, [item, openNative]);

  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(null), 4000);
    return () => clearTimeout(timer);
  }, [notice]);

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

  const isBook = item.kind === "book";
  const isPicture = item.kind === "picture" || item.mime.startsWith("image/");

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

      {isBook ? (
        <div className="mt-6 flex flex-col gap-6 sm:flex-row sm:items-start">
          <div className="mx-auto w-full max-w-[260px] shrink-0 overflow-hidden rounded-xl border border-line bg-panel-2 sm:mx-0">
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
            <div className="rounded-xl border border-line bg-panel p-4">
              <p className="text-sm text-muted">
                Books open in your default PDF or EPUB reader, not inside the
                browser. The cover is the first page of the file.
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={openNative}
                  className="flex items-center gap-2 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-accent-ink transition-opacity hover:opacity-90"
                >
                  <OpenIcon className="h-4 w-4" />
                  Open in app
                </button>
                <a
                  href={item.fileUrl}
                  className="rounded-lg border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
                >
                  Save a copy
                </a>
              </div>
              {notice ? (
                <p className="mt-3 text-xs text-muted" role="status">
                  {notice}
                </p>
              ) : null}
            </div>

            <div className="mt-4">
              <ItemActions item={item} />
            </div>
            <div className="mt-4">
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
        </>
      ) : null}

      {!isBook && !isPicture ? (
        <>
          <div className="mt-6 flex flex-col items-center gap-3 rounded-2xl border border-line bg-panel px-6 py-16 text-center">
            <p className="text-sm text-muted">
              This format cannot be previewed in the browser.
            </p>
            <a
              href={item.fileUrl}
              className="rounded-lg border border-line px-3.5 py-2 text-sm text-ink"
            >
              Download file
            </a>
          </div>
          <div className="mt-6 border-t border-line pt-5">
            <ItemActions item={item} />
            <div className="mt-3">
              <DeleteButton id={item.id} />
            </div>
          </div>
        </>
      ) : null}
    </div>
  );
}

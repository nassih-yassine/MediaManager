/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";

import { api, formatBytes, formatDate, formatDuration } from "@/lib/api";
import { type Item, type ItemsPage, type Kind, KINDS } from "@/lib/types";

function hrefFor(item: Item) {
  return item.kind === "music" || item.kind === "video"
    ? `/watch/${item.id}`
    : `/read/${item.id}`;
}

function Placeholder({ kind }: { kind: Kind }) {
  const glyph: Record<Kind, string> = {
    music: "♪",
    video: "▶",
    picture: "◧",
    book: "▤",
  };
  return (
    <div className="flex h-full w-full items-center justify-center bg-panel-2 text-3xl text-line-strong">
      {glyph[kind]}
    </div>
  );
}

function Card({ item }: { item: Item }) {
  const duration = formatDuration(item.durationMs);
  return (
    <Link href={hrefFor(item)} className="group block">
      <div className="relative aspect-[4/3] overflow-hidden rounded-xl border border-line bg-panel-2">
        {item.thumbUrl ? (
          <img
            src={item.thumbUrl}
            alt=""
            loading="lazy"
            className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
          />
        ) : (
          <Placeholder kind={item.kind} />
        )}
        <div className="absolute inset-0 flex items-end justify-between bg-black/25 p-2.5 opacity-0 transition-opacity duration-200 group-hover:opacity-100">
          <span className="rounded-md bg-black/55 px-2 py-1 text-[11px] font-medium tracking-wide text-white uppercase">
            {item.kind}
          </span>
          {duration ? (
            <span className="rounded-md bg-black/55 px-2 py-1 text-[11px] text-white tabular-nums">
              {duration}
            </span>
          ) : null}
        </div>
      </div>
      <div className="mt-2.5 px-0.5">
        <h3 className="line-clamp-2 text-[13.5px] leading-snug font-medium">
          {item.title}
        </h3>
        <p className="mt-1 text-xs text-muted">
          {formatBytes(item.bytes)} · {formatDate(item.createdAt)}
        </p>
      </div>
    </Link>
  );
}

function Skeleton() {
  return (
    <div className="animate-pulse">
      <div className="aspect-[4/3] rounded-xl border border-line bg-panel-2" />
      <div className="mt-2.5 h-3 w-4/5 rounded bg-panel-2" />
      <div className="mt-2 h-3 w-2/5 rounded bg-panel-2" />
    </div>
  );
}

export function Library() {
  const [items, setItems] = useState<Item[]>([]);
  const [total, setTotal] = useState(0);
  const [cursor, setCursor] = useState<string | null>(null);
  const [kind, setKind] = useState<Kind | "all">("all");
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const requestId = useRef(0);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(query.trim()), 250);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    const id = ++requestId.current;
    setLoading(true);
    setError(null);

    const params = new URLSearchParams({ limit: "48" });
    if (kind !== "all") params.set("kind", kind);
    if (debounced) params.set("q", debounced);

    api<ItemsPage>(`/api/items?${params}`)
      .then((page) => {
        if (id !== requestId.current) return;
        setItems(page.items);
        setTotal(page.total);
        setCursor(page.nextCursor);
      })
      .catch((err: Error) => {
        if (id !== requestId.current) return;
        setError(err.message);
      })
      .finally(() => {
        if (id === requestId.current) setLoading(false);
      });
  }, [kind, debounced]);

  async function loadMore() {
    if (!cursor || loadingMore) return;
    setLoadingMore(true);
    const params = new URLSearchParams({ limit: "48", cursor });
    if (kind !== "all") params.set("kind", kind);
    if (debounced) params.set("q", debounced);
    try {
      const page = await api<ItemsPage>(`/api/items?${params}`);
      setItems((prev) => [...prev, ...page.items]);
      setCursor(page.nextCursor);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoadingMore(false);
    }
  }

  const countLabel = useMemo(() => {
    if (loading) return "Loading…";
    if (total === 0) return "Nothing yet";
    return `${total.toLocaleString()} ${total === 1 ? "item" : "items"}`;
  }, [loading, total]);

  return (
    <div>
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Library</h1>
          <p className="mt-1 text-sm text-muted">{countLabel}</p>
        </div>
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search titles…"
          className="h-10 w-full rounded-lg border border-line bg-panel px-3.5 text-sm placeholder:text-muted/70 focus:border-line-strong focus:outline-none sm:w-72"
        />
      </header>

      <div className="mt-6 flex gap-1 overflow-x-auto border-b border-line pb-px">
        {KINDS.map((entry) => (
          <button
            key={entry.value}
            type="button"
            onClick={() => setKind(entry.value)}
            className={`-mb-px border-b-2 px-3 py-2 text-sm whitespace-nowrap transition-colors ${
              kind === entry.value
                ? "border-accent text-ink"
                : "border-transparent text-muted hover:text-ink"
            }`}
          >
            {entry.label}
          </button>
        ))}
      </div>

      {error ? (
        <div className="mt-8 rounded-xl border border-danger/40 bg-panel px-4 py-3 text-sm text-danger">
          {error}. Is the backend running on :8787?
        </div>
      ) : null}

      {loading ? (
        <div className="mt-6 grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3 lg:grid-cols-4 2xl:grid-cols-5">
          {Array.from({ length: 8 }).map((_, index) => (
            <Skeleton key={index} />
          ))}
        </div>
      ) : items.length === 0 ? (
        <div className="mt-10 flex flex-col items-center justify-center rounded-2xl border border-dashed border-line px-6 py-20 text-center">
          <p className="text-lg font-medium">
            {debounced ? "No matches" : "Your library is empty"}
          </p>
          <p className="mt-2 max-w-sm text-sm text-muted">
            {debounced
              ? "Try a different title or clear the search."
              : "Paste a link and pick what to keep — audio with its cover, or the full video."}
          </p>
          {!debounced ? (
            <Link
              href="/add"
              className="mt-6 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-accent-ink transition-opacity hover:opacity-90"
            >
              Add media
            </Link>
          ) : null}
        </div>
      ) : (
        <>
          <div className="mt-6 grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3 lg:grid-cols-4 2xl:grid-cols-5">
            {items.map((item) => (
              <Card key={item.id} item={item} />
            ))}
          </div>

          {cursor ? (
            <div className="mt-10 flex justify-center">
              <button
                type="button"
                onClick={loadMore}
                disabled={loadingMore}
                className="rounded-lg border border-line bg-panel px-4 py-2 text-sm text-muted transition-colors hover:text-ink disabled:opacity-50"
              >
                {loadingMore ? "Loading…" : "Load more"}
              </button>
            </div>
          ) : null}
        </>
      )}
    </div>
  );
}

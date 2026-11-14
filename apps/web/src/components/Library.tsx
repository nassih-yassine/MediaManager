/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";

import {
  CloseIcon,
  PlayIcon,
  PlusIcon,
  QueueIcon,
  TrashIcon,
} from "@/components/icons";
import { api, formatBytes, formatDate, formatDuration } from "@/lib/api";
import { usePlayer } from "@/lib/player";
import {
  type Item,
  type ItemsPage,
  type Kind,
  KINDS,
  type PlaylistSummary,
} from "@/lib/types";

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

function Card({
  item,
  selected,
  onToggle,
  onDelete,
}: {
  item: Item;
  selected: boolean;
  onToggle: (id: string) => void;
  onDelete: (id: string) => void;
}) {
  const player = usePlayer();
  const duration = formatDuration(item.durationMs);
  const playable = item.kind === "music" || item.kind === "video";

  return (
    <Link href={hrefFor(item)} className="group block">
      <div
        className={`relative aspect-[4/3] overflow-hidden rounded-xl border bg-panel-2 transition-colors ${
          selected ? "border-accent" : "border-line"
        }`}
      >
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

        <button
          type="button"
          aria-label={selected ? "Deselect" : "Select"}
          onClick={(event) => {
            event.preventDefault();
            event.stopPropagation();
            onToggle(item.id);
          }}
          className={`absolute top-2 right-2 z-10 flex h-6 w-6 items-center justify-center rounded-md border text-[11px] transition-opacity ${
            selected
              ? "border-accent bg-accent text-accent-ink opacity-100"
              : "border-white/30 bg-black/40 text-white opacity-0 group-hover:opacity-100"
          }`}
        >
          {selected ? "✓" : ""}
        </button>

        <button
          type="button"
          aria-label={`Delete ${item.title}`}
          onClick={(event) => {
            event.preventDefault();
            event.stopPropagation();
            onDelete(item.id);
          }}
          className="absolute top-2 left-2 z-10 rounded-md border border-white/30 bg-black/40 p-1.5 text-white opacity-0 transition-opacity group-hover:opacity-100 hover:border-danger/60 hover:text-danger"
        >
          <TrashIcon className="h-3.5 w-3.5" />
        </button>

        {playable ? (
          <button
            type="button"
            aria-label={`Play ${item.title}`}
            onClick={(event) => {
              event.preventDefault();
              event.stopPropagation();
              player.playItem(item);
            }}
            className="absolute inset-0 z-[5] flex items-center justify-center opacity-0 transition-opacity group-hover:opacity-100"
          >
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-accent text-accent-ink shadow-lg">
              <PlayIcon className="ml-0.5 h-5 w-5" />
            </span>
          </button>
        ) : null}

        <div className="pointer-events-none absolute inset-x-0 bottom-0 flex items-end justify-between bg-black/35 p-2.5 opacity-0 transition-opacity group-hover:opacity-100">
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
  const player = usePlayer();
  const [items, setItems] = useState<Item[]>([]);
  const [total, setTotal] = useState(0);
  const [cursor, setCursor] = useState<string | null>(null);
  const [kind, setKind] = useState<Kind | "all">("all");
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [playlists, setPlaylists] = useState<PlaylistSummary[]>([]);
  const [targetPlaylist, setTargetPlaylist] = useState("");
  const [notice, setNotice] = useState<string | null>(null);
  const requestId = useRef(0);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(query.trim()), 250);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    api<{ playlists: PlaylistSummary[] }>("/api/playlists")
      .then((data) => {
        setPlaylists(data.playlists);
        setTargetPlaylist((current) => current || data.playlists[0]?.id || "");
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(null), 3000);
    return () => clearTimeout(timer);
  }, [notice]);

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

  function toggle(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  const selectedItems = useMemo(
    () => items.filter((item) => selected.has(item.id)),
    [items, selected],
  );

  function playSelected() {
    if (selectedItems.length === 0) return;
    player.playNow(selectedItems, 0);
    setNotice(`Playing ${selectedItems.length} item(s)`);
  }

  async function addSelectedToPlaylist() {
    const playlist = playlists.find((p) => p.id === targetPlaylist);
    if (!playlist || selectedItems.length === 0) return;
    try {
      await api(`/api/playlists/${playlist.id}/items`, {
        method: "POST",
        body: JSON.stringify({ itemIds: selectedItems.map((i) => i.id) }),
      });
      setNotice(`Added ${selectedItems.length} to ${playlist.name}`);
      setSelected(new Set());
    } catch (err) {
      setNotice((err as Error).message);
    }
  }

  async function deleteIds(ids: string[]) {
    if (ids.length === 0) return;
    const label = ids.length === 1 ? "this item" : `${ids.length} items`;
    if (!window.confirm(`Delete ${label} and the file(s) on disk?`)) return;
    try {
      await Promise.all(
        ids.map((id) =>
          api(`/api/items/${id}?delete_file=true`, { method: "DELETE" }),
        ),
      );
      ids.forEach((id) => player.removeItem(id));
      setItems((prev) => prev.filter((item) => !ids.includes(item.id)));
      setTotal((value) => Math.max(0, value - ids.length));
      setSelected(new Set());
      setNotice(`Deleted ${label}`);
    } catch (err) {
      setNotice((err as Error).message);
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

      <div className="mt-6 flex items-center justify-between gap-3 border-b border-line pb-px">
        <div className="flex gap-1 overflow-x-auto">
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
        <div className="flex items-center gap-2 pb-1.5">
          {selected.size > 0 ? (
            <button
              type="button"
              onClick={() => setSelected(new Set())}
              className="flex items-center gap-1 text-xs text-muted hover:text-ink"
            >
              <CloseIcon className="h-3 w-3" />
              Clear
            </button>
          ) : (
            <button
              type="button"
              onClick={() => setSelected(new Set(items.map((i) => i.id)))}
              className="text-xs text-muted hover:text-ink"
            >
              Select all
            </button>
          )}
        </div>
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
              <Card
                key={item.id}
                item={item}
                selected={selected.has(item.id)}
                onToggle={toggle}
                onDelete={(id) => deleteIds([id])}
              />
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

      {selected.size > 0 ? (
        <div className="fixed inset-x-0 bottom-[73px] z-40 border-t border-line bg-panel/95 px-3 py-2.5 backdrop-blur md:px-6">
          <div className="mx-auto flex max-w-[1560px] flex-wrap items-center gap-2">
            <span className="mr-1 text-sm font-medium">
              {selected.size} selected
            </span>
            <button
              type="button"
              onClick={playSelected}
              className="flex items-center gap-1.5 rounded-lg bg-accent px-3 py-1.5 text-sm font-medium text-accent-ink hover:opacity-90"
            >
              <PlayIcon className="h-3.5 w-3.5" />
              Play
            </button>
            <button
              type="button"
              onClick={() => {
                player.addToQueue(selectedItems);
                setNotice(`Queued ${selectedItems.length}`);
                setSelected(new Set());
              }}
              className="flex items-center gap-1.5 rounded-lg border border-line px-3 py-1.5 text-sm text-muted hover:text-ink"
            >
              <QueueIcon className="h-3.5 w-3.5" />
              Queue
            </button>
            {playlists.length > 0 ? (
              <span className="flex items-center gap-1">
                <select
                  value={targetPlaylist}
                  onChange={(event) => setTargetPlaylist(event.target.value)}
                  className="h-8 max-w-[160px] rounded-lg border border-line px-2 text-sm text-muted"
                  aria-label="Target playlist"
                >
                  {playlists.map((playlist) => (
                    <option key={playlist.id} value={playlist.id}>
                      {playlist.name}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={addSelectedToPlaylist}
                  className="flex items-center gap-1.5 rounded-lg border border-line px-3 py-1.5 text-sm text-muted hover:text-ink"
                >
                  <PlusIcon className="h-3.5 w-3.5" />
                  Add
                </button>
              </span>
            ) : null}
            <button
              type="button"
              onClick={() => deleteIds(Array.from(selected))}
              className="flex items-center gap-1.5 rounded-lg border border-line px-3 py-1.5 text-sm text-muted hover:border-danger/50 hover:text-danger"
            >
              <TrashIcon className="h-3.5 w-3.5" />
              Delete
            </button>
            {notice ? (
              <span className="ml-auto text-xs text-muted" role="status">
                {notice}
              </span>
            ) : null}
          </div>
        </div>
      ) : null}
    </div>
  );
}

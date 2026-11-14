/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";

import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { useToast } from "@/components/ui/Toast";
import {
  BookIcon,
  ChevronDownIcon,
  ImageIcon,
  MovieIcon,
  PlayIcon,
  PlusIcon,
  QueueIcon,
  SearchIcon,
  SelectIcon,
  TrashIcon,
} from "@/components/icons";
import { api, formatBytes, formatDate, formatDuration } from "@/lib/api";
import { kindLabel } from "@/lib/kind";
import { usePlayer } from "@/lib/player";
import { SORT_OPTIONS, sortItems, type SortKey } from "@/lib/sort";
import {
  type Item,
  type ItemsPage,
  type Kind,
  KINDS,
  type PlaylistSummary,
} from "@/lib/types";

const KIND_ICON: Record<Kind, typeof BookIcon> = {
  music: PlayIcon,
  video: MovieIcon,
  picture: ImageIcon,
  book: BookIcon,
};

function hrefFor(item: Item) {
  return item.kind === "music" || item.kind === "video"
    ? `/watch/${item.id}`
    : `/read/${item.id}`;
}

function Cover({ item }: { item: Item }) {
  const Icon = KIND_ICON[item.kind];
  if (item.thumbUrl) {
    return (
      <img
        src={item.thumbUrl}
        alt=""
        loading="lazy"
        className={`h-full w-full transition-transform duration-500 group-hover:scale-[1.04] ${
          item.kind === "book" ? "bg-panel-2 object-contain p-2" : "object-cover"
        }`}
      />
    );
  }
  return (
    <div className="flex h-full w-full items-center justify-center bg-panel-2 text-line-strong">
      <Icon className="h-9 w-9" />
    </div>
  );
}

function Card({
  item,
  index,
  selected,
  selectionMode,
  onToggle,
  onPlay,
  onQueue,
  onDelete,
}: {
  item: Item;
  index: number;
  selected: boolean;
  selectionMode: boolean;
  onToggle: (id: string) => void;
  onPlay: (item: Item) => void;
  onQueue: (item: Item) => void;
  onDelete: (id: string) => void;
}) {
  const duration = formatDuration(item.durationMs);
  const playable = item.kind === "music" || item.kind === "video";

  return (
    <Link
      href={hrefFor(item)}
      onClick={(event) => {
        if (selectionMode) {
          event.preventDefault();
          onToggle(item.id);
        }
      }}
      className="group animate-fade-up block"
      style={{ animationDelay: `${Math.min(index, 12) * 22}ms` }}
    >
      <div
        className={`relative aspect-[4/3] overflow-hidden rounded-xl border bg-panel-2 transition-all duration-200 ${
          selected
            ? "border-accent ring-2 ring-accent/30"
            : "border-line group-hover:border-line-strong"
        }`}
      >
        <Cover item={item} />

        {selectionMode ? (
          <span
            className={`absolute top-2.5 right-2.5 z-10 flex h-6 w-6 items-center justify-center rounded-full border text-[11px] font-semibold ${
              selected
                ? "border-accent bg-accent text-accent-ink"
                : "border-white/50 bg-black/35 text-transparent"
            }`}
            aria-hidden
          >
            ✓
          </span>
        ) : (
          <div className="absolute top-2 right-2 z-10 flex gap-1 opacity-0 transition-opacity duration-150 group-hover:opacity-100 group-focus-within:opacity-100 [@media(hover:none)]:opacity-100">
            {playable ? (
              <button
                type="button"
                aria-label={`Queue ${item.title}`}
                onClick={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  onQueue(item);
                }}
                className="rounded-lg border border-white/25 bg-black/45 p-2 text-white backdrop-blur transition-colors hover:border-accent/60 hover:text-accent"
              >
                <PlusIcon className="h-3.5 w-3.5" />
              </button>
            ) : null}
            <button
              type="button"
              aria-label={`Delete ${item.title}`}
              onClick={(event) => {
                event.preventDefault();
                event.stopPropagation();
                onDelete(item.id);
              }}
              className="rounded-lg border border-white/25 bg-black/45 p-2 text-white backdrop-blur transition-colors hover:border-danger/60 hover:text-danger"
            >
              <TrashIcon className="h-3.5 w-3.5" />
            </button>
          </div>
        )}

        {playable && !selectionMode ? (
          <button
            type="button"
            aria-label={`Play ${item.title}`}
            onClick={(event) => {
              event.preventDefault();
              event.stopPropagation();
              onPlay(item);
            }}
            className="absolute inset-0 z-[5] flex items-center justify-center opacity-0 transition-opacity duration-150 group-hover:opacity-100 group-focus-within:opacity-100 [@media(hover:none)]:opacity-100"
          >
            <span className="flex h-12 w-12 translate-y-1 items-center justify-center rounded-full bg-accent text-accent-ink shadow-xl transition-transform duration-200 group-hover:translate-y-0">
              <PlayIcon className="ml-0.5 h-5 w-5" />
            </span>
          </button>
        ) : null}

        <div className="pointer-events-none absolute inset-x-0 bottom-0 flex items-end justify-between bg-gradient-to-t from-black/55 to-transparent p-2.5 opacity-0 transition-opacity group-hover:opacity-100">
          <span className="rounded-md bg-black/50 px-2 py-1 text-[10px] font-semibold tracking-[0.12em] text-white uppercase">
            {kindLabel(item.kind)}
          </span>
          {duration ? (
            <span className="font-mono text-[11px] text-white/90 tabular-nums">
              {duration}
            </span>
          ) : null}
        </div>

        {item.positionMs > 3_000 && item.durationMs ? (
          <div className="absolute inset-x-0 bottom-0 h-0.5 bg-black/40">
            <div
              className="h-full bg-accent"
              style={{
                width: `${Math.min(100, (item.positionMs / item.durationMs) * 100)}%`,
              }}
            />
          </div>
        ) : null}
      </div>

      <div className="mt-2.5 px-0.5">
        <h3 className="line-clamp-2 text-[13.5px] leading-snug font-medium">
          {item.title}
        </h3>
        <p className="mt-1 font-mono text-[11px] text-muted">
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
  const toast = useToast();
  const [items, setItems] = useState<Item[]>([]);
  const [total, setTotal] = useState(0);
  const [cursor, setCursor] = useState<string | null>(null);
  const [kind, setKind] = useState<Kind | "all">("all");
  const [sort, setSort] = useState<SortKey>("newest");
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [selectionMode, setSelectionMode] = useState(false);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [playlists, setPlaylists] = useState<PlaylistSummary[]>([]);
  const [targetPlaylist, setTargetPlaylist] = useState("");
  const [confirmIds, setConfirmIds] = useState<string[] | null>(null);
  const [deleting, setDeleting] = useState(false);
  const requestId = useRef(0);
  const searchRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(query.trim()), 220);
    return () => clearTimeout(timer);
  }, [query]);

  // "/" focuses search, Escape clears selection. Ignore while typing.
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null;
      const typing =
        target &&
        (target.tagName === "INPUT" ||
          target.tagName === "TEXTAREA" ||
          target.isContentEditable);
      if (typing) return;
      if (event.key === "/") {
        event.preventDefault();
        searchRef.current?.focus();
      }
      if (event.key === "Escape") {
        setSelected(new Set());
        setSelectionMode(false);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  useEffect(() => {
    api<{ playlists: PlaylistSummary[] }>("/api/playlists")
      .then((data) => {
        setPlaylists(data.playlists);
        setTargetPlaylist((current) => current || data.playlists[0]?.id || "");
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const id = ++requestId.current;
    setLoading(true);
    setError(null);

    const params = new URLSearchParams({ limit: "48", sort });
    if (kind !== "all") params.set("kind", kind);
    if (debounced) params.set("q", debounced);

    api<ItemsPage>(`/api/items?${params}`)
      .then((page) => {
        if (id !== requestId.current) return;
        setItems(sortItems(page.items, sort));
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
  }, [kind, debounced, sort, reload]);

  async function loadMore() {
    if (!cursor || loadingMore) return;
    setLoadingMore(true);
    const params = new URLSearchParams({ limit: "48", cursor, sort });
    if (kind !== "all") params.set("kind", kind);
    if (debounced) params.set("q", debounced);
    try {
      const page = await api<ItemsPage>(`/api/items?${params}`);
      setItems((prev) => sortItems([...prev, ...page.items], sort));
      setCursor(page.nextCursor);
    } catch (err) {
      toast((err as Error).message, "error");
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

  function enterSelection() {
    setSelectionMode(true);
  }

  function clearSelection() {
    setSelected(new Set());
    setSelectionMode(false);
  }

  const selectedItems = useMemo(
    () => items.filter((item) => selected.has(item.id)),
    [items, selected],
  );

  function queueItem(item: Item) {
    player.addToQueue([item]);
    toast(`Queued “${item.title}”`, "success");
  }

  async function addSelectedToPlaylist() {
    const playlist = playlists.find((p) => p.id === targetPlaylist);
    if (!playlist || selectedItems.length === 0) return;
    try {
      await api(`/api/playlists/${playlist.id}/items`, {
        method: "POST",
        body: JSON.stringify({ itemIds: selectedItems.map((i) => i.id) }),
      });
      toast(
        `Added ${selectedItems.length} to “${playlist.name}”`,
        "success",
      );
      clearSelection();
    } catch (err) {
      toast((err as Error).message, "error");
    }
  }

  async function deleteItems() {
    const ids = confirmIds ?? [];
    if (ids.length === 0) return;
    setDeleting(true);
    const results = await Promise.allSettled(
      ids.map((id) =>
        api(`/api/items/${id}?delete_file=true`, { method: "DELETE" }),
      ),
    );
    const failed = results.filter((r) => r.status === "rejected").length;
    const removed = ids.length - failed;
    if (removed > 0) {
      ids.forEach((id) => player.removeItem(id));
      setItems((prev) => prev.filter((item) => !ids.includes(item.id)));
      setTotal((value) => Math.max(0, value - removed));
      setSelected(new Set());
      setSelectionMode(false);
    }
    setDeleting(false);
    setConfirmIds(null);
    if (failed === 0) {
      toast(
        removed === 1 ? "Item deleted" : `${removed} items deleted`,
        "success",
      );
    } else {
      toast(
        `${failed} could not be deleted${removed ? `, ${removed} removed` : ""}`,
        "error",
      );
      setReload((value) => value + 1);
    }
  }

  const countLabel = useMemo(() => {
    if (loading) return "Fetching your collection…";
    if (total === 0) return "Nothing here yet";
    return `${total.toLocaleString()} ${total === 1 ? "item" : "items"}`;
  }, [loading, total]);

  return (
    <div>
      <header className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="text-[10px] font-semibold tracking-[0.18em] text-muted uppercase">
            Collection
          </p>
          <h1 className="mt-1 text-[2.1rem] leading-none font-semibold tracking-tight sm:text-[2.6rem]">
            Library
          </h1>
          <p className="mt-2 text-sm text-muted">{countLabel}</p>
        </div>

        <div className="flex w-full items-center gap-2 lg:w-auto">
          <label className="relative block min-w-0 flex-1 lg:w-72 lg:flex-none">
            <SearchIcon className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted" />
            <input
              ref={searchRef}
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search titles"
              aria-label="Search titles"
              className="h-10 w-full rounded-full border border-line bg-panel pr-10 pl-9 text-sm placeholder:text-muted/70 focus:border-line-strong focus:outline-none"
            />
            <kbd className="pointer-events-none absolute top-1/2 right-3 hidden -translate-y-1/2 rounded border border-line px-1.5 py-0.5 font-mono text-[10px] text-muted sm:block">
              /
            </kbd>
          </label>
          <Link
            href="/add"
            className="flex h-10 shrink-0 items-center gap-1.5 rounded-full bg-accent px-4 text-sm font-semibold text-accent-ink transition-opacity hover:opacity-90"
          >
            <PlusIcon className="h-4 w-4" />
            <span className="hidden sm:inline">Add</span>
          </Link>
        </div>
      </header>

      <div className="mt-7 flex flex-wrap items-center justify-between gap-3 border-b border-line pb-3">
        <div className="flex gap-1 overflow-x-auto">
          {KINDS.map((entry) => (
            <button
              key={entry.value}
              type="button"
              onClick={() => setKind(entry.value)}
              className={`rounded-full px-3.5 py-1.5 text-sm whitespace-nowrap transition-colors ${
                kind === entry.value
                  ? "bg-ink text-canvas"
                  : "text-muted hover:bg-panel-2 hover:text-ink"
              }`}
            >
              {entry.label}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2">
          <label className="relative">
            <select
              value={sort}
              onChange={(event) => setSort(event.target.value as SortKey)}
              aria-label="Sort library"
              className="h-9 appearance-none rounded-full border border-line bg-panel pr-8 pl-3 text-xs text-muted hover:text-ink focus:outline-none"
            >
              {SORT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <ChevronDownIcon className="pointer-events-none absolute top-1/2 right-2.5 h-3 w-3 -translate-y-1/2 text-muted" />
          </label>

          {selectionMode ? (
            <>
              <button
                type="button"
                onClick={() => setSelected(new Set(items.map((i) => i.id)))}
                className="text-xs text-muted transition-colors hover:text-ink"
              >
                All
              </button>
              <button
                type="button"
                onClick={clearSelection}
                className="text-xs text-muted transition-colors hover:text-ink"
              >
                Cancel
              </button>
            </>
          ) : (
            <button
              type="button"
              onClick={enterSelection}
              className="flex items-center gap-1.5 rounded-full border border-line px-3 py-1.5 text-xs text-muted transition-colors hover:text-ink"
            >
              <SelectIcon className="h-3.5 w-3.5" />
              Select
            </button>
          )}
        </div>
      </div>

      {error ? (
        <div className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-danger/40 bg-panel px-4 py-3 text-sm text-danger">
          <span>{error}. The backend may be down on :8787.</span>
          <button
            type="button"
            onClick={() => setReload((value) => value + 1)}
            className="rounded-lg border border-danger/40 px-3 py-1 text-xs"
          >
            Retry
          </button>
        </div>
      ) : null}

      {loading ? (
        <div className="mt-6 grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3 lg:grid-cols-4 2xl:grid-cols-5">
          {Array.from({ length: 8 }).map((_, index) => (
            <Skeleton key={index} />
          ))}
        </div>
      ) : items.length === 0 ? (
        <div className="mt-10 flex flex-col items-center justify-center rounded-2xl border border-dashed border-line px-6 py-24 text-center">
          <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-panel-2 text-muted">
            <LibraryGlyph />
          </span>
          <p className="mt-5 text-xl font-semibold tracking-tight">
            {debounced ? "No matches" : "A shelf waiting for its first record"}
          </p>
          <p className="mt-2 max-w-sm text-sm leading-relaxed text-muted">
            {debounced
              ? "Try a shorter title, or clear the search."
              : "Paste a link and choose what to keep — audio with its cover, the full video, a book, or a picture."}
          </p>
          {!debounced ? (
            <Link
              href="/add"
              className="mt-6 rounded-full bg-accent px-5 py-2.5 text-sm font-semibold text-accent-ink transition-opacity hover:opacity-90"
            >
              Add your first item
            </Link>
          ) : null}
        </div>
      ) : (
        <>
          <div className="mt-6 grid grid-cols-2 gap-x-4 gap-y-7 sm:grid-cols-3 lg:grid-cols-4 2xl:grid-cols-5">
            {items.map((item, index) => (
              <Card
                key={item.id}
                item={item}
                index={index}
                selected={selected.has(item.id)}
                selectionMode={selectionMode}
                onToggle={toggle}
                onPlay={(entry) => player.playItem(entry)}
                onQueue={queueItem}
                onDelete={(id) => setConfirmIds([id])}
              />
            ))}
          </div>

          {cursor ? (
            <div className="mt-10 flex justify-center">
              <button
                type="button"
                onClick={loadMore}
                disabled={loadingMore}
                className="rounded-full border border-line bg-panel px-5 py-2 text-sm text-muted transition-colors hover:text-ink disabled:opacity-50"
              >
                {loadingMore ? "Loading…" : "Load more"}
              </button>
            </div>
          ) : null}
        </>
      )}

      {selectionMode ? (
        <div className="fixed inset-x-0 bottom-[92px] z-40 px-4">
          <div className="animate-pop-in mx-auto flex w-full max-w-3xl flex-wrap items-center gap-2 rounded-2xl border border-line bg-panel/95 px-3 py-2.5 shadow-2xl backdrop-blur">
            <span className="mr-1 text-sm font-semibold">
              {selected.size} selected
            </span>
            <button
              type="button"
              disabled={selectedItems.length === 0}
              onClick={() => player.playNow(selectedItems, 0)}
              className="flex items-center gap-1.5 rounded-full bg-accent px-3 py-1.5 text-sm font-medium text-accent-ink disabled:opacity-50"
            >
              <PlayIcon className="h-3.5 w-3.5" />
              Play
            </button>
            <button
              type="button"
              disabled={selectedItems.length === 0}
              onClick={() => {
                player.addToQueue(selectedItems);
                toast(`Queued ${selectedItems.length} items`, "success");
                clearSelection();
              }}
              className="flex items-center gap-1.5 rounded-full border border-line px-3 py-1.5 text-sm text-muted hover:text-ink disabled:opacity-50"
            >
              <QueueIcon className="h-3.5 w-3.5" />
              Queue
            </button>
            {playlists.length > 0 ? (
              <span className="flex items-center gap-1">
                <select
                  value={targetPlaylist}
                  onChange={(event) => setTargetPlaylist(event.target.value)}
                  aria-label="Target playlist"
                  className="h-8 max-w-[150px] rounded-full border border-line px-2.5 text-xs text-muted"
                >
                  {playlists.map((playlist) => (
                    <option key={playlist.id} value={playlist.id}>
                      {playlist.name}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  disabled={selectedItems.length === 0}
                  onClick={addSelectedToPlaylist}
                  className="flex items-center gap-1.5 rounded-full border border-line px-3 py-1.5 text-sm text-muted hover:text-ink disabled:opacity-50"
                >
                  <PlusIcon className="h-3.5 w-3.5" />
                  Add
                </button>
              </span>
            ) : null}
            <button
              type="button"
              disabled={selected.size === 0}
              onClick={() => setConfirmIds(Array.from(selected))}
              className="flex items-center gap-1.5 rounded-full border border-line px-3 py-1.5 text-sm text-muted hover:border-danger/50 hover:text-danger disabled:opacity-50"
            >
              <TrashIcon className="h-3.5 w-3.5" />
              Delete
            </button>
            <button
              type="button"
              onClick={clearSelection}
              className="ml-auto text-xs text-muted hover:text-ink"
            >
              Done
            </button>
          </div>
        </div>
      ) : null}

      <ConfirmDialog
        open={confirmIds !== null}
        busy={deleting}
        title={
          (confirmIds?.length ?? 0) === 1
            ? "Delete this item?"
            : `Delete ${confirmIds?.length ?? 0} items?`
        }
        description="The entry and the file on disk will be removed. Playlists keep their other entries."
        confirmLabel="Delete permanently"
        onCancel={() => setConfirmIds(null)}
        onConfirm={deleteItems}
      />
    </div>
  );
}

function LibraryGlyph() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"
      strokeLinecap="round" strokeLinejoin="round" className="h-6 w-6" aria-hidden>
      <path d="M4 5.5v13M9 5.5v13" />
      <path d="M13.5 5.8l5 12.4" />
    </svg>
  );
}

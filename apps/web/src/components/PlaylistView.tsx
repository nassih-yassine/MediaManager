/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { CloseIcon, PlayIcon, TrashIcon } from "@/components/icons";
import { api, formatDate, formatDuration } from "@/lib/api";
import { usePlayer } from "@/lib/player";
import type { PlaylistDetail } from "@/lib/types";

function clock(seconds: number): string {
  const total = Math.floor(seconds);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  return h > 0 ? `${h} hr ${m} min` : `${m} min`;
}

export function PlaylistView() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const player = usePlayer();
  const [playlist, setPlaylist] = useState<PlaylistDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [nameDraft, setNameDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const dragFrom = useRef<number | null>(null);
  const [dragOver, setDragOver] = useState<number | null>(null);

  async function load() {
    if (!id) return;
    try {
      const data = await api<PlaylistDetail>(`/api/playlists/${id}`);
      setPlaylist(data);
      setNameDraft(data.name);
    } catch (err) {
      setError((err as Error).message);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function rename(event: React.FormEvent) {
    event.preventDefault();
    if (!playlist) return;
    const name = nameDraft.trim();
    if (!name || name === playlist.name) {
      setEditing(false);
      return;
    }
    setBusy(true);
    try {
      await api(`/api/playlists/${playlist.id}`, {
        method: "PATCH",
        body: JSON.stringify({ name }),
      });
      setPlaylist({ ...playlist, name });
      setEditing(false);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function removePlaylist() {
    if (!playlist) return;
    if (!window.confirm(`Delete the playlist "${playlist.name}"? Items stay in the library.`)) {
      return;
    }
    setBusy(true);
    try {
      await api(`/api/playlists/${playlist.id}`, { method: "DELETE" });
      router.push("/playlists");
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  }

  async function removeItem(itemId: string) {
    if (!playlist) return;
    setPlaylist({
      ...playlist,
      items: playlist.items.filter((item) => item.id !== itemId),
    });
    try {
      await api(`/api/playlists/${playlist.id}/items/${itemId}`, {
        method: "DELETE",
      });
    } catch (err) {
      setError((err as Error).message);
      void load();
    }
  }

  async function reorder(from: number, to: number) {
    if (!playlist || from === to) return;
    const items = [...playlist.items];
    const [moved] = items.splice(from, 1);
    items.splice(to, 0, moved);
    setPlaylist({ ...playlist, items });
    try {
      await api(`/api/playlists/${playlist.id}/order`, {
        method: "PUT",
        body: JSON.stringify({ itemIds: items.map((item) => item.id) }),
      });
    } catch (err) {
      setError((err as Error).message);
      void load();
    }
  }

  if (error && !playlist) {
    return (
      <div className="max-w-xl">
        <p className="rounded-xl border border-danger/40 bg-panel px-4 py-3 text-sm text-danger">
          {error}
        </p>
        <Link href="/playlists" className="mt-4 inline-block text-sm text-muted hover:text-ink">
          ← All playlists
        </Link>
      </div>
    );
  }

  if (!playlist) {
    return (
      <div className="max-w-3xl animate-pulse">
        <div className="h-4 w-24 rounded bg-panel-2" />
        <div className="mt-4 h-8 w-1/2 rounded bg-panel-2" />
        <div className="mt-6 h-40 rounded-2xl bg-panel-2" />
      </div>
    );
  }

  const totalSeconds = playlist.items.reduce(
    (sum, item) => sum + (item.durationMs ?? 0) / 1000,
    0,
  );

  return (
    <div className="max-w-4xl">
      <Link href="/playlists" className="text-sm text-muted transition-colors hover:text-ink">
        ← Playlists
      </Link>

      <div className="mt-3 flex flex-col gap-5 sm:flex-row sm:items-end">
        <div className="h-36 w-36 shrink-0 overflow-hidden rounded-xl border border-line bg-panel-2">
          {playlist.items[0]?.thumbUrl ? (
            <img
              src={playlist.items[0].thumbUrl}
              alt=""
              className="h-full w-full object-cover"
            />
          ) : (
            <div className="flex h-full items-center justify-center text-5xl font-semibold text-line-strong">
              {playlist.name.slice(0, 1).toUpperCase()}
            </div>
          )}
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-[11px] font-medium tracking-wide text-muted uppercase">
            Playlist
          </p>
          {editing ? (
            <form onSubmit={rename} className="mt-1 flex gap-2">
              <input
                value={nameDraft}
                onChange={(event) => setNameDraft(event.target.value)}
                autoFocus
                maxLength={80}
                className="h-10 min-w-0 flex-1 rounded-lg border border-line bg-canvas px-3 text-lg font-semibold focus:outline-none"
              />
              <button
                type="submit"
                disabled={busy}
                className="rounded-lg bg-accent px-3 text-sm font-medium text-accent-ink disabled:opacity-50"
              >
                Save
              </button>
            </form>
          ) : (
            <h1 className="mt-1 truncate text-3xl font-semibold tracking-tight">
              {playlist.name}
            </h1>
          )}
          <p className="mt-2 text-sm text-muted">
            {playlist.items.length} {playlist.items.length === 1 ? "item" : "items"}
            {totalSeconds > 0 ? ` · ${clock(totalSeconds)}` : ""} · created{" "}
            {formatDate(playlist.createdAt)}
          </p>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <button
              type="button"
              disabled={playlist.items.length === 0}
              onClick={() => player.playNow(playlist.items, 0)}
              className="flex items-center gap-2 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-accent-ink transition-opacity hover:opacity-90 disabled:opacity-50"
            >
              <PlayIcon className="h-3.5 w-3.5" />
              Play all
            </button>
            <button
              type="button"
              onClick={() => setEditing(true)}
              className="rounded-lg border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
            >
              Rename
            </button>
            <button
              type="button"
              onClick={removePlaylist}
              disabled={busy}
              className="rounded-lg border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:border-danger/50 hover:text-danger disabled:opacity-50"
            >
              Delete
            </button>
          </div>
        </div>
      </div>

      {error ? (
        <p className="mt-4 rounded-lg border border-danger/40 bg-danger/5 px-3 py-2 text-sm text-danger">
          {error}
        </p>
      ) : null}

      {playlist.items.length === 0 ? (
        <div className="mt-8 rounded-2xl border border-dashed border-line px-6 py-16 text-center">
          <p className="text-lg font-medium">This playlist is empty</p>
          <p className="mx-auto mt-2 max-w-sm text-sm text-muted">
            Select items in the library, then use “Add” to drop them in here.
          </p>
          <Link
            href="/"
            className="mt-5 inline-block rounded-lg border border-line px-4 py-2 text-sm text-ink"
          >
            Open library
          </Link>
        </div>
      ) : (
        <ul className="mt-8 space-y-1">
          {playlist.items.map((item, at) => (
            <li
              key={item.id}
              draggable
              onDragStart={(event) => {
                dragFrom.current = at;
                event.dataTransfer.effectAllowed = "move";
              }}
              onDragEnd={() => {
                dragFrom.current = null;
                setDragOver(null);
              }}
              onDragOver={(event) => {
                event.preventDefault();
                setDragOver(at);
              }}
              onDrop={(event) => {
                event.preventDefault();
                const from = dragFrom.current;
                dragFrom.current = null;
                setDragOver(null);
                if (from != null) void reorder(from, at);
              }}
              className={`group flex items-center gap-3 rounded-lg border px-2.5 py-2 ${
                dragOver === at
                  ? "border-accent/60 bg-panel-2"
                  : "border-transparent hover:bg-panel-2"
              }`}
            >
              <span className="w-6 shrink-0 text-center text-xs text-muted tabular-nums">
                {at + 1}
              </span>
              <div className="h-10 w-10 shrink-0 overflow-hidden rounded bg-canvas">
                {item.thumbUrl ? (
                  <img src={item.thumbUrl} alt="" className="h-full w-full object-cover" />
                ) : (
                  <span className="flex h-full items-center justify-center text-[10px] text-muted uppercase">
                    {item.kind.slice(0, 1)}
                  </span>
                )}
              </div>
              <button
                type="button"
                onClick={() => player.playNow(playlist.items, at)}
                className="min-w-0 flex-1 text-left"
              >
                <span className="block truncate text-sm font-medium">{item.title}</span>
                <span className="mt-0.5 block text-xs text-muted">
                  {item.kind}
                  {item.durationMs
                    ? ` · ${formatDuration(item.durationMs)}`
                    : ""}
                </span>
              </button>
              <button
                type="button"
                onClick={() => player.playNow(playlist.items, at)}
                aria-label={`Play ${item.title}`}
                className="rounded-md p-2 text-muted opacity-0 transition-opacity group-hover:opacity-100 hover:text-ink"
              >
                <PlayIcon className="h-3.5 w-3.5" />
              </button>
              <button
                type="button"
                onClick={() => removeItem(item.id)}
                aria-label={`Remove ${item.title} from playlist`}
                className="rounded-md p-2 text-muted opacity-0 transition-opacity group-hover:opacity-100 hover:text-danger"
              >
                <CloseIcon className="h-3.5 w-3.5" />
              </button>
              <span className="cursor-grab text-muted opacity-0 group-hover:opacity-100" aria-hidden>
                ⋮⋮
              </span>
            </li>
          ))}
        </ul>
      )}

      {playlist.items.length > 0 ? (
        <p className="mt-4 flex items-center gap-1.5 text-xs text-muted">
          <TrashIcon className="h-3 w-3" />
          Drag rows to reorder. Removing here never deletes the file.
        </p>
      ) : null}
    </div>
  );
}

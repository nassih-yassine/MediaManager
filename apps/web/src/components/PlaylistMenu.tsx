"use client";

import { useEffect, useRef, useState } from "react";

import { CheckIcon, PlaylistAddIcon, PlusIcon } from "@/components/icons";
import { useToast } from "@/components/ui/Toast";
import { api } from "@/lib/api";
import type {
  CollectionKind,
  PlaylistMembership,
  PlaylistSummary,
} from "@/lib/types";

interface PlaylistMenuProps {
  itemIds: string[];
  kind?: CollectionKind;
  variant?: "icon" | "pill";
  placement?: "top" | "bottom";
}

/**
 * One picker used by cards, the bulk bar and item pages. A single item toggles
 * membership with checkmarks; several items are appended in one request.
 */
export function PlaylistMenu({
  itemIds,
  kind = "playlist",
  variant = "pill",
  placement = "bottom",
}: PlaylistMenuProps) {
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [playlists, setPlaylists] = useState<PlaylistMembership[]>([]);
  const [loading, setLoading] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [creating, setCreating] = useState(false);
  const wrap = useRef<HTMLDivElement | null>(null);

  const single = itemIds.length === 1;
  const itemKey = itemIds.join(",");
  const noun = kind === "gallery" ? "gallery" : "playlist";

  useEffect(() => {
    if (!open) return;

    function onPointerDown(event: PointerEvent) {
      if (!wrap.current?.contains(event.target as Node)) setOpen(false);
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoading(true);
    const ids = itemKey ? itemKey.split(",") : [];
    const one = ids.length === 1;
    const request = one
      ? api<{ playlists: PlaylistMembership[] }>(
          `/api/items/${ids[0]}/playlists?kind=${kind}`,
        ).then((data) => data.playlists)
      : api<{ playlists: PlaylistSummary[] }>(
          `/api/playlists?kind=${kind}`,
        ).then((data) =>
          data.playlists.map((playlist) => ({
            id: playlist.id,
            name: playlist.name,
            kind: playlist.kind,
            itemCount: playlist.itemCount,
            contains: false,
          })),
        );

    request
      .then((items) => {
        if (!cancelled) setPlaylists(items);
      })
      .catch((err: Error) => {
        if (!cancelled) toast(err.message, "error");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [open, itemKey, kind, toast]);

  async function toggle(playlist: PlaylistMembership) {
    setBusyId(playlist.id);
    try {
      if (single && playlist.contains) {
        await api(`/api/playlists/${playlist.id}/items/${itemIds[0]}`, {
          method: "DELETE",
        });
        setPlaylists((current) =>
          current.map((entry) =>
            entry.id === playlist.id
              ? {
                  ...entry,
                  contains: false,
                  itemCount: Math.max(0, entry.itemCount - 1),
                }
              : entry,
          ),
        );
        toast(`Removed from “${playlist.name}”`, "info");
      } else {
        await api(`/api/playlists/${playlist.id}/items`, {
          method: "POST",
          body: JSON.stringify({ itemIds }),
        });
        if (single) {
          setPlaylists((current) =>
            current.map((entry) =>
              entry.id === playlist.id
                ? { ...entry, contains: true, itemCount: entry.itemCount + 1 }
                : entry,
            ),
          );
          toast(`Added to “${playlist.name}”`, "success");
        } else {
          toast(
            `Added ${itemIds.length} ${itemIds.length === 1 ? "item" : "items"} to “${playlist.name}”`,
            "success",
          );
          setOpen(false);
        }
      }
      window.dispatchEvent(new Event("mm:playlists-changed"));
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setBusyId(null);
    }
  }
  async function create() {
    const trimmed = name.trim();
    if (!trimmed) return;
    setCreating(true);
    try {
      const playlist = await api<PlaylistSummary>("/api/playlists", {
        method: "POST",
        body: JSON.stringify({ name: trimmed, kind }),
      });
      await api(`/api/playlists/${playlist.id}/items`, {
        method: "POST",
        body: JSON.stringify({ itemIds }),
      });
      toast(
        single
          ? `Created “${playlist.name}” and added the item`
          : `Created “${playlist.name}” and added ${itemIds.length} items`,
        "success",
      );
      setName("");
      setOpen(false);
      window.dispatchEvent(new Event("mm:playlists-changed"));
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setCreating(false);
    }
  }

  const trigger =
    variant === "icon"
      ? "rounded-lg border border-white/25 bg-black/45 p-2 text-white backdrop-blur transition-colors hover:border-accent/60 hover:text-accent"
      : "flex items-center gap-2 rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink";

  return (
    <div
      ref={wrap}
      className="relative"
      onClick={(event) => {
        event.preventDefault();
        event.stopPropagation();
      }}
    >
      <button
        type="button"
        aria-label={`Add to ${noun}`}
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className={trigger}
      >
        <PlaylistAddIcon className="h-3.5 w-3.5" />
        {variant === "pill" ? (
          <span>{kind === "gallery" ? "Gallery" : "Playlist"}</span>
        ) : null}
      </button>

      {open ? (
        <div
          className={`absolute right-0 z-50 w-72 rounded-xl border border-line bg-panel p-2 shadow-2xl ${
            placement === "top" ? "bottom-full mb-2" : "top-full mt-2"
          }`}
        >
          <p className="px-2 pt-1 pb-2 text-[10px] font-semibold tracking-[0.14em] text-muted uppercase">
            {single
              ? `Add to ${noun}`
              : `Add ${itemIds.length} items to ${noun}`}
          </p>

          {loading ? (
            <p className="px-2 py-4 text-sm text-muted">Loading playlists…</p>
          ) : playlists.length === 0 ? (
            <p className="px-2 py-3 text-sm text-muted">
              No playlists yet. Name one below.
            </p>
          ) : (
            <ul className="max-h-56 space-y-0.5 overflow-y-auto">
              {playlists.map((playlist) => (
                <li key={playlist.id}>
                  <button
                    type="button"
                    disabled={busyId === playlist.id}
                    onClick={() => toggle(playlist)}
                    className="flex w-full items-center gap-2.5 rounded-lg px-2 py-2 text-left text-sm text-muted transition-colors hover:bg-panel-2 hover:text-ink disabled:opacity-50"
                  >
                    <span
                      className={`flex h-4 w-4 shrink-0 items-center justify-center rounded border ${
                        playlist.contains
                          ? "border-accent bg-accent text-accent-ink"
                          : "border-line-strong"
                      }`}
                      aria-hidden
                    >
                      {playlist.contains ? <CheckIcon className="h-3 w-3" /> : null}
                    </span>
                    <span className="min-w-0 flex-1 truncate">{playlist.name}</span>
                    <span className="font-mono text-[10px] tabular-nums">
                      {playlist.itemCount}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}

          <div className="mt-2 flex items-center gap-1.5 border-t border-line pt-2">
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") void create();
              }}
              placeholder="New playlist"
              maxLength={80}
              className="h-8 min-w-0 flex-1 rounded-md border border-line bg-canvas px-2 text-xs placeholder:text-muted/70 focus:outline-none"
            />
            <button
              type="button"
              onClick={() => void create()}
              disabled={creating || !name.trim()}
              aria-label="Create playlist and add"
              className="flex h-8 w-8 items-center justify-center rounded-md bg-accent text-accent-ink transition-opacity hover:opacity-90 disabled:opacity-40"
            >
              <PlusIcon className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

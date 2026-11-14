"use client";

import { useEffect, useState } from "react";

import { PlayIcon, PlusIcon, QueueIcon } from "@/components/icons";
import { api } from "@/lib/api";
import { usePlayer } from "@/lib/player";
import type { Item, PlaylistSummary } from "@/lib/types";

export function ItemActions({ item }: { item: Item }) {
  const player = usePlayer();
  const [playlists, setPlaylists] = useState<PlaylistSummary[]>([]);
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api<{ playlists: PlaylistSummary[] }>("/api/playlists")
      .then((data) => {
        if (!cancelled) setPlaylists(data.playlists);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!status) return;
    const timer = setTimeout(() => setStatus(null), 3000);
    return () => clearTimeout(timer);
  }, [status]);

  async function addToPlaylist(playlist: PlaylistSummary) {
    try {
      await api(`/api/playlists/${playlist.id}/items`, {
        method: "POST",
        body: JSON.stringify({ itemIds: [item.id] }),
      });
      setStatus(`Added to ${playlist.name}`);
    } catch (err) {
      setStatus((err as Error).message);
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <button
        type="button"
        onClick={() => player.playItem(item)}
        className="flex items-center gap-2 rounded-lg bg-accent px-3.5 py-2 text-sm font-medium text-accent-ink transition-opacity hover:opacity-90"
      >
        <PlayIcon className="h-3.5 w-3.5" />
        Play
      </button>
      <button
        type="button"
        onClick={() => {
          player.playNext(item);
          setStatus("Playing next");
        }}
        className="rounded-lg border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
      >
        Play next
      </button>
      <button
        type="button"
        onClick={() => {
          player.addToQueue([item]);
          setStatus("Added to queue");
        }}
        className="flex items-center gap-2 rounded-lg border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
      >
        <QueueIcon className="h-3.5 w-3.5" />
        Add to queue
      </button>

      {playlists.length > 0 ? (
        <details className="relative">
          <summary className="flex cursor-pointer list-none items-center gap-2 rounded-lg border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink">
            <PlusIcon className="h-3.5 w-3.5" />
            Playlist
          </summary>
          <div className="absolute top-full left-0 z-30 mt-1 w-56 rounded-lg border border-line bg-panel p-1 shadow-xl">
            {playlists.map((playlist) => (
              <button
                key={playlist.id}
                type="button"
                onClick={() => addToPlaylist(playlist)}
                className="flex w-full items-center justify-between rounded-md px-2.5 py-2 text-left text-sm text-muted hover:bg-panel-2 hover:text-ink"
              >
                <span className="truncate">{playlist.name}</span>
                <span className="ml-2 text-[11px] tabular-nums">
                  {playlist.itemCount}
                </span>
              </button>
            ))}
          </div>
        </details>
      ) : null}

      {status ? (
        <span className="text-xs text-muted" role="status">
          {status}
        </span>
      ) : null}
    </div>
  );
}

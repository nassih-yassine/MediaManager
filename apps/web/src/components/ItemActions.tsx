"use client";

import { useEffect, useRef, useState } from "react";

import { useToast } from "@/components/ui/Toast";
import { PlayIcon, PlusIcon, QueueIcon } from "@/components/icons";
import { api } from "@/lib/api";
import { usePlayer } from "@/lib/player";
import type { Item, PlaylistSummary } from "@/lib/types";

export function ItemActions({ item }: { item: Item }) {
  const player = usePlayer();
  const toast = useToast();
  const [playlists, setPlaylists] = useState<PlaylistSummary[]>([]);
  const menu = useRef<HTMLDetailsElement | null>(null);

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

  async function addToPlaylist(playlist: PlaylistSummary) {
    try {
      await api(`/api/playlists/${playlist.id}/items`, {
        method: "POST",
        body: JSON.stringify({ itemIds: [item.id] }),
      });
      toast(`Added to “${playlist.name}”`, "success");
      menu.current?.removeAttribute("open");
    } catch (err) {
      toast((err as Error).message, "error");
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <button
        type="button"
        onClick={() => {
          if (player.current?.id === item.id) player.toggle();
          else player.playItem(item);
        }}
        className="flex items-center gap-2 rounded-full bg-accent px-4 py-2 text-sm font-semibold text-accent-ink transition-opacity hover:opacity-90"
      >
        <PlayIcon className="h-3.5 w-3.5" />
        {player.current?.id === item.id && player.playing ? "Pause" : "Play"}
      </button>
      <button
        type="button"
        onClick={() => {
          player.playNext(item);
          toast("Playing next", "info");
        }}
        className="rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
      >
        Play next
      </button>
      <button
        type="button"
        onClick={() => {
          player.addToQueue([item]);
          toast("Added to queue", "success");
        }}
        className="flex items-center gap-2 rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
      >
        <QueueIcon className="h-3.5 w-3.5" />
        Add to queue
      </button>

      {playlists.length > 0 ? (
        <details ref={menu} className="relative">
          <summary className="flex cursor-pointer list-none items-center gap-2 rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink">
            <PlusIcon className="h-3.5 w-3.5" />
            Playlist
          </summary>
          <div className="animate-pop-in absolute top-full left-0 z-30 mt-1.5 w-60 rounded-xl border border-line bg-panel p-1 shadow-2xl">
            <p className="px-2.5 py-1.5 text-[10px] font-semibold tracking-[0.14em] text-muted uppercase">
              Add to playlist
            </p>
            {playlists.map((playlist) => (
              <button
                key={playlist.id}
                type="button"
                onClick={() => addToPlaylist(playlist)}
                className="flex w-full items-center justify-between rounded-lg px-2.5 py-2 text-left text-sm text-muted hover:bg-panel-2 hover:text-ink"
              >
                <span className="truncate">{playlist.name}</span>
                <span className="ml-2 font-mono text-[11px] tabular-nums">
                  {playlist.itemCount}
                </span>
              </button>
            ))}
          </div>
        </details>
      ) : null}
    </div>
  );
}

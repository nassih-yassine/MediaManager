/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { PlusIcon } from "@/components/icons";
import { api } from "@/lib/api";
import type { PlaylistSummary } from "@/lib/types";

export function Playlists() {
  const [playlists, setPlaylists] = useState<PlaylistSummary[]>([]);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  async function load() {
    try {
      const data = await api<{ playlists: PlaylistSummary[] }>("/api/playlists");
      setPlaylists(data.playlists);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function create(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return;
    setBusy(true);
    setError(null);
    try {
      await api<PlaylistSummary>("/api/playlists", {
        method: "POST",
        body: JSON.stringify({ name: trimmed }),
      });
      setName("");
      await load();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">Playlists</h1>
      <p className="mt-1 text-sm text-muted">
        Named, ordered sets of anything in your library. Play them back to back.
      </p>

      <form onSubmit={create} className="mt-6 flex flex-wrap gap-2">
        <input
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="New playlist name…"
          maxLength={80}
          className="h-10 min-w-0 flex-1 rounded-lg border border-line bg-panel px-3.5 text-sm placeholder:text-muted/70 focus:border-line-strong focus:outline-none sm:max-w-sm"
        />
        <button
          type="submit"
          disabled={busy || !name.trim()}
          className="flex h-10 items-center gap-1.5 rounded-lg bg-accent px-4 text-sm font-medium text-accent-ink transition-opacity hover:opacity-90 disabled:opacity-50"
        >
          <PlusIcon className="h-3.5 w-3.5" />
          Create
        </button>
      </form>

      {error ? (
        <p className="mt-4 rounded-lg border border-danger/40 bg-danger/5 px-3 py-2 text-sm text-danger">
          {error}
        </p>
      ) : null}

      {loading ? (
        <div className="mt-8 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="animate-pulse">
              <div className="aspect-square rounded-xl bg-panel-2" />
              <div className="mt-2 h-3 w-2/3 rounded bg-panel-2" />
            </div>
          ))}
        </div>
      ) : playlists.length === 0 ? (
        <div className="mt-8 rounded-2xl border border-dashed border-line px-6 py-16 text-center">
          <p className="text-lg font-medium">No playlists yet</p>
          <p className="mx-auto mt-2 max-w-sm text-sm text-muted">
            Create one above, then select items in the library and add them to it.
          </p>
        </div>
      ) : (
        <div className="mt-8 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {playlists.map((playlist) => (
            <Link key={playlist.id} href={`/playlists/${playlist.id}`} className="group">
              <div className="aspect-square overflow-hidden rounded-xl border border-line bg-panel-2">
                {playlist.thumbUrl ? (
                  <img
                    src={playlist.thumbUrl}
                    alt=""
                    className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
                  />
                ) : (
                  <div className="flex h-full items-center justify-center text-4xl font-semibold text-line-strong">
                    {playlist.name.slice(0, 1).toUpperCase()}
                  </div>
                )}
              </div>
              <h2 className="mt-2.5 truncate text-sm font-medium">{playlist.name}</h2>
              <p className="mt-0.5 text-xs text-muted">
                {playlist.itemCount} {playlist.itemCount === 1 ? "item" : "items"}
              </p>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

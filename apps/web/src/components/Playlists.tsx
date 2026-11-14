/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { DiscIcon, PlusIcon } from "@/components/icons";
import { useToast } from "@/components/ui/Toast";
import { api } from "@/lib/api";
import type { PlaylistSummary } from "@/lib/types";

function Mosaic({ playlist }: { playlist: PlaylistSummary }) {
  const thumbs = playlist.thumbUrls.slice(0, 4);
  if (thumbs.length === 0) {
    return (
      <div className="flex h-full w-full items-center justify-center bg-panel-2 text-line-strong">
        <DiscIcon className="h-10 w-10" />
      </div>
    );
  }
  if (thumbs.length === 1) {
    return (
      <img
        src={thumbs[0]}
        alt=""
        className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-[1.03]"
      />
    );
  }
  return (
    <div className="grid h-full w-full grid-cols-2 grid-rows-2 gap-px bg-line">
      {[0, 1, 2, 3].map((index) => (
        <div key={index} className="overflow-hidden bg-panel-2">
          {thumbs[index] ? (
            <img
              src={thumbs[index]}
              alt=""
              className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-[1.04]"
            />
          ) : null}
        </div>
      ))}
    </div>
  );
}

export function Playlists() {
  const toast = useToast();
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
      toast(`Playlist “${trimmed}” created`, "success");
      await load();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <p className="text-[10px] font-semibold tracking-[0.18em] text-muted uppercase">
        Collections
      </p>
      <h1 className="mt-1 text-[2.1rem] leading-none font-semibold tracking-tight sm:text-[2.6rem]">
        Playlists
      </h1>
      <p className="mt-2 max-w-xl text-sm leading-relaxed text-muted">
        Named, ordered sets that can mix music, video, books and pictures. Play
        one straight through.
      </p>

      <form onSubmit={create} className="mt-6 flex flex-wrap gap-2">
        <input
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Name a new playlist"
          maxLength={80}
          className="h-11 min-w-0 flex-1 rounded-full border border-line bg-panel px-4 text-sm placeholder:text-muted/70 focus:border-line-strong focus:outline-none sm:max-w-sm"
        />
        <button
          type="submit"
          disabled={busy || !name.trim()}
          className="flex h-11 items-center gap-1.5 rounded-full bg-accent px-4 text-sm font-semibold text-accent-ink transition-opacity hover:opacity-90 disabled:opacity-50"
        >
          <PlusIcon className="h-4 w-4" />
          Create
        </button>
      </form>

      {error ? (
        <p className="mt-4 rounded-xl border border-danger/40 bg-danger/5 px-3.5 py-2.5 text-sm text-danger">
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
        <div className="mt-8 rounded-2xl border border-dashed border-line px-6 py-20 text-center">
          <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-panel-2 text-muted">
            <DiscIcon className="h-6 w-6" />
          </span>
          <p className="mt-5 text-xl font-semibold tracking-tight">
            No playlists yet
          </p>
          <p className="mx-auto mt-2 max-w-sm text-sm leading-relaxed text-muted">
            Create one above, then select items in the library and add them.
          </p>
        </div>
      ) : (
        <div className="mt-8 grid grid-cols-2 gap-x-4 gap-y-7 sm:grid-cols-3 lg:grid-cols-4">
          {playlists.map((playlist) => (
            <Link
              key={playlist.id}
              href={`/playlists/${playlist.id}`}
              className="group animate-fade-up"
            >
              <div className="aspect-square overflow-hidden rounded-xl border border-line bg-panel-2 transition-colors group-hover:border-line-strong">
                <Mosaic playlist={playlist} />
              </div>
              <h2 className="mt-2.5 truncate text-sm font-medium">{playlist.name}</h2>
              <p className="mt-0.5 font-mono text-[11px] text-muted">
                {playlist.itemCount} {playlist.itemCount === 1 ? "item" : "items"}
              </p>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

/* eslint-disable @next/next/no-img-element */
"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { AddItemsDialog } from "@/components/AddItemsDialog";
import { CoverDialog } from "@/components/CoverDialog";
import { GalleryLightbox } from "@/components/GalleryLightbox";
import {
  DeleteCollectionDialog,
  type DeleteCollectionMode,
} from "@/components/DeleteCollectionDialog";
import { SelectMenu } from "@/components/ui/SelectMenu";
import { useToast } from "@/components/ui/Toast";
import {
  CheckIcon,
  CloseIcon,
  DiscIcon,
  ImageIcon,
  PauseIcon,
  PlayIcon,
  PlusIcon,
  ShuffleIcon,
  TrashIcon,
} from "@/components/icons";
import { api, formatDuration } from "@/lib/api";
import { kindLabel } from "@/lib/kind";
import { usePlayer } from "@/lib/player";
import { isPlayable } from "@/lib/player-utils";
import { useRealtime } from "@/lib/realtime";
import type { PlaylistDetail, PlaylistSummary } from "@/lib/types";

function totalLabel(seconds: number): string {
  const total = Math.floor(seconds);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  return h > 0 ? `${h} hr ${m} min` : `${m} min`;
}

function HeroCover({
  playlist,
}: {
  playlist: PlaylistDetail;
}) {
  if (playlist.coverUrl) {
    return <img src={playlist.coverUrl} alt="" className="h-full w-full object-cover" />;
  }
  const thumbs = playlist.items
    .map((item) => item.thumbUrl)
    .filter((url): url is string => Boolean(url))
    .slice(0, 4);

  if (thumbs.length === 0) {
    return (
      <div className="flex h-full w-full items-center justify-center bg-panel-2 text-line-strong">
        <DiscIcon className="h-12 w-12" />
      </div>
    );
  }
  if (thumbs.length === 1) {
    return <img src={thumbs[0]} alt="" className="h-full w-full object-cover" />;
  }
  return (
    <div className="grid h-full w-full grid-cols-2 grid-rows-2 gap-px bg-line">
      {[0, 1, 2, 3].map((index) => (
        <div key={index} className="overflow-hidden bg-panel-2">
          {thumbs[index] ? (
            <img src={thumbs[index]} alt="" className="h-full w-full object-cover" />
          ) : null}
        </div>
      ))}
    </div>
  );
}

export function PlaylistView() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const player = usePlayer();
  const toast = useToast();
  const [playlist, setPlaylist] = useState<PlaylistDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [nameDraft, setNameDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [coverOpen, setCoverOpen] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [dropActive, setDropActive] = useState(false);
  const [viewer, setViewer] = useState<number | null>(null);
  const [selectionMode, setSelectionMode] = useState(false);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [galleries, setGalleries] = useState<PlaylistSummary[]>([]);
  const [targetGallery, setTargetGallery] = useState("");
  const [bulkBusy, setBulkBusy] = useState(false);
  const [deleteModeOpen, setDeleteModeOpen] = useState(false);
  const [deletePicturesOpen, setDeletePicturesOpen] = useState(false);
  const pictureInput = useRef<HTMLInputElement | null>(null);
  const dragFrom = useRef<number | null>(null);
  const [dragOver, setDragOver] = useState<number | null>(null);

  async function load() {
    if (!id) return;
    try {
      const data = await api<PlaylistDetail>(`/api/playlists/${id}`);
      setPlaylist(data);
      setNameDraft(data.name);
      setError(null);
    } catch (err) {
      setError((err as Error).message);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useRealtime((event) => {
    if (
      event.type === "item.deleted" ||
      (event.type === "collection.changed" && event.id === id)
    ) {
      void load();
    }
  });

  useEffect(() => {
    if (!selectionMode || !playlist || playlist.kind !== "gallery") return;
    api<{ playlists: PlaylistSummary[] }>("/api/playlists?kind=gallery")
      .then((data) => {
        const options = data.playlists.filter(
          (entry) => entry.id !== playlist.id,
        );
        setGalleries(options);
        setTargetGallery((current) =>
          options.some((entry) => entry.id === current)
            ? current
            : (options[0]?.id ?? ""),
        );
      })
      .catch(() => {});
  }, [selectionMode, playlist]);

  useEffect(() => {
    if (!selectionMode) return;
    function onKey(event: KeyboardEvent) {
      if (event.key !== "Escape") return;
      if (document.querySelector('[role="dialog"]')) return;
      setSelectionMode(false);
      setSelected(new Set());
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [selectionMode]);

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
      toast("Playlist renamed", "success");
      window.dispatchEvent(new Event("mm:playlists-changed"));
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  }

  async function removePlaylist(mode: DeleteCollectionMode = "move") {
    if (!playlist) return;
    setBusy(true);
    try {
      const url =
        playlist.kind === "gallery"
          ? `/api/playlists/${playlist.id}?mode=${mode}`
          : `/api/playlists/${playlist.id}`;
      await api(url, { method: "DELETE" });
      toast(
        playlist.kind === "gallery"
          ? mode === "delete_items"
            ? `Deleted “${playlist.name}” and its pictures`
            : `Deleted “${playlist.name}”; pictures moved to All pictures`
          : `Deleted “${playlist.name}”`,
        "success",
      );
      router.push(playlist.kind === "gallery" ? "/galleries" : "/playlists");
    } catch (err) {
      toast((err as Error).message, "error");
      setBusy(false);
      setConfirmDelete(false);
      setDeleteModeOpen(false);
    }
  }

  async function removeItem(itemId: string) {
    if (!playlist) return;
    const item = playlist.items.find((entry) => entry.id === itemId);
    setPlaylist({
      ...playlist,
      items: playlist.items.filter((entry) => entry.id !== itemId),
    });
    setViewer(null);
    try {
      await api(`/api/playlists/${playlist.id}/items/${itemId}`, {
        method: "DELETE",
      });
      toast(
        item ? `Removed “${item.title}” from the playlist` : "Removed",
        "info",
      );
      window.dispatchEvent(new Event("mm:playlists-changed"));
    } catch (err) {
      toast((err as Error).message, "error");
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
      toast((err as Error).message, "error");
      void load();
    }
  }

  function shufflePlay() {
    if (!playlist) return;
    const items = [...playlist.items];
    for (let i = items.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      [items[i], items[j]] = [items[j], items[i]];
    }
    player.playNow(items, 0);
  }

  async function uploadPictures(list: FileList | File[]) {
    if (!playlist) return;
    const files = Array.from(list).filter((file) =>
      file.type.startsWith("image/"),
    );
    if (files.length === 0) return;
    setUploading(true);
    let added = 0;
    const failures: string[] = [];
    for (const file of files) {
      try {
        const body = new FormData();
        body.append("file", file);
        const res = await fetch("/api/uploads?kind=picture", {
          method: "POST",
          body,
        });
        if (!res.ok) {
          const data = (await res.json().catch(() => null)) as {
            error?: string;
          } | null;
          throw new Error(data?.error ?? "Upload failed");
        }
        const created = (await res.json()) as { id: string };
        await api(`/api/playlists/${playlist.id}/items`, {
          method: "POST",
          body: JSON.stringify({ itemIds: [created.id] }),
        });
        added += 1;
      } catch (err) {
        failures.push(`${file.name}: ${(err as Error).message}`);
      }
    }
    window.dispatchEvent(new Event("mm:playlists-changed"));
    if (added > 0) {
      toast(
        `Added ${added} ${added === 1 ? "picture" : "pictures"} to “${playlist.name}”`,
        "success",
      );
    }
    if (failures.length > 0) toast(failures[0], "error");
    if (pictureInput.current) pictureInput.current.value = "";
    setUploading(false);
    await load();
  }

  function toggleSelected(itemId: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(itemId)) next.delete(itemId);
      else next.add(itemId);
      return next;
    });
  }

  function exitSelection() {
    setSelectionMode(false);
    setSelected(new Set());
  }

  async function moveSelected() {
    if (!playlist || !targetGallery || selected.size === 0) return;
    setBulkBusy(true);
    const ids = Array.from(selected);
    try {
      await api(`/api/playlists/${targetGallery}/items`, {
        method: "POST",
        body: JSON.stringify({ itemIds: ids }),
      });
      for (const itemId of ids) {
        await api(`/api/playlists/${playlist.id}/items/${itemId}`, {
          method: "DELETE",
        });
      }
      toast(
        `Moved ${ids.length} ${ids.length === 1 ? "picture" : "pictures"}`,
        "success",
      );
      exitSelection();
      await load();
      window.dispatchEvent(new Event("mm:playlists-changed"));
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setBulkBusy(false);
    }
  }

  async function removeSelected() {
    if (!playlist || selected.size === 0) return;
    setBulkBusy(true);
    const ids = Array.from(selected);
    try {
      for (const itemId of ids) {
        await api(`/api/playlists/${playlist.id}/items/${itemId}`, {
          method: "DELETE",
        });
      }
      toast(
        `Removed ${ids.length} ${ids.length === 1 ? "picture" : "pictures"} from this gallery`,
        "info",
      );
      exitSelection();
      await load();
      window.dispatchEvent(new Event("mm:playlists-changed"));
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setBulkBusy(false);
    }
  }

  async function deleteSelected() {
    if (selected.size === 0) return;
    setBulkBusy(true);
    const ids = Array.from(selected);
    const results = await Promise.allSettled(
      ids.map((itemId) =>
        api(`/api/items/${itemId}?delete_file=true`, { method: "DELETE" }),
      ),
    );
    const failed = results.filter((entry) => entry.status === "rejected").length;
    const removed = ids.length - failed;
    if (removed > 0) {
      toast(
        `Deleted ${removed} ${removed === 1 ? "picture" : "pictures"}`,
        "success",
      );
    }
    if (failed > 0) toast(`${failed} could not be deleted`, "error");
    setDeletePicturesOpen(false);
    setBulkBusy(false);
    exitSelection();
    await load();
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
        <div className="mt-4 h-9 w-1/2 rounded bg-panel-2" />
        <div className="mt-6 h-44 rounded-2xl bg-panel-2" />
      </div>
    );
  }

  const totalSeconds = playlist.items.reduce(
    (sum, item) => sum + (item.durationMs ?? 0) / 1000,
    0,
  );
  const playableCount = playlist.items.filter(isPlayable).length;
  const gallery = playlist.kind === "gallery";
  const base = gallery ? "/galleries" : "/playlists";

  return (
    <div className="max-w-4xl">
      <Link href={base} className="text-sm text-muted transition-colors hover:text-ink">
        ← {gallery ? "Galleries" : "Playlists"}
      </Link>

      <div className="mt-4 flex flex-col gap-6 sm:flex-row sm:items-end">
        <div className="h-40 w-40 shrink-0 overflow-hidden rounded-2xl border border-line bg-panel-2 shadow-xl">
          <HeroCover playlist={playlist} />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-[10px] font-semibold tracking-[0.18em] text-muted uppercase">
            {gallery ? "Gallery" : "Playlist"}
          </p>
          {editing ? (
            <form onSubmit={rename} className="mt-1 flex gap-2">
              <input
                value={nameDraft}
                onChange={(event) => setNameDraft(event.target.value)}
                autoFocus
                maxLength={80}
                aria-label="Playlist name"
                className="h-11 min-w-0 flex-1 rounded-xl border border-line bg-canvas px-3 text-xl font-semibold focus:outline-none"
              />
              <button
                type="submit"
                disabled={busy}
                className="rounded-xl bg-accent px-3.5 text-sm font-medium text-accent-ink disabled:opacity-50"
              >
                Save
              </button>
            </form>
          ) : (
            <h1 className="mt-1 truncate text-3xl leading-tight font-semibold tracking-tight sm:text-4xl">
              {playlist.name}
            </h1>
          )}
          <p className="mt-2 font-mono text-[11px] text-muted">
            {gallery
              ? `${playlist.items.length} ${
                  playlist.items.length === 1 ? "picture" : "pictures"
                }`
              : `${playlist.items.length} ${
                  playlist.items.length === 1 ? "item" : "items"
                }${totalSeconds > 0 ? ` · ${totalLabel(totalSeconds)}` : ""}`}
          </p>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            {gallery ? (
              <>
                <button
                  type="button"
                  onClick={() => setAddOpen(true)}
                  className="flex items-center gap-2 rounded-full bg-accent px-4 py-2 text-sm font-semibold text-accent-ink transition-opacity hover:opacity-90"
                >
                  <ImageIcon className="h-3.5 w-3.5" />
                  Add pictures
                </button>
                <button
                  type="button"
                  onClick={() => pictureInput.current?.click()}
                  disabled={uploading}
                  className="flex items-center gap-2 rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink disabled:opacity-50"
                >
                  <PlusIcon className="h-3.5 w-3.5" />
                  {uploading ? "Uploading…" : "Upload pictures"}
                </button>
                <input
                  ref={pictureInput}
                  type="file"
                  multiple
                  accept="image/*"
                  className="hidden"
                  onChange={(event) => {
                    if (event.target.files?.length) {
                      void uploadPictures(event.target.files);
                    }
                  }}
                />
              </>
            ) : (
              <>
                <button
                  type="button"
                  disabled={playableCount === 0}
                  onClick={() => player.playNow(playlist.items, 0)}
                  className="flex items-center gap-2 rounded-full bg-accent px-4 py-2 text-sm font-semibold text-accent-ink transition-opacity hover:opacity-90 disabled:opacity-50"
                >
                  <PlayIcon className="h-3.5 w-3.5" />
                  Play all
                </button>
                <button
                  type="button"
                  disabled={playableCount < 2}
                  onClick={shufflePlay}
                  className="flex items-center gap-2 rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink disabled:opacity-50"
                >
                  <ShuffleIcon className="h-3.5 w-3.5" />
                  Shuffle
                </button>
                <button
                  type="button"
                  onClick={() => setAddOpen(true)}
                  className="flex items-center gap-2 rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
                >
                  <PlusIcon className="h-3.5 w-3.5" />
                  Add items
                </button>
              </>
            )}
            {gallery && playlist.items.length > 0 ? (
              <button
                type="button"
                onClick={() => {
                  if (selectionMode) exitSelection();
                  else setSelectionMode(true);
                }}
                className="rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
              >
                {selectionMode ? "Cancel" : "Select"}
              </button>
            ) : null}
            <button
              type="button"
              onClick={() => setCoverOpen(true)}
              className="rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
            >
              Cover
            </button>
            <button
              type="button"
              onClick={() => setEditing(true)}
              className="rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
            >
              Rename
            </button>
            {!playlist.isDefault ? (
              <button
                type="button"
                onClick={() =>
                  gallery ? setDeleteModeOpen(true) : setConfirmDelete(true)
                }
                className="rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:border-danger/50 hover:text-danger"
              >
                Delete
              </button>
            ) : null}
          </div>
        </div>
      </div>

      {playlist.items.length === 0 ? (
        <div className="mt-8 rounded-2xl border border-dashed border-line px-6 py-16 text-center">
          <p className="text-xl font-semibold tracking-tight">
            {gallery ? "No pictures yet" : "Nothing in here yet"}
          </p>
          <p className="mx-auto mt-2 max-w-sm text-sm leading-relaxed text-muted">
            {gallery
              ? "Select pictures in the library and use “Gallery” to add them here."
              : "Select items in the library, then use “Playlist” to drop them in."}
          </p>
          <Link
            href="/"
            className="mt-5 inline-block rounded-full border border-line px-4 py-2 text-sm text-ink"
          >
            Open library
          </Link>
        </div>
      ) : gallery ? (
        <div
          onDragOver={(event) => {
            event.preventDefault();
            setDropActive(true);
          }}
          onDragLeave={() => setDropActive(false)}
          onDrop={(event) => {
            event.preventDefault();
            setDropActive(false);
            if (event.dataTransfer.files?.length) {
              void uploadPictures(event.dataTransfer.files);
            }
          }}
          className={`mt-8 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 ${
            dropActive
              ? "rounded-2xl outline-2 outline-offset-4 outline-dashed outline-accent"
              : ""
          }`}
        >
          {playlist.items.map((item, at) => {
            const isSelected = selected.has(item.id);
            return (
              <div
                key={item.id}
                className={`group relative aspect-square overflow-hidden rounded-xl border bg-panel-2 ${
                  isSelected
                    ? "border-accent ring-2 ring-accent/30"
                    : "border-line"
                }`}
              >
                <button
                  type="button"
                  onClick={() =>
                    selectionMode ? toggleSelected(item.id) : setViewer(at)
                  }
                  className="block h-full w-full"
                  aria-label={
                    selectionMode
                      ? `${isSelected ? "Deselect" : "Select"} ${item.title}`
                      : `Open ${item.title}`
                  }
                >
                  <img
                    src={item.thumbUrl ?? item.fileUrl}
                    alt={item.title}
                    loading="lazy"
                    className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-[1.03]"
                  />
                </button>
                {selectionMode ? (
                  <span
                    className={`pointer-events-none absolute top-2 left-2 z-10 flex h-6 w-6 items-center justify-center rounded-full border ${
                      isSelected
                        ? "border-accent bg-accent text-accent-ink"
                        : "border-white/60 bg-black/40 text-transparent"
                    }`}
                    aria-hidden
                  >
                    <CheckIcon className="h-3.5 w-3.5" />
                  </span>
                ) : null}
                {!selectionMode ? (
                  <button
                    type="button"
                    onClick={() => removeItem(item.id)}
                    aria-label={`Remove ${item.title} from gallery`}
                    className="absolute top-2 right-2 z-10 rounded-lg border border-white/25 bg-black/45 p-1.5 text-white opacity-0 transition-opacity group-hover:opacity-100 hover:border-danger/60 hover:text-danger"
                  >
                    <CloseIcon className="h-3.5 w-3.5" />
                  </button>
                ) : null}
                <div className="pointer-events-none absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/60 to-transparent p-2 opacity-0 transition-opacity group-hover:opacity-100">
                  <p className="truncate text-[11px] text-white">
                    {item.title}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <ul className="mt-8 space-y-1">
          {playlist.items.map((item, at) => {
            const isCurrent = player.current?.id === item.id;
            return (
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
                className={`group flex items-center gap-3 rounded-xl border px-2.5 py-2 transition-colors ${
                  dragOver === at
                    ? "border-accent/60 bg-panel-2"
                    : isCurrent
                      ? "border-accent/30 bg-panel-2/70"
                      : "border-transparent hover:bg-panel-2/70"
                }`}
              >
                <span className="w-7 shrink-0 text-center font-mono text-[11px] text-muted tabular-nums">
                  {isCurrent && player.playing ? (
                    <span className="inline-flex h-3.5 items-end gap-[2px]" aria-label="Playing">
                      {[0, 1, 2].map((bar) => (
                        <span
                          key={bar}
                          className="eq-bar w-[2px] rounded-full bg-accent"
                          style={{
                            height: "100%",
                            animationDelay: `${bar * 120}ms`,
                          }}
                        />
                      ))}
                    </span>
                  ) : (
                    at + 1
                  )}
                </span>
                <div className="h-10 w-10 shrink-0 overflow-hidden rounded-lg bg-canvas">
                  {item.thumbUrl ? (
                    <img src={item.thumbUrl} alt="" className="h-full w-full object-cover" />
                  ) : (
                    <span className="flex h-full items-center justify-center text-[10px] text-muted uppercase">
                      {item.kind.slice(0, 1)}
                    </span>
                  )}
                </div>
                {isPlayable(item) ? (
                  <button
                    type="button"
                    onClick={() => player.playNow(playlist.items, at)}
                    className="min-w-0 flex-1 text-left"
                  >
                    <span className="block truncate text-sm font-medium">{item.title}</span>
                    <span className="mt-0.5 block font-mono text-[11px] text-muted">
                      {kindLabel(item.kind)}
                      {item.durationMs ? ` · ${formatDuration(item.durationMs)}` : ""}
                    </span>
                  </button>
                ) : (
                  <Link
                    href={`/read/${item.id}`}
                    className="min-w-0 flex-1 text-left hover:underline"
                  >
                    <span className="block truncate text-sm font-medium">{item.title}</span>
                    <span className="mt-0.5 block font-mono text-[11px] text-muted">
                      {kindLabel(item.kind)} · open
                    </span>
                  </Link>
                )}
                {isPlayable(item) ? (
                  <button
                    type="button"
                    onClick={() => {
                      if (isCurrent) player.toggle();
                      else player.playNow(playlist.items, at);
                    }}
                    aria-label={
                      isCurrent && player.playing ? `Pause ${item.title}` : `Play ${item.title}`
                    }
                    className="rounded-lg p-2 text-muted transition-colors hover:text-ink"
                  >
                    {isCurrent && player.playing ? (
                      <PauseIcon className="h-3.5 w-3.5" />
                    ) : (
                      <PlayIcon className="h-3.5 w-3.5" />
                    )}
                  </button>
                ) : null}
                <button
                  type="button"
                  onClick={() => removeItem(item.id)}
                  aria-label={`Remove ${item.title} from playlist`}
                  className="rounded-lg p-2 text-muted transition-colors hover:text-danger"
                >
                  <CloseIcon className="h-3.5 w-3.5" />
                </button>
                <span
                  className="cursor-grab px-1 text-muted/60 opacity-0 transition-opacity group-hover:opacity-100"
                  aria-hidden
                >
                  ⋮⋮
                </span>
              </li>
            );
          })}
        </ul>
      )}

      {playlist.items.length > 0 ? (
        <p className="mt-4 flex items-center gap-1.5 text-[11px] text-muted">
          <TrashIcon className="h-3 w-3" />
          {gallery
            ? "Click a picture to open it. Removing here never deletes the file."
            : "Drag rows to reorder. Removing here never deletes the file."}
        </p>
      ) : null}

      <CoverDialog
        open={coverOpen}
        playlist={playlist}
        onClose={() => setCoverOpen(false)}
        onChanged={() => void load()}
      />

      {selectionMode ? (
        <div className="fixed inset-x-0 bottom-[92px] z-40 px-4">
          <div className="animate-pop-in mx-auto flex w-full max-w-3xl flex-wrap items-center gap-2 rounded-2xl border border-line bg-panel/95 px-3 py-2.5 shadow-2xl backdrop-blur">
            <button
              type="button"
              onClick={exitSelection}
              aria-label="Exit selection"
              className="flex items-center gap-1 rounded-full border border-line px-2.5 py-1.5 text-xs text-muted transition-colors hover:text-ink"
            >
              <CloseIcon className="h-3.5 w-3.5" />
              Cancel
            </button>
            <span className="mr-1 text-sm font-semibold">
              {selected.size} selected
            </span>

            {!playlist.isDefault ? (
              <>
                <SelectMenu
                  value={targetGallery}
                  options={galleries.map((entry) => ({
                    value: entry.id,
                    label: entry.name,
                  }))}
                  onChange={setTargetGallery}
                  ariaLabel="Move to gallery"
                  className="h-8 max-w-[170px] rounded-full border border-line px-2.5 text-xs text-muted"
                  panelWidth={220}
                />
                <button
                  type="button"
                  onClick={moveSelected}
                  disabled={!targetGallery || selected.size === 0 || bulkBusy}
                  className="rounded-full border border-line px-3 py-1.5 text-sm text-muted hover:text-ink disabled:opacity-40"
                >
                  Move
                </button>
                <button
                  type="button"
                  onClick={removeSelected}
                  disabled={selected.size === 0 || bulkBusy}
                  className="rounded-full border border-line px-3 py-1.5 text-sm text-muted hover:text-ink disabled:opacity-40"
                >
                  Remove
                </button>
              </>
            ) : null}

            <button
              type="button"
              onClick={() => setDeletePicturesOpen(true)}
              disabled={selected.size === 0 || bulkBusy}
              className="flex items-center gap-1.5 rounded-full border border-line px-3 py-1.5 text-sm text-muted hover:border-danger/50 hover:text-danger disabled:opacity-40"
            >
              <TrashIcon className="h-3.5 w-3.5" />
              Delete pictures
            </button>
          </div>
        </div>
      ) : null}

      {viewer !== null && playlist.items.length > 0 ? (
        <GalleryLightbox
          items={playlist.items}
          index={Math.min(viewer, playlist.items.length - 1)}
          onIndexChange={setViewer}
          onClose={() => setViewer(null)}
        />
      ) : null}

      <DeleteCollectionDialog
        open={deleteModeOpen}
        name={playlist.name}
        itemCount={playlist.items.length}
        busy={busy}
        onCancel={() => setDeleteModeOpen(false)}
        onConfirm={removePlaylist}
      />

      <ConfirmDialog
        open={deletePicturesOpen}
        busy={bulkBusy}
        title={`Delete ${selected.size} ${selected.size === 1 ? "picture" : "pictures"}?`}
        description="The files are removed from disk and from every gallery. This cannot be undone."
        confirmLabel="Delete pictures"
        onCancel={() => setDeletePicturesOpen(false)}
        onConfirm={deleteSelected}
      />

      <AddItemsDialog
        open={addOpen}
        collectionId={playlist.id}
        kind={gallery ? "gallery" : "playlist"}
        existingIds={playlist.items.map((item) => item.id)}
        onClose={() => setAddOpen(false)}
        onAdded={() => void load()}
      />

      <ConfirmDialog
        open={confirmDelete}
        busy={busy}
        title={`Delete “${playlist.name}”?`}
        description="The playlist is removed. Its items stay in the library."
        confirmLabel="Delete playlist"
        onCancel={() => setConfirmDelete(false)}
        onConfirm={() => void removePlaylist("move")}
      />
    </div>
  );
}

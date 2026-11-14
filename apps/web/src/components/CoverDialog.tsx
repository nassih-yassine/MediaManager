/* eslint-disable @next/next/no-img-element */
"use client";

import { useEffect, useId, useRef, useState } from "react";

import { TrashIcon } from "@/components/icons";
import { useToast } from "@/components/ui/Toast";
import { api } from "@/lib/api";
import type { PlaylistDetail } from "@/lib/types";

interface CoverDialogProps {
  open: boolean;
  playlist: PlaylistDetail;
  onClose: () => void;
  onChanged: () => void;
}

export function CoverDialog({ open, playlist, onClose, onChanged }: CoverDialogProps) {
  const toast = useToast();
  const titleId = useId();
  const fileInput = useRef<HTMLInputElement | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  const candidates = playlist.items.filter(
    (item) => item.thumbUrl || item.kind === "picture",
  );

  async function chooseItem(itemId: string) {
    setBusy(true);
    try {
      await api(`/api/playlists/${playlist.id}/cover`, {
        method: "PUT",
        body: JSON.stringify({ itemId }),
      });
      toast("Cover updated", "success");
      onChanged();
      onClose();
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  }

  async function upload(file: File) {
    setBusy(true);
    try {
      const body = new FormData();
      body.append("file", file);
      const res = await fetch(`/api/playlists/${playlist.id}/cover/upload`, {
        method: "POST",
        body,
      });
      if (!res.ok) {
        const data = (await res.json().catch(() => null)) as { error?: string } | null;
        throw new Error(data?.error ?? "Upload failed");
      }
      toast("Cover updated", "success");
      onChanged();
      onClose();
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    setBusy(true);
    try {
      await api(`/api/playlists/${playlist.id}/cover`, { method: "DELETE" });
      toast("Cover removed", "info");
      onChanged();
      onClose();
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  }

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[80] flex items-center justify-center bg-black/50 p-4 backdrop-blur-[2px]"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="animate-pop-in flex max-h-[80vh] w-full max-w-xl flex-col rounded-2xl border border-line bg-panel shadow-2xl"
      >
        <div className="flex items-center gap-3 border-b border-line p-4">
          <div className="h-14 w-14 shrink-0 overflow-hidden rounded-lg border border-line bg-panel-2">
            {playlist.coverUrl ? (
              <img src={playlist.coverUrl} alt="" className="h-full w-full object-cover" />
            ) : null}
          </div>
          <div className="min-w-0">
            <h2 id={titleId} className="text-base font-semibold">
              Cover image
            </h2>
            <p className="text-xs text-muted">
              Pick one from this collection or upload your own.
            </p>
          </div>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-4">
          <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
            {candidates.map((item) => (
              <button
                key={item.id}
                type="button"
                disabled={busy}
                onClick={() => chooseItem(item.id)}
                aria-label={`Use ${item.title} as the cover`}
                className="group relative aspect-square overflow-hidden rounded-lg border border-line bg-panel-2 hover:border-accent disabled:opacity-50"
              >
                <img
                  src={item.thumbUrl ?? item.fileUrl}
                  alt=""
                  className="h-full w-full object-cover"
                />
              </button>
            ))}
          </div>
          {candidates.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted">
              No images in this collection yet — upload one below.
            </p>
          ) : null}

          <label
            htmlFor="cover-file"
            className="mt-4 flex cursor-pointer items-center justify-center rounded-xl border border-dashed border-line px-4 py-4 text-sm text-muted hover:border-line-strong hover:text-ink"
          >
            Upload an image from this machine
          </label>
          <input
            ref={fileInput}
            id="cover-file"
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) void upload(file);
              event.currentTarget.value = "";
            }}
          />
        </div>

        <div className="flex items-center justify-between border-t border-line p-3">
          <button
            type="button"
            onClick={remove}
            disabled={busy || !playlist.coverUrl}
            className="flex items-center gap-1.5 rounded-lg border border-line px-3 py-2 text-xs text-muted hover:border-danger/50 hover:text-danger disabled:opacity-40"
          >
            <TrashIcon className="h-3.5 w-3.5" />
            Remove cover
          </button>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-line px-3.5 py-2 text-sm text-muted hover:text-ink"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

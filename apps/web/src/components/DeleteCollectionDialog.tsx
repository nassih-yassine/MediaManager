"use client";

import { useEffect, useId, useState } from "react";

import { ImageIcon, TrashIcon } from "@/components/icons";

export type DeleteCollectionMode = "move" | "delete_items";

interface DeleteCollectionDialogProps {
  open: boolean;
  name: string;
  itemCount: number;
  busy: boolean;
  onCancel: () => void;
  onConfirm: (mode: DeleteCollectionMode) => void;
}

export function DeleteCollectionDialog({
  open,
  name,
  itemCount,
  busy,
  onCancel,
  onConfirm,
}: DeleteCollectionDialogProps) {
  const titleId = useId();
  const [mode, setMode] = useState<DeleteCollectionMode>("move");

  useEffect(() => {
    if (open) setMode("move");
  }, [open]);

  useEffect(() => {
    if (!open) return;
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") onCancel();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onCancel]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[80] flex items-center justify-center bg-black/50 p-4 backdrop-blur-[2px]"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onCancel();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="animate-pop-in w-full max-w-md rounded-2xl border border-line bg-panel p-5 shadow-2xl"
      >
        <h2 id={titleId} className="text-base font-semibold">
          Delete gallery “{name}”?
        </h2>
        <p className="mt-1.5 text-sm text-muted">
          {itemCount} {itemCount === 1 ? "picture" : "pictures"}. Choose what
          happens to them.
        </p>

        <div className="mt-4 space-y-2">
          <button
            type="button"
            onClick={() => setMode("move")}
            aria-pressed={mode === "move"}
            className={`flex w-full items-start gap-3 rounded-xl border p-3 text-left transition-colors ${
              mode === "move"
                ? "border-accent bg-accent/10"
                : "border-line hover:border-line-strong"
            }`}
          >
            <ImageIcon className="mt-0.5 h-4 w-4 shrink-0 text-accent" />
            <span>
              <span className="block text-sm font-medium">
                Keep the pictures
              </span>
              <span className="mt-0.5 block text-xs text-muted">
                Move them to the default “All pictures” gallery.
              </span>
            </span>
          </button>

          <button
            type="button"
            onClick={() => setMode("delete_items")}
            aria-pressed={mode === "delete_items"}
            className={`flex w-full items-start gap-3 rounded-xl border p-3 text-left transition-colors ${
              mode === "delete_items"
                ? "border-danger/60 bg-danger/10"
                : "border-line hover:border-line-strong"
            }`}
          >
            <TrashIcon className="mt-0.5 h-4 w-4 shrink-0 text-danger" />
            <span>
              <span className="block text-sm font-medium text-danger">
                Delete the pictures too
              </span>
              <span className="mt-0.5 block text-xs text-muted">
                Removes the files from disk. This cannot be undone.
              </span>
            </span>
          </button>
        </div>

        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-lg border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => onConfirm(mode)}
            disabled={busy}
            className="rounded-lg bg-danger px-3.5 py-2 text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {busy
              ? "Working…"
              : mode === "delete_items"
                ? "Delete gallery and pictures"
                : "Delete gallery"}
          </button>
        </div>
      </div>
    </div>
  );
}

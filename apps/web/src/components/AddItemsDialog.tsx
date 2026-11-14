/* eslint-disable @next/next/no-img-element */
"use client";

import { useEffect, useId, useMemo, useRef, useState } from "react";

import { CheckIcon, SearchIcon } from "@/components/icons";
import { useToast } from "@/components/ui/Toast";
import { api } from "@/lib/api";
import { kindLabel } from "@/lib/kind";
import { isPlayable } from "@/lib/player-utils";
import type {
  CollectionKind,
  Item,
  ItemsPage,
} from "@/lib/types";

interface AddItemsDialogProps {
  open: boolean;
  collectionId: string;
  kind: CollectionKind;
  existingIds: string[];
  onClose: () => void;
  onAdded: () => void;
}

export function AddItemsDialog({
  open,
  collectionId,
  kind,
  existingIds,
  onClose,
  onAdded,
}: AddItemsDialogProps) {
  const toast = useToast();
  const titleId = useId();
  const searchRef = useRef<HTMLInputElement | null>(null);
  const [items, setItems] = useState<Item[]>([]);
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);

  const existing = useMemo(() => new Set(existingIds), [existingIds]);
  const gallery = kind === "gallery";

  useEffect(() => {
    if (!open) return;
    setSelected(new Set());
    setQuery("");
    setLoading(true);
    const params = new URLSearchParams({ limit: "150", sort: "title" });
    if (gallery) params.set("kind", "picture");
    api<ItemsPage>(`/api/items?${params}`)
      .then((page) =>
        setItems(page.items.filter((item) => !existing.has(item.id))),
      )
      .catch((err: Error) => toast(err.message, "error"))
      .finally(() => setLoading(false));
  }, [open, gallery, existing, toast]);

  useEffect(() => {
    if (!open) return;
    searchRef.current?.focus();
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return items;
    return items.filter((item) => item.title.toLowerCase().includes(needle));
  }, [items, query]);

  function toggle(id: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function add() {
    if (selected.size === 0) return;
    setBusy(true);
    try {
      await api(`/api/playlists/${collectionId}/items`, {
        method: "POST",
        body: JSON.stringify({ itemIds: Array.from(selected) }),
      });
      toast(
        `Added ${selected.size} ${selected.size === 1 ? "item" : "items"}`,
        "success",
      );
      window.dispatchEvent(new Event("mm:playlists-changed"));
      onAdded();
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
        className="animate-pop-in flex max-h-[78vh] w-full max-w-lg flex-col rounded-2xl border border-line bg-panel shadow-2xl"
      >
        <div className="border-b border-line p-4">
          <h2 id={titleId} className="text-base font-semibold">
            {gallery ? "Add pictures to the gallery" : "Add items to the playlist"}
          </h2>
          <div className="relative mt-3">
            <SearchIcon className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted" />
            <input
              ref={searchRef}
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search the library"
              aria-label="Search the library"
              className="h-9 w-full rounded-lg border border-line bg-canvas pr-3 pl-9 text-sm placeholder:text-muted/70 focus:outline-none"
            />
          </div>
        </div>

        <ul className="min-h-0 flex-1 overflow-y-auto p-2">
          {loading ? (
            <li className="px-3 py-6 text-center text-sm text-muted">Loading…</li>
          ) : visible.length === 0 ? (
            <li className="px-3 py-6 text-center text-sm text-muted">
              {items.length === 0
                ? `Every ${gallery ? "picture is" : "item is"} already here.`
                : "No matches."}
            </li>
          ) : (
            visible.map((item) => {
              const checked = selected.has(item.id);
              return (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => toggle(item.id)}
                    aria-pressed={checked}
                    className="flex w-full items-center gap-2.5 rounded-lg px-2 py-1.5 text-left transition-colors hover:bg-panel-2"
                  >
                    <span
                      className={`flex h-4 w-4 shrink-0 items-center justify-center rounded border ${
                        checked
                          ? "border-accent bg-accent text-accent-ink"
                          : "border-line-strong"
                      }`}
                      aria-hidden
                    >
                      {checked ? <CheckIcon className="h-3 w-3" /> : null}
                    </span>
                    <span className="h-9 w-9 shrink-0 overflow-hidden rounded-md bg-canvas">
                      {item.thumbUrl ? (
                        <img
                          src={item.thumbUrl}
                          alt=""
                          className="h-full w-full object-cover"
                        />
                      ) : (
                        <span className="flex h-full items-center justify-center text-[10px] text-muted uppercase">
                          {item.kind.slice(0, 1)}
                        </span>
                      )}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm">{item.title}</span>
                      <span className="mt-0.5 block font-mono text-[10px] text-muted">
                        {kindLabel(item.kind)}
                        {isPlayable(item) ? "" : " · opens in its app"}
                      </span>
                    </span>
                  </button>
                </li>
              );
            })
          )}
        </ul>

        <div className="flex items-center justify-between gap-2 border-t border-line p-3">
          <span className="text-xs text-muted">
            {selected.size} selected
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-line px-3.5 py-2 text-sm text-muted hover:text-ink"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={add}
              disabled={busy || selected.size === 0}
              className="rounded-lg bg-accent px-3.5 py-2 text-sm font-semibold text-accent-ink transition-opacity hover:opacity-90 disabled:opacity-40"
            >
              {busy ? "Adding…" : `Add ${selected.size || ""}`.trim()}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

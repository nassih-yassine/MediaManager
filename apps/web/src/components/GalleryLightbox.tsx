/* eslint-disable @next/next/no-img-element */
"use client";

import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";

import {
  ChevronLeftIcon,
  ChevronRightIcon,
  CloseIcon,
  OpenIcon,
} from "@/components/icons";
import type { Item } from "@/lib/types";

interface GalleryLightboxProps {
  items: Item[];
  index: number;
  onIndexChange: (index: number) => void;
  onClose: () => void;
}

export function GalleryLightbox({
  items,
  index,
  onIndexChange,
  onClose,
}: GalleryLightboxProps) {
  const thumbRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const touchStart = useRef<number | null>(null);
  const current = items[index];

  function go(delta: number) {
    if (items.length === 0) return;
    onIndexChange((index + delta + items.length) % items.length);
  }

  useEffect(() => {
    if (!current) return;
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
      if (event.key === "ArrowLeft") {
        event.preventDefault();
        onIndexChange((index - 1 + items.length) % items.length);
      }
      if (event.key === "ArrowRight") {
        event.preventDefault();
        onIndexChange((index + 1) % items.length);
      }
    }
    document.addEventListener("keydown", onKey);
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = previous;
    };
  }, [current, index, items.length, onClose, onIndexChange]);

  useEffect(() => {
    const thumb = thumbRefs.current[index];
    if (typeof thumb?.scrollIntoView !== "function") return;
    thumb.scrollIntoView({
      block: "nearest",
      inline: "center",
      behavior: "smooth",
    });
  }, [index]);

  if (!current) return null;

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Gallery viewer"
      className="fixed inset-0 z-[95] flex flex-col bg-black/95 text-white"
    >
      <div className="flex items-center justify-between gap-3 px-4 py-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">{current.title}</p>
          <p className="font-mono text-[11px] text-white/60">
            {index + 1} / {items.length}
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <a
            href={current.fileUrl}
            className="flex items-center gap-1.5 rounded-full border border-white/25 px-3 py-1.5 text-xs text-white/80 hover:text-white"
          >
            <OpenIcon className="h-3.5 w-3.5" />
            Open file
          </a>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close gallery viewer"
            className="rounded-full p-2 text-white/80 hover:bg-white/10 hover:text-white"
          >
            <CloseIcon />
          </button>
        </div>
      </div>

      <div
        className="relative flex min-h-0 flex-1 items-center justify-center px-2 sm:px-14"
        onClick={(event) => {
          if (event.target === event.currentTarget) onClose();
        }}
      >
        {items.length > 1 ? (
          <button
            type="button"
            onClick={() => go(-1)}
            aria-label="Previous picture"
            className="absolute left-2 z-10 rounded-full bg-black/50 p-2.5 text-white/80 backdrop-blur transition-colors hover:bg-black/70 hover:text-white sm:left-4"
          >
            <ChevronLeftIcon className="h-5 w-5" />
          </button>
        ) : null}

        <img
          src={current.fileUrl}
          alt={current.title}
          onTouchStart={(event) => {
            touchStart.current = event.touches[0]?.clientX ?? null;
          }}
          onTouchEnd={(event) => {
            if (touchStart.current == null) return;
            const delta =
              (event.changedTouches[0]?.clientX ?? touchStart.current) -
              touchStart.current;
            if (Math.abs(delta) > 50) go(delta > 0 ? -1 : 1);
            touchStart.current = null;
          }}
          className="max-h-full max-w-full rounded-lg object-contain"
        />

        {items.length > 1 ? (
          <button
            type="button"
            onClick={() => go(1)}
            aria-label="Next picture"
            className="absolute right-2 z-10 rounded-full bg-black/50 p-2.5 text-white/80 backdrop-blur transition-colors hover:bg-black/70 hover:text-white sm:right-4"
          >
            <ChevronRightIcon className="h-5 w-5" />
          </button>
        ) : null}
      </div>

      <div className="flex shrink-0 gap-1.5 overflow-x-auto px-3 py-3">
        {items.map((item, at) => (
          <button
            key={item.id}
            ref={(element) => {
              thumbRefs.current[at] = element;
            }}
            type="button"
            onClick={() => onIndexChange(at)}
            aria-label={`Show ${item.title}`}
            className={`h-14 w-14 shrink-0 overflow-hidden rounded-md border-2 transition-opacity ${
              at === index
                ? "border-accent opacity-100"
                : "border-transparent opacity-50 hover:opacity-90"
            }`}
          >
            <img
              src={item.thumbUrl ?? item.fileUrl}
              alt=""
              className="h-full w-full object-cover"
            />
          </button>
        ))}
      </div>
    </div>,
    document.body,
  );
}

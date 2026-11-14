"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

import { BellIcon } from "@/components/icons";
import { useNotifications } from "@/lib/notifications";

function timeAgo(ms: number): string {
  const seconds = Math.max(1, Math.round((Date.now() - ms) / 1000));
  if (seconds < 60) return "now";
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h`;
  return `${Math.round(hours / 24)}d`;
}

export function NotificationBell() {
  const { items, unread, markRead, markAllRead } = useNotifications();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [coords, setCoords] = useState<{ left: number; top: number } | null>(null);
  const wrapRef = useRef<HTMLDivElement | null>(null);
  const panelRef = useRef<HTMLDivElement | null>(null);
  const triggerRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (!open) {
      setCoords(null);
      return;
    }
    const rect = triggerRef.current?.getBoundingClientRect();
    if (rect) {
      const width = 320;
      setCoords({
        left: Math.min(
          Math.max(8, rect.right - width),
          window.innerWidth - width - 8,
        ),
        top: rect.bottom + 8,
      });
    }
    function onPointerDown(event: PointerEvent) {
      const target = event.target as Node;
      if (
        !wrapRef.current?.contains(target) &&
        !panelRef.current?.contains(target)
      ) {
        setOpen(false);
      }
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

  function follow(id: string, href: string | null) {
    void markRead(id);
    setOpen(false);
    if (href) router.push(href);
  }

  return (
    <div ref={wrapRef} className="relative">
      <button
        ref={triggerRef}
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label={`Notifications${unread > 0 ? ` (${unread} unread)` : ""}`}
        aria-expanded={open}
        className="relative rounded-lg p-2 text-muted transition-colors hover:bg-panel-2 hover:text-ink"
      >
        <BellIcon className="h-4 w-4" />
        {unread > 0 ? (
          <span className="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-accent px-1 text-[9px] font-bold text-accent-ink">
            {unread > 9 ? "9+" : unread}
          </span>
        ) : null}
      </button>

      {open && coords
        ? createPortal(
            <div
              ref={panelRef}
              role="dialog"
              aria-label="Notifications"
              style={{ left: coords.left, top: coords.top }}
              className="fixed z-[90] w-80 rounded-xl border border-line bg-panel p-2 shadow-xl"
            >
              <div className="flex items-center justify-between px-2 py-1">
                <span className="text-sm font-semibold">Notifications</span>
                <button
                  type="button"
                  onClick={() => void markAllRead()}
                  disabled={unread === 0}
                  className="text-[11px] text-muted hover:text-ink disabled:opacity-40"
                >
                  Mark all read
                </button>
              </div>
              {items.length === 0 ? (
                <p className="px-2 py-6 text-center text-xs text-muted">
                  Nothing yet. Reports, announcements and downloads land here.
                </p>
              ) : (
                <ul className="mt-1 max-h-80 space-y-0.5 overflow-y-auto">
                  {items.slice(0, 8).map((item) => (
                    <li key={item.id}>
                      <button
                        type="button"
                        onClick={() => follow(item.id, item.href)}
                        className={`w-full rounded-lg px-2 py-2 text-left transition-colors hover:bg-panel-2 ${
                          item.readAt === null ? "bg-panel-2/60" : ""
                        }`}
                      >
                        <span className="flex items-start gap-2">
                          <span
                            className={`mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full ${
                              item.readAt === null ? "bg-accent" : "bg-transparent"
                            }`}
                          />
                          <span className="min-w-0">
                            <span className="block truncate text-[13px] font-medium">
                              {item.title}
                            </span>
                            {item.body ? (
                              <span className="mt-0.5 line-clamp-2 block text-[11px] leading-relaxed text-muted">
                                {item.body}
                              </span>
                            ) : null}
                            <span className="mt-0.5 block font-mono text-[10px] text-muted">
                              {timeAgo(item.createdAt)}
                            </span>
                          </span>
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              <Link
                href="/notifications"
                onClick={() => setOpen(false)}
                className="mt-1 block rounded-lg px-2 py-1.5 text-center text-xs text-muted hover:text-ink"
              >
                View all
              </Link>
            </div>,
            document.body,
          )
        : null}
    </div>
  );
}

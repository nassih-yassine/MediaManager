"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

import { NotificationBell } from "@/components/NotificationBell";
import { useAuth } from "@/lib/auth";

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

export function AppHeader() {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [coords, setCoords] = useState<{ right: number; top: number } | null>(null);
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
      setCoords({ right: window.innerWidth - rect.right, top: rect.bottom + 8 });
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

  async function signOut() {
    setOpen(false);
    await logout();
    router.replace("/login");
  }

  if (!user) return null;

  return (
    <header className="flex items-center justify-end gap-1.5 px-4 pt-3 sm:px-6 md:px-10 md:pt-6">
      <NotificationBell />
      <div ref={wrapRef} className="relative">
        <button
          ref={triggerRef}
          type="button"
          onClick={() => setOpen((value) => !value)}
          aria-label="Account menu"
          aria-expanded={open}
          className="flex items-center gap-2 rounded-full border border-line py-1 pr-2.5 pl-1 text-sm transition-colors hover:border-line-strong"
        >
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-accent text-[11px] font-bold text-accent-ink">
            {initials(user.name)}
          </span>
          <span className="hidden max-w-28 truncate text-[13px] sm:block">
            {user.name}
          </span>
        </button>

        {open && coords
          ? createPortal(
              <div
                ref={panelRef}
                role="dialog"
                aria-label="Account"
                style={{ right: coords.right, top: coords.top }}
                className="fixed z-[90] w-56 rounded-xl border border-line bg-panel p-2 shadow-xl"
              >
                <div className="px-2 py-1.5">
                  <span className="block truncate text-[13px] font-medium">
                    {user.name}
                  </span>
                  <span className="block truncate text-[11px] text-muted">
                    {user.email}
                  </span>
                  <span className="mt-1 inline-block rounded-full border border-line px-1.5 py-0.5 text-[9px] uppercase tracking-[0.08em] text-muted">
                    {user.role}
                  </span>
                </div>
                <Link
                  href="/profile"
                  onClick={() => setOpen(false)}
                  className="mt-1 block rounded-lg px-2 py-1.5 text-[13px] text-muted transition-colors hover:bg-panel-2 hover:text-ink"
                >
                  Profile &amp; appearance
                </Link>
                {user.role === "admin" ? (
                  <Link
                    href="/admin"
                    onClick={() => setOpen(false)}
                    className="block rounded-lg px-2 py-1.5 text-[13px] text-muted transition-colors hover:bg-panel-2 hover:text-ink"
                  >
                    Admin
                  </Link>
                ) : null}
                <button
                  type="button"
                  onClick={() => void signOut()}
                  className="mt-1 block w-full rounded-lg px-2 py-1.5 text-left text-[13px] text-muted transition-colors hover:bg-panel-2 hover:text-ink"
                >
                  Sign out
                </button>
              </div>,
              document.body,
            )
          : null}
      </div>
    </header>
  );
}

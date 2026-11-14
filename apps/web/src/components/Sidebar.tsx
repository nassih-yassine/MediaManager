"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";

import { ThemePicker } from "@/components/ThemePicker";
import { PlusIcon } from "@/components/icons";
import { api } from "@/lib/api";
import type { PlaylistSummary } from "@/lib/types";

const NAV = [
  { href: "/", label: "Library" },
  { href: "/add", label: "Add media" },
];

export function Sidebar() {
  const pathname = usePathname();
  const [playlists, setPlaylists] = useState<PlaylistSummary[]>([]);

  useEffect(() => {
    let cancelled = false;
    api<{ playlists: PlaylistSummary[] }>("/api/playlists")
      .then((data) => {
        if (!cancelled) setPlaylists(data.playlists);
      })
      .catch(() => {
        // backend not up yet; the lists simply stay empty
      });
    return () => {
      cancelled = true;
    };
  }, [pathname]);

  return (
    <aside className="w-full border-b border-line bg-panel/60 md:sticky md:top-0 md:h-dvh md:w-[236px] md:shrink-0 md:overflow-y-auto md:border-r md:border-b-0">
      <div className="flex items-center justify-between gap-4 p-4 md:h-full md:flex-col md:items-stretch md:justify-start md:gap-6 md:p-6">
        <Link href="/" className="flex shrink-0 items-baseline gap-1.5">
          <span className="text-[17px] font-semibold tracking-tight">Media</span>
          <span className="text-[17px] font-semibold tracking-tight text-accent">
            Manager
          </span>
        </Link>

        <nav className="flex gap-1 md:w-full md:flex-col md:gap-1.5">
          {NAV.map((item) => {
            const active =
              item.href === "/"
                ? pathname === "/"
                : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`rounded-lg px-3 py-2 text-sm transition-colors ${
                  active
                    ? "bg-panel-2 text-ink"
                    : "text-muted hover:bg-panel-2 hover:text-ink"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="hidden md:mt-2 md:block md:w-full">
          <div className="flex items-center justify-between px-3 pb-1.5">
            <span className="text-[11px] font-medium tracking-wide text-muted uppercase">
              Playlists
            </span>
            <Link
              href="/playlists"
              aria-label="Manage playlists"
              className="rounded p-1 text-muted transition-colors hover:text-ink"
            >
              <PlusIcon className="h-3.5 w-3.5" />
            </Link>
          </div>
          <div className="space-y-0.5">
            {playlists.length === 0 ? (
              <p className="px-3 py-1 text-xs text-muted">None yet</p>
            ) : (
              playlists.slice(0, 10).map((playlist) => {
                const active = pathname === `/playlists/${playlist.id}`;
                return (
                  <Link
                    key={playlist.id}
                    href={`/playlists/${playlist.id}`}
                    className={`block truncate rounded-lg px-3 py-1.5 text-[13px] transition-colors ${
                      active
                        ? "bg-panel-2 text-ink"
                        : "text-muted hover:bg-panel-2 hover:text-ink"
                    }`}
                  >
                    {playlist.name}
                  </Link>
                );
              })
            )}
            {playlists.length > 10 ? (
              <Link
                href="/playlists"
                className="block px-3 py-1.5 text-xs text-muted hover:text-ink"
              >
                All playlists…
              </Link>
            ) : null}
          </div>
        </div>

        <div className="hidden md:mt-auto md:block md:w-full">
          <ThemePicker />
          <p className="mt-3 px-3 text-xs leading-relaxed text-muted">
            Files live in your library folder; this app only indexes them.
          </p>
        </div>

        <div className="md:hidden">
          <ThemePicker />
        </div>
      </div>
    </aside>
  );
}

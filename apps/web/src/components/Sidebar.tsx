"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";

import {
  AddMediaIcon,
  DiscIcon,
  LibraryIcon,
  PlusIcon,
} from "@/components/icons";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { useRealtime } from "@/lib/realtime";
import type { PlaylistSummary } from "@/lib/types";

const NAV = [
  { href: "/", key: "nav.library", icon: LibraryIcon, mobileOnly: false },
  { href: "/add", key: "nav.add", icon: AddMediaIcon, mobileOnly: false },
  { href: "/playlists", key: "nav.playlists", icon: DiscIcon, mobileOnly: true },
] as const;

export function Sidebar() {
  const pathname = usePathname();
  const { user } = useAuth();
  const { t } = useI18n();
  const [playlists, setPlaylists] = useState<PlaylistSummary[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    api<{ playlists: PlaylistSummary[] }>("/api/playlists?kind=playlist")
      .then((data) => {
        if (!cancelled) setPlaylists(data.playlists);
      })
      .catch(() => {
        // the library error banner covers a missing backend
      });
    return () => {
      cancelled = true;
    };
  }, [pathname, refreshKey]);

  useEffect(() => {
    function refresh() {
      setRefreshKey((value) => value + 1);
    }
    window.addEventListener("mm:playlists-changed", refresh);
    return () => window.removeEventListener("mm:playlists-changed", refresh);
  }, []);

  useRealtime((event) => {
    if (event.type === "collection.changed" || event.type === "item.deleted") {
      setRefreshKey((value) => value + 1);
    }
  });

  return (
    <aside className="w-full min-w-0 border-b border-line bg-panel/50 md:sticky md:top-0 md:h-dvh md:w-[252px] md:shrink-0 md:overflow-y-auto md:border-r md:border-b-0">
      <div className="flex min-w-0 items-center gap-2 px-3 py-2.5 md:h-full md:flex-col md:items-stretch md:justify-start md:gap-7 md:px-5 md:pt-6 md:pb-28">
        <Link href="/" className="group flex shrink-0 items-center gap-2.5">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/logo.svg"
            alt=""
            className="h-8 w-8 rounded-[10px] transition-transform group-hover:-rotate-3"
          />

          <span className="text-[15px] leading-none font-semibold tracking-tight">
            MediaManager
          </span>
        </Link>

        <nav className="flex min-w-0 flex-1 items-center gap-1 overflow-x-auto md:w-full md:flex-none md:flex-col md:items-stretch md:gap-1 md:overflow-visible">
          {NAV.map((item) => {
            const active =
              item.href === "/"
                ? pathname === "/"
                : pathname.startsWith(item.href);
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm transition-colors ${
                  item.mobileOnly ? "md:hidden" : ""
                } ${
                  active
                    ? "bg-panel-2 text-ink"
                    : "text-muted hover:bg-panel-2/70 hover:text-ink"
                }`}
              >
                <Icon className="h-4 w-4" />
                <span className="font-medium">{t(item.key)}</span>
              </Link>
            );
          })}
          {user?.role === "admin" ? (
            <Link
              href="/admin"
              className={`flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm transition-colors ${
                pathname.startsWith("/admin")
                  ? "bg-panel-2 text-ink"
                  : "text-muted hover:bg-panel-2/70 hover:text-ink"
              }`}
            >
              <span className="flex h-4 w-4 items-center justify-center rounded-[4px] border border-current text-[9px] font-bold">
                A
              </span>
              <span className="font-medium">{t("nav.admin")}</span>
            </Link>
          ) : null}
        </nav>

        <div className="hidden md:block md:w-full">
          <div className="flex items-center justify-between px-2.5 pb-2">
            <span className="text-[10px] font-semibold tracking-[0.14em] text-muted uppercase">
              {t("sidebar.playlists")}
            </span>
            <Link
              href="/playlists"
              aria-label={t("sidebar.newPlaylist")}
              className="rounded-md p-1 text-muted transition-colors hover:bg-panel-2 hover:text-ink"
            >
              <PlusIcon className="h-3.5 w-3.5" />
            </Link>
          </div>

          <div className="space-y-0.5">
            {playlists.length === 0 ? (
              <Link
                href="/playlists"
                className="block rounded-lg px-2.5 py-2 text-xs leading-relaxed text-muted transition-colors hover:bg-panel-2 hover:text-ink"
              >
                {t("sidebar.firstPlaylist")}
              </Link>
            ) : (
              playlists.slice(0, 12).map((playlist) => {
                const active = pathname === `/playlists/${playlist.id}`;
                return (
                  <Link
                    key={playlist.id}
                    href={`/playlists/${playlist.id}`}
                    className={`group flex items-center gap-2.5 rounded-lg px-2.5 py-1.5 text-[13px] transition-colors ${
                      active
                        ? "bg-panel-2 text-ink"
                        : "text-muted hover:bg-panel-2/70 hover:text-ink"
                    }`}
                  >
                    <DiscIcon className="h-3.5 w-3.5 shrink-0 opacity-70" />
                    <span className="min-w-0 flex-1 truncate">{playlist.name}</span>
                    <span className="text-[11px] text-muted tabular-nums">
                      {playlist.itemCount}
                    </span>
                  </Link>
                );
              })
            )}
            {playlists.length > 12 ? (
              <Link
                href="/playlists"
                className="block px-2.5 py-1.5 text-xs text-muted hover:text-ink"
              >
                All playlists…
              </Link>
            ) : null}
          </div>
        </div>

      </div>
    </aside>
  );
}

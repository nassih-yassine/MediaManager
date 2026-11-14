"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV = [
  { href: "/", label: "Library" },
  { href: "/add", label: "Add media" },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="w-full border-b border-line bg-panel/60 md:sticky md:top-0 md:h-dvh md:w-[236px] md:shrink-0 md:border-r md:border-b-0">
      <div className="flex items-center justify-between gap-6 p-4 md:h-full md:flex-col md:items-stretch md:justify-start md:gap-8 md:p-6">
        <Link href="/" className="flex items-baseline gap-1.5">
          <span className="text-[17px] font-semibold tracking-tight">
            Media
          </span>
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

        <p className="hidden text-xs leading-relaxed text-muted md:mt-auto md:block">
          Downloads land in your library folder on disk. The app only indexes
          them.
        </p>
      </div>
    </aside>
  );
}

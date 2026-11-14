"use client";

import { useRouter } from "next/navigation";

import { useNotifications } from "@/lib/notifications";
import { formatDate } from "@/lib/api";

export default function NotificationsPage() {
  const { items, unread, loading, markAllRead, markRead } = useNotifications();
  const router = useRouter();

  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-[10px] font-semibold tracking-[0.18em] text-muted uppercase">
            Account
          </p>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">Notifications</h1>
          <p className="mt-1 text-sm text-muted">
            Reports, announcements and download outcomes.
          </p>
        </div>
        <button
          type="button"
          onClick={() => void markAllRead()}
          disabled={unread === 0}
          className="rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink disabled:opacity-40"
        >
          Mark all read
        </button>
      </div>

      {loading ? (
        <p className="mt-8 text-sm text-muted">Loading…</p>
      ) : items.length === 0 ? (
        <div className="mt-8 rounded-2xl border border-dashed border-line px-6 py-16 text-center">
          <p className="text-lg font-semibold">Nothing yet</p>
          <p className="mx-auto mt-2 max-w-sm text-sm text-muted">
            New reports, announcements and finished downloads appear here.
          </p>
        </div>
      ) : (
        <ul className="mt-6 space-y-1.5">
          {items.map((item) => (
            <li key={item.id}>
              <button
                type="button"
                onClick={() => {
                  void markRead(item.id);
                  if (item.href) router.push(item.href);
                }}
                className={`flex w-full items-start gap-3 rounded-xl border px-4 py-3 text-left transition-colors hover:bg-panel-2 ${
                  item.readAt === null
                    ? "border-accent/30 bg-panel-2/50"
                    : "border-line bg-panel"
                }`}
              >
                <span
                  className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${
                    item.readAt === null ? "bg-accent" : "bg-line-strong"
                  }`}
                />
                <span className="min-w-0 flex-1">
                  <span className="flex flex-wrap items-baseline justify-between gap-2">
                    <span className="text-sm font-medium">{item.title}</span>
                    <span className="font-mono text-[10px] text-muted">
                      {formatDate(item.createdAt)}
                    </span>
                  </span>
                  {item.body ? (
                    <span className="mt-1 block text-sm leading-relaxed text-muted">
                      {item.body}
                    </span>
                  ) : null}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

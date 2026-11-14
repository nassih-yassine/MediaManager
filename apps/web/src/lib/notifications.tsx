"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";

import { api } from "@/lib/api";
import { useRealtime } from "@/lib/realtime";

export interface AppNotification {
  id: string;
  kind: string;
  title: string;
  body: string | null;
  href: string | null;
  readAt: number | null;
  createdAt: number;
}

interface NotificationState {
  items: AppNotification[];
  unread: number;
  loading: boolean;
  refresh: () => Promise<void>;
  markRead: (id: string) => Promise<void>;
  markAllRead: () => Promise<void>;
}

const NotificationContext = createContext<NotificationState | null>(null);

export function NotificationProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<AppNotification[]>([]);
  const [unread, setUnread] = useState(0);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const data = await api<{ notifications: AppNotification[]; unread: number }>(
        "/api/notifications?limit=50",
      );
      setItems(data.notifications);
      setUnread(data.unread);
    } catch {
      // The bell simply stays at its last known state.
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useRealtime((event) => {
    if (event.type === "notification.created") void refresh();
  });

  const markRead = useCallback(async (id: string) => {
    setItems((current) =>
      current.map((item) =>
        item.id === id && item.readAt === null
          ? { ...item, readAt: Date.now() }
          : item,
      ),
    );
    setUnread((current) => Math.max(0, current - 1));
    try {
      await api(`/api/notifications/${id}/read`, { method: "POST" });
    } catch {
      void refresh();
    }
  }, [refresh]);

  const markAllRead = useCallback(async () => {
    const now = Date.now();
    setItems((current) =>
      current.map((item) =>
        item.readAt === null ? { ...item, readAt: now } : item,
      ),
    );
    setUnread(0);
    try {
      await api("/api/notifications/read-all", { method: "POST" });
    } catch {
      void refresh();
    }
  }, [refresh]);

  return (
    <NotificationContext.Provider
      value={{ items, unread, loading, refresh, markRead, markAllRead }}
    >
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotifications(): NotificationState {
  const value = useContext(NotificationContext);
  if (!value) {
    throw new Error("useNotifications must be used inside NotificationProvider");
  }
  return value;
}

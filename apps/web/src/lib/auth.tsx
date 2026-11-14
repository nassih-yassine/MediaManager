"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";

import { api } from "@/lib/api";
import { resetRealtime } from "@/lib/realtime";
import type { AuthUser } from "@/lib/types";

/** Everything tied to the signed-in account, not the browser. */
function clearUserState() {
  if (typeof window !== "undefined") {
    localStorage.removeItem("mm.player.v1");
  }
  resetRealtime();
}

interface AuthState {
  user: AuthUser | null;
  loading: boolean;
  needsSetup: boolean;
  login: (email: string, password: string) => Promise<void>;
  setup: (email: string, name: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [needsSetup, setNeedsSetup] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const status = await api<{ needsSetup: boolean }>("/api/auth/status");
      setNeedsSetup(status.needsSetup);
      if (status.needsSetup) {
        setUser(null);
        return;
      }
      const me = await api<{ user: AuthUser }>("/api/auth/me").catch(() => null);
      setUser(me?.user ?? null);
    } catch {
      setUser(null);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    refresh().finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [refresh]);

  useEffect(() => {
    function unauthorized() {
      setUser(null);
      clearUserState();
    }
    window.addEventListener("mm:unauthorized", unauthorized);
    return () => window.removeEventListener("mm:unauthorized", unauthorized);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const data = await api<{ user: AuthUser }>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    setUser(data.user);
    setNeedsSetup(false);
  }, []);

  const setup = useCallback(async (email: string, name: string, password: string) => {
    const data = await api<{ user: AuthUser }>("/api/auth/setup", {
      method: "POST",
      body: JSON.stringify({ email, name, password }),
    });
    setUser(data.user);
    setNeedsSetup(false);
  }, []);

  const logout = useCallback(async () => {
    await api("/api/auth/logout", { method: "POST" }).catch(() => {});
    setUser(null);
    clearUserState();
  }, []);

  return (
    <AuthContext.Provider
      value={{ user, loading, needsSetup, login, setup, logout, refresh }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}

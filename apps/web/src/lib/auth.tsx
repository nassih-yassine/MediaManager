"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";

import { api } from "@/lib/api";
import {
  APPEARANCE_KEY,
  applyAppearance,
  currentAppearance,
  type ThemeMode,
  type ThemeName,
} from "@/lib/appearance";
import { resetRealtime } from "@/lib/realtime";
import type { AuthUser } from "@/lib/types";

export interface Preferences {
  theme: ThemeName;
  themeMode: ThemeMode;
}

/** Everything tied to the signed-in account, not the browser. */
function clearUserState() {
  if (typeof window !== "undefined") {
    localStorage.removeItem("mm.player.v1");
    // Never let the next account inherit this one's first-paint theme.
    localStorage.removeItem(APPEARANCE_KEY);
  }
  resetRealtime();
}

interface AuthState {
  user: AuthUser | null;
  loading: boolean;
  needsSetup: boolean;
  preferences: Preferences | null;
  login: (email: string, password: string) => Promise<void>;
  setup: (email: string, name: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  savePreferences: (partial: Partial<Preferences>) => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [needsSetup, setNeedsSetup] = useState(false);
  const [preferences, setPreferences] = useState<Preferences | null>(null);

  const loadPreferences = useCallback(async () => {
    const profile = await api<{ theme: ThemeName; themeMode: ThemeMode }>(
      "/api/profile",
    ).catch(() => null);
    if (profile) {
      setPreferences({ theme: profile.theme, themeMode: profile.themeMode });
      applyAppearance(profile.theme, profile.themeMode);
    }
  }, []);

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
      if (me?.user) await loadPreferences();
    } catch {
      setUser(null);
    }
  }, [loadPreferences]);

  const savePreferences = useCallback(async (partial: Partial<Preferences>) => {
    setPreferences((current) => {
      const fallback = currentAppearance();
      const next = {
        theme: partial.theme ?? current?.theme ?? fallback.theme,
        themeMode: partial.themeMode ?? current?.themeMode ?? fallback.mode,
      };
      applyAppearance(next.theme, next.themeMode);
      return next;
    });
    try {
      await api("/api/profile", {
        method: "PATCH",
        body: JSON.stringify(partial),
      });
    } catch {
      // Keep the local choice; the next successful save syncs it.
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
    await loadPreferences();
  }, [loadPreferences]);

  const setup = useCallback(async (email: string, name: string, password: string) => {
    const data = await api<{ user: AuthUser }>("/api/auth/setup", {
      method: "POST",
      body: JSON.stringify({ email, name, password }),
    });
    setUser(data.user);
    setNeedsSetup(false);
    await loadPreferences();
  }, [loadPreferences]);

  const logout = useCallback(async () => {
    await api("/api/auth/logout", { method: "POST" }).catch(() => {});
    setUser(null);
    setPreferences(null);
    clearUserState();
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        needsSetup,
        preferences,
        login,
        setup,
        logout,
        refresh,
        savePreferences,
      }}
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

/** For components that also render outside the signed-in shell (tests, docs). */
export function useOptionalAuth(): AuthState | null {
  return useContext(AuthContext);
}

export type ThemeName = "slate" | "paper" | "ocean" | "moss";
export type ThemeMode = "dark" | "light";

export const APPEARANCE_KEY = "mm.appearance";

export function applyAppearance(theme: ThemeName, mode: ThemeMode) {
  if (typeof document === "undefined") return;
  const root = document.documentElement;
  root.dataset.theme = theme;
  root.dataset.mode = mode;
  try {
    localStorage.setItem(APPEARANCE_KEY, JSON.stringify({ theme, mode }));
  } catch {
    // storage may be unavailable; the page still renders
  }
}

export function currentAppearance(): { theme: ThemeName; mode: ThemeMode } {
  if (typeof document === "undefined") return { theme: "slate", mode: "dark" };
  const root = document.documentElement;
  const prefersLight = window.matchMedia?.("(prefers-color-scheme: light)").matches;
  return {
    theme: (root.dataset.theme as ThemeName) || "slate",
    mode: (root.dataset.mode as ThemeMode) || (prefersLight ? "light" : "dark"),
  };
}

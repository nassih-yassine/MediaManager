"use client";

import { useEffect, useRef, useState } from "react";

const STORAGE_KEY = "mm.appearance";

const THEMES = [
  { id: "slate", label: "Slate", canvas: "#0a0a0b", surface: "#17171a", accent: "#9ae6b4" },
  { id: "paper", label: "Paper", canvas: "#12100e", surface: "#211d19", accent: "#e8b06a" },
  { id: "ocean", label: "Ocean", canvas: "#071018", surface: "#10222e", accent: "#5cc8f5" },
  { id: "moss", label: "Moss", canvas: "#080c09", surface: "#131d16", accent: "#b8e986" },
] as const;

type ThemeId = (typeof THEMES)[number]["id"];
type Mode = "dark" | "light";

function applyAppearance(theme: ThemeId, mode: Mode) {
  const root = document.documentElement;
  root.dataset.theme = theme;
  root.dataset.mode = mode;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ theme, mode }));
  } catch {
    // storage may be unavailable; the page still renders
  }
}

export function ThemePicker() {
  const [theme, setTheme] = useState<ThemeId>("slate");
  const [mode, setMode] = useState<Mode>("dark");
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const root = document.documentElement;
    const initialTheme = (root.dataset.theme as ThemeId) || "slate";
    const initialMode =
      (root.dataset.mode as Mode) ||
      (window.matchMedia("(prefers-color-scheme: light)").matches
        ? "light"
        : "dark");
    setTheme(initialTheme);
    setMode(initialMode);
    applyAppearance(initialTheme, initialMode);
  }, []);

  useEffect(() => {
    if (!open) return;
    function onPointerDown(event: PointerEvent) {
      if (!wrapRef.current?.contains(event.target as Node)) setOpen(false);
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

  function pickTheme(next: ThemeId) {
    setTheme(next);
    applyAppearance(next, mode);
  }

  function pickMode(next: Mode) {
    setMode(next);
    applyAppearance(theme, next);
  }

  return (
    <div ref={wrapRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-label="Appearance settings"
        className="flex w-full items-center justify-between rounded-lg px-3 py-2 text-sm text-muted transition-colors hover:bg-panel-2 hover:text-ink"
      >
        <span>Appearance</span>
        <span className="flex items-center gap-1" aria-hidden>
          {["var(--canvas)", "var(--panel-2)", "var(--accent)"].map((color) => (
            <span
              key={color}
              className="h-2.5 w-2.5 rounded-full border border-line"
              style={{ background: color }}
            />
          ))}
        </span>
      </button>

      {open ? (
        <div className="absolute right-0 bottom-full z-50 mb-2 w-60 rounded-xl border border-line bg-panel p-3 shadow-xl md:right-auto md:left-0">
          <p className="text-[11px] font-medium tracking-wide text-muted uppercase">
            Theme
          </p>
          <div className="mt-2 grid grid-cols-2 gap-2">
            {THEMES.map((entry) => (
              <button
                key={entry.id}
                type="button"
                onClick={() => pickTheme(entry.id)}
                className={`flex items-center gap-2 rounded-lg border px-2 py-2 text-left text-xs transition-colors ${
                  theme === entry.id
                    ? "border-accent/60 bg-panel-2 text-ink"
                    : "border-line text-muted hover:text-ink"
                }`}
              >
                <span
                  className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md border border-line"
                  style={{ background: entry.canvas }}
                >
                  <span
                    className="h-2 w-2 rounded-full"
                    style={{ background: entry.accent }}
                  />
                </span>
                {entry.label}
              </button>
            ))}
          </div>

          <p className="mt-3 text-[11px] font-medium tracking-wide text-muted uppercase">
            Mode
          </p>
          <div className="mt-2 inline-flex w-full rounded-lg border border-line bg-canvas p-1">
            {(["dark", "light"] as const).map((value) => (
              <button
                key={value}
                type="button"
                onClick={() => pickMode(value)}
                className={`flex-1 rounded-md px-2 py-1.5 text-xs capitalize transition-colors ${
                  mode === value
                    ? "bg-panel-2 text-ink"
                    : "text-muted hover:text-ink"
                }`}
              >
                {value}
              </button>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}

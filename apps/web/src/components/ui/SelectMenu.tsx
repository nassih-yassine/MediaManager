"use client";

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

import { CheckIcon, ChevronDownIcon } from "@/components/icons";

export interface SelectOption {
  value: string;
  label: string;
}

interface SelectMenuProps {
  value: string;
  options: SelectOption[];
  onChange: (value: string) => void;
  ariaLabel: string;
  className?: string;
  panelWidth?: number;
}

/** A styled alternative to <select>; the menu is portaled so it never clips. */
export function SelectMenu({
  value,
  options,
  onChange,
  ariaLabel,
  className = "",
  panelWidth = 224,
}: SelectMenuProps) {
  const [open, setOpen] = useState(false);
  const [coords, setCoords] = useState<{
    left: number;
    top: number;
    up: boolean;
  } | null>(null);
  const wrap = useRef<HTMLDivElement | null>(null);
  const trigger = useRef<HTMLButtonElement | null>(null);
  const panel = useRef<HTMLDivElement | null>(null);

  const selected = options.find((option) => option.value === value);

  useEffect(() => {
    if (!open) {
      setCoords(null);
      return;
    }

    function place() {
      const element = trigger.current;
      if (!element) return;
      const margin = 8;
      const rect = element.getBoundingClientRect();
      const left = Math.min(
        Math.max(margin, rect.left),
        window.innerWidth - panelWidth - margin,
      );
      const spaceBelow = window.innerHeight - rect.bottom;
      const up = spaceBelow < 260;
      setCoords({
        left,
        top: up ? rect.top - margin : rect.bottom + margin,
        up,
      });
    }

    place();
    window.addEventListener("resize", place);
    window.addEventListener("scroll", place, true);
    return () => {
      window.removeEventListener("resize", place);
      window.removeEventListener("scroll", place, true);
    };
  }, [open, panelWidth]);

  useEffect(() => {
    if (!open) return;
    function onPointerDown(event: PointerEvent) {
      const target = event.target as Node;
      if (
        !wrap.current?.contains(target) &&
        !panel.current?.contains(target)
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

  return (
    <div ref={wrap} className="relative">
      <button
        ref={trigger}
        type="button"
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
        className={`flex items-center justify-between gap-2 text-left ${className}`}
      >
        <span className="truncate">{selected?.label ?? options[0]?.label}</span>
        <ChevronDownIcon className="h-3 w-3 shrink-0 text-muted" />
      </button>

      {open && coords
        ? createPortal(
            <div
              ref={panel}
              role="listbox"
              aria-label={ariaLabel}
              style={{
                left: coords.left,
                top: coords.top,
                transform: coords.up ? "translateY(-100%)" : undefined,
                width: panelWidth,
              }}
              className="fixed z-[90] rounded-xl border border-line bg-panel p-1 shadow-2xl"
            >
              {options.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  role="option"
                  aria-selected={option.value === value}
                  onClick={() => {
                    onChange(option.value);
                    setOpen(false);
                  }}
                  className={`flex w-full items-center justify-between gap-2 rounded-lg px-2.5 py-2 text-left text-sm transition-colors ${
                    option.value === value
                      ? "bg-panel-2 text-ink"
                      : "text-muted hover:bg-panel-2 hover:text-ink"
                  }`}
                >
                  <span className="truncate">{option.label}</span>
                  {option.value === value ? (
                    <CheckIcon className="h-3.5 w-3.5 shrink-0 text-accent" />
                  ) : null}
                </button>
              ))}
            </div>,
            document.body,
          )
        : null}
    </div>
  );
}

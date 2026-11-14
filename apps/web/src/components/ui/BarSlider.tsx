"use client";

interface BarSliderProps {
  value: number;
  max: number;
  step?: number;
  onChange: (value: number) => void;
  ariaLabel: string;
  className?: string;
}

/**
 * One slider look for every control: a filled accent track and a thumb that
 * appears on hover/focus. The native input stays on top for accessibility.
 */
export function BarSlider({
  value,
  max,
  step = 0.1,
  onChange,
  ariaLabel,
  className = "",
}: BarSliderProps) {
  const safeMax = max > 0 ? max : 0;
  const pct = safeMax > 0 ? Math.min(100, Math.max(0, (value / safeMax) * 100)) : 0;

  return (
    <div className={`group relative flex h-4 items-center ${className}`}>
      <div className="absolute inset-x-0 h-1 rounded-full bg-line" />
      <div
        className="absolute left-0 h-1 rounded-full bg-accent"
        style={{ width: `${pct}%` }}
      />
      <span
        className="absolute h-2.5 w-2.5 -translate-x-1/2 rounded-full bg-accent opacity-0 shadow transition-opacity group-hover:opacity-100 group-focus-within:opacity-100"
        style={{ left: `${pct}%` }}
      />
      <input
        type="range"
        min={0}
        max={safeMax}
        step={step}
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
        aria-label={ariaLabel}
        className="absolute inset-0 h-full w-full cursor-pointer opacity-0 focus:outline-none focus-visible:outline-none"
      />
    </div>
  );
}

export type RepeatMode = "off" | "all" | "one";

/** Index to advance to when a track ends, or null when playback should stop. */
export function nextIndex(
  index: number,
  length: number,
  repeat: RepeatMode,
): number | null {
  if (length <= 0) return null;
  if (index + 1 < length) return index + 1;
  return repeat === "all" ? 0 : null;
}

/** Index for the previous button (wrap only when repeating). */
export function prevIndex(
  index: number,
  length: number,
  repeat: RepeatMode,
): number | null {
  if (length <= 0) return null;
  if (index > 0) return index - 1;
  return repeat === "all" ? length - 1 : null;
}

/** Move an item between positions without mutating the input. */
export function moveItem<T>(items: T[], from: number, to: number): T[] {
  if (
    from === to ||
    from < 0 ||
    to < 0 ||
    from >= items.length ||
    to >= items.length
  ) {
    return items;
  }
  const next = [...items];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved);
  return next;
}

/** Remove one entry; returns the input unchanged for an out-of-range index. */
export function removeAt<T>(items: T[], at: number): T[] {
  if (at < 0 || at >= items.length) return items;
  return items.filter((_, index) => index !== at);
}

/** The index that should be selected after removing `at`. */
export function indexAfterRemoval(
  index: number,
  at: number,
  previousLength: number,
  nextLength: number,
): number {
  if (nextLength <= 0) return 0;
  if (at < index) return index - 1;
  if (at === index) return Math.min(index, nextLength - 1);
  return Math.min(index, Math.max(0, previousLength - 1));
}

export function clamp01(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.min(1, Math.max(0, value));
}

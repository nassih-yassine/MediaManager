export type RepeatMode = "off" | "all" | "one";

/** Only audio and video can be handed to the media element. */
export function isPlayable(item: { kind: string }): boolean {
  return item.kind === "music" || item.kind === "video";
}

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

/** Append items, skipping any id already present (and duplicates in the batch). */
export function appendUnique<T extends { id: string }>(queue: T[], items: T[]): T[] {
  const seen = new Set(queue.map((item) => item.id));
  const next = [...queue];
  for (const item of items) {
    if (seen.has(item.id)) continue;
    seen.add(item.id);
    next.push(item);
  }
  return next;
}

/** Insert after the current item, moving an existing copy instead of cloning it. */
export function insertNextUnique<T extends { id: string }>(
  queue: T[],
  index: number,
  item: T,
): { queue: T[]; index: number } {
  const existing = queue.findIndex((entry) => entry.id === item.id);
  if (existing === index) return { queue, index };

  if (existing === -1) {
    const next = [...queue];
    next.splice(index + 1, 0, item);
    return { queue: next, index };
  }

  const without = queue.filter((_, at) => at !== existing);
  const shifted = existing < index ? index - 1 : index;
  const next = [...without];
  next.splice(shifted + 1, 0, item);
  return { queue: next, index: shifted };
}

/** Drop duplicate ids while keeping the first occurrence. */
export function dedupeById<T extends { id: string }>(items: T[]): T[] {
  const seen = new Set<string>();
  return items.filter((item) => {
    if (seen.has(item.id)) return false;
    seen.add(item.id);
    return true;
  });
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

/** Index after removing every occurrence of an id (queues allow duplicates). */
export function indexAfterRemovingAll<T extends { id: string }>(
  items: T[],
  index: number,
  id: string,
): number {
  const remaining = items.filter((item) => item.id !== id).length;
  if (remaining === 0) return 0;
  const removedBefore = items
    .slice(0, index)
    .filter((item) => item.id === id).length;
  return Math.min(index - removedBefore, remaining - 1);
}

export function clamp01(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.min(1, Math.max(0, value));
}

/**
 * Where playback should start for an item with a stored position.
 * Returns null for: no position, a barely-started item, a finished item, or an
 * item whose duration is unknown.
 */
export function resumeTarget(
  positionMs: number | null | undefined,
  durationMs: number | null | undefined,
): number | null {
  if (!positionMs || positionMs < 3_000) return null;
  if (!durationMs || durationMs <= 0) return null;
  if (positionMs >= durationMs - 5_000) return null;
  return positionMs / 1000;
}

import { describe, expect, it } from "vitest";

import {
  appendUnique,
  clamp01,
  dedupeById,
  indexAfterRemoval,
  indexAfterRemovingAll,
  insertNextUnique,
  isPlayable,
  moveItem,
  nextIndex,
  prevIndex,
  removeAt,
  resumeTarget,
} from "./player-utils";

describe("nextIndex", () => {
  it("advances within the queue", () => {
    expect(nextIndex(0, 3, "off")).toBe(1);
    expect(nextIndex(1, 3, "off")).toBe(2);
  });

  it("stops at the end unless repeating all", () => {
    expect(nextIndex(2, 3, "off")).toBeNull();
    expect(nextIndex(2, 3, "one")).toBeNull();
    expect(nextIndex(2, 3, "all")).toBe(0);
  });

  it("handles an empty queue", () => {
    expect(nextIndex(0, 0, "all")).toBeNull();
  });
});

describe("prevIndex", () => {
  it("goes back within the queue", () => {
    expect(prevIndex(2, 3, "off")).toBe(1);
  });

  it("wraps only when repeating all", () => {
    expect(prevIndex(0, 3, "off")).toBeNull();
    expect(prevIndex(0, 3, "all")).toBe(2);
  });
});

describe("moveItem", () => {
  const items = ["a", "b", "c", "d"];

  it("moves forward and backward", () => {
    expect(moveItem(items, 0, 2)).toEqual(["b", "c", "a", "d"]);
    expect(moveItem(items, 3, 1)).toEqual(["a", "d", "b", "c"]);
  });

  it("returns the same array for invalid moves", () => {
    expect(moveItem(items, 1, 1)).toBe(items);
    expect(moveItem(items, -1, 2)).toBe(items);
    expect(moveItem(items, 0, 9)).toBe(items);
  });

  it("does not mutate the input", () => {
    const input = [...items];
    moveItem(input, 0, 3);
    expect(input).toEqual(items);
  });
});

describe("removeAt", () => {
  it("removes one entry", () => {
    expect(removeAt(["a", "b", "c"], 1)).toEqual(["a", "c"]);
  });

  it("ignores out-of-range indexes", () => {
    const items = ["a"];
    expect(removeAt(items, 4)).toBe(items);
  });
});

describe("indexAfterRemoval", () => {
  it("keeps the current track when removing after it", () => {
    expect(indexAfterRemoval(1, 2, 4, 3)).toBe(1);
  });

  it("shifts left when removing before it", () => {
    expect(indexAfterRemoval(2, 0, 4, 3)).toBe(1);
  });

  it("selects the next track when removing the current one", () => {
    expect(indexAfterRemoval(2, 2, 4, 3)).toBe(2);
    expect(indexAfterRemoval(3, 3, 4, 3)).toBe(2);
  });

  it("resets to zero when the queue empties", () => {
    expect(indexAfterRemoval(2, 2, 3, 0)).toBe(0);
  });
});

describe("indexAfterRemovingAll", () => {
  const items = [{ id: "a" }, { id: "b" }, { id: "a" }, { id: "c" }];

  it("accounts for duplicates removed before the current index", () => {
    expect(indexAfterRemovingAll(items, 3, "a")).toBe(1);
  });

  it("keeps the current item when earlier duplicates are removed", () => {
    expect(indexAfterRemovingAll(items, 1, "a")).toBe(0);
  });

  it("clamps to the end when the tail is removed", () => {
    expect(indexAfterRemovingAll([{ id: "a" }, { id: "b" }], 1, "b")).toBe(0);
  });

  it("resets when everything is removed", () => {
    expect(indexAfterRemovingAll([{ id: "a" }, { id: "a" }], 1, "a")).toBe(0);
  });
});

describe("isPlayable", () => {
  it("accepts only audio and video", () => {
    expect(isPlayable({ kind: "music" })).toBe(true);
    expect(isPlayable({ kind: "video" })).toBe(true);
    expect(isPlayable({ kind: "book" })).toBe(false);
    expect(isPlayable({ kind: "picture" })).toBe(false);
  });
});

describe("clamp01", () => {
  it("clamps and guards NaN", () => {
    expect(clamp01(-1)).toBe(0);
    expect(clamp01(2)).toBe(1);
    expect(clamp01(0.4)).toBe(0.4);
    expect(clamp01(Number.NaN)).toBe(0);
  });
});

describe("resumeTarget", () => {
  it("resumes from a stored position", () => {
    expect(resumeTarget(30_000, 600_000)).toBe(30);
  });

  it("ignores a position that barely started", () => {
    expect(resumeTarget(2_000, 600_000)).toBeNull();
    expect(resumeTarget(0, 600_000)).toBeNull();
    expect(resumeTarget(null, 600_000)).toBeNull();
  });

  it("ignores a finished item", () => {
    expect(resumeTarget(598_000, 600_000)).toBeNull();
  });

  it("ignores unknown durations", () => {
    expect(resumeTarget(30_000, null)).toBeNull();
    expect(resumeTarget(30_000, 0)).toBeNull();
  });
});

describe("appendUnique", () => {
  const a = { id: "a" };
  const b = { id: "b" };

  it("skips ids already queued and duplicates in the batch", () => {
    expect(appendUnique([a], [a, b, b])).toEqual([a, b]);
  });

  it("returns an equal queue when everything is already there", () => {
    expect(appendUnique([a, b], [b])).toEqual([a, b]);
  });
});

describe("insertNextUnique", () => {
  const a = { id: "a" };
  const b = { id: "b" };
  const c = { id: "c" };
  const d = { id: "d" };

  it("inserts a new item after the current one", () => {
    expect(insertNextUnique([a, b, c], 1, d)).toEqual({
      queue: [a, b, d, c],
      index: 1,
    });
  });

  it("moves an existing item instead of duplicating it", () => {
    expect(insertNextUnique([a, b, c], 0, c)).toEqual({
      queue: [a, c, b],
      index: 0,
    });
  });

  it("does nothing when the current item is requested", () => {
    const queue = [a, b];
    expect(insertNextUnique(queue, 0, a).queue).toBe(queue);
  });
});

describe("dedupeById", () => {
  it("keeps the first occurrence", () => {
    expect(dedupeById([{ id: "a" }, { id: "b" }, { id: "a" }])).toEqual([
      { id: "a" },
      { id: "b" },
    ]);
  });
});

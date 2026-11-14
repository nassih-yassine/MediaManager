import { describe, expect, it } from "vitest";

import {
  clamp01,
  indexAfterRemoval,
  moveItem,
  nextIndex,
  prevIndex,
  removeAt,
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

describe("clamp01", () => {
  it("clamps and guards NaN", () => {
    expect(clamp01(-1)).toBe(0);
    expect(clamp01(2)).toBe(1);
    expect(clamp01(0.4)).toBe(0.4);
    expect(clamp01(Number.NaN)).toBe(0);
  });
});

import { describe, expect, it } from "vitest";

import { kindLabel } from "./kind";

describe("kindLabel", () => {
  it("shows Audio for the stored music kind", () => {
    expect(kindLabel("music")).toBe("Audio");
  });

  it("keeps the other kinds", () => {
    expect(kindLabel("video")).toBe("Video");
    expect(kindLabel("picture")).toBe("Picture");
    expect(kindLabel("book")).toBe("Book");
  });

  it("falls back to the raw value for unknown kinds", () => {
    expect(kindLabel("podcast")).toBe("podcast");
  });
});

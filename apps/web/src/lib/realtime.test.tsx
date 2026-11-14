import { act, render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { resetRealtime, useRealtime } from "./realtime";

class FakeSource {
  static instances: FakeSource[] = [];
  closed = false;
  onmessage: ((event: MessageEvent) => void) | null = null;
  constructor(public url: string) {
    FakeSource.instances.push(this);
  }
  close() {
    this.closed = true;
  }
}

vi.stubGlobal("EventSource", FakeSource);

function Probe() {
  useRealtime(() => {});
  return null;
}

describe("useRealtime", () => {
  beforeEach(() => {
    resetRealtime();
    FakeSource.instances = [];
  });

  it("shares one stream and closes it on reset", () => {
    render(<Probe />);
    render(<Probe />);
    expect(FakeSource.instances).toHaveLength(1);
    expect(FakeSource.instances[0].url).toBe("/api/events");

    act(() => resetRealtime());
    expect(FakeSource.instances[0].closed).toBe(true);

    // The next mount opens a fresh stream with the new session.
    render(<Probe />);
    expect(FakeSource.instances).toHaveLength(2);
  });
});

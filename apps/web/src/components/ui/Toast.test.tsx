import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ToastProvider, useToast } from "./Toast";

function Trigger() {
  const toast = useToast();
  return (
    <>
      <button type="button" onClick={() => toast("Saved to library", "success")}>
        save
      </button>
      <button type="button" onClick={() => toast("Could not delete", "error")}>
        fail
      </button>
    </>
  );
}

function renderToasts() {
  return render(
    <ToastProvider durationMs={60}>
      <Trigger />
    </ToastProvider>,
  );
}

describe("ToastProvider", () => {
  it("shows a success toast", async () => {
    const user = userEvent.setup();
    renderToasts();

    await user.click(screen.getByRole("button", { name: "save" }));
    expect(screen.getByText("Saved to library")).toBeInTheDocument();
  });

  it("dismisses itself after the duration", async () => {
    const user = userEvent.setup();
    renderToasts();

    await user.click(screen.getByRole("button", { name: "fail" }));
    expect(screen.getByText("Could not delete")).toBeInTheDocument();

    await waitFor(
      () => expect(screen.queryByText("Could not delete")).not.toBeInTheDocument(),
      { timeout: 1500 },
    );
  });

  it("cancels pending dismiss timers on unmount", () => {
    vi.useFakeTimers();
    try {
      const { unmount } = render(
        <ToastProvider durationMs={5000}>
          <Trigger />
        </ToastProvider>,
      );
      fireEvent.click(screen.getByRole("button", { name: "save" }));
      expect(vi.getTimerCount()).toBeGreaterThan(0);
      unmount();
      expect(vi.getTimerCount()).toBe(0);
    } finally {
      vi.useRealTimers();
    }
  });

  it("keeps at most three toasts visible", async () => {
    const user = userEvent.setup();
    renderToasts();

    const button = screen.getByRole("button", { name: "save" });
    await user.click(button);
    await user.click(button);
    await user.click(button);
    await user.click(button);

    expect(screen.getAllByText("Saved to library")).toHaveLength(3);
  });
});

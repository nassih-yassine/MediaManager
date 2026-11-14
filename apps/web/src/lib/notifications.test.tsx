import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { NotificationProvider, useNotifications } from "./notifications";

const mocks = vi.hoisted(() => ({
  api: vi.fn(),
  handler: null as ((event: { type: string }) => void) | null,
}));

vi.mock("@/lib/api", () => ({ api: mocks.api }));
vi.mock("@/lib/realtime", () => ({
  useRealtime: (handler: (event: { type: string }) => void) => {
    mocks.handler = handler;
  },
}));

function Probe() {
  const { unread, items, markAllRead, markRead } = useNotifications();
  return (
    <div>
      <span data-testid="unread">{unread}</span>
      <span data-testid="count">{items.length}</span>
      <button type="button" onClick={() => void markAllRead()}>
        read all
      </button>
      <button type="button" onClick={() => void markRead(items[0]?.id ?? "")}>
        read first
      </button>
    </div>
  );
}

const ITEM = {
  id: "n1",
  kind: "announcement",
  title: "New release",
  body: "Galleries",
  href: "/notifications",
  readAt: null,
  createdAt: 1,
};

describe("NotificationProvider", () => {
  beforeEach(() => {
    mocks.api.mockReset();
    mocks.handler = null;
  });

  it("loads items and reacts to a notification event", async () => {
    mocks.api.mockResolvedValue({ notifications: [ITEM], unread: 1 });
    render(
      <NotificationProvider>
        <Probe />
      </NotificationProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("unread")).toHaveTextContent("1"));
    expect(screen.getByTestId("count")).toHaveTextContent("1");

    await act(async () => {
      mocks.handler?.({ type: "notification.created" });
    });
    await waitFor(() =>
      expect(mocks.api.mock.calls.filter(([path]) => path === "/api/notifications?limit=50")).toHaveLength(2),
    );
  });

  it("does not decrement for an already-read notification", async () => {
    mocks.api.mockResolvedValue({
      notifications: [{ ...ITEM, readAt: 5 }],
      unread: 0,
    });
    render(
      <NotificationProvider>
        <Probe />
      </NotificationProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("unread")).toHaveTextContent("0"));
    await userEvent.click(screen.getByRole("button", { name: "read first" }));
    expect(screen.getByTestId("unread")).toHaveTextContent("0");
  });

  it("clears the badge optimistically on read-all", async () => {
    mocks.api.mockResolvedValue({ notifications: [ITEM], unread: 1 });
    render(
      <NotificationProvider>
        <Probe />
      </NotificationProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("unread")).toHaveTextContent("1"));
    mocks.api.mockResolvedValue({ updated: 1 });
    await userEvent.click(screen.getByRole("button", { name: "read all" }));
    await waitFor(() => expect(screen.getByTestId("unread")).toHaveTextContent("0"));
    expect(mocks.api).toHaveBeenCalledWith("/api/notifications/read-all", {
      method: "POST",
    });
  });
});

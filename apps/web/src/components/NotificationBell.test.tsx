import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { NotificationBell } from "./NotificationBell";

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  markRead: vi.fn(),
  markAllRead: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mocks.push }),
}));

vi.mock("@/lib/notifications", () => ({
  useNotifications: () => ({
    items: [
      {
        id: "n1",
        kind: "report_resolved",
        title: "Resolved: Add playlists",
        body: "Your report was marked resolved.",
        href: "/profile",
        readAt: null,
        createdAt: Date.now(),
      },
    ],
    unread: 1,
    loading: false,
    markRead: mocks.markRead,
    markAllRead: mocks.markAllRead,
  }),
}));

describe("NotificationBell", () => {
  it("shows the unread count and opens the flyout", async () => {
    render(<NotificationBell />);
    const trigger = screen.getByRole("button", { name: /Notifications \(1 unread\)/ });
    expect(trigger).toBeVisible();
    await userEvent.click(trigger);
    expect(screen.getByText("Resolved: Add playlists")).toBeVisible();
  });

  it("marks a notification read and follows its link", async () => {
    render(<NotificationBell />);
    await userEvent.click(screen.getByRole("button", { name: /Notifications/ }));
    await userEvent.click(screen.getByText("Resolved: Add playlists"));
    expect(mocks.markRead).toHaveBeenCalledWith("n1");
    expect(mocks.push).toHaveBeenCalledWith("/profile");
  });
});

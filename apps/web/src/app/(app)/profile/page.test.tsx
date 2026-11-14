import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ToastProvider } from "@/components/ui/Toast";
import ProfilePage from "./page";

const mocks = vi.hoisted(() => ({
  api: vi.fn(),
  refresh: vi.fn(),
  user: { id: "u1", email: "a@example.com", name: "Ann", role: "user" },
}));

vi.mock("@/lib/api", () => ({
  api: mocks.api,
  formatDate: () => "Jan 1, 2026",
}));
vi.mock("@/lib/auth", () => ({
  useAuth: () => ({ user: mocks.user, refresh: mocks.refresh }),
  useOptionalAuth: () => null,
}));

function renderPage() {
  return render(
    <ToastProvider>
      <ProfilePage />
    </ToastProvider>,
  );
}

describe("ProfilePage", () => {
  beforeEach(() => {
    mocks.api.mockReset();
    mocks.refresh.mockReset();
  });

  it("renders the account and empty reports", async () => {
    mocks.api.mockResolvedValue({ reports: [] });
    renderPage();
    expect(screen.getByRole("heading", { name: "Profile" })).toBeVisible();
    expect(screen.getByText(/a@example.com/)).toBeVisible();
    expect(await screen.findByText("Nothing submitted yet.")).toBeVisible();
  });

  it("sends a report and shows it as open", async () => {
    mocks.api.mockImplementation((path: string, init?: RequestInit) => {
      if (path === "/api/reports" && init?.method === "POST") {
        return Promise.resolve({
          report: {
            id: "r1",
            kind: "feature",
            title: "Add tags",
            body: "Tags would help organize things.",
            status: "open",
            createdAt: 1,
          },
        });
      }
      if (path === "/api/reports") return Promise.resolve({ reports: [] });
      return Promise.resolve({});
    });
    renderPage();
    await userEvent.type(screen.getByPlaceholderText("Short title"), "Add tags");
    await userEvent.type(
      screen.getByPlaceholderText("What happened, or what would help?"),
      "Tags would help organize things.",
    );
    await userEvent.click(screen.getByRole("button", { name: "Send to admin" }));
    await waitFor(() =>
      expect(mocks.api).toHaveBeenCalledWith("/api/reports", {
        method: "POST",
        body: JSON.stringify({
          kind: "feature",
          title: "Add tags",
          body: "Tags would help organize things.",
        }),
      }),
    );
    expect(await screen.findByText("Add tags")).toBeVisible();
    expect(screen.getByText("open")).toBeVisible();
  });

  it("saves the display name", async () => {
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/reports") return Promise.resolve({ reports: [] });
      return Promise.resolve({});
    });
    renderPage();
    const input = screen.getByLabelText("Display name");
    await userEvent.clear(input);
    await userEvent.type(input, "Annie");
    await userEvent.click(screen.getByRole("button", { name: "Save name" }));
    await waitFor(() =>
      expect(mocks.api).toHaveBeenCalledWith("/api/profile", {
        method: "PATCH",
        body: JSON.stringify({ name: "Annie" }),
      }),
    );
    expect(mocks.refresh).toHaveBeenCalled();
  });
});

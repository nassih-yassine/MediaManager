import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ToastProvider } from "@/components/ui/Toast";
import AdminPage from "./page";

const mocks = vi.hoisted(() => ({ api: vi.fn() }));

vi.mock("@/lib/api", () => ({
  api: mocks.api,
  formatBytes: () => "1 B",
  formatDate: () => "Jan 1, 2026",
}));
vi.mock("@/lib/auth", () => ({
  useAuth: () => ({
    user: { id: "admin", email: "a@example.com", name: "Admin", role: "admin" },
  }),
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

function renderPage() {
  return render(
    <ToastProvider>
      <AdminPage />
    </ToastProvider>,
  );
}

const REPORT = {
  id: "r1",
  kind: "feature",
  title: "Add tags",
  body: "Tags would help.",
  status: "open",
  createdAt: 1,
  userName: "Ann",
  userEmail: "ann@example.com",
};

describe("AdminPage", () => {
  beforeEach(() => {
    mocks.api.mockReset();
    mocks.api.mockImplementation((path: string, init?: RequestInit) => {
      if (path === "/api/admin/users") {
        return Promise.resolve({
          users: [
            {
              id: "admin",
              email: "a@example.com",
              name: "Admin",
              role: "admin",
              disabled: false,
              items: 0,
              bytes: 0,
              playlists: 0,
              activeJobs: 0,
              createdAt: 1,
            },
          ],
        });
      }
      if (path === "/api/admin/reports") return Promise.resolve({ reports: [REPORT] });
      if (path === "/api/admin/announcements" && init?.method === "POST") {
        return Promise.resolve({ created: 2 });
      }
      return Promise.resolve({});
    });
  });

  it("resolves a report", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "Reports" }));
    expect(await screen.findByText("Add tags")).toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: "Resolve" }));
    await waitFor(() =>
      expect(mocks.api).toHaveBeenCalledWith("/api/admin/reports/r1", {
        method: "PATCH",
        body: JSON.stringify({ status: "resolved" }),
      }),
    );
  });

  it("sends an announcement", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "Announce" }));
    await userEvent.type(screen.getByPlaceholderText("Title"), "New release");
    await userEvent.type(screen.getByPlaceholderText("What's new?"), "Galleries are here.");
    await userEvent.click(screen.getByRole("button", { name: "Send announcement" }));
    await waitFor(() =>
      expect(mocks.api).toHaveBeenCalledWith("/api/admin/announcements", {
        method: "POST",
        body: JSON.stringify({ title: "New release", body: "Galleries are here." }),
      }),
    );
    expect(await screen.findByText("Sent to 2 users")).toBeVisible();
  });
});

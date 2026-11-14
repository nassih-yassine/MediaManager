import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Sidebar } from "./Sidebar";

const mocks = vi.hoisted(() => ({
  api: vi.fn(),
  logout: vi.fn(),
  replace: vi.fn(),
  user: {
    id: "u1",
    email: "a@example.com",
    name: "Ann",
    role: "admin" as "admin" | "user",
  },
}));

vi.mock("@/lib/api", () => ({ api: mocks.api }));
vi.mock("@/components/NotificationBell", () => ({ NotificationBell: () => null }));
vi.mock("@/lib/realtime", () => ({ useRealtime: () => {} }));
vi.mock("@/lib/auth", () => ({
  useAuth: () => ({ user: mocks.user, logout: mocks.logout }),
  useOptionalAuth: () => null,
}));
vi.mock("next/navigation", () => ({
  usePathname: () => "/",
  useRouter: () => ({ replace: mocks.replace }),
}));

describe("Sidebar account menu", () => {
  beforeEach(() => {
    mocks.api.mockReset();
    mocks.api.mockResolvedValue({ playlists: [] });
    mocks.logout.mockReset();
    mocks.user = { id: "u1", email: "a@example.com", name: "Ann", role: "admin" };
  });

  it("shows the admin link only for admins", () => {
    render(<Sidebar />);
    expect(screen.getAllByText("Admin").length).toBeGreaterThan(0);
    mocks.user = { ...mocks.user, role: "user" };
    render(<Sidebar />);
    const adminLinks = screen
      .getAllByRole("link")
      .filter((link) => link.getAttribute("href") === "/admin");
    expect(adminLinks).toHaveLength(1);
  });

});

import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import LoginPage from "./page";

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("@/lib/auth", () => ({
  useAuth: () => ({
    login: mocks.login,
    user: null,
    needsSetup: false,
    loading: false,
  }),
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  useSearchParams: () => new URLSearchParams("next=/galleries"),
}));

describe("LoginPage", () => {
  beforeEach(() => {
    mocks.login.mockReset();
    mocks.replace.mockReset();
  });

  it("signs in and returns to the requested page", async () => {
    mocks.login.mockResolvedValue(undefined);
    render(<LoginPage />);
    await userEvent.type(screen.getByLabelText("Email"), "a@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "secret123");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await waitFor(() =>
      expect(mocks.login).toHaveBeenCalledWith("a@example.com", "secret123"),
    );
    expect(mocks.replace).toHaveBeenCalledWith("/galleries");
  });

  it("shows the API error and stays on the form", async () => {
    mocks.login.mockRejectedValue(new Error("sign in to continue"));
    render(<LoginPage />);
    await userEvent.type(screen.getByLabelText("Email"), "a@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "wrong");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("sign in to continue")).toBeVisible();
    expect(mocks.replace).not.toHaveBeenCalled();
  });
});

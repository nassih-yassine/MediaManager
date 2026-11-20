import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import SetupPage from "./page";

const mocks = vi.hoisted(() => ({
  setup: vi.fn(),
  replace: vi.fn(),
  auth: {
    setup: null as unknown,
    user: null as null | { id: string; name: string },
    needsSetup: true,
    loading: false,
  },
}));

vi.mock("@/lib/auth", () => ({ useAuth: () => mocks.auth }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: mocks.replace }) }));

async function fillForm() {
  await userEvent.type(screen.getByLabelText("Email"), "admin@example.com");
  await userEvent.type(screen.getByLabelText("Display name"), "Ann");
  await userEvent.type(screen.getByLabelText(/Password/), "secret123");
  await userEvent.click(screen.getByRole("button", { name: "Create admin" }));
}

describe("SetupPage", () => {
  beforeEach(() => {
    mocks.setup.mockReset();
    mocks.replace.mockReset();
    mocks.auth.setup = mocks.setup;
    mocks.auth.user = null;
    mocks.auth.needsSetup = true;
    mocks.auth.loading = false;
  });

  it("creates the admin and enters the app", async () => {
    mocks.setup.mockResolvedValue(undefined);
    render(<SetupPage />);

    await fillForm();

    await waitFor(() =>
      expect(mocks.setup).toHaveBeenCalledWith(
        "admin@example.com",
        "Ann",
        "secret123",
      ),
    );
    expect(mocks.replace).toHaveBeenCalledWith("/");
  });

  it("stays on the form and shows the API error", async () => {
    mocks.setup.mockRejectedValue(new Error("setup is already complete"));
    render(<SetupPage />);

    await fillForm();

    expect(await screen.findByText("setup is already complete")).toBeVisible();
    expect(mocks.replace).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Create admin" })).toBeEnabled();
  });

  it("disables the submit button while the request is in flight", async () => {
    mocks.setup.mockReturnValue(new Promise(() => {}));
    render(<SetupPage />);

    await fillForm();

    expect(screen.getByRole("button", { name: "Creating…" })).toBeDisabled();
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it("sends a signed-in user to the library", async () => {
    mocks.auth.user = { id: "u1", name: "Ann" };
    mocks.auth.needsSetup = false;
    render(<SetupPage />);

    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/"));
  });

  it("sends a visitor to login when setup is already done", async () => {
    mocks.auth.needsSetup = false;
    render(<SetupPage />);

    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/login"));
  });

  it("waits for the auth check before redirecting", async () => {
    mocks.auth.loading = true;
    mocks.auth.needsSetup = false;
    const { rerender } = render(<SetupPage />);

    expect(mocks.replace).not.toHaveBeenCalled();

    mocks.auth.loading = false;
    rerender(<SetupPage />);
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/login"));
  });
});

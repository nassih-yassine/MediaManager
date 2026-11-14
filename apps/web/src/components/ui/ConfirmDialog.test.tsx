import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ConfirmDialog } from "./ConfirmDialog";

function setup(overrides: Partial<React.ComponentProps<typeof ConfirmDialog>> = {}) {
  const onConfirm = vi.fn();
  const onCancel = vi.fn();
  render(
    <ConfirmDialog
      open
      title="Delete this item?"
      description="The file on disk will be removed."
      onConfirm={onConfirm}
      onCancel={onCancel}
      {...overrides}
    />,
  );
  return { onConfirm, onCancel };
}

describe("ConfirmDialog", () => {
  it("renders nothing while closed", () => {
    const { container } = render(
      <ConfirmDialog
        open={false}
        title="Hidden"
        description="Hidden"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("is a labelled dialog and confirms", async () => {
    const { onConfirm } = setup();
    const user = userEvent.setup();

    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(dialog).toHaveAccessibleName("Delete this item?");

    await user.click(screen.getByRole("button", { name: "Delete" }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("puts focus on the confirm action", async () => {
    setup();
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Delete" })).toHaveFocus(),
    );
  });

  it("cancels with Escape", async () => {
    const { onCancel } = setup();
    const user = userEvent.setup();
    await user.keyboard("{Escape}");
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("disables confirmation while busy", () => {
    setup({ busy: true });
    expect(screen.getByRole("button", { name: "Working…" })).toBeDisabled();
  });
});

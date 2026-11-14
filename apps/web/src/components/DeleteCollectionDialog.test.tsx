import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { DeleteCollectionDialog } from "./DeleteCollectionDialog";

function setup(overrides: Partial<React.ComponentProps<typeof DeleteCollectionDialog>> = {}) {
  const onConfirm = vi.fn();
  const onCancel = vi.fn();
  render(
    <DeleteCollectionDialog
      open
      name="Trips"
      itemCount={4}
      busy={false}
      onConfirm={onConfirm}
      onCancel={onCancel}
      {...overrides}
    />,
  );
  return { onConfirm, onCancel };
}

describe("DeleteCollectionDialog", () => {
  it("renders nothing while closed", () => {
    const { container } = render(
      <DeleteCollectionDialog
        open={false}
        name="Trips"
        itemCount={1}
        busy={false}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("defaults to keeping the pictures", async () => {
    const { onConfirm } = setup();
    expect(screen.getByText("4 pictures. Choose what happens to them.")).toBeVisible();
    expect(screen.getByRole("button", { name: /Keep the pictures/ })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await userEvent.click(screen.getByRole("button", { name: "Delete gallery" }));
    expect(onConfirm).toHaveBeenCalledWith("move");
  });

  it("confirms deleting the pictures when chosen", async () => {
    const { onConfirm } = setup({ itemCount: 1 });
    await userEvent.click(
      screen.getByRole("button", { name: /Delete the pictures too/ }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Delete gallery and pictures" }),
    );
    expect(onConfirm).toHaveBeenCalledWith("delete_items");
  });

  it("cancels with Escape", async () => {
    const { onCancel } = setup();
    await userEvent.keyboard("{Escape}");
    expect(onCancel).toHaveBeenCalled();
  });
});

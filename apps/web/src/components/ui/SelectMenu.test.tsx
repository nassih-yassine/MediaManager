import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { SelectMenu } from "./SelectMenu";

const OPTIONS = [
  { value: "newest", label: "Newest" },
  { value: "oldest", label: "Oldest" },
  { value: "title", label: "Title" },
];

describe("SelectMenu", () => {
  it("shows the selected label and keeps the menu closed", () => {
    render(
      <SelectMenu
        value="oldest"
        options={OPTIONS}
        onChange={() => {}}
        ariaLabel="Sort"
      />,
    );

    const trigger = screen.getByRole("button", { name: "Sort" });
    expect(trigger).toHaveTextContent("Oldest");
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("falls back to the first option when the value is unknown", () => {
    render(
      <SelectMenu value="nope" options={OPTIONS} onChange={() => {}} ariaLabel="Sort" />,
    );
    expect(screen.getByRole("button", { name: "Sort" })).toHaveTextContent("Newest");
  });

  it("opens a portaled listbox and marks the current option", async () => {
    const user = userEvent.setup();
    render(
      <SelectMenu value="title" options={OPTIONS} onChange={() => {}} ariaLabel="Sort" />,
    );

    await user.click(screen.getByRole("button", { name: "Sort" }));

    const listbox = screen.getByRole("listbox", { name: "Sort" });
    expect(listbox.parentElement).toBe(document.body);
    expect(within(listbox).getAllByRole("option")).toHaveLength(3);
    expect(screen.getByRole("option", { name: "Title" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.getByRole("option", { name: "Newest" })).toHaveAttribute(
      "aria-selected",
      "false",
    );
  });

  it("reports the chosen value and closes", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <SelectMenu value="newest" options={OPTIONS} onChange={onChange} ariaLabel="Sort" />,
    );

    await user.click(screen.getByRole("button", { name: "Sort" }));
    await user.click(screen.getByRole("option", { name: "Oldest" }));

    expect(onChange).toHaveBeenCalledWith("oldest");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("closes on Escape", async () => {
    const user = userEvent.setup();
    render(
      <SelectMenu value="newest" options={OPTIONS} onChange={() => {}} ariaLabel="Sort" />,
    );

    await user.click(screen.getByRole("button", { name: "Sort" }));
    await user.keyboard("{Escape}");

    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("closes when the user clicks outside the trigger and panel", async () => {
    const user = userEvent.setup();
    render(
      <div>
        <span data-testid="outside">elsewhere</span>
        <SelectMenu value="newest" options={OPTIONS} onChange={() => {}} ariaLabel="Sort" />
      </div>,
    );

    await user.click(screen.getByRole("button", { name: "Sort" }));
    expect(screen.getByRole("listbox")).toBeInTheDocument();

    await user.click(screen.getByTestId("outside"));
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("toggles closed when the trigger is clicked twice", async () => {
    const user = userEvent.setup();
    render(
      <SelectMenu value="newest" options={OPTIONS} onChange={() => {}} ariaLabel="Sort" />,
    );

    const trigger = screen.getByRole("button", { name: "Sort" });
    await user.click(trigger);
    expect(trigger).toHaveAttribute("aria-expanded", "true");

    await user.click(trigger);
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });
});

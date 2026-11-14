import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { ThemePicker } from "./ThemePicker";

describe("ThemePicker", () => {
  it("applies and stores a theme", async () => {
    const user = userEvent.setup();
    render(<ThemePicker />);

    await user.click(screen.getByRole("button", { name: "Appearance settings" }));
    await user.click(screen.getByRole("button", { name: "Ocean" }));

    expect(document.documentElement.dataset.theme).toBe("ocean");
    expect(JSON.parse(window.localStorage.getItem("mm.appearance") ?? "{}")).toMatchObject({
      theme: "ocean",
    });
  });

  it("toggles light mode without changing the theme", async () => {
    const user = userEvent.setup();
    render(<ThemePicker />);

    await user.click(screen.getByRole("button", { name: "Appearance settings" }));
    await user.click(screen.getByRole("button", { name: "Moss" }));
    await user.click(screen.getByRole("button", { name: "light" }));

    expect(document.documentElement.dataset.theme).toBe("moss");
    expect(document.documentElement.dataset.mode).toBe("light");
  });

  it("offers all four themes", async () => {
    const user = userEvent.setup();
    render(<ThemePicker />);

    await user.click(screen.getByRole("button", { name: "Appearance settings" }));
    for (const name of ["Slate", "Paper", "Ocean", "Moss"]) {
      expect(screen.getByRole("button", { name })).toBeInTheDocument();
    }
  });
});

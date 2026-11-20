import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { BarSlider } from "./BarSlider";

describe("BarSlider", () => {
  it("exposes the range input with its label and bounds", () => {
    render(
      <BarSlider value={2} max={10} step={0.5} onChange={() => {}} ariaLabel="Volume" />,
    );

    const input = screen.getByRole("slider", { name: "Volume" }) as HTMLInputElement;
    expect(input).toHaveValue("2");
    expect(input).toHaveAttribute("min", "0");
    expect(input).toHaveAttribute("max", "10");
    expect(input).toHaveAttribute("step", "0.5");
  });

  it("reports numeric values on change", async () => {
    const onChange = vi.fn();
    render(<BarSlider value={1} max={4} step={1} onChange={onChange} ariaLabel="Rating" />);

    const input = screen.getByRole("slider", { name: "Rating" });
    fireEvent.change(input, { target: { value: "3" } });
    expect(onChange).toHaveBeenCalledWith(3);
  });

  it("clamps the fill when max is zero or negative", () => {
    const { container, rerender } = render(
      <BarSlider value={5} max={0} onChange={() => {}} ariaLabel="Empty" />,
    );
    const fill = container.querySelector(".bg-accent") as HTMLElement;
    expect(fill.style.width).toBe("0%");
    expect(screen.getByRole("slider")).toHaveAttribute("max", "0");

    rerender(<BarSlider value={5} max={-3} onChange={() => {}} ariaLabel="Empty" />);
    expect(container.querySelector(".bg-accent")).toHaveStyle({ width: "0%" });
  });

  it("never fills past 100% for out-of-range values", () => {
    const { container } = render(
      <BarSlider value={20} max={10} onChange={() => {}} ariaLabel="Over" />,
    );
    expect(container.querySelector(".bg-accent")).toHaveStyle({ width: "100%" });
  });
});

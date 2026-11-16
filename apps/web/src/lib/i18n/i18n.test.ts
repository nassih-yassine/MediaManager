import { describe, expect, it, vi } from "vitest";

import {
  LOCALE_KEY,
  MESSAGES,
  applyLocale,
  currentLocale,
  needsLanguageChoice,
  translate,
} from "@/lib/i18n";

describe("i18n", () => {
  it("covers every English key in Arabic and nothing extra", () => {
    const english = Object.keys(MESSAGES.en).sort();
    const arabic = Object.keys(MESSAGES.ar).sort();
    expect(arabic).toEqual(english);
    for (const value of Object.values(MESSAGES.ar)) {
      expect(value.trim().length).toBeGreaterThan(0);
    }
  });

  it("translates and interpolates placeholders", () => {
    expect(translate("en", "nav.library")).toBe("Library");
    expect(translate("ar", "nav.library")).toBe("المكتبة");
    expect(
      translate("en", "sidebar.firstPlaylist"),
    ).toContain("first playlist");
  });

  it("falls back to English for an unknown locale key", () => {
    // @ts-expect-error deliberately unknown key
    expect(translate("ar", "nope.missing")).toBe("nope.missing");
  });

  it("applies the locale to the document and caches it", () => {
    const events: string[] = [];
    const listener = (event: Event) => {
      events.push((event as CustomEvent<string>).detail);
    };
    window.addEventListener("mm:locale-changed", listener);

    applyLocale("ar");

    expect(document.documentElement.lang).toBe("ar");
    expect(document.documentElement.dir).toBe("rtl");
    expect(localStorage.getItem(LOCALE_KEY)).toBe("ar");
    expect(currentLocale()).toBe("ar");
    expect(needsLanguageChoice()).toBe(false);
    expect(events).toEqual(["ar"]);

    applyLocale("en");
    expect(document.documentElement.dir).toBe("ltr");
    expect(currentLocale()).toBe("en");
    window.removeEventListener("mm:locale-changed", listener);
  });

  it("asks for a language until one is chosen", () => {
    localStorage.removeItem(LOCALE_KEY);
    expect(needsLanguageChoice()).toBe(true);
    applyLocale("en");
    expect(needsLanguageChoice()).toBe(false);
  });
});

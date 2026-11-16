import { ar } from "./messages/ar";
import { en, type MessageKey } from "./messages/en";

export type { MessageKey };

export type Locale = "en" | "ar";

export const LOCALES: Locale[] = ["en", "ar"];

export const DIRECTION: Record<Locale, "ltr" | "rtl"> = {
  en: "ltr",
  ar: "rtl",
};

export const LOCALE_KEY = "mm.locale";

export const MESSAGES: Record<Locale, Record<MessageKey, string>> = { en, ar };

export function isLocale(value: unknown): value is Locale {
  return value === "en" || value === "ar";
}

/** `{name}` placeholders; unknown keys fall back to English, then to the key. */
export function translate(
  locale: Locale,
  key: MessageKey,
  params?: Record<string, string | number>,
): string {
  const template =
    MESSAGES[locale][key] ?? MESSAGES.en[key] ?? (key as string);
  if (!params) return template;
  return template.replace(/\{(\w+)\}/g, (match, name: string) =>
    name in params ? String(params[name]) : match,
  );
}

/** Applies a locale to the document and caches it for the next first paint. */
export function applyLocale(locale: Locale) {
  if (typeof document === "undefined") return;
  const root = document.documentElement;
  root.lang = locale;
  root.dir = DIRECTION[locale];
  try {
    localStorage.setItem(LOCALE_KEY, locale);
  } catch {
    // storage may be unavailable; the page still renders
  }
  window.dispatchEvent(
    new CustomEvent<Locale>("mm:locale-changed", { detail: locale }),
  );
}

export function currentLocale(): Locale {
  if (typeof document === "undefined") return "en";
  if (isLocale(document.documentElement.lang)) {
    return document.documentElement.lang;
  }
  try {
    const saved = localStorage.getItem(LOCALE_KEY);
    if (isLocale(saved)) return saved;
  } catch {
    // ignore
  }
  return "en";
}

/** True until the person has picked a language once on this device. */
export function needsLanguageChoice(): boolean {
  try {
    return !isLocale(localStorage.getItem(LOCALE_KEY));
  } catch {
    return false;
  }
}

"use client";

import { useEffect, useState } from "react";

import {
  type Locale,
  applyLocale,
  currentLocale,
  needsLanguageChoice,
} from "@/lib/i18n";
import { useI18n } from "@/lib/i18n/I18nProvider";

const OPTIONS: Array<{ value: Locale; labelKey: "language.english" | "language.arabic" }> = [
  { value: "en", labelKey: "language.english" },
  { value: "ar", labelKey: "language.arabic" },
];

/**
 * Asked once per device, before anyone signs in. English is preselected; the
 * choice is cached locally and synced to the account on the next profile save.
 */
export function FirstRunLanguage() {
  const { t, locale } = useI18n();
  const [open, setOpen] = useState(false);
  const [picked, setPicked] = useState<Locale>("en");

  useEffect(() => {
    if (needsLanguageChoice()) {
      setPicked(currentLocale());
      setOpen(true);
    }
  }, []);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-4 sm:items-center">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="first-run-language"
        className="w-full max-w-sm rounded-2xl border border-line bg-panel p-5 shadow-2xl"
      >
        <h2 id="first-run-language" className="text-lg font-semibold tracking-tight">
          {t("language.firstRunTitle")}
        </h2>
        <p className="mt-1 text-sm text-muted">{t("language.firstRunBody")}</p>

        <div className="mt-4 space-y-2">
          {OPTIONS.map((option) => {
            const active = picked === option.value;
            return (
              <button
                key={option.value}
                type="button"
                onClick={() => setPicked(option.value)}
                className={`flex w-full items-center justify-between rounded-xl border px-3 py-2.5 text-sm transition-colors ${
                  active
                    ? "border-accent bg-accent/10 text-ink"
                    : "border-line text-muted hover:bg-panel-2 hover:text-ink"
                }`}
              >
                <span className={option.value === "ar" ? "font-sans" : undefined}>
                  {t(option.labelKey)}
                </span>
                <span
                  className={`h-2.5 w-2.5 rounded-full ${
                    active ? "bg-accent" : "bg-line"
                  }`}
                />
              </button>
            );
          })}
        </div>

        <button
          type="button"
          onClick={() => {
            applyLocale(picked);
            setOpen(false);
          }}
          className="mt-4 w-full rounded-xl bg-accent px-3 py-2.5 text-sm font-semibold text-accent-ink"
        >
          {t("language.continue")}
        </button>

        <p className="mt-3 text-center text-[11px] text-muted" dir="ltr">
          {locale === "en" ? "English is the default." : "الإنجليزية هي الافتراضية."}
        </p>
      </div>
    </div>
  );
}

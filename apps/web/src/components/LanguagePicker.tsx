"use client";

import { useAuth } from "@/lib/auth";
import { type Locale, applyLocale } from "@/lib/i18n";
import { useI18n } from "@/lib/i18n/I18nProvider";

const OPTIONS: Array<{ value: Locale; labelKey: "language.english" | "language.arabic" }> = [
  { value: "en", labelKey: "language.english" },
  { value: "ar", labelKey: "language.arabic" },
];

/**
 * Account-level language. Applies immediately and never blocks on the request:
 * a failed save leaves the local choice in place.
 */
export function LanguagePicker() {
  const { t, locale } = useI18n();
  const { savePreferences } = useAuth();

  return (
    <section className="rounded-2xl border border-line bg-panel p-4">
      <h2 className="text-sm font-semibold tracking-tight">{t("language.title")}</h2>
      <div className="mt-3 flex flex-wrap gap-2">
        {OPTIONS.map((option) => {
          const active = locale === option.value;
          return (
            <button
              key={option.value}
              type="button"
              onClick={() => {
                applyLocale(option.value);
                void savePreferences({ language: option.value });
              }}
              className={`rounded-xl border px-3 py-2 text-sm transition-colors ${
                active
                  ? "border-accent bg-accent/10 text-ink"
                  : "border-line text-muted hover:bg-panel-2 hover:text-ink"
              }`}
            >
              {t(option.labelKey)}
            </button>
          );
        })}
      </div>
    </section>
  );
}

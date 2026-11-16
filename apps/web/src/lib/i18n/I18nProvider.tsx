"use client";

import { createContext, useContext, useEffect, useMemo, useState } from "react";

import {
  type Locale,
  type MessageKey,
  DIRECTION,
  currentLocale,
  translate,
} from "@/lib/i18n";

type Value = {
  locale: Locale;
  dir: "ltr" | "rtl";
  t: (key: MessageKey, params?: Record<string, string | number>) => string;
};

/**
 * English without a provider, so component tests keep rendering plain copy.
 */
const I18nContext = createContext<Value>({
  locale: "en",
  dir: "ltr",
  t: (key, params) => translate("en", key, params),
});

export function I18nProvider({ children }: { children: React.ReactNode }) {
  const [locale, setLocale] = useState<Locale>("en");

  useEffect(() => {
    setLocale(currentLocale());
    function onLocale(event: Event) {
      const detail = (event as CustomEvent<Locale>).detail;
      setLocale(detail ?? currentLocale());
    }
    window.addEventListener("mm:locale-changed", onLocale);
    return () => window.removeEventListener("mm:locale-changed", onLocale);
  }, []);

  const value = useMemo<Value>(
    () => ({
      locale,
      dir: DIRECTION[locale],
      t: (key, params) => translate(locale, key, params),
    }),
    [locale],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): Value {
  return useContext(I18nContext);
}

import type { Metadata } from "next";

import { ToastProvider } from "@/components/ui/Toast";
import { AuthProvider } from "@/lib/auth";
import { I18nProvider } from "@/lib/i18n/I18nProvider";

import "./globals.css";

export const metadata: Metadata = {
  title: "MediaManager",
  description: "Local library for downloaded music, video, pictures and books.",
  icons: { icon: "/logo.svg", apple: "/logo.png" },
};

const APPEARANCE_SCRIPT = `(function(){try{var raw=localStorage.getItem('mm.appearance');var s=raw?JSON.parse(raw):null;var d=document.documentElement;var m=(s&&s.mode)?s.mode:(window.matchMedia('(prefers-color-scheme: light)').matches?'light':'dark');d.setAttribute('data-theme',(s&&s.theme)||'slate');d.setAttribute('data-mode',m);var l=localStorage.getItem('mm.locale');if(l==='ar'){d.lang='ar';d.dir='rtl';}else{d.lang='en';d.dir='ltr';}}catch(e){}})();`;

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="bg-canvas text-ink antialiased">
        <script dangerouslySetInnerHTML={{ __html: APPEARANCE_SCRIPT }} />
        <ToastProvider>
          <I18nProvider>
            <AuthProvider>{children}</AuthProvider>
          </I18nProvider>
        </ToastProvider>
      </body>
    </html>
  );
}

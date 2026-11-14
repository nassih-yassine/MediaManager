import type { Metadata } from "next";

import { ToastProvider } from "@/components/ui/Toast";
import { AuthProvider } from "@/lib/auth";

import "./globals.css";

export const metadata: Metadata = {
  title: "MediaManager",
  description: "Local library for downloaded music, video, pictures and books.",
  icons: { icon: "/logo.svg", apple: "/logo.png" },
};

const APPEARANCE_SCRIPT = `(function(){try{var raw=localStorage.getItem('mm.appearance');var s=raw?JSON.parse(raw):null;var d=document.documentElement;var m=(s&&s.mode)?s.mode:(window.matchMedia('(prefers-color-scheme: light)').matches?'light':'dark');d.setAttribute('data-theme',(s&&s.theme)||'slate');d.setAttribute('data-mode',m);}catch(e){}})();`;

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
          <AuthProvider>{children}</AuthProvider>
        </ToastProvider>
      </body>
    </html>
  );
}

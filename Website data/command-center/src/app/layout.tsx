import type { Metadata, Viewport } from 'next';
import { Inter, JetBrains_Mono } from 'next/font/google';
import { OpsProvider } from '@/state/ops-store';
import { AppShell } from '@/components/shell/AppShell';
import './globals.css';

/**
 * Inter for the interface, JetBrains Mono for anything a commander reads as a
 * figure — ids, coordinates, distances, counts. Both are loaded through
 * next/font, so they are self-hosted at build time rather than fetched from a
 * third party at runtime.
 */
const inter = Inter({
  subsets: ['latin'],
  variable: '--font-inter',
  display: 'swap',
});

const jetbrains = JetBrains_Mono({
  subsets: ['latin'],
  variable: '--font-mono-jb',
  weight: ['400', '500', '600', '700'],
  display: 'swap',
});

export const metadata: Metadata = {
  title: 'DisasterMesh — Rescue Command Center',
  description:
    'Turns information collected from infrastructure-independent mesh networks into coordinated rescue operations.',
};

export const viewport: Viewport = {
  themeColor: '#020617',
  width: 'device-width',
  initialScale: 1,
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>): React.JSX.Element {
  return (
    <html lang="en" className={`${inter.variable} ${jetbrains.variable}`}>
      <body>
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-[999] focus:rounded-sm focus:bg-surface-3 focus:px-3 focus:py-2 focus:text-[12px] focus:text-text"
        >
          Skip to main content
        </a>
        <OpsProvider>
          <AppShell>{children}</AppShell>
        </OpsProvider>
      </body>
    </html>
  );
}

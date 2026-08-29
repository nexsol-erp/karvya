import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';

import { getPublicSettings, settingsKeys, type PublicSettings } from '../api/settings';
import { config } from '../config';
import type { ThemeOverrides } from '../theme';

/**
 * The values the storefront renders, resolved at runtime.
 *
 * <p>This exists because Vite inlines `VITE_*` variables at build time. A
 * WhatsApp number set through the admin was therefore ignored in favour of
 * whatever had been compiled into the bundle - the setting saved, the admin
 * screen showed it, and the site kept using a different number. Anything an
 * administrator can change has to be fetched.
 *
 * <p>Build-time values remain as a fallback for cosmetic copy only, so the
 * first paint has something sensible. Contact details have no fallback: an
 * unconfigured channel hides itself rather than offering a number that may
 * belong to somebody else.
 */
export interface SiteSettings {
  storeName: string;
  tagline: string;
  whatsAppNumber: string;
  contactEmail: string;
  businessAddress: string;
  currency: string;
  locale: string;
  checkoutNotice: string;

  heroHeading: string;
  heroSubheading: string;
  storyHeading: string;
  storyBody: string;
  whyHandmadeBody: string;
  materialsBody: string;

  instagram: string;
  facebook: string;
  youtube: string;

  /** Appearance, as the theme needs it. Nulls mean "use the built-in default". */
  appearance: ThemeOverrides;

  /** True only when a usable number is configured; otherwise every link hides. */
  whatsAppEnabled: boolean;
  /** Still loading, so a first paint can avoid flashing a fallback. */
  isLoading: boolean;
}

const DEFAULT_CHECKOUT_NOTICE =
  'Your order will be confirmed by our team. Payment instructions will be shared separately.';

const CONTEXT = createContext<SiteSettings | null>(null);

/** Blank and whitespace-only both mean "not configured". */
function text(value: string | null | undefined, fallback = ''): string {
  return value != null && value.trim() !== '' ? value : fallback;
}

/**
 * A number is usable once it has a country code and a subscriber number.
 * The server already strips separators and any leading 00.
 */
function usable(number: string): boolean {
  return number.replace(/\D/g, '').length >= 8;
}

export function SiteSettingsProvider({ children }: { children: ReactNode }) {
  const { data, isPending } = useQuery({
    queryKey: settingsKeys.public,
    queryFn: getPublicSettings,
    staleTime: 5 * 60_000,
    // an unreachable settings endpoint must not blank the whole storefront;
    // the build-time fallback carries it
    retry: 1,
  });

  const value = useMemo<SiteSettings>(() => {
    const s: Partial<PublicSettings> = data ?? {};
    const whatsAppNumber = text(s.whatsAppNumber);

    return {
      storeName: text(s.storeName, config.storeName),
      tagline: text(s.tagline, config.tagline),
      whatsAppNumber,
      contactEmail: text(s.contactEmail),
      businessAddress: text(s.businessAddress),
      currency: text(s.currency, config.currency),
      locale: text(s.locale, config.locale),
      checkoutNotice: text(s.checkoutNotice, DEFAULT_CHECKOUT_NOTICE),

      heroHeading: text(s.heroHeading, 'Handwoven coir bird houses'),
      heroSubheading: text(
        s.heroSubheading,
        'Each piece is shaped by hand from natural coconut fibre.',
      ),
      storyHeading: text(s.storyHeading, 'Made by hand, one at a time'),
      storyBody: text(s.storyBody),
      whyHandmadeBody: text(s.whyHandmadeBody),
      materialsBody: text(s.materialsBody),

      instagram: text(s.instagram),
      facebook: text(s.facebook),
      youtube: text(s.youtube),

      appearance: {
        colourPrimary: s.colourPrimary ?? null,
        colourSecondary: s.colourSecondary ?? null,
        colourBackground: s.colourBackground ?? null,
        colourSurface: s.colourSurface ?? null,
        colourText: s.colourText ?? null,
        fontHeading: s.fontHeading ?? null,
        fontBody: s.fontBody ?? null,
        cornerRadius: s.cornerRadius == null ? null : Number(s.cornerRadius),
      },

      whatsAppEnabled: usable(whatsAppNumber),
      isLoading: isPending,
    };
  }, [data, isPending]);

  return <CONTEXT.Provider value={value}>{children}</CONTEXT.Provider>;
}

export function useSiteSettings(): SiteSettings {
  const context = useContext(CONTEXT);
  if (!context) {
    throw new Error('useSiteSettings must be used inside a SiteSettingsProvider');
  }
  return context;
}

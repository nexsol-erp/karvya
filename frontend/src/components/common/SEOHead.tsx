import { config } from '../../config';

interface Props {
  title: string;
  description?: string;
  /** Canonical path, e.g. '/shop'. Resolved against the current origin. */
  path?: string;
  /** Storage key of the sharing image, without width or extension. */
  imageKey?: string | null;
  /** Cart, checkout, account and admin pages must stay out of the index. */
  noIndex?: boolean;
  /** Serialised JSON-LD, rendered as-is. */
  structuredData?: object;
}

/**
 * Page metadata.
 *
 * <p>React 19 hoists {@code title}, {@code meta} and {@code link} to the
 * document head on its own, so this renders them inline and needs no helmet
 * library and no side-effecting layout effect.
 */
export function SEOHead({
  title,
  description,
  path,
  imageKey,
  noIndex = false,
  structuredData,
}: Props) {
  const fullTitle = title === config.storeName ? title : `${title} · ${config.storeName}`;
  const origin = typeof window === 'undefined' ? '' : window.location.origin;
  const canonical = path ? `${origin}${path}` : undefined;
  const image = imageKey ? `${origin}/media/${imageKey}-1280.jpg` : undefined;

  return (
    <>
      <title>{fullTitle}</title>
      {description && <meta name="description" content={description} />}
      {canonical && <link rel="canonical" href={canonical} />}
      {noIndex && <meta name="robots" content="noindex, nofollow" />}

      <meta property="og:type" content="website" />
      <meta property="og:title" content={fullTitle} />
      {description && <meta property="og:description" content={description} />}
      {canonical && <meta property="og:url" content={canonical} />}
      {image && <meta property="og:image" content={image} />}
      <meta property="og:site_name" content={config.storeName} />
      <meta name="twitter:card" content={image ? 'summary_large_image' : 'summary'} />

      {structuredData && (
        <script
          type="application/ld+json"
          // JSON.stringify output is inert data, not markup
          dangerouslySetInnerHTML={{ __html: JSON.stringify(structuredData) }}
        />
      )}
    </>
  );
}

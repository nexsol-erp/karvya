import Box from '@mui/material/Box';
import type { SxProps, Theme } from '@mui/material/styles';
import type { ImageRef } from '../../api/types';

/**
 * Widths the image pipeline emits. Kept in step with
 * scripts/optimise-images.mjs - the largest is the native width of the source
 * photographs, since the pipeline clamps rather than upscales.
 */
const WIDTHS = [480, 800, 1280, 1856];

interface Props {
  image: ImageRef | null;
  /** Maps viewport to rendered width so the browser can pick the right file. */
  sizes: string;
  /** Above-the-fold images load eagerly and get fetch priority; the rest are lazy. */
  priority?: boolean;
  /** Intrinsic aspect ratio placeholder, e.g. '1856 / 1958'. */
  aspectRatio?: string;
  sx?: SxProps<Theme>;
}

function srcSet(key: string, extension: string): string {
  return WIDTHS.map((w) => `/media/${key}-${w}.${extension} ${w}w`).join(', ');
}

/** Everything but the JPEG, which is the <img> fallback rather than a source. */
const SOURCE_TYPES: Record<string, string> = {
  avif: 'image/avif',
  webp: 'image/webp',
};

/**
 * Renders one product photograph across the renditions that exist for it.
 *
 * <p>The browser takes the first source type it understands, so ordering is
 * the compression ranking: AVIF, then WebP, then a JPEG that any browser can
 * read.
 *
 * <p>Only the formats the server says were written are offered. A browser
 * chooses a source on its type alone and does not check the file is there, so
 * offering AVIF for a photograph that only has JPEG shows a broken image
 * instead of falling through - and admin uploads are resized in the
 * application, which has JPEG only.
 *
 * <p>Intrinsic width and height are always set - without them the page reflows
 * as each image arrives, which is the single largest contributor to a poor
 * layout-shift score on an image-led catalogue.
 */
export function ProductImage({ image, sizes, priority = false, aspectRatio, sx }: Props) {
  if (!image) {
    return (
      <Box
        sx={{
          aspectRatio: aspectRatio ?? '1 / 1',
          bgcolor: 'action.hover',
          display: 'grid',
          placeItems: 'center',
          color: 'text.disabled',
          fontSize: 14,
          ...sx,
        }}
        role="img"
        aria-label="No photograph available"
      >
        No photograph
      </Box>
    );
  }

  return (
    <Box
      component="picture"
      sx={{ display: 'block', lineHeight: 0, ...sx }}
    >
      {(image.formats ?? ['jpg'])
        .filter((format) => format in SOURCE_TYPES)
        .map((format) => (
          <source
            key={format}
            type={SOURCE_TYPES[format]}
            srcSet={srcSet(image.key, format)}
            sizes={sizes}
          />
        ))}
      <Box
        component="img"
        src={`/media/${image.key}-800.jpg`}
        srcSet={srcSet(image.key, 'jpg')}
        sizes={sizes}
        alt={image.alt}
        width={image.width ?? undefined}
        height={image.height ?? undefined}
        loading={priority ? 'eager' : 'lazy'}
        decoding={priority ? 'sync' : 'async'}
        {...(priority ? { fetchPriority: 'high' as const } : {})}
        sx={{
          display: 'block',
          width: '100%',
          height: '100%',
          objectFit: 'cover',
          aspectRatio: aspectRatio ?? undefined,
        }}
      />
    </Box>
  );
}

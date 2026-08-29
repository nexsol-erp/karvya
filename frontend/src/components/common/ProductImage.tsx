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

/**
 * Renders one product photograph across AVIF, WebP and JPEG.
 *
 * <p>The browser takes the first source type it understands, so ordering is
 * the compression ranking: AVIF, then WebP, then a JPEG that any browser can
 * read. Intrinsic width and height are always set - without them the page
 * reflows as each image arrives, which is the single largest contributor to a
 * poor layout-shift score on an image-led catalogue.
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
      <source type="image/avif" srcSet={srcSet(image.key, 'avif')} sizes={sizes} />
      <source type="image/webp" srcSet={srcSet(image.key, 'webp')} sizes={sizes} />
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

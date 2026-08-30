import { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ThemeProvider, getContrastRatio } from '@mui/material/styles';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Divider from '@mui/material/Divider';
import Skeleton from '@mui/material/Skeleton';
import Slider from '@mui/material/Slider';

import { SEOHead } from '../../components/common/SEOHead';
import { ApiError } from '../../api/client';
import { adminKeys, listSettings, removeLogo, saveSettings, uploadLogo } from '../../api/admin';
import { buildTheme } from '../../theme';
import { FONTS, FONT_NAMES } from '../../theme/fonts';
import { settingsKeys } from '../../api/settings';

/** The settings this screen owns. Everything else stays on the Settings page. */
const KEYS = {
  primary: 'theme.colour_primary',
  secondary: 'theme.colour_secondary',
  background: 'theme.colour_background',
  surface: 'theme.colour_surface',
  text: 'theme.colour_text',
  heading: 'theme.font_heading',
  body: 'theme.font_body',
  radius: 'theme.corner_radius',
} as const;

const COLOUR_FIELDS = [
  { key: KEYS.primary, label: 'Accent', hint: 'Buttons and links' },
  { key: KEYS.secondary, label: 'Secondary', hint: 'Supporting accent' },
  { key: KEYS.background, label: 'Page background', hint: 'Behind everything' },
  { key: KEYS.surface, label: 'Cards', hint: 'Panels on the background' },
  { key: KEYS.text, label: 'Text', hint: 'Body copy' },
] as const;

/** WCAG AA for body text. Below this, a shop becomes tiring and then unusable. */
const AA = 4.5;
/** WCAG AA for large text, which is all headings and buttons are. */
const AA_LARGE = 3;

function ratio(a: string, b: string): number {
  try {
    return getContrastRatio(a, b);
  } catch {
    // a half-typed hex is not an error worth showing; the field flags it
    return 0;
  }
}

const isHex = (value: string) => /^#[0-9a-fA-F]{6}$/.test(value);

/** A contrast reading, stated plainly rather than as a pass/fail badge. */
function Contrast({ label, value, floor }: { label: string; value: number; floor: number }) {
  const ok = value >= floor;
  return (
    <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 1 }}>
      <Typography variant="body2">{label}</Typography>
      <Chip
        size="small"
        label={`${value.toFixed(1)}:1`}
        color={ok ? 'success' : 'error'}
        variant={ok ? 'outlined' : 'filled'}
      />
    </Stack>
  );
}

/**
 * Colours, typefaces and corner rounding for the storefront.
 *
 * <p>Separate from the Settings page because a hex code in a text box tells an
 * owner nothing about whether the result will be readable. The preview and the
 * contrast readings are the point of the screen: the shop is judged on whether
 * customers can read it, and that is not obvious from the values alone.
 *
 * <p>Poor contrast is reported, not forbidden. It is their shop, and there are
 * legitimate reasons to sit below the threshold on a decorative element - but
 * nobody should reach it without being told.
 */
export function AdminAppearance() {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);

  const settings = useQuery({ queryKey: adminKeys.settings, queryFn: listSettings });

  const logoInput = useRef<HTMLInputElement>(null);
  const [logoBusy, setLogoBusy] = useState(false);
  // bumped on every change so the browser reloads a key it has already cached
  const [logoVersion, setLogoVersion] = useState(0);

  const logoKey =
    settings.data?.find((s) => s.key === 'store.logo_key')?.value ?? '';

  function afterLogoChange() {
    setLogoVersion((v) => v + 1);
    queryClient.invalidateQueries({ queryKey: adminKeys.settings });
    // the storefront reads the same key for its header, footer and tab icon
    queryClient.invalidateQueries({ queryKey: settingsKeys.public });
  }

  async function handleLogo(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // cleared at once, so choosing the same file again still fires a change
    event.target.value = '';
    if (!file) return;

    setMessage(null);
    setLogoBusy(true);
    try {
      await uploadLogo(file);
      afterLogoChange();
      setMessage({ tone: 'success', text: 'Logo updated.' });
    } catch (err) {
      setMessage({
        tone: 'error',
        text: err instanceof ApiError ? err.message : 'That logo could not be uploaded.',
      });
    } finally {
      setLogoBusy(false);
    }
  }

  const dropLogo = useMutation({
    mutationFn: removeLogo,
    onSuccess: () => {
      afterLogoChange();
      setMessage({ tone: 'success', text: 'Logo removed. The shop name is used instead.' });
    },
    onError: () => setMessage({ tone: 'error', text: 'That could not be removed.' }),
  });

  useEffect(() => {
    if (!settings.data) return;
    const next: Record<string, string> = {};
    for (const setting of settings.data) {
      if (setting.key.startsWith('theme.')) next[setting.key] = setting.value ?? '';
    }
    setDraft(next);
  }, [settings.data]);

  const save = useMutation({
    mutationFn: () => saveSettings(draft),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminKeys.settings });
      // the storefront reads the same values from the public endpoint
      queryClient.invalidateQueries({ queryKey: settingsKeys.public });
      setMessage({ tone: 'success', text: 'Appearance saved. The shop uses it straight away.' });
    },
    onError: (err) =>
      setMessage({
        tone: 'error',
        text: err instanceof ApiError ? err.message : 'That could not be saved.',
      }),
  });

  const set = (key: string, value: string) => setDraft((prev) => ({ ...prev, [key]: value }));

  const preview = useMemo(
    () =>
      buildTheme({
        colourPrimary: draft[KEYS.primary],
        colourSecondary: draft[KEYS.secondary],
        colourBackground: draft[KEYS.background],
        colourSurface: draft[KEYS.surface],
        colourText: draft[KEYS.text],
        fontHeading: draft[KEYS.heading],
        fontBody: draft[KEYS.body],
        cornerRadius: draft[KEYS.radius] ? Number(draft[KEYS.radius]) : null,
      }),
    [draft],
  );

  if (settings.isPending) {
    return (
      <Box>
        <Skeleton height={44} width={240} />
        <Skeleton variant="rounded" height={420} sx={{ mt: 2 }} />
      </Box>
    );
  }

  if (settings.isError) {
    return <Alert severity="error">Settings could not be loaded. Please refresh.</Alert>;
  }

  const background = draft[KEYS.background] || '#F0E7D8';
  const surface = draft[KEYS.surface] || '#FBF7F0';
  const text = draft[KEYS.text] || '#33322E';
  const primary = draft[KEYS.primary] || '#A33B2E';

  const readings = [
    { label: 'Text on the page background', value: ratio(text, background), floor: AA },
    { label: 'Text on cards', value: ratio(text, surface), floor: AA },
    { label: 'Button label on the accent', value: ratio(primary, '#FFFFFF'), floor: AA_LARGE },
    { label: 'Accent on the page background', value: ratio(primary, background), floor: AA_LARGE },
  ];
  const failing = readings.filter((r) => r.value < r.floor);
  const malformed = COLOUR_FIELDS.filter(
    (field) => draft[field.key] && !isHex(draft[field.key]),
  );

  return (
    <Box>
      <SEOHead title="Appearance" path="/admin/appearance" noIndex />

      <Typography variant="h1" sx={{ fontSize: '1.9rem', mb: 0.5 }}>
        Appearance
      </Typography>
      <Typography variant="body2" sx={{ mb: 2.5 }}>
        Colours and typefaces for the shop. The preview updates as you change them.
      </Typography>

      {message && (
        <Alert severity={message.tone} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <Grid container spacing={2.5}>
        {/* ---- the mark ---- */}
        <Grid size={{ xs: 12 }}>
          <Card sx={{ p: 2.5 }}>
            <Typography variant="h6" component="h2" sx={{ fontSize: '1rem' }}>
              Logo
            </Typography>
            <Typography variant="body2" sx={{ mb: 2 }}>
              Shown in the header and the footer, and used as the browser tab icon.
              Without one the shop's name is set as a wordmark.
            </Typography>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2.5} sx={{ alignItems: 'center' }}>
              <Box
                sx={{
                  minWidth: 200, minHeight: 72, px: 2, py: 1.5,
                  border: 1, borderColor: 'divider', borderRadius: 2,
                  display: 'grid', placeItems: 'center',
                  // a transparent mark is invisible against a matching panel,
                  // so it is shown against the page rather than the card
                  bgcolor: 'background.default',
                }}
              >
                {logoKey ? (
                  <Box
                    component="img"
                    src={`/media/${logoKey}.png?v=${logoVersion}`}
                    alt="The current logo"
                    sx={{ maxHeight: 56, maxWidth: 220 }}
                  />
                ) : (
                  <Typography variant="body2" color="text.disabled">
                    No logo — the shop name is used
                  </Typography>
                )}
              </Box>

              <Stack spacing={1}>
                <input
                  ref={logoInput}
                  type="file"
                  accept="image/png,image/jpeg"
                  onChange={handleLogo}
                  hidden
                />
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={() => logoInput.current?.click()}
                    disabled={logoBusy}
                  >
                    {logoBusy ? 'Working…' : logoKey ? 'Replace' : 'Upload a logo'}
                  </Button>
                  {logoKey && (
                    <Button size="small" color="inherit" onClick={() => dropLogo.mutate()}
                            disabled={logoBusy}>
                      Remove
                    </Button>
                  )}
                </Stack>
                <Typography variant="body2">
                  PNG or JPEG, at least 200px a side. A PNG with a transparent
                  background sits best on a coloured header.
                </Typography>
              </Stack>
            </Stack>
          </Card>
        </Grid>

        {/* ---- controls ---- */}
        <Grid size={{ xs: 12, lg: 6 }}>
          <Card sx={{ p: 2.5 }}>
            <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 2 }}>
              Colours
            </Typography>

            <Stack spacing={2}>
              {COLOUR_FIELDS.map((field) => {
                const value = draft[field.key] ?? '';
                const bad = Boolean(value) && !isHex(value);
                return (
                  <Stack key={field.key} direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                    <Box
                      component="input"
                      type="color"
                      aria-label={`${field.label} colour`}
                      value={isHex(value) ? value : '#FFFFFF'}
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                        set(field.key, e.target.value.toUpperCase())
                      }
                      sx={{
                        width: 48, height: 48, flexShrink: 0, p: 0,
                        border: 1, borderColor: 'divider', borderRadius: 1,
                        background: 'none', cursor: 'pointer',
                      }}
                    />
                    <TextField
                      label={field.label}
                      value={value}
                      onChange={(e) => set(field.key, e.target.value.toUpperCase())}
                      error={bad}
                      helperText={bad ? 'Use a colour such as #A33B2E' : field.hint}
                      size="small"
                      fullWidth
                    />
                  </Stack>
                );
              })}
            </Stack>

            <Divider sx={{ my: 2.5 }} />

            <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 2 }}>
              Type
            </Typography>

            <Stack spacing={2}>
              <TextField
                select label="Headings" size="small" fullWidth
                value={draft[KEYS.heading] ?? ''}
                onChange={(e) => set(KEYS.heading, e.target.value)}
              >
                {FONT_NAMES.map((name) => (
                  <MenuItem key={name} value={name} sx={{ fontFamily: FONTS[name].stack }}>
                    {name}
                  </MenuItem>
                ))}
              </TextField>

              <TextField
                select label="Body text" size="small" fullWidth
                value={draft[KEYS.body] ?? ''}
                onChange={(e) => set(KEYS.body, e.target.value)}
                helperText="Used for paragraphs, buttons and form fields"
              >
                {FONT_NAMES.map((name) => (
                  <MenuItem key={name} value={name} sx={{ fontFamily: FONTS[name].stack }}>
                    {name}
                  </MenuItem>
                ))}
              </TextField>

              <Box>
                <Typography variant="body2" sx={{ mb: 0.5 }}>
                  Corner rounding — {draft[KEYS.radius] || '12'}px
                </Typography>
                <Slider
                  value={Number(draft[KEYS.radius] || 12)}
                  onChange={(_, next) => set(KEYS.radius, String(next))}
                  min={0} max={32} step={1}
                  marks={[
                    { value: 0, label: 'Square' },
                    { value: 16, label: '16' },
                    { value: 32, label: 'Round' },
                  ]}
                />
              </Box>
            </Stack>

            <Box sx={{ mt: 3 }}>
              <Button
                variant="contained"
                size="large"
                onClick={() => save.mutate()}
                disabled={save.isPending || malformed.length > 0}
              >
                {save.isPending ? 'Saving…' : 'Save appearance'}
              </Button>
            </Box>
          </Card>
        </Grid>

        {/* ---- readability and preview ---- */}
        <Grid size={{ xs: 12, lg: 6 }}>
          <Stack spacing={2.5}>
            <Card sx={{ p: 2.5 }}>
              <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 1.5 }}>
                Readability
              </Typography>

              <Stack spacing={1}>
                {readings.map((reading) => (
                  <Contrast key={reading.label} {...reading} />
                ))}
              </Stack>

              {failing.length > 0 && (
                <Alert severity="warning" sx={{ mt: 2 }}>
                  <AlertTitle>
                    {failing.length === 1 ? 'One combination is' : `${failing.length} combinations are`}{' '}
                    hard to read
                  </AlertTitle>
                  Contrast below {AA}:1 for body text is difficult for many people in
                  bright light or with weaker eyesight. You can save it anyway — it is
                  your shop — but it is worth a second look.
                </Alert>
              )}
            </Card>

            <Card sx={{ p: 0, overflow: 'hidden' }}>
              <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', p: 2, pb: 1.5 }}>
                Preview
              </Typography>
              <Divider />

              {/* the real theme, so what is shown is what customers get */}
              <ThemeProvider theme={preview}>
                <Box sx={{ bgcolor: 'background.default', p: 2.5 }}>
                  <Typography variant="h1" sx={{ fontSize: '2rem', mb: 0.5 }}>
                    Handwoven coir
                  </Typography>
                  <Typography variant="body2" sx={{ mb: 2 }}>
                    Each piece is shaped by hand from natural coconut fibre.
                  </Typography>

                  <Card sx={{ p: 2 }}>
                    <Typography variant="h6" component="p" sx={{ fontSize: '1rem' }}>
                      Terracotta Roof Coir Nest House
                    </Typography>
                    <Typography variant="body2" sx={{ mb: 1.5 }}>
                      A hanging nest house wound in natural coir fibre.
                    </Typography>
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <Button variant="contained" size="small">Add to cart</Button>
                      <Button variant="outlined" size="small">Details</Button>
                      <Chip size="small" label="In stock" color="success" variant="outlined" />
                    </Stack>
                  </Card>
                </Box>
              </ThemeProvider>
            </Card>
          </Stack>
        </Grid>
      </Grid>
    </Box>
  );
}

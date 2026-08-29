import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Chip from '@mui/material/Chip';
import Skeleton from '@mui/material/Skeleton';
import Divider from '@mui/material/Divider';
import MenuItem from '@mui/material/MenuItem';

import { SEOHead } from '../../components/common/SEOHead';
import { adminKeys, listSettings, saveSettings } from '../../api/admin';
import type { SettingView } from '../../api/admin';
import { ApiError } from '../../api/client';

/**
 * Groups settings by their key prefix, so the form reads as sections rather
 * than one long alphabetical list.
 */
const GROUP_LABELS: Record<string, string> = {
  store: 'Store identity',
  contact: 'Contact details',
  locale: 'Currency and locale',
  delivery: 'Delivery',
  catalogue: 'Catalogue',
  content: 'Homepage copy',
  social: 'Social links',
  policy: 'Policy pages',
};

export function AdminSettings() {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);

  const settings = useQuery({ queryKey: adminKeys.settings, queryFn: listSettings });

  // seed the form once the values arrive, without clobbering unsaved edits
  useEffect(() => {
    if (settings.data && Object.keys(draft).length === 0) {
      setDraft(Object.fromEntries(settings.data.map((s) => [s.key, s.value ?? ''])));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [settings.data]);

  const save = useMutation({
    mutationFn: () => saveSettings(draft),
    onSuccess: (updated) => {
      queryClient.setQueryData(adminKeys.settings, updated);
      setDraft(Object.fromEntries(updated.map((s) => [s.key, s.value ?? ''])));
      setMessage({ tone: 'success', text: 'Settings saved.' });
    },
    onError: (err) =>
      setMessage({
        tone: 'error',
        // the server names the offending setting, so pass it through verbatim
        text: err instanceof ApiError ? err.message : 'Settings could not be saved.',
      }),
  });

  const groups = useMemo(() => {
    const byGroup = new Map<string, SettingView[]>();
    for (const setting of settings.data ?? []) {
      const group = setting.key.split('.')[0];
      byGroup.set(group, [...(byGroup.get(group) ?? []), setting]);
    }
    return [...byGroup.entries()].sort(([a], [b]) =>
      Object.keys(GROUP_LABELS).indexOf(a) - Object.keys(GROUP_LABELS).indexOf(b));
  }, [settings.data]);

  const placeholderCount = (settings.data ?? []).filter((s) => s.placeholder).length;
  // an empty value is a different problem from placeholder copy: it does not
  // look wrong on the page, it just quietly removes whatever it drives
  const unsetCount = (settings.data ?? []).filter((s) => s.unset).length;
  const dirty = (settings.data ?? []).some((s) => (s.value ?? '') !== (draft[s.key] ?? ''));

  if (settings.isPending) {
    return (
      <Box>
        <Skeleton height={44} width={200} />
        <Skeleton variant="rounded" height={400} sx={{ mt: 2 }} />
      </Box>
    );
  }

  return (
    <Box sx={{ maxWidth: 860 }}>
      <SEOHead title="Settings" path="/admin/settings" noIndex />

      <Typography variant="h1" sx={{ fontSize: '1.9rem' }}>Settings</Typography>
      <Typography variant="body2" sx={{ mb: 2.5 }}>
        These drive the storefront. Changes take effect immediately.
      </Typography>

      {message && (
        <Alert severity={message.tone} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      {placeholderCount > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          <AlertTitle>
            {placeholderCount} setting{placeholderCount === 1 ? '' : 's'} still hold placeholder text
          </AlertTitle>
          Seeded copy nobody has reviewed. Customers can see most of it, so
          replace it before the shop goes live.
        </Alert>
      )}

      {unsetCount > 0 && (
        <Alert severity="info" sx={{ mb: 2.5 }}>
          <AlertTitle>
            {unsetCount} setting{unsetCount === 1 ? ' is' : 's are'} empty
          </AlertTitle>
          These do not look wrong on the page — the storefront simply hides what
          they drive. An empty WhatsApp number, for instance, removes the chat
          button everywhere.
        </Alert>
      )}

      <Stack spacing={2.5}>
        {groups.map(([group, items]) => (
          <Card key={group} sx={{ p: 2.5 }}>
            <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 0.5 }}>
              {GROUP_LABELS[group] ?? group}
            </Typography>
            <Divider sx={{ mb: 2 }} />

            <Stack spacing={2.5}>
              {items.map((setting) => {
                const multiline = setting.valueType === 'TEXT' || setting.valueType === 'HTML';
                const isBoolean = setting.valueType === 'BOOLEAN';

                return (
                  <Box key={setting.key}>
                    <Stack
                      direction="row"
                      spacing={1}
                      sx={{ alignItems: 'center', mb: 0.5, flexWrap: 'wrap' }}
                    >
                      <Typography sx={{ fontSize: 13, fontWeight: 600, fontFamily: 'monospace' }}>
                        {setting.key}
                      </Typography>
                      <Chip size="small" label={setting.valueType} variant="outlined"
                            sx={{ height: 18, fontSize: 10 }} />
                      {setting.placeholder && (
                        <Chip size="small" color="warning" label="Placeholder"
                              sx={{ height: 18, fontSize: 10 }} />
                      )}
                      {setting.unset && !setting.placeholder && (
                        <Chip size="small" variant="outlined" label="Empty"
                              sx={{ height: 18, fontSize: 10 }} />
                      )}
                    </Stack>

                    {isBoolean ? (
                      <TextField
                        select fullWidth size="small"
                        value={draft[setting.key] ?? ''}
                        onChange={(e) => setDraft({ ...draft, [setting.key]: e.target.value })}
                        helperText={setting.description}
                      >
                        <MenuItem value="true">Yes</MenuItem>
                        <MenuItem value="false">No</MenuItem>
                      </TextField>
                    ) : (
                      <TextField
                        fullWidth size="small"
                        multiline={multiline}
                        minRows={multiline ? 3 : undefined}
                        value={draft[setting.key] ?? ''}
                        onChange={(e) => setDraft({ ...draft, [setting.key]: e.target.value })}
                        helperText={
                          setting.valueType === 'HTML'
                            ? `${setting.description ?? ''} Basic formatting is kept; scripts are removed.`
                            : setting.description
                        }
                      />
                    )}
                  </Box>
                );
              })}
            </Stack>
          </Card>
        ))}
      </Stack>

      <Box
        sx={{
          position: 'sticky', bottom: 0, mt: 2.5, py: 2,
          bgcolor: 'background.default', borderTop: 1, borderColor: 'divider',
        }}
      >
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <Button
            variant="contained"
            disabled={!dirty || save.isPending}
            onClick={() => save.mutate()}
          >
            {save.isPending ? 'Saving…' : 'Save settings'}
          </Button>
          {dirty && (
            <Typography variant="body2">You have unsaved changes.</Typography>
          )}
        </Stack>
      </Box>
    </Box>
  );
}

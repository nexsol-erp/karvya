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
import { NumberField } from '../../components/common/NumberField';
import { ApiError } from '../../api/client';
import { adminKeys, listSettings, saveSettings, sendTestEmail } from '../../api/admin';
import type { MailTestResult } from '../../api/admin';
import type { SettingView } from '../../api/admin';

/**
 * Groups settings by their key prefix, so the form reads as sections rather
 * than one long alphabetical list.
 */
const GROUP_LABELS: Record<string, string> = {
  mail: 'Email delivery (SMTP)',
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
  const [testResult, setTestResult] = useState<MailTestResult | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Reports a delivery failure rather than throwing: a refused login is an
  // answer, not an error, and the provider's own wording is the useful part.
  const testMail = useMutation({
    mutationFn: sendTestEmail,
    onSuccess: setTestResult,
    onError: () =>
      setTestResult({ sent: false, recipient: '', source: 'the current configuration',
                      error: 'The request itself failed. Check the backend logs.' }),
  });

  const settings = useQuery({ queryKey: adminKeys.settings, queryFn: listSettings });

  // A link to #contact has to wait for the settings to arrive: the element it
  // names does not exist while the form is still a row of skeletons, so the
  // browser's own scroll-to-hash has already given up by the time it appears.
  useEffect(() => {
    if (!settings.data) return;
    const target = window.location.hash.slice(1);
    if (!target) return;
    document.getElementById(target)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [settings.data]);

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
    onError: (err) => {
      // Every rejected value comes back keyed by its setting, so each offending
      // field is marked where it is rather than named in a banner the reader
      // then has to go looking for.
      const fields = err instanceof ApiError ? err.fieldErrors : {};
      setFieldErrors(fields);
      setMessage({
        tone: 'error',
        text:
          Object.keys(fields).length > 0
            ? `Please correct the ${Object.keys(fields).length === 1 ? 'highlighted field' : `${Object.keys(fields).length} highlighted fields`}.`
            : err instanceof ApiError
              ? err.message
              : 'Settings could not be saved.',
      });
    },
  });

  const groups = useMemo(() => {
    const byGroup = new Map<string, SettingView[]>();
    for (const setting of settings.data ?? []) {
      const group = setting.key.split('.')[0];
      // appearance has its own screen, with a preview and contrast readings
      // that a row of hex codes here could not give
      if (group === 'theme') continue;
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
          // id so the help page can link straight to a group rather than
          // to the top of a long form the reader then has to scan
          <Card key={group} id={group} sx={{ p: 2.5, scrollMarginTop: 80 }}>
            <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 0.5 }}>
              {GROUP_LABELS[group] ?? group}
            </Typography>
            <Divider sx={{ mb: 2 }} />

            <Stack spacing={2.5}>
              {items.map((setting) => {
                const multiline = setting.valueType === 'TEXT' || setting.valueType === 'HTML';
                const isBoolean = setting.valueType === 'BOOLEAN';
                const isSecret = setting.valueType === 'SECRET';
                const isNumeric =
                  setting.valueType === 'INTEGER' || setting.valueType === 'DECIMAL';
                const fieldError = fieldErrors[setting.key];

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
                      {isSecret && !setting.unset && (
                        <Chip size="small" color="success" variant="outlined" label="Stored"
                              sx={{ height: 18, fontSize: 10 }} />
                      )}
                    </Stack>

                    {isBoolean ? (
                      <TextField
                        select fullWidth size="small"
                        slotProps={{ htmlInput: { 'aria-label': setting.key } }}
                        value={draft[setting.key] ?? ''}
                        onChange={(e) => setDraft({ ...draft, [setting.key]: e.target.value })}
                        error={Boolean(fieldError)}
                        helperText={fieldError ?? setting.description}
                      >
                        <MenuItem value="true">Yes</MenuItem>
                        <MenuItem value="false">No</MenuItem>
                      </TextField>
                    ) : isNumeric ? (
                      <NumberField
                        fullWidth size="small"
                        slotProps={{ htmlInput: { 'aria-label': setting.key } }}
                        decimal={setting.valueType === 'DECIMAL'}
                        value={draft[setting.key] ?? ''}
                        onChange={(v) => setDraft({ ...draft, [setting.key]: v })}
                        error={Boolean(fieldError)}
                        helperText={fieldError ?? setting.description}
                      />
                    ) : (
                      <TextField
                        fullWidth size="small"
                        slotProps={{ htmlInput: { 'aria-label': setting.key } }}
                        error={Boolean(fieldError)}
                        multiline={multiline}
                        minRows={multiline ? 3 : undefined}
                        // a stored secret is never sent back, so the field is
                        // always empty and empty has to mean "leave it alone"
                        type={isSecret ? 'password' : 'text'}
                        autoComplete={isSecret ? 'new-password' : undefined}
                        placeholder={isSecret && !setting.unset ? 'Stored — leave blank to keep' : undefined}
                        value={draft[setting.key] ?? ''}
                        onChange={(e) => setDraft({ ...draft, [setting.key]: e.target.value })}
                        helperText={
                          fieldError ??
                          (setting.valueType === 'HTML'
                            ? `${setting.description ?? ''} Basic formatting is kept; scripts are removed.`
                            : setting.description)
                        }
                      />
                    )}
                  </Box>
                );
              })}
            </Stack>

            {group === 'mail' && (
              <Box sx={{ mt: 2.5, pt: 2, borderTop: 1, borderColor: 'divider' }}>
                <Button
                  variant="outlined"
                  size="small"
                  onClick={() => testMail.mutate()}
                  disabled={testMail.isPending || dirty}
                >
                  {testMail.isPending ? 'Sending…' : 'Send a test email to myself'}
                </Button>
                <Typography variant="body2" sx={{ mt: 0.75 }}>
                  {dirty
                    ? 'Save your changes first — the test uses what is stored.'
                    : 'Delivery failures are hidden from shoppers by design, so this is the only way to know it works.'}
                </Typography>
                {testResult && (
                  <Alert
                    severity={testResult.sent ? 'success' : 'error'}
                    sx={{ mt: 1.5 }}
                    onClose={() => setTestResult(null)}
                  >
                    {testResult.sent
                      ? `Sent to ${testResult.recipient} using ${testResult.source}.`
                      : `Failed using ${testResult.source}: ${testResult.error}`}
                  </Alert>
                )}
              </Box>
            )}
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

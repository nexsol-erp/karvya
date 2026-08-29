import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Link from '@mui/material/Link';
import Alert from '@mui/material/Alert';
import Skeleton from '@mui/material/Skeleton';
import TablePagination from '@mui/material/TablePagination';
import WhatsAppIcon from '@mui/icons-material/WhatsApp';
import EmailIcon from '@mui/icons-material/EmailOutlined';

import { SEOHead } from '../../components/common/SEOHead';
import { EmptyState } from '../../components/common/EmptyState';
import { adminKeys, listEnquiries, setEnquiryStatus } from '../../api/admin';
import type { EnquiryView } from '../../api/admin';
import { ApiError } from '../../api/client';
import { whatsAppLink } from '../../lib/format';
import { palette } from '../../theme';

const STATUSES: EnquiryView['status'][] = ['NEW', 'IN_PROGRESS', 'RESOLVED'];

const TONE: Record<EnquiryView['status'], string> = {
  NEW: palette.terracotta,
  IN_PROGRESS: palette.coir,
  RESOLVED: palette.forest,
};

export function AdminEnquiries() {
  const [params, setParams] = useSearchParams();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [notes, setNotes] = useState<Record<number, string>>({});

  const status = params.get('status') ?? '';
  const q = params.get('q') ?? '';
  const page = Number(params.get('page') ?? 0);
  const [searchDraft, setSearchDraft] = useState(q);

  const enquiries = useQuery({
    queryKey: adminKeys.enquiries(status, q, page),
    queryFn: () => listEnquiries(status, q, page),
    placeholderData: keepPreviousData,
  });

  const triage = useMutation({
    mutationFn: ({ id, next, note }: { id: number; next: EnquiryView['status']; note?: string }) =>
      setEnquiryStatus(id, next, note),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'enquiries'] });
      queryClient.invalidateQueries({ queryKey: adminKeys.dashboard });
      setError(null);
    },
    onError: (err) =>
      setError(err instanceof ApiError ? err.message : 'That change could not be saved.'),
  });

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== 'page') next.delete('page');
    setParams(next, { replace: true });
  };

  const rows = enquiries.data?.content ?? [];

  return (
    <Box>
      <SEOHead title="Enquiries" path="/admin/enquiries" noIndex />

      <Typography variant="h1" sx={{ fontSize: '1.9rem' }}>Enquiries</Typography>
      <Typography variant="body2" sx={{ mb: 2.5 }}>
        {enquiries.isPending ? 'Loading…' : `${enquiries.data?.totalElements ?? 0} messages`}
      </Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>{error}</Alert>}

      <Card sx={{ p: 2, mb: 2.5 }}>
        <Box component="form" onSubmit={(e) => { e.preventDefault(); setFilter('q', searchDraft.trim()); }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: 'center' }}>
            <TextField
              size="small" label="Search" placeholder="Name, email or subject"
              value={searchDraft} onChange={(e) => setSearchDraft(e.target.value)}
              sx={{ flexGrow: 1, width: { xs: '100%', sm: 'auto' } }}
            />
            <TextField
              select size="small" label="Status" value={status}
              onChange={(e) => setFilter('status', e.target.value)}
              sx={{ minWidth: 170, width: { xs: '100%', sm: 'auto' } }}
            >
              <MenuItem value="">Any status</MenuItem>
              {STATUSES.map((s) => <MenuItem key={s} value={s}>{s.replace('_', ' ')}</MenuItem>)}
            </TextField>
            <Button type="submit" variant="contained" size="small">Find</Button>
          </Stack>
        </Box>
      </Card>

      {enquiries.isPending ? (
        <Stack spacing={2}>
          {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} variant="rounded" height={160} />)}
        </Stack>
      ) : rows.length === 0 ? (
        <Card sx={{ p: 3 }}>
          <EmptyState
            title="No messages"
            description="Enquiries sent through the contact form will appear here."
          />
        </Card>
      ) : (
        <Stack spacing={2}>
          {rows.map((enquiry) => (
            <Card key={enquiry.id} sx={{ p: 2 }}>
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                spacing={1}
                sx={{ justifyContent: 'space-between', alignItems: { sm: 'flex-start' }, mb: 1 }}
              >
                <Box sx={{ minWidth: 0 }}>
                  <Typography sx={{ fontWeight: 700 }}>{enquiry.subject}</Typography>
                  <Typography variant="body2">
                    {enquiry.name} ·{' '}
                    <Link href={`mailto:${enquiry.email}`}>{enquiry.email}</Link>
                    {enquiry.phone && ` · ${enquiry.phone}`}
                  </Typography>
                  <Typography variant="body2" sx={{ fontSize: 12 }}>
                    {new Date(enquiry.createdAt).toLocaleString('en-IN', {
                      day: 'numeric', month: 'short', year: 'numeric',
                      hour: '2-digit', minute: '2-digit',
                    })}
                  </Typography>
                </Box>
                <Chip
                  size="small" label={enquiry.status.replace('_', ' ')}
                  sx={{
                    fontWeight: 600,
                    color: TONE[enquiry.status],
                    border: `1px solid ${TONE[enquiry.status]}`,
                    bgcolor: 'transparent',
                  }}
                />
              </Stack>

              <Box
                sx={{
                  p: 1.5, borderRadius: 1.5, bgcolor: 'background.default',
                  whiteSpace: 'pre-wrap', fontSize: 14, mb: 1.5,
                }}
              >
                {enquiry.message}
              </Box>

              {enquiry.internalNote && (
                <Typography variant="body2" sx={{ fontStyle: 'italic', mb: 1.5 }}>
                  Note: {enquiry.internalNote}
                  {enquiry.handledBy && ` — ${enquiry.handledBy}`}
                </Typography>
              )}

              <Divider sx={{ mb: 1.5 }} />

              <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1, alignItems: 'center' }}>
                <Button
                  size="small" startIcon={<EmailIcon />} component="a"
                  href={`mailto:${enquiry.email}?subject=${encodeURIComponent('Re: ' + enquiry.subject)}`}
                >
                  Reply by email
                </Button>

                {enquiry.phone && (
                  <Button
                    size="small" startIcon={<WhatsAppIcon />} component="a" target="_blank"
                    rel="noopener noreferrer"
                    href={whatsAppLink(enquiry.phone, `Hello ${enquiry.name}, about your message "${enquiry.subject}":`)}
                  >
                    WhatsApp
                  </Button>
                )}

                <Box sx={{ flexGrow: 1 }} />

                <TextField
                  size="small" placeholder="Internal note (optional)"
                  value={notes[enquiry.id] ?? ''}
                  onChange={(e) => setNotes({ ...notes, [enquiry.id]: e.target.value })}
                  sx={{ minWidth: 200 }}
                />
                <TextField
                  select size="small" value="" sx={{ minWidth: 140 }}
                  slotProps={{ select: { displayEmpty: true } }}
                  onChange={(e) =>
                    triage.mutate({
                      id: enquiry.id,
                      next: e.target.value as EnquiryView['status'],
                      note: notes[enquiry.id] || undefined,
                    })}
                >
                  <MenuItem value="" disabled>Mark as</MenuItem>
                  {STATUSES.filter((s) => s !== enquiry.status).map((s) => (
                    <MenuItem key={s} value={s}>{s.replace('_', ' ')}</MenuItem>
                  ))}
                </TextField>
              </Stack>
            </Card>
          ))}

          <TablePagination
            component="div"
            count={enquiries.data?.totalElements ?? 0}
            page={page}
            rowsPerPage={20}
            rowsPerPageOptions={[20]}
            onPageChange={(_, next) => setFilter('page', String(next))}
          />
        </Stack>
      )}
    </Box>
  );
}

import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';

interface Props {
  title: string;
  /** What the reader can do next. Never an apology. */
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
}

/**
 * Shown when a list has nothing in it, or a request came back empty.
 *
 * <p>Always names a next step. An empty state that only says "no results"
 * leaves the reader stuck.
 */
export function EmptyState({ title, description, actionLabel, onAction }: Props) {
  return (
    <Box
      sx={{
        py: { xs: 6, md: 9 },
        px: 3,
        textAlign: 'center',
        border: 1,
        borderColor: 'divider',
        borderRadius: 3,
        bgcolor: 'background.paper',
      }}
    >
      <Stack spacing={1.5} sx={{ alignItems: 'center' }}>
        <Typography variant="h5" component="p">
          {title}
        </Typography>
        {description && (
          <Typography variant="body2" sx={{ maxWidth: 460 }}>
            {description}
          </Typography>
        )}
        {actionLabel && onAction && (
          <Button variant="outlined" onClick={onAction} sx={{ mt: 1 }}>
            {actionLabel}
          </Button>
        )}
      </Stack>
    </Box>
  );
}

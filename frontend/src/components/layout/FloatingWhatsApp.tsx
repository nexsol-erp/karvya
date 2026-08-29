import Fab from '@mui/material/Fab';
import WhatsAppIcon from '@mui/icons-material/WhatsApp';
import Tooltip from '@mui/material/Tooltip';

import { config, whatsAppEnabled } from '../../config';
import { whatsAppLink } from '../../lib/format';

/**
 * Floating WhatsApp action.
 *
 * <p>Positioned clear of the bottom edge so it does not sit under a mobile
 * browser's own chrome or over the thumb zone where primary buttons live. It
 * renders nothing at all when no number is configured, rather than offering a
 * link that goes nowhere.
 */
export function FloatingWhatsApp() {
  if (!whatsAppEnabled()) return null;

  const message = `Hello ${config.storeName}, I would like to know more about your pieces.`;

  return (
    <Tooltip title="Chat on WhatsApp" placement="left">
      <Fab
        component="a"
        href={whatsAppLink(config.whatsAppNumber, message)}
        target="_blank"
        rel="noopener noreferrer"
        aria-label="Chat on WhatsApp"
        sx={{
          position: 'fixed',
          right: { xs: 16, md: 24 },
          bottom: { xs: 20, md: 28 },
          zIndex: (t) => t.zIndex.speedDial,
          bgcolor: '#25D366',
          color: '#fff',
          '&:hover': { bgcolor: '#1FB855' },
        }}
      >
        <WhatsAppIcon />
      </Fab>
    </Tooltip>
  );
}

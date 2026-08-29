import { apiFetch } from './client';

export interface EnquiryInput {
  name: string;
  email: string;
  phone: string;
  subject: string;
  message: string;
  /**
   * Honeypot. A person never fills this in, because they never see it; a bot
   * filling every field does. The server accepts the submission either way and
   * discards it silently when this has anything in it - answering differently
   * would tell a bot exactly what to change.
   */
  website: string;
}

function csrfHeader(): Record<string, string> {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
}

/** Resolves on 202, which the server returns whether or not it kept the message. */
export const submitEnquiry = (input: EnquiryInput): Promise<void> =>
  apiFetch('/enquiries', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...csrfHeader() },
    body: JSON.stringify(input),
  });

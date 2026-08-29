import { apiFetch } from './client';

export interface CurrentUser {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  roles: string[];
  mustChangePassword: boolean;
}

export interface Address {
  id: number;
  label: string | null;
  recipientName: string;
  phone: string;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  postalCode: string;
  isDefault: boolean;
}

export type AddressInput = Omit<Address, 'id' | 'isDefault'> & { makeDefault: boolean };

export const authKeys = {
  me: ['auth', 'me'] as const,
  profile: ['account', 'profile'] as const,
  addresses: ['account', 'addresses'] as const,
};

/**
 * Reads the CSRF token the server sets as a readable cookie and echoes it in
 * the header. The pair only matches for a request originating from our own
 * origin, which is what makes a cross-site POST fail.
 */
function csrfHeader(): Record<string, string> {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
}

function post<T>(path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...csrfHeader() },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

function put<T>(path: string, body: unknown): Promise<T> {
  return apiFetch<T>(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...csrfHeader() },
    body: JSON.stringify(body),
  });
}

function del<T>(path: string): Promise<T> {
  return apiFetch<T>(path, { method: 'DELETE', headers: csrfHeader() });
}

// ---- authentication -------------------------------------------------------

export const register = (input: {
  fullName: string;
  email: string;
  phone?: string;
  password: string;
}) => post<CurrentUser>('/auth/register', input);

export const login = (input: { email: string; password: string }) =>
  post<CurrentUser>('/auth/login', input);

export const logout = () => post<void>('/auth/logout');

export const getCurrentUser = () => apiFetch<CurrentUser>('/auth/me');

export const changePassword = (input: { currentPassword: string; newPassword: string }) =>
  post<void>('/auth/password/change', input);

export const forgotPassword = (email: string) => post<void>('/auth/password/forgot', { email });

export const resetPassword = (input: { token: string; newPassword: string }) =>
  post<void>('/auth/password/reset', input);

// ---- account --------------------------------------------------------------

export interface Profile {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  memberSince: string;
}

export const getProfile = () => apiFetch<Profile>('/account/profile');

export const updateProfile = (input: { fullName: string; phone?: string }) =>
  put<Profile>('/account/profile', input);

export const listAddresses = () => apiFetch<Address[]>('/account/addresses');

export const createAddress = (input: AddressInput) => post<Address>('/account/addresses', input);

export const updateAddress = (id: number, input: AddressInput) =>
  put<Address>(`/account/addresses/${id}`, input);

export const makeAddressDefault = (id: number) =>
  post<Address>(`/account/addresses/${id}/default`);

export const deleteAddress = (id: number) => del<void>(`/account/addresses/${id}`);

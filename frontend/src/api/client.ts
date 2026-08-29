import type { ProblemDetail } from './types';

/**
 * An error carrying the backend's RFC 7807 problem document, so callers can
 * branch on status or surface per-field messages instead of parsing strings.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, problem: ProblemDetail | null, fallback: string) {
    super(problem?.detail ?? fallback);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  get fieldErrors(): Record<string, string> {
    return this.problem?.errors ?? {};
  }
}

const BASE = '/api/v1';

/** Serialises query parameters, dropping anything undefined, null or blank. */
export function toQueryString(params: Record<string, unknown>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    search.set(key, String(value));
  }
  const query = search.toString();
  return query ? `?${query}` : '';
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${BASE}${path}`, {
      // sessions are cookie-based, so credentials must travel on every call
      credentials: 'same-origin',
      headers: { Accept: 'application/json', ...(init?.headers ?? {}) },
      ...init,
    });
  } catch {
    // fetch only rejects on a transport failure, never on an HTTP status
    throw new ApiError(0, null, 'Cannot reach the server. Check your connection and try again.');
  }

  if (!response.ok) {
    let problem: ProblemDetail | null = null;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // a non-JSON error body is not worth reporting to the user verbatim
    }
    throw new ApiError(response.status, problem, `Request failed with status ${response.status}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

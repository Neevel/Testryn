import type { ApiError } from "./types";
import { getDevToken } from "./devToken";

const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "http://localhost:8080";

export class ApiRequestError extends Error {
  constructor(
    message: string,
    public status: number,
    public details?: ApiError,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const devToken = getDevToken();
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...(devToken ? { Authorization: `Bearer ${devToken}` } : {}),
      ...init?.headers,
    },
  });

  if (!response.ok) {
    let details: ApiError | undefined;
    try {
      details = await response.json();
    } catch {
      // response had no JSON body -- fall back to a generic message
    }
    throw new ApiRequestError(details?.message ?? response.statusText, response.status, details);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    return (await response.json()) as T;
  }
  return (await response.text()) as unknown as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body !== undefined ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body: unknown) => request<T>(path, { method: "PUT", body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) => request<T>(path, { method: "PATCH", body: JSON.stringify(body) }),
  del: <T>(path: string) => request<T>(path, { method: "DELETE" }),
  upload: <T>(path: string, file: File) => {
    const formData = new FormData();
    formData.append("file", file);
    return request<T>(path, { method: "POST", body: formData });
  },
};

export function downloadUrl(path: string): string {
  return `${API_BASE}${path}`;
}

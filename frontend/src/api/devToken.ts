// Abschnitt 8 (ADR 0012): a browser SPA cannot securely hold a permanent service
// token -- anything baked into the built bundle or written to localStorage is
// readable by anyone with access to the browser/machine indefinitely. This is a
// deliberately minimal, honest stand-in, not a login system: a human pastes a token
// they already created (via the Service Tokens admin UI or the bootstrap token) into
// this browser tab; it lives only in sessionStorage (cleared when the tab closes,
// never sent anywhere but Testryn's own API, never logged). Real human-user
// authentication (login, sessions) is a deliberately separate, not-yet-built product
// block -- see docs/security.md.

const STORAGE_KEY = "testryn_dev_token";

export function getDevToken(): string | null {
  try {
    return sessionStorage.getItem(STORAGE_KEY);
  } catch {
    // sessionStorage can throw in some locked-down/private-browsing contexts --
    // degrade to "no token" rather than crashing the app.
    return null;
  }
}

export function setDevToken(token: string): void {
  sessionStorage.setItem(STORAGE_KEY, token);
}

export function clearDevToken(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}

# Security

How Testryn's REST API is authenticated, and what is deliberately *not* built yet.
See [ADR 0012](adr/0012-service-token-authentication.md) for the full design
rationale behind every decision summarized here.

## Machine-to-Machine Authentication

Testryn's API is authenticated with **service tokens** -- credentials that belong to
Testryn itself, not to a person. There is no login, no password, no session.

```
CI Publisher / Agent / Forge Backend / CLI tool
   │  Authorization: Bearer testryn_<lookupId>_<secret>
   ▼
Testryn REST API
```

Every request under `/api/**` requires a valid, active token. `GET`/`HEAD` requires
the `testryn:read` scope; `POST`/`PUT`/`PATCH`/`DELETE` requires `testryn:write`;
`/api/v1/service-tokens/**` (token management itself) always requires
`testryn:admin`, regardless of method.

- Missing or invalid (malformed, unknown, revoked, expired) token → `401 Unauthorized`
- Valid token, insufficient scope → `403 Forbidden`
- `/v3/api-docs`, `/swagger-ui/**`, `/swagger-ui.html` stay open without a token, for
  local development convenience (this codebase has no dev/prod profile split to hook
  a stricter rule into -- see ADR 0012 for why that trade-off was made deliberately
  rather than building one just for this).

## Scopes

| Scope            | Grants                                              |
|-------------------|------------------------------------------------------|
| `testryn:read`    | Read access to projects, test cases, requirements, test plans, executions, results, report metadata. |
| `testryn:write`    | Everything `read` grants, plus creating/updating test cases, requirement links, test plans, executions, results (including the bulk result-update API), and report uploads. |
| `testryn:admin`   | Everything `write` grants, plus creating, listing, and revoking service tokens. |

A token typically needs exactly one of these. A CI pipeline publishing results needs
`write`. A read-only reporting integration needs only `read`. Only whoever
administers Testryn's integrations needs `admin`.

## Managing Service Tokens

Via the API (requires an `admin` token):

```
POST   /api/v1/service-tokens              create a token
GET    /api/v1/service-tokens              list tokens (never includes the raw value)
GET    /api/v1/service-tokens/{id}         get one token (never includes the raw value)
POST   /api/v1/service-tokens/{id}/revoke  revoke a token (idempotent)
```

Or via the UI: **Settings → Service Tokens**.

The raw token value is returned **exactly once**, in the response to `POST
/api/v1/service-tokens`. Copy it immediately -- Testryn only ever stores a SHA-256
hash of it afterward and cannot show it to you again. If it's lost, revoke that token
and create a new one.

```json
// POST /api/v1/service-tokens request
{
  "name": "CI Pipeline",
  "description": "Publisher token for nightly regression",
  "scopes": ["testryn:read", "testryn:write"]
}
```

```json
// response -- the only time "token" ever appears anywhere
{
  "id": "…",
  "name": "CI Pipeline",
  "token": "testryn_…",
  "scopes": ["testryn:read", "testryn:write"],
  "createdAt": "…",
  "expiresAt": null
}
```

Every other read of a token (list, get-by-id) returns id/name/description/scopes/
createdAt/lastUsedAt/expiresAt/revokedAt/active -- never the token or its hash.

### Expiration

`expiresAt` is optional. An expired token behaves exactly like an unknown one:
`401 Unauthorized`. There is no automatic rotation -- create a replacement and
revoke the old one before/around its expiry if you need continuity.

### Revocation

Revoking is the only supported way to invalidate a token; there is no hard delete.
The row (name, scopes, createdAt, lastUsedAt, revokedAt) is preserved as an audit
trail. Revoking is idempotent -- revoking an already-revoked token just returns its
current state. The instant a token is revoked, every subsequent request using it
gets `401 Unauthorized` -- there is no caching or delay.

## Bootstrap

The very first token has to come from somewhere before any admin-scoped API call is
possible. Set `TESTRYN_BOOTSTRAP_TOKEN` to a value of your choosing before the
backend's first start with an empty `service_tokens` table:

```bash
TESTRYN_BOOTSTRAP_TOKEN=<a-long-random-value-you-generate-yourself>
```

On startup, if (and only if) no service tokens exist yet, this value becomes an
ordinary, fully-revocable `admin`-scoped service token named "Bootstrap Admin
Token". Use it once to create real, named tokens via `POST /api/v1/service-tokens`,
then revoke it. This is not a standing backdoor: the moment any token exists (from
bootstrap or otherwise), the variable is permanently ignored on every future start,
even if it stays set.

Never commit a real value for `TESTRYN_BOOTSTRAP_TOKEN` anywhere -- set it via your
shell, a local (gitignored) `.env`, or your deployment platform's secret mechanism.
`docker-compose.yml` only ever references the variable name, never a value.

## The CI Publisher

`tools/testryn-publisher` reads `TESTRYN_API_TOKEN` from the environment (never a
CLI argument -- CLI arguments are visible in shell history and process lists) and
sends it as `Authorization: Bearer <token>` on every request. See
[docs/ci-integration.md](ci-integration.md).

## The Frontend (a deliberately honest limitation)

A browser single-page app **cannot** keep a long-lived service token truly secret --
anything shipped in the built JS bundle, or written to `localStorage`, is readable
by anyone with access to the browser or machine indefinitely. Service token
authentication is designed for machine-to-machine clients, not for a human sitting
in a browser.

So, for now: **Settings → Your API Token** lets you paste in a token you already
created (via the Service Tokens UI or the bootstrap token). It is kept only in that
browser tab's `sessionStorage` -- cleared the moment the tab closes, never written
to `localStorage`, never baked into the built bundle, never sent anywhere but
Testryn's own API. This is a minimal, explicitly-labeled development convenience,
not a login system, and it does not pretend otherwise in its own UI copy.

**Real human user authentication (login, sessions, and eventually perhaps SSO) is a
deliberately separate, not-yet-built product block** -- see `BACKLOG.md`. Until it
exists, treat frontend access the same way you'd treat direct API access: anyone
with a valid token can use it.

## Secret Handling

- Raw token values are never persisted anywhere -- only a SHA-256 hash of the
  token's secret half (see ADR 0012 for why a fast hash is the right, not the lazy,
  choice here).
- Raw token values never appear in logs, on any code path (success, malformed
  input, unknown token, revoked, expired, or a database error) -- verified by a
  dedicated test (`SecurityLoggingTest`) that captures all log output around several
  distinct fake token values and asserts none of them ever appear.
- Raw token values never appear in exception messages or stack traces.
- `Authorization` headers are never logged.
- No real token value appears in this file, in OpenAPI examples, in tests, or in
  `docker-compose.yml`.

## What This Is Not

No user accounts, no password login, no browser sessions, no OAuth/OIDC login for
Testryn itself, no SAML, no LDAP, no role-based access control beyond the three
service-token scopes, no audit-log platform, no API rate limiting. See `BACKLOG.md`
for what's next.

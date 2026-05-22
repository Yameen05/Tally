# Security Policy

## Reporting Security Issues

Do not open a public issue for a vulnerability that exposes secrets, account access, financial data, or infrastructure details. Report it privately to the repository owner.

## Secret Handling

- Never commit `.env` or real API keys.
- Use `.env.example` as the public template.
- Generate a fresh `JWT_SECRET` with `openssl rand -base64 48`.
- Generate a fresh `PLAID_ENCRYPTION_KEY` with `openssl rand -base64 32`.
- Rotate any key immediately if it was ever committed, pasted into an issue, or shared in logs.

## Previously Committed Keys

An earlier revision of `backend/src/main/resources/application-local.properties` shipped a real
base64 AES key as the `plaid.encryption-key` fallback. That key is in the public git history and
must be considered compromised — anyone forking or cloning the repo can read it. If you have ever
used that key against real Plaid data, rotate it now:

1. Generate a new key with `openssl rand -base64 32`.
2. Place the new value in your `.env` as `PLAID_ENCRYPTION_KEY` (and your deployment secret store).
3. Re-encrypt any persisted Plaid access tokens, or relink the affected Plaid items.

## Production Hardening

- Use HTTPS only.
- Use managed secret storage instead of checked-in files.
- Restrict `CORS_ORIGINS` to the production frontend domain.
- Use a non-root database user with the minimum permissions needed.
- Keep Plaid and OpenAI keys scoped to the intended environment.

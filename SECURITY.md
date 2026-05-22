# Security Policy

## Reporting Security Issues

Do not open a public issue for a vulnerability that exposes secrets, account access, financial data, or infrastructure details. Report it privately to the repository owner.

## Secret Handling

- Never commit `.env` or real API keys.
- Use `.env.example` as the public template.
- Generate a fresh `JWT_SECRET` with `openssl rand -base64 48`.
- Generate a fresh `PLAID_ENCRYPTION_KEY` with `openssl rand -base64 32`.
- Rotate any key immediately if it was ever committed, pasted into an issue, or shared in logs.

## Production Hardening

- Use HTTPS only.
- Use managed secret storage instead of checked-in files.
- Restrict `CORS_ORIGINS` to the production frontend domain.
- Use a non-root database user with the minimum permissions needed.
- Keep Plaid and OpenAI keys scoped to the intended environment.

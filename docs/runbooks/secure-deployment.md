# Secure production deployment

StackChan Companion must be served through HTTPS only. Direct public HTTP
exposure is unsupported. Keep the host binding on loopback, or connect the
container only to a private proxy network. Terminate TLS at the trusted proxy
and forward requests to the companion service over that protected boundary.

`compose.lan.yaml` is a private LAN HTTP development aid, never a production
ingress option. Never combine it with `compose.production.yaml`; production
deployments must retain the HTTPS-only boundary described here.

Use the production overlay to enable the production profile, Secure cookies,
and framework forwarded-header handling:

```powershell
docker compose -f compose.yaml -f compose.production.yaml config --quiet
docker compose -f compose.yaml -f compose.production.yaml up --build -d
```

Only trust forwarded headers supplied by that proxy. Do not expose the server
directly while `SERVER_FORWARD_HEADERS_STRATEGY=framework` is enabled.
Configure proxy access logs to redact `Authorization` headers and never copy
their values into deployment evidence or support tickets.

After signing in through HTTPS, inspect the administrator-session cookie and
confirm that it has `Secure`, `HttpOnly`, and `SameSite=Lax` attributes. If
any attribute is missing, stop the deployment and correct the proxy or
production overlay before allowing users to sign in.

`COMPANION_ADMIN_INITIAL_PASSWORD` is a bootstrap-only secret. After the
first administrator account has been created, remove it from the production
environment and redeploy. Rotate administrator passwords through the UI
password-rotation flow; do not restore or distribute a bootstrap password.

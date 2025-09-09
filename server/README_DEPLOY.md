Deploy Automation (server)
==========================

Script: deploy.sh

Purpose:
 - Idempotent deployments
 - Conditional npm ci (only when package-lock.json changes)
 - TypeScript build
 - Service restart & health verification
 - Optional rollback on failed health
 - Dry-run mode
 - Reload mode (skip git fetch/pull, rebuild + restart only)

Usage:
  ./deploy.sh           # normal deploy (pull + build + restart + health)
  ./deploy.sh --dry-run # show what would happen
  ./deploy.sh --reload  # rebuild & restart without git pull

Environment Vars:
  DEPLOY_BRANCH        (default: current branch)
  PUBLIC_HEALTH_URL    e.g. https://aparat.feezor.net/lwb-api/health
  PORT                 (injected by systemd normally)
  SMTP_HOST            SMTP server hostname (required for magic link email)
  SMTP_PORT            SMTP server port (default 587; use 465 for SMTPS)
  SMTP_USER            SMTP username
  SMTP_PASS            SMTP password
  SMTP_FROM            From address (defaults to SMTP_USER)
  APP_NAME             Branding in email subject (default: LiveWithoutBelief)
  APP_BASE_URL         Base URL used to build magic link (e.g. https://livewithoutbelief.app)
  MAGIC_JWT_SECRET     Secret for signing magic link session JWT

Service Integration:
  Systemd unit should include:
    ExecReload=/opt/lwb-app/server/deploy.sh --reload

Permissions:
  Ensure script is executable:
    chmod +x server/deploy.sh

Rollback Logic:
  Stores last commit in .deploy_last_commit and previous in .deploy_prev_commit.
  On failed health check it hard resets to previous commit (if available), reinstalls, rebuilds, restarts.

Logs:
  Append-only log at server/deploy.log

Security Notes:
  Avoid placing secrets inside deploy.sh. Use systemd EnvironmentFile for sensitive values.

reCAPTCHA (registration only):
  RECAPTCHA_SECRET       Server-side secret to verify registration tokens.
  RECAPTCHA_DEV_ALLOW    If set to '1', bypasses reCAPTCHA (DEV ONLY). Never enable in production.

Limitations / Next Steps:
  - No migration framework integrated yet
  - No artifact caching (could add) 
  - Consider adding structured JSON log entries for external ingestion
  - Consider rate limiting / abuse prevention for /v1/auth/magic/request
  - Add production email provider monitoring / bounce handling

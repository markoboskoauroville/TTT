# How to Login to GitHub Copilot Using a Fine-Grained Token

> **Why this document exists**
> GitHub Copilot CLI rejects Classic Personal Access Tokens (`ghp_...`).
> You must use a **Fine-Grained PAT** (`github_pat_...`) or an OAuth browser login.
> This is a hard policy from GitHub — there is no workaround.

---

## The Problem

When you run `copilot` and see this error:

```
Error: Classic Personal Access Tokens (ghp_) are not supported by Copilot.
The GITHUB_TOKEN environment variable contains a classic PAT.
```

It means your stored token is a **Classic PAT**. Classic tokens start with `ghp_` and are the old format. GitHub removed their support from Copilot because they lack the fine-grained permission scopes Copilot needs.

---

## Solution A — Fine-Grained Personal Access Token (Recommended)

### Step 1 · Create the token on GitHub

1. Open: **https://github.com/settings/personal-access-tokens/new**
2. Fill in:
   - **Token name**: e.g. `copilot-termux`
   - **Expiration**: 90 days or No expiration
   - **Resource owner**: your account (`markoboskoauroville`)
3. Under **Account permissions**, find **Copilot** → set to **Read-only**
4. Click **Generate token**
5. **Copy the token immediately** — GitHub shows it only once.
   It will look like: `github_pat_11ABCDE...`

### Step 2 · Replace the old token in `gh` CLI

Open Termux and run:

```bash
# Remove the old classic PAT
gh auth logout

# Log in with the new fine-grained PAT
gh auth login --with-token <<< "github_pat_YOUR_NEW_TOKEN_HERE"

# Verify it worked
gh auth status
```

### Step 3 · Launch Copilot

```bash
copilot
```

That is it. No `GITHUB_TOKEN` env var is needed — `gh` handles authentication.

---

## Solution B — OAuth Browser Login (Easiest)

If you do not want to manage tokens manually, `gh` can generate a proper OAuth token for you automatically via the browser:

```bash
gh auth logout
gh auth login --web
```

This opens a browser page, you approve it, and `gh` stores a valid token automatically.

> **Note on Termux**: `--web` opens the URL in your Android browser. After approving, switch back to Termux and press Enter.

---

## Why the `GITHUB_TOKEN` Environment Variable Is Set

If you are running Copilot from inside **Antigravity (AGY)** or another AI tool, the tool may inject `GITHUB_TOKEN` into the environment automatically. This injected token is also a Classic PAT — it comes from the same `~/.config/gh/hosts.yml` file.

**The fix is the same**: replace the token stored in `gh auth` with a fine-grained PAT. Once `hosts.yml` holds the new token, everything that reads from it (including AI tools) will use the correct format.

---

## Token Format Reference

| Format | Type | Copilot Support |
|--------|------|----------------|
| `ghp_...` | Classic Personal Access Token | ❌ Rejected |
| `gho_...` | OAuth App token | ❌ Rejected |
| `github_pat_...` | Fine-Grained PAT | ✅ Works |
| OAuth via `gh auth login --web` | OAuth Device Flow | ✅ Works |

---

## Where Tokens Are Stored

| Location | What it is |
|----------|-----------|
| `~/.config/gh/hosts.yml` | Token used by `gh` CLI and anything that calls `gh auth token` |
| `$GITHUB_TOKEN` env var | Overrides `hosts.yml` if set — check with `echo $GITHUB_TOKEN` |
| `$GH_TOKEN` env var | Same as above, alternative name |

To check which token is active right now:

```bash
gh auth token
```

---

## Quick Reference — Full Reset

```bash
# 1. Clear everything
gh auth logout
unset GITHUB_TOKEN
unset GH_TOKEN

# 2. Log in fresh (choose one)
gh auth login --web                                      # browser OAuth (easiest)
gh auth login --with-token <<< "github_pat_YOUR_TOKEN"  # paste fine-grained PAT

# 3. Confirm
gh auth status

# 4. Run Copilot
copilot
```

---

## Useful Links

- Create Fine-Grained Token: https://github.com/settings/personal-access-tokens/new
- Manage all your tokens: https://github.com/settings/tokens
- GitHub Copilot CLI docs: https://docs.github.com/copilot/how-tos/copilot-cli
- `gh` CLI reference: https://cli.github.com/manual/gh_auth_login

---

*Written for Termux / Android · Account: markoboskoauroville · November 2026*

import { useState } from 'react';
import { Link } from 'react-router-dom';
import { formatDistanceToNow, parseISO, format } from 'date-fns';
import Card from '../Card';
import SourceTag from '../SourceTag';

function PlatformIcon({ name }) {
  if (name === 'github') {
    return (
      <svg className="h-5 w-5 text-ink" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path
          fillRule="evenodd"
          clipRule="evenodd"
          d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"
        />
      </svg>
    );
  }
  return (
    <svg
      className="h-5 w-5 text-primary"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      aria-hidden="true"
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M8 9l-4 3 4 3M16 9l4 3-4 3M13 6l-2 12" />
    </svg>
  );
}

function NoSyncTooltip({ platformName }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="relative inline-block">
      <button
        type="button"
        onClick={() => setOpen((p) => !p)}
        onBlur={() => setOpen(false)}
        aria-label={`Why ${platformName} is not synced`}
        className="inline-flex h-4 w-4 items-center justify-center rounded-full border border-border bg-surface text-[10px] font-bold text-muted hover:text-ink"
      >
        ?
      </button>
      {open && (
        <div
          role="tooltip"
          className="absolute right-0 bottom-full mb-2 w-60 z-30 rounded-xl bg-ink text-white text-xs p-3 shadow-card-hover leading-relaxed"
        >
          {platformName} does not provide an official public sync API. PathPilot never scrapes
          external sites — this row is only a saved link, and your {platformName} practice is logged
          manually below.
        </div>
      )}
    </div>
  );
}

function formatSyncedTime(iso) {
  if (!iso) return 'Never synced yet';
  try {
    return formatDistanceToNow(parseISO(iso), { addSuffix: true });
  } catch {
    return iso;
  }
}

export default function ExternalProfilesCard({
  githubStatus,
  profileLinks,
  repos,
  reposLoading,
  reposError,
  syncing,
  connecting,
  onConnectGitHub,
  onSyncGitHub,
}) {
  const [editingGithub, setEditingGithub] = useState(false);
  const [usernameInput, setUsernameInput] = useState(githubStatus?.username || '');
  const [tokenInput, setTokenInput] = useState('');
  const [formError, setFormError] = useState('');

  const isConnected = Boolean(githubStatus?.connected && githubStatus?.username);
  const leetcodeUrl = profileLinks?.leetcodeUrl;
  const codingbatUrl = profileLinks?.codingbatUrl;

  const handleConnectSubmit = async (e) => {
    e.preventDefault();
    const cleaned = usernameInput.trim();
    if (!cleaned) {
      setFormError('Enter your GitHub username.');
      return;
    }
    setFormError('');
    const ok = await onConnectGitHub({
      username: cleaned,
      personalAccessToken: tokenInput.trim() || null,
    });
    if (ok) {
      setTokenInput('');
      setEditingGithub(false);
    }
  };

  return (
    <Card className="flex flex-col justify-between h-full space-y-5">
      <div className="space-y-4">
        <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
          <h2 className="text-base font-bold text-ink">External Profiles</h2>
          <Link
            to="/app/settings"
            className="text-xs font-semibold text-primary hover:underline"
          >
            Edit links in Settings
          </Link>
        </div>

        {/* GitHub Row */}
        <div className="rounded-xl border border-border bg-canvas/60 p-3.5 space-y-3">
          <div className="flex items-start justify-between gap-2">
            <div className="flex items-center gap-2.5 min-w-0">
              <PlatformIcon name="github" />
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-semibold text-ink">GitHub</span>
                  {isConnected ? (
                    <span className="inline-flex items-center rounded-pill border border-completed/25 bg-completed-soft px-2 py-0.5 text-[11px] font-semibold text-completed">
                      Connected (imported)
                    </span>
                  ) : (
                    <span className="inline-flex items-center rounded-pill border border-border bg-surface px-2 py-0.5 text-[11px] font-semibold text-muted">
                      Not connected
                    </span>
                  )}
                </div>
                {isConnected ? (
                  <p className="text-xs text-muted mt-0.5 truncate">
                    @{githubStatus.username} · Last synced {formatSyncedTime(githubStatus.lastSyncedAtUtc)}
                  </p>
                ) : (
                  <p className="text-xs text-muted mt-0.5">
                    Save a public GitHub username to import up to 30 days of public activity.
                  </p>
                )}
              </div>
            </div>

            {isConnected && githubStatus.profileUrl && (
              <a
                href={githubStatus.profileUrl}
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Open GitHub profile in a new tab"
                className="shrink-0 rounded-lg p-1.5 text-muted hover:text-ink hover:bg-surface"
              >
                ›
              </a>
            )}
          </div>

          {githubStatus?.lastSyncError && (
            <div className="rounded-lg border border-pending/40 bg-pending-soft px-3 py-2 text-xs text-amber-900">
              {githubStatus.lastSyncError}
            </div>
          )}

          <div className="flex flex-wrap items-center gap-2">
            {isConnected && (
              <button
                type="button"
                onClick={onSyncGitHub}
                disabled={syncing}
                className="rounded-pill bg-primary px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
              >
                {syncing ? 'Syncing...' : 'Sync now'}
              </button>
            )}
            <button
              type="button"
              onClick={() => {
                setUsernameInput(githubStatus?.username || '');
                setFormError('');
                setEditingGithub((prev) => !prev);
              }}
              className="rounded-pill border border-border bg-surface px-3 py-1.5 text-xs font-semibold text-body hover:bg-canvas"
            >
              {editingGithub ? 'Cancel' : isConnected ? 'Change account' : 'Connect GitHub'}
            </button>
          </div>

          {editingGithub && (
            <form onSubmit={handleConnectSubmit} className="space-y-2.5 pt-2 border-t border-border/70">
              <div>
                <label htmlFor="gh-username" className="block text-xs font-semibold text-ink mb-1">
                  GitHub Username
                </label>
                <input
                  id="gh-username"
                  type="text"
                  value={usernameInput}
                  onChange={(e) => setUsernameInput(e.target.value)}
                  placeholder="e.g. octocat"
                  className="w-full rounded-xl border border-border bg-surface px-3 py-1.5 text-xs text-ink focus:outline-none focus:border-primary"
                />
              </div>
              <div>
                <label htmlFor="gh-token" className="block text-xs font-semibold text-ink mb-1">
                  Optional Personal Access Token (stored encrypted server-side)
                </label>
                <input
                  id="gh-token"
                  type="password"
                  value={tokenInput}
                  onChange={(e) => setTokenInput(e.target.value)}
                  placeholder="Leave blank for public read-only sync (60 req/hr)"
                  className="w-full rounded-xl border border-border bg-surface px-3 py-1.5 text-xs text-ink focus:outline-none focus:border-primary"
                />
              </div>
              {formError && <p className="text-xs text-skipped">{formError}</p>}
              <button
                type="submit"
                disabled={connecting}
                className="rounded-pill bg-primary px-4 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
              >
                {connecting ? 'Connecting...' : 'Save & Sync'}
              </button>
            </form>
          )}
        </div>

        {/* LeetCode Row */}
        <div className="flex items-center justify-between gap-2 rounded-xl border border-border bg-canvas/40 px-3.5 py-2.5">
          <div className="flex items-center gap-2.5 min-w-0">
            <PlatformIcon name="code" />
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-sm font-semibold text-ink">LeetCode</span>
                <span className="inline-flex items-center rounded-pill border border-border bg-surface px-2 py-0.5 text-[11px] font-semibold text-body">
                  {leetcodeUrl ? 'Profile linked' : 'Not linked'}
                </span>
                <NoSyncTooltip platformName="LeetCode" />
              </div>
              <p className="text-[11px] text-muted truncate">
                {leetcodeUrl || 'Add your profile link in Settings (no scraping)'}
              </p>
            </div>
          </div>
          {leetcodeUrl ? (
            <a
              href={leetcodeUrl}
              target="_blank"
              rel="noopener noreferrer"
              aria-label="Open LeetCode profile in a new tab"
              className="shrink-0 rounded-lg px-2 py-1 text-sm font-semibold text-primary hover:bg-primary-soft"
            >
              ›
            </a>
          ) : (
            <Link to="/app/settings" className="text-xs font-semibold text-primary hover:underline">
              Link
            </Link>
          )}
        </div>

        {/* CodingBat Row */}
        <div className="flex items-center justify-between gap-2 rounded-xl border border-border bg-canvas/40 px-3.5 py-2.5">
          <div className="flex items-center gap-2.5 min-w-0">
            <PlatformIcon name="code" />
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-sm font-semibold text-ink">CodingBat</span>
                <span className="inline-flex items-center rounded-pill border border-border bg-surface px-2 py-0.5 text-[11px] font-semibold text-body">
                  {codingbatUrl ? 'Profile linked' : 'Not linked'}
                </span>
                <NoSyncTooltip platformName="CodingBat" />
              </div>
              <p className="text-[11px] text-muted truncate">
                {codingbatUrl || 'Add your profile link in Settings (no scraping)'}
              </p>
            </div>
          </div>
          {codingbatUrl ? (
            <a
              href={codingbatUrl}
              target="_blank"
              rel="noopener noreferrer"
              aria-label="Open CodingBat profile in a new tab"
              className="shrink-0 rounded-lg px-2 py-1 text-sm font-semibold text-primary hover:bg-primary-soft"
            >
              ›
            </a>
          ) : (
            <Link to="/app/settings" className="text-xs font-semibold text-primary hover:underline">
              Link
            </Link>
          )}
        </div>
      </div>

      {/* Real Repositories List */}
      <div className="pt-4 border-t border-border/70 space-y-2.5">
        <div className="flex items-center justify-between gap-2">
          <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
            Public Repositories
          </h3>
          {isConnected && <SourceTag source="IMPORTED" />}
        </div>

        {!isConnected ? (
          <p className="text-xs text-muted">
            Connect your GitHub username above to view your recently pushed repositories.
          </p>
        ) : reposLoading ? (
          <p className="text-xs text-muted">Loading repositories from GitHub...</p>
        ) : reposError ? (
          <p className="rounded-xl border border-pending/40 bg-pending-soft p-3 text-xs text-amber-900">
            {reposError}
          </p>
        ) : repos.length === 0 ? (
          <p className="text-xs text-muted">No public repositories found for @{githubStatus.username}.</p>
        ) : (
          <ul className="space-y-2 max-h-56 overflow-y-auto pr-1">
            {repos.slice(0, 6).map((repo) => (
              <li
                key={repo.fullName || repo.name}
                className="flex items-center justify-between gap-2 rounded-xl border border-border/80 bg-canvas/40 px-3 py-2 text-xs"
              >
                <div className="min-w-0">
                  <a
                    href={repo.htmlUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="font-semibold text-primary hover:underline truncate block"
                  >
                    {repo.name} ↗
                  </a>
                  <div className="flex flex-wrap items-center gap-2 text-[11px] text-muted mt-0.5">
                    <span>{repo.language || 'Plain text'}</span>
                    {repo.pushedAtUtc && (
                      <span>· Pushed {format(parseISO(repo.pushedAtUtc), 'MMM d, yyyy')}</span>
                    )}
                  </div>
                </div>
                {repo.stars > 0 && (
                  <span className="shrink-0 rounded-pill border border-border bg-surface px-2 py-0.5 text-[11px] font-medium text-body">
                    ★ {repo.stars}
                  </span>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}

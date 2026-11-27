import { useEffect, useMemo, useState } from 'react';
import {
  fetchNotificationPreferences,
  fetchProfileSettings,
  updateNotificationPreferences,
  updateProfileSettings,
} from '../api/client';
import Card from '../components/Card';
import PageHeader from '../components/PageHeader';
import Toast from '../components/Toast';
import { useAuth } from '../context/AuthContext';
import { notifyRemindersChanged } from '../hooks/useReminders';
import { isValidHttpsUrl } from '../utils/urlValidation';

const LEAD_TIME_OPTIONS = [
  { value: 0, label: '0 minutes (at start time)' },
  { value: 1, label: '1 minute before (quick test)' },
  { value: 5, label: '5 minutes before' },
  { value: 10, label: '10 minutes before' },
  { value: 30, label: '30 minutes before' },
];

const SKILL_LEVELS = ['Beginner', 'Working', 'Comfortable'];

const SKILL_LEVEL_BADGE = {
  Beginner: 'bg-slate-100 text-slate-700 border-slate-200',
  Working: 'bg-primary-soft text-primary border-primary/25',
  Comfortable: 'bg-completed-soft text-completed border-completed/25',
};

const COMMON_TIME_ZONES = [
  'Asia/Kolkata',
  'UTC',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Berlin',
  'Asia/Dubai',
  'Asia/Singapore',
  'Asia/Tokyo',
  'Australia/Sydney',
];

function getTimeZoneOptions(currentTz) {
  let supported = COMMON_TIME_ZONES;
  try {
    if (typeof Intl !== 'undefined' && typeof Intl.supportedValuesOf === 'function') {
      const all = Intl.supportedValuesOf('timeZone');
      if (Array.isArray(all) && all.length > 0) {
        supported = all;
      }
    }
  } catch {
    supported = COMMON_TIME_ZONES;
  }
  const merged = new Set([currentTz || 'Asia/Kolkata', ...COMMON_TIME_ZONES, ...supported]);
  return Array.from(merged).filter(Boolean);
}

function getBrowserPermissionState() {
  if (typeof window === 'undefined' || !('Notification' in window)) {
    return 'unsupported';
  }
  return window.Notification.permission;
}

export default function SettingsPage() {
  const { user, refreshUser } = useAuth();
  const [permission, setPermission] = useState(getBrowserPermissionState);
  const [browserEnabled, setBrowserEnabled] = useState(false);
  const [leadMinutes, setLeadMinutes] = useState(10);
  const [quietHoursStart, setQuietHoursStart] = useState('');
  const [quietHoursEnd, setQuietHoursEnd] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  // Profile fields
  const [timeZone, setTimeZone] = useState(user?.timeZone || 'Asia/Kolkata');
  const [targetRoles, setTargetRoles] = useState([]);
  const [roleInput, setRoleInput] = useState('');
  const [skills, setSkills] = useState([]);
  const [skillNameInput, setSkillNameInput] = useState('');
  const [skillLevelInput, setSkillLevelInput] = useState('Working');

  // Profile links
  const [portfolioUrl, setPortfolioUrl] = useState('');
  const [githubInput, setGithubInput] = useState('');
  const [leetcodeUrl, setLeetcodeUrl] = useState('');
  const [codingbatUrl, setCodingbatUrl] = useState('');
  const [linkedinUrl, setLinkedinUrl] = useState('');
  const [savingProfile, setSavingProfile] = useState(false);

  const [toast, setToast] = useState({ message: '', type: 'info' });

  const timeZoneOptions = useMemo(() => getTimeZoneOptions(timeZone), [timeZone]);

  useEffect(() => {
    let active = true;
    Promise.all([
      fetchNotificationPreferences().catch(() => null),
      fetchProfileSettings().catch(() => null),
    ])
      .then(([prefs, profile]) => {
        if (!active) return;
        if (prefs) {
          setBrowserEnabled(Boolean(prefs.browserEnabled));
          setLeadMinutes(prefs.leadMinutes ?? 10);
          setQuietHoursStart(prefs.quietHoursStart ? prefs.quietHoursStart.slice(0, 5) : '');
          setQuietHoursEnd(prefs.quietHoursEnd ? prefs.quietHoursEnd.slice(0, 5) : '');
        }
        if (profile) {
          setTimeZone(profile.timeZone || 'Asia/Kolkata');
          setTargetRoles(profile.targetRoles || []);
          setSkills(profile.skills || []);
          setPortfolioUrl(profile.portfolioUrl || '');
          setGithubInput(profile.githubUrl || profile.githubUsername || '');
          setLeetcodeUrl(profile.leetcodeUrl || '');
          setCodingbatUrl(profile.codingbatUrl || '');
          setLinkedinUrl(profile.linkedinUrl || '');
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const addRole = () => {
    const cleaned = roleInput.trim();
    if (!cleaned) return;
    if (!targetRoles.some((r) => r.toLowerCase() === cleaned.toLowerCase())) {
      setTargetRoles((prev) => [...prev, cleaned]);
    }
    setRoleInput('');
  };

  const removeRole = (role) => {
    setTargetRoles((prev) => prev.filter((r) => r !== role));
  };

  const addSkill = () => {
    const cleaned = skillNameInput.trim();
    if (!cleaned) return;
    setSkills((prev) => {
      const idx = prev.findIndex((s) => s.name.toLowerCase() === cleaned.toLowerCase());
      if (idx >= 0) {
        const copy = [...prev];
        copy[idx] = { name: cleaned, level: skillLevelInput };
        return copy;
      }
      return [...prev, { name: cleaned, level: skillLevelInput }];
    });
    setSkillNameInput('');
  };

  const updateSkillLevel = (name, level) => {
    setSkills((prev) => prev.map((s) => (s.name === name ? { ...s, level } : s)));
  };

  const removeSkill = (name) => {
    setSkills((prev) => prev.filter((s) => s.name !== name));
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    if (savingProfile) return;

    // Validate https URLs client-side before sending
    const urlChecks = [
      { label: 'Portfolio URL', value: portfolioUrl },
      { label: 'LeetCode URL', value: leetcodeUrl },
      { label: 'CodingBat URL', value: codingbatUrl },
      { label: 'LinkedIn URL', value: linkedinUrl },
    ];
    for (const check of urlChecks) {
      if (check.value.trim() && !isValidHttpsUrl(check.value.trim())) {
        setToast({ message: `${check.label} must start with https://`, type: 'error' });
        return;
      }
    }
    if (
      githubInput.trim() &&
      (githubInput.trim().includes('://') || githubInput.trim().toLowerCase().startsWith('http')) &&
      !isValidHttpsUrl(githubInput.trim())
    ) {
      setToast({ message: 'GitHub URL must start with https://', type: 'error' });
      return;
    }

    const nextRoles = [...targetRoles];
    if (roleInput.trim() && !nextRoles.some((r) => r.toLowerCase() === roleInput.trim().toLowerCase())) {
      nextRoles.push(roleInput.trim());
    }
    const nextSkills = [...skills];
    if (skillNameInput.trim()) {
      const idx = nextSkills.findIndex((s) => s.name.toLowerCase() === skillNameInput.trim().toLowerCase());
      if (idx < 0) {
        nextSkills.push({ name: skillNameInput.trim(), level: skillLevelInput });
      }
    }

    setSavingProfile(true);
    try {
      const updated = await updateProfileSettings({
        timeZone,
        targetRoles: nextRoles,
        skills: nextSkills,
        githubInput: githubInput.trim(),
        leetcodeUrl: leetcodeUrl.trim(),
        codingbatUrl: codingbatUrl.trim(),
        portfolioUrl: portfolioUrl.trim(),
        linkedinUrl: linkedinUrl.trim(),
      });

      setTimeZone(updated.timeZone || 'Asia/Kolkata');
      setTargetRoles(updated.targetRoles || []);
      setRoleInput('');
      setSkills(updated.skills || []);
      setSkillNameInput('');
      setGithubInput(updated.githubUrl || updated.githubUsername || '');
      setLeetcodeUrl(updated.leetcodeUrl || '');
      setCodingbatUrl(updated.codingbatUrl || '');
      setPortfolioUrl(updated.portfolioUrl || '');
      setLinkedinUrl(updated.linkedinUrl || '');
      await refreshUser();
      setToast({ message: 'Profile and links saved.', type: 'success' });
    } catch (err) {
      setToast({ message: err?.message || 'Unable to save profile settings.', type: 'error' });
    } finally {
      setSavingProfile(false);
    }
  };

  // Only requests browser permission after an explicit button click (never on page load).
  const handleEnableBrowserNotifications = async () => {
    if (typeof window === 'undefined' || !('Notification' in window)) {
      setPermission('unsupported');
      setToast({
        message: 'This browser does not support desktop notifications. In-app toasts still work.',
        type: 'warning',
      });
      return;
    }

    const result = await window.Notification.requestPermission();
    setPermission(result);

    if (result === 'granted') {
      setBrowserEnabled(true);
      await savePreferences(true, leadMinutes, quietHoursStart, quietHoursEnd);
      setToast({ message: 'Browser notifications enabled while PathPilot is open.', type: 'success' });
    } else if (result === 'denied') {
      setBrowserEnabled(false);
      await savePreferences(false, leadMinutes, quietHoursStart, quietHoursEnd);
      setToast({
        message: 'Permission denied. In-app toast reminders will still appear.',
        type: 'warning',
      });
    }
  };

  const savePreferences = async (nextBrowserEnabled, nextLead, nextQuietStart, nextQuietEnd) => {
    setSaving(true);
    try {
      const updated = await updateNotificationPreferences({
        browserEnabled: nextBrowserEnabled,
        leadMinutes: Number(nextLead),
        quietHoursStart: nextQuietStart || null,
        quietHoursEnd: nextQuietEnd || null,
      });
      setBrowserEnabled(Boolean(updated.browserEnabled));
      setLeadMinutes(updated.leadMinutes ?? 10);
      notifyRemindersChanged();
      return true;
    } catch (err) {
      setToast({ message: err?.message || 'Unable to save notification preferences.', type: 'error' });
      return false;
    } finally {
      setSaving(false);
    }
  };

  const handleSaveSubmit = async (e) => {
    e.preventDefault();
    if (saving) return;
    const ok = await savePreferences(browserEnabled, leadMinutes, quietHoursStart, quietHoursEnd);
    if (ok) {
      setToast({ message: 'Notification preferences saved.', type: 'success' });
    }
  };

  const permissionBadge = {
    granted: { text: 'Granted', classes: 'bg-completed-soft text-completed border-completed/25' },
    denied: { text: 'Denied', classes: 'bg-skipped-soft text-skipped border-skipped/25' },
    default: { text: 'Not asked yet (Default)', classes: 'bg-pending-soft text-amber-800 border-pending/30' },
    unsupported: { text: 'Not supported', classes: 'bg-slate-100 text-muted border-border' },
  }[permission];

  return (
    <div className="space-y-6">
      <PageHeader title="Settings" breadcrumb="Account & Preferences" />

      {/* Profile, Skills, Roles & Links */}
      <form onSubmit={handleSaveProfile} className="space-y-6">
        <Card>
          <div className="pb-3 border-b border-border/70">
            <h2 className="text-base font-bold text-ink">Profile</h2>
            <p className="text-xs text-muted mt-0.5">
              Configure your time zone, target career roles, and skills (used later for job-description matching).
            </p>
          </div>

          <div className="mt-4 space-y-5">
            <dl className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-sm">
              <div>
                <dt className="text-xs font-medium text-muted">Full Name</dt>
                <dd className="mt-1 font-semibold text-ink">{user?.fullName || '—'}</dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-muted">Email</dt>
                <dd className="mt-1 font-semibold text-ink">{user?.email || '—'}</dd>
              </div>
              <div>
                <label htmlFor="profile-tz" className="block text-xs font-semibold text-ink mb-1">
                  Time Zone
                </label>
                <select
                  id="profile-tz"
                  value={timeZone}
                  onChange={(e) => setTimeZone(e.target.value)}
                  className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
                >
                  {timeZoneOptions.map((tz) => (
                    <option key={tz} value={tz}>
                      {tz}
                    </option>
                  ))}
                </select>
              </div>
            </dl>

            {/* Target Roles */}
            <div className="space-y-2">
              <label htmlFor="target-role-input" className="block text-xs font-semibold text-ink">
                Target Roles
              </label>
              <div className="flex gap-2">
                <input
                  id="target-role-input"
                  type="text"
                  value={roleInput}
                  onChange={(e) => setRoleInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      addRole();
                    }
                  }}
                  placeholder="e.g. Junior Java Developer, Full Stack Engineer"
                  className="flex-1 rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
                />
                <button
                  type="button"
                  onClick={addRole}
                  className="rounded-pill border border-border bg-canvas px-4 py-2 text-xs font-semibold text-ink hover:bg-primary-soft hover:text-primary"
                >
                  Add Role
                </button>
              </div>
              {targetRoles.length > 0 && (
                <div className="flex flex-wrap gap-2 pt-1">
                  {targetRoles.map((role) => (
                    <span
                      key={role}
                      className="inline-flex items-center gap-1.5 rounded-pill border border-primary/25 bg-primary-soft px-3 py-1 text-xs font-semibold text-primary"
                    >
                      {role}
                      <button
                        type="button"
                        onClick={() => removeRole(role)}
                        aria-label={`Remove ${role}`}
                        className="hover:text-ink"
                      >
                        ×
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>

            {/* Skills I Know */}
            <div className="space-y-2">
              <label htmlFor="skill-name-input" className="block text-xs font-semibold text-ink">
                Skills I Know (Beginner / Working / Comfortable)
              </label>
              <div className="flex flex-col sm:flex-row gap-2">
                <input
                  id="skill-name-input"
                  type="text"
                  value={skillNameInput}
                  onChange={(e) => setSkillNameInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      addSkill();
                    }
                  }}
                  placeholder="e.g. Spring Boot, React, SQL"
                  className="flex-1 rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
                />
                <select
                  aria-label="Skill level"
                  value={skillLevelInput}
                  onChange={(e) => setSkillLevelInput(e.target.value)}
                  className="rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
                >
                  {SKILL_LEVELS.map((lvl) => (
                    <option key={lvl} value={lvl}>
                      {lvl}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={addSkill}
                  className="rounded-pill border border-border bg-canvas px-4 py-2 text-xs font-semibold text-ink hover:bg-primary-soft hover:text-primary"
                >
                  Add Skill
                </button>
              </div>

              {skills.length > 0 && (
                <div className="flex flex-wrap gap-2 pt-1">
                  {skills.map((skill) => (
                    <span
                      key={skill.name}
                      className={`inline-flex items-center gap-2 rounded-pill border px-3 py-1 text-xs font-semibold ${
                        SKILL_LEVEL_BADGE[skill.level] || SKILL_LEVEL_BADGE.Working
                      }`}
                    >
                      <span>{skill.name}</span>
                      <select
                        aria-label={`Level for ${skill.name}`}
                        value={skill.level}
                        onChange={(e) => updateSkillLevel(skill.name, e.target.value)}
                        className="bg-transparent text-[11px] font-bold focus:outline-none cursor-pointer"
                      >
                        {SKILL_LEVELS.map((lvl) => (
                          <option key={lvl} value={lvl} className="text-ink">
                            {lvl}
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        onClick={() => removeSkill(skill.name)}
                        aria-label={`Remove ${skill.name}`}
                        className="hover:opacity-75"
                      >
                        ×
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>
          </div>
        </Card>

        {/* External Profile Links */}
        <Card>
          <div className="pb-3 border-b border-border/70">
            <h2 className="text-base font-bold text-ink">Developer Profile Links</h2>
            <p className="text-xs text-muted mt-0.5">
              All profile URLs must use <strong>https://</strong>. Only GitHub public activity is synced via the
              official REST API; other links are stored as bookmarks only (never scraped).
            </p>
          </div>

          <div className="mt-4 space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label htmlFor="link-portfolio" className="block text-xs font-semibold text-ink mb-1">
                  Portfolio Website URL
                </label>
                <input
                  id="link-portfolio"
                  type="url"
                  value={portfolioUrl}
                  onChange={(e) => setPortfolioUrl(e.target.value)}
                  placeholder="https://your-portfolio.dev"
                  className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
                />
              </div>

              <div>
                <label htmlFor="link-github" className="block text-xs font-semibold text-ink mb-1">
                  GitHub Profile (username) or Repository URL
                </label>
                <input
                  id="link-github"
                  type="text"
                  value={githubInput}
                  onChange={(e) => setGithubInput(e.target.value)}
                  placeholder="octocat or https://github.com/octocat/repo"
                  className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
                />
              </div>

              <div>
                <label htmlFor="link-leetcode" className="block text-xs font-semibold text-ink mb-1">
                  Coding-Practice Profile URL (e.g. LeetCode)
                </label>
                <input
                  id="link-leetcode"
                  type="url"
                  value={leetcodeUrl}
                  onChange={(e) => setLeetcodeUrl(e.target.value)}
                  placeholder="https://leetcode.com/u/username"
                  className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
                />
              </div>

              <div>
                <label htmlFor="link-linkedin" className="block text-xs font-semibold text-ink mb-1">
                  LinkedIn Profile URL (Optional)
                </label>
                <input
                  id="link-linkedin"
                  type="url"
                  value={linkedinUrl}
                  onChange={(e) => setLinkedinUrl(e.target.value)}
                  placeholder="https://linkedin.com/in/username"
                  className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
                />
              </div>

              <div className="sm:col-span-2">
                <label htmlFor="link-codingbat" className="block text-xs font-semibold text-ink mb-1">
                  CodingBat Profile URL (Optional)
                </label>
                <input
                  id="link-codingbat"
                  type="url"
                  value={codingbatUrl}
                  onChange={(e) => setCodingbatUrl(e.target.value)}
                  placeholder="https://codingbat.com/java"
                  className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
                />
              </div>
            </div>

            <div className="flex justify-end">
              <button
                type="submit"
                disabled={savingProfile}
                className="rounded-pill bg-primary px-5 py-2 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
              >
                {savingProfile ? 'Saving...' : 'Save Profile & Links'}
              </button>
            </div>
          </div>
        </Card>
      </form>

      {/* Notifications Section */}
      <Card>
        <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-border/70">
          <h2 className="text-base font-bold text-ink">Notifications</h2>
          <span
            className={`inline-flex items-center px-2.5 py-0.5 text-xs font-semibold rounded-pill border ${permissionBadge.classes}`}
          >
            Browser Permission: {permissionBadge.text}
          </span>
        </div>

        <div className="mt-4 space-y-5">
          <div className="rounded-xl border border-primary/20 bg-primary-soft p-4 text-xs sm:text-sm text-ink leading-relaxed">
            <p className="font-semibold">Honest reminder scope (Open-tab reminders)</p>
            <p className="mt-1 text-body">
              In-app toast alerts and browser notifications run via local timers while PathPilot is
              open in a browser tab. When you close the tab or close the browser completely, no
              notifications can fire. Background delivery via Web Push &amp; a Service Worker is
              planned for a future version.
            </p>
          </div>

          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl border border-border/80 bg-canvas/60 p-4">
            <div>
              <p className="text-sm font-semibold text-ink">Browser Notifications</p>
              <p className="text-xs text-muted mt-0.5">
                Optional desktop popups when a reminder fires while this tab is open.
              </p>
            </div>
            <button
              type="button"
              onClick={handleEnableBrowserNotifications}
              disabled={permission === 'unsupported'}
              className="self-start sm:self-auto rounded-pill bg-primary px-4 py-2 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-50"
            >
              {permission === 'granted' ? 'Browser Notifications Active ✓' : 'Enable browser notifications'}
            </button>
          </div>

          {permission === 'denied' && (
            <div
              role="status"
              className="rounded-xl border border-pending/30 bg-pending-soft p-4 text-xs text-amber-950 space-y-1.5"
            >
              <p className="font-semibold">Browser notifications are currently blocked</p>
              <p>
                Only in-app toast reminders will appear while PathPilot is open. To re-enable
                browser notifications: click the tune/lock icon in your browser&apos;s address bar →{' '}
                <strong>Site settings</strong> → set <strong>Notifications</strong> to{' '}
                <strong>Allow</strong>, then reload this page.
              </p>
            </div>
          )}

          {loading ? (
            <p className="text-xs text-muted">Loading notification preferences...</p>
          ) : (
            <form onSubmit={handleSaveSubmit} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label htmlFor="pref-lead" className="block text-xs font-semibold text-ink mb-1">
                    Reminder Lead Time
                  </label>
                  <select
                    id="pref-lead"
                    value={leadMinutes}
                    onChange={(e) => setLeadMinutes(Number(e.target.value))}
                    className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
                  >
                    {LEAD_TIME_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label htmlFor="pref-quiet-start" className="block text-xs font-semibold text-ink mb-1">
                    Quiet Hours Start
                  </label>
                  <input
                    id="pref-quiet-start"
                    type="time"
                    value={quietHoursStart}
                    onChange={(e) => setQuietHoursStart(e.target.value)}
                    className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
                  />
                </div>

                <div>
                  <label htmlFor="pref-quiet-end" className="block text-xs font-semibold text-ink mb-1">
                    Quiet Hours End
                  </label>
                  <input
                    id="pref-quiet-end"
                    type="time"
                    value={quietHoursEnd}
                    onChange={(e) => setQuietHoursEnd(e.target.value)}
                    className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
                  />
                </div>
              </div>

              <div className="flex flex-wrap items-center justify-between gap-3 pt-2">
                <label className="inline-flex items-center gap-2 text-xs font-medium text-body cursor-pointer">
                  <input
                    type="checkbox"
                    checked={browserEnabled}
                    disabled={permission !== 'granted'}
                    onChange={(e) => setBrowserEnabled(e.target.checked)}
                    className="h-4 w-4 rounded border-border text-primary"
                  />
                  <span>Show browser popup when permission is granted</span>
                </label>

                <div className="flex items-center gap-2">
                  {(quietHoursStart || quietHoursEnd) && (
                    <button
                      type="button"
                      onClick={() => {
                        setQuietHoursStart('');
                        setQuietHoursEnd('');
                      }}
                      className="rounded-pill border border-border px-3.5 py-2 text-xs font-semibold text-body hover:bg-canvas"
                    >
                      Clear quiet hours
                    </button>
                  )}
                  <button
                    type="submit"
                    disabled={saving}
                    className="rounded-pill bg-primary px-5 py-2 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
                  >
                    {saving ? 'Saving...' : 'Save Preferences'}
                  </button>
                </div>
              </div>
            </form>
          )}
        </div>
      </Card>

      <Toast
        message={toast.message}
        type={toast.type}
        onDismiss={() => setToast({ message: '', type: 'info' })}
      />
    </div>
  );
}

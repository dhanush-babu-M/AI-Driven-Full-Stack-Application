import { useEffect, useState } from 'react';
import {
  fetchNotificationPreferences,
  updateNotificationPreferences,
} from '../api/client';
import Card from '../components/Card';
import PageHeader from '../components/PageHeader';
import Toast from '../components/Toast';
import { useAuth } from '../context/AuthContext';
import { notifyRemindersChanged } from '../hooks/useReminders';

const LEAD_TIME_OPTIONS = [
  { value: 0, label: '0 minutes (at start time)' },
  { value: 1, label: '1 minute before (quick test)' },
  { value: 5, label: '5 minutes before' },
  { value: 10, label: '10 minutes before' },
  { value: 30, label: '30 minutes before' },
];

function getBrowserPermissionState() {
  if (typeof window === 'undefined' || !('Notification' in window)) {
    return 'unsupported';
  }
  return window.Notification.permission; // 'granted' | 'denied' | 'default'
}

export default function SettingsPage() {
  const { user } = useAuth();
  const [permission, setPermission] = useState(getBrowserPermissionState);
  const [browserEnabled, setBrowserEnabled] = useState(false);
  const [leadMinutes, setLeadMinutes] = useState(10);
  const [quietHoursStart, setQuietHoursStart] = useState('');
  const [quietHoursEnd, setQuietHoursEnd] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState({ message: '', type: 'info' });

  useEffect(() => {
    let active = true;
    fetchNotificationPreferences()
      .then((prefs) => {
        if (!active || !prefs) return;
        setBrowserEnabled(Boolean(prefs.browserEnabled));
        setLeadMinutes(prefs.leadMinutes ?? 10);
        setQuietHoursStart(prefs.quietHoursStart ? prefs.quietHoursStart.slice(0, 5) : '');
        setQuietHoursEnd(prefs.quietHoursEnd ? prefs.quietHoursEnd.slice(0, 5) : '');
      })
      .catch(() => {})
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

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

      {/* Profile Summary */}
      <Card>
        <h2 className="text-base font-bold text-ink pb-3 border-b border-border/70">Profile</h2>
        <dl className="mt-4 grid grid-cols-1 sm:grid-cols-3 gap-4 text-sm">
          <div>
            <dt className="text-xs font-medium text-muted">Full Name</dt>
            <dd className="mt-1 font-semibold text-ink">{user?.fullName || '—'}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-muted">Email</dt>
            <dd className="mt-1 font-semibold text-ink">{user?.email || '—'}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-muted">Time Zone</dt>
            <dd className="mt-1 font-semibold text-ink">{user?.timeZone || 'Asia/Kolkata'}</dd>
          </div>
        </dl>
      </Card>

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
          {/* Honest closed-tab limitation note */}
          <div className="rounded-xl border border-primary/20 bg-primary-soft p-4 text-xs sm:text-sm text-ink leading-relaxed">
            <p className="font-semibold">Honest reminder scope (Open-tab reminders)</p>
            <p className="mt-1 text-body">
              In-app toast alerts and browser notifications run via local timers while PathPilot is
              open in a browser tab. When you close the tab or close the browser completely, no
              notifications can fire. Background delivery via Web Push &amp; a Service Worker is
              planned for a future version.
            </p>
          </div>

          {/* Explicit click-only browser permission control */}
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

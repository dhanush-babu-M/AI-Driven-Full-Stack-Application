import { useEffect, useState } from 'react';
import { API_BASE_URL, fetchHealth } from '../api/client';
import PageHeader from '../components/PageHeader';
import Card from '../components/Card';
import StatusChip from '../components/StatusChip';
import SourceTag from '../components/SourceTag';

export default function DashboardPlaceholderPage() {
  const [health, setHealth] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const checkBackend = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await fetchHealth();
      setHealth(data);
    } catch (err) {
      setHealth(null);
      setError(err.message || 'Unable to reach backend /api/health endpoint.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    checkBackend();
  }, []);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Dashboard"
        breadcrumb="Home"
        rightSlot={
          <button
            type="button"
            onClick={checkBackend}
            className="px-4 py-2 rounded-pill bg-primary text-white text-sm font-medium hover:bg-primary-hover transition-colors duration-fast"
          >
            {loading ? 'Checking...' : 'Re-check Backend'}
          </button>
        }
      />

      <Card>
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 pb-4 border-b border-border">
          <div>
            <h2 className="text-lg font-semibold text-ink">Backend Connectivity Check</h2>
            <p className="text-sm text-muted">
              Live response from <code className="font-mono text-xs bg-canvas px-1.5 py-0.5 rounded">{API_BASE_URL}/api/health</code>
            </p>
          </div>
          <div className="flex items-center gap-2">
            <SourceTag source="CALCULATED" />
            {loading ? (
              <StatusChip status="pending" label="Checking" />
            ) : health ? (
              <StatusChip status="completed" label="Connected" />
            ) : (
              <StatusChip status="skipped" label="Disconnected" />
            )}
          </div>
        </div>

        {loading && (
          <p className="pt-4 text-sm text-muted">Contacting Spring Boot backend...</p>
        )}

        {!loading && error && (
          <div className="mt-4 rounded-xl bg-skipped-soft border border-skipped/20 p-4 text-sm text-skipped">
            <p className="font-semibold">Could not reach backend</p>
            <p className="mt-1 text-xs text-body">{error}</p>
          </div>
        )}

        {!loading && health && (
          <dl className="pt-4 grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="rounded-xl bg-canvas p-4">
              <dt className="text-xs font-medium uppercase tracking-wider text-muted">Status</dt>
              <dd className="mt-1 text-base font-semibold text-completed">{health.status}</dd>
            </div>
            <div className="rounded-xl bg-canvas p-4">
              <dt className="text-xs font-medium uppercase tracking-wider text-muted">App Name</dt>
              <dd className="mt-1 text-base font-semibold text-ink">{health.appName}</dd>
            </div>
            <div className="rounded-xl bg-canvas p-4">
              <dt className="text-xs font-medium uppercase tracking-wider text-muted">Server UTC Time</dt>
              <dd className="mt-1 text-sm font-mono text-ink break-all">{health.time}</dd>
            </div>
          </dl>
        )}
      </Card>

      <Card>
        <h2 className="text-base font-semibold text-ink">Phase 1 Placeholder</h2>
        <p className="mt-1 text-sm text-muted">
          The full Dashboard widgets and ProgressCalculator metrics will be built in Phase 4 once your wireframe and data APIs are ready.
        </p>
      </Card>
    </div>
  );
}

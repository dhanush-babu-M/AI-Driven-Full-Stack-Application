import { useParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import Card from '../components/Card';
import EmptyState from '../components/EmptyState';

export default function PlaceholderPage({ title, breadcrumb, description }) {
  const params = useParams();
  const paramEntries = Object.entries(params);

  return (
    <div>
      <PageHeader title={title} breadcrumb={breadcrumb} />
      <Card>
        <EmptyState
          title={`${title} — Placeholder`}
          description={
            description ||
            'This screen is part of the Phase 1 navigation shell. Its full layout and features will be built in a later phase using your wireframe.'
          }
        />
        {paramEntries.length > 0 && (
          <div className="mt-4 text-center text-xs text-muted">
            Route parameters:{' '}
            {paramEntries.map(([key, value]) => (
              <span key={key} className="font-mono text-ink">
                {key}={value}
              </span>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}

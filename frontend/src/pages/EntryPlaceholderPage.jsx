import { Link } from 'react-router-dom';
import { APP_NAME, APP_TAGLINE } from '../config/app';
import Card from '../components/Card';

export default function EntryPlaceholderPage({ title, subtitle }) {
  return (
    <div className="min-h-screen bg-canvas flex items-center justify-center p-4">
      <Card className="w-full max-w-md text-center space-y-4">
        <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl bg-primary text-white font-bold text-lg">
          P
        </div>
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-muted">
            {APP_NAME} · {APP_TAGLINE}
          </p>
          <h1 className="mt-1 text-2xl font-bold text-ink">{title}</h1>
          <p className="mt-2 text-sm text-muted">{subtitle}</p>
        </div>
        <div className="pt-2">
          <Link
            to="/app/dashboard"
            className="inline-flex items-center justify-center px-5 py-2.5 rounded-pill bg-primary text-white text-sm font-medium hover:bg-primary-hover transition-colors duration-fast"
          >
            Go to App Shell
          </Link>
        </div>
      </Card>
    </div>
  );
}

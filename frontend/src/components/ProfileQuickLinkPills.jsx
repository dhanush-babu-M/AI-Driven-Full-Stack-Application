import { Link } from 'react-router-dom';
import { isValidHttpUrl } from '../utils/urlValidation';

function LinkGlyph({ kind }) {
  const props = {
    className: 'h-4 w-4 shrink-0',
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: '2',
    'aria-hidden': 'true',
  };
  if (kind === 'github') {
    return (
      <svg className="h-4 w-4 shrink-0" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path
          fillRule="evenodd"
          clipRule="evenodd"
          d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"
        />
      </svg>
    );
  }
  if (kind === 'leetcode') {
    return (
      <svg {...props}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M8 9l-4 3 4 3M16 9l4 3-4 3M13 6l-2 12" />
      </svg>
    );
  }
  return (
    <svg {...props}>
      <circle cx="12" cy="12" r="10" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
    </svg>
  );
}

/**
 * Renders the three prominent profile link pill buttons: Portfolio, GitHub, LeetCode.
 * Valid URLs open in a new tab with rel="noopener noreferrer"; empty links show "Add link".
 */
export default function ProfileQuickLinkPills({ links, layout = 'row' }) {
  const items = [
    { key: 'portfolio', label: 'Portfolio', url: links?.portfolioUrl },
    { key: 'github', label: 'GitHub', url: links?.githubUrl },
    { key: 'leetcode', label: 'LeetCode', url: links?.leetcodeUrl || links?.codingProfileUrl },
  ];

  const containerClass =
    layout === 'grid'
      ? 'grid grid-cols-1 sm:grid-cols-3 gap-2.5'
      : 'flex flex-wrap items-center gap-3';

  return (
    <div className={containerClass}>
      {items.map((item) => {
        const valid = isValidHttpUrl(item.url);
        if (valid) {
          return (
            <a
              key={item.key}
              href={item.url}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center justify-center gap-2 rounded-pill border border-primary/25 bg-primary-soft px-4 py-2.5 text-sm font-semibold text-primary hover:bg-primary hover:text-white transition-colors duration-fast shadow-sm"
            >
              <LinkGlyph kind={item.key} />
              <span>{item.label}</span>
              <span aria-hidden="true" className="text-xs opacity-80">↗</span>
            </a>
          );
        }

        return (
          <Link
            key={item.key}
            to="/app/settings"
            className="inline-flex items-center justify-center gap-2 rounded-pill border border-dashed border-border bg-canvas px-4 py-2.5 text-sm font-medium text-muted hover:border-primary/40 hover:text-primary hover:bg-primary-soft/50 transition-colors duration-fast"
          >
            <LinkGlyph kind={item.key} />
            <span>{item.label}:</span>
            <span className="font-semibold text-primary">Add link</span>
          </Link>
        );
      })}
    </div>
  );
}

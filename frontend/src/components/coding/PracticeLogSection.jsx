import { Fragment, useState } from 'react';
import Card from '../Card';
import EmptyState from '../EmptyState';
import SourceTag from '../SourceTag';

const PLATFORM_TABS = ['All', 'LeetCode', 'CodingBat', 'Other'];

const RESULT_BADGE = {
  Solved: 'bg-completed-soft text-completed border-completed/25',
  Attempted: 'bg-pending-soft text-amber-800 border-pending/30',
  Practiced: 'bg-primary-soft text-primary border-primary/20',
};

export default function PracticeLogSection({
  items,
  totalItems,
  totalPages,
  page,
  viewAll,
  availableTopics,
  platformFilter,
  topicFilter,
  resultFilter,
  loading,
  onPlatformChange,
  onTopicChange,
  onResultChange,
  onPageChange,
  onToggleViewAll,
  onOpenCreate,
  onOpenEdit,
  onDelete,
  onViewHistory,
}) {
  const [expandedIds, setExpandedIds] = useState(() => new Set());

  const toggleNotes = (id) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  return (
    <Card className="space-y-4">
      {/* Header & CTA */}
      <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-border/70">
        <div>
          <h2 className="text-base font-bold text-ink">Coding Practice Records</h2>
          <p className="text-xs text-muted mt-0.5">
            Your manual coding practice log with topic tags, outcomes, and reflection notes.
          </p>
        </div>

        <button
          type="button"
          onClick={onOpenCreate}
          className="rounded-pill bg-primary px-4 py-2 text-xs font-semibold text-white hover:bg-primary-hover shadow-sm"
        >
          + Log Practice
        </button>
      </div>

      {/* Platform Tabs + Filters */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div
          role="tablist"
          aria-label="Platform tabs"
          className="inline-flex flex-wrap rounded-pill border border-border bg-canvas p-1 text-xs font-semibold"
        >
          {PLATFORM_TABS.map((tab) => (
            <button
              key={tab}
              type="button"
              role="tab"
              aria-selected={platformFilter === tab}
              onClick={() => onPlatformChange(tab)}
              className={`rounded-pill px-3.5 py-1 transition-colors duration-fast ${
                platformFilter === tab
                  ? 'bg-primary text-white shadow-sm'
                  : 'text-body hover:text-ink'
              }`}
            >
              {tab}
            </button>
          ))}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <select
            aria-label="Filter by topic"
            value={topicFilter}
            onChange={(e) => onTopicChange(e.target.value)}
            className="rounded-pill border border-border bg-surface px-3 py-1.5 text-xs font-medium text-ink focus:outline-none focus:border-primary"
          >
            <option value="">All Topics</option>
            {availableTopics.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>

          <select
            aria-label="Filter by result"
            value={resultFilter}
            onChange={(e) => onResultChange(e.target.value)}
            className="rounded-pill border border-border bg-surface px-3 py-1.5 text-xs font-medium text-ink focus:outline-none focus:border-primary"
          >
            <option value="">All Results</option>
            <option value="Solved">Solved</option>
            <option value="Attempted">Attempted</option>
            <option value="Practiced">Practiced</option>
          </select>
        </div>
      </div>

      {/* Table or Empty State */}
      {loading ? (
        <p className="py-8 text-center text-xs text-muted">Loading practice records...</p>
      ) : items.length === 0 ? (
        <EmptyState
          title="No coding practice logged in this view"
          description="Record problems you solved, attempted, or practiced — every entry is tagged MANUAL."
          action={
            <button
              type="button"
              onClick={onOpenCreate}
              className="rounded-pill bg-primary px-4 py-2 text-xs font-semibold text-white hover:bg-primary-hover"
            >
              + Log Practice
            </button>
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs sm:text-sm border-collapse">
            <thead>
              <tr className="border-b border-border text-[11px] font-semibold uppercase tracking-wider text-muted">
                <th className="py-2.5 pr-3">Date</th>
                <th className="py-2.5 pr-3">Platform</th>
                <th className="py-2.5 pr-3">Problem</th>
                <th className="py-2.5 pr-3">Topic</th>
                <th className="py-2.5 pr-3">Difficulty</th>
                <th className="py-2.5 pr-3">Result</th>
                <th className="py-2.5 pr-3">Source</th>
                <th className="py-2.5 pr-3">Notes</th>
                <th className="py-2.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/70">
              {items.map((row) => {
                const isExpanded = expandedIds.has(row.id);
                const resultStyle = RESULT_BADGE[row.result] || RESULT_BADGE.Practiced;
                return (
                  <Fragment key={row.id}>
                    <tr className="hover:bg-canvas/50 transition-colors">
                      <td className="py-3 pr-3 whitespace-nowrap font-medium text-ink">
                        {row.activityDate}
                      </td>
                      <td className="py-3 pr-3 whitespace-nowrap text-body">{row.platform}</td>
                      <td className="py-3 pr-3 font-semibold text-ink">
                        {row.referenceUrl ? (
                          <a
                            href={row.referenceUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-primary hover:underline"
                          >
                            {row.problemTitle} ↗
                          </a>
                        ) : (
                          row.problemTitle
                        )}
                      </td>
                      <td className="py-3 pr-3 whitespace-nowrap text-body">{row.topic || '—'}</td>
                      <td className="py-3 pr-3 whitespace-nowrap text-body">
                        {row.difficulty || '—'}
                      </td>
                      <td className="py-3 pr-3 whitespace-nowrap">
                        <span
                          className={`inline-flex items-center rounded-pill border px-2.5 py-0.5 text-[11px] font-semibold ${resultStyle}`}
                        >
                          {row.result}
                        </span>
                      </td>
                      <td className="py-3 pr-3 whitespace-nowrap">
                        <SourceTag source={row.dataSource || 'MANUAL'} />
                      </td>
                      <td className="py-3 pr-3 whitespace-nowrap">
                        {row.notes ? (
                          <button
                            type="button"
                            onClick={() => toggleNotes(row.id)}
                            className="text-xs font-semibold text-primary hover:underline"
                          >
                            {isExpanded ? 'Hide notes' : 'View notes'}
                          </button>
                        ) : (
                          <span className="text-xs text-muted">—</span>
                        )}
                      </td>
                      <td className="py-3 text-right whitespace-nowrap space-x-2">
                        <button
                          type="button"
                          onClick={() => onOpenEdit(row)}
                          className="text-xs font-semibold text-primary hover:underline"
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          onClick={() => onViewHistory(row)}
                          className="text-xs font-semibold text-body hover:underline"
                        >
                          History
                        </button>
                        <button
                          type="button"
                          onClick={() => onDelete(row)}
                          className="text-xs font-semibold text-skipped hover:underline"
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                    {isExpanded && row.notes && (
                      <tr className="bg-canvas/70">
                        <td colSpan={9} className="px-4 py-3 text-xs text-body whitespace-pre-wrap">
                          <span className="font-semibold text-ink">What I learned: </span>
                          {row.notes}
                        </td>
                      </tr>
                    )}
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination & View All */}
      {totalItems > 0 && (
        <div className="pt-3 border-t border-border/70 flex flex-wrap items-center justify-between gap-3 text-xs text-muted">
          <span>
            Showing {items.length} of {totalItems} record(s)
          </span>

          <div className="flex items-center gap-2">
            {!viewAll && totalPages > 1 && (
              <>
                <button
                  type="button"
                  disabled={page <= 0}
                  onClick={() => onPageChange(page - 1)}
                  className="rounded-pill border border-border px-3 py-1 font-semibold text-body hover:bg-canvas disabled:opacity-50"
                >
                  Prev
                </button>
                <span className="font-medium text-ink">
                  Page {page + 1} / {totalPages}
                </span>
                <button
                  type="button"
                  disabled={page + 1 >= totalPages}
                  onClick={() => onPageChange(page + 1)}
                  className="rounded-pill border border-border px-3 py-1 font-semibold text-body hover:bg-canvas disabled:opacity-50"
                >
                  Next
                </button>
              </>
            )}
            {totalItems > 10 && (
              <button
                type="button"
                onClick={onToggleViewAll}
                className="rounded-pill border border-border px-3 py-1 font-semibold text-primary hover:bg-primary-soft"
              >
                {viewAll ? 'Paginate (10)' : 'View all'}
              </button>
            )}
          </div>
        </div>
      )}
    </Card>
  );
}

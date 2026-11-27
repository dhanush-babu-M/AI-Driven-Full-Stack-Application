import { useCallback, useEffect, useState } from 'react';
import {
  createJob,
  fetchJobDetail,
  fetchJobs,
  fetchJobStats,
  fetchUpcomingFollowups,
  updateJob,
  updateJobStatus,
} from '../../api/client';
import { notifyRemindersChanged } from '../../hooks/useReminders';
import Card from '../Card';
import EmptyState from '../EmptyState';
import StatTile from '../StatTile';
import JobDetailDrawer from './JobDetailDrawer';
import JobFormModal from './JobFormModal';
import {
  getJobSourceLabel,
  JOB_STATUS_OPTIONS,
  JobStatusChip,
} from './jobMeta';
import UpcomingFollowupsCard from './UpcomingFollowupsCard';

export default function JobApplicationsSection({
  initialJobId = null,
  onClearInitialJobId,
  onToast,
}) {
  const [stats, setStats] = useState(null);
  const [jobsPage, setJobsPage] = useState({
    items: [],
    page: 0,
    size: 10,
    totalItems: 0,
    totalPages: 1,
  });
  const [followups, setFollowups] = useState([]);
  const [loading, setLoading] = useState(true);

  // Filters & pagination
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [viewAll, setViewAll] = useState(false);

  // Modal & Drawer
  const [formOpen, setFormOpen] = useState(false);
  const [editingJob, setEditingJob] = useState(null);
  const [savingJob, setSavingJob] = useState(false);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [detailJob, setDetailJob] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [pageData, statsData, followupData] = await Promise.all([
        fetchJobs({
          search: search.trim(),
          status: statusFilter,
          from: fromDate,
          to: toDate,
          page: viewAll ? 0 : page,
          size: viewAll ? 500 : 10,
        }),
        fetchJobStats(),
        fetchUpcomingFollowups(),
      ]);
      setJobsPage(pageData);
      setStats(statsData);
      setFollowups(followupData || []);
    } catch (err) {
      onToast?.(err?.message || 'Unable to load job applications.', 'error');
    } finally {
      setLoading(false);
    }
  }, [search, statusFilter, fromDate, toDate, page, viewAll, onToast]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const openJobDrawer = useCallback(async (id) => {
    setDrawerOpen(true);
    setDetailLoading(true);
    setDetailError('');
    try {
      const data = await fetchJobDetail(id);
      setDetailJob(data);
    } catch (err) {
      setDetailJob(null);
      setDetailError(err?.message || 'Job application not found or access denied.');
    } finally {
      setDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    if (initialJobId) {
      openJobDrawer(initialJobId);
    }
  }, [initialJobId, openJobDrawer]);

  const closeDrawer = () => {
    setDrawerOpen(false);
    setDetailJob(null);
    setDetailError('');
    onClearInitialJobId?.();
  };

  const handleSaveJob = async (payload) => {
    setSavingJob(true);
    try {
      if (editingJob) {
        const updated = await updateJob(editingJob.id, payload);
        onToast?.('Application updated.', 'success');
        if (drawerOpen && detailJob?.id === editingJob.id) {
          setDetailJob(updated);
        }
      } else {
        const created = await createJob(payload);
        onToast?.('Application added.', 'success');
        if (created?.id) {
          setDetailJob(created);
          setDrawerOpen(true);
        }
      }
      notifyRemindersChanged();
      await loadAll();
      return true;
    } catch (err) {
      onToast?.(err?.message || 'Unable to save application.', 'error');
      return false;
    } finally {
      setSavingJob(false);
    }
  };

  const handleInlineStatusChange = async (jobId, newStatus) => {
    try {
      const updated = await updateJobStatus(jobId, { status: newStatus });
      onToast?.('Status updated.', 'success');
      await loadAll();
      if (drawerOpen && detailJob?.id === jobId) {
        setDetailJob(updated);
      }
    } catch (err) {
      onToast?.(err?.message || 'Unable to update status.', 'error');
    }
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 id="job-apps-heading" className="text-lg font-bold text-ink">
            Job Applications
          </h2>
          <p className="text-xs text-muted">
            Manual application tracker with status timeline, follow-up reminders, and interview prep checklists.
          </p>
        </div>

        <button
          type="button"
          onClick={() => {
            setEditingJob(null);
            setFormOpen(true);
          }}
          className="inline-flex items-center justify-center rounded-pill bg-primary px-4 py-2 text-xs sm:text-sm font-semibold text-white hover:bg-primary-hover shadow-sm"
        >
          + Add Application
        </button>
      </div>

      {/* Statistics Strip */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        <StatTile label="Total" value={stats?.total ?? 0} />
        <StatTile label="Applied" value={stats?.applied ?? 0} />
        <StatTile label="Interviews" value={stats?.interviews ?? 0} />
        <StatTile label="Offers" value={stats?.offers ?? 0} />
        <StatTile label="Rejected" value={stats?.rejected ?? 0} />
        <StatTile label="Follow-ups Due (7d)" value={stats?.followupsDueThisWeek ?? 0} />
      </div>

      {/* Upcoming Follow-ups Card */}
      <UpcomingFollowupsCard
        followups={followups}
        onUpdated={loadAll}
        onOpenJob={(id) => openJobDrawer(id)}
        onToast={onToast}
      />

      {/* Search, Filters & Table */}
      <Card className="space-y-4">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2.5 flex-1">
            <input
              type="search"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              placeholder="Search company, role, or location..."
              aria-label="Search applications"
              className="w-full sm:w-64 rounded-pill border border-border bg-canvas px-3.5 py-1.5 text-xs text-ink focus:outline-none focus:border-primary focus:bg-surface"
            />

            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                setPage(0);
              }}
              aria-label="Filter by status"
              className="rounded-pill border border-border bg-surface px-3 py-1.5 text-xs font-medium text-ink"
            >
              <option value="">All Statuses</option>
              {JOB_STATUS_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>

            <div className="flex items-center gap-1.5 text-xs text-muted">
              <input
                type="date"
                value={fromDate}
                onChange={(e) => {
                  setFromDate(e.target.value);
                  setPage(0);
                }}
                aria-label="Applied from date"
                className="rounded-xl border border-border bg-surface px-2.5 py-1 text-xs text-ink"
              />
              <span>to</span>
              <input
                type="date"
                value={toDate}
                onChange={(e) => {
                  setToDate(e.target.value);
                  setPage(0);
                }}
                aria-label="Applied to date"
                className="rounded-xl border border-border bg-surface px-2.5 py-1 text-xs text-ink"
              />
            </div>

            {(search || statusFilter || fromDate || toDate) && (
              <button
                type="button"
                onClick={() => {
                  setSearch('');
                  setStatusFilter('');
                  setFromDate('');
                  setToDate('');
                  setPage(0);
                }}
                className="text-xs font-semibold text-primary hover:underline"
              >
                Reset
              </button>
            )}
          </div>

          <button
            type="button"
            onClick={() => {
              setViewAll((prev) => !prev);
              setPage(0);
            }}
            className="self-start lg:self-auto rounded-pill border border-border bg-canvas px-3.5 py-1.5 text-xs font-semibold text-ink hover:bg-primary-soft hover:text-primary"
          >
            {viewAll ? 'Paginate (10/page)' : 'View All'}
          </button>
        </div>

        {loading ? (
          <p className="py-6 text-xs text-muted text-center">Loading applications...</p>
        ) : jobsPage.items.length === 0 ? (
          <EmptyState
            title="No applications yet. Add the first job you are interested in."
            description="Track companies, roles, follow-up reminders, status history, and interview prep in one place."
            action={
              <button
                type="button"
                onClick={() => {
                  setEditingJob(null);
                  setFormOpen(true);
                }}
                className="rounded-pill bg-primary px-5 py-2 text-xs font-semibold text-white hover:bg-primary-hover"
              >
                + Add Application
              </button>
            }
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-border text-[11px] uppercase tracking-wider text-muted">
                    <th className="py-2.5 pr-4 font-semibold">Company</th>
                    <th className="py-2.5 pr-4 font-semibold">Role</th>
                    <th className="py-2.5 pr-4 font-semibold">Applied On</th>
                    <th className="py-2.5 pr-4 font-semibold">Status</th>
                    <th className="py-2.5 pr-4 font-semibold">Follow-up Date</th>
                    <th className="py-2.5 font-semibold">Source</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/70">
                  {jobsPage.items.map((job) => (
                    <tr
                      key={job.id}
                      onClick={() => openJobDrawer(job.id)}
                      className="cursor-pointer hover:bg-canvas/70 transition-colors"
                    >
                      <td className="py-3 pr-4 font-semibold text-ink whitespace-nowrap">
                        {job.companyName}
                        {job.dataSource === 'DEMO' && (
                          <span className="ml-1.5 text-[10px] font-bold text-muted">(DEMO)</span>
                        )}
                      </td>
                      <td className="py-3 pr-4 text-body">
                        <div className="font-medium text-ink">{job.roleTitle}</div>
                        {job.location && (
                          <div className="text-[11px] text-muted">{job.location}</div>
                        )}
                      </td>
                      <td className="py-3 pr-4 text-body whitespace-nowrap">{job.appliedDate}</td>
                      <td
                        className="py-3 pr-4"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <div className="flex items-center gap-2">
                          <JobStatusChip status={job.status} />
                          <select
                            aria-label={`Change status for ${job.companyName}`}
                            value={job.status}
                            onChange={(e) => handleInlineStatusChange(job.id, e.target.value)}
                            className="rounded-lg border border-border bg-surface px-1.5 py-1 text-[11px] text-ink"
                          >
                            {JOB_STATUS_OPTIONS.map((opt) => (
                              <option key={opt.value} value={opt.value}>
                                {opt.label}
                              </option>
                            ))}
                          </select>
                        </div>
                      </td>
                      <td className="py-3 pr-4 text-body whitespace-nowrap">
                        {job.followupDate || '—'}
                      </td>
                      <td className="py-3 text-body whitespace-nowrap">
                        {getJobSourceLabel(job.source)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {!viewAll && jobsPage.totalPages > 1 && (
              <div className="pt-3 border-t border-border/70 flex items-center justify-between text-xs text-muted">
                <span>
                  Page {jobsPage.page + 1} of {jobsPage.totalPages} ({jobsPage.totalItems} total)
                </span>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    disabled={page === 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    className="rounded-pill border border-border px-3 py-1 font-semibold text-ink disabled:opacity-40"
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    disabled={page + 1 >= jobsPage.totalPages}
                    onClick={() => setPage((p) => p + 1)}
                    className="rounded-pill border border-border px-3 py-1 font-semibold text-ink disabled:opacity-40"
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </Card>

      <JobFormModal
        open={formOpen}
        initialJob={editingJob}
        saving={savingJob}
        onClose={() => setFormOpen(false)}
        onSubmit={handleSaveJob}
      />

      <JobDetailDrawer
        open={drawerOpen}
        job={detailJob}
        loading={detailLoading}
        error={detailError}
        onClose={closeDrawer}
        onEditJob={(j) => {
          setEditingJob(j);
          setFormOpen(true);
        }}
        onReloadJob={openJobDrawer}
        onJobsMutated={loadAll}
        onToast={onToast}
      />
    </div>
  );
}

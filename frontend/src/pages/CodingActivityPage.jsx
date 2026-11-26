import { useCallback, useEffect, useState } from 'react';
import {
  addMonths,
  endOfMonth,
  format,
  parseISO,
  startOfMonth,
  subDays,
  subMonths,
} from 'date-fns';
import {
  connectGitHub,
  createCodingActivity,
  deleteCodingActivity,
  fetchCodingActivities,
  fetchCodingDayDetails,
  fetchCodingHeatmap,
  fetchCodingHistory,
  fetchCodingStreak,
  fetchGitHubRepos,
  fetchGitHubStatus,
  fetchProfileLinks,
  syncGitHub,
  updateCodingActivity,
} from '../api/client';
import Modal from '../components/Modal';
import PageHeader from '../components/PageHeader';
import SourceTag from '../components/SourceTag';
import Toast from '../components/Toast';
import ActivitySummarySection from '../components/coding/ActivitySummarySection';
import ContributionHeatmap from '../components/coding/ContributionHeatmap';
import DayActivityDrawer from '../components/coding/DayActivityDrawer';
import ExternalProfilesCard from '../components/coding/ExternalProfilesCard';
import PracticeFormModal from '../components/coding/PracticeFormModal';
import PracticeLogSection from '../components/coding/PracticeLogSection';

function getCurrentMonthRange() {
  const now = new Date();
  return {
    mode: 'month',
    from: format(startOfMonth(now), 'yyyy-MM-dd'),
    to: format(endOfMonth(now), 'yyyy-MM-dd'),
  };
}

export default function CodingActivityPage() {
  const [range, setRange] = useState(getCurrentMonthRange);
  const [heatmapSource, setHeatmapSource] = useState('combined');

  const [heatmap, setHeatmap] = useState(null);
  const [heatmapLoading, setHeatmapLoading] = useState(true);

  const [summary, setSummary] = useState(null);
  const [summaryLoading, setSummaryLoading] = useState(true);

  const [githubStatus, setGithubStatus] = useState(null);
  const [profileLinks, setProfileLinks] = useState(null);
  const [repos, setRepos] = useState([]);
  const [reposLoading, setReposLoading] = useState(false);
  const [reposError, setReposError] = useState('');
  const [syncing, setSyncing] = useState(false);
  const [connecting, setConnecting] = useState(false);

  const [platformTab, setPlatformTab] = useState('All');
  const [topicFilter, setTopicFilter] = useState('');
  const [resultFilter, setResultFilter] = useState('');
  const [page, setPage] = useState(0);
  const [viewAll, setViewAll] = useState(false);
  const [practicePage, setPracticePage] = useState({
    items: [],
    totalItems: 0,
    totalPages: 1,
    availableTopics: [],
  });
  const [practiceLoading, setPracticeLoading] = useState(true);

  const [selectedDay, setSelectedDay] = useState('');
  const [dayDetail, setDayDetail] = useState(null);
  const [dayLoading, setDayLoading] = useState(false);

  const [formOpen, setFormOpen] = useState(false);
  const [editingRow, setEditingRow] = useState(null);
  const [formDefaultDate, setFormDefaultDate] = useState('');
  const [savingPractice, setSavingPractice] = useState(false);

  const [historyTarget, setHistoryTarget] = useState(null);
  const [historyRows, setHistoryRows] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  const [toast, setToast] = useState({ message: '', type: 'info' });

  const loadHeatmap = useCallback(async () => {
    setHeatmapLoading(true);
    try {
      const data = await fetchCodingHeatmap({
        from: range.from,
        to: range.to,
        source: heatmapSource,
      });
      setHeatmap(data);
    } catch (err) {
      setToast({ message: err?.message || 'Unable to load heatmap.', type: 'error' });
    } finally {
      setHeatmapLoading(false);
    }
  }, [range.from, range.to, heatmapSource]);

  const loadSummary = useCallback(async () => {
    setSummaryLoading(true);
    try {
      const data = await fetchCodingStreak({ from: range.from, to: range.to });
      setSummary(data);
    } catch (err) {
      setToast({ message: err?.message || 'Unable to load coding summary.', type: 'error' });
    } finally {
      setSummaryLoading(false);
    }
  }, [range.from, range.to]);

  const loadPractices = useCallback(async () => {
    setPracticeLoading(true);
    try {
      const data = await fetchCodingActivities({
        from: range.from,
        to: range.to,
        platform: platformTab === 'All' ? '' : platformTab,
        topic: topicFilter,
        result: resultFilter,
        page: viewAll ? 0 : page,
        size: viewAll ? 500 : 10,
      });
      setPracticePage(data);
    } catch (err) {
      setToast({ message: err?.message || 'Unable to load practice logs.', type: 'error' });
    } finally {
      setPracticeLoading(false);
    }
  }, [range.from, range.to, platformTab, topicFilter, resultFilter, page, viewAll]);

  const loadProfilesAndRepos = useCallback(async () => {
    try {
      const [ghStatus, links] = await Promise.all([fetchGitHubStatus(), fetchProfileLinks()]);
      setGithubStatus(ghStatus);
      setProfileLinks(links);

      if (ghStatus?.connected) {
        setReposLoading(true);
        setReposError('');
        try {
          const repoList = await fetchGitHubRepos();
          setRepos(repoList || []);
        } catch (repoErr) {
          setReposError(repoErr?.message || 'Could not load public repositories.');
        } finally {
          setReposLoading(false);
        }
      } else {
        setRepos([]);
      }
    } catch (err) {
      setToast({ message: err?.message || 'Unable to load external profiles.', type: 'error' });
    }
  }, []);

  useEffect(() => {
    loadHeatmap();
  }, [loadHeatmap]);

  useEffect(() => {
    loadSummary();
  }, [loadSummary]);

  useEffect(() => {
    loadPractices();
  }, [loadPractices]);

  useEffect(() => {
    loadProfilesAndRepos();
  }, [loadProfilesAndRepos]);

  const openDayDrawer = async (dateStr) => {
    setSelectedDay(dateStr);
    setDayLoading(true);
    try {
      const detail = await fetchCodingDayDetails(dateStr);
      setDayDetail(detail);
    } catch (err) {
      setToast({ message: err?.message || 'Unable to load day details.', type: 'error' });
    } finally {
      setDayLoading(false);
    }
  };

  const handleShiftMonth = (delta) => {
    const anchor = parseISO(range.from);
    const nextMonth = delta > 0 ? addMonths(anchor, 1) : subMonths(anchor, 1);
    setRange({
      mode: 'month',
      from: format(startOfMonth(nextMonth), 'yyyy-MM-dd'),
      to: format(endOfMonth(nextMonth), 'yyyy-MM-dd'),
    });
    setPage(0);
  };

  const applyPreset = (preset) => {
    const today = new Date();
    if (preset === 'month') {
      setRange(getCurrentMonthRange());
    } else if (preset === '30d') {
      setRange({
        mode: '30d',
        from: format(subDays(today, 29), 'yyyy-MM-dd'),
        to: format(today, 'yyyy-MM-dd'),
      });
    } else if (preset === '90d') {
      setRange({
        mode: '90d',
        from: format(subDays(today, 89), 'yyyy-MM-dd'),
        to: format(today, 'yyyy-MM-dd'),
      });
    } else if (preset === '12w') {
      setRange({
        mode: '12w',
        from: format(subDays(today, 83), 'yyyy-MM-dd'),
        to: format(today, 'yyyy-MM-dd'),
      });
    }
    setPage(0);
  };

  const handleConnectGitHub = async (payload) => {
    setConnecting(true);
    try {
      await connectGitHub(payload);
      await Promise.all([loadProfilesAndRepos(), loadHeatmap()]);
      setToast({ message: `Connected GitHub account @${payload.username}.`, type: 'success' });
      return true;
    } catch (err) {
      setToast({ message: err?.message || 'Failed to connect GitHub account.', type: 'error' });
      return false;
    } finally {
      setConnecting(false);
    }
  };

  const handleSyncGitHub = async () => {
    setSyncing(true);
    try {
      const res = await syncGitHub();
      await Promise.all([loadProfilesAndRepos(), loadHeatmap()]);
      if (selectedDay) {
        await openDayDrawer(selectedDay);
      }
      setToast({ message: res?.message || 'GitHub sync complete.', type: 'success' });
    } catch (err) {
      await loadProfilesAndRepos();
      setToast({ message: err?.message || 'GitHub sync failed.', type: 'warning' });
    } finally {
      setSyncing(false);
    }
  };

  const handleSavePractice = async (payload) => {
    setSavingPractice(true);
    try {
      if (editingRow) {
        await updateCodingActivity(editingRow.id, payload);
        setToast({ message: 'Practice entry updated.', type: 'success' });
      } else {
        await createCodingActivity(payload);
        setToast({ message: 'Coding practice logged.', type: 'success' });
      }
      setFormOpen(false);
      setEditingRow(null);
      await Promise.all([loadPractices(), loadHeatmap(), loadSummary()]);
      if (selectedDay) {
        await openDayDrawer(selectedDay);
      }
    } catch (err) {
      setToast({ message: err?.message || 'Unable to save practice entry.', type: 'error' });
    } finally {
      setSavingPractice(false);
    }
  };

  const handleDeletePractice = async (row) => {
    if (!window.confirm(`Delete practice entry "${row.problemTitle}"? Audit history will be preserved.`)) {
      return;
    }
    try {
      await deleteCodingActivity(row.id);
      setToast({ message: 'Practice entry deleted (history preserved).', type: 'info' });
      await Promise.all([loadPractices(), loadHeatmap(), loadSummary()]);
      if (selectedDay) {
        await openDayDrawer(selectedDay);
      }
    } catch (err) {
      setToast({ message: err?.message || 'Unable to delete practice entry.', type: 'error' });
    }
  };

  const handleViewHistory = async (row) => {
    setHistoryTarget(row);
    setHistoryLoading(true);
    try {
      const rows = await fetchCodingHistory(row.id);
      setHistoryRows(rows || []);
    } catch (err) {
      setToast({ message: err?.message || 'Unable to load history.', type: 'error' });
    } finally {
      setHistoryLoading(false);
    }
  };

  const rangeLabel =
    range.mode === 'month'
      ? format(parseISO(range.from), 'MMMM yyyy')
      : `${range.from} → ${range.to}`;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Developer Activity"
        breadcrumb="Coding & Contributions"
        rightSlot={
          <div className="flex flex-wrap items-center gap-2">
            <div className="inline-flex items-center rounded-pill border border-border bg-surface px-1 py-1 text-xs shadow-sm">
              <button
                type="button"
                onClick={() => handleShiftMonth(-1)}
                aria-label="Previous month"
                className="rounded-pill px-2.5 py-1 font-semibold text-body hover:bg-canvas"
              >
                ‹ Prev
              </button>
              <span className="px-2.5 font-semibold text-ink">{rangeLabel}</span>
              <button
                type="button"
                onClick={() => handleShiftMonth(1)}
                aria-label="Next month"
                className="rounded-pill px-2.5 py-1 font-semibold text-body hover:bg-canvas"
              >
                Next ›
              </button>
            </div>

            <div className="inline-flex flex-wrap items-center gap-1 rounded-pill border border-border bg-surface p-1 text-xs">
              {[
                { id: 'month', label: 'This month' },
                { id: '30d', label: 'Last 30d' },
                { id: '12w', label: '12 weeks' },
                { id: '90d', label: 'Last 90d' },
              ].map((preset) => (
                <button
                  key={preset.id}
                  type="button"
                  onClick={() => applyPreset(preset.id)}
                  className={`rounded-pill px-2.5 py-1 font-semibold transition-colors ${
                    range.mode === preset.id
                      ? 'bg-primary text-white'
                      : 'text-body hover:bg-canvas'
                  }`}
                >
                  {preset.label}
                </button>
              ))}
            </div>
          </div>
        }
      />

      <p className="-mt-4 text-sm text-muted">
        Track your coding practice, contributions and profile links.
      </p>

      {/* Section 1 (Heatmap) + Section 2 (External Profiles) */}
      <div className="grid grid-cols-1 xl:grid-cols-12 gap-6 items-stretch">
        <div className="xl:col-span-8">
          <ContributionHeatmap
            heatmap={heatmap}
            source={heatmapSource}
            onSourceChange={setHeatmapSource}
            onSelectDay={openDayDrawer}
            onLogFirstPractice={() => {
              setEditingRow(null);
              setFormDefaultDate('');
              setFormOpen(true);
            }}
            loading={heatmapLoading}
          />
        </div>
        <div className="xl:col-span-4">
          <ExternalProfilesCard
            githubStatus={githubStatus}
            profileLinks={profileLinks}
            repos={repos}
            reposLoading={reposLoading}
            reposError={reposError}
            syncing={syncing}
            connecting={connecting}
            onConnectGitHub={handleConnectGitHub}
            onSyncGitHub={handleSyncGitHub}
          />
        </div>
      </div>

      {/* Section 4: Activity Summary */}
      <ActivitySummarySection summary={summary} loading={summaryLoading} />

      {/* Section 3: Coding Practice Records */}
      <PracticeLogSection
        items={practicePage.items || []}
        totalItems={practicePage.totalItems || 0}
        totalPages={practicePage.totalPages || 1}
        page={page}
        viewAll={viewAll}
        availableTopics={practicePage.availableTopics || []}
        platformFilter={platformTab}
        topicFilter={topicFilter}
        resultFilter={resultFilter}
        loading={practiceLoading}
        onPlatformChange={(tab) => {
          setPlatformTab(tab);
          setPage(0);
        }}
        onTopicChange={(val) => {
          setTopicFilter(val);
          setPage(0);
        }}
        onResultChange={(val) => {
          setResultFilter(val);
          setPage(0);
        }}
        onPageChange={setPage}
        onToggleViewAll={() => {
          setViewAll((v) => !v);
          setPage(0);
        }}
        onOpenCreate={() => {
          setEditingRow(null);
          setFormDefaultDate('');
          setFormOpen(true);
        }}
        onOpenEdit={(row) => {
          setEditingRow(row);
          setFormOpen(true);
        }}
        onDelete={handleDeletePractice}
        onViewHistory={handleViewHistory}
      />

      {/* Side Drawer for Clicked Heatmap Day */}
      <DayActivityDrawer
        dateStr={selectedDay}
        detail={dayDetail}
        loading={dayLoading}
        onClose={() => setSelectedDay('')}
        onLogForDate={(dateStr) => {
          setEditingRow(null);
          setFormDefaultDate(dateStr);
          setFormOpen(true);
        }}
      />

      {/* Create / Edit Practice Modal */}
      <PracticeFormModal
        open={formOpen}
        initialValue={editingRow}
        defaultDate={formDefaultDate}
        onClose={() => {
          setFormOpen(false);
          setEditingRow(null);
        }}
        onSubmit={handleSavePractice}
        saving={savingPractice}
      />

      {/* Record History Modal */}
      <Modal
        open={Boolean(historyTarget)}
        onClose={() => setHistoryTarget(null)}
        title={historyTarget ? `History · ${historyTarget.problemTitle}` : 'History'}
      >
        {historyLoading ? (
          <p className="text-xs text-muted">Loading audit history...</p>
        ) : historyRows.length === 0 ? (
          <p className="text-xs text-muted">No audit history entries found.</p>
        ) : (
          <ul className="space-y-2.5 max-h-80 overflow-y-auto">
            {historyRows.map((h) => (
              <li
                key={h.id}
                className="rounded-xl border border-border bg-canvas/60 p-3 text-xs space-y-1"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-bold text-ink">{h.actionType}</span>
                  <SourceTag source={h.actorSource || 'MANUAL'} />
                </div>
                <p className="text-body">{h.changeSummary}</p>
                <p className="text-[11px] text-muted">{h.createdAtUtc}</p>
              </li>
            ))}
          </ul>
        )}
      </Modal>

      <Toast
        message={toast.message}
        type={toast.type}
        onDismiss={() => setToast({ message: '', type: 'info' })}
      />
    </div>
  );
}

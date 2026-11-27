import { useState } from 'react';
import { approveAiProposal, rejectAiProposal } from '../../api/client';
import { notifyAiInboxChanged } from '../../hooks/useAiInbox';
import { notifyRemindersChanged } from '../../hooks/useReminders';
import CategoryChip from '../CategoryChip';
import Modal from '../Modal';
import SourceTag from '../SourceTag';

export default function ApprovalInboxModal({
  open,
  proposals = [],
  loading = false,
  onClose,
  onToast,
}) {
  const [busyId, setBusyId] = useState(null);

  const handleApprove = async (id) => {
    setBusyId(id);
    try {
      await approveAiProposal(id);
      notifyAiInboxChanged();
      notifyRemindersChanged();
      onToast?.('Proposal approved and added to your tasks.', 'success');
    } catch (err) {
      onToast?.(err?.message || 'Unable to approve proposal.', 'error');
    } finally {
      setBusyId(null);
    }
  };

  const handleReject = async (id) => {
    setBusyId(id);
    try {
      await rejectAiProposal(id);
      notifyAiInboxChanged();
      onToast?.('Proposal dismissed.', 'info');
    } catch (err) {
      onToast?.(err?.message || 'Unable to reject proposal.', 'error');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <Modal
      open={open}
      title="AI Approval Inbox"
      onClose={onClose}
      footer={
        <button
          type="button"
          onClick={onClose}
          className="rounded-pill border border-border bg-surface px-4 py-1.5 text-xs font-semibold text-ink hover:bg-canvas"
        >
          Close
        </button>
      }
    >
      <div className="space-y-3">
        <div className="rounded-xl border border-primary/20 bg-primary-soft/60 px-3 py-2 text-xs text-ink">
          AI never changes your tasks automatically. Review and approve each suggested task before it is saved.
        </div>

        {loading && proposals.length === 0 ? (
          <p className="text-xs text-muted py-4 text-center">Loading pending proposals...</p>
        ) : proposals.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border bg-canvas/50 p-6 text-center space-y-1">
            <p className="text-sm font-semibold text-ink">No pending AI proposals</p>
            <p className="text-xs text-muted">
              Use &ldquo;Add as task&rdquo; on Dashboard, &ldquo;Turn checklist into tasks&rdquo; in Job Analysis, or the AI Assistant to propose tasks.
            </p>
          </div>
        ) : (
          <ul className="space-y-2.5 max-h-[55vh] overflow-y-auto pr-1">
            {proposals.map((item) => {
              const busy = busyId === item.id;
              return (
                <li
                  key={item.id}
                  className="rounded-xl border border-border bg-canvas/60 p-3.5 space-y-2"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <CategoryChip category={item.category || 'JAVA_LEARNING'} />
                      <SourceTag source={item.dataSource || 'AI'} />
                      {item.taskDate && (
                        <span className="rounded-pill border border-border bg-surface px-2 py-0.5 text-[11px] font-medium text-muted">
                          {item.taskDate}
                        </span>
                      )}
                      {item.priority && (
                        <span className="rounded-pill border border-border bg-surface px-2 py-0.5 text-[11px] font-medium text-muted">
                          {item.priority}
                        </span>
                      )}
                    </div>
                  </div>

                  <div>
                    <p className="text-sm font-semibold text-ink">{item.title}</p>
                    {item.description && (
                      <p className="mt-0.5 text-xs text-body whitespace-pre-wrap">{item.description}</p>
                    )}
                    <p className="mt-1 text-[11px] text-muted">
                      {item.dataSource === 'AI'
                        ? 'AI-generated. Based on your saved records.'
                        : 'Rule-suggested from your saved records.'}
                    </p>
                  </div>

                  <div className="flex items-center justify-end gap-2 pt-1">
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => handleReject(item.id)}
                      className="rounded-pill border border-border bg-surface px-3 py-1 text-xs font-semibold text-muted hover:text-ink hover:bg-canvas disabled:opacity-50"
                    >
                      Reject
                    </button>
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => handleApprove(item.id)}
                      className="rounded-pill bg-primary px-3.5 py-1 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-50"
                    >
                      {busy ? 'Saving...' : 'Approve & Add Task'}
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </Modal>
  );
}

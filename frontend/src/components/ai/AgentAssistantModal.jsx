import { useState } from 'react';
import { sendAgentMessage } from '../../api/client';
import { notifyAiInboxChanged, openAiInbox } from '../../hooks/useAiInbox';
import Modal from '../Modal';
import SourceTag from '../SourceTag';

const QUICK_PROMPTS = [
  'What are my overdue tasks and what should I do next?',
  'Check my progress this week and suggest a task for today.',
  'Review my recorded activity over the last 7 days.',
];

export default function AgentAssistantModal({ open, onClose }) {
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);

  const runPrompt = async (promptText) => {
    const trimmed = (promptText ?? message).trim();
    if (!trimmed) {
      setError('Enter a question or ask PathPilot Assistant to plan a task.');
      return;
    }
    setSending(true);
    setError('');
    try {
      const data = await sendAgentMessage(trimmed);
      setResult(data);
      if (data?.createdProposals?.length > 0) {
        notifyAiInboxChanged();
      }
    } catch (err) {
      setError(err?.message || 'Unable to reach PathPilot Assistant right now.');
    } finally {
      setSending(false);
    }
  };

  return (
    <Modal
      open={open}
      title="PathPilot AI Assistant"
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
      <div className="space-y-4">
        <p className="text-xs text-muted">
          Tool-assisted mode reads only your own saved records (up to 4 tool steps) and can only draft task proposals for your Approval Inbox.
        </p>

        <div className="flex flex-wrap gap-1.5">
          {QUICK_PROMPTS.map((q) => (
            <button
              key={q}
              type="button"
              onClick={() => {
                setMessage(q);
                runPrompt(q);
              }}
              disabled={sending}
              className="rounded-pill border border-border bg-canvas px-2.5 py-1 text-[11px] font-medium text-ink hover:bg-primary-soft hover:text-primary disabled:opacity-50 text-left"
            >
              {q}
            </button>
          ))}
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            runPrompt(message);
          }}
          className="space-y-2"
        >
          <textarea
            rows={3}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            maxLength={1500}
            placeholder="Ask about your overdue tasks, weekly progress, or ask to propose a task..."
            className="w-full rounded-xl border border-border bg-canvas px-3 py-2 text-xs text-ink focus:outline-none focus:bg-surface focus:border-primary"
          />
          <div className="flex items-center justify-between gap-2">
            <span className="text-[11px] text-muted">{message.length}/1500</span>
            <button
              type="submit"
              disabled={sending}
              className="rounded-pill bg-primary px-4 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
            >
              {sending ? 'Running tools...' : 'Ask Assistant'}
            </button>
          </div>
        </form>

        {error && (
          <div className="rounded-xl border border-skipped/30 bg-skipped-soft px-3 py-2 text-xs font-medium text-skipped">
            {error}
          </div>
        )}

        {result && (
          <div className="rounded-xl border border-border bg-canvas/60 p-3.5 space-y-3 max-h-[42vh] overflow-y-auto">
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-bold text-ink">Assistant Response</span>
              <SourceTag source={result.source || 'RULE'} />
            </div>

            {result.notice && (
              <p className="rounded-lg border border-amber-300/70 bg-amber-50 px-2.5 py-1.5 text-[11px] text-amber-900">
                {result.notice}
              </p>
            )}

            <p className="text-xs text-body whitespace-pre-wrap leading-relaxed">{result.reply}</p>

            <p className="text-[11px] text-muted">
              {result.disclaimer || 'AI-generated. Based on your saved records.'}
            </p>

            {result.toolCalls?.length > 0 && (
              <div className="pt-2 border-t border-border/70 space-y-1.5">
                <p className="text-[11px] font-bold uppercase tracking-wider text-muted">
                  Tools checked ({result.toolCalls.length})
                </p>
                <ul className="space-y-1">
                  {result.toolCalls.map((call, idx) => (
                    <li key={`${call.toolName}-${idx}`} className="text-[11px] text-body">
                      <span className="font-mono font-semibold text-ink">{call.toolName}</span>
                      {' — '}
                      <span className="text-muted">{call.summary}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {result.createdProposals?.length > 0 && (
              <div className="pt-2 border-t border-border/70 flex items-center justify-between gap-2">
                <span className="text-xs font-semibold text-ink">
                  {result.createdProposals.length} task proposal queued
                </span>
                <button
                  type="button"
                  onClick={() => {
                    onClose?.();
                    openAiInbox();
                  }}
                  className="rounded-pill bg-primary px-3 py-1 text-[11px] font-semibold text-white hover:bg-primary-hover"
                >
                  Review in Approval Inbox →
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </Modal>
  );
}

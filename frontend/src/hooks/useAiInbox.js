import { useCallback, useEffect, useState } from 'react';
import { fetchAiProposals } from '../api/client';

const INBOX_CHANGED_EVENT = 'pathpilot:ai-inbox-changed';
const OPEN_INBOX_EVENT = 'pathpilot:open-ai-inbox';
const OPEN_AGENT_EVENT = 'pathpilot:open-ai-agent';

export function notifyAiInboxChanged() {
  window.dispatchEvent(new CustomEvent(INBOX_CHANGED_EVENT));
}

export function openAiInbox() {
  window.dispatchEvent(new CustomEvent(OPEN_INBOX_EVENT));
}

export function openAiAgent() {
  window.dispatchEvent(new CustomEvent(OPEN_AGENT_EVENT));
}

export function useAiInbox(enabled = true) {
  const [pendingProposals, setPendingProposals] = useState([]);
  const [loading, setLoading] = useState(false);

  const reloadInbox = useCallback(async () => {
    if (!enabled) {
      setPendingProposals([]);
      return;
    }
    setLoading(true);
    try {
      const items = await fetchAiProposals('PENDING');
      setPendingProposals(Array.isArray(items) ? items : []);
    } catch {
      // Keep non-blocking on background refresh errors
    } finally {
      setLoading(false);
    }
  }, [enabled]);

  useEffect(() => {
    reloadInbox();
    const handler = () => reloadInbox();
    window.addEventListener(INBOX_CHANGED_EVENT, handler);
    return () => window.removeEventListener(INBOX_CHANGED_EVENT, handler);
  }, [reloadInbox]);

  return {
    pendingProposals,
    pendingCount: pendingProposals.length,
    loading,
    reloadInbox,
  };
}

export function useAiModalListeners({ onOpenInbox, onOpenAgent }) {
  useEffect(() => {
    const inboxHandler = () => onOpenInbox?.();
    const agentHandler = () => onOpenAgent?.();
    window.addEventListener(OPEN_INBOX_EVENT, inboxHandler);
    window.addEventListener(OPEN_AGENT_EVENT, agentHandler);
    return () => {
      window.removeEventListener(OPEN_INBOX_EVENT, inboxHandler);
      window.removeEventListener(OPEN_AGENT_EVENT, agentHandler);
    };
  }, [onOpenInbox, onOpenAgent]);
}

const TOAST_STYLES = {
  info: 'bg-ink text-white border-ink',
  success: 'bg-completed text-white border-completed',
  warning: 'bg-pending text-ink border-pending',
  error: 'bg-skipped text-white border-skipped',
};

export default function Toast({ message, type = 'info', onDismiss }) {
  if (!message) return null;
  const styles = TOAST_STYLES[type] || TOAST_STYLES.info;

  return (
    <div
      role="status"
      aria-live="polite"
      className={`fixed bottom-5 right-5 z-50 flex items-center gap-3 px-4 py-3 rounded-card shadow-card-hover border text-sm font-medium transition-all duration-base ${styles}`}
    >
      <span>{message}</span>
      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          aria-label="Dismiss notification"
          className="opacity-80 hover:opacity-100 text-xs font-bold"
        >
          ✕
        </button>
      )}
    </div>
  );
}

/**
 * Blue paper-plane brand icon shared by the /login and /welcome entry screens.
 */
export function PaperPlaneIcon({ className = 'h-6 w-6', wrapperClassName = '' }) {
  const icon = (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M22 2L11 13" />
      <path d="M22 2L15 22L11 13L2 9L22 2Z" />
    </svg>
  );

  if (!wrapperClassName) {
    return icon;
  }

  return <span className={wrapperClassName}>{icon}</span>;
}

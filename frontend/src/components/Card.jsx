export default function Card({ children, className = '', padding = 'p-6' }) {
  return (
    <section
      className={`bg-surface rounded-card shadow-card border border-border/70 transition-shadow duration-base ${padding} ${className}`}
    >
      {children}
    </section>
  );
}

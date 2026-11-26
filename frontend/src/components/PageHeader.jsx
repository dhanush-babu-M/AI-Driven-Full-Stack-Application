export default function PageHeader({ title, breadcrumb, rightSlot }) {
  return (
    <header className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        {breadcrumb && (
          <p className="text-xs font-medium uppercase tracking-wider text-muted mb-1">{breadcrumb}</p>
        )}
        <h1 className="text-2xl font-bold text-ink tracking-tight">{title}</h1>
      </div>
      {rightSlot && <div className="flex items-center gap-2">{rightSlot}</div>}
    </header>
  );
}

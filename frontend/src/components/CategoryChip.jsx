import { GROUP_CHIP_STYLES, getCategoryMeta } from '../utils/taskCategories';

export default function CategoryChip({ category, label }) {
  const meta = getCategoryMeta(category || label);
  const text = label || `${meta.group} · ${meta.label}`;
  const styles = GROUP_CHIP_STYLES[meta.group] || GROUP_CHIP_STYLES.Personal;

  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 text-xs font-medium rounded-pill border ${styles}`}
    >
      {text}
    </span>
  );
}

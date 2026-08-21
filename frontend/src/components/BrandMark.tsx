export function BrandMark({ compact = false }: { compact?: boolean }) {
  return (
    <span className={compact ? "brand-mark brand-mark-compact" : "brand-mark"} aria-hidden="true">
      <svg viewBox="0 0 32 32">
        <path d="M7 8.5h11.5a6.5 6.5 0 0 1 0 13H14" />
        <path d="M7 8.5v15" />
        <circle cx="7" cy="8.5" r="2.25" />
        <circle cx="7" cy="23.5" r="2.25" />
        <circle cx="22" cy="15" r="2.25" />
      </svg>
    </span>
  );
}

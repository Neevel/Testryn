export function BrandMark({ compact = false }: { compact?: boolean }) {
  return (
    <span className={compact ? "brand-mark brand-mark-compact" : "brand-mark"} aria-hidden="true">
      <svg viewBox="0 0 32 32">
        <g className="brand-guild">
          <path d="M16 2.5 28.5 10l-4 16H7.5l-4-16Z" />
          <path d="m3.5 10 12.5 5.5L28.5 10M16 2.5v13M7.5 26 16 15.5 24.5 26" />
          <path className="brand-rune" d="m12.2 9.4 3.1 3.2 5.1-6" />
        </g>
        <g className="brand-modern">
          <path d="M7 8.5h11.5a6.5 6.5 0 0 1 0 13H14" />
          <path d="M7 8.5v15" />
          <circle cx="7" cy="8.5" r="2.25" />
          <circle cx="7" cy="23.5" r="2.25" />
          <circle cx="22" cy="15" r="2.25" />
        </g>
      </svg>
    </span>
  );
}

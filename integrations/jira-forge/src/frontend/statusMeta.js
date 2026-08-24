/**
 * Shared status -> {label, lozenge appearance, icon} mapping (Abschnitt 13): every
 * status is always shown as text AND an icon AND a color, never color alone, so the
 * panel stays meaningful without relying on color perception.
 */
const STATUS_META = {
  PASSED: { label: "PASSED", appearance: "success", glyph: "check-circle", color: "color.icon.success" },
  FAILED: { label: "FAILED", appearance: "removed", glyph: "cross-circle", color: "color.icon.danger" },
  BLOCKED: { label: "BLOCKED", appearance: "moved", glyph: "lock-locked", color: "color.icon.warning" },
  SKIPPED: { label: "SKIPPED", appearance: "default", glyph: "arrow-right-circle", color: "color.icon.subtle" },
  NOT_RUN: { label: "NOT RUN", appearance: "default", glyph: "question-circle", color: "color.icon.subtle" },
};

const FALLBACK = { label: "UNKNOWN", appearance: "default", glyph: "question-circle", color: "color.icon.subtle" };

export function statusMeta(status) {
  return STATUS_META[status] ?? FALLBACK;
}

const VARIANT_BY_VALUE: Record<string, "success" | "danger" | "warning" | "info" | undefined> = {
  PASSED: "success",
  COMPLETED: "success",
  ACTIVE: "success",
  FAILED: "danger",
  ABORTED: "danger",
  BLOCKED: "warning",
  SKIPPED: "warning",
  DRAFT: "info",
  RUNNING: "info",
  CREATED: "info",
  NOT_RUN: undefined,
  DEPRECATED: undefined,
};

export function StatusBadge({ value }: { value: string }) {
  const variant = VARIANT_BY_VALUE[value];
  const className = variant ? `badge badge-${variant}` : "badge";
  return <span className={className}>{value.replace(/_/g, " ")}</span>;
}

import type { ReactNode } from "react";

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty-state">
      <div className="empty-visual" aria-hidden="true">
        <span className="empty-node" /><span className="empty-line" /><span className="empty-node empty-node-main" /><span className="empty-line" /><span className="empty-node" />
      </div>
      <div className="empty-title">{title}</div>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

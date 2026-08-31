import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ExecutionsApi, ProjectsApi } from "../api/endpoints";
import type { Execution, Project } from "../api/types";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { EmptyState } from "../components/EmptyState";
import { LoadingState } from "../components/LoadingState";
import { StatusBadge } from "../components/StatusBadge";
import { EntityIcon } from "../components/EntityIcon";
import { summarize } from "./executionSummary";

interface Row {
  execution: Execution;
  projectName: string;
}

export function AllExecutionsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [rows, setRows] = useState<Row[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setError(null);
    ProjectsApi.list()
      .then(async (projects: Project[]) => {
        const perProject = await Promise.all(
          projects.map(async (project) => {
            const executions = await ExecutionsApi.listForProject(project.key);
            return executions.map((execution) => ({ execution, projectName: project.name }));
          }),
        );
        const all = perProject.flat().sort((a, b) => b.execution.createdAt.localeCompare(a.execution.createdAt));
        setRows(all);
      })
      .catch((err) => setError(errorMessage(err)));
  }, []);

  return (
    <div>
      <div className="page-header">
        <div className="title-group">
          <h1>Executions</h1>
        </div>
      </div>
      <p className="page-subtitle" style={{ marginTop: "-0.75rem", marginBottom: "1.5rem" }}>
        Every execution across all projects, most recent first.
      </p>
      <ErrorBanner message={error} />

      {rows === null && !error && <LoadingState label="Loading executions…" />}
      {rows?.length === 0 && (
        <EmptyState title="No executions yet">Start an execution from a test plan to see it here.</EmptyState>
      )}

      {rows && rows.length > 0 && (() => {
        const status = searchParams.get("status");
        const result = searchParams.get("result");
        const visible = rows.filter(({ execution }) => (!status || execution.status === status) && (!result || summarize(execution.testCases).counts[result as keyof ReturnType<typeof summarize>["counts"]] > 0));
        return <>
        <div className="quick-filter-bar"><span>Quick filters</span>{["RUNNING","COMPLETED","ABORTED"].map((value) => <button key={value} className={`filter-chip ${status === value ? "active" : ""}`} onClick={() => setSearchParams(status === value ? {} : { status: value })}>{value.toLowerCase()}</button>)}{["PASSED","FAILED","BLOCKED"].map((value) => <button key={value} className={`filter-chip ${result === value ? "active" : ""}`} onClick={() => setSearchParams(result === value ? {} : { result: value })}>{value.toLowerCase()} results</button>)}<strong>{visible.length} shown</strong></div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Execution</th>
                <th>Project</th>
                <th>Status</th>
                <th>Results</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {visible.map(({ execution, projectName }) => {
                const counts = summarize(execution.testCases).counts;
                return (
                  <tr key={execution.id}>
                    <td>
                      <Link className="entity-link" to={`/executions/${execution.id}`}><EntityIcon kind="execution"/>{execution.name}</Link>
                      <div className="faint">#{execution.iterationNumber}</div>
                    </td>
                    <td>{projectName}</td>
                    <td>
                      <StatusBadge value={execution.status} />
                    </td>
                    <td className="muted">
                      {counts.PASSED} passed · {counts.FAILED} failed · {counts.BLOCKED} blocked ·{" "}
                      {counts.NOT_RUN} not run
                    </td>
                    <td className="muted">{new Date(execution.createdAt).toLocaleString()}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        </>;
      })()}
    </div>
  );
}

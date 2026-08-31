import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ProjectsApi, TestCasesApi, TestPlansApi } from "../api/endpoints";
import type { Project, TestCase, TestCasePriority, TestCaseStatus, TestPlan } from "../api/types";
import { EmptyState } from "../components/EmptyState";
import { ErrorBanner, errorMessage } from "../components/ErrorBanner";
import { LoadingState } from "../components/LoadingState";
import { StatusBadge } from "../components/StatusBadge";
import { EntityIcon, type EntityKind } from "../components/EntityIcon";

type ProjectTestCase = { project: Project; testCase: TestCase };
type ProjectPlan = { project: Project; plan: TestPlan };

function CatalogHeader({ eyebrow, title, subtitle }: { eyebrow: string; title: string; subtitle: string }) {
  const kinds: Record<string, EntityKind> = { Projects: "project", "Test cases": "testCase", "Test plans": "testPlan" };
  return <div className="page-header catalog-header"><div><div className="eyebrow">{eyebrow}</div><h1 className="entity-title"><EntityIcon kind={kinds[title]}/>{title}</h1><p className="page-subtitle">{subtitle}</p></div></div>;
}

export function WorkspaceProjectsPage() {
  const [projects, setProjects] = useState<Project[] | null>(null);
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { ProjectsApi.list().then(setProjects).catch((e) => setError(errorMessage(e))); }, []);
  const visible = projects?.filter((p) => `${p.key} ${p.name} ${p.description ?? ""}`.toLowerCase().includes(query.toLowerCase()));
  return <div><CatalogHeader eyebrow="Workspace" title="Projects" subtitle="Choose the quality workspace that contains your test library, plans and executions."/><div className="catalog-toolbar"><input className="search-input catalog-search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search projects…"/><span className="result-count">{visible?.length ?? 0} projects</span></div><ErrorBanner message={error}/>{projects === null && !error && <LoadingState label="Loading projects…"/>}{visible?.length === 0 && <EmptyState title="No projects found">Try another search.</EmptyState>}<div className="catalog-card-grid">{visible?.map((p) => <Link className="catalog-card" to={`/projects/${p.key}`} key={p.id}><span className="technical-id">{p.key}</span><h2>{p.name}</h2><p>{p.description || "Open the project's test cases, plans and executions."}</p><span className="catalog-card-action">Open project →</span></Link>)}</div></div>;
}

export function WorkspaceTestCasesPage() {
  const [searchParams] = useSearchParams();
  const [items, setItems] = useState<ProjectTestCase[] | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [query, setQuery] = useState("");
  const [projectKey, setProjectKey] = useState("");
  const [status, setStatus] = useState<TestCaseStatus | "">((searchParams.get("status") as TestCaseStatus) || "");
  const [priority, setPriority] = useState<TestCasePriority | "">("");
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { ProjectsApi.list().then(async (ps) => { setProjects(ps); const pages = await Promise.all(ps.map(async (project) => ({ project, page: await TestCasesApi.search(project.key, { size: 100 }) }))); setItems(pages.flatMap(({ project, page }) => page.content.map((testCase) => ({ project, testCase })))); }).catch((e) => setError(errorMessage(e))); }, []);
  const visible = useMemo(() => items?.filter(({ project, testCase: tc }) => (!projectKey || project.key === projectKey) && (!status || tc.status === status) && (!priority || tc.priority === priority) && `${tc.humanId} ${tc.currentVersion?.title ?? ""} ${tc.tags.join(" ")}`.toLowerCase().includes(query.toLowerCase())), [items, projectKey, status, priority, query]);
  return <div><CatalogHeader eyebrow="Test library" title="Test cases" subtitle="Find reusable, versioned tests across all projects."/><div className="catalog-toolbar"><div className="toolbar-filters"><input className="search-input catalog-search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search ID, title or tag…"/><select value={projectKey} onChange={(e) => setProjectKey(e.target.value)}><option value="">All projects</option>{projects.map((p) => <option value={p.key} key={p.id}>{p.name}</option>)}</select><select value={status} onChange={(e) => setStatus(e.target.value as TestCaseStatus | "")}><option value="">All statuses</option><option value="DRAFT">Draft</option><option value="ACTIVE">Active</option><option value="DEPRECATED">Deprecated</option></select><select value={priority} onChange={(e) => setPriority(e.target.value as TestCasePriority | "")}><option value="">All priorities</option><option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option><option value="CRITICAL">Critical</option></select></div><Link className="btn" to={projectKey ? `/projects/${projectKey}?tab=test-cases&create=true` : "/projects"}>New test case</Link></div><div className="active-filter-row"><span>{visible?.length ?? 0} test cases</span>{(query || projectKey || status || priority) && <button className="btn btn-ghost btn-sm" onClick={() => { setQuery(""); setProjectKey(""); setStatus(""); setPriority(""); }}>Clear filters</button>}</div><ErrorBanner message={error}/>{items === null && !error && <LoadingState label="Loading test library…"/>}{visible?.length === 0 && <EmptyState title="No test cases found">Change or clear the active filters.</EmptyState>}{visible && visible.length > 0 && <div className="table-wrap"><table><thead><tr><th>ID</th><th>Title</th><th>Project</th><th>Status</th><th>Priority</th><th>Tags</th><th>Version</th></tr></thead><tbody>{visible.map(({ project, testCase: tc }) => <tr key={tc.id}><td><Link to={`/test-cases/${tc.id}`}>{tc.humanId}</Link></td><td>{tc.currentVersion?.title}</td><td><Link to={`/projects/${project.key}`}>{project.name}</Link></td><td><StatusBadge value={tc.status}/></td><td>{tc.priority}</td><td>{tc.tags.map((tag) => <span className="tag" key={tag}>{tag}</span>)}</td><td>v{tc.currentVersion?.versionNumber}</td></tr>)}</tbody></table></div>}</div>;
}

export function WorkspaceTestPlansPage() {
  const [items, setItems] = useState<ProjectPlan[] | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [query, setQuery] = useState("");
  const [projectKey, setProjectKey] = useState("");
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { ProjectsApi.list().then(async (ps) => { setProjects(ps); const plans = await Promise.all(ps.map(async (project) => ({ project, plans: await TestPlansApi.listForProject(project.key) }))); setItems(plans.flatMap(({ project, plans }) => plans.map((plan) => ({ project, plan })))); }).catch((e) => setError(errorMessage(e))); }, []);
  const visible = items?.filter(({ project, plan }) => (!projectKey || project.key === projectKey) && `${plan.name} ${plan.description ?? ""} ${project.name}`.toLowerCase().includes(query.toLowerCase()));
  return <div><CatalogHeader eyebrow="Planning" title="Test plans" subtitle="Assemble reusable tests, then start a durable execution snapshot."/><div className="catalog-toolbar"><div className="toolbar-filters"><input className="search-input catalog-search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search test plans…"/><select value={projectKey} onChange={(e) => setProjectKey(e.target.value)}><option value="">All projects</option>{projects.map((p) => <option value={p.key} key={p.id}>{p.name}</option>)}</select></div><Link className="btn" to={projectKey ? `/projects/${projectKey}?tab=test-plans` : "/projects"}>New test plan</Link></div><div className="active-filter-row"><span>{visible?.length ?? 0} test plans</span>{(query || projectKey) && <button className="btn btn-ghost btn-sm" onClick={() => { setQuery(""); setProjectKey(""); }}>Clear filters</button>}</div><ErrorBanner message={error}/>{items === null && !error && <LoadingState label="Loading test plans…"/>}{visible?.length === 0 && <EmptyState title="No test plans found">Create a plan in a project or change the filters.</EmptyState>}<div className="catalog-card-grid">{visible?.map(({ project, plan }) => <Link className="catalog-card plan-card" to={`/test-plans/${plan.id}`} key={plan.id}><span className="technical-id">{project.key}</span><h2>{plan.name}</h2><p>{plan.description || "Reusable test plan"}</p><div className="plan-card-meta"><strong>{plan.testCases.length}</strong><span>test cases</span></div><span className="catalog-card-action">Open test plan →</span></Link>)}</div></div>;
}

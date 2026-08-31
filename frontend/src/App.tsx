import { Link, Route, Routes, useLocation } from "react-router-dom";
import { DashboardPage } from "./pages/DashboardPage";
import { ProjectPage } from "./pages/ProjectPage";
import { TestCasePage } from "./pages/TestCasePage";
import { TestPlanPage } from "./pages/TestPlanPage";
import { ExecutionPage } from "./pages/ExecutionPage";
import { AllExecutionsPage } from "./pages/AllExecutionsPage";
import { SettingsPage } from "./pages/SettingsPage";
import { WorkspaceProjectsPage, WorkspaceTestCasesPage, WorkspaceTestPlansPage } from "./pages/WorkspaceCatalogPage";
import { BrandMark } from "./components/BrandMark";
import { Icon } from "./components/Icon";
import { ThemeSelector } from "./components/ThemeSelector";
import { DragonBackdrop } from "./components/DragonBackdrop";

function NavLink({ to, icon, children }: { to: string; icon: "dashboard" | "play" | "settings" | "projects" | "testCases" | "testPlans"; children: React.ReactNode }) {
  const location = useLocation();
  const active = to === "/" ? location.pathname === "/" : location.pathname.startsWith(to);
  return (
    <Link to={to} className={active ? "active" : ""}>
      <Icon name={icon} />
      <span>{children}</span>
    </Link>
  );
}

export default function App() {
  return (
    <div className="app-shell">
      <DragonBackdrop />
      <aside className="app-sidebar">
        <div className="app-brand">
          <BrandMark />
          <span className="wordmark">Testryn<small><span className="fantasy-copy">Quality Guild</span><span className="standard-copy">Quality workspace</span></small></span>
        </div>
        <nav className="sidebar-nav">
          <span className="nav-label"><span className="fantasy-copy">Guild workspace</span><span className="standard-copy">Workspace</span></span>
          <NavLink to="/" icon="dashboard">Dashboard</NavLink>
          <NavLink to="/projects" icon="projects">Projects</NavLink>
          <NavLink to="/test-cases" icon="testCases">Test cases</NavLink>
          <NavLink to="/test-plans" icon="testPlans">Test plans</NavLink>
          <NavLink to="/executions" icon="play">Executions</NavLink>
          <span className="nav-label nav-label-admin"><span className="fantasy-copy">Guild hall</span><span className="standard-copy">Administration</span></span>
          <NavLink to="/settings" icon="settings">Settings</NavLink>
        </nav>
        <div className="sidebar-bottom"><ThemeSelector /><div className="sidebar-footer"><span className="system-dot" /> <span className="fantasy-copy">Guild archive connected</span><span className="standard-copy">Test management connected</span></div></div>
      </aside>
      <main className="app-content">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/executions" element={<AllExecutionsPage />} />
          <Route path="/projects" element={<WorkspaceProjectsPage />} />
          <Route path="/test-cases" element={<WorkspaceTestCasesPage />} />
          <Route path="/test-plans" element={<WorkspaceTestPlansPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/projects/:projectKey" element={<ProjectPage />} />
          <Route path="/test-cases/:id" element={<TestCasePage />} />
          <Route path="/test-plans/:id" element={<TestPlanPage />} />
          <Route path="/executions/:id" element={<ExecutionPage />} />
        </Routes>
      </main>
    </div>
  );
}

import { Link, Route, Routes, useLocation } from "react-router-dom";
import { DashboardPage } from "./pages/DashboardPage";
import { ProjectPage } from "./pages/ProjectPage";
import { TestCasePage } from "./pages/TestCasePage";
import { TestPlanPage } from "./pages/TestPlanPage";
import { ExecutionPage } from "./pages/ExecutionPage";
import { AllExecutionsPage } from "./pages/AllExecutionsPage";
import { SettingsPage } from "./pages/SettingsPage";
import { BrandMark } from "./components/BrandMark";
import { Icon } from "./components/Icon";

function NavLink({ to, icon, children }: { to: string; icon: "dashboard" | "play" | "settings"; children: React.ReactNode }) {
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
      <aside className="app-sidebar">
        <div className="app-brand">
          <BrandMark />
          <span className="wordmark">Testryn<small>Quality workspace</small></span>
        </div>
        <nav className="sidebar-nav">
          <span className="nav-label">Workspace</span>
          <NavLink to="/" icon="dashboard">Dashboard</NavLink>
          <NavLink to="/executions" icon="play">Executions</NavLink>
          <span className="nav-label nav-label-admin">Administration</span>
          <NavLink to="/settings" icon="settings">Settings</NavLink>
        </nav>
        <div className="sidebar-footer"><span className="system-dot" /> Test management, connected</div>
      </aside>
      <main className="app-content">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/executions" element={<AllExecutionsPage />} />
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

import { Link, Route, Routes, useLocation } from "react-router-dom";
import { DashboardPage } from "./pages/DashboardPage";
import { ProjectPage } from "./pages/ProjectPage";
import { TestCasePage } from "./pages/TestCasePage";
import { TestPlanPage } from "./pages/TestPlanPage";
import { ExecutionPage } from "./pages/ExecutionPage";
import { AllExecutionsPage } from "./pages/AllExecutionsPage";
import { SettingsPage } from "./pages/SettingsPage";

function NavLink({ to, children }: { to: string; children: React.ReactNode }) {
  const location = useLocation();
  const active = to === "/" ? location.pathname === "/" : location.pathname.startsWith(to);
  return (
    <Link to={to} className={active ? "active" : ""}>
      {children}
    </Link>
  );
}

export default function App() {
  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div className="app-brand">
          <span className="mark">T</span>
          Testryn
        </div>
        <nav className="sidebar-nav">
          <NavLink to="/">Dashboard</NavLink>
          <NavLink to="/executions">Executions</NavLink>
          <NavLink to="/settings">Settings</NavLink>
        </nav>
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

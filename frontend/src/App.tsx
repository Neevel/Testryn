import { Link, Route, Routes } from "react-router-dom";
import { DashboardPage } from "./pages/DashboardPage";
import { ProjectPage } from "./pages/ProjectPage";
import { TestCasePage } from "./pages/TestCasePage";
import { TestPlanPage } from "./pages/TestPlanPage";
import { ExecutionPage } from "./pages/ExecutionPage";

export default function App() {
  return (
    <div className="app-shell">
      <header className="app-header">
        <Link to="/" className="brand">
          Testryn
        </Link>
        <nav>
          <Link to="/">Dashboard</Link>
        </nav>
      </header>
      <main className="app-main">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/projects/:projectKey" element={<ProjectPage />} />
          <Route path="/test-cases/:id" element={<TestCasePage />} />
          <Route path="/test-plans/:id" element={<TestPlanPage />} />
          <Route path="/executions/:id" element={<ExecutionPage />} />
        </Routes>
      </main>
    </div>
  );
}

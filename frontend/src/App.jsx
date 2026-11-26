import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute, PublicOnlyRoute } from './components/RouteGuards';
import PlannerChooser from './components/PlannerChooser';
import { AuthProvider } from './context/AuthContext';
import AppShell from './layouts/AppShell';
import DailyPlannerPage from './pages/DailyPlannerPage';
import DashboardPage from './pages/DashboardPage';
import LoginPage from './pages/LoginPage';
import MonthlyPlannerPage from './pages/MonthlyPlannerPage';
import PlaceholderPage from './pages/PlaceholderPage';
import WeeklyPlannerPage from './pages/WeeklyPlannerPage';
import WelcomePage from './pages/WelcomePage';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route
          path="/login"
          element={
            <PublicOnlyRoute>
              <LoginPage />
            </PublicOnlyRoute>
          }
        />
        <Route
          path="/welcome"
          element={
            <ProtectedRoute>
              <WelcomePage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/app"
          element={
            <ProtectedRoute>
              <AppShell />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />

          <Route path="planner" element={<PlannerChooser />} />
          <Route path="planner/daily" element={<DailyPlannerPage />} />
          <Route path="planner/weekly" element={<WeeklyPlannerPage />} />
          <Route path="planner/monthly" element={<MonthlyPlannerPage />} />

          <Route
            path="coding"
            element={<PlaceholderPage title="Coding Activity" breadcrumb="Practice & Sync" />}
          />
          <Route
            path="projects"
            element={
              <PlaceholderPage
                title="Projects and Applications"
                breadcrumb="Portfolio & Jobs"
                description="Phase 6 will implement the Projects | Job Applications tabs here."
              />
            }
          />
          <Route
            path="reports/:yearMonth"
            element={<PlaceholderPage title="Monthly Report" breadcrumb="Reports" />}
          />
          <Route
            path="settings"
            element={<PlaceholderPage title="Settings" breadcrumb="Account & Preferences" />}
          />
        </Route>

        <Route path="/" element={<Navigate to="/app/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/app/dashboard" replace />} />
      </Routes>
    </AuthProvider>
  );
}

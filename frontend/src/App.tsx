import { lazy, Suspense } from "react";
import {
  createBrowserRouter,
  Navigate,
  RouterProvider,
} from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Layout from "./components/Layout";

const DashboardPage = lazy(() => import("./pages/DashboardPage"));
const TracesPage = lazy(() => import("./pages/TracesPage"));
const EvaluationsPage = lazy(() => import("./pages/EvaluationsPage"));
const SettingsPage = lazy(() => import("./pages/SettingsPage"));
const ShadowTestsPage = lazy(() => import("./pages/ShadowTestsPage"));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,
      retry: 1,
    },
  },
});

const router = createBrowserRouter([
  {
    path: "/",
    element: <Layout />,
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: "dashboard", element: <DashboardPage /> },
      { path: "traces", element: <TracesPage /> },
      { path: "traces/:traceId", element: <TracesPage /> },
      { path: "evaluations", element: <EvaluationsPage /> },
      { path: "evaluations/:evalId", element: <EvaluationsPage /> },
      { path: "settings", element: <SettingsPage /> },
      { path: "shadow", element: <ShadowTestsPage /> },
      { path: "shadow/:testId", element: <ShadowTestsPage /> },
    ],
  },
]);

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <Suspense
        fallback={
          <div className="flex h-screen items-center justify-center">
            <p className="text-muted-foreground">Loading...</p>
          </div>
        }
      >
        <RouterProvider router={router} />
      </Suspense>
    </QueryClientProvider>
  );
}

export default App;

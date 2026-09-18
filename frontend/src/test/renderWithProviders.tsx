// Test helper: renders a page inside a fresh store, query client and in-memory router.
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { ReactElement } from 'react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { createStore } from '../store';

export function renderWithProviders(page: ReactElement, { path = '/', url }: { path?: string; url?: string } = {}) {
  const store = createStore();
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const result = render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[url ?? path]}>
          <Routes>
            <Route path={path} element={page} />
            <Route path="*" element={<p>navigated away</p>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  );
  return { ...result, store };
}

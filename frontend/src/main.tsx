// React app entry point: store, query client and router providers around <App />.
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import ReactDOM from 'react-dom/client';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './index.css';
import { store } from './store';

const statusOf = (error: unknown) => (error as { response?: { status: number } }).response?.status;
/** No response at all, or nginx saying the backend is not there (yet). */
const booting = (error: unknown) => [undefined, 502, 503, 504].includes(statusOf(error));
const serverSide = (error: unknown) => { const s = statusOf(error); return s === undefined || s >= 500; };

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // A 4xx (not found, validation, rate limited) won't succeed on retry. Transport failures and 5xx
      // do: right after ./start.sh nginx answers 502 until the backend has booted (~15s). A backend that
      // isn't up yet is retried every second for 30s, so the page fills the moment it is; the default
      // doubling delay (1, 2, 4, 8, 16s) left it on "Loading" for up to 15s after the backend was ready.
      // Any other 5xx is a real server error: a few tries, then show it.
      retry: (failureCount, error) => failureCount < (booting(error) ? 30 : 3) && serverSide(error),
      retryDelay: (attempt, error) => (booting(error) ? 1000 : Math.min(1000 * 2 ** attempt, 8000)),
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </Provider>
  </React.StrictMode>,
);

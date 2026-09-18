// Temporary switch between the two candidate themes; delete once one is chosen.
import { useEffect, useState } from 'react';

export type Theme = 'techwear' | 'spec';

const STORAGE_KEY = 'ils-theme';

/** Reads the saved theme. Storage can throw in private windows, so failure means the default. */
export function initialTheme(): Theme {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'spec' ? 'spec' : 'techwear';
  } catch {
    return 'techwear';
  }
}

export default function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(initialTheme);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Not saved; the toggle still works for this page view.
    }
  }, [theme]);

  const next = theme === 'techwear' ? 'spec' : 'techwear';
  return (
    <button
      type="button"
      className="theme-toggle"
      onClick={() => setTheme(next)}
      aria-label={`Switch to the ${next === 'spec' ? 'paper spec-sheet' : 'dark techwear'} theme`}
    >
      <span className="theme-toggle__dot" aria-hidden="true" />
      {theme === 'techwear' ? 'DARK' : 'PAPER'}
    </button>
  );
}

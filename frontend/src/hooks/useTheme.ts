import { useCallback, useState } from 'react';

type Theme = 'dark' | 'light';

/**
 * Theme state lives on <html data-theme> and drives the CSS variables defined
 * in index.html; index.html also applies the saved value before first paint.
 */
export function useTheme(): [Theme, () => void] {
  const [theme, setTheme] = useState<Theme>(
    () => (document.documentElement.dataset.theme === 'light' ? 'light' : 'dark'));

  const toggle = useCallback(() => {
    setTheme(current => {
      const next = current === 'dark' ? 'light' : 'dark';
      if (next === 'light') {
        document.documentElement.dataset.theme = 'light';
      } else {
        delete document.documentElement.dataset.theme;
      }
      localStorage.setItem('tally-theme', next);
      return next;
    });
  }, []);

  return [theme, toggle];
}

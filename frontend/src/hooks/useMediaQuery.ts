// Whether a media query matches, kept in step as the window resizes.
import { useEffect, useState } from 'react';

/** The site's desktop width: index.css switches layouts at 62rem. */
export const DESKTOP = '(min-width: 62.01rem)';
/** A phone: index.css and p3.css switch to one column at 40rem. */
export const PHONE = '(max-width: 40rem)';

export function useMediaQuery(query: string) {
  const [matches, setMatches] = useState(() => matchMedia(query).matches);
  useEffect(() => {
    const list = matchMedia(query);
    const onChange = () => setMatches(list.matches);
    onChange();
    list.addEventListener('change', onChange);
    return () => list.removeEventListener('change', onChange);
  }, [query]);
  return matches;
}

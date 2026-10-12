// Which of a page's sections is being read, for marking it in a contents column.
import { useEffect, useState } from 'react';

/** The last section whose top has passed the upper third of the window; the first one before any has. */
export function useActiveSection(ids: string[]) {
  const [active, setActive] = useState(ids[0] ?? null);
  const key = ids.join(' ');
  useEffect(() => {
    const update = () => {
      let current = ids[0] ?? null;
      for (const id of ids) {
        const el = document.getElementById(id);
        if (el && el.getBoundingClientRect().top < innerHeight / 3) current = id;
      }
      setActive(current);
    };
    update();
    addEventListener('scroll', update, { passive: true });
    return () => removeEventListener('scroll', update);
    // ids is read through key, so a new array with the same ids doesn't re-subscribe
  }, [key]); // eslint-disable-line react-hooks/exhaustive-deps
  return active;
}

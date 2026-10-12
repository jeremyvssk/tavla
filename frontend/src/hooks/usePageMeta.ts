// Sets the page's <title> and meta description while the page is shown (SEO: titles under 60 characters).
import { useEffect } from 'react';

const SITE_TITLE = 'Tavla · Chess, Go and Backgammon';
const SITE_DESCRIPTION = 'Chess, Go and Backgammon equipment for home, club and tournament play.';

function describe(text: string) {
  let tag = document.querySelector<HTMLMetaElement>('meta[name="description"]');
  if (!tag) {
    tag = document.createElement('meta');
    tag.name = 'description';
    document.head.append(tag);
  }
  tag.content = text;
}

/** `title` is the page's own name; " · Tavla" is added when it still fits under 60 characters. */
export function usePageMeta(title: string | null, description?: string) {
  useEffect(() => {
    if (title === null) return;
    document.title = title.length + 8 <= 60 ? `${title} · Tavla` : title.slice(0, 59);
    if (description) describe(description);
    return () => {
      document.title = SITE_TITLE;
      describe(SITE_DESCRIPTION);
    };
  }, [title, description]);
}

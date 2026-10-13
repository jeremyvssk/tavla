// Holds every page's title and description to the SEO rules: titles under 60, descriptions 155-160, all unique.
import { describe, expect, it } from 'vitest';
import { catalogMeta, metaDescription, PAGE_META } from './pageMeta';
import { LEGAL as LEGAL_DOCS } from './legal';

const pages = [...Object.values(PAGE_META), ...LEGAL_DOCS.map((d) => [d.title, d.description] as const)];

describe('page titles and descriptions', () => {
  it.each(pages)('%s: a title under 60 characters and a description of 155 to 160', (title, description) => {
    expect(`${title} · Tavla`.length).toBeLessThan(60);
    expect(description.length).toBeGreaterThanOrEqual(155);
    expect(description.length).toBeLessThanOrEqual(160);
  });

  it('gives every page its own title and description', () => {
    expect(new Set(pages.map((p) => p[0])).size).toBe(pages.length);
    expect(new Set(pages.map((p) => p[1])).size).toBe(pages.length);
  });
});

describe('metaDescription', () => {
  it('pads a short text with the shop offer and cuts a long one at a word', () => {
    const short = metaDescription('A walnut chess board.');
    expect(short.startsWith('A walnut chess board. Buy it at Tavla')).toBe(true);
    const long = metaDescription('word '.repeat(80));
    expect(long.endsWith('word…')).toBe(true);
    for (const d of [short, long, metaDescription(''), metaDescription('x'.repeat(400))]) {
      expect(d.length).toBeGreaterThanOrEqual(155);
      expect(d.length).toBeLessThanOrEqual(160);
    }
  });

  it('describes a search, a category and the whole catalog differently', () => {
    const [searchTitle, search] = catalogMeta('dgt', undefined, 12);
    const [categoryTitle, category] = catalogMeta(undefined, 'Chess clocks', 40);
    expect(searchTitle).toBe('Search: dgt');
    expect(search).toContain('12 results for “dgt”');
    expect(categoryTitle).toBe('Chess clocks');
    expect(category.startsWith('Chess clocks: 40 products')).toBe(true);
    expect(catalogMeta(undefined, undefined, 5)).toEqual([...PAGE_META.catalog]);
    for (const d of [search, category]) expect(d.length).toBeLessThanOrEqual(160);
  });
});

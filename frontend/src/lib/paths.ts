// Readable product addresses: /catalog/{id}/{name-slug}. The id finds the product; the slug is for people and search engines.

/** "DGT 3000 Chess Clock (Black)" → "dgt-3000-chess-clock-black"; accents dropped, at most 80 characters. */
export function slugify(name: string): string {
  return name
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .slice(0, 80)
    .replace(/^-+|-+$/g, '');
}

/**
 * The product page's address. The id stays in it so a renamed product never 404s: an old link carries the
 * old slug, the page still finds the product by its id and replaces the slug with the current one.
 */
export function productPath(p: { id: string; name: string }): string {
  const slug = slugify(p.name);
  return slug ? `/catalog/${p.id}/${slug}` : `/catalog/${p.id}`;
}

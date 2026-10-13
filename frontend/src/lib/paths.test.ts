// Tests for product addresses: readable slugs, and the id kept so old links still work.
import { describe, expect, it } from 'vitest';
import { productPath, slugify } from './paths';

describe('slugify', () => {
  it('keeps letters and digits, joined by single hyphens', () => {
    expect(slugify('DGT 3000 Chess Clock (Black)')).toBe('dgt-3000-chess-clock-black');
    expect(slugify('  Go stones -- 9.2 mm  ')).toBe('go-stones-9-2-mm');
  });

  it('drops accents and caps the length without a trailing hyphen', () => {
    expect(slugify('Échiquier Šahovnica')).toBe('echiquier-sahovnica');
    const long = slugify('walnut board '.repeat(20));
    expect(long.length).toBeLessThanOrEqual(80);
    expect(long.endsWith('-')).toBe(false);
  });
});

describe('productPath', () => {
  it('puts the slug after the id, and leaves it out when the name has no letters', () => {
    expect(productPath({ id: 'abc', name: 'Walnut Board' })).toBe('/catalog/abc/walnut-board');
    expect(productPath({ id: 'abc', name: '★' })).toBe('/catalog/abc');
  });
});

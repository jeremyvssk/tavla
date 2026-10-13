// Tests for photoSrcSet: which photos get narrower copies, and the URLs they point at.
import { describe, expect, it } from 'vitest';
import { photoSrcSet } from './images';

describe('photoSrcSet', () => {
  it('lists the narrower copies of an upload beside the original', () => {
    expect(photoSrcSet('/images/products/0b8f1c2e-1111-4a2b-9c3d-123456789abc.jpg')).toBe(
      '/images/products/0b8f1c2e-1111-4a2b-9c3d-123456789abc-160.jpg 160w, ' +
        '/images/products/0b8f1c2e-1111-4a2b-9c3d-123456789abc-600.jpg 600w, ' +
        '/images/products/0b8f1c2e-1111-4a2b-9c3d-123456789abc-1200.jpg 1200w',
    );
  });

  it("asks Shopify's CDN for each width, keeping the other parameters", () => {
    const set = photoSrcSet('https://cdn.shopify.com/s/files/1/0876/1176/files/go-set.jpg?v=17&width=1000')!;
    expect(set.split(', ')).toEqual([
      'https://cdn.shopify.com/s/files/1/0876/1176/files/go-set.jpg?v=17&width=160 160w',
      'https://cdn.shopify.com/s/files/1/0876/1176/files/go-set.jpg?v=17&width=600 600w',
      'https://cdn.shopify.com/s/files/1/0876/1176/files/go-set.jpg?v=17&width=1200 1200w',
    ]);
  });

  it('gives no srcset for a photo that exists in one size only', () => {
    expect(photoSrcSet('https://www.polishchess.com/images/mini/1000px_CH166.webp')).toBeUndefined();
    // a name that isn't ours can't be assumed to have copies
    expect(photoSrcSet('/images/products/holiday.jpg')).toBeUndefined();
  });
});

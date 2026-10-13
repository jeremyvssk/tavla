// srcset for a product photo: the narrower copies of our own uploads, or the supplier CDN's own resizing.

/** The widths ImageProcessor writes beside every upload (backend ImageProcessor.WIDTHS). */
export const PHOTO_WIDTHS = [160, 600, 1200] as const;

const UPLOAD = /^(\/images\/products\/[0-9a-f-]{36})\.(jpg|png)$/;
const SHOPIFY = /^https:\/\/cdn\.shopify\.com\//;

/**
 * The srcset for a photo, or undefined when only one size exists. Our uploads have abc-160.jpg and so on
 * beside abc.jpg (nginx serves the original for a width that was never written). Shopify's CDN resizes
 * on request through its width parameter. The other supplier serves one 1000 px file, so those photos
 * get no srcset.
 */
export function photoSrcSet(src: string): string | undefined {
  const upload = UPLOAD.exec(src);
  if (upload) {
    return PHOTO_WIDTHS.map((w) => `${upload[1]}-${w}.${upload[2]} ${w}w`).join(', ');
  }
  if (SHOPIFY.test(src)) {
    try {
      const url = new URL(src);
      return PHOTO_WIDTHS.map((w) => {
        url.searchParams.set('width', String(w));
        return `${url.toString()} ${w}w`;
      }).join(', ');
    } catch {
      return undefined;
    }
  }
  return undefined;
}

/** How wide a photo is drawn, for the sizes attribute: the browser picks from srcset by this. */
export const PHOTO_SIZES = {
  /** cart lines, the cart panel, search suggestions, order lines */
  thumb: '80px',
  /** a catalog or home page card: 2 columns on a phone, up to about 290 px on a wide screen */
  tile: '(max-width: 40rem) 50vw, (max-width: 64rem) 33vw, 290px',
  /** the photo in a list-view row */
  row: '160px',
  /** the product page's main photo */
  main: '(max-width: 48rem) 100vw, 600px',
} as const;

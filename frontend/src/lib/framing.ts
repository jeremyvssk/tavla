// Places a supplier photo on its tile from the measurements frame_photos.py stored with it.
import type { CSSProperties } from 'react';

/** Measured per photo by tools/catalog-import/frame_photos.py; that script documents each field. */
export interface ImageFraming {
  ratio: number;
  box: [number, number, number, number];
  bleed: string;
  lift: number;
  print: boolean;
  /** the middle of the product, as fractions of the box; absent on photos measured before it existed */
  focus?: [number, number];
}

/** The furthest a photo is blown up past "whole photo fits the tile", so a small subject stays sharp. */
export const MAX_ZOOM = 2;

/**
 * One rule for every photo. The framed part (`box`) is scaled as large as the tile allows, keeping
 * `pad` of the tile clear on each side, with two exceptions:
 * - a side where the photo is cut goes onto the tile's own edge, so the cut reads as the tile's
 *   crop rather than a hard line through the middle of the tile. Cut on two opposite sides, the
 *   photo fills that direction exactly; cut on all four, it covers the tile and the longer
 *   direction loses its overflow;
 * - a print (book cover, page) keeps all four sides clear and is shown whole.
 * Within that, the framed part is centred, unless one side is cut and the other is not: then it
 * sits against the cut side. Where it overflows the tile, the crop keeps `focus` as near the middle
 * as it can, so a backdrop photo keeps its product rather than the empty middle of the backdrop.
 *
 * `tile` is the tile's width / height. Returns styles for the frame (the framed part's place on the
 * tile, in % of the tile) and for the image inside it (in % of the frame).
 */
export function framePhoto(f: ImageFraming, pad: number, tile = 1): { frame: CSSProperties; img: CSSProperties } {
  const [x0, y0, x1, y1] = f.box;
  const bleed = f.print ? '' : f.bleed;
  // in tile widths: the tile is 1 wide and 1/tile high; w is the whole image's width on the tile
  const axes = [
    { a: 'l', b: 'r', size: x1 - x0, room: 1 },
    { a: 't', b: 'b', size: (y1 - y0) / f.ratio, room: 1 / tile },
  ].map((ax) => ({ ...ax, cutA: bleed.includes(ax.a), cutB: bleed.includes(ax.b) }));

  let atLeast = 0;
  let atMost = Infinity;
  for (const ax of axes) {
    // cut at both ends: exactly fills that direction, no more, or it loses more of the photo than it must
    if (ax.cutA && ax.cutB) {
      atLeast = Math.max(atLeast, ax.room / ax.size);
      atMost = Math.min(atMost, ax.room / ax.size);
    } else if (ax.cutA || ax.cutB) atMost = Math.min(atMost, (ax.room * (1 - pad)) / ax.size);
    else atMost = Math.min(atMost, (ax.room * (1 - 2 * pad)) / ax.size);
  }
  const whole = Math.min(1, f.ratio / tile);
  // when the two clash (cut all round, the photo covers the tile), filling the cut direction wins
  const w = Math.max(atLeast, Math.min(atMost, whole * MAX_ZOOM));

  const [h, v] = axes.map((ax, i) => {
    const length = ax.size * w;
    const focus = f.focus?.[i] ?? 0.5;
    const start = ax.cutA && !ax.cutB ? 0 : ax.cutB && !ax.cutA ? ax.room - length
      : length > ax.room ? Math.min(0, Math.max(ax.room - length, ax.room / 2 - focus * length))
      : (ax.room - length) / 2;
    return { start: start / ax.room, length: length / ax.room };
  });
  const pct = (n: number) => `${+(n * 100).toFixed(3)}%`;
  return {
    frame: { left: pct(h.start), top: pct(v.start), width: pct(h.length), height: pct(v.length) },
    img: {
      width: pct(1 / (x1 - x0)),
      left: pct(-x0 / (x1 - x0)),
      top: pct(-y0 / (y1 - y0)),
      filter: f.lift !== 1 ? `brightness(${f.lift})` : undefined,
    },
  };
}

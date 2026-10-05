// Unit tests for the photo framing rule: uncut photos centred with room, cuts on the tile edge, prints whole.
import { describe, expect, it } from 'vitest';
import type { CSSProperties } from 'react';
import { framePhoto, ImageFraming, MAX_ZOOM } from './framing';

const f = (over: Partial<ImageFraming>): ImageFraming => ({ ratio: 1, box: [0, 0, 1, 1], bleed: '', lift: 1, print: false, ...over });
const num = (v: unknown) => parseFloat(String(v));
// the frame's four edges as fractions of the tile
const edges = (frame: CSSProperties) => {
  const [l, t, w, h] = [frame.left, frame.top, frame.width, frame.height].map(num).map((n) => n / 100);
  return { l, t, r: l + w, b: t + h, w, h };
};
const close = (a: number, b: number) => expect(a).toBeCloseTo(b, 4);

describe('framePhoto', () => {
  it('centres an uncut product and grows it to the padding on its longer side', () => {
    // a 3:2 photo whose subject is the middle half of the width and a third of the height
    const e = edges(framePhoto(f({ ratio: 1.5, box: [0.25, 0.3, 0.75, 0.63] }), 0.08).frame);
    close(e.l, 0.08);
    close(e.r, 0.92);
    close(e.t, 1 - e.b);
  });

  it('puts a cut bottom on the tile edge and keeps room on the other sides', () => {
    const e = edges(framePhoto(f({ ratio: 1.5, box: [0.1, 0.2, 0.9, 0.9], bleed: 'b' }), 0.08).frame);
    close(e.b, 1);
    expect(e.t).toBeGreaterThanOrEqual(0.08 - 1e-9);
    close(e.l, 1 - e.r);
  });

  it('fills the width when cut left and right, even if that needs more than the padding allows', () => {
    const e = edges(framePhoto(f({ ratio: 1.5, box: [0.1, 0.1, 0.9, 0.9], bleed: 'lr' }), 0.08).frame);
    close(e.l, 0);
    close(e.r, 1);
  });

  it('covers the tile when cut on all four sides, centred, cropping the longer direction', () => {
    const e = edges(framePhoto(f({ ratio: 1.5, bleed: 'tblr' }), 0.08).frame);
    close(e.t, 0);
    close(e.b, 1);
    close(e.w, 1.5);
    close(e.l, -0.25);
  });

  it('crops a covering photo towards its focus, never past the photo edge', () => {
    // a 3:2 photo whose product sits low and right: the overflow is horizontal only
    const e = edges(framePhoto(f({ ratio: 1.5, bleed: 'tblr', focus: [0.8, 0.9] }), 0.08).frame);
    close(e.r, 1); // 0.5 - 0.8 * 1.5 would leave a gap on the right; clamped to the photo edge
    close(e.t, 0);
    const mid = edges(framePhoto(f({ ratio: 1.5, bleed: 'tblr', focus: [0.6, 0.5] }), 0.08).frame);
    close(mid.l, 0.5 - 0.6 * 1.5);
  });

  it('shows a print whole with room all round, ignoring its cuts', () => {
    const e = edges(framePhoto(f({ ratio: 0.7, bleed: 'tblr', print: true }), 0.08).frame);
    close(e.t, 0.08);
    close(e.b, 0.92);
    expect(e.l).toBeGreaterThan(0.08);
  });

  it('does not blow a small subject up past MAX_ZOOM', () => {
    const { img } = framePhoto(f({ box: [0.45, 0.45, 0.55, 0.55] }), 0.08);
    // the whole image fits the tile at width 1, so it is never drawn wider than MAX_ZOOM tiles
    const frameWidth = num(framePhoto(f({ box: [0.45, 0.45, 0.55, 0.55] }), 0.08).frame.width) / 100;
    close(frameWidth * (num(img.width) / 100), MAX_ZOOM);
  });

  it('places the image so the framed part fills the frame exactly', () => {
    const { img } = framePhoto(f({ box: [0.2, 0.1, 0.7, 0.6] }), 0.08);
    close(num(img.width), 200);
    close(num(img.left), -40);
    close(num(img.top), -20);
  });

  it('frames to a wide tile: room is measured against its own height', () => {
    const e = edges(framePhoto(f({}), 0.1, 2).frame);
    close(e.t, 0.1);
    close(e.b, 0.9);
    close(e.w, 0.4); // 0.8 of the height, which is half the width
  });

  it('brightens only a photo with an off-white ground', () => {
    expect(framePhoto(f({ lift: 1.05 }), 0.08).img.filter).toBe('brightness(1.05)');
    expect(framePhoto(f({}), 0.08).img.filter).toBeUndefined();
  });
});

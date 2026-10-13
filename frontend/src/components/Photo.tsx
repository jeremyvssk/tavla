// A product photo placed on its tile by framePhoto; a photo with no framing (an uploaded one) is shown whole.
import type { ImgHTMLAttributes } from 'react';
import { framePhoto, ImageFraming } from '../lib/framing';
import { PHOTO_SIZES, photoSrcSet } from '../lib/images';

type Props = ImgHTMLAttributes<HTMLImageElement> & {
  src: string;
  framing: ImageFraming | null | undefined;
  /** share of the tile kept clear on each uncut side */
  pad: number;
  /** the tile's width / height, when it is not square */
  tile?: number;
  /** how wide the photo is drawn, so the browser fetches a fitting copy (lib/images.ts) */
  size?: keyof typeof PHOTO_SIZES;
};

// The tile must be position:relative with overflow:hidden; .ph-fr and .ph-whole in index.css do the rest.
export default function Photo({ src, framing, pad, tile = 1, size = 'thumb', alt = '', ...img }: Props) {
  const srcSet = photoSrcSet(src);
  const sizes = srcSet ? PHOTO_SIZES[size] : undefined;
  if (!framing) {
    return <img className="ph-whole" src={src} srcSet={srcSet} sizes={sizes} alt={alt} style={{ padding: `${pad * 100}%` }} {...img} />;
  }
  const { frame, img: style } = framePhoto(framing, pad, tile);
  return (
    <span className="ph-fr" style={frame} data-print={framing.print ? '' : undefined}>
      <img src={src} srcSet={srcSet} sizes={sizes} alt={alt} style={style} {...img} />
    </span>
  );
}

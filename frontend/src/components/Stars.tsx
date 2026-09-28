// A rating as five amber stars, with a text equivalent for screen readers. With `count` it is a product's average.
import { Star } from './Icon';

export default function Stars({ rating, count }: { rating: number | null; count?: number }) {
  if (rating === null || count === 0) {
    return <span className="stars-none">No reviews yet</span>;
  }
  const label = count === undefined
    ? `Rated ${rating} out of 5`
    : `Rated ${rating.toFixed(1)} out of 5 from ${count} reviews`;
  return (
    <span className="pd-rate" role="img" aria-label={label}>
      <span className="stars" aria-hidden="true">
        {/* a quarter-star short still counts as a full star, as in the design */}
        {[1, 2, 3, 4, 5].map((i) => <Star key={i} off={rating < i - 0.25} />)}
      </span>
      <span aria-hidden="true">
        {count === undefined ? `${rating} / 5` : `${rating.toFixed(1)} · ${count} ${count === 1 ? 'review' : 'reviews'}`}
      </span>
    </span>
  );
}

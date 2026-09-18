// A rating as five squares, with a text equivalent for screen readers. With `count` it is a product's average.
export default function Stars({ rating, count }: { rating: number | null; count?: number }) {
  if (rating === null || count === 0) {
    return <span className="stars stars--none">No reviews</span>;
  }
  const filled = Math.round(rating);
  const label = count === undefined
    ? `Rated ${rating} out of 5`
    : `Rated ${rating.toFixed(1)} out of 5 from ${count} reviews`;
  return (
    <span className="stars" aria-label={label}>
      <span aria-hidden="true">
        {[1, 2, 3, 4, 5].map((i) => (
          <span key={i} className={i <= filled ? 'stars__unit stars__unit--on' : 'stars__unit'} />
        ))}
      </span>
      <span aria-hidden="true" className="stars__count">
        {count === undefined ? `${rating}/5` : `${rating.toFixed(1)} (${count})`}
      </span>
    </span>
  );
}

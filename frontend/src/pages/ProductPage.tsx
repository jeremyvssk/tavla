// Product detail: breadcrumb, specs in metric and imperial, attributes, paged reviews and a review form.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchProduct, fetchReviews, postReview, ProductDetail } from '../api/catalog';
import { toApiError } from '../api/errors';
import Field from '../components/Field';
import Stars from '../components/Stars';
import { formatPrice } from '../lib/format';
import { useAppSelector } from '../store';

export default function ProductPage() {
  const { id = '' } = useParams();
  const { data: product, error, isPending } = useQuery({ queryKey: ['product', id], queryFn: () => fetchProduct(id) });

  if (isPending) return <p className="status-line">Loading product…</p>;
  if (error || !product) {
    return (
      <div className="empty">
        <p className="form-error" role="alert">{toApiError(error).message}</p>
        <Link to="/catalog">Back to the catalog</Link>
      </div>
    );
  }

  const primary = product.images.find((i) => i.primary) ?? product.images[0];
  return (
    <article className="product">
      <nav className="breadcrumb" aria-label="Breadcrumb">
        <Link to="/catalog">Catalog</Link>
        {product.breadcrumb.map((c) => (
          <span key={c.id}>
            {' / '}
            <Link to={`/catalog?category=${c.slug}`}>{c.name}</Link>
          </span>
        ))}
      </nav>

      <div className="product__media">
        {primary ? (
          <img src={primary.url} alt={primary.altText ?? product.name} />
        ) : (
          <span className="card__placeholder" aria-hidden="true">{product.category.name}</span>
        )}
      </div>

      <div className="product__info">
        <p className="label">{product.brand?.name ?? 'Unbranded'}</p>
        <h1 className="title">{product.name}</h1>
        <Stars rating={product.averageRating} count={product.reviewCount} />
        <p className="product__price">{formatPrice(product.price)}</p>
        <p className={product.inStock ? 'tag tag--ok' : 'tag'}>
          {product.inStock ? `In stock · ${product.stockQuantity} left` : 'Out of stock'}
        </p>
        {product.description && <p className="product__description">{product.description}</p>}
        <Specs product={product} />
      </div>

      <Reviews productId={product.id} />
    </article>
  );
}

function Specs({ product }: { product: ProductDetail }) {
  const m = product.measurements;
  const dims = (a: number | null, b: number | null, c: number | null, unit: string) =>
    a && b && c ? `${a} × ${b} × ${c} ${unit}` : null;
  const rows: [string, string | null][] = [
    ['Weight', m.weightKg ? `${m.weightKg} kg / ${m.weightLbs} lb` : null],
    ['Size (W × H × D)', dims(m.widthCm, m.heightCm, m.depthCm, 'cm')],
    ['Size, imperial', dims(m.widthIn, m.heightIn, m.depthIn, 'in')],
    ...Object.entries(product.attributes ?? {}).map(
      ([key, value]): [string, string] => [key.replace(/_/g, ' '), Array.isArray(value) ? value.join(', ') : String(value)],
    ),
  ];
  return (
    <dl className="spec-list">
      {rows.filter(([, value]) => value !== null).map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function Reviews({ productId }: { productId: string }) {
  const [page, setPage] = useState(0);
  const status = useAppSelector((s) => s.auth.status);
  const { data } = useQuery({
    queryKey: ['reviews', productId, page],
    queryFn: () => fetchReviews(productId, page),
  });

  return (
    <section className="reviews" aria-labelledby="reviews-title">
      <h2 id="reviews-title" className="panel__title">Reviews</h2>
      {status === 'authenticated' ? (
        <ReviewForm productId={productId} onPosted={() => setPage(0)} />
      ) : (
        status === 'anonymous' && <p><Link to="/login" state={{ from: `/catalog/${productId}` }}>Sign in</Link> to write a review.</p>
      )}
      {data?.items.length === 0 && <p>No reviews yet.</p>}
      <ul className="review-list">
        {data?.items.map((r) => (
          <li key={r.id} className="review">
            <Stars rating={r.rating} />
            <h3 className="review__title">{r.title}</h3>
            <p>{r.body}</p>
            <p className="field__hint">
              {new Date(r.createdAt).toLocaleDateString()}
              {r.verifiedPurchase && ' · Verified purchase'}
            </p>
          </li>
        ))}
      </ul>
      {data && data.totalPages > 1 && (
        <nav className="pager" aria-label="Review pages">
          <button type="button" className="button button--ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</button>
          <span className="pager__status">{page + 1} / {data.totalPages}</span>
          <button type="button" className="button button--ghost" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Older</button>
        </nav>
      )}
    </section>
  );
}

// Mirrors ReviewRequest: rating 1-5, title 1-255, body 1-5000.
function ReviewForm({ productId, onPosted }: { productId: string; onPosted: () => void }) {
  const queryClient = useQueryClient();
  const [rating, setRating] = useState(0);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [errors, setErrors] = useState<{ rating?: string; title?: string; body?: string }>({});

  const mutation = useMutation({
    mutationFn: () => postReview(productId, { rating, title: title.trim(), body: body.trim() }),
    onSuccess: () => {
      setRating(0);
      setTitle('');
      setBody('');
      onPosted();
      // The trigger updated the product's average, so both the product and its reviews are stale.
      queryClient.invalidateQueries({ queryKey: ['reviews', productId] });
      queryClient.invalidateQueries({ queryKey: ['product', productId] });
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    const found = {
      rating: rating >= 1 && rating <= 5 ? undefined : 'Choose a rating.',
      title: !title.trim() ? 'Add a title.' : title.length > 255 ? 'Title must be at most 255 characters.' : undefined,
      body: !body.trim() ? 'Write a few words.' : body.length > 5000 ? 'Review must be at most 5000 characters.' : undefined,
    };
    setErrors(found);
    if (Object.values(found).some(Boolean)) return;
    mutation.mutate();
  }

  return (
    <form className="panel review-form" onSubmit={submit} noValidate>
      <fieldset className="rating-input">
        <legend className="field__label">Rating</legend>
        {[1, 2, 3, 4, 5].map((n) => (
          <label key={n} className="rating-input__option">
            <input type="radio" name="rating" value={n} checked={rating === n} onChange={() => setRating(n)} />
            {n}
          </label>
        ))}
        {errors.rating && <p className="field__error">{errors.rating}</p>}
      </fieldset>
      <Field label="Title" name="title" maxLength={255} value={title} error={errors.title}
        onChange={(e) => setTitle(e.target.value)} />
      <div className="field">
        <label className="field__label" htmlFor="review-body">Review</label>
        <textarea id="review-body" className="field__input" rows={4} maxLength={5000} value={body}
          aria-invalid={errors.body ? true : undefined} onChange={(e) => setBody(e.target.value)} />
        {errors.body && <p className="field__error">{errors.body}</p>}
      </div>
      {mutation.error && <p className="form-error" role="alert">{toApiError(mutation.error).message}</p>}
      <button className="button" type="submit" disabled={mutation.isPending}>
        {mutation.isPending ? 'Posting…' : 'Post review'}
      </button>
    </form>
  );
}

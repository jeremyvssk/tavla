// Product detail: photo gallery with a Back button with a zoom viewer, colour/size variants, specs in both unit systems, paged reviews.
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, MouseEvent, PointerEvent, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { fetchProduct, fetchReviews, postReview, ProductDetail, ProductVariant, searchProducts } from '../api/catalog';
import { toApiError } from '../api/errors';
import Field from '../components/Field';
import Icon, { Perks } from '../components/Icon';
import Photo from '../components/Photo';
import ProductRow from '../components/ProductRow';
import Stars from '../components/Stars';
import { formatPrice } from '../lib/format';
import { axesOf, variantFor } from '../lib/variants';
import { useAppDispatch, useAppSelector } from '../store';
import { added, LINE_LIMIT, opened } from '../store/cartSlice';

export default function ProductPage() {
  const { id = '' } = useParams();
  const { data: product, error, isPending } = useQuery({ queryKey: ['product', id], queryFn: () => fetchProduct(id) });

  useEffect(() => {
    if (product) document.title = `${product.name} · Tavla`;
    return () => { document.title = 'Tavla · Chess, Go and Backgammon'; };
  }, [product]);

  if (isPending) return <div className="wrap page"><p className="status-line">Loading product…</p></div>;
  if (error || !product) {
    return (
      <div className="wrap page">
        <div className="empty">
          <p className="t-eyebrow">Not found</p>
          <h2>That product is not on the shelf.</h2>
          <p className="form-error" role="alert">{toApiError(error).message}</p>
          <Link className="btn" to="/catalog">Browse all products</Link>
        </div>
      </div>
    );
  }

  const stock = !product.inStock ? <span className="stock out">Out of stock</span>
    : product.stockQuantity < 10 ? <span className="stock low">Only {product.stockQuantity} left</span>
    : <span className="stock">In stock</span>;

  return (
    <>
      <article className="wrap pd">
        {/* keyed by product so switching variant starts again at its first photo */}
        <Gallery key={product.id} product={product} />

        {/* the order a shopper reads in: can I have it, is it good, who makes it, what is it, what does it cost, which one */}
        <div className="pd-info">
          <div className="pd-top">
            {stock}
            <a href="#reviews-title" className="pd-top__rate"><Stars rating={product.averageRating} count={product.reviewCount} /></a>
          </div>
          <p className="by">
            {product.brand ? <Link to={`/catalog?brand=${product.brand.slug}`}>{product.brand.name}</Link> : 'Tavla'}
          </p>
          <h1>{product.name}</h1>
          <p className="pd-price">{formatPrice(product.price)}</p>
          <Variants product={product} />
          <Buy key={product.id} product={product} />
          <div className="pd-perks"><Perks /></div>
          {product.description && <p className="pd-desc">{product.description}</p>}
          <Specs product={product} />
        </div>
      </article>

      <div className="wrap">
        <Reviews productId={product.id} />
      </div>
      <Related product={product} />
    </>
  );
}

// Images arrive primary first, then in display order. They are hotlinked from the suppliers, so a
// photo can vanish from their site at any time: one that fails to load drops out of the gallery.
// The photos sit in a native scroll-snap track, so a swipe follows the finger; the arrows, thumbnails and
// the viewer move the same track, and its scroll position decides which photo counts as shown.
function Gallery({ product }: { product: ProductDetail }) {
  const [shown, setAt] = useState(0);
  const [viewer, setViewer] = useState(false);
  const [broken, setBroken] = useState<ReadonlySet<number>>(new Set());
  const track = useRef<HTMLDivElement>(null);
  // the photo an arrow or thumbnail is gliding to: the scroll events on the way would otherwise report the
  // photos it passes, and a second quick click would step from one of those
  const target = useRef<number | null>(null);
  const navigate = useNavigate();
  const location = useLocation();
  const images = product.images.filter((img) => !broken.has(img.id));
  const at = Math.min(shown, Math.max(0, images.length - 1));
  const drop = (id: number) => setBroken((b) => new Set(b).add(id));
  const shelf = `/catalog?category=${product.category.slug}`;
  // the stage is square unless a short window caps its height; the photos are framed to its real shape
  const [tile, setTile] = useState(1);
  const hasImages = images.length > 0;
  useEffect(() => {
    const t = track.current;
    if (!t) return;
    const watch = new ResizeObserver(() => t.clientHeight && setTile(t.clientWidth / t.clientHeight));
    watch.observe(t);
    return () => watch.disconnect();
  }, [hasImages]);

  // Back returns to wherever the shopper came from; opened from a link elsewhere, it goes to the shelf.
  const back = (
    <button className="bk" type="button" aria-label="Back"
      onClick={() => (location.key !== 'default' ? navigate(-1) : navigate(shelf))}>
      <Icon name="chevL" width={2.2} />
    </button>
  );

  if (images.length === 0) {
    return (
      <div className="pd-media">
        <div className="stage stage--none">{back}<span aria-hidden="true">{product.category.name}</span></div>
      </div>
    );
  }
  const fine = () => matchMedia('(hover:hover) and (pointer:fine)').matches;
  function go(i: number, behavior: ScrollBehavior = 'smooth') {
    const t = track.current;
    if (!t) return;
    const n = (i + images.length) % images.length;
    // a glide only to the photo next door: a wrap-around or a far thumbnail would sweep past every photo
    // in between, and reduced motion gets no glide at all
    if (Math.abs(n - at) > 1 || matchMedia('(prefers-reduced-motion: reduce)').matches) behavior = 'instant';
    // already there: no scroll event will come to clear the target
    target.current = Math.abs(t.scrollLeft - n * t.clientWidth) < 2 ? null : n;
    t.scrollTo({ left: n * t.clientWidth, behavior });
    setAt(n);
  }

  return (
    <div className="pd-media">
      <div className="stage" role="group" aria-roledescription="gallery" aria-label={`Photos of ${product.name}`}>
        <div className="track" ref={track}
          onScroll={(e) => {
            const t = e.currentTarget;
            if (target.current !== null) {
              if (Math.abs(t.scrollLeft - target.current * t.clientWidth) < 2) target.current = null;
              return;
            }
            setAt(Math.round(t.scrollLeft / t.clientWidth));
          }}
          // a finger on the photo takes over from any glide still running
          onPointerDown={() => { target.current = null; }}
          onClick={() => setViewer(true)}>
          {images.map((img, i) => (
            <div className="slide" key={img.id} aria-hidden={i !== at}>
              <Photo src={img.url} framing={img.framing} pad={0.06} tile={tile} alt={img.altText ?? product.name} draggable={false}
                loading={i === 0 ? 'eager' : 'lazy'} onError={() => drop(img.id)} />
            </div>
          ))}
        </div>
        {back}
        {images.length > 1 && (
          <>
            <span className="cnt">{at + 1} / {images.length}</span>
            <button className="nv l" type="button" aria-label="Previous photo" onClick={() => go(at - 1)}><Icon name="chevL" width={2.2} /></button>
            <button className="nv r" type="button" aria-label="Next photo" onClick={() => go(at + 1)}><Icon name="arrow" width={2.2} /></button>
            <div className="dots" aria-hidden="true">
              {images.map((img, i) => <i key={img.id} data-on={i === at ? '' : undefined} />)}
            </div>
          </>
        )}
      </div>
      {images.length > 1 && (
        <div className="thumbs" role="group" aria-label="Choose a photo">
          {images.map((img, i) => (
            <button key={img.id} type="button" aria-label={`Photo ${i + 1}`} aria-current={i === at}
              onClick={() => go(i)}
              // pointing at a thumbnail shows it, as on most large shops; a click still works for touch and keyboard
              onPointerEnter={(e) => e.pointerType === 'mouse' && fine() && go(i, 'instant')}>
              <Photo src={img.url} framing={img.framing} pad={0.08} loading="lazy" onError={() => drop(img.id)} />
            </button>
          ))}
        </div>
      )}
      {/* the viewer moves the track instantly behind itself, so closing it lands on the photo last seen */}
      {viewer && <Viewer name={product.name} images={images} at={at} onStep={(i) => go(i, 'instant')} onPick={(i) => go(i, 'instant')}
        onClose={() => setViewer(false)} />}
    </div>
  );
}

// Full-screen photos. A click zooms 2.5x around the point clicked and the photo pans with the pointer.
// Opening pushes one history entry, so the browser's Back button closes the viewer instead of leaving.
function Viewer({ name, images, at, onStep, onPick, onClose }: {
  name: string; images: ProductDetail['images']; at: number;
  onStep: (i: number) => void; onPick: (i: number) => void; onClose: () => void;
}) {
  const [zoom, setZoom] = useState(false);
  const [origin, setOrigin] = useState('50% 50%');
  const imgRef = useRef<HTMLImageElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const base = useRef<DOMRect | null>(null);
  const pushed = useRef(false);

  // the latest callbacks, so the listeners below are attached once
  const live = useRef({ at, onStep, onClose, zoom });
  live.current = { at, onStep, onClose, zoom };

  useEffect(() => {
    const before = document.activeElement as HTMLElement | null;
    closeRef.current?.focus();
    document.documentElement.style.overflow = 'hidden';
    try {
      history.pushState({ ...history.state, viewer: true }, '');
      pushed.current = true;
    } catch {
      // no history entry; Back simply leaves the page as before
    }
    const onPop = () => { pushed.current = false; live.current.onClose(); };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopImmediatePropagation();
        if (live.current.zoom) setZoom(false);
        else close();
      }
      if (e.key === 'ArrowLeft') live.current.onStep(live.current.at - 1);
      if (e.key === 'ArrowRight') live.current.onStep(live.current.at + 1);
    };
    addEventListener('popstate', onPop);
    addEventListener('keydown', onKey, true);
    return () => {
      removeEventListener('popstate', onPop);
      removeEventListener('keydown', onKey, true);
      document.documentElement.style.overflow = '';
      before?.focus({ preventScroll: true });
    };
  }, []);

  useEffect(() => setZoom(false), [at]);

  function close() {
    if (pushed.current) {
      pushed.current = false;
      history.back(); // the popstate listener closes the viewer
    } else live.current.onClose();
  }

  // the point under the pointer stays under it: the origin is measured in the unzoomed box
  function aim(e: MouseEvent | PointerEvent) {
    const b = base.current ?? imgRef.current!.getBoundingClientRect();
    base.current = b;
    const x = Math.min(100, Math.max(0, ((e.clientX - b.left) / b.width) * 100));
    const y = Math.min(100, Math.max(0, ((e.clientY - b.top) / b.height) * 100));
    setOrigin(`${x}% ${y}%`);
  }

  const image = images[at];
  const fine = matchMedia('(hover:hover) and (pointer:fine)').matches;
  return (
    <div className="lb" role="dialog" aria-modal="true" aria-label="Photos" data-zoom={zoom ? '' : undefined}>
      <div className="lb-top">
        <div>
          {name} <span>{at + 1} / {images.length}</span>
          <span className="lb-hint">{fine ? 'Click the photo to zoom' : 'Tap the photo to zoom'}</span>
        </div>
        <button type="button" ref={closeRef} aria-label="Close photos" onClick={close}><Icon name="close" width={2} /></button>
      </div>
      <div className="lb-img" onClick={(e) => e.target === e.currentTarget && close()}>
        <img ref={imgRef} src={image.url} alt={image.altText ?? name} draggable={false}
          style={{ transformOrigin: zoom ? origin : undefined }}
          onClick={(e) => {
            base.current = null;
            if (!zoom) aim(e);
            setZoom(!zoom);
          }}
          onPointerMove={(e) => {
            if (!zoom || (e.pointerType !== 'mouse' && !e.buttons)) return;
            aim(e);
          }} />
        {images.length > 1 && (
          <>
            <button className="nv l" type="button" aria-label="Previous photo" onClick={() => onStep(at - 1)}><Icon name="chevL" width={2.2} /></button>
            <button className="nv r" type="button" aria-label="Next photo" onClick={() => onStep(at + 1)}><Icon name="arrow" width={2.2} /></button>
          </>
        )}
      </div>
      <div className="lb-strip">
        {images.length > 1 && images.map((img, i) => (
          <button key={img.id} type="button" aria-label={`Photo ${i + 1}`} aria-current={i === at} onClick={() => onPick(i)}>
            <Photo src={img.url} framing={img.framing} pad={0.08} loading="lazy" />
          </button>
        ))}
      </div>
    </div>
  );
}

// Add to cart, one at a time: the cart panel it opens has the quantity stepper, so the page keeps one
// clear button. On a phone the button rides the bottom of the screen until the page scrolls to its place.
function Buy({ product }: { product: ProductDetail }) {
  const dispatch = useAppDispatch();
  const inCart = useAppSelector((s) => s.cart.lines.find((l) => l.productId === product.id)?.quantity ?? 0);
  // stops where the cart would: stock and the per-line limit, minus what is already in the cart
  const room = Math.max(0, Math.min(product.stockQuantity, LINE_LIMIT) - inCart);

  if (!product.inStock) {
    return <div className="pd-buy"><button type="button" className="btn btn--buy" disabled>Out of stock</button></div>;
  }

  function add() {
    const current = product.variants.find((v) => v.id === product.id);
    dispatch(added({
      productId: product.id,
      name: product.name,
      brandName: product.brand?.name ?? null,
      options: current && product.variants.length > 1
        ? Object.entries(current.options).map(([axis, value]) => `${axis}: ${value}`).join(', ') : null,
      imageUrl: product.images[0]?.url ?? null,
      imageFraming: product.images[0]?.framing ?? null,
      price: product.price,
      quantity: 1,
      maxQuantity: product.stockQuantity,
    }));
    dispatch(opened());
  }

  return (
    <div className="pd-buy">
      <button type="button" className="btn btn--buy" disabled={room === 0} onClick={add}>
        <Icon name="bag" width={2} />{room === 0 ? 'All in your cart' : 'Add to cart'}
      </button>
    </div>
  );
}

// One row of options per axis (Colour, Size, Pieces...); each option links to the sibling it selects.
function Variants({ product }: { product: ProductDetail }) {
  const current = product.variants.find((v) => v.id === product.id);
  if (!current || product.variants.length < 2) return null;
  const axes = axesOf(product.variants);
  return (
    <div className="vo">
      {axes.map(([axis, values]) => (
        <fieldset key={axis}>
          {/* one axis names itself ("Blue", "Brown"); with two, "Colour" and "Size" tell the rows apart */}
          <legend className={axes.length === 1 ? 'vh' : undefined}>{axis}: <b>{current.options[axis]}</b></legend>
          <div className="vsw-row">
            {values.map((value) => {
              const target = variantFor(product.variants, current, axis, value) as ProductVariant;
              const on = value === current.options[axis];
              return (
                <Link key={value} to={`/catalog/${target.id}`} replace
                  className={target.inStock ? 'vch' : 'vch oos'}
                  aria-current={on ? true : undefined}
                  aria-label={target.inStock ? value : `${value}, out of stock`}>
                  {value}
                </Link>
              );
            })}
          </div>
        </fieldset>
      ))}
    </div>
  );
}

// variant_group and variant drive the selector above; they are not specs.
const HIDDEN_ATTRIBUTES = new Set(['variant_group', 'variant']);
const SPEC_LABELS: Record<string, string> = {
  king_height_mm: 'King height', pieces_material: 'Pieces', board_material: 'Board', board_size_in: 'Board size',
  board_size: 'Board size', stones: 'Stones', wood: 'Wood', language: 'Language', format: 'Format',
};
const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

function specValue(key: string, value: unknown) {
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (Array.isArray(value)) return cap(value.join(' and '));
  if (key.endsWith('_mm')) return `${value} mm`;
  if (key.endsWith('_cm')) return `${value} cm`;
  if (key.endsWith('_in')) return `${value} in`;
  return cap(String(value));
}

function Specs({ product }: { product: ProductDetail }) {
  const m = product.measurements;
  const dims = (a: number | null, b: number | null, c: number | null) => [a, b, c].filter((v) => v !== null);
  const cm = dims(m.widthCm, m.heightCm, m.depthCm);
  const inch = dims(m.widthIn, m.heightIn, m.depthIn);
  const attributes = Object.entries(product.attributes ?? {}).filter(([key]) => !HIDDEN_ATTRIBUTES.has(key));
  if (!attributes.length && !cm.length && !m.weightKg) return null;
  return (
    <dl className="specs">
      {attributes.map(([key, value]) => (
        <div key={key}>
          <dt>{SPEC_LABELS[key] ?? cap(key.replace(/_/g, ' '))}</dt>
          <dd>{specValue(key, value)}</dd>
        </div>
      ))}
      {cm.length > 0 && (
        <div>
          <dt>Size (W × H × D)</dt>
          <dd>{cm.join(' × ')} cm{inch.length > 0 && <small>{inch.join(' × ')} in</small>}</dd>
        </div>
      )}
      {m.weightKg !== null && (
        <div>
          <dt>Weight</dt>
          <dd>{m.weightKg} kg{m.weightLbs !== null && <small>{m.weightLbs} lb</small>}</dd>
        </div>
      )}
    </dl>
  );
}

// Related products, as a row like the home page's: the same shelf first, then the shelf above it,
// because a small shelf (a handful of clocks) cannot fill a row on its own.
function Related({ product }: { product: ProductDetail }) {
  const shelf = product.category;
  const parent = product.breadcrumb.length > 1 ? product.breadcrumb[product.breadcrumb.length - 2] : null;
  const sources = useQueries({
    queries: [shelf, parent].filter((c) => c !== null).map((c) => ({
      queryKey: ['products', `category=${c.slug}&sort=rating&size=16`],
      queryFn: () => searchProducts({ category: c.slug, sort: 'rating', size: '16' }),
      staleTime: 5 * 60_000,
    })),
  });
  const [near, wide] = sources.map((s) => s.data);
  // one card per product name, so the colours of this very product don't fill the row; memoised so
  // the row keeps one array and its motion wiring between renders
  const items = useMemo(() => {
    const seen = new Set([product.name]);
    return [near, wide].flatMap((d) => d?.items ?? []).filter((p) => !seen.has(p.name) && seen.add(p.name)).slice(0, 10);
  }, [product.name, near, wide]);
  if (!items.length) return null;
  const top = product.breadcrumb[0] ?? shelf;
  return (
    <>
      <div className="t-rule" aria-hidden="true" />
      <div className="wrap">
        <ProductRow id="rel-h" title="Related products" items={items}
          more={{ label: <>Shop all<br />{top.name}</>, to: `/catalog?category=${top.slug}` }} />
      </div>
    </>
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
    <section className="reviews t-sec" aria-labelledby="reviews-title">
      <div className="t-head"><h2 id="reviews-title">Reviews</h2></div>
      {status === 'authenticated' ? (
        <ReviewForm productId={productId} onPosted={() => setPage(0)} />
      ) : (
        status === 'anonymous' && <p><Link className="link" to="/login" state={{ from: `/catalog/${productId}` }}>Sign in</Link> to write a review.</p>
      )}
      {data?.items.length === 0 && <p className="status-line">No reviews yet.</p>}
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
          <button type="button" className="btn btn--ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</button>
          <span className="pager__status">{page + 1} / {data.totalPages}</span>
          <button type="button" className="btn btn--ghost" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Older</button>
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

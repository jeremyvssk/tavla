// Header search: as you type, completed searches, shelves to narrow to and four products, as one keyboard-navigable combobox.
import { useQuery } from '@tanstack/react-query';
import { FormEvent, KeyboardEvent, useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { fetchSuggestions, searchProducts } from '../api/catalog';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { completions } from '../lib/completions';
import { formatPrice } from '../lib/format';
import Icon from './Icon';
import Photo from './Photo';

// The backend returns nothing under two characters, so there is no point asking.
const MIN_LENGTH = 2;
// Fires once the typing pauses: a word typed at normal speed is one request, not one per key,
// which keeps an active shopper far under the 120-per-minute limit on /search/suggestions.
const DEBOUNCE_MS = 250;

type Option = { kind: 'search'; text: string } | { kind: 'product'; id: string };

export default function SearchBox() {
  const [searchParams] = useSearchParams();
  const [text, setText] = useState(searchParams.get('q') ?? '');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const navigate = useNavigate();
  const listId = useId();
  const form = useRef<HTMLFormElement>(null);

  const query = useDebouncedValue(text.trim(), DEBOUNCE_MS);
  const enabled = query.length >= MIN_LENGTH;
  const { data: names = [] } = useQuery({
    queryKey: ['suggestions', query],
    queryFn: () => fetchSuggestions(query),
    enabled,
    staleTime: 60_000,
  });
  const { data: found } = useQuery({
    queryKey: ['quick', query],
    queryFn: () => searchProducts({ q: query, size: '4' }),
    enabled,
    staleTime: 60_000,
  });
  const words = enabled ? completions(query, names.map((n) => n.name)) : [];
  const products = enabled ? found?.items ?? [] : [];
  const shelves = enabled ? (found?.facets.categories ?? []).slice().sort((a, b) => b.count - a.count).slice(0, 3) : [];
  const options: Option[] = [
    ...words.map((w) => ({ kind: 'search' as const, text: w.text })),
    ...products.map((p) => ({ kind: 'product' as const, id: p.id })),
  ];
  const visible = open && enabled && (options.length > 0 || found !== undefined);

  // Keep the box in step with the URL, e.g. after the back button or a facet link.
  useEffect(() => {
    setText(searchParams.get('q') ?? '');
  }, [searchParams]);

  useEffect(() => setActive(-1), [names, found]);

  // On a phone the panel is a sheet fixed from just under the field to the bottom of the window.
  useLayoutEffect(() => {
    const f = form.current;
    if (visible && f) f.style.setProperty('--sg-top', `${Math.round(f.getBoundingClientRect().bottom + 8)}px`);
  }, [visible]);

  const go = (to: string) => {
    setOpen(false);
    navigate(to);
  };
  const search = (q: string) => go(q ? `/catalog?q=${encodeURIComponent(q)}` : '/catalog');

  function choose(option: Option) {
    if (option.kind === 'search') {
      setText(option.text);
      search(option.text);
    } else {
      // a product page has no ?q= for the box to mirror, so the box empties
      setText('');
      go(`/catalog/${option.id}`);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (visible && active >= 0) choose(options[active]);
    else search(text.trim());
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' && options.length) {
      event.preventDefault();
      setOpen(true);
      setActive((i) => (i + 1) % options.length);
    } else if (event.key === 'ArrowUp' && options.length) {
      event.preventDefault();
      setActive((i) => (i <= 0 ? options.length - 1 : i - 1));
    } else if (event.key === 'Escape') {
      setOpen(false);
    }
  }

  // mousedown would blur the input first and close the panel before the click lands
  const keepFocus = (e: React.MouseEvent) => e.preventDefault();
  const optionProps = (i: number) => ({
    id: `${listId}-${i}`,
    role: 'option',
    'aria-selected': i === active,
    'data-on': i === active ? '' : undefined,
    onMouseDown: keepFocus,
    onClick: () => choose(options[i]),
  });
  const total = found?.totalItems ?? 0;

  return (
    <form className="t-search" ref={form} role="search" autoComplete="off" onSubmit={submit}>
      <Icon name="search" width={2} />
      <input
        type="search"
        placeholder="Search sets, boards, stones, clocks"
        aria-label="Search products"
        role="combobox"
        aria-expanded={visible}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={visible && active >= 0 ? `${listId}-${active}` : undefined}
        enterKeyHint="search"
        value={text}
        maxLength={200}
        onChange={(e) => {
          setText(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        // Delay so a click on a suggestion lands before the list disappears.
        onBlur={() => setTimeout(() => setOpen(false), 120)}
        onKeyDown={onKeyDown}
      />
      {text && (
        <button className="clr" type="button" aria-label="Clear search" onClick={() => setText('')}>
          <Icon name="close" width={2} />
        </button>
      )}
      {/* The listbox holds only the options, its two groups placed in the panel's grid; the shelves and
          "All results" are plain links beside it. */}
      <div className="sgp" data-on={visible ? '' : undefined} data-products={products.length ? '' : undefined}>
        <div className="sgl" id={listId} role="listbox" aria-label="Suggestions">
          {words.length > 0 && (
            <div className="sgw" role="group" aria-label="Searches">
              <h3 aria-hidden="true">Searches</h3>
              <ul role="none">
                {words.map((w, i) => (
                  <li key={w.text} className="sgr" {...optionProps(i)}>
                    <Icon name="search" width={2} />
                    <span>{w.parts.map((p, j) => (p.typed ? <span key={j}>{p.text}</span> : <b key={j}>{p.text}</b>))}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {products.length > 0 && (
            <div className="prods" role="group" aria-label="Products">
              <h3 aria-hidden="true">Products</h3>
              <ul role="none">
                {products.map((p, i) => (
                  <li key={p.id} className="sgprod" {...optionProps(words.length + i)}>
                    <span className="th">
                      {p.primaryImageUrl && <Photo src={p.primaryImageUrl} framing={p.primaryImageFraming} pad={0.06} />}
                    </span>
                    <span className="nm">{p.name}</span>
                    <b className="pz">{formatPrice(p.price)}</b>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
        {shelves.length > 0 && (
          <div className="sgscope">
            <span>Narrow to</span>
            {shelves.map((s) => (
              <Link key={s.slug} className="pill" onMouseDown={keepFocus} onClick={() => setOpen(false)}
                to={`/catalog?q=${encodeURIComponent(query)}&category=${s.slug}`}>
                {s.name} <small>{s.count}</small>
              </Link>
            ))}
          </div>
        )}
        {found && (
          <Link className="all" to={`/catalog?q=${encodeURIComponent(query)}`} onMouseDown={keepFocus} onClick={() => setOpen(false)}>
            {total === 0 ? <>Nothing matches “{query}”. Browse everything</> : <>All {total} {total === 1 ? 'result' : 'results'}</>}
            <Icon name="arrow" width={2} />
          </Link>
        )}
      </div>
    </form>
  );
}

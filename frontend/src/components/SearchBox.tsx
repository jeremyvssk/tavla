// Header search: debounced live suggestions as a keyboard-navigable combobox; Enter searches the catalog.
import { useQuery } from '@tanstack/react-query';
import { FormEvent, KeyboardEvent, useEffect, useId, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { fetchSuggestions } from '../api/catalog';
import { useDebouncedValue } from '../hooks/useDebouncedValue';

// The backend returns nothing under two characters, so there is no point asking.
const MIN_LENGTH = 2;
// Fires once the typing pauses: a word typed at normal speed is one request, not one per key,
// which keeps an active shopper far under the 120-per-minute limit on /search/suggestions.
const DEBOUNCE_MS = 250;

export default function SearchBox() {
  const [searchParams] = useSearchParams();
  const [text, setText] = useState(searchParams.get('q') ?? '');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const navigate = useNavigate();
  const listId = useId();

  const query = useDebouncedValue(text.trim(), DEBOUNCE_MS);
  const { data: suggestions = [] } = useQuery({
    queryKey: ['suggestions', query],
    queryFn: () => fetchSuggestions(query),
    enabled: query.length >= MIN_LENGTH,
    staleTime: 60_000,
  });
  const visible = open && query.length >= MIN_LENGTH && suggestions.length > 0;

  // Keep the box in step with the URL, e.g. after the back button or a facet link.
  useEffect(() => {
    setText(searchParams.get('q') ?? '');
  }, [searchParams]);

  useEffect(() => setActive(-1), [suggestions]);

  function submit(event: FormEvent) {
    event.preventDefault();
    setOpen(false);
    if (active >= 0 && visible) {
      navigate(`/catalog/${suggestions[active].id}`);
      return;
    }
    const q = text.trim();
    navigate(q ? `/catalog?q=${encodeURIComponent(q)}` : '/catalog');
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' && suggestions.length) {
      event.preventDefault();
      setOpen(true);
      setActive((i) => (i + 1) % suggestions.length);
    } else if (event.key === 'ArrowUp' && suggestions.length) {
      event.preventDefault();
      setActive((i) => (i <= 0 ? suggestions.length - 1 : i - 1));
    } else if (event.key === 'Escape') {
      setOpen(false);
    }
  }

  return (
    <form className="search" role="search" onSubmit={submit}>
      <input
        className="search__input"
        type="search"
        placeholder="Search"
        aria-label="Search products"
        role="combobox"
        aria-expanded={visible}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={visible && active >= 0 ? `${listId}-${active}` : undefined}
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
      <ul id={listId} role="listbox" className="search__list" hidden={!visible}>
        {suggestions.map((s, i) => (
          <li
            key={s.id}
            id={`${listId}-${i}`}
            role="option"
            aria-selected={i === active}
            className="search__option"
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => {
              setOpen(false);
              navigate(`/catalog/${s.id}`);
            }}
          >
            {s.name}
          </li>
        ))}
      </ul>
    </form>
  );
}

// Admin categories: the shelf tree (drag to reorder, switch to hide) beside a form that adds or edits one shelf.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CSSProperties, DragEvent, FormEvent, KeyboardEvent, useEffect, useState } from 'react';
import { createCategory, deleteCategory, fetchStaffCategories, moveCategory, StaffCategory, updateCategory } from '../../api/admin';
import { toApiError } from '../../api/errors';
import Icon from '../../components/Icon';
import { AdminHead, FieldError, invalid, LoadState, useConfirm } from '../parts';

const KEY = ['staff', 'categories'];
const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const slugify = (s: string) => s.toLowerCase().normalize('NFKD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

type Row = { node: StaffCategory; depth: number; path: string; parent: StaffCategory | null };

/** Every shelf in tree order, with its depth and "Chess › Chess sets" path. */
function flatten(nodes: StaffCategory[], depth = 0, path = '', parent: StaffCategory | null = null): Row[] {
  return nodes.flatMap((node) => {
    const here = path ? `${path} › ${node.name}` : node.name;
    return [{ node, depth, path: here, parent }, ...flatten(node.children, depth + 1, here, node)];
  });
}
const inside = (node: StaffCategory, id: number): boolean => node.children.some((c) => c.id === id || inside(c, id));

export default function CategoriesPage() {
  const client = useQueryClient();
  const q = useQuery({ queryKey: KEY, queryFn: fetchStaffCategories, retry: false });
  const tree = q.data?.tree ?? [];
  const rows = flatten(tree);
  const [sel, setSel] = useState<number | 'new'>('new');
  // shelves start folded; the first one, and the path to whatever is picked, open
  const [opened, setOpened] = useState<Set<number>>(new Set());
  useEffect(() => {
    if (tree[0] && !opened.size) setOpened(new Set([tree[0].id]));
  }, [tree]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (sel === 'new') return;
    const up: number[] = [];
    for (let r = rows.find((x) => x.node.id === sel); r?.parent; r = rows.find((x) => x.node.id === r!.parent!.id)) up.push(r.parent.id);
    if (up.some((id) => !opened.has(id))) setOpened((s) => new Set([...s, ...up]));
  }, [sel, rows]); // eslint-disable-line react-hooks/exhaustive-deps
  const [dragged, setDragged] = useState<StaffCategory | null>(null);
  const [over, setOver] = useState<{ id: number; below: boolean } | null>(null);
  const changed = () => {
    client.invalidateQueries({ queryKey: KEY });
    client.invalidateQueries({ queryKey: ['categories'] });
  };
  const toggle = useMutation({
    mutationFn: (n: StaffCategory) => updateCategory(n.id, { name: n.name, slug: n.slug, parentId: n.parentId, active: !n.active }),
    onSuccess: changed,
  });
  const move = useMutation({ mutationFn: (m: { id: number; parentId: number | null; beforeId: number | null }) => moveCategory(m.id, m.parentId, m.beforeId), onSuccess: changed });
  const error = toggle.error ?? move.error;
  const picked = sel === 'new' ? null : rows.find((r) => r.node.id === sel) ?? null;

  /** Puts `node` just before or after `target`, among the target's siblings. */
  function place(node: StaffCategory, target: Row, below: boolean) {
    if (node.id === target.node.id || inside(node, target.node.id)) return;
    const siblings = target.parent ? target.parent.children : tree;
    const at = siblings.findIndex((s) => s.id === target.node.id);
    const before = below ? siblings.slice(at + 1).find((s) => s.id !== node.id) ?? null : target.node;
    move.mutate({ id: node.id, parentId: target.parent?.id ?? null, beforeId: before?.id ?? null });
  }
  function onGripKey(e: KeyboardEvent<HTMLButtonElement>, r: Row) {
    if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
    e.preventDefault();
    const siblings = r.parent ? r.parent.children : tree;
    const at = siblings.findIndex((s) => s.id === r.node.id);
    const other = siblings[at + (e.key === 'ArrowUp' ? -1 : 1)];
    if (other) place(r.node, { node: other, depth: r.depth, path: '', parent: r.parent }, e.key === 'ArrowDown');
  }
  function onDragOver(e: DragEvent<HTMLDivElement>, r: Row) {
    if (!dragged || dragged.id === r.node.id || inside(dragged, r.node.id)) return;
    e.preventDefault();
    const box = e.currentTarget.getBoundingClientRect();
    setOver({ id: r.node.id, below: e.clientY > box.top + box.height / 2 });
  }
  function onDrop(r: Row) {
    if (dragged && over) place(dragged, r, over.below);
    setDragged(null);
    setOver(null);
  }

  function branch(nodes: StaffCategory[]) {
    return nodes.map((node) => {
      const r = rows.find((x) => x.node.id === node.id)!;
      const open = node.children.length > 0 && opened.has(node.id);
      const cls = [sel === node.id && 'is-sel', dragged?.id === node.id && 'dragging', over?.id === node.id && (over.below ? 'drop-below' : 'drop-above')]
        .filter(Boolean).join(' ');
      return (
        <li key={node.id}>
          <div className={cls || undefined} style={{ '--d': r.depth } as CSSProperties} draggable={dragged?.id === node.id}
            onDragStart={(e) => { e.dataTransfer.effectAllowed = 'move'; }} onDragOver={(e) => onDragOver(e, r)} onDrop={() => onDrop(r)}
            onDragEnd={() => { setDragged(null); setOver(null); }}>
            <button className="grip" type="button" aria-label={`Move ${node.name}: arrow up or down`}
              onPointerDown={() => setDragged(node)} onPointerUp={() => !over && setDragged(null)} onKeyDown={(e) => onGripKey(e, r)}>
              <Icon name="grip" />
            </button>
            <button className="tname" type="button" aria-current={sel === node.id || undefined} onClick={() => setSel(node.id)}>
              <b>{node.name}</b> <span className="slug">{node.slug}</span>
            </button>
            <small>{node.productCount >= 0 && node.productCount.toLocaleString('en')}</small>
            <label className="sw">
              <input type="checkbox" checked={node.active} disabled={toggle.isPending} onChange={() => toggle.mutate(node)} />
              <i aria-hidden="true" /><span className="vh">{node.name} shown in the shop</span>
            </label>
            {node.children.length > 0 ? (
              <button className="chevb" type="button" aria-expanded={open} aria-label={`Shelves in ${node.name}`}
                onClick={() => setOpened((s) => { const n = new Set(s); if (n.has(node.id)) n.delete(node.id); else n.add(node.id); return n; })}>
                <Icon name={open ? 'down' : 'chevR'} width={2} />
              </button>
            ) : <span />}
          </div>
          {open && <ul className="kids">{branch(node.children)}</ul>}
        </li>
      );
    });
  }

  return (
    <main className="adm-body">
      <AdminHead title="Categories" sub="Drag to reorder or pick where a shelf sits. Counts include the shelves below.">
        <button className="btn" type="button" onClick={() => setSel('new')}>Add a category</button>
      </AdminHead>
      <LoadState query={q} what="the categories" />
      {error && <p className="msg msg--bad adm-err" role="alert"><Icon name="alert" />{toApiError(error).message}</p>}
      {q.data && (
        <div className="adm-2col">
          <section className="apanel" aria-label="Category tree">
            <ul className="tree">{branch(tree)}</ul>
          </section>
          <ShelfForm key={picked?.node.id ?? 'new'} row={picked} rows={rows} onSaved={(id) => { changed(); if (id !== undefined) setSel(id); }} onDeleted={() => { changed(); setSel('new'); }} />
        </div>
      )}
    </main>
  );
}

function ShelfForm({ row, rows, onSaved, onDeleted }: { row: Row | null; rows: Row[]; onSaved: (id?: number) => void; onDeleted: () => void }) {
  const n = row?.node;
  const [ask, confirmDialog] = useConfirm();
  const [name, setName] = useState(n?.name ?? '');
  const [slug, setSlug] = useState(n?.slug ?? '');
  // a new shelf's address follows its name until someone types one
  const [ownSlug, setOwnSlug] = useState(!!n);
  const [parentId, setParentId] = useState<number | null>(n?.parentId ?? null);
  const [active, setActive] = useState(n?.active ?? true);
  const [errors, setErrors] = useState<{ name?: string; slug?: string }>({});
  const [said, setSaid] = useState<string | null>(null);
  useEffect(() => { if (!ownSlug) setSlug(slugify(name)); }, [name, ownSlug]);

  const save = useMutation({
    mutationFn: () => n
      ? updateCategory(n.id, { name: name.trim(), slug, parentId, active }).then(() => n.id)
      : createCategory({ name: name.trim(), slug, parentId }).then((c) => c.id),
    onSuccess: (id) => { setSaid(n ? 'Saved.' : 'Added.'); onSaved(id); },
  });
  const remove = useMutation({ mutationFn: () => deleteCategory(n!.id), onSuccess: onDeleted });
  const fieldErrors = save.error ? toApiError(save.error).fields : {};

  function submit(e: FormEvent) {
    e.preventDefault();
    setSaid(null);
    const found = {
      name: name.trim().length < 2 || name.trim().length > 60 ? 'A name of 2 to 60 characters' : undefined,
      slug: !SLUG.test(slug) ? 'Lowercase letters, digits and single hyphens' : undefined,
    };
    setErrors(found);
    if (!found.name && !found.slug) save.mutate();
  }

  // a shelf can't sit under itself or anything below it
  const parents = rows.filter((r) => !n || (r.node.id !== n.id && !inside(n, r.node.id)));
  const why = !n ? null
    : n.productCount > 0 ? `Can't delete a category with products in it. Move its ${n.productCount.toLocaleString('en')} ${n.productCount === 1 ? 'product' : 'products'} first.`
    : n.children.length > 0 ? "Can't delete a category with shelves under it. Move or delete them first."
    : null;

  return (
    <section className="apanel" aria-labelledby="ce-h">
      <div className="panel-h">
        <h2 id="ce-h">{n ? n.name : 'New category'}</h2>
        {n && n.productCount >= 0 && <span className="mute">{n.productCount.toLocaleString('en')} {n.productCount === 1 ? 'product' : 'products'}</span>}
      </div>
      <form className="sec-in shelf" noValidate onSubmit={submit}>
        <div className="f">
          <label htmlFor="cn">Name</label>
          <input className="in" id="cn" value={name} maxLength={60} {...invalid('cn', errors.name ?? fieldErrors.name)} onChange={(e) => setName(e.target.value)} />
          <FieldError id="cn" error={errors.name ?? fieldErrors.name} />
        </div>
        <div className="f">
          <label htmlFor="cs">Address</label>
          <div className="fw fw--q">
            <input className="in" id="cs" value={slug} {...invalid('cs', errors.slug ?? fieldErrors.slug, 'cs-h')}
              onChange={(e) => { setOwnSlug(true); setSlug(e.target.value.toLowerCase()); }} />
            <span className="unit" aria-hidden="true">?category=</span>
          </div>
          <FieldError id="cs" error={errors.slug ?? fieldErrors.slug} />
          <p className="hint" id="cs-h">{n ? 'Changing it breaks links people saved. Old addresses should forward.' : 'Made from the name until you change it.'}</p>
        </div>
        <div className="f">
          <label htmlFor="cp">Sits under</label>
          <div className="fw">
            <select className="in" id="cp" value={parentId ?? ''} onChange={(e) => setParentId(e.target.value ? Number(e.target.value) : null)}>
              <option value="">Top level</option>
              {parents.map((r) => <option key={r.node.id} value={r.node.id}>{r.path}</option>)}
            </select>
            <Icon name="down" width={2.4} />
          </div>
        </div>
        {n && (
          <label className="sw shelf-sw">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} /><i aria-hidden="true" />Shown in the shop
          </label>
        )}
        <div aria-live="polite" className="foot-end">
          {said && <p className="msg msg--ok"><Icon name="check" width={2.4} />{said}</p>}
          {(save.error || remove.error) && !Object.keys(fieldErrors).length && (
            <p className="msg msg--bad" role="alert"><Icon name="alert" />{toApiError(save.error ?? remove.error).message}</p>
          )}
          {why && <p className="hint" id="del-why">{why}</p>}
        </div>
        <div className="formfoot">
          {n && (
            <button className="btn btn--line-danger btn--sm" type="button" disabled={!!why || remove.isPending} aria-describedby={why ? 'del-why' : undefined}
              onClick={async () => (await ask({ title: `Delete ${n.name}?`, body: <p>This can't be undone.</p>, action: 'Delete', danger: true })) && remove.mutate()}>
              Delete
            </button>
          )}
          <button className="btn" type="submit" disabled={save.isPending}>{save.isPending ? 'Saving…' : n ? 'Save' : 'Add the category'}</button>
        </div>
      </form>
      {confirmDialog}
    </section>
  );
}

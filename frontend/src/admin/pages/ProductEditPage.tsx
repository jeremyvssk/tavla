// Add or edit a product: every field ProductRequest takes, photos, specs as rows, variants, and the search listing preview.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { createProduct, deleteImage, deleteProduct, fetchBrands, ProductInput, saveProduct, uploadImage } from '../../api/admin';
import { CategoryNode, fetchCategories, fetchProduct, ProductDetail } from '../../api/catalog';
import { toApiError } from '../../api/errors';
import Icon from '../../components/Icon';
import Photo from '../../components/Photo';
import { AdminHead } from '../parts';

// attribute keys this page edits through its own fields; every other key is a row under "Details"
const OWN = ['supplier_sku', 'ean', 'variant_group', 'variant', 'seo_title', 'seo_description'];
export const TITLE_MAX = 60;
export const META_MAX = 160;

interface Form {
  name: string;
  description: string;
  price: string;
  stock: string;
  sku: string;
  ean: string;
  categoryId: string;
  brandId: string;
  active: boolean;
  weightKg: string;
  widthCm: string;
  heightCm: string;
  depthCm: string;
  /** raw: the value as stored, kept as is (a number stays a number) unless the text is edited */
  details: { key: string; value: string; raw?: unknown }[];
  group: string;
  variant: string;
  seoTitle: string;
  seoDescription: string;
}

const EMPTY: Form = {
  name: '', description: '', price: '', stock: '0', sku: '', ean: '', categoryId: '', brandId: '', active: true,
  weightKg: '', widthCm: '', heightCm: '', depthCm: '', details: [], group: '', variant: '', seoTitle: '', seoDescription: '',
};

function fromProduct(p: ProductDetail): Form {
  const a = (p.attributes ?? {}) as Record<string, unknown>;
  const str = (v: unknown) => (v === null || v === undefined ? '' : typeof v === 'object' ? JSON.stringify(v) : String(v));
  const num = (v: number | null) => (v === null ? '' : String(v));
  return {
    name: p.name, description: p.description ?? '', price: p.price.toFixed(2), stock: String(p.stockQuantity),
    sku: str(a.supplier_sku), ean: str(a.ean), categoryId: String(p.category.id), brandId: p.brand ? String(p.brand.id) : '',
    // PLANNED: the product API doesn't send `active` yet, so a hidden product reads as shown here
    active: true, weightKg: num(p.measurements.weightKg), widthCm: num(p.measurements.widthCm),
    heightCm: num(p.measurements.heightCm), depthCm: num(p.measurements.depthCm),
    details: Object.entries(a).filter(([k]) => !OWN.includes(k)).map(([key, value]) => ({ key, value: str(value), raw: value })),
    group: str(a.variant_group),
    variant: a.variant && typeof a.variant === 'object' ? Object.entries(a.variant as Record<string, string>).map(([k, v]) => `${k}: ${v}`).join(', ') : '',
    seoTitle: str(a.seo_title), seoDescription: str(a.seo_description),
  };
}

type Errors = Partial<Record<keyof Form, string>>;

function validate(f: Form): Errors {
  const e: Errors = {};
  const n = (s: string) => Number(s.replace(',', '.'));
  if (!f.name.trim()) e.name = 'Give the product a name.';
  else if (f.name.length > 255) e.name = 'Keep the name under 255 characters.';
  if (f.description.length > 10_000) e.description = 'Keep the description under 10,000 characters.';
  if (!f.price.trim() || !/^\d+([.,]\d{1,2})?$/.test(f.price.trim())) e.price = 'A price like 97.00, with at most two decimals.';
  if (!/^\d+$/.test(f.stock.trim())) e.stock = 'A whole number, 0 or more.';
  if (!f.categoryId) e.categoryId = 'Choose where it lives in the shop.';
  if (f.ean.trim() && !/^\d{8}(\d{5})?$/.test(f.ean.trim())) e.ean = 'An EAN is 8 or 13 digits.';
  for (const k of ['weightKg', 'widthCm', 'heightCm', 'depthCm'] as const) {
    if (f[k].trim() && !(n(f[k]) > 0)) e[k] = 'A number above 0, or leave it empty.';
  }
  if (f.variant.trim() && !f.variant.split(',').every((p) => /^[^:]+:[^:]+$/.test(p.trim()))) e.variant = 'Like "Colour: Blue, Size: 19\\"".';
  if (f.seoTitle.length > TITLE_MAX) e.seoTitle = `Search engines cut titles after about ${TITLE_MAX} characters.`;
  if (f.seoDescription.length > META_MAX) e.seoDescription = `Search engines cut descriptions after about ${META_MAX} characters.`;
  if (f.details.length + 7 > 30) e.details = 'At most 30 details, the server\'s limit.';
  return e;
}

function toInput(f: Form): ProductInput {
  const n = (s: string) => (s.trim() ? Number(s.replace(',', '.')) : null);
  const same = (d: Form['details'][number]) => d.raw !== undefined && d.value === (typeof d.raw === 'object' ? JSON.stringify(d.raw) : String(d.raw));
  const attributes: Record<string, unknown> = Object.fromEntries(f.details.filter((d) => d.key.trim()).map((d) => [d.key.trim(), same(d) ? d.raw : d.value.trim()]));
  if (f.sku.trim()) attributes.supplier_sku = f.sku.trim();
  if (f.ean.trim()) attributes.ean = f.ean.trim();
  if (f.group.trim()) attributes.variant_group = f.group.trim();
  if (f.variant.trim()) attributes.variant = Object.fromEntries(f.variant.split(',').map((p) => p.split(':').map((x) => x.trim())));
  if (f.seoTitle.trim()) attributes.seo_title = f.seoTitle.trim();
  if (f.seoDescription.trim()) attributes.seo_description = f.seoDescription.trim();
  return {
    name: f.name.trim(), description: f.description.trim() || null, price: Number(f.price.replace(',', '.')),
    stockQuantity: Number(f.stock), categoryId: Number(f.categoryId), brandId: f.brandId ? Number(f.brandId) : null,
    attributes: Object.keys(attributes).length ? attributes : null, active: f.active,
    weightKg: n(f.weightKg), widthCm: n(f.widthCm), heightCm: n(f.heightCm), depthCm: n(f.depthCm),
  };
}

function flatten(nodes: CategoryNode[], depth = 0): { id: number; name: string; depth: number }[] {
  return nodes.flatMap((c) => [{ id: c.id, name: c.name, depth }, ...flatten(c.children, depth + 1)]);
}

export default function ProductEditPage() {
  const { id } = useParams();
  const isNew = !id;
  const navigate = useNavigate();
  const client = useQueryClient();
  const product = useQuery({ queryKey: ['product', id], queryFn: () => fetchProduct(id!), enabled: !isNew });
  const { data: tree = [] } = useQuery({ queryKey: ['categories'], queryFn: fetchCategories, staleTime: 5 * 60_000 });
  const { data: brands = [] } = useQuery({ queryKey: ['brands'], queryFn: fetchBrands, staleTime: 5 * 60_000 });
  const [form, setForm] = useState<Form>(EMPTY);
  const [loadedFor, setLoadedFor] = useState<string | null>(null);
  const [errors, setErrors] = useState<Errors>({});
  const [said, setSaid] = useState<string | null>(null);
  const [alt, setAlt] = useState('');

  useEffect(() => {
    if (product.data && loadedFor !== product.data.id) { setForm(fromProduct(product.data)); setLoadedFor(product.data.id); }
  }, [product.data, loadedFor]);

  const initial = useMemo(() => (product.data ? fromProduct(product.data) : EMPTY), [product.data]);
  const dirty = JSON.stringify(form) !== JSON.stringify(initial);
  const set = <K extends keyof Form>(k: K, v: Form[K]) => setForm((f) => ({ ...f, [k]: v }));

  const save = useMutation({
    mutationFn: () => (isNew ? createProduct(toInput(form)) : saveProduct(id!, toInput(form))),
    onSuccess: (r: { id: string }) => {
      client.invalidateQueries({ queryKey: ['product', id ?? r.id] });
      client.invalidateQueries({ queryKey: ['admin-products'] });
      setSaid(isNew ? 'Product added. Add its photos next.' : 'Saved. The shop shows the change now.');
      if (isNew) navigate(`/admin/products/${r.id}`, { replace: true });
      else setLoadedFor(null);
    },
    onError: (err) => setErrors(toApiError(err).fields as Errors),
  });
  const upload = useMutation({
    mutationFn: (file: File) => uploadImage(id!, file, alt.trim()),
    onSuccess: () => { setAlt(''); product.refetch(); },
  });
  const dropImage = useMutation({ mutationFn: (imageId: number) => deleteImage(id!, imageId), onSuccess: () => product.refetch() });
  const remove = useMutation({
    mutationFn: () => deleteProduct(id!),
    onSuccess: () => { client.invalidateQueries({ queryKey: ['admin-products'] }); navigate('/admin/products'); },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setSaid(null);
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length) {
      requestAnimationFrame(() => document.querySelector<HTMLElement>('.adm-body [aria-invalid=true]')?.focus());
      return;
    }
    save.mutate();
  }

  if (!isNew && product.isPending) return <main className="adm-body"><p className="status-line">Loading the product…</p></main>;
  if (!isNew && product.isError) {
    return <main className="adm-body"><p className="msg msg--bad" role="alert"><Icon name="alert" />{toApiError(product.error).message}</p></main>;
  }

  const err = (k: keyof Form) => errors[k] && <p className="err" id={`pe-${k}-e`}><Icon name="alert" width={2} />{errors[k]}</p>;
  const input = (k: keyof Form, label: string, props: React.InputHTMLAttributes<HTMLInputElement> = {}, unit?: string) => (
    <div className="f">
      <label htmlFor={`pe-${k}`}>{label}</label>
      <div className="fw">
        <input className="in" id={`pe-${k}`} value={form[k] as string} onChange={(e) => set(k, e.target.value as never)}
          aria-invalid={errors[k] ? true : undefined} aria-describedby={errors[k] ? `pe-${k}-e` : undefined} {...props} />
        {unit && <span className="unit">{unit}</span>}
      </div>
      {err(k)}
    </div>
  );
  const cm = (s: string) => (s.trim() && Number(s) > 0 ? (Number(s) / 2.54).toFixed(1) : null);
  const imperial = [cm(form.widthCm), cm(form.heightCm), cm(form.depthCm)];
  const lbs = form.weightKg.trim() && Number(form.weightKg) > 0 ? (Number(form.weightKg) * 2.20462).toFixed(2) : null;
  const title = form.seoTitle.trim() || `${form.name.trim() || 'Product name'} · Tavla`;
  const description = form.seoDescription.trim() || form.description.trim().slice(0, META_MAX) || 'The first lines of the description show here.';
  const images = product.data?.images ?? [];

  return (
    <form className="adm-form" noValidate onSubmit={submit}>
      {dirty && (
        <div className="savebar" role="status">
          <Icon name="info" /><span>Unsaved changes</span>
          <div className="row">
            <button className="btn btn--ghost" type="button" onClick={() => { setForm(initial); setErrors({}); }}>Discard</button>
            <button className="btn" type="submit" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save'}</button>
          </div>
        </div>
      )}
      <main className="adm-body">
        <Link className="backl" to="/admin/products"><Icon name="left" width={2} />Products</Link>
        <AdminHead title={isNew ? 'Add a product' : form.name || 'Product'} sub={!isNew && product.data ? `${product.data.category.name} · ${product.data.reviewCount} ${product.data.reviewCount === 1 ? 'review' : 'reviews'}` : undefined}>
          {!isNew && <Link className="btn btn--ghost" to={`/catalog/${id}`} target="_blank">See it in the shop</Link>}
          <button className="btn" type="submit" disabled={save.isPending || (!dirty && !isNew)}>{save.isPending ? 'Saving…' : isNew ? 'Add product' : 'Save'}</button>
        </AdminHead>
        <div aria-live="polite">
          {said && <p className="msg msg--ok adm-err"><Icon name="check" width={2.4} />{said}</p>}
          {save.error && !Object.keys(errors).length && <p className="msg msg--bad adm-err" role="alert"><Icon name="alert" />{toApiError(save.error).message}</p>}
        </div>
        <div className="adm-2col">
          <div className="stack">
            <section className="apanel" aria-labelledby="pe-basics">
              <div className="panel-h"><h2 id="pe-basics">Basics</h2></div>
              <div className="sec-in">
                {input('name', 'Name', { maxLength: 255 })}
                <div className="f">
                  <label htmlFor="pe-description">Description</label>
                  <textarea className="in" id="pe-description" rows={7} maxLength={10_000} value={form.description}
                    onChange={(e) => set('description', e.target.value)} aria-invalid={errors.description ? true : undefined} />
                  <p className="hint">Plain text. Shown under the photos; its first 160 characters stand in for the search listing.</p>
                  {err('description')}
                </div>
              </div>
            </section>

            <section className="apanel" aria-labelledby="pe-photos">
              <div className="panel-h"><h2 id="pe-photos">Photos</h2></div>
              <div className="sec-in">
                {isNew ? <p className="hint">Add the product first; then photos can go on it.</p> : (
                  <>
                    <div className="media">
                      {images.map((im, i) => (
                        <div key={im.id} className="m">
                          <Photo src={im.url} framing={im.framing} pad={0.06} alt={im.altText ?? ''} />
                          {i === 0 && <span className="pri">Main</span>}
                          <button type="button" className="rowact mdel" aria-label={`Remove photo ${i + 1}`} disabled={dropImage.isPending}
                            onClick={() => dropImage.mutate(im.id)}><Icon name="trash" /></button>
                        </div>
                      ))}
                      <label className="drop">
                        <Icon name="upload" />Add a photo<small>JPG, PNG or WebP. We make the small sizes.</small>
                        <input type="file" accept="image/jpeg,image/png,image/webp" className="vh"
                          onChange={(e) => { const f = e.target.files?.[0]; if (f) upload.mutate(f); e.target.value = ''; }} />
                      </label>
                    </div>
                    <div className="f">
                      <label htmlFor="pe-alt">Alt text for the next photo</label>
                      <input className="in" id="pe-alt" maxLength={255} value={alt} onChange={(e) => setAlt(e.target.value)}
                        placeholder="What the photo shows, for people who can't see it" />
                    </div>
                    {upload.isPending && <p className="hint" role="status">Uploading…</p>}
                    {(upload.error ?? dropImage.error) && <p className="err" role="alert"><Icon name="alert" width={2} />{toApiError(upload.error ?? dropImage.error).message}</p>}
                  </>
                )}
              </div>
            </section>

            <section className="apanel" aria-labelledby="pe-price">
              <div className="panel-h"><h2 id="pe-price">Price and stock</h2></div>
              <div className="sec-in">
                <div className="grid3">
                  {input('price', 'Price', { inputMode: 'decimal' }, 'EUR')}
                  {input('stock', 'In stock', { inputMode: 'numeric' }, 'pcs')}
                  {input('sku', 'SKU', { maxLength: 60 })}
                </div>
                <div className="grid3">{input('ean', 'EAN', { inputMode: 'numeric', maxLength: 13 })}</div>
                <p className="hint">The price includes VAT. Stock goes down when an order is placed and back up when one is cancelled.</p>
              </div>
            </section>

            <section className="apanel" aria-labelledby="pe-details">
              <div className="panel-h"><h2 id="pe-details">Details on the product page</h2></div>
              <div className="sec-in">
                <div className="kvrows">
                  {form.details.map((d, i) => (
                    <div key={i} className="kvr">
                      <input className="in" aria-label={`Detail ${i + 1} name`} placeholder="Name, like king_height" value={d.key}
                        onChange={(e) => set('details', form.details.map((x, j) => (j === i ? { ...x, key: e.target.value } : x)))} />
                      <input className="in" aria-label={`Detail ${i + 1} value`} placeholder="Value" value={d.value}
                        onChange={(e) => set('details', form.details.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)))} />
                      <button type="button" className="rowact" aria-label={`Remove detail ${i + 1}`}
                        onClick={() => set('details', form.details.filter((_, j) => j !== i))}><Icon name="trash" /></button>
                    </div>
                  ))}
                </div>
                <button type="button" className="linkb" onClick={() => set('details', [...form.details, { key: '', value: '' }])}>Add a detail</button>
                {err('details')}
              </div>
            </section>

            <section className="apanel" aria-labelledby="pe-var">
              <div className="panel-h"><h2 id="pe-var">Variants</h2></div>
              <div className="sec-in">
                <div className="grid2">
                  {input('group', 'Group', { placeholder: 'Shared by every colour or size of it' })}
                  {input('variant', 'This one is', { placeholder: 'Colour: Blue' })}
                </div>
                <p className="hint">Products with the same group show as one, with a chip for each option.</p>
              </div>
            </section>
          </div>

          <div className="stack">
            <section className="apanel" aria-labelledby="pe-shop">
              <div className="panel-h"><h2 id="pe-shop">In the shop</h2></div>
              <div className="sec-in">
                <label className="sw">
                  <input type="checkbox" checked={form.active} onChange={(e) => set('active', e.target.checked)} />
                  <i aria-hidden="true" /><span>Shown in the shop and search</span>
                </label>
                <p className="hint">Hidden products keep their page for past orders, but nobody can find or buy them.</p>
              </div>
            </section>

            <section className="apanel" aria-labelledby="pe-where">
              <div className="panel-h"><h2 id="pe-where">Where it lives</h2></div>
              <div className="sec-in">
                <div className="f">
                  <label htmlFor="pe-categoryId">Category</label>
                  <div className="fw">
                    <select className="in" id="pe-categoryId" value={form.categoryId} onChange={(e) => set('categoryId', e.target.value)}
                      aria-invalid={errors.categoryId ? true : undefined}>
                      <option value="">Choose a category</option>
                      {flatten(tree).map((c) => <option key={c.id} value={c.id}>{' '.repeat(c.depth)}{c.name}</option>)}
                    </select>
                    <Icon name="down" width={2} />
                  </div>
                  {err('categoryId')}
                </div>
                <div className="f">
                  <label htmlFor="pe-brandId">Brand</label>
                  <div className="fw">
                    <select className="in" id="pe-brandId" value={form.brandId} onChange={(e) => set('brandId', e.target.value)}>
                      <option value="">No brand</option>
                      {brands.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
                    </select>
                    <Icon name="down" width={2} />
                  </div>
                </div>
              </div>
            </section>

            <section className="apanel" aria-labelledby="pe-size">
              <div className="panel-h"><h2 id="pe-size">Size and weight</h2></div>
              <div className="sec-in">
                <div className="grid2">
                  {input('weightKg', 'Weight', { inputMode: 'decimal' }, 'kg')}
                  {input('widthCm', 'Width', { inputMode: 'decimal' }, 'cm')}
                  {input('heightCm', 'Height', { inputMode: 'decimal' }, 'cm')}
                  {input('depthCm', 'Depth', { inputMode: 'decimal' }, 'cm')}
                </div>
                <p className="conv">
                  {lbs || imperial.some(Boolean)
                    ? <>Shown to imperial shoppers as {lbs && `${lbs} lb`}{lbs && imperial.some(Boolean) && ', '}{imperial.some(Boolean) && `${imperial.map((x) => x ?? '–').join(' × ')} in`}.</>
                    : 'We work out inches and pounds for the product page.'}
                </p>
              </div>
            </section>

            <section className="apanel" aria-labelledby="pe-seo">
              <div className="panel-h"><h2 id="pe-seo">Search engine listing</h2></div>
              <div className="sec-in">
                <div className="serp" aria-hidden="true">
                  <span className="u">tavla.ee <span>› catalog › {id ?? 'new'}</span></span>
                  <span className="tt">{title.length > TITLE_MAX ? `${title.slice(0, TITLE_MAX - 1)}…` : title}</span>
                  <span className="dd">{description.length > META_MAX ? `${description.slice(0, META_MAX - 1)}…` : description}</span>
                </div>
                <div className="f">
                  <label htmlFor="pe-seoTitle">Page title</label>
                  <div className="fw">
                    <input className="in wctr" id="pe-seoTitle" value={form.seoTitle} placeholder={`${form.name || 'Product name'} · Tavla`}
                      onChange={(e) => set('seoTitle', e.target.value)} aria-invalid={errors.seoTitle ? true : undefined} />
                    <span className={form.seoTitle.length > TITLE_MAX ? 'ctr red-t' : 'ctr'}>{form.seoTitle.length} / {TITLE_MAX}</span>
                  </div>
                  {err('seoTitle')}
                </div>
                <div className="f">
                  <label htmlFor="pe-seoDescription">Meta description</label>
                  <textarea className="in" id="pe-seoDescription" rows={3} value={form.seoDescription}
                    onChange={(e) => set('seoDescription', e.target.value)} aria-invalid={errors.seoDescription ? true : undefined} />
                  <p className={form.seoDescription.length > META_MAX ? 'count over' : 'count'}>{form.seoDescription.length} / {META_MAX}</p>
                  {err('seoDescription')}
                </div>
              </div>
            </section>

            {!isNew && (
              <section className="apanel danger" aria-labelledby="pe-arch">
                <div className="panel-h"><h2 id="pe-arch">Archive product</h2></div>
                <div className="sec-in">
                  <p className="hint">Takes it out of the shop and search. A product in past orders is archived, so those orders keep their copy; one never ordered is deleted.</p>
                  <button type="button" className="btn btn--line-danger" disabled={remove.isPending}
                    onClick={() => confirm(`Take “${form.name}” out of the shop?`) && remove.mutate()}>
                    {remove.isPending ? 'Working…' : 'Archive product'}
                  </button>
                  {remove.error && <p className="err" role="alert"><Icon name="alert" width={2} />{toApiError(remove.error).message}</p>}
                </div>
              </section>
            )}
          </div>
        </div>
      </main>
    </form>
  );
}

// Admin bulk upload: upload a CSV or JSON file, read what the check found, then import every row or none.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { DragEvent, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { confirmImport, fetchImport, fetchImportErrors, ImportJob, startImport } from '../../api/admin';
import { toApiError } from '../../api/errors';
import Icon from '../../components/Icon';
import { AdminHead, LoadState, saveBlob } from '../parts';

const MAX_BYTES = 10 * 1024 * 1024;
const COLUMNS = ['sku', 'name', 'description', 'price', 'stock', 'category', 'brand', 'ean', 'weight_kg', 'width_cm', 'height_cm', 'depth_cm',
  'variant_group', 'variant', 'image_url', 'image_alt'];
const SAMPLE = ['CHCA20', 'DGT 3000', 'Official FIDE clock.', '65.00', '12', 'chess-clocks', 'DGT', '8717274780022', '0.4', '18', '11', '5',
  '', '', 'https://cdn.example.com/dgt3000.jpg', 'DGT 3000 chess clock, front'];
const SHOWN_CHANGES = 3;

const clock = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });
const size = (bytes: number) => bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;

function template(kind: 'csv' | 'json') {
  if (kind === 'csv') {
    const quote = (v: string) => (/[",\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v);
    return new Blob([`${COLUMNS.join(',')}\n${SAMPLE.map(quote).join(',')}\n`], { type: 'text/csv' });
  }
  const row = Object.fromEntries(COLUMNS.map((c, i) => [c, SAMPLE[i] || null]));
  return new Blob([JSON.stringify([row], null, 2)], { type: 'application/json' });
}

export default function ImportPage() {
  const [url, setUrl] = useSearchParams();
  const client = useQueryClient();
  const id = url.get('job');
  const [fileError, setFileError] = useState<string | null>(null);
  // the job is kept on the server, so the URL brings the same check back after a reload
  const job = useQuery({ queryKey: ['staff', 'import', id], queryFn: () => fetchImport(id!), enabled: !!id, retry: false });
  const upload = useMutation({
    mutationFn: startImport,
    onSuccess: (j) => {
      client.setQueryData(['staff', 'import', j.id], j);
      setUrl({ job: j.id });
    },
  });

  function pick(file: File | undefined) {
    if (!file) return;
    setFileError(null);
    if (!/\.(csv|json)$/i.test(file.name)) { setFileError('Choose a .csv or .json file.'); return; }
    if (file.size > MAX_BYTES) { setFileError(`That file is ${size(file.size)}. Split it into files of up to 10 MB.`); return; }
    upload.mutate(file);
  }

  const j = id ? job.data : undefined;
  const step = !j ? 0 : j.status === 'CHECKED' ? 1 : 2;

  return (
    <main className="adm-body">
      <AdminHead title="Bulk upload" sub="Add or update many products from one CSV or JSON file.">
        <button className="btn btn--ghost" type="button" onClick={() => saveBlob(template('csv'), 'tavla-products-template.csv')}>
          <Icon name="download" />CSV template
        </button>
        <button className="btn btn--ghost" type="button" onClick={() => saveBlob(template('json'), 'tavla-products-template.json')}>
          <Icon name="download" />JSON template
        </button>
      </AdminHead>
      <ol className="steps" aria-label="Steps">
        {['Upload', 'Check', 'Import'].map((label, i) => {
          const done = i < step || (i === 2 && j?.status === 'DONE');
          return (
            <li key={label} className={done ? 'done' : i === step ? 'now' : undefined} aria-current={i === step ? 'step' : undefined}>
              <span className="n2">{done ? <Icon name="check" width={2.6} /> : i + 1}</span>{label}
            </li>
          );
        })}
      </ol>
      {id && !j && <LoadState query={job} what="the upload" />}
      {!id && <Upload onPick={pick} pending={upload.isPending} error={fileError ?? (upload.error ? uploadError(upload.error) : null)} />}
      {j && j.status === 'CHECKED' && <Check job={j} onAnother={() => setUrl({})} />}
      {j && (j.status === 'DONE' || j.status === 'FAILED') && <Result job={j} onAnother={() => setUrl({})} />}
    </main>
  );
}

/** A file the server can't read at all says why (an unknown column, not UTF-8, too many rows). */
function uploadError(err: unknown) {
  const e = toApiError(err);
  return e.fields.file ?? e.message;
}

function Upload({ onPick, pending, error }: { onPick: (f: File | undefined) => void; pending: boolean; error: string | null }) {
  const [over, setOver] = useState(false);
  const drop = (e: DragEvent<HTMLLabelElement>) => {
    e.preventDefault();
    setOver(false);
    onPick(e.dataTransfer.files[0]);
  };
  return (
    <section className="apanel" aria-labelledby="up-h">
      <div className="panel-h">
        <div><h2 id="up-h">Choose the file</h2><p>Nothing changes in the shop until you have read the check and pressed Import.</p></div>
      </div>
      <div className="panel-b imp-b">
        <label className="idrop" data-over={over ? '' : undefined} onDragOver={(e) => { e.preventDefault(); setOver(true); }}
          onDragLeave={() => setOver(false)} onDrop={drop} aria-describedby={error ? 'up-e' : 'up-h2'}>
          <Icon name="upload" />
          <b>{pending ? 'Uploading and checking…' : 'Drop a CSV or JSON file here, or choose one'}</b>
          <small id="up-h2">Up to 10 MB and 5,000 rows. One product per row, matched on SKU. Photos are linked from image_url.</small>
          <input type="file" accept=".csv,.json,text/csv,application/json" className="vh" disabled={pending}
            onChange={(e) => { onPick(e.target.files?.[0]); e.target.value = ''; }} />
        </label>
        {error && <p className="err" id="up-e" role="alert"><Icon name="alert" width={2} />{error}</p>}
      </div>
    </section>
  );
}

function FileBox({ job, onAnother }: { job: ImportJob; onAnother?: () => void }) {
  return (
    <div className="filebox">
      <span className="fic">{job.format.toUpperCase()}</span>
      <span><b>{job.fileName}</b><small>{job.rows.toLocaleString('en')} rows · {size(job.sizeBytes)} · uploaded {clock.format(new Date(job.uploadedAt))}</small></span>
      {onAnother && <button className="linkb" type="button" onClick={onAnother}>Choose another file</button>}
    </div>
  );
}

function Check({ job: j, onAnother }: { job: ImportJob; onAnother: () => void }) {
  const client = useQueryClient();
  const [onMatch, setOnMatch] = useState<ImportJob['onMatch']>(j.onMatch);
  const [allChanges, setAllChanges] = useState(false);
  const go = useMutation({
    mutationFn: () => confirmImport(j.id, onMatch),
    onSuccess: (next) => client.setQueryData(['staff', 'import', j.id], next),
    // the shop changed since the check: the job now lists the new errors, so show them
    onError: () => client.invalidateQueries({ queryKey: ['staff', 'import', j.id] }),
  });
  const errorRows = useMutation({ mutationFn: () => fetchImportErrors(j.id), onSuccess: (b) => saveBlob(b, `rows-with-errors-${j.fileName}`) });
  const importing = j.counts.new + (onMatch === 'update' ? j.counts.update : 0);
  const skipping = onMatch === 'skip' ? j.counts.update : 0;
  const blocked = j.counts.error > 0;
  const changes = allChanges ? j.changes : j.changes.slice(0, SHOWN_CHANGES);
  return (
    <>
      <section className="apanel" aria-labelledby="chk-h">
        <div className="panel-h"><div><h2 id="chk-h">What this file will do</h2><p>Nothing has changed yet. Read it through, then import.</p></div></div>
        <div className="panel-b imp-b">
          <FileBox job={j} onAnother={onAnother} />
          <div className="counts">
            <div><b>{j.counts.new.toLocaleString('en')}</b><span>New products</span></div>
            <div className="warn"><b>{j.counts.update.toLocaleString('en')}</b><span>Updates to existing products</span></div>
            <div className={j.counts.error ? 'bad' : undefined}><b>{j.counts.error.toLocaleString('en')}</b><span>Rows with errors</span></div>
            <div><b>{j.counts.photos.toLocaleString('en')}</b><span>Photos to link</span></div>
          </div>
          {j.counts.update > 0 && (
            <fieldset className="matchset">
              <legend>When a row's SKU is already in the shop</legend>
              <div className="roleopts two">
                <label className={onMatch === 'update' ? 'on' : undefined}>
                  <input type="radio" name="match" checked={onMatch === 'update'} onChange={() => setOnMatch('update')} />
                  <b>Update that product</b><span>Only the columns in the file change. An empty cell leaves the old value alone.</span>
                </label>
                <label className={onMatch === 'skip' ? 'on' : undefined}>
                  <input type="radio" name="match" checked={onMatch === 'skip'} onChange={() => setOnMatch('skip')} />
                  <b>Skip it</b><span>Only rows with new SKUs are added.</span>
                </label>
              </div>
            </fieldset>
          )}
        </div>
      </section>
      {j.errors.length > 0 && (
        <section className="apanel" aria-labelledby="err-h">
          <div className="panel-h">
            <div><h2 id="err-h">{j.counts.error} {j.counts.error === 1 ? 'row' : 'rows'} with errors</h2><p>Nothing is imported while any row has an error. Fix them in the file and upload it again.</p></div>
            <button className="btn btn--ghost btn--sm" type="button" disabled={errorRows.isPending} onClick={() => errorRows.mutate()}>Download these rows</button>
          </div>
          <div className="tblw">
            <table className="tbl txt">
              <thead><tr><th scope="col" className="r">Row</th><th scope="col">Column</th><th scope="col">Problem</th><th scope="col">Value in the file</th></tr></thead>
              <tbody>
                {j.errors.map((e) => (
                  <tr key={`${e.line}-${e.column}`}>
                    <td className="r">{e.line}</td>
                    <td className="mono">{e.column}</td>
                    <td>{e.problem}</td>
                    <td className={e.value ? 'mono red-t' : 'mono mutet'}>{e.value ? (e.value.length > 32 ? `${e.value.slice(0, 30)}…` : e.value) : '(empty)'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
      {onMatch === 'update' && j.changes.length > 0 && (
        <section className="apanel" aria-labelledby="upd-h">
          <div className="panel-h">
            <div>
              <h2 id="upd-h">{j.counts.update} {j.counts.update === 1 ? 'update' : 'updates'}</h2>
              <p>{changes.length < j.changes.length ? `Showing ${changes.length}. ` : ''}Only these values change.</p>
            </div>
            {j.changes.length > SHOWN_CHANGES && (
              <button className="linkb" type="button" onClick={() => setAllChanges(!allChanges)}>{allChanges ? 'Show fewer' : `Show all ${j.changes.length}`}</button>
            )}
          </div>
          <div className="tblw">
            <table className="tbl diff txt">
              <thead><tr><th scope="col">SKU</th><th scope="col">Product</th><th scope="col">Column</th><th scope="col">Change</th></tr></thead>
              <tbody>
                {changes.map((c) => (
                  <tr key={`${c.sku}-${c.column}`}>
                    <td className="mono">{c.sku}</td>
                    <td>{c.name}</td>
                    <td className="mono">{c.column}</td>
                    <td className="num2">{c.from !== null && <><del>{c.from}</del> → </>}<ins>{c.to}</ins></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
      <div className="row imp-foot">
        <button className="btn btn--ghost" type="button" onClick={onAnother}>Back</button>
        <span className="row">
          {go.error ? <p className="err" role="alert"><Icon name="alert" width={2} />{toApiError(go.error).message}</p>
            : <span className="hint">{blocked ? 'Fix the rows with errors first.' : 'Every row is imported, or none of them if anything goes wrong.'}</span>}
          <button className="btn" type="button" disabled={go.isPending || blocked || importing === 0} onClick={() => go.mutate()}>
            {go.isPending ? 'Importing…' : blocked ? 'Fix the errors to import' : importing === 0 ? 'Nothing to import'
              : `Import ${importing.toLocaleString('en')} ${importing === 1 ? 'row' : 'rows'}${skipping ? `, skip ${skipping.toLocaleString('en')}` : ''}`}
          </button>
        </span>
      </div>
    </>
  );
}

function Result({ job: j, onAnother }: { job: ImportJob; onAnother: () => void }) {
  return (
    <section className="apanel" aria-labelledby="res-h">
      <div className="panel-h">
        <div>
          <h2 id="res-h">{j.status === 'DONE' ? 'Imported' : 'The import stopped'}</h2>
          <p>From {j.fileName}.</p>
        </div>
      </div>
      <div className="panel-b imp-b">
        <FileBox job={j} />
        <div role="status">
          {j.status === 'FAILED'
            ? <p className="msg msg--bad"><Icon name="alert" />{j.failure ?? 'Something went wrong on the server.'} Nothing was imported.</p>
            : <p>{`${j.counts.done.toLocaleString('en')} ${j.counts.done === 1 ? 'product' : 'products'} added or updated.`}</p>}
        </div>
        <div className="row">
          <Link className="btn" to="/admin/products?sort=newest">See the products</Link>
          <button className="btn btn--ghost" type="button" onClick={onAnother}>Upload another file</button>
        </div>
      </div>
    </section>
  );
}

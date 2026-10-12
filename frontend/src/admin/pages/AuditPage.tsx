// Admin audit log: every staff change and sign-in, newest first. Read-only here; the server keeps entries for 12 months.
import { useMutation, useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { AuditParams, exportAudit, fetchAudit } from '../../api/admin';
import { toApiError } from '../../api/errors';
import Icon from '../../components/Icon';
import { ROLE_NAMES } from '../access';
import { AdminHead, AuditWhat, LoadState, saveBlob, stamp, TableFoot } from '../parts';

const KINDS = [['', 'All changes'], ['orders', 'Orders and refunds'], ['products', 'Products and prices'], ['reviews', 'Reviews'], ['access', 'Roles and sign-ins']] as const;
const SINCE = [['today', 'Today'], ['7d', 'Last 7 days'], ['30d', 'Last 30 days']] as const;
const SIZE = 50;
const clock = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });

export default function AuditPage() {
  const [url, setUrl] = useSearchParams();
  const actor = url.get('actor') ?? '';
  const kind = (url.get('kind') ?? '') as '' | NonNullable<AuditParams['kind']>;
  const since = (SINCE.find(([v]) => v === url.get('since'))?.[0] ?? 'today') as NonNullable<AuditParams['since']>;
  const page = Number(url.get('page') ?? 0);
  const set = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(url);
    for (const [k, v] of Object.entries(changes)) { if (v === null || v === '') next.delete(k); else next.set(k, v); }
    if (!('page' in changes)) next.delete('page');
    setUrl(next, { replace: true });
  };
  const params: AuditParams = { actor: actor || undefined, kind: kind || undefined, since, page, size: SIZE };
  const q = useQuery({ queryKey: ['staff', 'audit', params], queryFn: () => fetchAudit(params), retry: false });
  const csv = useMutation({ mutationFn: () => exportAudit({ ...params, page: undefined, size: undefined }), onSuccess: (b) => saveBlob(b, 'tavla-audit-log.csv') });
  const total = q.data?.totalItems ?? 0;
  const when = SINCE.find(([v]) => v === since)![1].toLowerCase();

  return (
    <main className="adm-body">
      <AdminHead title="Audit log"
        sub="Every change a staff member makes, and every sign-in. Nobody can edit or delete an entry, admins included. Entries are kept for 12 months, then deleted automatically.">
        <button className="btn btn--ghost" type="button" disabled={csv.isPending || !total} onClick={() => csv.mutate()}>{csv.isPending ? 'Preparing…' : 'Export CSV'}</button>
      </AdminHead>
      {csv.error && <p className="msg msg--bad adm-err" role="alert"><Icon name="alert" />{toApiError(csv.error).message}</p>}
      <section className="apanel" aria-label="Log entries">
        <div className="filt">
          <div className="row">
            <label className="sel">
              <span className="vh">Person</span>
              <select value={actor} onChange={(e) => set({ actor: e.target.value })}>
                <option value="">Everyone</option>
                {q.data?.actors.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select>
              <Icon name="down" width={2.4} />
            </label>
            <label className="sel">
              <span className="vh">Kind</span>
              <select value={kind} onChange={(e) => set({ kind: e.target.value })}>
                {KINDS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
              </select>
              <Icon name="down" width={2.4} />
            </label>
            <label className="sel">
              <span className="vh">When</span>
              <select value={since} onChange={(e) => set({ since: e.target.value === 'today' ? null : e.target.value })}>
                {SINCE.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
              </select>
              <Icon name="down" width={2.4} />
            </label>
          </div>
          {q.data && <span className="mute">{total.toLocaleString('en')} {total === 1 ? 'entry' : 'entries'} {since === 'today' ? 'today' : `in the ${when}`}</span>}
        </div>
        <LoadState query={q} what="the log" />
        {q.data && (q.data.items.length === 0 ? <p className="panel-b mute">Nothing logged for these filters.</p> : (
          <div className="tblw">
            <table className="tbl txt">
              <thead>
                <tr><th scope="col">When</th><th scope="col">Who</th><th scope="col">What</th><th scope="col">Before → after</th><th scope="col">From</th></tr>
              </thead>
              <tbody className="diff">
                {q.data.items.map((e) => (
                  <tr key={e.id}>
                    <td className="num2"><time dateTime={e.at}>{since === 'today' ? clock.format(new Date(e.at)) : stamp.format(new Date(e.at))}</time></td>
                    <td className={e.actor ? 'nw' : 'mutet'}>
                      {e.actor ?? 'Unknown'}
                      {e.actorRole && <span className={`role ${e.actorRole.toLowerCase()} by`}>{ROLE_NAMES[e.actorRole]}</span>}
                    </td>
                    <td><AuditWhat entry={e} /></td>
                    <td className={e.change ? 'num2' : e.flag ? undefined : 'mutet'}>
                      {e.flag ? <span className={e.flag.serious ? 'flag red' : 'flag'}>{e.flag.text}</span>
                        : e.change ? <>{e.change.from !== null && <><del>{e.change.from}</del> → </>}<ins>{e.change.to}</ins></>
                        : e.detail}
                    </td>
                    <td className="ipm">{e.ip ?? '–'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
        {q.data && total > 0 && (
          <TableFoot page={page} size={SIZE} total={total} note="newest first · kept for 12 months" onPage={(p) => set({ page: String(p) })} />
        )}
      </section>
    </main>
  );
}

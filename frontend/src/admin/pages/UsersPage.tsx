// Admin users and roles: everyone with an account, and a drawer that changes a role after the changer's two-factor code.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { changeRole, fetchUsers, Role, StaffUser, UserParams } from '../../api/admin';
import { toApiError } from '../../api/errors';
import Icon from '../../components/Icon';
import { useAppSelector } from '../../store';
import { ROLE_NAMES } from '../access';
import { AdminHead, Drawer, FieldError, invalid, LoadState, stamp, TableFoot } from '../parts';

const GROUPS = [['all', 'All'], ['customers', 'Customers'], ['staff', 'Staff']] as const;
const SIZE = 25;
const ROLES: { role: Role; text: string }[] = [
  { role: 'CUSTOMER', text: 'Shops. No admin pages.' },
  { role: 'SUPPORT', text: "Orders, customers, refunds, reviews and contact messages. Can't change products or prices." },
  { role: 'SALES', text: "Products, categories, prices, bulk upload and delivery options. Can't see customers." },
  { role: 'ADMIN', text: "Everything, including roles and the audit log. Nobody can change their own role, and the last admin can't be demoted." },
];
// [what, Support, Sales, Admin]: the same split as access.ts
const MATRIX: [string, boolean, boolean, boolean][] = [
  ['See orders and customers', true, false, true],
  ['Pack, ship, cancel, refund', true, false, true],
  ['Read contact messages', true, false, true],
  ['Moderate reviews', true, false, true],
  ['Products, categories, prices', false, true, true],
  ['Bulk upload', false, true, true],
  ['Delivery options', false, true, true],
  ['Users, roles, audit log', false, false, true],
];

const clock = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });
/** "Today, 09:12", "Yesterday, 18:40", "3 Oct, 10:14" */
function lastSeen(iso: string | null) {
  if (!iso) return 'Never';
  const d = new Date(iso);
  const days = Math.round((new Date().setHours(0, 0, 0, 0) - new Date(iso).setHours(0, 0, 0, 0)) / 86_400_000);
  return days === 0 ? `Today, ${clock.format(d)}` : days === 1 ? `Yesterday, ${clock.format(d)}` : stamp.format(d);
}

export default function UsersPage() {
  const me = useAppSelector((s) => s.auth.user!);
  const [url, setUrl] = useSearchParams();
  const group = (GROUPS.find(([v]) => v === url.get('group'))?.[0] ?? 'staff') as NonNullable<UserParams['group']>;
  const twoFactorOff = url.get('twoFactor') === 'off';
  const q = url.get('q') ?? '';
  const page = Number(url.get('page') ?? 0);
  const [text, setText] = useState(q);
  const [editing, setEditing] = useState<StaffUser | null>(null);
  const [said, setSaid] = useState<string | null>(null);
  const set = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(url);
    for (const [k, v] of Object.entries(changes)) { if (v === null || v === '') next.delete(k); else next.set(k, v); }
    if (!('page' in changes)) next.delete('page');
    setUrl(next, { replace: true });
  };
  useEffect(() => {
    const t = setTimeout(() => text.trim() !== q && set({ q: text.trim() }), 300);
    return () => clearTimeout(t);
  }, [text]); // eslint-disable-line react-hooks/exhaustive-deps

  const params: UserParams = { group, q: q || undefined, twoFactor: twoFactorOff ? 'off' : undefined, page, size: SIZE };
  const list = useQuery({ queryKey: ['staff', 'users', params], queryFn: () => fetchUsers(params), retry: false });
  const counts = list.data?.counts;

  return (
    <main className="adm-body">
      <AdminHead title="Users and roles"
        sub={counts && `${counts.customers.toLocaleString('en')} customers · ${counts.staff.toLocaleString('en')} staff`} />
      <div aria-live="polite">{said && <p className="msg msg--ok adm-err"><Icon name="check" width={2.4} />{said}</p>}</div>
      <section className="apanel" aria-label="Users">
        <div className="filt">
          <nav className="tabs2" aria-label="Show">
            {GROUPS.map(([value, label]) => (
              <Link key={value} to={value === 'staff' ? '?' : `?group=${value}`} replace aria-current={value === group && !twoFactorOff ? 'page' : undefined}>
                {label}{counts && <small>{counts[value].toLocaleString('en')}</small>}
              </Link>
            ))}
            {twoFactorOff && <Link to="?group=staff&twoFactor=off" replace aria-current="page">Two-factor off</Link>}
          </nav>
          <label className="isearch">
            <Icon name="search" width={2} /><span className="vh">Find by email</span>
            <input type="search" placeholder="Email" value={text} onChange={(e) => setText(e.target.value)} />
          </label>
        </div>
        <LoadState query={list} what="the users" />
        {list.data && (list.data.items.length === 0 ? <p className="panel-b mute">Nobody matches.</p> : (
          <div className="tblw">
            <table className="tbl">
              <thead>
                <tr>
                  <th scope="col">Name</th><th scope="col">Email</th><th scope="col">Role</th><th scope="col">Two-factor</th>
                  <th scope="col">Last sign-in</th><th scope="col"><span className="vh">Change role</span></th>
                </tr>
              </thead>
              <tbody>
                {list.data.items.map((u) => (
                  <tr key={u.id} className={editing?.id === u.id ? 'is-sel' : undefined}>
                    <td className="nw"><b>{u.fullName}</b>{u.id === me.id && <span className="mutet"> · you</span>}</td>
                    <td className="mutet">{u.email}</td>
                    <td><span className={`role ${u.role.toLowerCase()}`}>{ROLE_NAMES[u.role]}</span></td>
                    <td>{u.twoFactorEnabled
                      ? <span className="on2fa"><Icon name="check" width={2.4} />On</span>
                      : u.role === 'CUSTOMER' ? <span className="mutet">Off</span> : <span className="off2fa"><Icon name="alert" />Not yet</span>}</td>
                    <td className="num2 mutet">{lastSeen(u.lastSignInAt)}</td>
                    <td>{u.id !== me.id && <button className="linkb" type="button" onClick={() => { setSaid(null); setEditing(u); }}>Change role</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
        {list.data && list.data.totalItems > SIZE && (
          <TableFoot page={page} size={SIZE} total={list.data.totalItems} onPage={(p) => set({ page: String(p) })} />
        )}
      </section>
      {editing && (
        <RoleDrawer user={editing} lastAdmin={editing.role === 'ADMIN' && counts?.admins === 1} onClose={() => setEditing(null)}
          onSaved={(u) => { setEditing(null); setSaid(`${u.fullName} is now ${ROLE_NAMES[u.role]}.`); }} />
      )}
    </main>
  );
}

function RoleDrawer({ user, lastAdmin, onClose, onSaved }: { user: StaffUser; lastAdmin: boolean; onClose: () => void; onSaved: (u: StaffUser) => void }) {
  const client = useQueryClient();
  const [role, setRole] = useState<Role>(user.role);
  const [code, setCode] = useState('');
  const [codeError, setCodeError] = useState<string>();
  const save = useMutation({
    mutationFn: () => changeRole(user.id, role, code),
    onSuccess: (u) => { client.invalidateQueries({ queryKey: ['staff'] }); onSaved(u); },
    onError: (e) => { if (toApiError(e).code === 'invalid_2fa_code') setCodeError('That code is not valid. Type the one your app shows now.'); },
  });
  const first = user.fullName.split(' ')[0];
  function submit(e: FormEvent) {
    e.preventDefault();
    if (!/^\d{6}$/.test(code)) { setCodeError('The 6 digits from your authenticator app'); return; }
    setCodeError(undefined);
    save.mutate();
  }
  const otherError = save.error && toApiError(save.error).code !== 'invalid_2fa_code' ? toApiError(save.error).message : null;

  return (
    <Drawer title={`Role for ${user.fullName}`} onClose={onClose}>
      <form className="modal-b dform" id="role-form" noValidate onSubmit={submit}>
        {!user.twoFactorEnabled && role !== 'CUSTOMER' && (
          <div className="msg msg--warn">
            <Icon name="alert" />
            <p><b>{first} hasn't turned on two-factor yet.</b> No admin page opens for this account until it's on. The setup screen appears at the next sign-in.</p>
          </div>
        )}
        <fieldset>
          <legend>Role</legend>
          <div className="roleopts">
            {ROLES.map((r) => {
              const locked = lastAdmin && r.role !== 'ADMIN';
              return (
                <label key={r.role} className={[role === r.role && 'on', locked && 'off'].filter(Boolean).join(' ') || undefined}>
                  <input type="radio" name="role" checked={role === r.role} disabled={locked} onChange={() => setRole(r.role)} />
                  <b>{ROLE_NAMES[r.role]}</b><span>{r.text}</span>
                </label>
              );
            })}
          </div>
          {lastAdmin && <p className="hint">{first} is the only admin, so the role stays Admin until someone else has it.</p>}
        </fieldset>
        <details className="rolemx">
          <summary>What each role can do</summary>
          <table className="matrix">
            <thead><tr><th scope="col">Can</th><th scope="col">Support</th><th scope="col">Sales</th><th scope="col">Admin</th></tr></thead>
            <tbody>
              {MATRIX.map(([what, ...yes]) => (
                <tr key={what}>
                  <th scope="row">{what}</th>
                  {yes.map((y, i) => (
                    <td key={i}><span className={y ? 'y' : 'n'} role="img" aria-label={y ? 'Yes' : 'No'}><Icon name={y ? 'check' : 'minus'} width={y ? 2.6 : 1.8} /></span></td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </details>
        <div className="f">
          <label htmlFor="otp">Your two-factor code</label>
          <input className="in otp-in" id="otp" inputMode="numeric" autoComplete="one-time-code" placeholder="6 digits" maxLength={6} value={code}
            {...invalid('otp', codeError, 'otp-h')} onChange={(e) => { setCode(e.target.value.replace(/\D/g, '')); setCodeError(undefined); }} />
          <FieldError id="otp" error={codeError} />
          <p className="hint" id="otp-h">
            Every role change asks for it, so a stolen session alone can't hand out rights. The change goes to the audit log with your name,
            and every admin gets an email when someone gains or loses Admin.
          </p>
        </div>
        {otherError && <p className="msg msg--bad" role="alert"><Icon name="alert" />{otherError}</p>}
      </form>
      <div className="modal-f">
        <button className="btn btn--ghost" type="button" onClick={onClose}>Cancel</button>
        <button className="btn" type="submit" form="role-form" disabled={save.isPending || role === user.role}>{save.isPending ? 'Saving…' : 'Save role'}</button>
      </div>
    </Drawer>
  );
}

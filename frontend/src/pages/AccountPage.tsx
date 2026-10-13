// Account, Profile: name and phone to edit, the sign-in email shown read-only, how you sign in, your data, and deleting the account.
import { useMutation, useQuery } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { deleteAccount, exportMyData, fetchProfile, updateProfile } from '../api/account';
import { toApiError } from '../api/errors';
import { endSession, reloadUser } from '../auth/session';
import Icon from '../components/Icon';
import { AccountTabs } from '../components/OrderParts';
import { usePageMeta } from '../hooks/usePageMeta';
import { useAppDispatch, useAppSelector } from '../store';
import { NAME_MAX } from '../validation/authRules';
import { validatePhone } from '../validation/checkoutRules';
import { PAGE_META } from '../content/pageMeta';

const month = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric' });
const day = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
const initials = (name: string) => name.trim().split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase();

export default function AccountPage() {
  usePageMeta(...PAGE_META.account);
  const user = useAppSelector((s) => s.auth.user)!;
  const dispatch = useAppDispatch();
  // PLANNED endpoint: until it exists the page works from /auth/me, without the phone and dates
  const profile = useQuery({ queryKey: ['profile', user.id], queryFn: fetchProfile, retry: false });
  const [name, setName] = useState<string | null>(null);
  const [phone, setPhone] = useState<string | null>(null);
  const [errors, setErrors] = useState<{ name?: string; phone?: string }>({});
  const [saved, setSaved] = useState(false);
  const shownName = name ?? profile.data?.fullName ?? user.fullName;
  const shownPhone = phone ?? profile.data?.phone ?? '';

  const save = useMutation({
    mutationFn: () => updateProfile({ fullName: shownName.trim(), phone: shownPhone.trim() || null }),
    onSuccess: async () => {
      setSaved(true);
      setName(null);
      setPhone(null);
      await profile.refetch();
      await reloadUser(dispatch);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setSaved(false);
    const n = shownName.trim();
    const found = {
      name: !n ? 'Enter your name.' : n.length > NAME_MAX ? `Keep it under ${NAME_MAX} characters.` : undefined,
      phone: shownPhone.trim() ? validatePhone(shownPhone) : undefined,
    };
    setErrors(found);
    if (!found.name && !found.phone) save.mutate();
  }
  const dirty = name !== null || phone !== null;

  return (
    <main className="acct">
      <p className="t-eyebrow">Your account</p>
      <AccountTabs />
      <div className="ttl"><h1 className="co-h1">Profile</h1></div>
      {saved && <p className="msg msg--ok" role="status"><Icon name="check" width={2.4} />Your changes are saved.</p>}
      <div className="acct2 even">
        <section className="card" aria-labelledby="pf-h">
          <h2 id="pf-h" className="vh">Your details</h2>
          <div className="prof-top">
            <span className="av-lg" aria-hidden="true">{initials(user.fullName)}</span>
            <div>
              <b className="who">{user.fullName}</b>
              {profile.data && <p>Customer since {month.format(new Date(profile.data.createdAt))}</p>}
            </div>
          </div>
          <form noValidate onSubmit={submit}>
            <div className="f">
              <label htmlFor="pf-name">Full name</label>
              <input className="in" id="pf-name" autoComplete="name" maxLength={NAME_MAX} value={shownName}
                onChange={(e) => setName(e.target.value)} aria-invalid={errors.name ? true : undefined}
                aria-describedby={errors.name ? 'pf-name-e' : undefined} />
              {errors.name && <p className="err" id="pf-name-e"><Icon name="alert" width={2} />{errors.name}</p>}
            </div>
            <div className="f">
              <span className="flb" id="pf-em">Email</span>
              <p className="ro" aria-labelledby="pf-em" aria-describedby="pf-em-h">
                <Icon name="mail" />{user.email}
                {profile.data?.emailVerified && <span className="verified"><Icon name="check" width={2.4} />Verified</span>}
              </p>
              <p className="hint" id="pf-em-h">You sign in with it and password resets go to it, so it can't be changed here.</p>
            </div>
            <div className="f">
              <label htmlFor="pf-ph">Phone <small>(optional)</small></label>
              <input className="in" id="pf-ph" type="tel" autoComplete="tel" value={shownPhone}
                onChange={(e) => setPhone(e.target.value)} aria-invalid={errors.phone ? true : undefined}
                aria-describedby={errors.phone ? 'pf-ph-e pf-ph-h' : 'pf-ph-h'} />
              <p className="hint" id="pf-ph-h">Filled in at checkout for the courier.</p>
              {errors.phone && <p className="err" id="pf-ph-e"><Icon name="alert" width={2} />{errors.phone}</p>}
            </div>
            {save.error && <p className="form-error" role="alert">{toApiError(save.error).message}</p>}
            <div className="formfoot">
              {profile.data && <p className="hint">Last changed {day.format(new Date(profile.data.updatedAt))}</p>}
              {dirty && <button className="btn btn--ghost" type="button" onClick={() => { setName(null); setPhone(null); setErrors({}); }}>Cancel</button>}
              <button className="btn" type="submit" disabled={save.isPending || !dirty}>{save.isPending ? 'Saving…' : 'Save changes'}</button>
            </div>
          </form>
        </section>
        <div className="side-list">
          <section className="card" aria-labelledby="si-h">
            <div className="card-h"><h2 id="si-h">Signing in</h2><Link className="lnk" to="/account/security">Security</Link></div>
            <dl className="kv">
              <div><dt>Method</dt><dd>{user.authProvider === 'LOCAL' ? 'Email and password' : `${user.authProvider[0]}${user.authProvider.slice(1).toLowerCase()} account`}</dd></div>
              <div>
                <dt>Two-factor</dt>
                <dd>{user.twoFactorEnabled
                  ? <span className="on2fa"><Icon name="check" width={2.4} />On, with an authenticator app</span>
                  : <span className="off2fa">Off</span>}</dd>
              </div>
            </dl>
          </section>
          <DataCard />
          <DeleteCard local={user.authProvider === 'LOCAL'} onDeleted={async () => { await endSession(dispatch); }} />
        </div>
      </div>
    </main>
  );
}

function DataCard() {
  const download = useMutation({
    mutationFn: exportMyData,
    onSuccess: (blob) => {
      const url = URL.createObjectURL(blob);
      const a = Object.assign(document.createElement('a'), { href: url, download: 'tavla-my-data.json' });
      a.click();
      URL.revokeObjectURL(url);
    },
  });
  return (
    <section className="card" aria-labelledby="dl-h">
      <h2 id="dl-h">Your data</h2>
      <p>One file with everything we keep about you: profile, addresses, orders and reviews.</p>
      <button className="btn btn--ghost" type="button" disabled={download.isPending} onClick={() => download.mutate()}>
        <Icon name="download" width={2} />{download.isPending ? 'Preparing…' : 'Download my data (JSON)'}
      </button>
      {download.error && <p className="err" role="alert"><Icon name="alert" width={2} />{toApiError(download.error).message}</p>}
    </section>
  );
}

/** Deleting asks for the password first (a Google account types its email): a borrowed, unlocked laptop shouldn't be enough. */
function DeleteCard({ local, onDeleted }: { local: boolean; onDeleted: () => Promise<void> }) {
  const navigate = useNavigate();
  const [asking, setAsking] = useState(false);
  const [password, setPassword] = useState('');
  const remove = useMutation({
    mutationFn: () => deleteAccount(local ? { password } : { email: password }),
    onSuccess: async () => {
      await onDeleted();
      navigate('/', { replace: true });
    },
  });
  return (
    <section className="card danger" aria-labelledby="del-h">
      <h2 id="del-h">Delete account</h2>
      <p>Removes your account, addresses, saved cart and reviews. Past orders stay for 7 years, as accounting law requires, no longer linked to you.{local ? ' We ask for your password first.' : ' We ask you to type your email first.'}</p>
      {!asking ? (
        <button className="btn btn--line-danger" type="button" onClick={() => setAsking(true)}>Delete my account</button>
      ) : (
        <form className="delform" noValidate onSubmit={(e) => { e.preventDefault(); remove.mutate(); }}>
          <div className="f">
            <label htmlFor="del-pw">{local ? 'Your password' : 'Your email'}</label>
            <input className="in" id="del-pw" type={local ? 'password' : 'email'} autoComplete={local ? 'current-password' : 'email'}
              value={password} onChange={(e) => setPassword(e.target.value)} autoFocus />
          </div>
          {remove.error && <p className="err" role="alert"><Icon name="alert" width={2} />{toApiError(remove.error).message}</p>}
          <div className="two">
            <button className="btn btn--ghost" type="button" onClick={() => { setAsking(false); setPassword(''); }}>Keep it</button>
            <button className="btn btn--danger" type="submit" disabled={remove.isPending || !password}>
              {remove.isPending ? 'Deleting…' : 'Delete for good'}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}

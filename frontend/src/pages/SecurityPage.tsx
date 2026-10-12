// Account, Security: how you sign in, and two-factor authentication on or off.
import { Link } from 'react-router-dom';
import TwoFactorPanel from '../components/TwoFactorPanel';
import { AccountTabs } from '../components/OrderParts';
import { usePageMeta } from '../hooks/usePageMeta';
import { useAppSelector } from '../store';

export default function SecurityPage() {
  usePageMeta('Sign-in and security', 'How you sign in to Tavla, and two-factor authentication with an authenticator app and backup codes.');
  const user = useAppSelector((s) => s.auth.user)!;
  return (
    <main className="acct">
      <p className="t-eyebrow">Your account</p>
      <AccountTabs />
      <div className="ttl"><h1 className="co-h1">Security</h1></div>
      <div className="sec-grid">
        <section className="card" aria-labelledby="si-h">
          <h2 id="si-h">Signing in</h2>
          <dl className="kv">
            <div><dt>Email</dt><dd>{user.email}</dd></div>
            <div><dt>Method</dt><dd>{user.authProvider === 'LOCAL' ? 'Email and password' : `${user.authProvider[0]}${user.authProvider.slice(1).toLowerCase()} account`}</dd></div>
          </dl>
          {user.authProvider === 'LOCAL' && (
            <p className="foot-end sec-pw">
              To change your password, <Link className="lnk" to="/forgot">get a reset link by email</Link>.
            </p>
          )}
        </section>
        {user.authProvider === 'LOCAL' ? (
          <TwoFactorPanel enabled={user.twoFactorEnabled} />
        ) : (
          <p className="notice">Two-factor settings are managed by your Google account.</p>
        )}
      </div>
    </main>
  );
}

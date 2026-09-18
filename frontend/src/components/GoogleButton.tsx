// "Sign in with Google" via Google Identity Services; hands the ID token to the backend, which verifies it.
import { useEffect, useRef } from 'react';
import { loadScript } from '../lib/loadScript';

interface GoogleIdentity {
  accounts: {
    id: {
      initialize(options: { client_id: string; callback: (response: { credential: string }) => void }): void;
      renderButton(element: HTMLElement, options: Record<string, unknown>): void;
    };
  };
}

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID;

export default function GoogleButton({ onCredential }: { onCredential: (idToken: string) => void }) {
  const container = useRef<HTMLDivElement>(null);
  const callback = useRef(onCredential);
  callback.current = onCredential;

  useEffect(() => {
    if (!CLIENT_ID) return;
    let cancelled = false;
    loadScript('https://accounts.google.com/gsi/client')
      .then(() => {
        const google = (window as unknown as { google: GoogleIdentity }).google;
        if (cancelled || !container.current) return;
        google.accounts.id.initialize({ client_id: CLIENT_ID, callback: (r) => callback.current(r.credential) });
        google.accounts.id.renderButton(container.current, { theme: 'outline', size: 'large', width: 280 });
      })
      .catch(() => {
        // Blocked or offline: the password form still works, so the button simply doesn't appear.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // No client id configured (the dev default): hide the option rather than render a broken button.
  if (!CLIENT_ID) return null;
  return <div ref={container} className="google-button" />;
}

// "Sign in with Google" via Google Identity Services; hands the ID token to the backend, which verifies it.
import { useEffect, useRef, useState } from 'react';
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
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!CLIENT_ID) return;
    let cancelled = false;
    let observer: ResizeObserver | undefined;
    let timer = 0;
    // Pinned to English to match the site: Google otherwise picks the language from the visitor's location.
    loadScript('https://accounts.google.com/gsi/client?hl=en')
      .then(() => {
        const google = (window as unknown as { google: GoogleIdentity }).google;
        const el = container.current;
        if (cancelled || !el) return;
        google.accounts.id.initialize({ client_id: CLIENT_ID, callback: (r) => callback.current(r.credential) });
        // Google draws the button at a fixed pixel width (400 at most), so it is redrawn when the card narrows or widens.
        let drawn = 0;
        const draw = () => {
          const width = Math.min(400, el.clientWidth);
          if (width === drawn) return;
          drawn = width;
          el.replaceChildren();
          google.accounts.id.renderButton(el, {
            theme: 'outline',
            size: 'large',
            shape: 'pill',
            text: 'continue_with',
            logo_alignment: 'center',
            width,
            locale: 'en',
          });
        };
        draw();
        observer = new ResizeObserver(() => {
          clearTimeout(timer);
          timer = window.setTimeout(draw, 150);
        });
        observer.observe(el);
      })
      .catch(() => {
        // Blocked or offline: the password form still works, so the option simply doesn't appear.
        if (!cancelled) setFailed(true);
      });
    return () => {
      cancelled = true;
      observer?.disconnect();
      clearTimeout(timer);
    };
  }, []);

  // No client id configured (the dev default): hide the option rather than render a broken button.
  if (!CLIENT_ID || failed) return null;
  return (
    <>
      <p className="or">or</p>
      <div ref={container} className="google-button" />
    </>
  );
}

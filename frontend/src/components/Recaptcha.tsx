// reCAPTCHA v2 checkbox. Renders only when a site key is configured, matching RECAPTCHA_ENABLED on the server.
import { useEffect, useRef } from 'react';
import { loadScript } from '../lib/loadScript';

interface Grecaptcha {
  ready(callback: () => void): void;
  render(element: HTMLElement, options: Record<string, unknown>): number;
  reset(widgetId: number): void;
}

export const RECAPTCHA_SITE_KEY = import.meta.env.VITE_RECAPTCHA_SITE_KEY;

interface RecaptchaProps {
  onToken: (token: string | null) => void;
  // Bumped by the parent after a failed submit: a token is single-use, so the box must be ticked again.
  resetSignal: number;
}

export default function Recaptcha({ onToken, resetSignal }: RecaptchaProps) {
  const container = useRef<HTMLDivElement>(null);
  const widget = useRef<number | null>(null);
  const callback = useRef(onToken);
  callback.current = onToken;

  useEffect(() => {
    if (!RECAPTCHA_SITE_KEY) return;
    loadScript('https://www.google.com/recaptcha/api.js?render=explicit')
      .then(() => {
        const grecaptcha = (window as unknown as { grecaptcha: Grecaptcha }).grecaptcha;
        grecaptcha.ready(() => {
          if (!container.current || widget.current !== null) return;
          widget.current = grecaptcha.render(container.current, {
            sitekey: RECAPTCHA_SITE_KEY,
            callback: (token: string) => callback.current(token),
            'expired-callback': () => callback.current(null),
          });
        });
      })
      .catch(() => callback.current(null));
  }, []);

  useEffect(() => {
    if (resetSignal === 0 || widget.current === null) return;
    (window as unknown as { grecaptcha: Grecaptcha }).grecaptcha.reset(widget.current);
    callback.current(null);
  }, [resetSignal]);

  if (!RECAPTCHA_SITE_KEY) return null;
  return <div ref={container} className="recaptcha" />;
}

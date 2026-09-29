// Display formatting shared across pages.
const eur = new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR' });

export function formatPrice(value: number) {
  return eur.format(value);
}

const whole = new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });

/** A price range as "€25 – €100"; the open top band reads "€250+". Band edges are whole euros. */
export function formatBand(min: number, max: number | null) {
  return max === null ? `${whole.format(min)}+` : `${whole.format(min)} – ${whole.format(max)}`;
}

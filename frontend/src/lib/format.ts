// Display formatting shared across pages.
const eur = new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR' });

export function formatPrice(value: number) {
  return eur.format(value);
}

export function formatBand(min: number, max: number | null) {
  return max === null ? `${formatPrice(min)} and up` : `${formatPrice(min)} – ${formatPrice(max)}`;
}

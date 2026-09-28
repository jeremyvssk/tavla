// Picks which sibling a colour or size swatch leads to on the product page.
import type { ProductVariant } from '../api/catalog';

/**
 * The variant with `value` on `axis` that keeps as many of the current picks on the other axes as
 * possible, so switching colour keeps the chosen size. Ties go to one that is in stock.
 */
export function variantFor(variants: ProductVariant[], current: ProductVariant, axis: string, value: string) {
  const score = (v: ProductVariant) =>
    Object.keys(current.options).filter((a) => a !== axis && v.options[a] === current.options[a]).length * 2 +
    (v.inStock ? 1 : 0);
  return variants
    .filter((v) => v.options[axis] === value)
    .reduce<ProductVariant | undefined>((best, v) => (!best || score(v) > score(best) ? v : best), undefined);
}

/** Axis names in the order the supplier gave them, with each axis's values in first-seen order. */
export function axesOf(variants: ProductVariant[]) {
  const axes = new Map<string, string[]>();
  for (const v of variants) {
    for (const [axis, value] of Object.entries(v.options)) {
      const values = axes.get(axis) ?? [];
      if (!values.includes(value)) values.push(value);
      axes.set(axis, values);
    }
  }
  return [...axes.entries()];
}

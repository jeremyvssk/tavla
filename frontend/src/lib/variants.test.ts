// Unit tests for the variant picker: switching one axis keeps the others, stock breaks ties.
import { describe, expect, it } from 'vitest';
import type { ProductVariant } from '../api/catalog';
import { axesOf, variantFor } from './variants';

const v = (id: string, options: Record<string, string>, inStock = true): ProductVariant =>
  ({ id, options, price: 100, inStock, imageUrl: null });

const set = [
  v('16-red', { Size: '16"', Colour: 'Red' }),
  v('16-green', { Size: '16"', Colour: 'Green' }),
  v('19-red', { Size: '19"', Colour: 'Red' }),
  v('19-purple', { Size: '19"', Colour: 'Purple' }, false),
  v('19-green', { Size: '19"', Colour: 'Green' }),
];

describe('variantFor', () => {
  it('keeps the chosen size when the colour changes', () => {
    expect(variantFor(set, set[2], 'Colour', 'Green')?.id).toBe('19-green');
  });

  it('falls back to another size when the colour only exists there', () => {
    expect(variantFor(set, set[0], 'Colour', 'Purple')?.id).toBe('19-purple');
  });

  it('prefers an in-stock variant when the other axes tie', () => {
    const tie = [v('a', { Colour: 'Blue' }, false), v('b', { Colour: 'Blue' })];
    expect(variantFor(tie, v('c', { Colour: 'Red' }), 'Colour', 'Blue')?.id).toBe('b');
  });

  it('returns nothing for a value no variant has', () => {
    expect(variantFor(set, set[0], 'Colour', 'Orange')).toBeUndefined();
  });
});

describe('axesOf', () => {
  it('lists each axis once with its values in first-seen order', () => {
    expect(axesOf(set)).toEqual([['Size', ['16"', '19"']], ['Colour', ['Red', 'Green', 'Purple']]]);
  });
});

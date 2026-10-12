// How long an order has waited, as the queue shows it: exact while it matters, rounded once it doesn't.
import { waited } from './parts';

const NOW = Date.parse('2026-10-10T12:00:00Z');
const ago = (min: number) => new Date(NOW - min * 60_000).toISOString();

describe('waited', () => {
  it('counts minutes, then hours and minutes, then hours, then days and hours', () => {
    expect(waited(ago(0), NOW)).toBe('0 min');
    expect(waited(ago(59), NOW)).toBe('59 min');
    expect(waited(ago(60), NOW)).toBe('1 h 0 min');
    expect(waited(ago(4 * 60 + 13), NOW)).toBe('4 h 13 min');
    expect(waited(ago(11 * 60 + 5), NOW)).toBe('11 h 5 min');
    expect(waited(ago(12 * 60), NOW)).toBe('12 h');
    expect(waited(ago(23 * 60 + 59), NOW)).toBe('23 h');
    expect(waited(ago(24 * 60), NOW)).toBe('1 d 0 h');
    expect(waited(ago(55 * 60 + 30), NOW)).toBe('2 d 7 h');
  });

  it('never goes below zero when the server clock is a little ahead', () => {
    expect(waited(new Date(NOW + 90_000).toISOString(), NOW)).toBe('0 min');
  });
});

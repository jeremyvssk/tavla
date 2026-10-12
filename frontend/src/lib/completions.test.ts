// Search completions: finish the word being typed and add the next one, from real product names.
import { completions } from './completions';

const NAMES = [
  'DGT Centaur electronic chess computer',
  'DGT 3000 chess clock',
  'Staunton No 5 Chess Pieces – Handmade',
  'German Staunton chess pieces with box',
  'Tournament chess set and board',
];
const texts = (q: string, names = NAMES, max?: number) => completions(q, names, max).map((c) => c.text);

describe('search completions', () => {
  it('finishes the typed word and adds the word after it', () => {
    expect(texts('dgt')).toEqual(['dgt centaur', 'dgt 3000']);
    expect(texts('stau')).toEqual(['staunton no', 'german staunton chess']);
  });

  it('brings the word before a match inside a name, and skips little words around it', () => {
    expect(texts('staunton')).toContain('german staunton chess');
    // "set and board": "and" is never offered as the next word
    expect(texts('tournament chess s')).toEqual(['tournament chess set']);
  });

  it('marks which parts were typed and which are predicted', () => {
    const [first] = completions('dgt c', NAMES);
    expect(first.parts).toEqual([
      { text: 'dgt ', typed: true },
      { text: 'c', typed: true },
      { text: 'entaur electronic', typed: false },
    ]);
  });

  it('matches whole earlier words, so a typo in them finds nothing', () => {
    expect(texts('dgx c')).toEqual([]);
  });

  it('offers nothing that only repeats the query, never twice the same, and at most max', () => {
    expect(texts('dgt 3000 chess clock')).toEqual([]);
    expect(texts('chess', ['chess set', 'chess set', 'chess clock', 'chess board'], 2)).toEqual(['chess set', 'chess clock']);
    expect(texts('  ')).toEqual([]);
  });
});

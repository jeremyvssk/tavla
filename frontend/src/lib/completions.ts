// Search completions built from the product names the suggestion endpoint returns: the word being
// typed finished, plus the word after it, so "dgt" offers "dgt centaur" and "stau" offers "staunton no 5".

const STOP = new Set(['-', '–', '&', 'and', 'with', 'for', 'of', 'in', 'the', 'a', 'to', 'by']);
const words = (s: string) => s.toLowerCase().replace(/[(),"]/g, ' ').split(/\s+/).filter(Boolean);

export interface Completion {
  /** the whole phrase, what a pick searches for */
  text: string;
  /** the phrase in parts: typed ones stay plain, predicted ones are bold */
  parts: { text: string; typed: boolean }[];
}

export function completions(query: string, names: string[], max = 5): Completion[] {
  const typed = words(query);
  if (!typed.length) return [];
  const seen = new Set<string>();
  const out: Completion[] = [];
  for (const name of names) {
    const w = words(name);
    // where the typed words sit in the name: all but the last match whole, the last as a prefix
    const at = w.findIndex((_, i) => typed.every((t, j) =>
      j < typed.length - 1 ? w[i + j] === t : w[i + j]?.startsWith(t)));
    if (at < 0) continue;
    const last = at + typed.length - 1;
    const next = w[last + 1];
    const parts: Completion['parts'] = [];
    // a match inside the name brings the word before it along: "german staunton"
    if (at > 0 && !STOP.has(w[at - 1])) parts.push({ text: `${w[at - 1]} `, typed: false });
    typed.slice(0, -1).forEach((t) => parts.push({ text: `${t} `, typed: true }));
    const tail = typed[typed.length - 1];
    parts.push({ text: tail, typed: true });
    const rest = w[last].slice(tail.length) + (next && !STOP.has(next) ? ` ${next}` : '');
    if (rest) parts.push({ text: rest, typed: false });
    const text = parts.map((p) => p.text).join('');
    if (text === typed.join(' ') || seen.has(text)) continue;
    seen.add(text);
    out.push({ text, parts });
    if (out.length === max) break;
  }
  return out;
}

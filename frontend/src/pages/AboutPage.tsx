// About: what Tavla is, a photo band, four real numbers, three principles, the people, then contact and social links.
import { Link } from 'react-router-dom';
import Icon from '../components/Icon';
import { SOCIAL } from '../components/Layout';
import { usePageMeta } from '../hooks/usePageMeta';
import { PAGE_META } from '../content/pageMeta';

const NUMBERS = [
  ['1,772', 'products, each with real photos and a real stock count'],
  ['3', 'games: chess, Go and backgammon, plus draughts'],
  ['3', 'suppliers, in Poland, the United States and Germany'],
  ['30', 'days to send anything back'],
];

const PRINCIPLES = [
  ["What we're for", 'A good set makes a good game better. We want a player to find the right one in a minute, know exactly what will arrive, and have it on the table within days.'],
  ['How we choose', 'We stock what our suppliers make well: hand-turned Staunton pieces from Poland, Go stones and boards, tournament backgammon cases. Every product page shows sizes, woods and weight.'],
  ['How we work', 'Orders leave within a working day. You can cancel until we pack, and send anything back for 30 days. Your card goes to Stripe, never to us.'],
];

// [initials] until there are real photos and names
const TEAM = [
  ['[AB]', '[Name]', '[Role, for example founder and developer]', '[One line on what they do at Tavla, or which game they play.]'],
  ['[CD]', '[Name]', '[Role]', '[One line on what they do at Tavla.]'],
  ['[EF]', '[Name]', '[Role]', '[One line on what they do at Tavla.]'],
];

export default function AboutPage() {
  usePageMeta(...PAGE_META.about);
  return (
    <div className="wrap about2">
      <section className="ab2-hero" aria-labelledby="ab-h">
        <h1 id="ab-h">Equipment for three of the oldest games still played.</h1>
        <p className="lede">Tavla is the Turkish word for backgammon. We sell sets, boards, stones and clocks for chess, Go and backgammon, for the kitchen table, the club night and the tournament hall.</p>
      </section>

      <div className="ab2-band">
        <img src="/tavla/hero/chess.webp" alt="A wooden chess set mid-game" loading="lazy" />
        <img src="/tavla/hero/go.webp" alt="Black and white Go stones on a wooden board" style={{ objectPosition: '55% 40%' }} loading="lazy" />
        <img src="/tavla/hero/backgammon.webp" alt="An open backgammon board with checkers and dice" loading="lazy" />
      </div>

      <dl className="ab2-nums">
        {NUMBERS.map(([n, text]) => (
          <div key={text}><dt className="vh">{text.split(',')[0]}</dt><dd><b>{n}</b><span>{text}</span></dd></div>
        ))}
      </dl>

      <section className="ab2-sec ab2-first" aria-labelledby="pr-h">
        <h2 id="pr-h" className="vh">What we stand for</h2>
        <ol className="ab2-pr">
          {PRINCIPLES.map(([title, text], i) => (
            <li key={title}><span className="n">0{i + 1}</span><h3>{title}</h3><p>{text}</p></li>
          ))}
        </ol>
      </section>

      <section className="ab2-sec" aria-labelledby="team-h">
        <h2 id="team-h">The people behind it</h2>
        <ul className="ab2-team">
          {TEAM.map(([initials, name, role, line], i) => (
            <li key={i}><span className="av" aria-hidden="true">{initials}</span><b>{name}</b><small>{role}</small><p>{line}</p></li>
          ))}
        </ul>
      </section>

      <section className="ab2-end" aria-labelledby="end-h">
        <div><h2 id="end-h">Questions about a set?</h2><p>Write to us, or follow along for new arrivals and the odd famous game.</p></div>
        <Link className="btn" to="/contact">Contact us</Link>
        <nav className="socl" aria-label="Tavla on social media">
          {SOCIAL.map((s) => <a key={s.name} href={s.href} rel="noopener noreferrer"><Icon name={s.icon} />{s.name}</a>)}
        </nav>
      </section>
    </div>
  );
}

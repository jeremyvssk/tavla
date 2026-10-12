// FAQ: questions by topic with a contents column, and a search that narrows them as you type.
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { FAQ } from '../content/faq';
import Icon from '../components/Icon';
import { useActiveSection } from '../hooks/useActiveSection';
import { usePageMeta } from '../hooks/usePageMeta';

export default function FaqPage() {
  usePageMeta('Shipping, payment and returns FAQ',
    'Answers on shipping prices and times, card payments, returns and refunds, cancelling an order, and your Tavla account. Free shipping on orders over €100.');
  const [query, setQuery] = useState('');
  const q = query.trim().toLowerCase();
  const topics = FAQ.map((t) => ({
    ...t,
    items: q ? t.items.filter((i) => `${i.q} ${i.a.join(' ')}`.toLowerCase().includes(q)) : t.items,
  })).filter((t) => t.items.length > 0);
  const active = useActiveSection(topics.map((t) => t.id));

  return (
    <div className="hpage hpage--full faqpage">
      <div className="hhead flat">
        <nav aria-label="Breadcrumb">
          <ol className="crumbs">
            <li><Link to="/">Home</Link></li>
            <li><Link to="/contact">Help</Link></li>
            <li><span aria-current="page">FAQ</span></li>
          </ol>
        </nav>
        <h1 className="dt">Questions and answers</h1>
        <form className="faqsearch" role="search" onSubmit={(e) => e.preventDefault()}>
          <Icon name="search" width={2} />
          <input type="search" aria-label="Search the questions" placeholder="Search the questions, like “refund” or “parcel locker”"
            value={query} onChange={(e) => setQuery(e.target.value)} />
        </form>
      </div>
      <div className="faqwrap">
        <aside className="hside">
          <nav className="toc flat" aria-labelledby="toc-h">
            <h2 id="toc-h">Topics</h2>
            {topics.map((t) => (
              <a key={t.id} href={`#${t.id}`} aria-current={active === t.id ? 'true' : undefined}>
                {t.title}<small>{t.items.length}</small>
              </a>
            ))}
          </nav>
        </aside>
        <div className="faqs">
          {topics.length === 0 && (
            <p className="lede" role="status">No question mentions “{query.trim()}”. Write to us below and we'll answer it.</p>
          )}
          {topics.map((t, ti) => (
            <section key={t.id} id={t.id} aria-labelledby={`q-${t.id}`}>
              <h2 id={`q-${t.id}`}>{t.title}</h2>
              {t.items.map((item, i) => (
                // a search opens every match; otherwise only the first question starts open
                <details key={item.q} className="qa" open={q ? true : ti === 0 && i === 0}>
                  <summary>{item.q}<Icon name="down" width={2} /></summary>
                  <div className="ans">{item.a.map((p) => <p key={p}>{p}</p>)}</div>
                </details>
              ))}
            </section>
          ))}
          <div className="stuck">
            <div><h2>Didn't find it?</h2><p>Write to us and say what you need.</p></div>
            <Link className="btn" to="/contact">Contact us</Link>
          </div>
        </div>
      </div>
    </div>
  );
}

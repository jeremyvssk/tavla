// Terms and privacy: one page with a tab per document, a contents column, the text, and a way to ask.
import { Fragment } from 'react';
import { Link, Navigate, NavLink, useParams } from 'react-router-dom';
import { LEGAL } from '../content/legal';
import Icon from '../components/Icon';
import { useActiveSection } from '../hooks/useActiveSection';
import { usePageMeta } from '../hooks/usePageMeta';

// [date] until the owner publishes the text
const UPDATED = '[date]';

export default function LegalPage() {
  const { doc: slug } = useParams();
  const doc = LEGAL.find((d) => d.slug === slug);
  usePageMeta(doc ? doc.title : null, doc?.description);
  const active = useActiveSection(doc ? doc.sections.map((s) => s.id) : []);
  if (!doc) return <Navigate to="/legal/terms" replace />;

  return (
    <div className="hpage hpage--full legalpage">
      <div className="hhead flat">
        <nav aria-label="Breadcrumb">
          <ol className="crumbs"><li><Link to="/">Home</Link></li><li><span aria-current="page">{doc.title}</span></li></ol>
        </nav>
        <h1 className="dt">Terms and privacy</h1>
        <nav className="tabs" aria-label="Legal pages">
          {LEGAL.map((d) => <NavLink key={d.slug} className="tab" to={`/legal/${d.slug}`}>{d.tab}</NavLink>)}
        </nav>
      </div>
      <div className="doc three">
        <aside className="hside">
          <nav className="toc flat" aria-labelledby="toc-h">
            <h2 id="toc-h">On this page</h2>
            {doc.sections.map((s) => (
              <a key={s.id} href={`#${s.id}`} aria-current={active === s.id ? 'true' : undefined}>{s.title}</a>
            ))}
          </nav>
        </aside>
        <article>
          {doc.sections.map((s) => (
            <Fragment key={s.id}>
              <h2 id={s.id}>{s.title}</h2>
              {s.body}
            </Fragment>
          ))}
        </article>
        <aside className="hside">
          <p className="updated"><Icon name="clock" />Last updated {UPDATED}</p>
          <Link className="askus" to="/contact">
            <b>{doc.slug === 'terms' ? 'Questions about an order?' : 'Questions about your data?'}</b>
            <span>Write to us and say what you need.</span>
            <i>Contact us<Icon name="arrow" width={2} /></i>
          </Link>
        </aside>
      </div>
    </div>
  );
}

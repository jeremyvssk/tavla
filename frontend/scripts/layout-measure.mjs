// The in-page measurements behind layout-check.mjs: runs inside the page and returns what looks misaligned.
// Checks: content wider than the screen, near-miss edges (0.75-5px), table headers vs their column and
// their row's font, text baselines across a table row or flex row, control vs label centring, uneven gaps,
// cards side by side that end at different heights, text over text, and text cut off by its box.
export function measure(MAX) {
  const checks = {};
  const add = (k, msg) => (checks[k] ||= []).push(msg);
  const cs = el => getComputedStyle(el);
  const R = el => el.getBoundingClientRect();
  const visible = el => {
    const r = R(el); if (r.width < 2 || r.height < 2 || el.closest('.vh')) return false;
    const d = el.closest('details'); if (d && !d.open && !el.closest('summary')) return false;
    for (let e = el; e && e !== document.body; e = e.parentElement) {
      const s = cs(e); if (s.display === 'none' || s.visibility === 'hidden' || +s.opacity < 0.05) return false;
    }
    return true;
  };
  const txt = el => String(el.innerText || el.value || el.placeholder || el.getAttribute('aria-label') || '').trim().replace(/\s+/g, ' ');
  const name = el => {
    let s = el.tagName.toLowerCase(); const c = [...el.classList].slice(0, 2).join('.'); if (c) s += '.' + c;
    const t = txt(el).slice(0, 22); return t ? `${s} "${t}"` : s;
  };
  const ownText = el => [...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim());
  const lines = el => { // client rects of the element's own text, line by line
    const rs = [];
    for (const n of el.childNodes) if (n.nodeType === 3 && n.textContent.trim()) {
      const rg = document.createRange(); rg.selectNodeContents(n); rs.push(...[...rg.getClientRects()].filter(r => r.width > 0.5));
    }
    return rs;
  };
  const layer = el => { for (let e = el.parentElement; e; e = e.parentElement) { const s = cs(e); if ((s.position === 'absolute' || s.position === 'fixed') && s.zIndex !== 'auto') return e; } return null; };
  const ctx = document.createElement('canvas').getContext('2d');
  const ascent = el => { const s = cs(el); ctx.font = `${s.fontStyle} ${s.fontWeight} ${s.fontSize} ${s.fontFamily}`; return ctx.measureText('Hg').fontBoundingBoxAscent; };
  const baseline = el => { const l = lines(el)[0]; return l ? l.top + ascent(el) : null; };
  const filled = el => {
    const s = cs(el); const bg = s.backgroundColor;
    return (bg && bg !== 'transparent' && !/rgba\(.*,\s*0\)$/.test(bg)) || parseFloat(s.borderTopWidth) > 0 && s.borderTopStyle !== 'none' && parseFloat(s.borderLeftWidth) > 0 || s.boxShadow !== 'none' || s.backgroundImage !== 'none';
  };
  const isCtl = el => el.matches('input,select,textarea,button,img,svg,[role=switch],.sw i,.chip,.pill,.btn,.iconb') || (!txt(el) && R(el).width < 80 && R(el).height < 80 && filled(el));
  const all = [...document.querySelectorAll('body *')].filter(e => !e.closest('svg') || e.tagName === 'svg').filter(visible);
  const W = document.documentElement.clientWidth;

  // overx
  const clipped = e => { for (let a = e.parentElement; a && a !== document.body; a = a.parentElement) { const s = cs(a); if (s.overflowX !== 'visible' && !a.matches('.t')) return true; } return false; };
  for (const e of all) { const r = R(e); if (r.right > W + 1 && !clipped(e)) add('overx', `${name(e)} right=${r.right.toFixed(0)} > ${W}`); }

  // edges: near-miss left/right edges inside one region
  const REGION = 'section,form,aside,article,dialog,.panel,.modal,.adm-drawer,.card,li,header,footer,nav,main,table';
  const items = [];
  for (const e of all) {
    if (e.closest('table')) continue; // tables get the column check
    const s = cs(e); if (s.display === 'inline' && !e.parentElement.matches('[style*="flex"],[style*="grid"]') && !['flex', 'inline-flex', 'grid'].includes(cs(e.parentElement).display)) continue;
    let left, right, kind;
    if (e.tagName === 'svg' || e.tagName === 'IMG' || s.position === 'absolute') continue; // overlays sit where their box puts them
    if (isCtl(e) && !e.closest('button,.btn,.chip,.pill') || e.matches('button,.btn,.chip,.pill,input,select,textarea')) { const r = R(e); left = r.left; right = r.right; kind = 'box'; }
    else if (ownText(e) && !e.matches('button,.btn') && !e.closest('button,.btn,.chip,.pill,a.pill')) {
      if (s.textAlign === 'center') continue;
      { const ps = cs(e.parentElement); if (ps.justifyItems === 'center' || ps.display.includes('flex') && ps.flexDirection.startsWith('column') && ps.alignItems === 'center') continue; }
      const ls = lines(e); if (!ls.length) continue; left = Math.min(...ls.map(r => r.left)); right = Math.max(...ls.map(r => r.right)); kind = 'ink';
    } else continue;
    const reg = e.parentElement.closest(REGION); const r = R(e);
    let ta = s.textAlign;
    { const pr = R(e.parentElement), ps = cs(e.parentElement); const pR = pr.right - parseFloat(ps.paddingRight) - parseFloat(ps.borderRightWidth);
      const pL = pr.left + parseFloat(ps.paddingLeft) + parseFloat(ps.borderLeftWidth);
      if (Math.abs(r.right - pR) < 1 && Math.abs(r.left - pL) > 2 && ps.display.includes('flex')) ta = 'end';
      if (s.position === 'absolute' && s.right !== 'auto' && s.left === 'auto') ta = 'end'; }
    items.push({ e, left, right, kind, reg, top: r.top, bottom: r.bottom, ta });
  }
  const seen = new Set();
  for (let i = 0; i < items.length; i++) for (let j = i + 1; j < items.length; j++) {
    const a = items[i], b = items[j];
    if (a.reg !== b.reg || a.e.contains(b.e) || b.e.contains(a.e)) continue;
    if (a.e.parentElement === b.e.parentElement && cs(a.e.parentElement).flexWrap === 'wrap') continue; // wrapped chips can't align
    if (Math.min(Math.abs(a.top - b.bottom), Math.abs(b.top - a.bottom), Math.abs(a.top - b.top)) > 220) continue;
    if (a.top < b.bottom - 2 && b.top < a.bottom - 2) continue; // same row: left edges can't align anyway
    for (const side of ['left', 'right']) {
      const ra = x => x.ta === 'right' || x.ta === 'end';
      if (side === 'left' && (ra(a) || ra(b))) continue;
      if (side === 'right' && !(ra(a) && ra(b))) continue; // a right edge only matters when the thing is pinned right
      const d = Math.abs(a[side] - b[side]);
      if (d >= 1.5 && d <= 5) {
        const key = `${side}${Math.round(a[side])}/${Math.round(b[side])}`; if (seen.has(key)) continue; seen.add(key);
        add('edges', `${side} ${a[side].toFixed(1)} ${name(a.e)} vs ${b[side].toFixed(1)} ${name(b.e)} (${d.toFixed(1)}px) y${a.top.toFixed(0)}/${b.top.toFixed(0)}`);
      }
    }
  }

  // font: header cells in one row draw their text in the same typeface (a cell's class once swapped the font of a sortable header)
  for (const tr of document.querySelectorAll('thead tr')) {
    if (!visible(tr)) continue;
    const ink = [...tr.children].map((th) => [...th.querySelectorAll('*'), th].filter((e) => ownText(e) && visible(e)).pop()).filter(Boolean);
    const count = {}; for (const e of ink) { const f = cs(e).fontFamily; count[f] = (count[f] || 0) + 1; }
    const usual = Object.entries(count).sort((a, b) => b[1] - a[1])[0]?.[0];
    for (const e of ink) if (cs(e).fontFamily !== usual) add('font', `${name(e)} in ${cs(e).fontFamily.split(',')[0]}, the row's other headers in ${usual.split(',')[0]}`);
  }

  // cols: table header ink vs body cell ink, per column
  for (const t of document.querySelectorAll('table')) {
    if (!visible(t)) continue;
    const head = t.querySelector('thead tr'); const body = [...t.querySelectorAll('tbody tr')].filter(visible); if (!head || !body.length) continue;
    const hs = [...head.children];
    hs.forEach((th, ci) => {
      if (th.querySelector('.vh') && !ownText(th)) return; const thL = lines(th)[0] || (th.firstElementChild && lines(th.firstElementChild)[0]); if (!thL) return;
      const al = cs(th).textAlign;
      for (const tr of body.slice(0, 3)) {
        const td = tr.children[ci]; if (!td) continue;
        const parts = [td, ...td.querySelectorAll('*')].filter(x => visible(x) && !x.closest('svg') || x.tagName.toLowerCase() === 'svg' && visible(x));
        const boxes = parts.flatMap(x => x.matches('.st,.pill,.chip,.btn,button,span.flag,.role,img,svg,input,.th,.on2fa,.off2fa') ? [R(x)] : lines(x));
        if (!boxes.length) continue; const leaf = parts.find(x => ownText(x)) || td;
        const tdBox = { left: Math.min(...boxes.map(b => b.left)), right: Math.max(...boxes.map(b => b.right)) };
        const right = al === 'right' || al === 'end';
        if (al === 'center') { const d = Math.abs((tdBox.left + tdBox.right) / 2 - (thL.left + thL.right) / 2); if (d > 1.25) { add('cols', `col ${ci} "${txt(th).slice(0, 14)}" centre off by ${d.toFixed(1)}px`); break; } continue; }
        const d = right ? Math.abs(tdBox.right - thL.right) : Math.abs(tdBox.left - thL.left);
        if (d > 1.25) { add('cols', `col ${ci} "${txt(th).slice(0, 14)}" ${right ? 'right' : 'left'} ${right ? thL.right.toFixed(1) : thL.left.toFixed(1)} vs cell ${name(leaf)} ${(right ? tdBox.right : tdBox.left).toFixed(1)} (${d.toFixed(1)}px)`); break; }
      }
    });
    // base: text cells in one row share a baseline
    for (const tr of body.slice(0, 4)) {
      const bs = [...tr.children].map(td => { let l = td; while (l && !ownText(l) && l.firstElementChild) l = l.firstElementChild; return l && ownText(l) && !l.closest('.st,.pill,.chip,.btn,button,.flag') ? [l, baseline(l)] : null; }).filter(x => x && x[1] != null);
      if (bs.length > 1 && !bs.every((x, i) => i === 0 || R(x[0]).top >= R(bs[i - 1][0]).bottom - 1)) { const v = bs.map(x => x[1]); const sp = Math.max(...v) - Math.min(...v); if (sp > 1.5) { const lo = bs.find(x => x[1] === Math.max(...v)), hi = bs.find(x => x[1] === Math.min(...v)); add('base', `table row: ${name(hi[0])} ${hi[1].toFixed(1)} vs ${name(lo[0])} ${lo[1].toFixed(1)} (${sp.toFixed(1)}px)`); } }
    }
  }

  // flex rows: control vs label centring, and text baselines
  for (const p of all) {
    const s = cs(p); if (!(s.display.includes('flex') && s.flexDirection.startsWith('row')) && !(s.display === 'grid')) continue;
    const kids = [...p.children].filter(visible).filter(k => cs(k).position !== 'absolute');
    for (let i = 0; i < kids.length; i++) for (let j = 0; j < kids.length; j++) {
      if (i === j) continue; const c = kids[i], t = kids[j];
      const rc = R(c), rt = R(t);
      if (!(rc.top < rt.bottom && rt.top < rc.bottom)) continue; // not side by side
      if (Math.abs(rc.right - rt.left) > 40 && Math.abs(rt.right - rc.left) > 40) continue; // not neighbours
      const ctl = isCtl(c) && !txt(c) && rc.height <= 44 && Math.abs(i - j) === 1 ? c : null; // a control answers to the label right next to it
      if (ctl && txt(t) && !isCtl(t)) {
        let leaf = t; while (leaf && !ownText(leaf) && leaf.firstElementChild) leaf = leaf.firstElementChild;
        const l1 = leaf && lines(leaf)[0]; if (!l1) continue;
        const cc = (rc.top + rc.bottom) / 2, lc = (l1.top + l1.bottom) / 2, bc = (rt.top + rt.bottom) / 2;
        const dl = Math.abs(cc - lc), db = Math.abs(cc - bc);
        if (dl > 2 && db > 2) add('control', `${name(c)} centre ${cc.toFixed(1)} vs label line ${lc.toFixed(1)} / block ${bc.toFixed(1)} of ${name(t)} (${Math.min(dl, db).toFixed(1)}px)`);
      }
      if (i < j && ownText(c) && ownText(t) && !filled(c) && !filled(t) && cs(p).alignItems !== 'baseline' && !(rt.height > 1.8 * parseFloat(cs(t).lineHeight || 20) || rc.height > 1.8 * parseFloat(cs(c).lineHeight || 20))) {
        const b1 = baseline(c), b2 = baseline(t);
        if (b1 != null && b2 != null && Math.abs(b1 - b2) > 1.5 && Math.abs(b1 - b2) < 14) add('base', `${name(c)} ${b1.toFixed(1)} vs ${name(t)} ${b2.toFixed(1)} (${Math.abs(b1 - b2).toFixed(1)}px)`);
      }
    }
  }

  // label.sw style switches: the track vs the label text inside it
  for (const sw of document.querySelectorAll('.sw')) {
    if (!visible(sw)) continue; const tr = sw.querySelector('i'); if (!tr) continue;
    const t = [...sw.querySelectorAll('*')].find(x => ownText(x)) || (ownText(sw) ? sw : null); if (!t) continue;
    const l1 = lines(t)[0]; const rc = R(tr); if (!l1) continue;
    const cc = (rc.top + rc.bottom) / 2, lc = (l1.top + l1.bottom) / 2, bc = (R(t).top + R(t).bottom) / 2;
    if (Math.abs(cc - lc) > 2 && Math.abs(cc - bc) > 2) add('control', `switch ${name(sw)} track ${cc.toFixed(1)} vs label ${lc.toFixed(1)} (${Math.abs(cc - lc).toFixed(1)}px)`);
  }

  // rhythm: same-kind siblings, equal gaps
  for (const p of all) {
    if (p.matches('tr,thead,tbody,table')) continue;
    const kids = [...p.children].filter(visible).filter(k => cs(k).position !== 'absolute'); if (kids.length < 3) continue;
    const groups = {}; let run = null, prev = null;
    kids.forEach(k => { const key = k.tagName + '.' + [...k.classList].join('.'); if (key !== prev) run = (groups[key + '#' + kids.indexOf(k)] = []); run.push(k); prev = key; });
    for (const [g, ks] of Object.entries(groups)) {
      if (ks.length < 3) continue;
      const rs = ks.map(R); const rows = rs.every((r, i) => i === 0 || Math.abs(r.top - rs[0].top) < 2);
      const cols = rs.every((r, i) => i === 0 || r.top >= rs[i - 1].bottom - 1);
      let gaps = null;
      if (rows) gaps = rs.slice(1).map((r, i) => r.left - rs[i].right);
      else if (cols && rs.every((r, i) => i === 0 || Math.abs(r.left - rs[0].left) < 2)) gaps = rs.slice(1).map((r, i) => r.top - rs[i].bottom);
      if (!gaps) continue;
      const sp = Math.max(...gaps) - Math.min(...gaps);
      if (sp > 1.5 && Math.max(...gaps) < 200) add('rhythm', `${name(p)} > ${g.split('#')[0].toLowerCase()} gaps ${gaps.map(x => x.toFixed(0)).join(',')}`);
    }
  }

  // stack: groups in a form or panel body sit on one vertical rhythm (the drawer that crammed its switches under the chips)
  for (const p of all.filter(e => e.matches('form,fieldset,.modal-b,.panel-b,.adm-drawer > div'))) {
    const all2 = [...p.children].filter(visible).filter(k => cs(k).position !== 'absolute');
    const skip = k => k.matches('h2,h3,legend,.err,.hint,.formfoot,.foot-end,.modal-f,.btn,button'); // the action button gets its own breathing room
    const kids = all2.filter(k => !skip(k));
    if (kids.length < 4) continue;
    const rs = kids.map(R); if (!rs.every((r, i) => i === 0 || r.top >= rs[i - 1].bottom - 1)) continue;
    const gaps = rs.slice(1).map((r, i) => all2.indexOf(kids[i + 1]) - all2.indexOf(kids[i]) > 1 ? null : r.top - rs[i].bottom).filter(g => g !== null);
    if (gaps.length < 2) continue;
    if (Math.max(...gaps) - Math.min(...gaps) > 3) add('stack', `${name(p)} gaps ${gaps.map(x => x.toFixed(0)).join(',')}`);
  }

  // balance: side-by-side cards should end together
  for (const p of all) {
    const s = cs(p); if (!(s.display.includes('grid') || (s.display.includes('flex') && s.flexDirection.startsWith('row')))) continue;
    const kids = [...p.children].filter(visible).filter(k => filled(k) && R(k).height > 60);
    for (let i = 0; i < kids.length; i++) for (let j = i + 1; j < kids.length; j++) {
      const a = R(kids[i]), b = R(kids[j]);
      if (Math.abs(a.top - b.top) > 2) continue;
      if (Math.abs(a.bottom - b.bottom) > 2) add('balance', `${name(kids[i])} bottom ${a.bottom.toFixed(0)} vs ${name(kids[j])} ${b.bottom.toFixed(0)} (${Math.abs(a.bottom - b.bottom).toFixed(0)}px)`);
    }
  }

  // icon-only buttons: icon centred
  for (const b of all.filter(e => e.matches('button,a,.iconb') && !txt(e).replace(e.getAttribute('aria-label') || '', '').trim())) {
    const svg = b.querySelector(':scope > svg'); if (!svg || b.innerText.trim()) continue;
    const rb = R(b), ri = R(svg);
    const dx = Math.abs((rb.left + rb.right) / 2 - (ri.left + ri.right) / 2), dy = Math.abs((rb.top + rb.bottom) / 2 - (ri.top + ri.bottom) / 2);
    if (dx > 1 || dy > 1) add('icon', `${name(b)} icon off centre by ${dx.toFixed(1)},${dy.toFixed(1)}`);
  }

  // overlap: text drawn over text
  const texts = all.filter(e => ownText(e)).map(e => ({ e, ls: lines(e) }));
  for (let i = 0; i < texts.length; i++) for (let j = i + 1; j < texts.length; j++) {
    const a = texts[i], b = texts[j]; if (a.e.contains(b.e) || b.e.contains(a.e) || layer(a.e) !== layer(b.e)) continue;
    // a line's content box is taller than its ink (ascent/descent room), so compare the middle 70% of each line
    const ink = r => ({ left: r.left, right: r.right, top: r.top + r.height * .15, bottom: r.bottom - r.height * .15 });
    const hit = a.ls.some(x => b.ls.some(y => { x = ink(x); y = ink(y); return x.left < y.right - 1 && y.left < x.right - 1 && x.top < y.bottom - 1 && y.top < x.bottom - 1; }));
    if (hit) add('overlap', `${name(a.e)} over ${name(b.e)}`);
  }

  // clip + tiny
  for (const e of all) {
    const s = cs(e);
    if (ownText(e) && parseFloat(s.fontSize) < 11) add('tiny(info)', `${name(e)} ${s.fontSize}`);
    if ((s.overflow + s.overflowX + s.overflowY).includes('hidden') && txt(e) && e.matches(':not(.t)')) {
      const cut = e.scrollWidth > e.clientWidth + 1 || e.scrollHeight > e.clientHeight + 2;
      const scroller = s.overflowX !== 'visible' && (s.display.includes('flex') && !s.flexWrap.includes('wrap') || s.display.includes('grid') && s.gridAutoFlow.includes('column') || s.overflowX === 'auto' || s.overflowX === 'scroll');
      if (cut) add(scroller ? 'scroller(info)' : s.textOverflow === 'ellipsis' || s.webkitLineClamp !== 'none' ? 'clip(info)' : 'clip', `${name(e)} ${e.scrollWidth}x${e.scrollHeight} in ${e.clientWidth}x${e.clientHeight}`);
    }
  }
  for (const k in checks) { const n = checks[k].length; checks[k] = checks[k].slice(0, MAX); if (n > MAX) checks[k].push(`... ${n - MAX} more`); }
  return checks;
}


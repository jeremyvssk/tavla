// Line icons from the Tavla design: one 24px stroke set, coloured by currentColor.
const PATHS = {
  search: <><circle cx="11" cy="11" r="6.5" /><path d="M20 20l-4.2-4.2" /></>,
  user: <><circle cx="12" cy="8.2" r="3.6" /><path d="M4.8 20c.6-3.8 3.6-6 7.2-6s6.6 2.2 7.2 6" /></>,
  arrow: <path d="M4 12h15M13 6l6 6-6 6" />,
  left: <path d="M20 12H5M11 6l-6 6 6 6" />,
  chevL: <path d="M15 6l-6 6 6 6" />,
  down: <path d="M6 9l6 6 6-6" />,
  menu: <path d="M4 7h16M4 12h16M4 17h16" />,
  close: <path d="M6 6l12 12M18 6L6 18" />,
  check: <path d="M5 12.5l4.5 4.5L19 7.5" />,
  sliders: <><path d="M4 7h10M18 7h2M4 17h4M12 17h8" /><circle cx="16" cy="7" r="2" /><circle cx="10" cy="17" r="2" /></>,
  truck: <><path d="M2 7.5h11v9H2z" /><path d="M13 10.5h4.2l3 3.2v2.8H13z" /><circle cx="6.2" cy="18.2" r="1.7" /><circle cx="16.8" cy="18.2" r="1.7" /></>,
  back: <><path d="M20 12a8 8 0 1 1-2.6-5.9" /><path d="M20 4v4.6h-4.6" /></>,
  expand: <path d="M14 4h6v6M10 20H4v-6M20 4l-6.5 6.5M4 20l6.5-6.5" />,
  bag: <><path d="M5.2 8.5h13.6l-1 11.5H6.2z" /><path d="M9 8.5V7a3 3 0 0 1 6 0v1.5" /></>,
  plus: <path d="M12 5.5v13M5.5 12h13" />,
  minus: <path d="M5.5 12h13" />,
  bolt: <path d="M13.2 3L5.5 13.4h5.6L10.3 21l7.9-10.6h-5.7z" />,
};

export type IconName = keyof typeof PATHS;

export default function Icon({ name, width = 1.8 }: { name: IconName; width?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={width}
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {PATHS[name]}
    </svg>
  );
}

export function Star({ off }: { off?: boolean }) {
  return (
    <svg viewBox="0 0 24 24" className={off ? 'off' : undefined} aria-hidden="true">
      <path fill="currentColor" d="M12 2.8l2.8 6 6.5.7-4.9 4.4 1.4 6.4L12 17l-5.8 3.3 1.4-6.4-4.9-4.4 6.5-.7z" />
    </svg>
  );
}

/** The three delivery promises; the top line, the phone menu and the product page all show them. */
export function Perks() {
  return (
    <>
      <span><Icon name="truck" />Free shipping over €100</span>
      <span><Icon name="bolt" />Fast shipping</span>
      <span><Icon name="back" />30-day returns</span>
    </>
  );
}

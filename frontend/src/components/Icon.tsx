// Line icons from the Tavla design: one 24px stroke set, coloured by currentColor.
const PATHS = {
  search: <><circle cx="11" cy="11" r="6.5" /><path d="M20 20l-4.2-4.2" /></>,
  user: <><circle cx="12" cy="8.2" r="3.6" /><path d="M4.8 20c.6-3.8 3.6-6 7.2-6s6.6 2.2 7.2 6" /></>,
  arrow: <path d="M4 12h15M13 6l6 6-6 6" />,
  left: <path d="M20 12H5M11 6l-6 6 6 6" />,
  chevL: <path d="M15 6l-6 6 6 6" />,
  down: <path d="M6 9l6 6 6-6" />,
  up: <path d="M6 15l6-6 6 6" />,
  grip: <path d="M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01" />,
  more: <path d="M6 12h.01M12 12h.01M18 12h.01" />,
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
  chevR: <path d="M9 6l6 6-6 6" />,
  info: <><circle cx="12" cy="12" r="9" /><path d="M12 11v5M12 8h.01" /></>,
  alert: <><circle cx="12" cy="12" r="9" /><path d="M12 7.5v5.5M12 16.5h.01" /></>,
  clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
  lock: <><rect x="5" y="10.5" width="14" height="10" rx="2" /><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" /></>,
  card: <><rect x="3" y="5.5" width="18" height="13" rx="2" /><path d="M3 10h18M7 15h3" /></>,
  instagram: <><rect x="3.5" y="3.5" width="17" height="17" rx="5" /><circle cx="12" cy="12" r="3.8" /><path d="M17.2 6.8h.01" /></>,
  youtube: <><rect x="2.5" y="5.5" width="19" height="13" rx="4" /><path d="M10.3 9.3v5.4l4.6-2.7z" /></>,
  facebook: <path d="M14 21v-7.5h2.6l.4-3H14V8.6c0-.9.3-1.5 1.6-1.5H17V4.4c-.3 0-1.2-.1-2.3-.1-2.3 0-3.8 1.4-3.8 3.9v2.3H8.3v3H11V21" />,
  x: <path d="M4.5 4.5l6.3 8.4-6.3 6.6h1.6l5.4-5.7 4.3 5.7h3.7l-6.6-8.8 5.9-6.2h-1.6l-5 5.3-4-5.3z" />,
  mail: <><rect x="3" y="5.5" width="18" height="13" rx="2" /><path d="M3.5 7l8.5 6.5L20.5 7" /></>,
  pin: <><path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11z" /><circle cx="12" cy="10" r="2.4" /></>,
  thumb: <path d="M7.5 10.5V20H4.5v-9.5zM7.5 10.5l3.8-6.3c1.4 0 2.3 1.1 2 2.5l-.7 3.3h5.3c1.2 0 2.1 1.1 1.8 2.3l-1.5 6.4c-.2.8-1 1.3-1.8 1.3H7.5" />,
  flag: <path d="M5.5 21V4M5.5 4.5h11l-2 4 2 4h-11" />,
  pencil: <path d="M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4" />,
  trash: <path d="M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13M10 11v5.5M14 11v5.5" />,
  upload: <path d="M12 16V4.5M7 9l5-5 5 5M4.5 15v4.5h15V15" />,
  download: <path d="M12 4.5V16M7 11l5 5 5-5M4.5 15v4.5h15V15" />,
  eye: <><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" /><circle cx="12" cy="12" r="3" /></>,
  grid: <path d="M4.5 4.5h6v6h-6zM13.5 4.5h6v6h-6zM4.5 13.5h6v6h-6zM13.5 13.5h6v6h-6z" />,
  list: <path d="M4.5 6h3M4.5 12h3M4.5 18h3M10.5 6h9M10.5 12h9M10.5 18h9" />,
  shield: <><path d="M12 3l7.5 3v5.5c0 4.6-3.2 8.3-7.5 9.5-4.3-1.2-7.5-4.9-7.5-9.5V6z" /><path d="M8.8 12.2l2.2 2.2 4.2-4.4" /></>,
  box: <><path d="M3.5 7.5L12 3l8.5 4.5v9L12 21l-8.5-4.5z" /><path d="M3.5 7.5L12 12l8.5-4.5M12 12v9" /></>,
  users: <><circle cx="9" cy="8.5" r="3.3" /><path d="M2.8 19.5c.5-3.4 3.1-5.5 6.2-5.5s5.7 2.1 6.2 5.5M16 5.4a3.2 3.2 0 0 1 0 6.2M18 14.3c1.8.7 3 2.6 3.2 5.2" /></>,
  tag: <><path d="M3.5 12.5V4h8.5l8.5 8.5-8 8z" /><circle cx="8" cy="8.5" r="1.4" /></>,
  layers: <path d="M12 3.5l9 4.5-9 4.5L3 8zM3 12l9 4.5 9-4.5M3 16l9 4.5 9-4.5" />,
  home: <path d="M4 11l8-6.5 8 6.5V20h-5.5v-5.5h-5V20H4z" />,
  chat: <path d="M4 5.5h16v11H9l-5 4z" />,
  log: <><rect x="5" y="3.5" width="14" height="17" rx="2" /><path d="M8.5 8h7M8.5 12h7M8.5 16h4" /></>,
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

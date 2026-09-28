# Link Streamz Android port checklist

The native port keeps the source HTML as the visual and behavioral reference:

- App shell: `.app`, `body`, and `html` — radial orange glow over the black linear background, centered 440px max width, safe-area aware.
- Home header: `header`, `.brand`, `.logo`, `header h1`, `header p`, `.head-actions` — gradient play mark, Syncopate title, mono subtitle, settings button.
- Header action: `.icon-btn` — 38px square, 12px radius, translucent gradient, 1px line, pressed scale.
- Filter rail: `.chips`, `.chip`, `.chip.on`, `.chip b` — horizontal scrolling pills with orange active state and mono counts.
- Event list: `.list` — 16px horizontal inset, 10px vertical gap, preserved scroll position.
- Loading state: `.skel`, `@keyframes sh` — 96px shimmer cards.
- Shared event/highlight card: `.card`, `.card.live`, `.lg`, `.row`, `.side`, `.crest`, `.ctr` — 18px corners, 1px line, exact card padding, three-column match row.
- Sport identity: `.lg svg`, `sportIcon()` — 13px orange sport icon and uppercase league line.
- Team crest: `.crest`, `.crest img`, `.f-*`, `.b*` — 36px circular flag/gradient badge with optional logo image.
- Live center: `.ctr.live-now`, `.min`, `@keyframes blink` — signal icon and mono match minute.
- Upcoming center: `.ctr.countdown`, `.t small` — “STARTS IN” label and mono countdown.
- Finished center: `.ctr.dim` — muted “Ended” mono label.
- Highlight center: `.play-ctr .play-circle`, `.hcard`, `.dur-chip` — orange gradient 34px play circle and duration chip.
- Empty/error feed: `.empty`, `.btn` — server error message, retry action, and empty list copy.
- Player header: `.pbar`, `.ttl`, `.pbar h2`, `.pbar small` — back button, uppercase event title, mono sport/league label.
- Player stage: `.stage`, `video`, `.stage:fullscreen` — 16:9 stage with 12px gutters, 14px radius, dark shadow, edge-to-edge fullscreen.
- Stage overlay: `.overlay`, `.stage.ui`, `.o-top`, `.o-mid`, `.o-bot` — gradient controls, fade visibility, exact control group spacing.
- Player status: `.livebadge` — orange gradient LIVE/HIGHLIGHT/REPLAY badge.
- Transport controls: `.pc`, `.pc.big`, `.pc.skip`, `.time` — circular controls, 64px play button, 42px side buttons, mono times.
- Seek bar: `.seek`, track/thumb pseudo-elements — 4px orange/white track and 14px white-bordered thumb.
- Loading/error overlays: `.spin`, `.err`, `.err.on`, `.btn.ghost` — spinner, unavailable message, retry and server switch actions.
- Seek feedback: `.sk`, `.sk.l`, `.sk.r`, `.sk.on` — transient -10s/+10s chips on the corresponding side.
- Lock state: `.lockrow`, `.unlock`, `.stage.locked` — hidden controls and top-corner unlock buttons.
- “Also live” section: `.pbody`, `.sect`, `.more`, `.mini`, `.mini.cur`, `.playing-now`, `.eq`, `@keyframes eq` — related stream list and equalizer.
- Toast: `.toast`, `.toast.on` — bottom pill with shadow and cubic-bezier transition.
- Server popover: `.srvpop`, `.srvpop.on`, `.srvpop::before`, `.srvpop-item`, `.srvpop-item.on` — anchored vertical switcher, arrow, active row, quality chip.
- Initial server dialog: `.dlg`, `.dbox`, `.dhead`, `.dlist`, `.srv` — centered 360px modal with fade/translate/scale entrance.
- Settings body: `#sbody`, `.gt`, `.group`, `.srow`, `.srow.col` — grouped settings panels and section labels.
- Settings icon/text rows: `.ic`, `.tx`, `.segc`, `.sw`, `.field` — orange icon wells, descriptions, segmented controls, switches, custom URL field.
- Settings footer: `.about` — mono version label.
- Responsive framing: `@media(min-width:600px) .app` — desktop border and outer shadow.
- Shared transitions: `--ease: cubic-bezier(.2,.8,.2,1)` — custom Compose easing for screen and control motion.
- Shared shadows/gradients: `--sh-1`, `--sh-2`, `--sh-accent`, `.app`, `.card`, `.stage`, `.srvpop`, `.dbox` — Compose `shadow()` and `drawBehind` approximations are marked in source.
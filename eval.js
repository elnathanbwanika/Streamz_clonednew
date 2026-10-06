
/* ═══════════ Utilities ═══════════ */
const $ = id => 
function esc(v){ return String(v).replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c])); }

/* ═══════════ Test videos ═══════════
   Free public sample clips. Replace `video` on any item with a real stream URL.
   Movies/episodes use the long (~10-15 min) clips; live channels use a looping pool. */
const G = 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/';
const LONG = [
  G + 'BigBuckBunny.mp4', G + 'ElephantsDream.mp4', G + 'SubaruOutbackOnStreetAndDirt.mp4', G + 'VolkswagenGTIReview.mp4',
  G + 'WeAreGoingOnBullrun.mp4', G + 'WhatCarCanYouGetForAGrand.mp4', G + 'TearsOfSteel.mp4', G + 'Sintel.mp4',
];
const SHORT = [
  G + 'ForBiggerBlazes.mp4', G + 'ForBiggerEscapes.mp4', G + 'ForBiggerFun.mp4', G + 'ForBiggerJoyrides.mp4', G + 'ForBiggerMeltdowns.mp4',
  'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4',
  'https://www.w3schools.com/html/mov_bbb.mp4',
  'https://media.w3.org/2010/05/sintel/trailer.mp4',
  'https://media.w3.org/2010/05/bunny/trailer.mp4',
  'https://media.w3.org/2010/05/bunny/movie.mp4',
  'https://media.w3.org/2010/05/video/movie_300.mp4',
  'https://vjs.zencdn.net/v/oceans.mp4',
  'https://test-videos.co.uk/vids/jellyfish/mp4/h264/720/Jellyfish_720_10s_1MB.mp4',
  'https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4',
  'https://test-videos.co.uk/vids/sintel/mp4/h264/720/Sintel_720_10s_1MB.mp4',
  'https://samplelib.com/lib/preview/mp4/sample-5s.mp4',
  'https://samplelib.com/lib/preview/mp4/sample-10s.mp4',
  'https://filesamples.com/samples/video/mp4/sample_640x360.mp4',
  'https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-mp4-file.mp4',
];
const BACKUP = [   // tried in order when a clip can't be loaded
  'https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4',
  'https://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4',
  'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4',
];
const CLIP_DUR = {   // approximate runtimes of the long clips
  'BigBuckBunny.mp4':'9:56', 'ElephantsDream.mp4':'10:53', 'SubaruOutbackOnStreetAndDirt.mp4':'9:54', 'VolkswagenGTIReview.mp4':'9:45',
  'WeAreGoingOnBullrun.mp4':'9:29', 'WhatCarCanYouGetForAGrand.mp4':'9:27', 'TearsOfSteel.mp4':'12:14', 'Sintel.mp4':'14:48',
};
const durOf = url => CLIP_DUR[url.split('/').pop()] || '';
const longClip = i => LONG[i % LONG.length];

/* ═══════════ Catalog ═══════════ */
const mkMovie = (n, vj, translated, title, genre, year, seed, clip) => {
  const video = longClip(clip);
  return { id:'mv-' + n, kind:'movie', vj, translated, title, genre, year, video, dur:durOf(video), thumb:`https://picsum.photos/seed/${seed}/400/250` };
};
const MOVIES = [
  mkMovie(1,  'VJ Emmy',   true,  'The Last Stand of the Fallen King', 'ACTION',    2025, 'm1',  0),
  mkMovie(2,  'VJ Emmy',   true,  'Nightfall Over Nairobi',            'THRILLER',  2024, 'm2',  1),
  mkMovie(3,  'VJ Emmy',   true,  'Rivers of Dust',                    'WESTERN',   2021, 'm7',  2),
  mkMovie(4,  'VJ Ulio',   true,  'Broken Compass',                    'ADVENTURE', 2024, 'm5',  3),
  mkMovie(5,  'VJ Ulio',   true,  'The Long Road Home',                'DRAMA',     2023, 'm6',  4),
  mkMovie(6,  'VJ Marley', true,  'The Quiet Sea',                     'DRAMA',     2021, 'm11', 5),
  mkMovie(7,  'VJ Marley', true,  'Savanna Run',                       'ADVENTURE', 2024, 'm12', 6),
  mkMovie(8,  'VJ Ulio',   true,  'The Last Harvest',                  'DRAMA',     2020, 'm13', 7),
  mkMovie(9,  'VJ Emmy',   true,  'Midnight Express',                  'THRILLER',  2022, 'm14', 3),
  mkMovie(10, null,        false, 'Golden Hour',                       'DRAMA',     2023, 'm3',  6),
  mkMovie(11, null,        false, 'Blackout',                          'ACTION',    2025, 'm9',  7),
  mkMovie(12, null,        false, 'Iron Tides',                        'ACTION',    2024, 'm15', 0),
];
const mkSeries = (n, vj, translated, title, genre, year, seasons) => {
  let k = n * 2;
  return { id:'sr-' + n, kind:'series', vj, translated, title, genre, year, thumb:`https://picsum.photos/seed/sr${n}/600/340`,
    seasons: seasons.map((eps, si) => ({ number:si + 1, episodes: eps.map((t, ei) => {
      const video = longClip(k++);
      return { n:ei + 1, title:t, video, dur:durOf(video), thumb:`https://picsum.photos/seed/e${n}${si + 1}${ei + 1}/200/120` };
    }) })) };
};
const SERIES = [
  mkSeries(1, 'VJ Emmy',   true,  'Silent Witness',      'MYSTERY',  2024, [['The Beginning', 'Shadows', 'The Bridge', 'Cold Trail'], ['Homecoming', 'The Letter', 'Broken Glass']]),
  mkSeries(2, 'VJ Ulio',   true,  'Steel Horizon',       'SCI-FI',   2023, [['Ignition', 'Orbit', 'The Signal'], ['Decay', 'Re-entry']]),
  mkSeries(3, 'VJ Marley', true,  'Echoes of Tomorrow',  'SCI-FI',   2024, [['Yesterday', 'Tomorrow', 'Paradox']]),
  mkSeries(4, null,        false, 'Crimson Signal',      'THRILLER', 2023, [['Red Static', 'Dead Air', 'Interference']]),
  mkSeries(5, null,        false, 'Iron Coast',          'ACTION',   2022, [['Landfall', 'The Wall', 'Undertow']]),
];
/* name, category, country, initials, colour 1, colour 2, current programme */
const CH_RAW = [
  ['NBS TV','General','Uganda','NBS','#7a3fff','#3f1f8a','News at 9'], ['Bukedde TV','General','Uganda','BKD','#1a6dff','#003bb0','Agataliiko Nfuufu'],
  ['UBC TV','General','Uganda','UBC','#16a34a','#065f1f','National News'], ['NTV Uganda','News','Uganda','NTV','#dc2626','#7f1d1d','The Market'],
  ['Spark TV','Entertainment','Uganda','SPK','#ec4899','#831843','The Junction'], ['BBS TV','General','Uganda','BBS','#eab308','#78350f','Amasengejje'],
  ['Salt TV','Entertainment','Uganda','SLT','#14b8a6','#0f766e','Music Live'], ['Urban TV','Music','Uganda','URB','#f97316','#7c2d12','The Countdown'],
  ['SuperSport Football','Sports','South Africa','SSF','#0ea5e9','#075985','Champions League'], ['SuperSport Cricket','Sports','South Africa','SSC','#16a34a','#14532d','Test Cricket Day 2'],
  ['SuperSport Premier','Sports','South Africa','SSP','#8b5cf6','#4c1d95','Manchester Derby'], ['BeIN Sports 1','Sports','Qatar','BS1','#e11d48','#7f1d1d','La Liga'],
  ['BeIN Sports 2','Sports','Qatar','BS2','#e11d48','#7f1d1d','Serie A'], ['Sky Sports F1','Sports','UK','SKF','#dc2626','#991b1b','Race Weekend'],
  ['ESPN','Sports','USA','ESP','#dc2626','#450a0a','NBA Tonight'], ['Eurosport 1','Sports','France','EU1','#0ea5e9','#0c4a6e','Tour de France'],
  ['BBC World News','News','UK','BBC','#dc2626','#7f1d1d','World News'], ['CNN International','News','USA','CNN','#dc2626','#450a0a','Amanpour'],
  ['Al Jazeera','News','Qatar','AJZ','#eab308','#78350f','The Stream'], ['Sky News','News','UK','SKY','#dc2626','#7f1d1d','Sky News Tonight'],
  ['France 24','News','France','F24','#0ea5e9','#0c4a6e','The Debate'], ['DW News','News','Germany','DW','#1e40af','#1e1b4b','DW News'],
  ['Africanews','News','France','AFN','#7c3aed','#4c1d95','Journal'], ['CNBC Africa','News','Kenya','CNB','#0ea5e9','#075985','Power Lunch'],
  ['MTV','Entertainment','USA','MTV','#ec4899','#831843','Top 20 Video Countdown'], ['Comedy Central','Entertainment','USA','CC','#f59e0b','#78350f','Stand-Up Showcase'],
  ['E! Entertainment','Entertainment','USA','E!','#a855f7','#4c1d95','Red Carpet Live'], ['TNT','Entertainment','USA','TNT','#dc2626','#7f1d1d','Movie Marathon'],
  ['USA Network','Entertainment','USA','USA','#0ea5e9','#0c4a6e','Suits'], ['Bravo','Entertainment','USA','BRV','#a855f7','#581c87','Top Chef'],
  ['Cartoon Network','Kids','USA','CN','#000000','#1f2937','Ben 10'], ['Nickelodeon','Kids','USA','NIC','#f97316','#7c2d12','SpongeBob'],
  ['Disney Channel','Kids','USA','DIS','#1e40af','#312e81','Mickey Mouse Clubhouse'], ['Boomerang','Kids','USA','BMG','#f59e0b','#78350f','Tom & Jerry'],
  ['CBeebies','Kids','UK','CBB','#0ea5e9','#0c4a6e','Bluey'], ['PBS Kids','Kids','USA','PBS','#16a34a','#065f1f','Daniel Tiger'],
  ['MTV Base','Music','UK','MB','#ec4899','#831843','The Official Chart'], ['Trace TV','Music','France','TRC','#7c3aed','#4c1d95','Afrobeats Top 10'],
  ['BET','Music','USA','BET','#dc2626','#7f1d1d','106 & Park'], ['VH1','Music','USA','VH1','#0ea5e9','#075985','Behind the Music'],
  ['Zee TV','Entertainment','India','ZEE','#a855f7','#4c1d95','Kumkum Bhagya'], ['Star Plus','Entertainment','India','STR','#0ea5e9','#075985','Anupamaa'],
  ['Colors TV','Entertainment','India','CLR','#f97316','#7c2d12','Bigg Boss'], ['CCTV-4','News','China','CCT','#dc2626','#7f1d1d','News Hour'],
  ['NHK World','News','Japan','NHK','#1e40af','#1e1b4b','NHK News 7'], ['Arirang TV','Entertainment','Korea','ARI','#ec4899','#831843','K-Pop Star'],
  ['RT News','News','Russia','RT','#16a34a','#065f1f','RT News'], ['TV5 Monde','General','France','TV5','#0ea5e9','#0c4a6e','Journal'],
];
const CH_POOL = [...SHORT, ...LONG];   // 27 distinct clips; stride 4 keeps neighbouring channels different
const CHANNELS = CH_RAW.map((r, i) => ({ id:'ch-' + (i + 1), kind:'channel', title:r[0], cat:r[1], country:r[2], initials:r[3], c1:r[4], c2:r[5], program:r[6], video:CH_POOL[(i * 4) % CH_POOL.length] }));

const ALL = [...MOVIES, ...SERIES, ...CHANNELS];
const byId = id => ALL.find(x => x.id === id);
const epCount = s => s.seasons.reduce((a, x) => a + x.episodes.length, 0);
const episodeOf = (s, ep) => { const se = s.seasons.find(x => x.number === ep.season); return se && se.episodes.find(x => x.n === ep.n); };
const isLocal = ch => ch.country === 'Uganda';
const initials = name => (name || '').replace(/^VJ\s*/i, '').trim().split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase();

/* ═══════════ Sections: movies & channels ═══════════ */
const ICON = {
  film:  '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 9h18M7 5v4M17 5v4"/></svg>',
  user:  '<svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="3.5"/><path d="M4.5 20c0-3.5 3.4-6.5 7.5-6.5s7.5 3 7.5 6.5"/></svg>',
  tv:    '<svg viewBox="0 0 24 24"><rect x="3" y="6" width="18" height="12" rx="2"/><path d="M8 21h8M12 18v3"/></svg>',
  globe: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a15 15 0 010 18M12 3a15 15 0 000 18"/></svg>',
};
const CHECK = '<svg class="check" viewBox="0 0 24 24"><path d="M5 12l5 5L20 7"/></svg>';
const PLAY_SVG = '<svg viewBox="0 0 24 24"><path d="M7 4v16l13-8z"/></svg>';
const ARROW_SVG = '<svg viewBox="0 0 24 24"><path d="M9 5l7 7-7 7"/></svg>';
const CAT_ABBR = k => String(k).slice(0, 3).toUpperCase();

const SEC = {
  movies: {
    id:'movies', eyebrow:'Movies', noun:'item', search:'Search movies, series or VJs', items:[...MOVIES, ...SERIES],
    text: it => [it.title, it.vj, it.genre, it.year].join(' '),
    rows: [
      { key:'newly',          title:'Newly Added',     ids:['mv-1', 'sr-1', 'mv-4', 'sr-2', 'mv-2', 'sr-3', 'mv-9'] },
      { key:'trendingMovies', title:'Trending Movies', ids:['mv-1', 'mv-4', 'mv-2', 'mv-6', 'mv-5', 'mv-3', 'mv-7', 'mv-8', 'mv-10', 'mv-11'] },
      { key:'trendingSeries', title:'Trending Series', ids:SERIES.map(s => s.id) },
    ],
    filter: {
      primary:[{ key:'true', label:'Translated' }, { key:'false', label:'Non-translated' }], def:'true',
      inScope: (it, st) => String(it.translated) === st.primary,
      dd1: { label:'Type', icon:ICON.film, all:false, def:'movie', get:it => it.kind, name:k => k === 'movie' ? 'Movies' : 'Series' },
      dd2: { label:'VJ', icon:ICON.user, all:true, get:it => it.vj, name:k => k, av:initials, visible:st => st.primary === 'true' },
      parts: st => [st.primary === 'true' ? 'Translated' : 'Non-translated', st.f1 === 'movie' ? 'Movies' : 'Series', st.f2 && st.f2.replace(/^VJ\s*/i, '')],
    },
  },
  channels: {
    id:'channels', eyebrow:'Channels', noun:'channel', search:'Search channels or categories', items:CHANNELS,
    text: it => [it.title, it.cat, it.country, it.program].join(' '),
    rows: [
      { key:'local',  title:'Local Channels', dot:'red', ids:CHANNELS.filter(isLocal).map(c => c.id) },
      { key:'sports', title:'Sports',         dot:'red', ids:CHANNELS.filter(c => c.cat === 'Sports').map(c => c.id) },
      { key:'news',   title:'News',           ids:CHANNELS.filter(c => c.cat === 'News' && !c.id.match(/^ch-4[3-9]$/)).map(c => c.id) },
      { key:'ent',    title:'Entertainment',  ids:CHANNELS.filter(c => c.cat === 'Entertainment' && !isLocal(c)).slice(0, 6).map(c => c.id) },
      { key:'kids',   title:'Kids & Family',  ids:CHANNELS.filter(c => c.cat === 'Kids').map(c => c.id) },
      { key:'music',  title:'Music',          ids:CHANNELS.filter(c => c.cat === 'Music').map(c => c.id) },
      { key:'intl',   title:'International',  ids:CHANNELS.slice(40).map(c => c.id) },
    ],
    filter: {
      primary:[{ key:'local', label:'Local' }, { key:'abroad', label:'Abroad' }], def:'local',
      inScope: (it, st) => st.primary === 'local' ? isLocal(it) : !isLocal(it),
      dd1: { label:'Category', icon:ICON.tv, all:true, def:null, get:it => it.cat, name:k => k },
      dd2: { label:'Country', icon:ICON.globe, all:true, get:it => it.country, name:k => k, visible:st => st.primary === 'abroad' },
      parts: st => [st.primary === 'local' ? 'Local' : 'Abroad', st.f1, st.primary === 'abroad' && st.f2],
    },
  },
};
const TAB_LABEL = { all:'All', live:'Live', upcoming:'Upcoming', finished:'Finished', highlights:'Highlights' };
const freshState = sec => ({ screen:'home', pageKey:null, primary:sec.filter.def, f1:sec.filter.dd1.def, f2:null });
Object.values(SEC).forEach(s => { s.st = freshState(s); });

let tab = 'movies', searchOpen = false, searchQuery = '';
const S = () => SEC[tab], ST = () => SEC[tab].st;
const rowList = (sec, row) => row.ids.map(byId).filter(Boolean);

/* ---- filtering ---- */
const scope = (sec, st) => sec.items.filter(it => sec.filter.inScope(it, st));
function matches(sec, st){
  const f = sec.filter; let l = scope(sec, st);
  if (st.f1) l = l.filter(it => f.dd1.get(it) === st.f1);
  if (st.f2 && f.dd2.visible(st)) l = l.filter(it => f.dd2.get(it) === st.f2);
  return l;
}
function group(list, get){
  const m = new Map();
  list.forEach(it => { const k = get(it); if (k) m.set(k, (m.get(k) || 0) + 1); });
  return [...m].map(([key, count]) => ({ key, count })).sort((a, b) => String(a.key).localeCompare(String(b.key)));
}
const dd1Options = (sec, st) => group(scope(sec, st), sec.filter.dd1.get);
const dd2Options = (sec, st) => group(scope(sec, st).filter(it => !st.f1 || sec.filter.dd1.get(it) === st.f1), sec.filter.dd2.get);

/* ---- cards ---- */
function tagOf(it){
  const series = it.kind === 'series', raw = !it.translated;
  if (series && raw) return ['raw-series', 'RAW'];
  if (series) return ['series', 'SERIES'];
  if (raw) return ['raw', 'RAW'];
  return ['', 'MOVIE'];
}
function chCard(ch){
  return `<button class="chcard" type="button" data-id="${ch.id}">
    <div class="chcard-logo" style="background:linear-gradient(140deg,${ch.c1},${ch.c2})"><span class="initials">${esc(ch.initials)}</span><span class="chcard-live">Live</span></div>
    <div class="chcard-info"><div class="chcard-name">${esc(ch.title)}</div><div class="chcard-prog">${esc(ch.program)}</div></div></button>`;
}
function hCard(it){
  if (it.kind === 'channel') return chCard(it);
  const [mod, txt] = tagOf(it), series = it.kind === 'series';
  const meta = series ? `<span>S${it.seasons.length}</span><span class="dot"></span><span>${epCount(it)} EP</span>` : `<span>${it.dur}</span>`;
  return `<button class="hcard" type="button" data-id="${it.id}">
    <div class="hcard-thumb"><img src="${it.thumb}" alt="" loading="lazy"><span class="hcard-tag ${mod}">${txt}</span><span class="hcard-play">${PLAY_SVG}</span><span class="hcard-meta">${meta}</span></div>
    <div class="hcard-info"><div class="hcard-title">${esc(it.title)}</div><div class="hcard-sub">${esc(it.genre)} · ${it.year}</div></div></button>`;
}
function gridCard(it){
  if (it.kind === 'channel') return chCard(it);
  const [mod, txt] = tagOf(it), series = it.kind === 'series';
  const stats = series ? `<div class="movie-stats">${it.seasons.length} season${it.seasons.length === 1 ? '' : 's'} · ${epCount(it)} episodes</div>` : '';
  return `<button class="movie" type="button" data-id="${it.id}">
    <div class="movie-thumb"><img src="${it.thumb}" alt="" loading="lazy"><span class="movie-dur">${series ? 'S' + it.seasons.length : it.dur}</span><span class="movie-tag ${mod}">${txt}</span><span class="movie-play">${PLAY_SVG}</span></div>
    <div class="movie-info">${it.vj ? `<div class="movie-vj">${esc(it.vj)}</div>` : ''}<div class="movie-title">${esc(it.title)}</div><div class="movie-sub">${esc(it.genre)} · ${it.year}</div>${stats}
      <span class="watch">${PLAY_SVG}${series ? 'Browse' : 'Watch'}</span></div></button>`;
}
const gridWrap = list => tab === 'channels' ? `<div class="chgrid">${list.map(gridCard).join('')}</div>` : `<div class="movies grid-view">${list.map(gridCard).join('')}</div>`;
const empty = (t, p) => `<div class="empty"><b>${t}</b><p>${p}</p></div>`;

/* ---- views ---- */
function renderHome(){
  const sec = S();
  $('content').innerHTML = sec.rows.map(row => {
    const list = rowList(sec, row);
    if (!list.length) return '';
    return `<div class="home-section"><div class="section-head">
      <div class="section-title"><span class="cat-dot ${row.dot || ''}"></span>${esc(row.title)}</div>
      <button class="viewall-btn" type="button" data-viewall="${row.key}">View all ${ARROW_SVG}</button></div>
      <div class="${sec.id === 'channels' ? 'chrow' : 'hcard-row'}">${list.map(hCard).join('')}</div></div>`;
  }).join('');
}
function renderPage(){
  const sec = S(), row = sec.rows.find(r => r.key === ST().pageKey);
  if (!row) return;
  const list = rowList(sec, row);
  $('pageEyebrow').textContent = sec.eyebrow; $('pageTitle').textContent = row.title; $('pageCount').textContent = list.length;
  $('content').innerHTML = list.length ? gridWrap(list) : empty('Nothing here yet', 'Check back soon.');
}
function renderGrid(){
  const list = matches(S(), ST());
  $('content').innerHTML = list.length ? gridWrap(list) : empty('Nothing matches', 'Try a different filter.');
}
function renderSearch(){
  const sec = S(), list = sec.items.filter(it => sec.text(it).toLowerCase().includes(searchQuery));
  $('content').innerHTML = list.length ? gridWrap(list) : empty(`No results for "${esc(searchQuery)}"`, 'Try a different title, category or name.');
}
function renderContent(){
  if (!SEC[tab]){ $('content').innerHTML = empty(TAB_LABEL[tab], "This tab isn't part of the Movies & Channels build."); return; }
  const st = ST();
  if (searchOpen && searchQuery) renderSearch();
  else if (st.screen === 'home') renderHome();
  else if (st.screen === 'page') renderPage();
  else renderGrid();
}
function updateBars(){
  const real = !!SEC[tab], page = real && ST().screen === 'page' && !searchOpen;
  $('actionRow').classList.toggle('hidden', !real || searchOpen || page);
  $('pageBar').classList.toggle('on', page);
}
function setSection(id){
  if (searchOpen) closeSearchBar(true);
  tab = id;
  
    const on = b.dataset.sec === id;
    b.classList.toggle('on', on);
    if (on) b.setAttribute('aria-current', 'page'); else b.removeAttribute('aria-current');
  });
  if (SEC[id]) $('searchInput').placeholder = SEC[id].search;
  renderFilterUI(); renderFilterLabel(); updateBars(); renderContent(); 
}
$('chips').addEventListener('click', e => {
  const b = e.target.closest('.chip'); if (!b) return;
  setSection(b.dataset.sec);
  b.scrollIntoView({ inline:'center', block:'nearest', behavior:'smooth' });
});
$('pageBack').onclick = () => { ST().screen = 'home'; ST().pageKey = null; updateBars(); renderFilterLabel(); renderContent(); };
$('content').addEventListener('click', e => {
  const va = e.target.closest('.viewall-btn');
  if (va){ ST().screen = 'page'; ST().pageKey = va.dataset.viewall; updateBars(); renderContent(); 
  const card = e.target.closest('[data-id]');
  if (card){ const it = byId(card.dataset.id); if (it) openWatch(it); }
});

/* ---- search ---- */
function openSearchBar(){
  searchOpen = true; searchQuery = '';
  $('searchBar').classList.add('on'); $('searchInput').value = ''; $('clearSearch').classList.remove('on');
  updateBars(); renderContent(); setTimeout(() => $('searchInput').focus(), 30);
}
function closeSearchBar(silent){
  searchOpen = false; searchQuery = '';
  $('searchBar').classList.remove('on');
  if (silent === true) return;
  updateBars(); renderContent();
}
$('openSearch').onclick = openSearchBar;
$('closeSearch').onclick = () => closeSearchBar();
$('searchInput').addEventListener('input', e => {
  searchQuery = e.target.value.trim().toLowerCase();
  $('clearSearch').classList.toggle('on', searchQuery.length > 0);
  renderContent();
});
$('clearSearch').onclick = () => { $('searchInput').value = ''; searchQuery = ''; $('clearSearch').classList.remove('on'); $('searchInput').focus(); renderContent(); };

/* ---- filter modal (one modal, driven by the active section's spec) ---- */
function ddOpts(spec, opts, cur){
  const total = opts.reduce((a, o) => a + o.count, 0), av = spec.av || CAT_ABBR;
  const all = spec.all ? `<button class="dd-opt ${!cur ? 'on' : ''}" type="button" data-k=""><span class="av">ALL</span><span class="name">All</span><span class="count">${total}</span>${CHECK}</button>` : '';
  return all + opts.map(o => `<button class="dd-opt ${cur === o.key ? 'on' : ''}" type="button" data-k="${esc(o.key)}"><span class="av">${esc(av(o.key))}</span><span class="name">${esc(spec.name(o.key))}</span><span class="count">${o.count}</span>${CHECK}</button>`).join('');
}
function renderFilterUI(){
  if (!SEC[tab]) return;
  const sec = S(), st = ST(), f = sec.filter;
  $('seg').innerHTML = f.primary.map(p => `<button type="button" data-k="${p.key}" class="${p.key === st.primary ? 'on' : ''}">${p.label} <span class="cnt">${scope(sec, { ...st, primary:p.key }).length}</span></button>`).join('');
  [['dd1', f.dd1, st.f1, dd1Options(sec, st)], ['dd2', f.dd2, st.f2, dd2Options(sec, st)]].forEach(([id, spec, cur, opts]) => {
    $(id + 'Ic').innerHTML = spec.icon;
    $(id + 'Label').innerHTML = `<em>${spec.label}</em> ${esc(cur ? spec.name(cur) : 'All')}`;
    $(id + 'Btn').classList.toggle('has-value', !!cur);
    $(id + 'Panel').innerHTML = ddOpts(spec, opts, cur);
  });
  const two = f.dd2.visible(st);
  $('dd2Cell').style.display = two ? '' : 'none';
  $('ddRow').classList.toggle('single', !two);
  renderFmCount();
}
function renderFilterLabel(){
  const btn = $('filterToggle'), label = $('filterLabel'), badge = $('filterBadge');
  if (!SEC[tab] || ST().screen !== 'grid'){ label.className = 'label blank'; label.textContent = 'Filter'; btn.classList.remove('has-filter'); badge.textContent = ''; return; }
  label.className = 'label';
  label.innerHTML = `<em>Filter</em> ${S().filter.parts(ST()).filter(Boolean).map(esc).join(' · ')}`;
  btn.classList.add('has-filter'); badge.textContent = matches(S(), ST()).length;
}
function renderFmCount(){
  if (!SEC[tab]) return;
  const sec = S(), n = ST().screen === 'grid' ? matches(sec, ST()).length : sec.items.length;
  $('fmCount').textContent = `${n} ${sec.noun}${n === 1 ? '' : 's'}`;
}
function afterFilter(){ renderFilterUI(); renderFilterLabel(); updateBars(); renderContent(); }
function closeAllDropdowns(){
  
  
}
function toggleDropdown(n){
  const btn = $('dd' + n + 'Btn'), panel = $('dd' + n + 'Panel'), was = panel.classList.contains('on');
  closeAllDropdowns();
  if (!was){ panel.classList.add('on'); btn.classList.add('open'); }
}
function openFilterModal(){ renderFilterUI(); $('filterScrim').classList.add('on'); $('filterModal').classList.add('on'); }
function closeFilterModal(){ closeAllDropdowns(); $('filterScrim').classList.remove('on'); $('filterModal').classList.remove('on'); }
$('filterToggle').onclick = openFilterModal;
$('fmClose').onclick = closeFilterModal;
$('fmDone').onclick = closeFilterModal;
$('fmReset').onclick = () => { S().st = freshState(S()); closeAllDropdowns(); afterFilter(); };
$('filterScrim').onclick = () => { if (
$('fmCard').addEventListener('click', e => { if (!e.target.closest('.dd-cell') && 
$('seg').onclick = e => {
  const b = e.target.closest('button'); if (!b) return;
  const st = ST(); st.primary = b.dataset.k; st.f1 = S().filter.dd1.def; st.f2 = null; st.screen = 'grid'; st.pageKey = null;
  afterFilter();
  const nb = $('seg').querySelector('button.on'); if (nb){ nb.classList.add('pulse'); setTimeout(() => nb.classList.remove('pulse'), 500); }
};
$('dd1Btn').onclick = e => { e.stopPropagation(); toggleDropdown(1); };
$('dd2Btn').onclick = e => { e.stopPropagation(); toggleDropdown(2); };
$('dd1Panel').onclick = e => { const o = e.target.closest('.dd-opt'); if (!o) return; const st = ST(); st.f1 = o.dataset.k || null; st.f2 = null; st.screen = 'grid'; st.pageKey = null; afterFilter(); closeAllDropdowns(); };
$('dd2Panel').onclick = e => { const o = e.target.closest('.dd-opt'); if (!o) return; const st = ST(); st.f2 = o.dataset.k || null; st.screen = 'grid'; st.pageKey = null; afterFilter(); closeAllDropdowns(); };

/* ═══════════ Watch screen: inline player + related list ═══════════ */
const W = { open:false, item:null, ep:null, season:1, cand:[], ci:0, lastFocus:null };
const V = $('videoEl'), pf = $('pf');
const PLAY_P = 'M7 4v16l13-8z', PAUSE_P = 'M6 4h4v16H6zM14 4h4v16h-4z';
const VOL_ON = '<path d="M3 9v6h4l5 5V4L7 9zM16 8.5a5 5 0 010 7l1.4 1.4a7 7 0 000-9.8z"/>';
const VOL_OFF = '<path d="M3 9v6h4l5 5V4L7 9z"/><path d="M16 9l5 6M21 9l-5 6" stroke="#fff" stroke-width="2" stroke-linecap="round" fill="none"/>';
const fmt = s => { s = Math.max(0, Math.floor(s || 0)); const h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60, p = n => String(n).padStart(2, '0'); return h ? `${h}:${p(m)}:${p(x)}` : `${p(m)}:${p(x)}`; };
let uiTimer = null;
const spin = on => $('pfSpin').classList.toggle('on', on);
function showUi(){ pf.classList.add('ui'); clearTimeout(uiTimer); uiTimer = setTimeout(() => { if (!V.paused) pf.classList.remove('ui'); }, 3500); }
function showErr(t, m){ spin(false); $('pfErrT').textContent = t; $('pfErrM').textContent = m; $('pfErr').classList.add('on'); }
const hideErr = () => $('pfErr').classList.remove('on');
function syncMute(){
  pf.classList.toggle('muted', V.muted);
  $('pfMuteIcon').innerHTML = V.muted ? VOL_OFF : VOL_ON;
  $('pfHint').textContent = V.muted ? 'Tap to unmute' : 'Sound on';
  $('pfMute').setAttribute('aria-label', V.muted ? 'Unmute' : 'Mute');
}
function buildCandidates(url){
  const list = [url];
  if (url.includes('commondatastorage.googleapis.com')) list.push(url.replace('commondatastorage.googleapis.com', 'storage.googleapis.com'));
  return [...new Set([...list, ...BACKUP])];
}
function startPlayback(){
  spin(true); hideErr();
  V.src = W.cand[W.ci]; V.load(); V.muted = false;
  const p = V.play();
  if (p && p.catch) p.catch(err => {
    if (err && err.name === 'AbortError') return;
    if (err && err.name === 'NotAllowedError'){ V.muted = true; V.play().catch(() => { spin(false); showUi(); }); }   // autoplay with sound blocked → start muted
  });
  syncMute();
}
function loadVideo(item, url){
  const live = item.kind === 'channel';
  W.cand = buildCandidates(url); W.ci = 0;
  pf.classList.toggle('is-live', live); pf.classList.toggle('is-vod', !live);
  V.loop = live;
  $('pfCur').textContent = '00:00'; $('pfDur').textContent = '--:--'; $('pfSeek').value = 0; $('pfSeek').style.setProperty('--p', '0%');
  showUi(); startPlayback();
}

/* ---- related-list cards (same look as the event cards in the reference screen) ---- */
const ev = o => `<button class="ev${o.now ? ' now' : ''}" type="button" ${o.attrs}>
  <div class="ev-cat"><span>${esc(o.cat)}</span></div>
  <div class="ev-row">${o.logo}<div class="ev-name"><b>${esc(o.name)}</b>${o.sub ? `<small>${esc(o.sub)}</small>` : ''}</div>${o.status}</div></button>`;
const LIVE_STATUS = '<span class="ev-status"><i class="ev-dot"></i>Live</span>';
const NOW_STATUS = '<span class="ev-status"><span class="eq"><b></b><b></b><b></b></span>Playing</span>';
const vodStatus = dur => `<span class="ev-status"><span class="ev-ic"><svg class="pl" viewBox="0 0 24 24"><path d="M7 4v16l13-8z"/></svg></span>${dur || ''}</span>`;
const thumbLogo = src => `<span class="ev-logo sq"><img src="${src}" alt="" loading="lazy"></span>`;
function evItem(it){
  if (it.kind === 'channel')
    return ev({ attrs:`data-id="${it.id}"`, cat:`${it.cat} | ${it.country}`, name:it.title, sub:it.program, status:LIVE_STATUS,
      logo:`<span class="ev-logo" style="background:linear-gradient(140deg,${it.c1},${it.c2})">${esc(it.initials)}</span>` });
  const series = it.kind === 'series';
  return ev({ attrs:`data-id="${it.id}"`, cat:`${it.genre} | ${it.year}${it.vj ? ' | ' + it.vj : ''}`, name:it.title,
    sub: series ? `${it.seasons.length} season${it.seasons.length === 1 ? '' : 's'} · ${epCount(it)} episodes` : (it.translated ? 'Translated' : 'Raw'),
    status: vodStatus(series ? 'S' + it.seasons.length : it.dur), logo:thumbLogo(it.thumb) });
}
function evEpisode(s, se, e){
  const now = W.ep && W.ep.season === se.number && W.ep.n === e.n;
  return ev({ attrs:`data-ep="${se.number}.${e.n}"`, now, cat:`${s.title} | S${se.number} · EP ${e.n}`, name:e.title, sub:s.vj || s.genre,
    status: now ? NOW_STATUS : vodStatus(e.dur), logo:thumbLogo(e.thumb) });
}
function relatedFor(item){
  const rest = (list, same) => list.filter(x => x.id !== item.id).sort((a, b) => (same(b) ? 1 : 0) - (same(a) ? 1 : 0));
  if (item.kind === 'channel') return { label:'More channels', list:rest(CHANNELS, x => x.cat === item.cat) };
  if (item.kind === 'series')  return { label:'More series',   list:rest(SERIES,   x => x.translated === item.translated) };
  return { label:'More movies', list:rest(MOVIES, x => x.translated === item.translated) };
}
function renderLists(){
  const item = W.item; let html = '';
  if (item.kind === 'series'){
    const se = item.seasons.find(x => x.number === W.season) || item.seasons[0];
    html += `<div class="ws-block"><div class="ws-label">Episodes</div>
      <div class="season-scroll">${item.seasons.map(x => `<button class="season-chip ${x.number === se.number ? 'on' : ''}" type="button" data-season="${x.number}">Season ${x.number}<span class="n">${x.episodes.length} EP</span></button>`).join('')}</div>
      <div class="ev-list">${se.episodes.map(e => evEpisode(item, se, e)).join('')}</div></div>`;
  }
  const rel = relatedFor(item);
  if (rel.list.length) html += `<div class="ws-block"><div class="ws-label">${rel.label}</div><div class="ev-list">${rel.list.slice(0, 24).map(evItem).join('')}</div></div>`;
  $('wsScroll').innerHTML = html;
}
$('wsScroll').addEventListener('click', e => {
  const chip = e.target.closest('.season-chip');
  if (chip){ W.season = +chip.dataset.season; renderLists(); return; }
  const epBtn = e.target.closest('[data-ep]');
  if (epBtn){ const [season, n] = epBtn.dataset.ep.split('.').map(Number); openWatch(W.item, { season, n }); return; }
  const card = e.target.closest('[data-id]');
  if (card){ const it = byId(card.dataset.id); if (it) openWatch(it); }
});

/* ---- open / close ---- */
function subtitleOf(item, ep, epObj){
  if (item.kind === 'channel') return `${item.cat} | ${item.country}`;
  if (item.kind === 'series') return `S${ep.season} E${ep.n} | ${epObj.title}`;
  return `${item.genre} | ${item.year}${item.vj ? ' | ' + item.vj : ''}`;
}
function openWatch(item, ep){
  if (item.kind === 'series' && !ep) ep = { season:item.seasons[0].number, n:item.seasons[0].episodes[0].n };
  const epObj = ep ? episodeOf(item, ep) : null;
  if (item.kind === 'series' && !epObj) return;
  if (!W.open) W.lastFocus = 
  W.item = item; W.ep = ep || null; W.season = ep ? ep.season : 1; W.open = true;
  $('wsTitle').textContent = item.title;
  $('wsSub').textContent = subtitleOf(item, ep, epObj);
  $('wscreen').classList.add('on');
  loadVideo(item, epObj ? epObj.video : item.video);
  renderLists(); $('wsScroll').scrollTop = 0;
  setTimeout(() => $('wsBack').focus({ preventScroll:true }), 40);
}
function closeWatch(){
  if (!W.open) return;
  W.open = false;
  try { V.pause(); } catch(e){}
  V.removeAttribute('src'); try { V.load(); } catch(e){}
  if (
  $('wscreen').classList.remove('on'); spin(false); hideErr(); clearTimeout(uiTimer);
  if (W.lastFocus && 
  W.item = null; W.ep = null;
}
$('wsBack').onclick = closeWatch;
$('pfRetry').onclick = () => { if (W.item){ W.ci = 0; startPlayback(); } };

/* ---- player controls & events ---- */
$('pfPlay').onclick = () => { if (V.paused) V.play().catch(() => {}); else V.pause(); showUi(); };
$('pfBack').onclick = () => { V.currentTime = Math.max(0, V.currentTime - 10); showUi(); };
$('pfForward').onclick = () => { if (isFinite(V.duration)) V.currentTime = Math.min(V.duration, V.currentTime + 10); showUi(); };
$('pfMute').onclick = () => { V.muted = !V.muted; showUi(); };
$('pfFs').onclick = () => {
  if (
  else if (pf.requestFullscreen) pf.requestFullscreen();
  else if (V.webkitEnterFullscreen) V.webkitEnterFullscreen();
  showUi();
};
if (!
$('pfPip').onclick = async () => {
  try { if (
  showUi();
};
$('pfSeek').addEventListener('input', e => {
  if (isFinite(V.duration) && V.duration > 0) V.currentTime = e.target.value / 1000 * V.duration;
  e.target.style.setProperty('--p', e.target.value / 10 + '%'); showUi();
});
pf.addEventListener('click', e => {
  if (e.target.closest('button,input')) return;
  if (pf.classList.contains('ui')) pf.classList.remove('ui'); else showUi();
});
['loadstart', 'waiting'].forEach(n => V.addEventListener(n, () => { if (W.open) spin(true); }));
['playing', 'canplay'].forEach(n => V.addEventListener(n, () => spin(false)));
V.addEventListener('play', () => { $('pfPlayIcon').innerHTML = `<path d="${PAUSE_P}"/>`; hideErr(); });
V.addEventListener('pause', () => { $('pfPlayIcon').innerHTML = `<path d="${PLAY_P}"/>`; if (W.open) showUi(); });
V.addEventListener('volumechange', syncMute);
V.addEventListener('ended', showUi);
V.addEventListener('loadedmetadata', () => { if (isFinite(V.duration)) $('pfDur').textContent = fmt(V.duration); });
V.addEventListener('timeupdate', () => {
  $('pfCur').textContent = fmt(V.currentTime);
  if (isFinite(V.duration) && V.duration > 0){ const p = V.currentTime / V.duration * 1000; $('pfSeek').value = p; $('pfSeek').style.setProperty('--p', p / 10 + '%'); }
});
V.addEventListener('error', () => {
  if (!V.getAttribute('src') || !W.open) return;
  console.warn('Video failed:', W.cand[W.ci]);
  if (W.ci < W.cand.length - 1){ W.ci++; startPlayback(); return; }
  const live = W.item && W.item.kind === 'channel';
  showErr(live ? 'Channel unavailable' : "Can't play this video", 'None of the test sources could be reached. Check your connection and try again.');
});

/* ═══════════ Keyboard, scroll lock, housekeeping ═══════════ */

  if (e.key === 'Escape'){
    if (W.open) closeWatch();
    else if ($('filterModal').classList.contains('on')) closeFilterModal();
    else if (searchOpen) closeSearchBar();
    return;
  }
  if (!W.open) return;
  const tag = 
  if (tag === 'INPUT' || tag === 'TEXTAREA') return;
  const vod = W.item && W.item.kind !== 'channel';
  if ((e.key === ' ' || e.key === 'k') && tag !== 'BUTTON'){ e.preventDefault(); $('pfPlay').click(); }
  else if (e.key === 'ArrowLeft' && vod){ e.preventDefault(); $('pfBack').click(); }
  else if (e.key === 'ArrowRight' && vod){ e.preventDefault(); $('pfForward').click(); }
  else if (e.key === 'm'){ $('pfMute').click(); }
  else if (e.key === 'f'){ $('pfFs').click(); }
});

/* Lock page scroll while an overlay is open */
const overlays = ['wscreen', 'filterModal'].map($);
const syncScrollLock = () => 
const lockObserver = new MutationObserver(syncScrollLock);
overlays.forEach(el => lockObserver.observe(el, { attributes:true, attributeFilter:['class'] }));

/* Broken thumbnails fall back to the neutral placeholder background */


/* ═══════════ Init ═══════════ */
$('cntMovies').textContent = SEC.movies.items.length;
$('cntChannels').textContent = CHANNELS.length;
syncMute();
setSection('movies');

fs.writeFileSync('movies.json', JSON.stringify(movies, null, 2));
fs.writeFileSync('series.json', JSON.stringify(series, null, 2));
fs.writeFileSync('channels.json', JSON.stringify(channels, null, 2));

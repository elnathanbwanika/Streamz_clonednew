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
const fs = require('fs');
fs.writeFileSync('movies.json', JSON.stringify(MOVIES, null, 2));
fs.writeFileSync('series.json', JSON.stringify(SERIES, null, 2));
fs.writeFileSync('channels.json', JSON.stringify(CHANNELS, null, 2));

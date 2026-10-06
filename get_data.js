const fs = require('fs');

const html = fs.readFileSync('prototype.html', 'utf-8');
const scriptMatch = html.match(/<script>([\s\S]*?)<\/script>/);
let script = scriptMatch[1];
script = script.replace(/const \$ =.*/, 'const $ = () => ({});');
script = script.replace(/document\.[a-zA-Z]+/g, '{}'); // strip document.body etc
script = script.replace(/window\.[a-zA-Z]+/g, '{}');
script = script.replace(/localStorage/g, '{}');
script = script.replace(/function init\(\).*/s, ''); // remove the rest after init

script += `
fs.writeFileSync('movies.json', JSON.stringify(MOVIES, null, 2));
fs.writeFileSync('series.json', JSON.stringify(SERIES, null, 2));
fs.writeFileSync('channels.json', JSON.stringify(CHANNELS, null, 2));
`;

fs.writeFileSync('extract.js', script);

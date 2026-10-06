const fs = require('fs');
const vjs = { 'A': 'VJ A', 'B': 'VJ B' };
const videoBucket = 'https://storage.googleapis.com/gtv-videos-bucket/sample/';
const pools = {
  long: [
    videoBucket + 'BigBuckBunny.mp4',
    videoBucket + 'ElephantsDream.mp4',
    videoBucket + 'Sintel.mp4',
    videoBucket + 'TearsOfSteel.mp4',
    videoBucket + 'SubaruOutbackOnStreetAndDirt.mp4',
    videoBucket + 'VolkswagenGTIReview.mp4',
  ],
  short: [
    'https://mdn.github.io/learning-area/html/multimedia-and-embedding/video-and-audio-content/rabbit320.mp4',
    'https://media.w3.org/2010/05/sintel/trailer.mp4',
    videoBucket + 'ForBiggerBlazes.mp4',
    videoBucket + 'ForBiggerEscapes.mp4',
    videoBucket + 'ForBiggerFun.mp4',
    videoBucket + 'ForBiggerJoyrides.mp4',
    videoBucket + 'ForBiggerMeltdowns.mp4',
  ]
};
let longIndex = 0; function getLongVideo() { const v = pools.long[longIndex]; longIndex = (longIndex + 1) % pools.long.length; return v; }
let shortIndex = 0; function getShortVideo() { const v = pools.short[shortIndex]; shortIndex = (shortIndex + 1) % pools.short.length; return v; }

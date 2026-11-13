'use strict';
/* ================= SONORA — music player ================= */
const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];

/* Demo stream library (royalty-free demo tracks) + the user's own device songs. */
const DEMO = 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-';
const TRACKS = [
  { id:'t1', title:'Midnight Drive',      artist:'Neon Coast',   genre:'Chill',   hue:265, src:DEMO+'1.mp3',
    lyrics:[[0,'Midnight roads and city lights'],[7,'Your voice is riding by my side'],[14,'Every mile is pulling me closer'],[21,'To the place where the stars collide'],[28,'So keep the engine humming low'],[35,'We are chasing the afterglow']] },
  { id:'t2', title:'Purple Skyline',      artist:'Aria Vale',    genre:'Pop',     hue:290, src:DEMO+'2.mp3',
    lyrics:[[0,'Paint the sky in violet blue'],[7,'Every window shining through'],[14,'We are dancing on the rooftop'],[21,'And the whole world fades from view'],[28,'Purple skyline over you'],[35,'Every dream is coming true']] },
  { id:'t3', title:'Echoes In The Dark',  artist:'KAIRO',        genre:'Electronic', hue:230, src:DEMO+'3.mp3',
    lyrics:[[0,'Echoes calling in the dark'],[7,'Every beat ignites a spark'],[14,'Shadows moving on the wall'],[21,'Hear the silence start to fall'],[28,'We are echoes, we are light'],[35,'Burning through the endless night']] },
  { id:'t4', title:'Golden Hour',         artist:'Luna Waves',   genre:'Chill',   hue:320, src:DEMO+'4.mp3',
    lyrics:[[0,'Golden hour on your skin'],[7,'Let the evening pull us in'],[14,'Salt and sugar on the breeze'],[21,'Time is moving through the trees'],[28,'Hold this moment, keep it slow'],[35,'Watch the amber rivers flow']] },
  { id:'t5', title:'Heartbeat City',      artist:'Milo Rey',     genre:'Pop',     hue:275, src:DEMO+'5.mp3',
    lyrics:[[0,'Heartbeat city never sleeps'],[7,'Every promise that it keeps'],[14,'Neon rivers, midnight trains'],[21,'Running wild inside my veins'],[28,'Feel it louder, feel it true'],[35,'This city beats because of you']] },
  { id:'t6', title:'Dancing Shadows',     artist:'Nova Rae',     genre:'Electronic', hue:250, src:DEMO+'6.mp3',
    lyrics:[[0,'Dancing shadows on the floor'],[7,'Every move is asking more'],[14,'Turn the music up tonight'],[21,'Lose ourselves inside the light'],[28,'Spin me round and round again'],[35,'Till the morning finds us then']] },
  { id:'t7', title:'Ocean Eyes',          artist:'The Wanderers', genre:'Rock',    hue:210, src:DEMO+'7.mp3',
    lyrics:[[0,'Ocean eyes and open skies'],[7,'Every truth and every lie'],[14,'Sailing where the wild winds blow'],[21,'There is nowhere else to go'],[28,'Take me where the waves run free'],[35,'You and I, the endless sea']] },
  { id:'t8', title:'Starlight Run',       artist:'Ivy Sol',      genre:'Jazz',    hue:300, src:DEMO+'8.mp3',
    lyrics:[[0,'Starlight running through the town'],[7,'Silk and silver slowing down'],[14,'Every corner hums a tune'],[21,'Underneath a paper moon'],[28,'Take my hand and do not wait'],[35,'Love is never running late']] },
  { id:'t9', title:'Voltage Heart',       artist:'KAIRO',        genre:'Electronic', hue:240, src:DEMO+'9.mp3',
    lyrics:[[0,'Voltage running through my heart'],[7,'Every signal, every part'],[14,'Turn it up and let it spark'],[21,'Light the corners of the dark'],[28,'We are wired to the sound'],[35,'Feet are lifting off the ground']] },
  { id:'t10', title:'Slow Gravity',       artist:'Aria Vale',    genre:'Jazz',    hue:285, src:DEMO+'10.mp3',
    lyrics:[[0,'Slow gravity is pulling me'],[7,'Down where all the rivers flee'],[14,'Every word you say is gold'],[21,'Every story left untold'],[28,'Drift a little closer now'],[35,'We will find our way somehow']] },
];

const store = {
  get(k, d){ try { const v = localStorage.getItem('sonora:'+k); return v ? JSON.parse(v) : d; } catch(e){ return d; } },
  set(k, v){ try { localStorage.setItem('sonora:'+k, JSON.stringify(v)); } catch(e){} }
};

const state = {
  queue: TRACKS.map(t => t.id),
  queueName: 'Made For You',
  index: -1,
  playing: false,
  shuffle: store.get('shuffle', false),
  repeat: store.get('repeat', 0), // 0 off, 1 all, 2 one
  favorites: new Set(store.get('favorites', ['t1','t3'])),
  playlists: store.get('playlists', [
    { id:'p1', name:'My Favorites', hue:330, trackIds:['t1','t3','t5'] },
    { id:'p2', name:'Chill Vibes',  hue:265, trackIds:['t1','t4','t10'] },
    { id:'p3', name:'Workout Mix',  hue:245, trackIds:['t3','t6','t9'] },
    { id:'p4', name:'Party Time',   hue:295, trackIds:['t2','t5','t6','t9'] },
  ]),
  locals: [],           // {id,title,artist:'Your Device',src(objectURL),local:true,hue}
  genre: 'All',
  addToTrackId: null,
};

/* Two pipelines: demo streams play directly (always reliable); device songs
   play through a Web Audio chain so the equalizer really works on them. */
const streamAudio = $('#audio');
const localAudio = document.createElement('audio');
localAudio.preload = 'metadata';
let audio = streamAudio;
const savedVol = store.get('volume', 0.9);
streamAudio.volume = savedVol; localAudio.volume = savedVol;
$('#volBar').value = Math.round(savedVol * 100);

function allTracks(){ return TRACKS.concat(state.locals); }
function trackById(id){ return allTracks().find(t => t.id === id); }
function current(){ return state.index >= 0 ? trackById(state.queue[state.index]) : null; }

/* ---------- cover art ---------- */
function coverStyle(t){ return `background:linear-gradient(135deg,hsl(${t.hue} 90% 62%),hsl(${t.hue+45} 85% 40%) 55%,hsl(${t.hue-30} 90% 55%))`; }
function coverHTML(t, cls){ return `<div class="${cls}" style="${coverStyle(t)}"><div class="cover-art"></div></div>`; }

/* ---------- toast ---------- */
let toastTimer;
function toast(msg){
  const el = $('#toast'); el.textContent = msg; el.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => el.hidden = true, 2400);
}

/* ---------- navigation ---------- */
function goto(view){
  $$('.view').forEach(v => v.classList.toggle('active', v.id === 'view-' + view));
  $$('.nav-btn').forEach(b => b.classList.toggle('active', b.dataset.goto === view));
  $('.views').scrollTop = 0;
  if (view === 'favorites') renderFavorites();
  if (view === 'library') renderLibrary();
}
document.addEventListener('click', e => {
  const g = e.target.closest('[data-goto]');
  if (g) goto(g.dataset.goto);
});
$('#btnTopSearch').onclick = () => { goto('search'); setTimeout(() => $('#searchInput').focus(), 60); };
$('#btnMenu').onclick = () => goto('profile');
$('#menuAbout').onclick = () => toast('Sonora v1.0 — modern music player by Nawazish Ali');
$('#btnLogout').onclick = () => toast('Tum already guest mode mein ho — enjoy the music!');

/* ================= rendering ================= */
function trackRow(t){
  const isCur = current() && current().id === t.id;
  return `<div class="track ${isCur ? 'playing' : ''}" data-play="${t.id}">
    ${coverHTML(t, 'cover-sm')}
    <div class="track-meta"><strong>${t.title}</strong><span>${t.artist}${t.device ? ' • Phone' : (t.local ? ' • Device' : '')}</span></div>
    <span class="eq-mini"><i></i><i></i><i></i></span>
    <button class="add-btn" data-addto="${t.id}" title="Add to playlist">＋</button>
    <button class="heart-btn ${state.favorites.has(t.id) ? 'liked' : ''}" data-fav="${t.id}" title="Favorite">♥</button>
  </div>`;
}
function bindList(){ /* clicks are handled by one delegated listener below — survives re-renders */ }
document.addEventListener('click', e => {
  const acc = e.target.closest('[data-access]');
  if (acc){ if (window.SonoraNative) SonoraNative.requestAccess(); return; }
  const fav = e.target.closest('[data-fav]');
  if (fav){ toggleFav(fav.dataset.fav); return; }
  const add = e.target.closest('[data-addto]');
  if (add){ openAddTo(add.dataset.addto); return; }
  const trend = e.target.closest('[data-trend]');
  if (trend){ playQueue(TRACKS.map(t => t.id), trend.dataset.trend, 'Trending Now'); return; }
  const row = e.target.closest('[data-play]');
  if (row){
    const container = row.closest('[data-name]');
    const siblings = container ? [...container.querySelectorAll('[data-play]')].map(r => r.dataset.play) : [];
    playQueue(siblings.length ? siblings : allTracks().map(t => t.id), row.dataset.play, (container && container.dataset.name) || 'Sonora Mix');
  }
});
function renderHome(){
  $('#trendRow').innerHTML = TRACKS.slice(0, 7).map(t =>
    `<div class="trend-card" data-trend="${t.id}"><div class="trend-cover" style="${coverStyle(t)}"><div class="cover-art"></div></div><strong>${t.title}</strong><span>${t.artist}</span></div>`).join('');
  renderPlaylistCards();
  const list = $('#homeList'); list.dataset.name = 'Made For You';
  list.innerHTML = TRACKS.map(trackRow).join(''); bindList(list);
}
function renderPlaylistCards(){
  $('#playlistCards').innerHTML = `
    <button class="pl-card create" id="cardCreate"><span class="pl-plus">+</span><span><strong>Create Playlist</strong><span>Apni pasand ki list banao</span></span></button>
    <button class="pl-card" id="cardLiked"><span class="pl-heart">♥</span><strong>Liked Songs</strong><span>${state.favorites.size} Songs</span></button>`;
  $('#cardCreate').onclick = () => openPlaylistModal();
  $('#cardLiked').onclick = () => goto('favorites');
}
function renderSearch(){
  const q = $('#searchInput').value.trim().toLowerCase();
  const list = allTracks().filter(t =>
    (state.genre === 'All' || t.genre === state.genre || t.local) &&
    (!q || t.title.toLowerCase().includes(q) || t.artist.toLowerCase().includes(q)));
  const el = $('#searchList'); el.dataset.name = 'Search Results';
  el.innerHTML = list.map(trackRow).join(''); bindList(el);
  $('#searchEmpty').hidden = list.length > 0;
}
function renderGenreChips(){
  const genres = ['All','Pop','Chill','Electronic','Rock','Jazz'];
  $('#genreChips').innerHTML = genres.map(g => `<button class="chip ${state.genre === g ? 'active' : ''}" data-genre="${g}">${g}</button>`).join('');
  $$('#genreChips [data-genre]').forEach(c => c.onclick = () => { state.genre = c.dataset.genre; renderGenreChips(); renderSearch(); });
}
function renderFavorites(){
  const favs = allTracks().filter(t => state.favorites.has(t.id));
  const el = $('#favList'); el.dataset.name = 'Liked Songs';
  el.innerHTML = favs.map(trackRow).join(''); bindList(el);
  $('#favEmpty').hidden = favs.length > 0;
}
function renderLibrary(){
  const wrap = $('#playlistList');
  const counts = p => p.trackIds.filter(id => trackById(id)).length;
  wrap.innerHTML = state.playlists.map(p => `
    <button class="pl-row" data-pl="${p.id}">
      <span class="cover-sm" style="${coverStyle({hue:p.hue})}"><span class="cover-art"></span></span>
      <span><strong>${p.name}</strong><span>${counts(p)} Songs</span></span>
      <span class="pl-count">›</span>
    </button>`).join('') || '<p class="empty-note">Koi playlist nahi — + se pehli playlist banao.</p>';
  wrap.querySelectorAll('[data-pl]').forEach(r => r.onclick = () => {
    const p = state.playlists.find(x => x.id === r.dataset.pl);
    const ids = p.trackIds.filter(id => trackById(id));
    if (!ids.length) return toast('Is playlist mein abhi koi song nahi');
    playQueue(ids, ids[0], p.name);
  });
  const ll = $('#localList'); ll.dataset.name = 'Your Device Songs';
  ll.innerHTML = state.locals.map(trackRow).join('') ||
    '<p class="empty-note">Abhi device se koi song add nahi hua.</p>';
  bindList(ll);
}

/* ---------- favorites & playlists ---------- */
function toggleFav(id){
  state.favorites.has(id) ? state.favorites.delete(id) : state.favorites.add(id);
  store.set('favorites', [...state.favorites]);
  renderAll();
  syncPlayerUI();
}
function openPlaylistModal(){ $('#playlistModal').hidden = false; setTimeout(() => $('#playlistName').focus(), 50); }
function closePlaylistModal(){ $('#playlistModal').hidden = true; $('#playlistName').value = ''; }
function createPlaylist(name){
  const p = { id:'p' + Date.now(), name, hue: 250 + Math.floor(Math.random() * 80), trackIds: [] };
  state.playlists.push(p); store.set('playlists', state.playlists);
  renderAll(); toast(`Playlist "${name}" ban gayi`);
  return p;
}
$('#btnNewPlaylist2').onclick = openPlaylistModal;
$('#btnCancelPlaylist').onclick = closePlaylistModal;
$('#btnCreatePlaylist').onclick = () => {
  const name = $('#playlistName').value.trim();
  if (!name) return toast('Pehle playlist ka naam likho');
  createPlaylist(name); closePlaylistModal(); goto('library');
};
function openAddTo(id){
  state.addToTrackId = id;
  if (!Array.isArray(state.playlists) || !state.playlists.length){
    state.playlists = [{ id:'p1', name:'My Favorites', hue:330, trackIds:[] }];
  }
  let rows = '';
  try {
    rows = state.playlists.map(p =>
      `<button class="addto-row" data-addpl="${p.id}">♪ ${p.name} <span style="color:var(--muted);font-size:12px">(${(p.trackIds || []).length})</span></button>`).join('');
  } catch(e){ rows = ''; }
  $('#addToList').innerHTML = rows + `<button class="addto-row" data-addpl="__new">＋ New playlist…</button>`;
  $('#addToModal').hidden = false;
  $$('#addToList [data-addpl]').forEach(b => b.onclick = () => {
    if (b.dataset.addpl === '__new'){
      const name = prompt('Playlist ka naam?');
      if (!name) return;
      const p = createPlaylist(name.trim());
      p.trackIds.push(id); store.set('playlists', state.playlists);
    } else {
      const p = state.playlists.find(x => x.id === b.dataset.addpl);
      if (!p.trackIds.includes(id)) p.trackIds.push(id);
      store.set('playlists', state.playlists);
      toast(`"${trackById(id).title}" → ${p.name}`);
    }
    $('#addToModal').hidden = true; renderAll();
  });
}
$('#btnCancelAddTo').onclick = () => $('#addToModal').hidden = true;

/* ---------- device songs ---------- */
$('#fileInput').addEventListener('change', e => {
  const files = [...e.target.files];
  if (!files.length) return;
  files.forEach((f, i) => {
    state.locals.push({
      id:'loc' + Date.now() + '_' + i,
      title: f.name.replace(/\.[^.]+$/, '').slice(0, 60),
      artist:'Your Device', genre:'Local', hue: 255 + (i * 23) % 90,
      src: URL.createObjectURL(f), local:true,
      lyrics:[[0,'♪ ♪ ♪'],[8,'Your song, your lyrics'],[16,'Sing along with Sonora']],
    });
  });
  toast(files.length + ' song(s) add ho gaye');
  renderAll(); goto('library');
  e.target.value = '';
});

/* ================= player engine ================= */
function fmt(s){ if (!isFinite(s) || s < 0) s = 0; return Math.floor(s/60) + ':' + String(Math.floor(s%60)).padStart(2,'0'); }

function pickAudioFor(t){
  const target = t.local ? localAudio : streamAudio;
  if (target !== audio){ audio.pause(); audio = target; }
  if (t.local) ensureEQGraph();
}

function loadTrack(id, autoplay = true){
  const t = trackById(id); if (!t) return;
  state.index = state.queue.indexOf(id);
  pickAudioFor(t);
  audio.src = t.src;
  audio.playbackRate = 1;
  if (autoplay) audio.play().catch(() => toast('Play dabao — browser ne autoplay roka'));
  clearTimeout(window._loadTimer);
  window._loadTimer = setTimeout(() => {
    if (audio.readyState < 3) toast('Gana load ho raha hai… slow net par kuch seconds lagte hain');
  }, 1600);
  updateMediaSession(t);
  syncPlayerUI(); renderAllListsLight();
}

function playQueue(ids, startId, name){
  if (!ids.length) return;
  state.queue = ids.slice(); state.queueName = name || 'Sonora Mix';
  loadTrack(startId || ids[0], true);
  openPlayer();
}
function playPause(){
  if (!current()){ playQueue(TRACKS.map(t => t.id), TRACKS[0].id, 'Trending Now'); return; }
  if (audio.paused) audio.play().catch(()=>{}); else audio.pause();
}
function step(dir){
  if (!state.queue.length) return;
  if (state.shuffle && state.queue.length > 1){
    let n; do { n = Math.floor(Math.random() * state.queue.length); } while (n === state.index);
    loadTrack(state.queue[n]); return;
  }
  let n = state.index + dir;
  if (n >= state.queue.length){ if (state.repeat === 1) n = 0; else { audio.pause(); return; } }
  if (n < 0) n = state.queue.length - 1;
  loadTrack(state.queue[n]);
}
function next(auto = false){
  if (auto && state.repeat === 2){ audio.currentTime = 0; audio.play(); return; }
  step(1);
}
function prev(){
  if (audio.currentTime > 3){ audio.currentTime = 0; return; }
  step(-1);
}

[streamAudio, localAudio].forEach(el => {
  el.addEventListener('play',  () => { state.playing = true;  syncPlayerUI(); });
  el.addEventListener('pause', () => { state.playing = false; syncPlayerUI(); });
  el.addEventListener('ended', () => { if (el === audio) next(true); });
  el.addEventListener('loadedmetadata', () => { if (el === audio) syncPlayerUI(); });
  el.addEventListener('timeupdate', () => { if (el === audio) syncProgress(); });
  el.addEventListener('waiting', () => { if (el === audio) document.body.classList.add('audio-loading'); });
  el.addEventListener('playing', () => { if (el === audio) document.body.classList.remove('audio-loading'); });
  el.addEventListener('canplay', () => { if (el === audio) document.body.classList.remove('audio-loading'); });
  el.addEventListener('error', () => {
    if (el === audio && el.getAttribute('src')){
      document.body.classList.remove('audio-loading');
      toast('Gana load nahi ho saka — internet check karke dobara tap karo');
    }
  });
});

function syncProgress(){
  const t = current(); if (!t) return;
  const d = audio.duration || 0, c = audio.currentTime || 0;
  $('#seekBar').value = d ? Math.round(c / d * 1000) : 0;
  $('#timeCur').textContent = fmt(c); $('#timeDur').textContent = fmt(d);
  $('#miniProgressFill').style.width = (d ? (c / d * 100) : 0) + '%';
  syncLyrics(c);
}
function syncLyrics(c){
  const t = current(); if (!t || !t.lyrics || $('#lyricsSheet').hidden) return;
  let now = 0;
  t.lyrics.forEach((l, i) => { if (c >= l[0]) now = i; });
  $$('#lyricsSheet p').forEach((p, i) => p.classList.toggle('now', i === now));
}
function syncPlayerUI(){
  const t = current();
  const playPath = 'M7 4.8v14.4c0 .9 1 1.5 1.8 1L20 13a1.15 1.15 0 0 0 0-2L8.8 3.9C8 3.4 7 4 7 4.8z';
  const pausePath = 'M7 5h3.4v14H7zM13.6 5H17v14h-3.4z';
  const icon = state.playing ? pausePath : playPath;
  $('#playIcon').innerHTML = `<path d="${icon}"/>`;
  $('#miniPlayIcon').innerHTML = `<path d="${icon}"/>`;
  $('#nowPlaying').classList.toggle('is-playing', state.playing);
  $('#btnShuffle').classList.toggle('on', state.shuffle);
  $('#btnRepeat').classList.toggle('on', state.repeat > 0);
  $('#repeatOne').hidden = state.repeat !== 2;
  if (!t){ $('#miniPlayer').hidden = true; return; }
  $('#miniPlayer').hidden = false;
  $('#miniTitle').textContent = t.title; $('#miniArtist').textContent = t.artist;
  $('#miniCover').style.cssText = coverStyle(t); $('#miniCover').innerHTML = '<div class="cover-art"></div>';
  $('#npTitle').textContent = t.title; $('#npArtist').textContent = t.artist;
  $('#npTitleTop').textContent = t.title; $('#npArtistTop').textContent = t.artist;
  const nc = $('#npCover'); nc.style.cssText = coverStyle(t);
  if (!nc.querySelector('.cover-art')) nc.insertAdjacentHTML('afterbegin', '<div class="cover-art"></div>');
  $('#btnNpFav').classList.toggle('liked', state.favorites.has(t.id));
  if (!$('#lyricsSheet').hidden) renderLyrics();
  if (!$('#queueSheet').hidden) renderQueue();
  syncProgress();
}
function renderLyrics(){
  const t = current(); if (!t) return;
  $('#lyricsSheet').innerHTML = (t.lyrics || [[0,'♪ ♪ ♪']]).map(l => `<p>${l[1]}</p>`).join('');
  syncLyrics(audio.currentTime || 0);
}
function renderQueue(){
  const ids = state.queue.slice(state.index + 1).concat(state.queue.slice(0, state.index + 1));
  $('#queueList').innerHTML = ids.map((id, i) => {
    const t = trackById(id); if (!t) return '';
    return `<div class="track ${i === ids.length - 1 && false ? '' : ''}" data-q="${id}">${coverHTML(t,'cover-sm')}<div class="track-meta"><strong>${t.title}</strong><span>${t.artist}</span></div>${id === state.queue[state.index] ? '<span style="color:var(--magenta);font-size:12px">Playing</span>' : ''}</div>`;
  }).join('');
  $$('#queueList [data-q]').forEach(r => r.onclick = () => loadTrack(r.dataset.q));
}
function renderAll(){ renderHome(); renderSearch(); renderFavorites(); renderLibrary(); }
function renderAllListsLight(){ renderHome(); renderSearch(); renderFavorites(); }

/* ---------- controls wiring ---------- */
$('#btnPlay').onclick = playPause;
$('#btnMiniPlay').onclick = e => { e.stopPropagation(); playPause(); };
$('#btnNext').onclick = () => next();
$('#btnPrev').onclick = prev;
$('#btnMiniNext').onclick = e => { e.stopPropagation(); next(); };
$('#btnMiniPrev').onclick = e => { e.stopPropagation(); prev(); };
$('#btnHeroPlay').onclick = () => playQueue(TRACKS.map(t => t.id), TRACKS[0].id, 'Trending Now');
$('#btnShuffle').onclick = () => { state.shuffle = !state.shuffle; store.set('shuffle', state.shuffle); syncPlayerUI(); toast(state.shuffle ? 'Shuffle ON' : 'Shuffle OFF'); };
$('#btnRepeat').onclick = () => { state.repeat = (state.repeat + 1) % 3; store.set('repeat', state.repeat); syncPlayerUI(); toast(['Repeat OFF','Repeat ALL','Repeat ONE'][state.repeat]); };
$('#btnNpFav').onclick = () => { const t = current(); if (t) toggleFav(t.id); };
$('#seekBar').addEventListener('input', e => { if (audio.duration) audio.currentTime = audio.duration * (e.target.value / 1000); });
$('#volBar').addEventListener('input', e => {
  const v = e.target.value / 100;
  streamAudio.volume = v; localAudio.volume = v; store.set('volume', v);
});
$('#miniPlayer').onclick = () => openPlayer();
$('#miniMeta').onclick = () => openPlayer();
$('#btnCloseNp').onclick = () => { $('#nowPlaying').hidden = true; };
function openPlayer(){ $('#nowPlaying').hidden = false; syncPlayerUI(); }
$('#btnLyrics').onclick = () => {
  const s = $('#lyricsSheet'); s.hidden = !s.hidden;
  if (!s.hidden){ $('#queueSheet').hidden = true; renderLyrics(); }
};
$('#btnQueue').onclick = () => {
  const s = $('#queueSheet'); s.hidden = !s.hidden;
  if (!s.hidden){ $('#lyricsSheet').hidden = true; renderQueue(); }
};
$('#searchInput').addEventListener('input', renderSearch);
document.addEventListener('keydown', e => {
  if (e.code === 'Space' && !/INPUT|TEXTAREA/.test(document.activeElement.tagName)){ e.preventDefault(); playPause(); }
});

/* ---------- Media Session: lock-screen / background controls ---------- */
function updateMediaSession(t){
  if (!('mediaSession' in navigator)) return;
  const art = 'data:image/svg+xml,' + encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="hsl(${t.hue},90%,62%)"/><stop offset="1" stop-color="hsl(${t.hue+50},85%,38%)"/></linearGradient></defs><rect width="512" height="512" fill="url(#g)"/><text x="256" y="330" font-size="240" text-anchor="middle" fill="white">♪</text></svg>`);
  navigator.mediaSession.metadata = new MediaMetadata({ title: t.title, artist: t.artist, album: 'Sonora — ' + state.queueName, artwork: [{ src: art, sizes: '512x512', type: 'image/svg+xml' }] });
  navigator.mediaSession.setActionHandler('play', playPause);
  navigator.mediaSession.setActionHandler('pause', playPause);
  navigator.mediaSession.setActionHandler('previoustrack', prev);
  navigator.mediaSession.setActionHandler('nexttrack', () => next());
  navigator.mediaSession.setActionHandler('seekto', d => { if (d.seekTime != null) audio.currentTime = d.seekTime; });
}

/* ================= equalizer (Web Audio — device songs) ================= */
const EQ_FREQS = [60, 230, 910, 3600, 14000];
const EQ_LABELS = ['60Hz','230Hz','910Hz','3.6kHz','14kHz'];
const PRESETS = {
  normal: [0,0,0,0,0], rock: [4,2,-1,3,5], pop: [-1,2,4,2,1], jazz: [3,2,1,2,4], bass: [7,4,1,0,1],
};
let eq = store.get('eq', { on:true, gains:[0,0,0,0,0], bass:false, virt:false });
let actx = null, eqBands = [], bassFilter = null, virtPan = null, virtLFO = null;

function buildEqUI(){
  $('#eqSliders').innerHTML = EQ_FREQS.map((f, i) => `
    <div class="eq-band">
      <output id="eqOut${i}">${eq.gains[i] > 0 ? '+' : ''}${eq.gains[i]}dB</output>
      <input type="range" min="-12" max="12" step="1" value="${eq.gains[i]}" data-band="${i}" aria-label="${EQ_LABELS[i]}">
      <label>${EQ_LABELS[i]}</label>
    </div>`).join('');
  $$('#eqSliders [data-band]').forEach(inp => inp.addEventListener('input', () => {
    const i = +inp.dataset.band; eq.gains[i] = +inp.value;
    $('#eqOut' + i).textContent = (eq.gains[i] > 0 ? '+' : '') + eq.gains[i] + 'dB';
    store.set('eq', eq); applyEQ();
    $$('#presetChips .chip').forEach(c => c.classList.remove('active'));
  }));
  $('#eqMaster').checked = eq.on; $('#bassToggle').checked = eq.bass; $('#virtToggle').checked = eq.virt;
}
function ensureEQGraph(){
  if (actx) { if (actx.state === 'suspended') actx.resume(); return; }
  try {
    actx = new (window.AudioContext || window.webkitAudioContext)();
    const src = actx.createMediaElementSource(localAudio);
    let node = src;
    eqBands = EQ_FREQS.map(f => {
      const b = actx.createBiquadFilter(); b.type = 'peaking'; b.frequency.value = f; b.Q.value = 0.9;
      node.connect(b); node = b; return b;
    });
    bassFilter = actx.createBiquadFilter(); bassFilter.type = 'lowshelf'; bassFilter.frequency.value = 200;
    node.connect(bassFilter); node = bassFilter;
    virtPan = actx.createStereoPanner ? actx.createStereoPanner() : null;
    if (virtPan){ node.connect(virtPan); node = virtPan; }
    node.connect(actx.destination);
    applyEQ();
  } catch(e){ /* EQ unavailable in this browser — playback continues direct */ }
}
function applyEQ(){
  if (!actx) return;
  eqBands.forEach((b, i) => b.gain.value = eq.on ? eq.gains[i] : 0);
  if (bassFilter) bassFilter.gain.value = (eq.on && eq.bass) ? 8 : 0;
  if (virtPan){
    if (eq.on && eq.virt){
      if (!virtLFO){
        virtLFO = actx.createOscillator(); const g = actx.createGain();
        virtLFO.frequency.value = 0.15; g.gain.value = 0.35;
        virtLFO.connect(g); g.connect(virtPan.pan); virtLFO.start();
      }
    } else if (virtLFO){ try { virtLFO.stop(); } catch(e){} virtLFO = null; virtPan.pan.value = 0; }
  }
}
$('#eqMaster').addEventListener('change', e => { eq.on = e.target.checked; store.set('eq', eq); applyEQ(); toast(eq.on ? 'Equalizer ON' : 'Equalizer OFF'); });
$('#bassToggle').addEventListener('change', e => { eq.bass = e.target.checked; store.set('eq', eq); applyEQ(); });
$('#virtToggle').addEventListener('change', e => { eq.virt = e.target.checked; store.set('eq', eq); applyEQ(); });
$$('#presetChips .chip').forEach(c => c.onclick = () => {
  const p = PRESETS[c.dataset.preset]; if (!p) return;
  eq.gains = p.slice(); store.set('eq', eq);
  $$('#presetChips .chip').forEach(x => x.classList.toggle('active', x === c));
  buildEqUI(); applyEQ(); toast('Preset: ' + c.textContent);
});

/* ---- Android app mode: sirf phone ke apne ganay ---- */
let APP_MODE = false;
if (window.SonoraNative) {
  APP_MODE = true;
  document.body.classList.add('app-mode');
  window.onSonoraAccessChanged = () => location.reload();
  const hint = document.querySelector('#view-library .hello-sub');
  if (hint) hint.style.display = 'none';
  try {
    const dev = JSON.parse(SonoraNative.getSongs() || '[]');
    const devTracks = dev.map((s, i) => ({
      id: s.id, title: s.title, artist: s.artist, genre: 'Phone',
      hue: 255 + (i * 37) % 80, src: s.src, device: true, duration: s.duration || 0,
      lyrics: [[0,'♪ ♪ ♪'],[8,'Your song, from your phone'],[16,'Sing along with Sonora']],
    }));
    TRACKS.length = 0; TRACKS.push(...devTracks);
    state.queue = TRACKS.map(t => t.id); state.index = -1;
    state.favorites = new Set([...state.favorites].filter(id => String(id).startsWith('dev')));
    state.playlists.forEach(p => { p.trackIds = p.trackIds.filter(id => String(id).startsWith('dev')); });
  } catch(e) {}
}

/* ================= init ================= */
buildEqUI();
renderGenreChips();
renderAll();
syncPlayerUI();
if ('serviceWorker' in navigator && location.protocol.startsWith('http')){
  navigator.serviceWorker.register('sw.js').catch(() => {});
}

/* =========================================================================
   R2 存储管理器 · Android 原型 — 图标库 / 组件工厂 / 原型引擎
   screens.js 依赖本文件暴露的全局函数，故本文件必须先加载。
   ========================================================================= */

/* ---------------- 图标：统一 24 网格，描边 1.7 ---------------- */
const ICONS = {
  search:'<circle cx="11" cy="11" r="7.2"/><path d="M16.5 16.5 21 21"/>',
  more:'<circle cx="12" cy="5.8" r="1.6" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="12" cy="18.2" r="1.6" fill="currentColor" stroke="none"/>',
  back:'<path d="M14.8 4.6 7.2 12l7.6 7.4"/>',
  chev:'<path d="M9.4 5.6 15.8 12l-6.4 6.4"/>',
  close:'<path d="M6.6 6.6 17.4 17.4M17.4 6.6 6.6 17.4"/>',
  plus:'<path d="M12 5.4v13.2M5.4 12h13.2"/>',
  folder:'<path d="M3.4 8.4A2.6 2.6 0 0 1 6 5.8h2.9l1.9 2.2h7.2a2.6 2.6 0 0 1 2.6 2.6v6.1a2.6 2.6 0 0 1-2.6 2.6H6a2.6 2.6 0 0 1-2.6-2.6z"/>',
  folderFill:'<path d="M3.4 8.4A2.6 2.6 0 0 1 6 5.8h2.9l1.9 2.2h7.2a2.6 2.6 0 0 1 2.6 2.6v6.1a2.6 2.6 0 0 1-2.6 2.6H6a2.6 2.6 0 0 1-2.6-2.6z" fill="currentColor" stroke="none"/>',
  swap:'<path d="M6.8 8.2h10.6l-2.4-2.4M17.2 15.8H6.6l2.4 2.4"/>',
  sliders:'<path d="M4.6 8.6h8.2M17.8 8.6h1.6M4.6 15.4h2.4M11.6 15.4h7.8"/><circle cx="15.2" cy="8.6" r="2.1"/><circle cx="9.4" cy="15.4" r="2.1"/>',
  down:'<path d="M12 3.6v10.8m0 0-4.3-4.3M12 14.4l4.3-4.3M4.8 19.6h14.4"/>',
  up:'<path d="M12 20.4V9.6m0 0-4.3 4.3M12 9.6l4.3 4.3M4.8 4.4h14.4"/>',
  trash:'<path d="M4.6 6.8h14.8M9.6 6.8V4.4h4.8v2.4M6.4 6.8l1 12.8h9.2l1-12.8M10.2 10.4v5.8M13.8 10.4v5.8"/>',
  share:'<path d="M12 15.4V3.4m0 0L8.4 7M12 3.4 15.6 7M4.6 13.4v6.4a.9.9 0 0 0 .9.9h13a.9.9 0 0 0 .9-.9v-6.4"/>',
  link:'<path d="M9.4 14.6a4.3 4.3 0 0 1 0-6.1l2.1-2.1a4.3 4.3 0 0 1 6.1 6.1l-1 1M14.6 9.4a4.3 4.3 0 0 1 0 6.1l-2.1 2.1a4.3 4.3 0 0 1-6.1-6.1l1-1"/>',
  copy:'<rect x="9" y="9" width="11.2" height="11.2" rx="2.4"/><path d="M15 6.4V4.8a1.2 1.2 0 0 0-1.2-1.2H4.8A1.2 1.2 0 0 0 3.6 4.8V12a1.2 1.2 0 0 0 1.2 1.2h1.7"/>',
  check:'<path d="M5 12.6 9.4 17 19 7.4"/>',
  refresh:'<path d="M19.8 12a7.8 7.8 0 1 1-2.3-5.5M19.8 4v4.4h-4.4"/>',
  info:'<circle cx="12" cy="12" r="8.6"/><path d="M12 8h.02M12 11.4v4.6"/>',
  lock:'<rect x="4.6" y="10.2" width="14.8" height="10" rx="2.8"/><path d="M8.4 10.2V7.6a3.6 3.6 0 0 1 7.2 0v2.6"/><circle cx="12" cy="15.2" r="1.3" fill="currentColor" stroke="none"/>',
  eye:'<path d="M2.6 12S6.4 6.4 12 6.4 21.4 12 21.4 12 17.6 17.6 12 17.6 2.6 12 2.6 12z"/><circle cx="12" cy="12" r="2.7"/>',
  file:'<path d="M6.4 3.6h6.4l4.8 4.8v12H6.4z"/><path d="M12.8 3.6v4.8h4.8"/>',
  cloud:'<path d="M7.4 18h9.4a3.4 3.4 0 0 0 .3-6.8 5.1 5.1 0 0 0-9.6 1A3 3 0 0 0 7.4 18z"/>',
  play:'<path d="M9.6 7.4 16.8 12l-7.2 4.6z" fill="currentColor"/>',
  grid:'<rect x="4" y="4" width="6.9" height="6.9" rx="2"/><rect x="13.1" y="4" width="6.9" height="6.9" rx="2"/><rect x="4" y="13.1" width="6.9" height="6.9" rx="2"/><rect x="13.1" y="13.1" width="6.9" height="6.9" rx="2"/>',
  list:'<path d="M4 7h16M4 12h16M4 17h16"/>',
  wifi:'<path d="M3 3l18 18M8.8 15.6a4.8 4.8 0 0 1 6.4 0M5.6 12.4a10 10 0 0 1 2.8-1.8M18.4 12.4a10 10 0 0 0-4.2-2.4M12 19h.02"/>',
  shield:'<path d="M12 3.4 5.4 6v5.3c0 4 2.8 7.5 6.6 9.1 3.8-1.6 6.6-5.1 6.6-9.1V6z"/>',
  globe:'<circle cx="12" cy="12" r="8.6"/><path d="M3.4 12h17.2M12 3.4c2.5 2.7 2.5 14.5 0 17.2M12 3.4c-2.5 2.7-2.5 14.5 0 17.2"/>',
  db:'<ellipse cx="12" cy="6.4" rx="7.2" ry="2.9"/><path d="M4.8 6.4v11.2c0 1.6 3.2 2.9 7.2 2.9s7.2-1.3 7.2-2.9V6.4M4.8 12c0 1.6 3.2 2.9 7.2 2.9s7.2-1.3 7.2-2.9"/>',
  key:'<circle cx="7.8" cy="14.4" r="3.6"/><path d="M10.5 12.2 19.4 3.3M16 6.7l2.5 2.5"/>',
  camera:'<path d="M4.4 8.6h3l1.4-2h6.4l1.4 2h3a1.4 1.4 0 0 1 1.4 1.4v7.2a1.4 1.4 0 0 1-1.4 1.4H4.4A1.4 1.4 0 0 1 3 17.2V10a1.4 1.4 0 0 1 1.4-1.4z"/><circle cx="12" cy="13.4" r="3.2"/>',
  doc:'<path d="M6.4 3.6h6.4l4.8 4.8v12H6.4z"/><path d="M12.8 3.6v4.8h4.8M9 13h6M9 16.4h4"/>',
  clock:'<circle cx="12" cy="12" r="8.6"/><path d="M12 7.4V12l3.2 2"/>',
  sort:'<path d="M7 5.4v13m0 0-3-3m3 3 3-3M17 18.6v-13m0 0-3 3m3-3 3 3"/>',
  filter:'<path d="M4 6.4h16M7 12h10M10 17.6h4"/>',
  image:'<rect x="3.6" y="5" width="16.8" height="14" rx="2.4"/><circle cx="8.8" cy="10.2" r="1.7"/><path d="M4.4 17.4 9.8 12l2.8 2.8 2.6-2.4 4.4 4.2"/>',
  logout:'<path d="M15 8.4V5.6a1.6 1.6 0 0 0-1.6-1.6H5.6A1.6 1.6 0 0 0 4 5.6v12.8A1.6 1.6 0 0 0 5.6 20h7.8a1.6 1.6 0 0 0 1.6-1.6v-2.8M9.6 12h10.8m0 0-3.2-3.2M20.4 12l-3.2 3.2"/>'
};
const ic = (n, s) => '<svg class="ic" width="' + (s || 22) + '" height="' + (s || 22) + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">' + (ICONS[n] || '') + '</svg>';

const STATUS = '<svg width="54" height="12" viewBox="0 0 56 12" fill="none"><g fill="currentColor">'
  + '<rect x="0" y="7.6" width="2.6" height="4.4" rx="1.1"/><rect x="4.2" y="5.4" width="2.6" height="6.6" rx="1.1"/>'
  + '<rect x="8.4" y="3" width="2.6" height="9" rx="1.1"/><rect x="12.6" y="0.8" width="2.6" height="11.2" rx="1.1"/>'
  + '<path d="M21 4.2a6.9 6.9 0 0 1 9.4 0l-1.4 1.4a5 5 0 0 0-6.6 0z"/><path d="M23.2 6.8a3.7 3.7 0 0 1 5 0l-1.4 1.4a1.9 1.9 0 0 0-2.2 0z"/>'
  + '<circle cx="25.7" cy="10.2" r="1.25"/><rect x="38" y="1.4" width="15.6" height="9" rx="2.7" stroke="currentColor" stroke-opacity=".45" fill="none"/>'
  + '<rect x="39.4" y="2.8" width="11.6" height="6.2" rx="1.5"/><rect x="54.2" y="4.4" width="1.6" height="3.2" rx=".8"/></g></svg>';

/* ---------------- 缩略图工厂 ---------------- */
const GRAD = {
  sunrise:'linear-gradient(142deg,#FFC98B,#F6821F 46%,#D2468C)',
  ocean:'linear-gradient(142deg,#A9E0FF,#2F6FED 52%,#12306E)',
  forest:'linear-gradient(142deg,#CFF3D8,#12905F 55%,#0A4A33)',
  dusk:'linear-gradient(142deg,#FFB6C1,#8D3AC4 50%,#2A1560)',
  amber:'linear-gradient(142deg,#FFE6A8,#F6821F 58%,#8A4400)',
  steel:'linear-gradient(142deg,#DEE6F2,#5A6472 55%,#20242C)',
  mint:'linear-gradient(142deg,#B8F0E6,#12908F 55%,#0B4A4A)',
  rose:'linear-gradient(142deg,#FFD1DC,#E5484D 55%,#7A1220)'
};
const thPhoto = (g) => '<span class="thumb th-photo" style="--g:' + GRAD[g] + '"></span>';
const thVideo = (g, d) => '<span class="thumb th-photo" style="--g:' + GRAD[g] + '"><i class="pl"></i><b class="dur">' + d + '</b></span>';
const thDoc = (ext, tone) => '<span class="thumb th-doc" style="--tone:' + tone + '"><span>' + ext + '</span></span>';
const thMono = (ext, tone) => '<span class="thumb th-mono" style="--tone:' + tone + '"><span>' + ext + '</span></span>';
const thFolder = () => '<span class="thumb th-folder">' + ic('folderFill', 24) + '</span>';
const thIcon = (name, bg, fg) => '<span class="thumb" style="background:' + bg + ';color:' + fg + '">' + ic(name, 22) + '</span>';

/* ---------------- 组件工厂 ---------------- */
const sb = (dark) => '<div class="sb' + (dark ? ' dark' : '') + '"><span>14:20</span>' + STATUS + '</div>';

const appbar = (o) => {
  o = o || {};
  return '<div class="appbar' + (o.dark ? ' dark' : '') + '">'
    + (o.back ? '<button class="iconbtn" data-goto="' + o.back + '">' + ic('back') + '</button>' : '')
    + (o.bucket ? '<button class="bucketchip" data-goto="bucket-switch"><span class="sw"></span>picture' + ic('chev', 15) + '</button>' : '')
    + (o.glyph ? '<span class="baricon">' + ic(o.glyph, 20) + '</span>' : '')
    + '<div class="ttl">' + (o.title || '') + (o.sub ? '<div class="sub">' + o.sub + '</div>' : '') + '</div>'
    + (o.actions || '')
    + '</div>';
};

const navbar = (active) => '<div class="navbar">'
  + '<button class="' + (active === 'files' ? 'active' : '') + '" data-goto="home-list">' + ic('folder', 22) + '<span>文件</span></button>'
  + '<button class="' + (active === 'transfer' ? 'active' : '') + '" data-goto="transfers">' + ic('swap', 22) + '<span>传输</span></button>'
  + '<button class="' + (active === 'settings' ? 'active' : '') + '" data-goto="set-home">' + ic('sliders', 22) + '<span>设置</span></button>'
  + '</div>';

const ROW = (o) => '<button class="row' + (o.sel ? ' sel' : '') + '" data-goto="' + (o.goto || '') + '">'
  + o.thumb
  + '<span class="main"><span class="rt">' + o.name + '</span><span class="rs">' + (o.meta || '') + '</span></span>'
  + '<span class="trail">' + (o.trail !== undefined ? o.trail : ic('more', 19)) + '</span></button>';

/* 首页/各处的样例数据：桶 picture 根目录 */
const FILE_ROWS =
  ROW({thumb:thFolder(),name:'camera',meta:'<span>文件夹</span><i></i><span>128 项</span>',goto:'folder-nav',trail:ic('chev',17)})
+ ROW({thumb:thPhoto('sunrise'),name:'image.png',meta:'<span>1.2 MB</span><i></i><span>2024-01-15 14:30</span>',goto:'preview-image'})
+ ROW({thumb:thVideo('ocean','1:24'),name:'video.mp4',meta:'<span>24.6 MB</span><i></i><span>2024-01-15 09:12</span>',goto:'preview-video'})
+ ROW({thumb:thDoc('PDF','#E5484D'),name:'report.pdf',meta:'<span>3.4 MB</span><i></i><span>2024-01-14 20:15</span>',goto:'preview-pdf'})
+ ROW({thumb:thDoc('TXT','#5A6472'),name:'log-20240115.txt',meta:'<span>86 KB</span><i></i><span>2024-01-15 08:02</span>',goto:'preview-text'})
+ ROW({thumb:thMono('EXE','#38414F'),name:'setup-1.0.0.exe',meta:'<span>78.4 MB</span><i></i><span>2024-01-12 11:40</span>',goto:'preview-other'});

/* =========================================================================
   原型引擎
   ========================================================================= */
document.addEventListener('DOMContentLoaded', function () {
  const SCREENS = window.SCREENS || [];
  const pool = document.getElementById('pool');
  const viewport = document.getElementById('viewport');
  const overview = document.getElementById('overview');
  const stageEl = document.getElementById('stage');
  const jump = document.getElementById('jump');
  const picker = document.getElementById('picker');
  const sideTitle = document.getElementById('sideTitle');
  const sideNote = document.getElementById('sideNote');
  const map = {};
  let mode = 'interactive';
  let cur = 'home-list';

  SCREENS.forEach(s => {
    const el = document.createElement('div');
    el.className = 'screen';
    el.dataset.screen = s.id;
    el.innerHTML = s.html;
    pool.appendChild(el);
    map[s.id] = el;
  });

  let lastGroup = '';
  SCREENS.forEach((s, i) => {
    const o = document.createElement('option');
    o.value = s.id;
    o.textContent = s.group + ' · ' + s.title;
    jump.appendChild(o);

    if (s.group !== lastGroup) {
      lastGroup = s.group;
      const g = document.createElement('div');
      g.className = 'grp';
      g.textContent = s.group;
      picker.appendChild(g);
    }
    const b = document.createElement('button');
    b.dataset.id = s.id;
    b.textContent = (i + 1) + '. ' + s.title;
    b.onclick = () => { setMode('interactive'); go(s.id); };
    picker.appendChild(b);
  });

  function go(id) {
    if (!map[id]) return;
    cur = id;
    jump.value = id;
    const meta = SCREENS.find(x => x.id === id);
    sideTitle.textContent = meta.title;
    sideNote.textContent = meta.note;
    picker.querySelectorAll('button').forEach(b => b.classList.toggle('active', b.dataset.id === id));
    if (mode === 'interactive') {
      viewport.innerHTML = '';
      viewport.appendChild(map[id]);
    } else {
      const box = overview.querySelector('[data-focus="' + id + '"]');
      if (box) box.scrollIntoView({ behavior:'smooth', block:'center' });
    }
  }

  function setMode(m) {
    mode = m;
    document.querySelectorAll('#modeseg button').forEach(b => b.classList.toggle('active', b.dataset.mode === m));
    if (m === 'interactive') {
      overview.classList.remove('on');
      stageEl.style.display = 'flex';
      viewport.innerHTML = '';
      viewport.appendChild(map[cur]);
    } else {
      stageEl.style.display = 'none';
      overview.classList.add('on');
      buildOverview();
    }
  }

  function buildOverview() {
    overview.innerHTML = '<div class="hd"><h2>全部页面总览</h2><span class="cnt">' + SCREENS.length + ' 屏</span></div>';
    let g = '';
    let wrap = null;
    SCREENS.forEach(s => {
      if (s.group !== g) {
        g = s.group;
        const lab = document.createElement('div');
        lab.className = 'grouplabel';
        lab.textContent = g;
        overview.appendChild(lab);
        wrap = document.createElement('div');
        wrap.className = 'cards';
        overview.appendChild(wrap);
      }
      const box = document.createElement('div');
      box.className = 'frame-box';
      box.dataset.focus = s.id;
      box.onclick = () => { setMode('interactive'); go(s.id); };

      const miniWrap = document.createElement('div');
      miniWrap.className = 'mini-wrap';
      const dev = document.createElement('div');
      dev.className = 'device';
      const vp = document.createElement('div');
      vp.className = 'viewport';
      const clone = map[s.id].cloneNode(true);
      clone.querySelectorAll('[data-goto]').forEach(n => n.removeAttribute('data-goto'));
      vp.appendChild(clone);
      dev.appendChild(vp);
      miniWrap.appendChild(dev);

      const cap = document.createElement('div');
      cap.innerHTML = '<div class="cap">' + s.title + '</div><div class="capid">' + s.id + '</div>';
      box.appendChild(miniWrap);
      box.appendChild(cap);
      wrap.appendChild(box);
    });
  }

  viewport.addEventListener('click', e => {
    const t = e.target.closest('[data-goto]');
    if (t && t.dataset.goto && map[t.dataset.goto]) go(t.dataset.goto);
  });

  document.querySelectorAll('#modeseg button').forEach(b => { b.onclick = () => setMode(b.dataset.mode); });
  jump.onchange = () => { setMode('interactive'); go(jump.value); };
  document.getElementById('prev').onclick = () => {
    const i = SCREENS.findIndex(s => s.id === cur);
    go(SCREENS[(i - 1 + SCREENS.length) % SCREENS.length].id);
  };
  document.getElementById('next').onclick = () => {
    const i = SCREENS.findIndex(s => s.id === cur);
    go(SCREENS[(i + 1) % SCREENS.length].id);
  };

  /* #overview 或 #<screen-id> 可直接定位，方便把某一屏链接发给别人 */
  const h = (location.hash || '').replace('#', '');
  if (h === 'overview') setMode('overview');
  else if (map[h]) { setMode('interactive'); go(h); }
  else go(cur);
});

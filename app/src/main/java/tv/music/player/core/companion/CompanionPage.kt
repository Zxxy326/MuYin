package tv.music.player.core.companion

internal const val COMPANION_PAGE: String = """
<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<meta name="theme-color" content="#0b0d12">
<title>幕音遥控器</title>
<style>
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
body{margin:0;background:#0b0d12;color:#eef1f7;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif}
main{max-width:620px;margin:0 auto;padding:18px 16px 30px}
header{display:flex;align-items:center;justify-content:space-between;margin:6px 2px 18px}
.brand{display:flex;align-items:center;font-weight:700;letter-spacing:.12em;font-size:15px}
.brand .dot{width:9px;height:9px;border-radius:50%;background:#8f7bff;margin-right:9px;box-shadow:0 0 12px #8f7bff}
.brand small{font-weight:400;letter-spacing:0;color:#7d8495;margin-left:10px;font-size:11px}
.pill{font-size:11px;color:#9aa2b5;border:1px solid #293040;background:#151924;border-radius:30px;padding:6px 10px}
.pill.ok{color:#63e2c0;border-color:#245a4f}
.card{background:#131722;border:1px solid #222938;border-radius:20px;padding:16px;margin-bottom:14px}
.label{font-size:11px;letter-spacing:.12em;color:#818a9f;text-transform:uppercase}
.hint{font-size:11px;color:#707a90;line-height:1.6}
.search form{display:flex;gap:8px;margin-top:10px}
.search input{flex:1;min-width:0;height:46px;border-radius:14px;border:1px solid #2b3345;background:#0d1119;color:#eef1f7;padding:0 14px;font-size:16px;outline:none}
.search input:focus{border-color:#8f7bff}
.search button{height:46px;padding:0 18px;border-radius:14px;border:0;background:#8f7bff;color:#0c0a18;font-weight:700;font-size:14px}
.chips{display:flex;gap:8px;flex-wrap:wrap;margin-top:10px}
.chip{border:1px solid #2b3345;background:#161b27;color:#c4cad8;border-radius:30px;padding:8px 12px;font-size:13px}
#searchMsg{margin-top:9px;min-height:17px}
.now{display:flex;gap:14px}
.cover{width:92px;height:92px;border-radius:14px;object-fit:cover;background:#202637;flex:none}
.nowBody{flex:1;min-width:0}
.now h1{font-size:23px;line-height:1.25;margin:8px 0 4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.sub{font-size:13px;color:#8f98ad;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.progress{position:relative;height:5px;border-radius:5px;background:#252c3b;margin-top:16px;overflow:hidden}
#bar{position:absolute;left:0;top:0;bottom:0;width:0;background:#8f7bff;border-radius:5px}
#seek{position:absolute;left:0;right:0;top:-8px;width:100%;height:21px;margin:0;opacity:.001}
.times{display:flex;justify-content:space-between;font-size:11px;color:#7d869a;margin-top:7px}
.controls{display:flex;align-items:center;justify-content:space-between;gap:7px;margin-top:10px}
.controls button{height:42px;min-width:42px;border-radius:50%;border:1px solid #2b3345;background:#171c28;color:#e9ecf4;font-size:17px}
.controls button.primary{background:#8f7bff;border-color:#8f7bff;color:#0c0a18;font-size:21px}
.controls button:disabled{opacity:.4}
.queueHead{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px}
.queueList{display:flex;flex-direction:column;gap:2px;max-height:42vh;overflow:auto}
.qrow{display:flex;align-items:center;gap:10px;padding:9px 4px;border-radius:10px;border:0;background:transparent;color:#eef1f7;text-align:left;width:100%}
.qrow:active{background:#1c2230}
.qrow.current{background:#1a2130}
.qidx{width:20px;flex:none;color:#6f788c;font-size:12px;text-align:right}
.qmeta{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}
.qtitle{font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.qartist{font-size:11px;color:#7d869a;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.eq{width:5px;height:16px;border-radius:3px;background:#8f7bff;flex:none;display:none}
.qrow.current .eq{display:block}
footer{text-align:center;color:#586074;font-size:11px;line-height:1.7;margin-top:10px}
.gate{position:fixed;inset:0;background:#0b0d12;z-index:10;display:flex;align-items:center;justify-content:center;padding:24px}
.gate.hidden{display:none}
.gateBox{width:100%;max-width:360px;background:#131722;border:1px solid #222938;border-radius:22px;padding:24px}
.gateBox h2{font-size:20px;margin:0 0 8px}
.gateBox p{font-size:13px;color:#8f98ad;line-height:1.7;margin:0 0 18px}
.gateBox input{width:100%;height:52px;border-radius:14px;border:1px solid #2b3345;background:#0d1119;color:#eef1f7;font-size:22px;letter-spacing:.3em;text-align:center;outline:none}
.gateBox button{width:100%;height:48px;border-radius:14px;border:0;background:#8f7bff;color:#0c0a18;font-weight:700;font-size:15px;margin-top:12px}
</style>
</head>
<body>
<div id="gate" class="gate hidden">
  <div class="gateBox">
    <h2>连接电视</h2>
    <p>输入电视「手机伴侣」页面显示的 4 位配对码。手机与电视需要连接同一个 Wi-Fi / 局域网。</p>
    <input id="gateCode" inputmode="numeric" maxlength="4" placeholder="0000" autocomplete="one-time-code">
    <button id="gateBtn">连接</button>
    <div id="gateMsg" class="hint" style="margin-top:12px"></div>
  </div>
</div>
<main id="app">
  <header>
    <div class="brand"><span class="dot"></span>幕音<small>手机伴侣</small></div>
    <div id="conn" class="pill">连接中</div>
  </header>
  <section class="search card">
    <div class="label">在电视上搜索</div>
    <form id="searchForm">
      <input id="q" placeholder="歌名 / 歌手" autocomplete="off" autocapitalize="off">
      <button type="submit">发送</button>
    </form>
    <div class="chips">
      <button class="chip" data-q="晴天">晴天</button>
      <button class="chip" data-q="周杰伦">周杰伦</button>
      <button class="chip" data-q="夜航星">夜航星</button>
      <button class="chip" data-q="海阔天空">海阔天空</button>
    </div>
    <div id="searchMsg" class="hint"></div>
  </section>
  <section class="now card">
    <img id="cover" class="cover" alt="" style="display:none">
    <div class="nowBody">
      <div id="status" class="label">电视端未播放</div>
      <h1 id="title">—</h1>
      <div id="artist" class="sub">—</div>
      <div class="progress"><div id="bar"></div><input id="seek" type="range" min="0" max="1000" value="0"></div>
      <div class="times"><span id="pos">0:00</span><span id="dur">0:00</span></div>
      <div class="controls">
        <button id="prev" aria-label="上一首">⏮</button>
        <button id="minus" aria-label="快退 10 秒">−10</button>
        <button id="play" class="primary" aria-label="播放/暂停">▶</button>
        <button id="plus" aria-label="快进 10 秒">+10</button>
        <button id="next" aria-label="下一首">⏭</button>
      </div>
    </div>
  </section>
  <section class="queue card">
    <div class="queueHead"><div class="label">播放队列</div><div id="qcount" class="hint">0 首</div></div>
    <div id="queue" class="queueList"></div>
  </section>
  <footer>电视与手机需在同一局域网<br>搜索会直接发送到电视并打开结果页</footer>
</main>
<script>
(function(){
  var params = new URLSearchParams(location.search);
  var code = params.get('code') || localStorage.getItem('tvmusic_code') || '';
  var gate = document.getElementById('gate');
  var gateCode = document.getElementById('gateCode');
  var gateBtn = document.getElementById('gateBtn');
  var gateMsg = document.getElementById('gateMsg');
  var conn = document.getElementById('conn');
  var title = document.getElementById('title');
  var artist = document.getElementById('artist');
  var cover = document.getElementById('cover');
  var statusEl = document.getElementById('status');
  var posEl = document.getElementById('pos');
  var durEl = document.getElementById('dur');
  var bar = document.getElementById('bar');
  var seek = document.getElementById('seek');
  var playBtn = document.getElementById('play');
  var queueEl = document.getElementById('queue');
  var qcount = document.getElementById('qcount');
  var searchMsg = document.getElementById('searchMsg');
  var dragging = false;
  var lastQueueKey = '';

  cover.addEventListener('error', function(){ cover.style.display = 'none'; });

  function showGate(show){ gate.classList.toggle('hidden', !show); if(show){ setTimeout(function(){gateCode.focus();}, 120); } }
  if(!code) showGate(true);

  function api(path, options){
    var sep = path.indexOf('?') >= 0 ? '&' : '?';
    var url = path + sep + 'code=' + encodeURIComponent(code);
    return fetch(url, options || {}).then(function(r){
      if(r.status === 401){ gateMsg.textContent = '配对码不正确，请重新输入'; showGate(true); throw new Error('pairing'); }
      if(!r.ok) throw new Error('http');
      return r.json();
    });
  }

  gateBtn.addEventListener('click', function(){
    var value = gateCode.value.replace(/\D/g,'').slice(0,4);
    if(value.length !== 4){ gateMsg.textContent = '请输入 4 位配对码'; return; }
    code = value;
    localStorage.setItem('tvmusic_code', code);
    gateMsg.textContent = '';
    showGate(false);
    poll();
  });
  gateCode.addEventListener('keydown', function(e){ if(e.key === 'Enter') gateBtn.click(); });

  function fmt(ms){
    ms = Math.max(0, Number(ms) || 0);
    var s = Math.floor(ms / 1000);
    var m = Math.floor(s / 60);
    s = s % 60;
    return m + ':' + (s < 10 ? '0' : '') + s;
  }
  function esc(value){
    var d = document.createElement('div');
    d.textContent = value == null ? '' : String(value);
    return d.innerHTML;
  }
  function post(path){
    return api(path, {method:'POST'}).catch(function(e){
      if(e.message !== 'pairing') conn.textContent = '连接断开';
      throw e;
    });
  }
  function control(action, value){
    var path = '/api/control?action=' + encodeURIComponent(action);
    if(value !== undefined) path += '&value=' + encodeURIComponent(value);
    post(path).then(poll).catch(function(){});
  }
  function playAt(index){
    post('/api/queue/play?index=' + index).then(poll).catch(function(){});
  }

  document.getElementById('prev').addEventListener('click', function(){ control('prev'); });
  document.getElementById('next').addEventListener('click', function(){ control('next'); });
  document.getElementById('minus').addEventListener('click', function(){ control('seekBy', -10000); });
  document.getElementById('plus').addEventListener('click', function(){ control('seekBy', 10000); });
  playBtn.addEventListener('click', function(){ control('toggle'); });
  seek.addEventListener('input', function(){ dragging = true; });
  seek.addEventListener('change', function(){
    control('seekTo', Math.round(seek.value / 1000 * (Number(seek.dataset.duration) || 0)));
    dragging = false;
  });

  document.getElementById('searchForm').addEventListener('submit', function(e){
    e.preventDefault();
    var q = document.getElementById('q').value.trim();
    if(!q){ searchMsg.textContent = '先输入关键词'; return; }
    searchMsg.textContent = '发送中…';
    post('/api/input?text=' + encodeURIComponent(q)).then(function(){
      searchMsg.textContent = '已发送到电视，正在搜索「' + q + '」';
    }).catch(function(){
      searchMsg.textContent = '发送失败，请检查电视是否在同一网络';
    });
  });
  Array.prototype.forEach.call(document.querySelectorAll('.chip'), function(btn){
    btn.addEventListener('click', function(){
      document.getElementById('q').value = btn.getAttribute('data-q');
      document.getElementById('searchForm').dispatchEvent(new Event('submit', {cancelable:true}));
    });
  });

  function render(st){
    var track = st.track || {};
    title.textContent = track.title || '—';
    artist.textContent = [track.artist, track.album].filter(Boolean).join(' · ') || '—';
    if(track.cover){ cover.src = track.cover; cover.style.display = 'block'; }
    else { cover.removeAttribute('src'); cover.style.display = 'none'; }
    statusEl.textContent = !track.title ? '电视端未播放' : (st.buffering ? '正在解析音源…' : (st.playing ? '正在播放' : '已暂停'));
    playBtn.textContent = st.playing ? '⏸' : '▶';
    var duration = Number(st.durationMs) || 0;
    var position = Number(st.positionMs) || 0;
    posEl.textContent = fmt(position);
    durEl.textContent = fmt(duration);
    bar.style.width = (duration > 0 ? Math.min(100, position / duration * 100) : 0) + '%';
    seek.dataset.duration = String(duration);
    if(!dragging) seek.value = duration > 0 ? Math.round(position / duration * 1000) : 0;
    var queue = st.queue || [];
    qcount.textContent = queue.length + ' 首';
    var key = queue.map(function(t){ return (t.title || '') + '|' + (t.artist || '') + '|' + (t.current ? '1' : '0'); }).join(',');
    if(key !== lastQueueKey){
      lastQueueKey = key;
      queueEl.innerHTML = queue.map(function(t, i){
        return '<button class="qrow ' + (t.current ? 'current' : '') + '" data-i="' + i + '">' +
          '<span class="eq"></span><span class="qidx">' + (i + 1) + '</span>' +
          '<span class="qmeta"><span class="qtitle">' + esc(t.title) + '</span>' +
          '<span class="qartist">' + esc(t.artist) + '</span></span></button>';
      }).join('');
      Array.prototype.forEach.call(queueEl.querySelectorAll('.qrow'), function(row){
        row.addEventListener('click', function(){ playAt(Number(row.getAttribute('data-i'))); });
      });
    }
    conn.textContent = '已连接';
    conn.classList.add('ok');
  }

  function poll(){
    if(!code) return;
    api('/api/state').then(render).catch(function(e){
      if(e.message !== 'pairing'){
        conn.textContent = '连接断开';
        conn.classList.remove('ok');
      }
    });
  }

  poll();
  setInterval(poll, 1600);
  document.addEventListener('visibilitychange', function(){ if(!document.hidden) poll(); });
})();
</script>
</body>
</html>
"""

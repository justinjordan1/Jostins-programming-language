(function () {
  var encoder = new TextEncoder();
  var worker = null;
  var busy = false;
  var current = null;
  var shown = 0;
  var goal = 30;
  var landing = null;

  var stage = function (percent, words) {
    goal = percent;
    if (!landing) return;
    landing.querySelector(".words").textContent = words;
    if (percent >= 100) {
      var leaving = landing;
      setTimeout(function () { leaving.className = "gone"; }, 250);
      setTimeout(function () { leaving.remove(); if (landing === leaving) landing = null; }, 700);
    }
  };

  var creep = function () {
    if (!landing) return;
    shown += (goal - shown) * (goal >= 100 ? 0.3 : 0.06);
    landing.querySelector(".fill").style.width = shown.toFixed(1) + "%";
    requestAnimationFrame(creep);
  };

  var fresh = function () {
    if (worker) worker.terminate();
    busy = false;
    worker = new Worker("worker.js");
    worker.onmessage = function (e) {
      var text = e.data;
      if (text === "@@ready") {
        stage(70, "running the first example");
        return;
      }
      if (!current) return;
      current.enqueue(encoder.encode(text));
      if (text.indexOf("@@error ") === 0 || text.endsWith("\n@@asm\n")) {
        current.close();
        current = null;
        busy = false;
        stage(100, "ready");
      }
    };
  };

  window.mexplRun = function (body, signal) {
    if (busy) fresh();
    if (!worker) fresh();
    var mine = null;
    var stream = new ReadableStream({
      start: function (controller) {
        mine = controller;
      }
    });
    current = mine;
    busy = true;
    signal.addEventListener("abort", function () {
      if (current === mine) {
        current = null;
        mine.close();
        fresh();
      }
    });
    worker.postMessage(body);
    return Promise.resolve({body: stream});
  };

  document.addEventListener("DOMContentLoaded", function () {
    var style = document.createElement("style");
    style.textContent = "#landing { position: fixed; inset: 0; z-index: 100; display: flex; flex-direction: column; align-items: center;"
        + " justify-content: center; gap: 18px; background: #0d1b3d; color: #e8ecf7; transition: opacity 0.4s; }"
        + "#landing.gone { opacity: 0; pointer-events: none; }"
        + "#landing h1 { margin: 0; font-size: 44px; font-weight: 700; letter-spacing: -1px; }"
        + "#landing .bar { width: min(360px, 80vw); height: 6px; border-radius: 3px; background: rgba(255,255,255,0.12); overflow: hidden; }"
        + "#landing .fill { width: 0; height: 100%; border-radius: 3px; background: #bf28c7; }"
        + "#landing .words { font-size: 13px; color: #9aa6c4; }";
    document.head.appendChild(style);
    landing = document.createElement("div");
    landing.id = "landing";
    landing.innerHTML = "<h1>MeXpl</h1><div class=bar><div class=fill></div></div><div class=words>loading</div>";
    document.body.appendChild(landing);
    requestAnimationFrame(creep);
    var asm = document.getElementById("showAsm");
    asm.checked = false;
    asm.onchange();
  });
})();

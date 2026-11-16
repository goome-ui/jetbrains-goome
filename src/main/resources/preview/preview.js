(function () {
  const A = window.GMApp;
  const S = A.S;
  const noop = () => {};
  const stubs = { renderSide: noop, renderTop: noop, renderPalette: noop, renderDock: noop, scheduleCode: noop, refreshCode: noop, updateUndoPublic: noop, startDrag: noop, removeSel: noop, duplicate: noop, shift: noop, mpanel: noop, openTab: noop, elHooks: () => ({}) };
  for (const k in stubs) if (!A[k]) A[k] = stubs[k];
  A.renderAll = () => A.renderStage();

  const send = (m) => window.goomeSend(JSON.stringify(m));
  const err = document.getElementById("gpErr");
  const nameEl = document.getElementById("gpName");
  let good = false;
  let last = null;

  function showError(msg) {
    err.textContent = msg;
    err.hidden = !msg;
  }

  window.goomeLoad = function (text, file) {
    let obj;
    try {
      obj = JSON.parse(text);
    } catch (e) {
      showError((good ? "showing the last version that worked, " : "") + "this json doesnt parse: " + e.message);
      send({ type: "rendered", nodes: 0, error: e.message });
      return;
    }
    try {
      S.project = GM.normProject(obj);
      S.sel = [];
      S.pv = {};
      S.editing = null;
      if (A.resetVals) A.resetVals();
      A.renderStage();
      nameEl.textContent = S.project.name || file || "goome ui";
      good = true;
      last = obj;
      showError("");
      send({ type: "rendered", nodes: document.querySelectorAll("#stageWrap [data-nid]").length, error: "" });
    } catch (e) {
      showError("the preview tripped over this project: " + e.message);
      send({ type: "rendered", nodes: 0, error: e.message });
    }
  };

  function copy(lang) {
    if (!last) return send({ type: "error", error: "theres nothing that parses to copy yet" });
    try {
      const r = GMCode.generate(GM.normProject(GM.clone(last)), lang);
      send({ type: "copy", lang: lang, code: r.code, warnings: (r.warnings || []).map((w) => w.msg) });
    } catch (e) {
      send({ type: "error", error: "couldnt make the code: " + e.message });
    }
  }

  document.querySelectorAll("[data-copy]").forEach((b) => b.addEventListener("click", () => copy(b.dataset.copy)));
  document.getElementById("gpBuilder").addEventListener("click", () => send({ type: "builder" }));
  send({ type: "ready" });
})();

/*
 * Kiwix Layout Fixup content script (Gecko build only).
 *
 * Mirrors ArticleLayoutFixup on the WebView side: gives ZIM articles a mobile
 * viewport when they lack one, caps oversized media, and forces a right-to-left
 * direction on pages whose text is predominantly RTL but that do not declare a
 * direction themselves (e.g. some Hebrew ZIM files). Pages that already declare
 * `dir="ltr"` or `dir="rtl"` are left untouched.
 */
(function () {
  "use strict";
  try {
    function head() {
      return document.head || document.documentElement;
    }

    function addViewport() {
      // Fill the screen width and prevent pinch-zooming smaller than the screen
      // (minimum-scale=1); override the ZIM's own zoomable viewport if present.
      var vp = document.querySelector('meta[name="viewport"]');
      if (!vp) {
        vp = document.createElement("meta");
        vp.name = "viewport";
        head().appendChild(vp);
      }
      vp.setAttribute(
        "content",
        "width=device-width, initial-scale=1, minimum-scale=1"
      );
    }

    function addCss() {
      if (!document.getElementById("kiwix-layout-fixup")) {
        var s = document.createElement("style");
        s.id = "kiwix-layout-fixup";
        s.textContent =
          "html,body{margin:0!important;max-width:100%!important;width:auto!important;}" +
          "body{padding:8px!important;border:none!important;box-sizing:border-box!important;}" +
          "#mw-panel,#mw-head,#mw-head-base,#mw-page-base,#mw-navigation," +
          "#left-navigation,#right-navigation,#mw-data-after-content{display:none!important;}" +
          "#content,.mw-body,#bodyContent,#mw-content-text,.mw-body-content," +
          ".mw-parser-output{margin:0!important;padding:0!important;max-width:100%!important;" +
          "width:auto!important;border:none!important;box-sizing:border-box!important;}" +
          "img,video{max-width:100%!important;height:auto!important;}" +
          "table{display:block!important;overflow-x:auto!important;max-width:100%!important;}";
        head().appendChild(s);
      }
    }

    function applyRtl() {
      var htmlEl = document.documentElement;
      if (!htmlEl) return;
      var dir = (htmlEl.getAttribute("dir") || "").toLowerCase();
      // Respect an explicitly declared direction.
      if (dir === "rtl" || dir === "ltr") return;
      var text = (document.body && document.body.innerText) || "";
      var sample = text.slice(0, 400);
      // Hebrew (U+0590-05FF), Arabic (U+0600-06FF), Syriac (U+0700-074F).
      var rtl = (sample.match(/[֐-׿؀-ۿ܀-ݏ]/g) || [])
        .length;
      var ltr = (sample.match(/[A-Za-z]/g) || []).length;
      if (rtl > 0 && rtl > ltr) {
        htmlEl.setAttribute("dir", "rtl");
      }
    }

    // The viewport must be in place as early as possible to avoid a desktop
    // width layout flash; the RTL/media fixes need the DOM.
    addViewport();
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", function () {
        addCss();
        applyRtl();
      });
    } else {
      addCss();
      applyRtl();
    }
  } catch (e) {
    // Never let the fixup break page rendering.
  }
})();

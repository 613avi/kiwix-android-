/*
 * Kiwix Android
 * Copyright (c) 2026 Kiwix <android.kiwix.org>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.kiwix.kiwixmobile.core.main.reader

/**
 * Fixes the on-screen layout of ZIM articles that render badly on a phone:
 *
 * - Many ZIM pages have no mobile `<meta name="viewport">`, so the renderer
 *   lays them out at desktop width and the text overflows/hides off screen
 *   (e.g. the "shironet" ZIM). We add a device-width viewport when missing.
 * - Hebrew (and other RTL) ZIM files whose HTML does not declare
 *   `dir="rtl"` render left aligned, leaving the right side blank (e.g. the
 *   "hamichlol" ZIM). When the book language is RTL we force `dir="rtl"`.
 * - Wide images/tables are capped to the viewport width so they do not force
 *   horizontal scrolling.
 *
 * The same fix is applied in both renderers: the WebView build injects
 * [injectionJs] after each page load, while the Gecko build ships an equivalent
 * content script (see the `layoutfix` web extension in the gecko source set).
 */
object ArticleLayoutFixup {
  // ISO 639-1/639-2 codes of right-to-left languages that ZIM files use in
  // their "Language" metadata. Kept lowercase for case-insensitive matching.
  // Hebrew, Arabic, Persian, Urdu, Yiddish, Aramaic, Divehi, Syriac,
  // Central Kurdish (Sorani), Pashto, Sindhi and Uyghur.
  private val RTL_LANGUAGE_CODES = setOf(
    "he", "heb",
    "ar", "ara",
    "fa", "fas", "per",
    "ur", "urd",
    "yi", "yid",
    "arc",
    "dv", "div",
    "syr",
    "ckb",
    "ps", "pus",
    "sd", "snd",
    "ug", "uig"
  )

  /**
   * Whether a ZIM `Language` metadata value denotes a right-to-left language.
   * The metadata may list several comma/semicolon separated codes.
   */
  fun isRtlLanguage(language: String?): Boolean {
    if (language.isNullOrBlank()) return false
    return language.split(',', ';', ' ', '-')
      .any { it.trim().lowercase() in RTL_LANGUAGE_CODES }
  }

  // CSS applied to every article. Fixes the two common problems on a phone:
  // oversized media that forces horizontal scrolling, and the MediaWiki
  // "Vector" skin reserving a wide side margin for a (stripped) sidebar, which
  // squeezes the article into a narrow column and leaves a large empty strip.
  private const val LAYOUT_CSS =
    "html,body{margin:0!important;max-width:100%!important;width:auto!important;}" +
      "body{padding:8px!important;border:none!important;box-sizing:border-box!important;}" +
      // Hide the MediaWiki Vector skin scaffolding (sidebar, header, page base)
      // that is empty in an offline ZIM but still reserves a wide margin/strip.
      "#mw-panel,#mw-head,#mw-head-base,#mw-page-base,#mw-navigation," +
      "#left-navigation,#right-navigation,#mw-data-after-content{display:none!important;}" +
      // Let the article body use the full width (no fixed content column).
      "#content,.mw-body,#bodyContent,#mw-content-text,.mw-body-content," +
      ".mw-parser-output{margin:0!important;padding:0!important;max-width:100%!important;" +
      "width:auto!important;border:none!important;box-sizing:border-box!important;}" +
      "img,video{max-width:100%!important;height:auto!important;}" +
      // Long content (wide tables) scrolls inside itself instead of the page.
      "table{display:block!important;overflow-x:auto!important;max-width:100%!important;}"

  // Viewport that fills the screen width and, crucially, cannot be pinch-zoomed
  // smaller than the screen (minimum-scale=1). Zooming in is still allowed.
  private const val VIEWPORT_CONTENT = "width=device-width, initial-scale=1, minimum-scale=1"

  /**
   * JavaScript that adds a device-width viewport (when the page has none), caps
   * oversized media, and — when [isRtl] is true — sets the document direction to
   * right-to-left. Safe to run more than once.
   */
  fun injectionJs(isRtl: Boolean): String {
    val setRtl = if (isRtl) {
      "document.documentElement.setAttribute('dir','rtl');"
    } else {
      ""
    }
    return """
      (function(){
        try {
          var head = document.head || document.documentElement;
          var vp = document.querySelector('meta[name="viewport"]');
          if (!vp) {
            vp = document.createElement('meta');
            vp.name = 'viewport';
            head.appendChild(vp);
          }
          vp.setAttribute('content', '$VIEWPORT_CONTENT');
          if (!document.getElementById('kiwix-layout-fixup')) {
            var s = document.createElement('style');
            s.id = 'kiwix-layout-fixup';
            s.textContent = '$LAYOUT_CSS';
            head.appendChild(s);
          }
          $setRtl
        } catch (e) {}
      })();
      """.trimIndent()
  }
}

package com.hanifedma.ponder.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.io.ByteArrayInputStream
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.ponder.core.Embeds

/**
 * Plays linked media inside the app instead of handing it to YouTube, Instagram
 * or a browser.
 *
 * Everything runs in a WebView pointed at the service's own embed page, which is
 * how the web app does it too — it means no per-service SDK, no API keys, and
 * the players stay current on their own. Direct video files are wrapped in a
 * minimal HTML5 page.
 *
 * The player is only created once someone actually presses play: a WebView is
 * expensive, and a list of them would be slow.
 */
@Composable
fun InlineWebPlayer(
    media: Embeds.Media,
    modifier: Modifier = Modifier,
    onOpenExternally: () -> Unit,
) {
    val embedUrl = media.embedUrl ?: return
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Set while the page is in full-screen mode; holds the view the player asked
    // us to display over everything else.
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    // Held here rather than read back with WebView.getWebChromeClient(), which
    // only exists from API 26 and would crash on the older phones this supports.
    var chromeClient by remember { mutableStateOf<PlayerChromeClient?>(null) }
    // Set when the service refuses to play here — most often because the video's
    // owner disabled embedding.
    var refused by remember(media.id) { mutableStateOf(false) }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val client = PlayerChromeClient(
                    onEnterFullscreen = { view -> fullscreenView = view },
                    onExitFullscreen = { fullscreenView = null },
                )
                chromeClient = client
                // YouTube checks where the embed is being shown, so its player
                // page is served from a real origin (see shouldInterceptRequest).
                val servedPage =
                    if (media.kind == Embeds.Kind.YOUTUBE) youtubePlayerPage(media.id) else null
                createPlayerWebView(
                    context = ctx,
                    chrome = client,
                    page = servedPage,
                    onExternalLink = { onOpenExternally() },
                ).also { view ->
                    webView = view
                    view.addJavascriptInterface(
                        PlayerBridge { refused = true },
                        "PonderPlayer",
                    )
                    if (servedPage != null) view.loadUrl(PLAYER_URL)
                    else view.loadPlayer(media, embedUrl)
                }
            },
        )
        if (refused) {
            EmbedRefusedNotice(media = media, onOpenExternally = onOpenExternally)
        }
    }

    // Stop playback the moment the card leaves the screen, so nothing keeps
    // talking from a card you scrolled past.
    DisposableEffect(embedUrl) {
        onDispose {
            webView?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
            }
            webView = null
            chromeClient = null
            fullscreenView = null
            activity?.showSystemUi()
        }
    }

    val custom = fullscreenView
    if (custom != null) {
        BackHandler(enabled = true) { chromeClient?.onHideCustomView() }
        Dialog(
            onDismissRequest = { chromeClient?.onHideCustomView() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
            ),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                factory = { ctx -> FrameLayout(ctx).apply { setBackgroundColor(android.graphics.Color.BLACK) } },
                update = { frame ->
                    if (custom.parent !== frame) {
                        (custom.parent as? ViewGroup)?.removeView(custom)
                        frame.removeAllViews()
                        frame.addView(
                            custom,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            ),
                        )
                    }
                },
            )
        }
        DisposableEffect(custom) {
            activity?.hideSystemUi()
            onDispose { activity?.showSystemUi() }
        }
    }
}

/** Handles the player asking to go full screen, and coming back out again. */
private class PlayerChromeClient(
    private val onEnterFullscreen: (View) -> Unit,
    private val onExitFullscreen: () -> Unit,
) : WebChromeClient() {
    private var callback: CustomViewCallback? = null

    override fun onShowCustomView(view: View, cb: CustomViewCallback) {
        if (callback != null) {
            cb.onCustomViewHidden()
            return
        }
        callback = cb
        onEnterFullscreen(view)
    }

    override fun onHideCustomView() {
        callback?.onCustomViewHidden()
        callback = null
        onExitFullscreen()
    }
}

/**
 * The only thing the player page can call back into: it reports that the video
 * cannot be shown here. Nothing is passed the other way, so this exposes no app
 * data to the page.
 */
private class PlayerBridge(private val onRefused: () -> Unit) {
    @android.webkit.JavascriptInterface
    fun onPlayerError(code: String) {
        android.util.Log.w("PonderPlayer", "embed refused: " + code)
        android.os.Handler(android.os.Looper.getMainLooper()).post { onRefused() }
    }
}

/** Shown over the player when the service won't allow embedding. */
@Composable
private fun EmbedRefusedNotice(media: Embeds.Media, onOpenExternally: () -> Unit) {
    val tr = com.hanifedma.ponder.ui.theme.PonderTheme.tr
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onOpenExternally() },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            androidx.compose.material3.Text(
                text = tr("media.noEmbed"),
                color = Color.White,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            androidx.compose.material3.Text(
                text = tr.f("media.open", "label" to media.label),
                color = Color(0xFF4ADE80),
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createPlayerWebView(
    context: Context,
    chrome: PlayerChromeClient,
    /** Page served for [PLAYER_URL]; null when the media is loaded another way. */
    page: String?,
    onExternalLink: () -> Unit,
): WebView = WebView(context).apply {
    setBackgroundColor(android.graphics.Color.BLACK)
    layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
    )
    with(settings) {
        javaScriptEnabled = true          // every embed player needs it
        domStorageEnabled = true
        loadWithOverviewMode = true
        useWideViewPort = true
        // Without this the embed refuses to start until a second tap inside the
        // player; the first tap on the poster is the gesture we already have.
        mediaPlaybackRequiresUserGesture = false
        // No browser chrome inside a card.
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
    }

    webChromeClient = chrome

    webViewClient = object : WebViewClient() {
        // Answers the one made-up URL the player page lives at. Going through a
        // real https origin (rather than loadDataWithBaseURL, which produces an
        // opaque one and sends no Referer) is what lets YouTube accept the
        // embed instead of failing with "video player configuration error".
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            if (page == null) return null
            val url = request.url
            if (url.host != PLAYER_HOST || url.path != PLAYER_PATH) return null
            return WebResourceResponse(
                "text/html",
                "utf-8",
                ByteArrayInputStream(page.toByteArray(Charsets.UTF_8)),
            )
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            // The embed itself stays in here; a tap through to the full site
            // (a channel page, a profile) goes out to the real app.
            val url = request.url.toString()
            if (isPlayerUrl(url)) return false
            onExternalLink()
            return true
        }
    }
}

/**
 * Loads the right kind of player page.
 *
 * The embeds are put inside an iframe on a page whose base URL is the service's
 * own origin. Pointing the WebView straight at an embed URL sends no referrer,
 * and YouTube answers that with "Error 153: video player configuration error"
 * instead of playing anything.
 */
private fun WebView.loadPlayer(media: Embeds.Media, embedUrl: String) {
    when (media.kind) {
        Embeds.Kind.VIDEO ->
            loadDataWithBaseURL(null, html5VideoPage(embedUrl), "text/html", "utf-8", null)

        Embeds.Kind.VIMEO ->
            loadDataWithBaseURL(
                "https://player.vimeo.com", iframePage(embedUrl), "text/html", "utf-8", null,
            )

        else ->
            loadDataWithBaseURL(
                "https://www.instagram.com", iframePage(embedUrl), "text/html", "utf-8", null,
            )
    }
}

// A host reserved by AndroidX for exactly this: it never resolves on the real
// internet, so the page can only ever be answered by us, yet the WebView treats
// it as an ordinary secure origin and sends a proper Referer.
private const val PLAYER_HOST = "appassets.androidplatform.net"
private const val PLAYER_PATH = "/ponder-player.html"
private const val PLAYER_URL = "https://$PLAYER_HOST$PLAYER_PATH"
private const val PLAYER_ORIGIN = "https://$PLAYER_HOST"

/**
 * YouTube's own IFrame Player API, which is the supported way to embed inside an
 * app. Loading the /embed/ URL straight into a WebView sends no usable origin
 * and YouTube answers with "video player configuration error" instead of
 * playing. Errors are handed back to Kotlin so the card can offer to open the
 * video in the YouTube app rather than leaving YouTube's own error card sitting
 * there.
 */
private fun youtubePlayerPage(videoId: String): String {
    val id = videoId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    return """
        <!doctype html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}
          #player{width:100%;height:100%}
        </style></head>
        <body>
          <div id="player"></div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            function report(code) {
              try { PonderPlayer.onPlayerError(String(code)); } catch (e) {}
            }
            function onYouTubeIframeAPIReady() {
              new YT.Player('player', {
                videoId: '$id',
                playerVars: {
                  autoplay: 1, playsinline: 1, rel: 0, modestbranding: 1,
                  origin: '$PLAYER_ORIGIN'
                },
                events: {
                  onReady: function (e) { e.target.playVideo(); },
                  onError: function (e) { report(e.data); }
                }
              });
            }
            // If the API script itself never arrives, say so rather than hang.
            setTimeout(function () {
              if (typeof YT === 'undefined') report('no-api');
            }, 12000);
          </script>
        </body></html>
    """.trimIndent()
}

private fun iframePage(src: String): String = """
    <!doctype html><html><head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
      html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}
      iframe{border:0;display:block;width:100%;height:100%}
    </style></head>
    <body><iframe src="${escapeHtml(src)}"
      allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
      allowfullscreen referrerpolicy="origin"></iframe></body></html>
""".trimIndent()

private fun html5VideoPage(src: String): String = """
    <!doctype html><html><head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
      html,body{margin:0;padding:0;background:#000;height:100%}
      video{width:100%;height:100%;object-fit:contain;background:#000}
    </style></head>
    <body><video src="${escapeHtml(src)}" controls autoplay playsinline
      preload="metadata"></video></body></html>
""".trimIndent()

private fun escapeHtml(s: String): String = s
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

/** URLs the in-app player should handle rather than push out to another app. */
private fun isPlayerUrl(url: String): Boolean {
    val u = url.lowercase()
    return u.startsWith(PLAYER_URL) ||
        u.startsWith("about:") ||
        u.startsWith("data:") ||
        u.contains("/embed/") ||
        u.contains("player.vimeo.com") ||
        u.contains("youtube.com/embed") ||
        u.contains("youtube-nocookie.com") ||
        u.contains("googlevideo.com")
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun Activity.hideSystemUi() {
    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
}

private fun Activity.showSystemUi() {
    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
}

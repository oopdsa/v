package com.example.pgck2;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.view.ViewGroup;

/** One embedded-player implementation shared by preview and full playback. */
final class YouTubeEmbed {
    interface Listener {
        void onLoading(boolean loading);
        void onError(String message);
    }

    static void load(WebView view, String id, boolean preview, Listener listener) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{11}")) {
            listener.onError("Liên kết YouTube không hợp lệ.");
            return;
        }
        String origin = "https://" + view.getContext().getPackageName().toLowerCase(java.util.Locale.ROOT);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setUserAgentString(WebSettings.getDefaultUserAgent(view.getContext()));
        view.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView webView, String url, Bitmap icon) {
                listener.onLoading(true);
            }
            @Override public void onReceivedError(WebView webView, WebResourceRequest request,
                                                   WebResourceError error) {
                if (request.isForMainFrame()) {
                    listener.onLoading(false);
                    listener.onError("Không tải được YouTube. Kiểm tra kết nối mạng.");
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                openExternally(webView.getContext(), request.getUrl());
                return true;
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView webView, String title) {
                if (title == null) return;
                if (title.equals("yt:ready") || title.startsWith("yt:state:")) listener.onLoading(false);
                if (title.startsWith("yt:error:")) {
                    listener.onLoading(false);
                    listener.onError("YouTube không phát được video này (" + title.substring(9)
                            + "). Video có thể bị giới hạn hoặc tắt nhúng.");
                }
            }
        });
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<meta name='referrer' content='strict-origin-when-cross-origin'>"
                + "<style>html,body{margin:0;width:100%;height:100%;background:#000}#ytplayer{width:100%;height:100%;border:0}</style>"
                + "</head><body><div id='ytplayer'></div><script>var player;"
                + "function onYouTubeIframeAPIReady(){player=new YT.Player('ytplayer',{videoId:'" + id + "',"
                + "playerVars:{playsinline:1,autoplay:1,fs:0,origin:'" + origin + "'},events:{"
                + "onReady:function(e){" + (preview ? "e.target.mute();" : "")
                + "document.title='yt:ready';e.target.playVideo();},"
                + "onStateChange:function(e){document.title='yt:state:'+e.data;},"
                + "onError:function(e){document.title='yt:error:'+e.data;},"
                + "onAutoplayBlocked:function(){document.title='yt:ready';}}});}"
                + "</script><script src='https://www.youtube.com/iframe_api'></script></body></html>";
        view.loadDataWithBaseURL(origin + "/", html, "text/html", "utf-8", null);
    }

    static void openExternally(Context context, Uri uri) {
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) return;
        try { context.startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (ActivityNotFoundException error) {
            Toast.makeText(context, "Không có ứng dụng mở liên kết này.", Toast.LENGTH_SHORT).show();
        }
    }

    static void release(WebView view) {
        view.stopLoading();
        view.loadUrl("about:blank");
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        view.setWebChromeClient(null);
        view.setWebViewClient(new WebViewClient());
        view.removeAllViews();
        view.destroy();
    }
}

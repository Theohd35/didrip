package com.didvip.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public final class MainActivity extends Activity {
    private static final String HOME_URL = "https://didvip.com/b6ig41m4d/c/didvip/29/0";
    private FrameLayout root;
    private WebView webView;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setFlags(0x01000000, 0x01000000); // FLAG_HARDWARE_ACCELERATED

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        configureWebView();
        enterImmersiveMode();
        if (state == null) webView.loadUrl(HOME_URL); else webView.restoreState(state);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " AndroidTV Formuler-Z11-Pro-Max");
        webView.addJavascriptInterface(new TvBridge(), "DidVipTV");

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override public void onPageFinished(WebView view, String url) {
                injectTvNavigation();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenView = view;
                fullscreenCallback = callback;
                webView.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                enterImmersiveMode();
            }

            @Override public void onHideCustomView() { hideFullscreenVideo(); }
        });
    }

    private void injectTvNavigation() {
        webView.evaluateJavascript(TV_NAVIGATION_SCRIPT, null);
    }

    private void enterImmersiveMode() {
        // Formuler firmware honors these legacy TV flags reliably, including while
        // a WebChromeClient custom video view is attached to the window.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    private void hideFullscreenVideo() {
        if (fullscreenView == null) return;
        root.removeView(fullscreenView);
        fullscreenView = null;
        webView.setVisibility(View.VISIBLE);
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
        enterImmersiveMode();
        webView.requestFocus();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            Log.d("FormulerRemote", "Pressed KeyCode: " + event.getKeyCode());
        }
        if (isMediaPlaybackKey(event.getKeyCode())) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                playVideoFullscreen(false);
            }
            // Consume both down and up so Android does not also route the key to a
            // second media session after the WebView has handled it.
            return true;
        }
        if (isOkKey(event.getKeyCode())) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                // The first OK on Regarder switches tabs and focuses the revealed
                // player. A second OK, now inside that player, starts playback.
                webView.evaluateJavascript(
                        "window.__didVipActivate&&window.__didVipActivate()", null);
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && fullscreenView == null) {
            String direction = null;
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) direction = "up";
            else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) direction = "down";
            else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) direction = "left";
            else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) direction = "right";
            if (direction != null) {
                webView.evaluateJavascript("window.__didVipMove&&window.__didVipMove('" + direction + "')", null);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean isMediaPlaybackKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_PROG_RED
                || keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    private static boolean isOkKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
    }

    private void playVideoFullscreen(boolean requireFocusedTarget) {
        // Keep the native chrome immersive even when a player does not expose the
        // HTML fullscreen API. onShowCustomView handles players that do expose it.
        enterImmersiveMode();
        webView.evaluateJavascript(
                PLAY_FULLSCREEN_SCRIPT.replace("__REQUIRE_FOCUS__", Boolean.toString(requireFocusedTarget)),
                null);
    }

    @Override public void onBackPressed() {
        if (fullscreenView != null) hideFullscreenVideo();
        else if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onResume() { super.onResume(); webView.onResume(); enterImmersiveMode(); }
    @Override protected void onPause() { webView.onPause(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle out) { webView.saveState(out); super.onSaveInstanceState(out); }
    @Override protected void onDestroy() {
        if (fullscreenView != null) hideFullscreenVideo();
        root.removeView(webView);
        webView.removeJavascriptInterface("DidVipTV");
        webView.destroy();
        super.onDestroy();
    }

    public final class TvBridge {
        @JavascriptInterface public void playbackStarted() { runOnUiThread(MainActivity.this::enterImmersiveMode); }
    }

    private static final String TV_NAVIGATION_SCRIPT = "(function(){" +
            "if(window.__didVipTvReady)return;window.__didVipTvReady=true;" +
            "var s=document.createElement('style');s.textContent='html,body{scrollbar-width:none!important;overscroll-behavior:none}::-webkit-scrollbar{display:none!important;width:0!important;height:0!important}.didvip-tv-focus{outline:none!important;box-shadow:0 0 10px 3px #E50914!important;border-radius:8px!important;position:relative!important;z-index:2147483646!important;transition:box-shadow .12s ease-out,transform .12s ease-out!important;transform:scale(1.025)!important}';document.head.appendChild(s);" +
            "var q='a[href],button,input,select,textarea,[role=button],[tabindex],video';" +
            "function visible(e){var r=e.getBoundingClientRect(),c=getComputedStyle(e);return r.width>2&&r.height>2&&c.visibility!='hidden'&&c.display!='none'}" +
            "function items(){return Array.prototype.filter.call(document.querySelectorAll(q),visible)}" +
            "function focus(e){document.querySelectorAll('.didvip-tv-focus').forEach(function(x){x.classList.remove('didvip-tv-focus')});e.classList.add('didvip-tv-focus');if(!e.hasAttribute('tabindex')&&!/^(A|BUTTON|INPUT|SELECT|TEXTAREA|VIDEO|IFRAME)$/.test(e.tagName))e.setAttribute('tabindex','-1');e.focus({preventScroll:true});e.scrollIntoView({block:'center',inline:'center',behavior:'smooth'})}" +
            "var focusAttempt=0;window.__didVipMove=function(d){focusAttempt++;var a=items();if(!a.length)return false;var cur=document.activeElement;if(a.indexOf(cur)<0){focus(a[0]);return true}var r=cur.getBoundingClientRect(),cx=r.left+r.width/2,cy=r.top+r.height/2,b=null,score=1/0;a.forEach(function(e){if(e===cur)return;var x=e.getBoundingClientRect(),dx=x.left+x.width/2-cx,dy=x.top+x.height/2-cy;if((d=='left'&&dx>=-2)||(d=='right'&&dx<=2)||(d=='up'&&dy>=-2)||(d=='down'&&dy<=2))return;var primary=(d=='left'||d=='right')?Math.abs(dx):Math.abs(dy),cross=(d=='left'||d=='right')?Math.abs(dy):Math.abs(dx),v=primary+cross*2.5;if(v<score){score=v;b=e}});if(b)focus(b);return !!b};" +
            "function playerTarget(){var a=Array.prototype.slice.call(document.querySelectorAll('.play-btn,.btn-play,[data-action=play],.vjs-big-play-button,.plyr__control[data-plyr=play],video,iframe,.video,.player,[class*=player]'));return a.find(visible)}" +
            "function inPlayer(e){return !!(e&&(e.matches('.play-btn,.btn-play,[data-action=play],.vjs-big-play-button,.plyr__control[data-plyr=play],video,iframe,.video,.player,[class*=player]')||e.closest('.video,.player,[class*=player]')))}" +
            "function startPlayer(e){var v=document.querySelector('video');if(!v&&e&&e.tagName==='IFRAME')try{v=e.contentDocument.querySelector('video')}catch(x){}if(v){try{var p=v.play();if(p&&p.catch)p.catch(function(){})}catch(x){}fullVideo(v)}else if(e)e.click();if(window.DidVipTV)DidVipTV.playbackStarted()}" +
            "window.__didVipActivate=function(){var a=document.activeElement,label=((a&&a.textContent)||'')+' '+((a&&a.getAttribute&&a.getAttribute('aria-label'))||'');if(/regarder/i.test(label)&&!inPlayer(a)){a.click();var token=++focusAttempt,tries=0;function seek(){if(token!==focusAttempt)return;var p=playerTarget();if(p){focus(p);return}if(++tries<4)setTimeout(seek,250)}setTimeout(seek,75);return 'tab'}if(inPlayer(a)){startPlayer(a);return 'player'}if(a&&a.click)a.click();return 'click'};" +
            "document.addEventListener('focusin',function(e){if(e.target.matches&&e.target.matches(q))focus(e.target)},true);" +
            "function fullVideo(v){if(!v)return;var f=v.requestFullscreen||v.webkitRequestFullscreen||v.webkitEnterFullscreen;if(f)try{var p=f.call(v);if(p&&p.catch)p.catch(function(){})}catch(x){}}" +
            "document.addEventListener('play',function(e){if(e.target.tagName==='VIDEO'){fullVideo(e.target);if(window.DidVipTV)DidVipTV.playbackStarted()}},true);" +
            "setTimeout(function(){var a=items();if(a.length)focus(a[0])},250);" +
            "})();";

    /**
     * Searches the main document and all same-origin player frames. Cross-origin
     * frames are intentionally skipped by the browser's same-origin policy; for
     * those players the script clicks their visible iframe/player control fallback.
     */
    private static final String PLAY_FULLSCREEN_SCRIPT = "(function(){var requireFocus=__REQUIRE_FOCUS__;" +
            "function docs(w,out){out.push(w.document);var f=w.document.querySelectorAll('iframe');for(var i=0;i<f.length;i++){try{if(f[i].contentWindow&&f[i].contentDocument)docs(f[i].contentWindow,out)}catch(e){}}return out}" +
            "function full(v){var f=v.requestFullscreen||v.webkitRequestFullscreen||v.webkitEnterFullscreen;if(f)try{var p=f.call(v);if(p&&p.catch)p.catch(function(){})}catch(e){}}" +
            "var all=docs(window,[]),active=document.activeElement;if(requireFocus){var label=((active&&active.textContent)||'')+' '+((active&&active.getAttribute&&active.getAttribute('aria-label'))||'');var player=active&&(active.tagName==='VIDEO'||active.tagName==='IFRAME'||active.closest('video,.video,.player,[class*=player],[data-action=play]'));if(!player&&!/regarder|play|lecture/i.test(label))return 'focus-not-player'}" +
            "var fsq='.vjs-fullscreen-control,.plyr__control[data-plyr=fullscreen],button[title*=\\\"Plein écran\\\"],button[aria-label*=Fullscreen],.fullscreen-icon,.vjs-big-play-button';" +
            "for(var x=0;x<all.length;x++){var fsBtn=all[x].querySelector(fsq);if(fsBtn){fsBtn.click();if(window.DidVipTV)DidVipTV.playbackStarted();return 'fullscreen-control'}}" +
            "var v=null;for(var i=0;i<all.length&&!v;i++)v=all[i].querySelector('video');" +
            "if(v){try{if(v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){})}}catch(e){}full(v);if(window.DidVipTV)DidVipTV.playbackStarted();return 'video'}" +
            "var q='.btn-play,[data-action=play],button,[role=button],[aria-label],.play,.play-button,.vjs-big-play-button,.jw-icon-playback';" +
            "for(var d=0;d<all.length;d++){var b=all[d].querySelectorAll(q);for(var j=0;j<b.length;j++){var t=((b[j].getAttribute('aria-label')||'')+' '+(b[j].textContent||'')+' '+(b[j].className||'')).toLowerCase();if(/play|lecture|regarder/.test(t)){b[j].click();if(window.DidVipTV)DidVipTV.playbackStarted();return 'control'}}}" +
            "var frame=document.querySelector('iframe');if(frame){frame.focus();frame.click();return 'iframe'}return 'none'" +
            "})();";
}

package com.example.wemakeapp;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    // Android system WebView — no bundled rendering engine, hence the small APK size.
    // TTS is supplied by android.speech.tts.TextToSpeech and bridged to a
    // window.speechSynthesis polyfill (see AndroidTTS interface + injected shim below).
    private static final String[] BLOCKED_PATTERNS = {  };
    private static final String INJECTED_JS = "(function() {\n  if (window.speechSynthesis && window.speechSynthesis.__androidBridge) return;\n  var __u = {};\n  var __c = 0;\n  window.__ttsEvent = function(id, type) {\n    var utt = __u[id];\n    if (!utt) return;\n    if (type === 'start') { try { if (utt.onstart) utt.onstart({ utterance: utt }); } catch(e) {} }\n    if (type === 'end') {\n      window.speechSynthesis.speaking = false;\n      try { if (utt.onend) utt.onend({ utterance: utt }); } catch(e) {}\n      delete __u[id];\n    }\n    if (type === 'error') {\n      window.speechSynthesis.speaking = false;\n      try { if (utt.onerror) utt.onerror({ utterance: utt, error: 'synthesis-failed' }); } catch(e) {}\n      delete __u[id];\n    }\n  };\n  function SpeechSynthesisUtterance(text) {\n    this.text = text || '';\n    this.lang = '';\n    this.rate = 1;\n    this.pitch = 1;\n    this.volume = 1;\n    this.voice = null;\n    this.onstart = null;\n    this.onend = null;\n    this.onerror = null;\n    this.onpause = null;\n    this.onresume = null;\n  }\n  window.SpeechSynthesisUtterance = SpeechSynthesisUtterance;\n  window.speechSynthesis = {\n    __androidBridge: true,\n    speaking: false,\n    pending: false,\n    paused: false,\n    speak: function(utterance) {\n      if (!window.AndroidTTS || !utterance) return;\n      var id = 'u' + (__c++);\n      __u[id] = utterance;\n      window.speechSynthesis.speaking = true;\n      try {\n        if (utterance.lang) window.AndroidTTS.setLanguage(utterance.lang);\n        window.AndroidTTS.speak(String(utterance.text || ''), utterance.rate || 1, utterance.pitch || 1, id);\n      } catch(e) {}\n    },\n    cancel: function() {\n      try { if (window.AndroidTTS) window.AndroidTTS.stop(); } catch(e) {}\n      window.speechSynthesis.speaking = false;\n      window.speechSynthesis.pending = false;\n    },\n    pause: function() { window.speechSynthesis.paused = true; },\n    resume: function() { window.speechSynthesis.paused = false; },\n    getVoices: function() { return []; },\n    onvoiceschanged: null\n  };\n})();\n(function(){\n  // Always: keyboard input visibility fix\n  function __siv(el){if(!el)return;var t=el.tagName?el.tagName.toLowerCase():'';if(t==='input'||t==='textarea'||el.contentEditable==='true'){setTimeout(function(){try{el.scrollIntoView({block:'center',behavior:'smooth'});}catch(e){}},150);}}\n  window.addEventListener('resize',function(){__siv(document.activeElement);});\n  document.addEventListener('focusin',function(e){__siv(e.target);});\n\n})();\n";
    private static final int FILE_CHOOSER_REQUEST = 51426;

    private WebView webView;
    private TextToSpeech tts;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                tts.setLanguage(Locale.getDefault());
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                runOnUiThread(() -> evalJs("window.__ttsEvent && window.__ttsEvent('" + utteranceId + "','start')"));
            }
            @Override
            public void onDone(String utteranceId) {
                runOnUiThread(() -> evalJs("window.__ttsEvent && window.__ttsEvent('" + utteranceId + "','end')"));
            }
            @Override
            public void onError(String utteranceId) {
                runOnUiThread(() -> evalJs("window.__ttsEvent && window.__ttsEvent('" + utteranceId + "','error')"));
            }
        });

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new TTSBridge(), "AndroidTTS");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                if (u.startsWith("intent:") || u.startsWith("market:") || u.startsWith("tel:") || u.startsWith("mailto:")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (ActivityNotFoundException e) { /* ignore */ }
                    return true;
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                for (String pattern : BLOCKED_PATTERNS) {
                    if (pattern.length() > 0 && u.contains(pattern)) {
                        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                evalJs(INJECTED_JS);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                evalJs(INJECTED_JS);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
        });

        setContentView(webView);
        webView.loadUrl("https://we-make-app--krutum2008.replit.app/");
    }

    private void evalJs(String script) {
        if (webView != null) webView.evaluateJavascript(script, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null && data.getDataString() != null) {
                results = new Uri[]{ Uri.parse(data.getDataString()) };
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
            } else {
                finish();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    /** Bridges window.speechSynthesis calls to Android's native TextToSpeech engine. */
    private class TTSBridge {
        @JavascriptInterface
        public void speak(String text, double rate, double pitch, String utteranceId) {
            if (tts == null || text == null) return;
            tts.setSpeechRate(rate > 0 ? (float) rate : 1.0f);
            tts.setPitch(pitch > 0 ? (float) pitch : 1.0f);
            tts.speak(text, TextToSpeech.QUEUE_ADD, new Bundle(), utteranceId);
        }

        @JavascriptInterface
        public void stop() {
            if (tts != null) tts.stop();
        }

        @JavascriptInterface
        public boolean isSpeaking() {
            return tts != null && tts.isSpeaking();
        }

        @JavascriptInterface
        public void setLanguage(String lang) {
            if (tts == null || lang == null || lang.isEmpty()) return;
            try {
                String[] parts = lang.split("-");
                Locale locale = parts.length > 1 ? new Locale(parts[0], parts[1]) : new Locale(parts[0]);
                tts.setLanguage(locale);
            } catch (Exception e) { /* ignore unsupported locale */ }
        }
    }
}

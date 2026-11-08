package space.nomadinfo.nexus;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    static final String URL = "https://dpurgar3.github.io/nexus/";
    static final String ACTION_GLAS = "space.nomadinfo.nexus.GLAS";

    private static final int REQ_MIC = 11, REQ_LOC = 12, REQ_FILE = 13, REQ_DICT = 14, REQ_MIC_THEN_TAP = 15, REQ_LOC_NATIVE = 16;
    private final ArrayList<Integer> locWaiting = new ArrayList<>();

    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean pageReady = false;
    private boolean pendingMicTap = false;
    private String pendingKeysJs = null;

    private SpeechRecognizer rec;
    private boolean listening = false;
    private String listenLang = "hr-HR";

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private volatile boolean speaking = false;
    private int ttsSeq = 0;
    private String lastUttId = "";

    private ValueCallback<Uri[]> fileCb;
    private String geoOrigin;
    private GeolocationPermissions.Callback geoCb;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0D0F14"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setTextZoom(100);
        s.setUserAgentString(s.getUserAgentString() + " NexusApp/1");

        web.addJavascriptInterface(new Bridge(), "NexusAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String sc = u.getScheme() == null ? "" : u.getScheme();
                if (u.toString().startsWith(URL)) return false;
                try {
                    Intent i;
                    if (sc.equals("intent")) {
                        i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
                    } else {
                        i = new Intent(Intent.ACTION_VIEW, u);
                    }
                    startActivity(i);
                } catch (Exception e) {
                    if (sc.equals("intent")) toast("Ta aplikacija nije instalirana.");
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                pageReady = true;
                if (pendingKeysJs != null) {
                    String js = pendingKeysJs;
                    pendingKeysJs = null;
                    web.evaluateJavascript(js, null);
                    return;
                }
                if (pendingMicTap) {
                    pendingMicTap = false;
                    tapMic();
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    Intent i = p.createIntent();
                    if (p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE)
                        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(Intent.createChooser(i, "Odaberi"), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    cb.invoke(origin, true, false);
                } else {
                    geoOrigin = origin;
                    geoCb = cb;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest r) {
                runOnUiThread(() -> r.deny());
            }
        });

        tts = new TextToSpeech(this, st -> {
            if (st == TextToSpeech.SUCCESS) {
                int res = tts.setLanguage(new Locale("hr", "HR"));
                ttsReady = true;
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED)
                    toast("Za čitanje naglas instaliraj hrvatski glas: Postavke → Opće upravljanje → Pretvaranje teksta u govor.");
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { speaking = true; }
                    @Override public void onDone(String id) { if (id.equals(lastUttId)) finishSpeak(); }
                    @Override public void onError(String id) { finishSpeak(); }
                    @Override public void onStop(String id, boolean interrupted) { if (id.equals(lastUttId)) finishSpeak(); }
                });
            }
        });

        handleIntent(getIntent());
        if (b != null) web.restoreState(b);
        if (web.getUrl() == null) web.loadUrl(URL);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handleIntent(i);
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        if (ACTION_GLAS.equals(i.getAction()) || i.getBooleanExtra("glas", false)) {
            if (pageReady) tapMic(); else pendingMicTap = true;
            return;
        }
        Uri d = i.getData();
        if (d != null && "nexusapp".equals(d.getScheme())) {
            try {
                JSONObject k = new JSONObject();
                k.put("g", nz(d.getQueryParameter("g")));
                k.put("t", nz(d.getQueryParameter("t")));
                k.put("q", nz(d.getQueryParameter("q")));
                String js = "(function(k){var key='nexus_settings',s={};try{s=JSON.parse(localStorage.getItem(key))||{};}catch(e){}" +
                        "if(k.g)s.gkey=k.g;if(k.t)s.token=k.t;if(k.q)s.qkey=k.q;" +
                        "if(s.sys===undefined)s.sys='';if(s.temp===undefined)s.temp=0.6;if(s.tts===undefined)s.tts=true;" +
                        "localStorage.setItem(key,JSON.stringify(s));localStorage.removeItem('nexus_gmodels2');location.reload();})(" + k + ")";
                if (pageReady) web.evaluateJavascript(js, null); else pendingKeysJs = js;
                toast("Ključevi preneseni u NEXUS ✓");
            } catch (Exception ignored) { }
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private void tapMic() {
        if (!has(Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC_THEN_TAP);
            return;
        }
        ui.postDelayed(() -> web.evaluateJavascript(
                "(function(){var b=document.getElementById('micBtn');if(b&&!b.classList.contains('rec'))b.click();})()", null), 500);
    }

    private boolean has(String p) {
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void toast(String t) {
        runOnUiThread(() -> Toast.makeText(this, t, Toast.LENGTH_LONG).show());
    }

    private void js(String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    private void srEvent(String type, String text, boolean fin, String err) {
        js("window.__nxSR&&window.__nxSR(" + JSONObject.quote(type) + "," + JSONObject.quote(text == null ? "" : text) + "," + fin + "," + JSONObject.quote(err == null ? "" : err) + ")");
    }

    /* ---------------- govor → tekst ---------------- */

    private void startListening() {
        if (!has(Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        stopSpeaking();
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            startDictationDialog();
            return;
        }
        try {
            if (rec == null) {
                rec = SpeechRecognizer.createSpeechRecognizer(this);
                rec.setRecognitionListener(new Listener());
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, listenLang);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, listenLang);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
            listening = true;
            rec.startListening(i);
            srEvent("start", null, false, null);
        } catch (Exception e) {
            listening = false;
            startDictationDialog();
        }
    }

    private void startDictationDialog() {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, listenLang);
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Govori…");
            startActivityForResult(i, REQ_DICT);
            srEvent("start", null, false, null);
        } catch (Exception e) {
            srEvent("error", null, false, "service-not-allowed");
            srEvent("end", null, false, null);
        }
    }

    private class Listener implements RecognitionListener {
        boolean gotFinal = false;
        @Override public void onReadyForSpeech(Bundle p) { gotFinal = false; }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float v) { }
        @Override public void onBufferReceived(byte[] b) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int t, Bundle p) { }

        @Override public void onPartialResults(Bundle r) {
            String t = first(r);
            if (t != null && !t.isEmpty()) srEvent("result", t, false, null);
        }

        @Override public void onResults(Bundle r) {
            String t = first(r);
            listening = false;
            if (t != null && !t.isEmpty()) srEvent("result", t, true, null);
            else srEvent("error", null, false, "no-speech");
            srEvent("end", null, false, null);
        }

        @Override public void onError(int e) {
            if (!listening) return;
            listening = false;
            if (e == SpeechRecognizer.ERROR_CLIENT || e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    || e == 12 /* LANGUAGE_NOT_SUPPORTED */ || e == 13 /* LANGUAGE_UNAVAILABLE */) {
                try { rec.destroy(); } catch (Exception ignored) { }
                rec = null;
                startDictationDialog();
                return;
            }
            String code;
            if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) code = "no-speech";
            else if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) code = "not-allowed";
            else if (e == SpeechRecognizer.ERROR_NETWORK || e == SpeechRecognizer.ERROR_NETWORK_TIMEOUT || e == SpeechRecognizer.ERROR_SERVER) code = "network";
            else if (e == SpeechRecognizer.ERROR_AUDIO) code = "audio-capture";
            else code = "greška " + e;
            srEvent("error", null, false, code);
            srEvent("end", null, false, null);
        }

        private String first(Bundle r) {
            ArrayList<String> l = r == null ? null : r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            return (l == null || l.isEmpty()) ? null : l.get(0);
        }
    }

    private void stopListening() {
        if (rec != null && listening) {
            try { rec.stopListening(); } catch (Exception ignored) { }
        }
    }

    /* ---------------- tekst → govor ---------------- */

    private void speakText(String text, float rate) {
        if (!ttsReady) { toast("Čitanje naglas još nije spremno, pokušaj za trenutak."); finishSpeak(); return; }
        tts.stop();
        tts.setSpeechRate(Math.max(0.5f, Math.min(2f, rate)));
        int max = Math.max(500, TextToSpeech.getMaxSpeechInputLength() - 100);
        ArrayList<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String sent : text.split("(?<=[.!?:;\\n])\\s+")) {
            if (cur.length() + sent.length() + 1 > max && cur.length() > 0) { parts.add(cur.toString()); cur.setLength(0); }
            while (sent.length() > max) { parts.add(sent.substring(0, max)); sent = sent.substring(max); }
            cur.append(sent).append(' ');
        }
        if (cur.toString().trim().length() > 0) parts.add(cur.toString());
        if (parts.isEmpty()) { finishSpeak(); return; }
        speaking = true;
        ttsSeq++;
        for (int i = 0; i < parts.size(); i++) {
            String id = "u" + ttsSeq + "_" + i;
            if (i == parts.size() - 1) lastUttId = id;
            tts.speak(parts.get(i), TextToSpeech.QUEUE_ADD, null, id);
        }
    }

    private void stopSpeaking() {
        lastUttId = "";
        speaking = false;
        if (tts != null) tts.stop();
    }

    private void finishSpeak() {
        speaking = false;
        js("window.__nxTTS&&window.__nxTTS()");
    }

    /* ---------------- lokacija (GPS telefona) ---------------- */

    private void locEvent(int id, Location l, String err) {
        if (l != null) js("window.__nxLoc&&window.__nxLoc(" + id + "," + l.getLatitude() + "," + l.getLongitude() + "," + (l.hasAccuracy() ? l.getAccuracy() : 500) + ",null)");
        else js("window.__nxLoc&&window.__nxLoc(" + id + ",0,0,0," + JSONObject.quote(err) + ")");
    }

    private void getLocation(int id) {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION) && !has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            locWaiting.add(id);
            if (locWaiting.size() == 1)
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC_NATIVE);
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) { locEvent(id, null, "nema"); return; }
        if (Build.VERSION.SDK_INT >= 28 && !lm.isLocationEnabled()) {
            toast("Uključi Lokaciju na mobitelu (povuci gornju traku → Lokacija).");
            locEvent(id, null, "iskljuceno");
            return;
        }
        Location best = null;
        try {
            for (String pr : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(pr);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) { }
        if (best != null && System.currentTimeMillis() - best.getTime() < 120000) locEvent(id, best, null);
        String prov = null;
        if (Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) prov = LocationManager.FUSED_PROVIDER;
        else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && has(Manifest.permission.ACCESS_FINE_LOCATION)) prov = LocationManager.GPS_PROVIDER;
        else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) prov = LocationManager.NETWORK_PROVIDER;
        final Location fallback = best;
        if (prov == null || Build.VERSION.SDK_INT < 30) {
            if (fallback != null) locEvent(id, fallback, null); else locEvent(id, null, "nema");
            return;
        }
        final CancellationSignal cs = new CancellationSignal();
        final boolean[] done = {false};
        ui.postDelayed(() -> { if (!done[0]) { done[0] = true; cs.cancel(); if (fallback != null) locEvent(id, fallback, null); else locEvent(id, null, "timeout"); } }, 15000);
        try {
            lm.getCurrentLocation(prov, cs, getMainExecutor(), l -> {
                if (done[0]) return;
                done[0] = true;
                if (l != null) locEvent(id, l, null);
                else if (fallback != null) locEvent(id, fallback, null);
                else locEvent(id, null, "nema");
            });
        } catch (SecurityException e) {
            done[0] = true;
            locEvent(id, null, "dozvola");
        }
    }

    /* ---------------- most prema web stranici ---------------- */

    private class Bridge {
        @JavascriptInterface public void listen(String lang) {
            ui.post(() -> { listenLang = (lang == null || lang.isEmpty()) ? "hr-HR" : lang; startListening(); });
        }
        @JavascriptInterface public void stopListen() { ui.post(MainActivity.this::stopListening); }
        @JavascriptInterface public void speak(String text, double rate) { ui.post(() -> speakText(text == null ? "" : text, (float) rate)); }
        @JavascriptInterface public void stopSpeak() { ui.post(MainActivity.this::stopSpeaking); }
        @JavascriptInterface public boolean isSpeaking() { return speaking; }
        @JavascriptInterface public void location(int id) { ui.post(() -> getLocation(id)); }
        @JavascriptInterface public boolean saveFile(String name, String content) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u == null) return false;
                try (OutputStream o = getContentResolver().openOutputStream(u)) {
                    o.write(content.getBytes(StandardCharsets.UTF_8));
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /* ---------------- dozvole i rezultati ---------------- */

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        boolean ok = r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED;
        if (req == REQ_MIC) {
            if (ok) startListening();
            else { srEvent("error", null, false, "not-allowed"); srEvent("end", null, false, null); }
        } else if (req == REQ_MIC_THEN_TAP) {
            if (ok) tapMic(); else toast("NEXUS treba dozvolu za mikrofon da bi te čuo.");
        } else if (req == REQ_LOC_NATIVE) {
            boolean any = false;
            for (int x : r) if (x == PackageManager.PERMISSION_GRANTED) any = true;
            ArrayList<Integer> w = new ArrayList<>(locWaiting);
            locWaiting.clear();
            for (int id : w) {
                if (any) getLocation(id);
                else { toast("NEXUS treba dozvolu za lokaciju."); locEvent(id, null, "dozvola"); }
            }
        } else if (req == REQ_LOC && geoCb != null) {
            boolean any = false;
            for (int x : r) if (x == PackageManager.PERMISSION_GRANTED) any = true;
            geoCb.invoke(geoOrigin, any, false);
            geoCb = null;
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE) {
            if (fileCb != null) {
                Uri[] out = null;
                if (res == RESULT_OK && data != null) {
                    if (data.getClipData() != null) {
                        int n = data.getClipData().getItemCount();
                        out = new Uri[n];
                        for (int i = 0; i < n; i++) out[i] = data.getClipData().getItemAt(i).getUri();
                    } else if (data.getData() != null) {
                        out = new Uri[]{data.getData()};
                    }
                }
                fileCb.onReceiveValue(out);
                fileCb = null;
            }
        } else if (req == REQ_DICT) {
            String t = null;
            if (res == RESULT_OK && data != null) {
                ArrayList<String> l = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (l != null && !l.isEmpty()) t = l.get(0);
            }
            if (t != null && !t.isEmpty()) srEvent("result", t, true, null);
            srEvent("end", null, false, null);
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript(
                "(function(){var m=document.getElementById('modal');if(m&&m.classList.contains('show')){m.classList.remove('show');return 1;}" +
                "var s=document.getElementById('side');if(s&&s.classList.contains('open')){s.classList.remove('open');return 1;}return 0;})()",
                v -> {
                    if ("1".equals(v)) return;
                    if (web.canGoBack()) web.goBack();
                    else moveTaskToBack(true);
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        if (rec != null) try { rec.destroy(); } catch (Exception ignored) { }
        if (tts != null) tts.shutdown();
        web.destroy();
        super.onDestroy();
    }
}

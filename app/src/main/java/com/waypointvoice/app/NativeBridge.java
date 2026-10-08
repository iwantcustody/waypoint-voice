package com.waypointvoice.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the web app can ask Android to do. Exposed to JavaScript as window.Native.
 * Results flow back by calling window.__native* functions in the page.
 */
public class NativeBridge {
    final MainActivity act;
    final WebView web;
    final Context ctx;
    final Handler main = new Handler(Looper.getMainLooper());

    NativeBridge(MainActivity a, WebView w) {
        act = a;
        web = w;
        ctx = a.getApplicationContext();
        am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        voice = new VoiceEngine(this, ctx);
    }

    /* ======================= Hands-free: wake phrase, phone mic, aux, car ======================= */
    final VoiceEngine voice;
    Runnable afterMic;

    /** Runs r once the microphone permission is granted (asks if needed). */
    void withMic(Runnable r) {
        if (has(Manifest.permission.RECORD_AUDIO)) { r.run(); return; }
        afterMic = r;
        main.post(() -> act.requestPermissions(new String[]{ Manifest.permission.RECORD_AUDIO }, MainActivity.REQ_ENGINE_MIC));
    }

    void onEngineMicResult(boolean ok) {
        Runnable r = afterMic;
        afterMic = null;
        if (ok && r != null) r.run();
        else if (!ok) js("window.__nativeWakeError && window.__nativeWakeError('Microphone permission is needed')");
    }

    @JavascriptInterface
    public String getAudioRoute() { return voice.routeJson(); }

    @JavascriptInterface
    public String speechStatus() { return voice.status(); }

    @JavascriptInterface
    public void downloadSpeech() { voice.ensureModel(); }

    @JavascriptInterface
    public void deleteSpeech() { new Thread(voice::deleteModel).start(); }

    @JavascriptInterface
    public void startWake(boolean phoneMic) { withMic(() -> voice.startWake(phoneMic)); }

    @JavascriptInterface
    public void stopWake() { voice.stopWake(); }

    @JavascriptInterface
    public void recordClip(boolean phoneMic, boolean transcribe) { withMic(() -> voice.recordClip(phoneMic, transcribe)); }

    @JavascriptInterface
    public void cancelClip() { voice.cancelClip(); }

    void onPause() { voice.onPause(NavService.running && NavService.withMic); }

    void onResume() { voice.onResume(); }

    // --- car Bluetooth ---
    @JavascriptInterface
    public String listBtDevices() {
        if (Build.VERSION.SDK_INT >= 31 && !has(Manifest.permission.BLUETOOTH_CONNECT)) {
            main.post(() -> act.requestPermissions(new String[]{ Manifest.permission.BLUETOOTH_CONNECT }, MainActivity.REQ_BT));
            return "need-permission";
        }
        org.json.JSONArray arr = new org.json.JSONArray();
        try {
            android.bluetooth.BluetoothManager bm = ctx.getSystemService(android.bluetooth.BluetoothManager.class);
            android.bluetooth.BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
            if (ad != null) {
                for (android.bluetooth.BluetoothDevice d : ad.getBondedDevices()) {
                    JSONObject o = new JSONObject();
                    o.put("name", d.getName() == null ? d.getAddress() : d.getName());
                    o.put("addr", d.getAddress());
                    arr.put(o);
                }
            }
        } catch (SecurityException e) {
            return "need-permission";
        } catch (Exception ignored) { }
        return arr.toString();
    }

    void onBtPermissionResult(boolean ok) {
        js("window.__nativeBtReady && window.__nativeBtReady(" + ok + ")");
    }

    @JavascriptInterface
    public void setCar(String addr, String name, boolean autoOpen) {
        CarReceiver.prefs(ctx).edit().putString("addr", addr == null ? "" : addr).putString("name", name == null ? "" : name)
                .putBoolean("autoOpen", autoOpen).apply();
    }

    @JavascriptInterface
    public boolean canDrawOverlays() { return Settings.canDrawOverlays(ctx); }

    @JavascriptInterface
    public void openOverlaySettings() {
        main.post(() -> {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.getPackageName()));
                act.startActivity(i);
            } catch (Exception ignored) { }
        });
    }

    void js(String code) {
        main.post(() -> {
            try { web.evaluateJavascript(code, null); } catch (Exception ignored) { }
        });
    }

    static String q(String s) { return JSONObject.quote(s == null ? "" : s); }

    boolean has(String perm) { return ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED; }

    /* ======================= GPS ======================= */
    LocationManager lm;
    LocationListener gpsListener, netListener;
    long lastGps = 0;
    boolean locWanted = false, locRunning = false;

    @JavascriptInterface
    public void startLocation() {
        main.post(() -> { locWanted = true; beginLocation(); });
    }

    void onLocationPermissionResult() {
        main.post(() -> {
            if (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                if (locWanted) beginLocation();
            } else {
                js("window.__nativeLocationDenied && window.__nativeLocationDenied()");
            }
        });
    }

    abstract static class SimpleListener implements LocationListener {
        // Implemented explicitly so older Android versions (8–9) don't crash.
        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
        @Override public void onProviderEnabled(String provider) { }
        @Override public void onProviderDisabled(String provider) { }
    }

    @SuppressWarnings("MissingPermission")
    void beginLocation() {
        if (locRunning) return;
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION) && !has(Manifest.permission.ACCESS_COARSE_LOCATION)) return;
        lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        gpsListener = new SimpleListener() {
            @Override public void onLocationChanged(Location l) { lastGps = System.currentTimeMillis(); pushLocation(l); }
        };
        netListener = new SimpleListener() {
            @Override public void onLocationChanged(Location l) { if (System.currentTimeMillis() - lastGps > 8000) pushLocation(l); }
        };
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsListener, Looper.getMainLooper()); } catch (Exception ignored) { }
        try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4000, 0, netListener, Looper.getMainLooper()); } catch (Exception ignored) { }
        try {
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (l != null) pushLocation(l);
        } catch (Exception ignored) { }
        locRunning = true;
    }

    void pushLocation(Location l) {
        try {
            JSONObject o = new JSONObject();
            o.put("lat", l.getLatitude());
            o.put("lng", l.getLongitude());
            o.put("acc", l.hasAccuracy() ? l.getAccuracy() : 30);
            o.put("speed", l.hasSpeed() ? l.getSpeed() : -1);
            o.put("hasBearing", l.hasBearing() && l.hasSpeed() && l.getSpeed() > 1);
            o.put("bearing", l.getBearing());
            o.put("t", l.getTime());
            js("window.__nativeLocation && window.__nativeLocation(" + o + ")");
        } catch (Exception ignored) { }
    }

    /* ======================= Navigation session ======================= */
    @JavascriptInterface
    public void navStarted(String dest) {
        main.post(() -> {
            act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (!has(Manifest.permission.ACCESS_FINE_LOCATION) && !has(Manifest.permission.ACCESS_COARSE_LOCATION)) return;
            Intent i = new Intent(ctx, NavService.class);
            i.putExtra("title", "Navigating to " + dest);
            i.putExtra("text", "Starting route");
            try { ctx.startForegroundService(i); } catch (Exception ignored) { }
        });
    }

    @JavascriptInterface
    public void navUpdate(String text) {
        main.post(() -> NavService.update(ctx, text));
    }

    @JavascriptInterface
    public void navEnded() {
        main.post(() -> {
            act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            try { ctx.stopService(new Intent(ctx, NavService.class)); } catch (Exception ignored) { }
        });
    }

    /* ======================= Voice playback (ducks your music) ======================= */
    final AudioManager am;
    AudioFocusRequest focusReq;
    MediaPlayer player;
    String playingId;
    TextToSpeech tts;
    boolean ttsReady = false;
    final List<String[]> ttsPending = new ArrayList<>();

    AudioAttributes navAttrs() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    void requestFocus() {
        if (focusReq != null) return;
        focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(navAttrs())
                .setOnAudioFocusChangeListener(change -> { })
                .build();
        am.requestAudioFocus(focusReq);
    }

    void abandonFocus() {
        if (focusReq != null) {
            am.abandonAudioFocusRequest(focusReq);
            focusReq = null;
        }
    }

    void abandonFocusIfIdle() {
        boolean ttsBusy = tts != null && ttsReady && tts.isSpeaking();
        if (player == null && !ttsBusy) abandonFocus();
    }

    void audioDone(String id) {
        js("window.__nativeAudioDone && window.__nativeAudioDone(" + q(id) + ")");
    }

    void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
        }
        player = null;
        playingId = null;
    }

    @JavascriptInterface
    public void playAudio(String id, String b64) {
        final byte[] data;
        try { data = Base64.decode(b64, Base64.DEFAULT); }
        catch (Exception e) { audioDone(id); return; }
        main.post(() -> {
            String prev = playingId;
            releasePlayer();
            if (prev != null) audioDone(prev);
            File f = new File(ctx.getCacheDir(), "clip_" + id + ".mp3");
            try {
                try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
                player = new MediaPlayer();
                player.setAudioAttributes(navAttrs());
                player.setDataSource(f.getAbsolutePath());
                playingId = id;
                player.setOnCompletionListener(mp -> finishClip(id, f));
                player.setOnErrorListener((mp, what, extra) -> { finishClip(id, f); return true; });
                player.prepare();
                requestFocus();
                player.start();
                js("window.__nativeAudioStart && window.__nativeAudioStart(" + q(id) + ")");
            } catch (Exception e) {
                releasePlayer();
                f.delete();
                main.postDelayed(this::abandonFocusIfIdle, 300);
                audioDone(id);
            }
        });
    }

    void finishClip(String id, File f) {
        if (id.equals(playingId)) releasePlayer();
        f.delete();
        main.postDelayed(this::abandonFocusIfIdle, 400); // short gap so music doesn't bounce between back-to-back lines
        audioDone(id);
    }

    @JavascriptInterface
    public void stopAudio() {
        main.post(() -> {
            String id = playingId;
            releasePlayer();
            if (tts != null) { try { tts.stop(); } catch (Exception ignored) { } }
            abandonFocus();
            if (id != null) audioDone(id);
        });
    }

    @JavascriptInterface
    public void speak(String id, String text, float rate) {
        main.post(() -> {
            if (tts == null) {
                ttsPending.add(new String[]{ id, text, String.valueOf(rate) });
                tts = new TextToSpeech(ctx, status -> {
                    ttsReady = status == TextToSpeech.SUCCESS;
                    if (ttsReady) {
                        tts.setAudioAttributes(navAttrs());
                        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override public void onStart(String uid) { js("window.__nativeAudioStart && window.__nativeAudioStart(" + q(uid) + ")"); }
                            @Override public void onDone(String uid) { main.postDelayed(NativeBridge.this::abandonFocusIfIdle, 400); audioDone(uid); }
                            @Override @SuppressWarnings("deprecation") public void onError(String uid) { main.postDelayed(NativeBridge.this::abandonFocusIfIdle, 400); audioDone(uid); }
                        });
                        for (String[] p : ttsPending) sayTts(p[0], p[1], Float.parseFloat(p[2]));
                    } else {
                        for (String[] p : ttsPending) audioDone(p[0]);
                    }
                    ttsPending.clear();
                });
                return;
            }
            if (!ttsReady) { ttsPending.add(new String[]{ id, text, String.valueOf(rate) }); return; }
            sayTts(id, text, rate);
        });
    }

    void sayTts(String id, String text, float rate) {
        requestFocus();
        tts.setSpeechRate(rate);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), id);
    }

    /* ======================= Music controls (YouTube Music, Spotify, …) ======================= */
    MediaSessionManager msm;
    MediaSessionManager.OnActiveSessionsChangedListener sessionsListener;
    MediaController controller;
    MediaController.Callback controllerCb;
    String artKey;
    String artB64;

    @JavascriptInterface
    public boolean hasMediaAccess() {
        String s = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(ctx.getPackageName());
    }

    @JavascriptInterface
    public void openMediaAccess() {
        main.post(() -> {
            try {
                Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                act.startActivity(i);
            } catch (Exception ignored) { }
        });
    }

    @JavascriptInterface
    public void startMedia() { main.post(this::startMediaMain); }

    void startMediaMain() {
        if (!hasMediaAccess()) return;
        try {
            if (msm == null) msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(ctx, MediaListener.class);
            if (sessionsListener == null) {
                sessionsListener = this::pickController;
                msm.addOnActiveSessionsChangedListener(sessionsListener, cn, main);
            }
            pickController(msm.getActiveSessions(cn));
        } catch (Exception ignored) { }
    }

    void pickController(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                if (ctx.getPackageName().equals(c.getPackageName())) continue;
                PlaybackState ps = c.getPlaybackState();
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) { best = c; break; }
                if (best == null) best = c;
            }
        }
        setController(best);
    }

    void setController(MediaController c) {
        if (controller != null && c != null && controller.getSessionToken().equals(c.getSessionToken())) { pushMedia(); return; }
        if (controller != null && controllerCb != null) {
            try { controller.unregisterCallback(controllerCb); } catch (Exception ignored) { }
        }
        controller = c;
        controllerCb = null;
        if (c != null) {
            controllerCb = new MediaController.Callback() {
                @Override public void onPlaybackStateChanged(PlaybackState state) { pushMedia(); }
                @Override public void onMetadataChanged(MediaMetadata metadata) { pushMedia(); }
                @Override public void onSessionDestroyed() { main.post(() -> { setController(null); startMediaMain(); }); }
            };
            c.registerCallback(controllerCb, main);
        }
        pushMedia();
    }

    static String appLabel(PackageManager pm, String pkg) {
        if ("com.google.android.apps.youtube.music".equals(pkg)) return "YouTube Music";
        if ("com.spotify.music".equals(pkg)) return "Spotify";
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return String.valueOf(pm.getApplicationLabel(ai));
        } catch (Exception e) { return ""; }
    }

    void pushMedia() {
        if (controller == null) { js("window.__nativeMedia && window.__nativeMedia(null)"); return; }
        try {
            MediaMetadata md = controller.getMetadata();
            PlaybackState ps = controller.getPlaybackState();
            String title = "", artist = "";
            Bitmap art = null;
            if (md != null) {
                title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (title == null) title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null) artist = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                if (artist == null) artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            }
            boolean playing = ps != null && (ps.getState() == PlaybackState.STATE_PLAYING || ps.getState() == PlaybackState.STATE_BUFFERING);

            String key = controller.getPackageName() + "|" + title + "|" + artist;
            if (!key.equals(artKey)) {
                artKey = key;
                artB64 = null;
                if (art != null) {
                    Bitmap small = Bitmap.createScaledBitmap(art, 120, 120 * art.getHeight() / Math.max(1, art.getWidth()), true);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    small.compress(Bitmap.CompressFormat.JPEG, 80, bos);
                    artB64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                }
            }

            JSONObject o = new JSONObject();
            o.put("title", title == null ? "" : title);
            o.put("artist", artist == null ? "" : artist);
            o.put("app", appLabel(ctx.getPackageManager(), controller.getPackageName()));
            o.put("playing", playing);
            o.put("art", artB64 == null ? "" : artB64);
            js("window.__nativeMedia && window.__nativeMedia(" + o + ")");
        } catch (Exception ignored) { }
    }

    @JavascriptInterface
    public void mediaControl(String action) {
        main.post(() -> {
            if (controller == null) return;
            MediaController.TransportControls t = controller.getTransportControls();
            switch (action) {
                case "play": t.play(); break;
                case "pause": t.pause(); break;
                case "next": t.skipToNext(); break;
                case "prev": t.skipToPrevious(); break;
                default: break;
            }
        });
    }

    @JavascriptInterface
    public void openMediaApp() {
        main.post(() -> {
            if (controller == null) return;
            Intent i = ctx.getPackageManager().getLaunchIntentForPackage(controller.getPackageName());
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { act.startActivity(i); } catch (Exception ignored) { }
            }
        });
    }

    /* ======================= Voice search ======================= */
    SpeechRecognizer recog;

    @JavascriptInterface
    public void startVoice() {
        voice.stopWake();
        main.post(() -> {
            if (!has(Manifest.permission.RECORD_AUDIO)) { act.askMic(); return; }
            beginVoice();
        });
    }

    @JavascriptInterface
    public void stopVoice() {
        main.post(() -> { if (recog != null) { try { recog.cancel(); } catch (Exception ignored) { } } });
    }

    void onMicPermissionResult(boolean ok) {
        if (ok) main.post(this::beginVoice);
        else voiceEnd("Microphone permission is needed for voice search");
    }

    void voiceEnd(String err) {
        js("window.__nativeVoiceEnd && window.__nativeVoiceEnd(" + (err == null ? "null" : q(err)) + ")");
    }

    void voiceSend(Bundle b, boolean fin) {
        if (b == null) return;
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (r != null && !r.isEmpty()) js("window.__nativeVoice && window.__nativeVoice(" + q(r.get(0)) + "," + fin + ")");
    }

    void beginVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { voiceEnd("Voice search isn't available on this phone"); return; }
        if (recog != null) { try { recog.destroy(); } catch (Exception ignored) { } }
        recog = SpeechRecognizer.createSpeechRecognizer(ctx);
        recog.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onError(int error) {
                boolean quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
                voiceEnd(quiet ? "Didn't catch that. Tap the mic and try again." : null);
            }
            @Override public void onResults(Bundle results) { voiceSend(results, true); voiceEnd(null); }
            @Override public void onPartialResults(Bundle partialResults) { voiceSend(partialResults, false); }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recog.startListening(i);
    }

    /* ======================= Sharing & clipboard ======================= */
    @JavascriptInterface
    public void share(String text) {
        main.post(() -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            try { act.startActivity(Intent.createChooser(i, "Share place")); } catch (Exception ignored) { }
        });
    }

    @JavascriptInterface
    public void copy(String text) {
        main.post(() -> {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Waypoint Voice", text));
        });
    }

    /* ======================= App icon ======================= */
    static final String[] ICONS = { "Default", "Saba", "Midnight", "Sakura" };

    ComponentName iconAlias(String name) {
        return new ComponentName(ctx.getPackageName(), "com.waypointvoice.app.Icon" + name);
    }

    @JavascriptInterface
    public String getAppIcon() {
        PackageManager pm = ctx.getPackageManager();
        for (String n : ICONS) {
            int st = pm.getComponentEnabledSetting(iconAlias(n));
            boolean on = st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || (st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && n.equals("Default"));
            if (on) return n;
        }
        return "Default";
    }

    /** Switches the launcher icon. Turns the new style on first so the app always has an icon. */
    @JavascriptInterface
    public void setAppIcon(String name) {
        boolean known = false;
        for (String n : ICONS) if (n.equals(name)) known = true;
        if (!known) return;
        PackageManager pm = ctx.getPackageManager();
        pm.setComponentEnabledSetting(iconAlias(name), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        for (String n : ICONS) {
            if (n.equals(name)) continue;
            pm.setComponentEnabledSetting(iconAlias(n), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    /** Adds an extra home-screen icon using your own picture (Android asks you to confirm). */
    @JavascriptInterface
    public boolean pinShortcut(String label, String b64) {
        try {
            ShortcutManager sm = ctx.getSystemService(ShortcutManager.class);
            if (sm == null || !sm.isRequestPinShortcutSupported()) return false;
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) return false;
            Intent open = new Intent(ctx, MainActivity.class);
            open.setAction(Intent.ACTION_MAIN);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ShortcutInfo info = new ShortcutInfo.Builder(ctx, "custom-" + System.currentTimeMillis())
                    .setShortLabel(label == null || label.isEmpty() ? "Waypoint Voice" : label)
                    .setIcon(Icon.createWithAdaptiveBitmap(bmp))
                    .setIntent(open)
                    .build();
            return sm.requestPinShortcut(info, null);
        } catch (Exception e) {
            return false;
        }
    }

    /* ======================= Cleanup ======================= */
    void destroy() {
        try { if (lm != null) { lm.removeUpdates(gpsListener); lm.removeUpdates(netListener); } } catch (Exception ignored) { }
        voice.destroy();
        releasePlayer();
        abandonFocus();
        if (tts != null) { try { tts.shutdown(); } catch (Exception ignored) { } }
        if (recog != null) { try { recog.destroy(); } catch (Exception ignored) { } }
        try { if (msm != null && sessionsListener != null) msm.removeOnActiveSessionsChangedListener(sessionsListener); } catch (Exception ignored) { }
        if (controller != null && controllerCb != null) { try { controller.unregisterCallback(controllerCb); } catch (Exception ignored) { } }
        try { ctx.stopService(new Intent(ctx, NavService.class)); } catch (Exception ignored) { }
    }
}

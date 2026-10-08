package com.waypointvoice.app;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Offline speech on the phone (Vosk): the wake phrase ("Hey Saba"), recording a question from a
 * chosen microphone (so an aux cable's "mic" isn't used by mistake), and watching for aux / Bluetooth.
 * Nothing you say leaves the phone from here; the web app decides what to do with the text.
 */
public class VoiceEngine {
    static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";
    static final int RATE = 16000;

    final NativeBridge bridge;
    final Context ctx;
    final AudioManager am;
    final Handler main = new Handler(Looper.getMainLooper());

    volatile Model model;
    volatile boolean downloading = false;

    // wake phrase listening
    volatile boolean wakeWanted = false;     // the web app asked for it
    volatile boolean wakePaused = false;     // app went to the background
    volatile boolean preferPhoneMic = false;
    Thread wakeThread;
    volatile boolean wakeRun = false;

    // one-shot recording
    Thread clipThread;
    volatile boolean clipRun = false;

    VoiceEngine(NativeBridge b, Context c) {
        bridge = b;
        ctx = c;
        am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        try { LibVosk.setLogLevel(LogLevel.WARNINGS); } catch (Throwable ignored) { }
        try {
            am.registerAudioDeviceCallback(new AudioDeviceCallback() {
                @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { pushRoute(); }
                @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { pushRoute(); }
            }, main);
        } catch (Throwable ignored) { }
    }

    /* ---------------- offline speech model ---------------- */
    File modelRoot() { return new File(ctx.getFilesDir(), "vosk"); }

    File findModelDir(File dir) {
        if (dir == null || !dir.isDirectory()) return null;
        if (new File(dir, "am").isDirectory() || new File(dir, "conf").isDirectory()) return dir;
        File[] kids = dir.listFiles();
        if (kids == null) return null;
        for (File k : kids) {
            File f = findModelDir(k);
            if (f != null) return f;
        }
        return null;
    }

    boolean modelOnDisk() {
        File done = new File(modelRoot(), ".complete");
        return done.exists() && findModelDir(modelRoot()) != null;
    }

    String status() {
        if (model != null) return "ready";
        if (downloading) return "downloading";
        return modelOnDisk() ? "downloaded" : "none";
    }

    /** Loads the model (downloading it once, ~40 MB) on a background thread. */
    void ensureModel() {
        if (model != null) { bridge.js("window.__nativeModel && window.__nativeModel('ready')"); return; }
        if (downloading) return;
        downloading = true;
        new Thread(() -> {
            try {
                if (!modelOnDisk()) download();
                File dir = findModelDir(modelRoot());
                if (dir == null) throw new Exception("Speech model files are missing");
                bridge.js("window.__nativeModel && window.__nativeModel('loading')");
                model = new Model(dir.getAbsolutePath());
                downloading = false;
                bridge.js("window.__nativeModel && window.__nativeModel('ready')");
                if (wakeWanted && !wakePaused) main.post(this::startWakeThread);
            } catch (Throwable e) {
                downloading = false;
                bridge.js("window.__nativeModel && window.__nativeModel(" + NativeBridge.q("error:" + e.getMessage()) + ")");
            }
        }, "vosk-model").start();
    }

    void download() throws Exception {
        File root = modelRoot();
        deleteAll(root);
        root.mkdirs();
        HttpURLConnection c = (HttpURLConnection) new URL(MODEL_URL).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Download failed (" + code + ")");
        long total = c.getContentLengthLong();
        long read = 0;
        int lastPct = -1;
        try (InputStream raw = new BufferedInputStream(c.getInputStream());
             ZipInputStream zin = new ZipInputStream(new CountingStream(raw))) {
            ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = zin.getNextEntry()) != null) {
                File out = new File(root, e.getName());
                if (!out.getCanonicalPath().startsWith(root.getCanonicalPath())) continue; // stay inside our folder
                if (e.isDirectory()) { out.mkdirs(); continue; }
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                try (OutputStream os = new FileOutputStream(out)) {
                    int n;
                    while ((n = zin.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        long got = CountingStream.count;
                        if (total > 0) {
                            int pct = (int) Math.min(99, got * 100 / total);
                            if (pct != lastPct) { lastPct = pct; bridge.js("window.__nativeModel && window.__nativeModel('" + pct + "%')"); }
                        }
                    }
                }
            }
        }
        new File(root, ".complete").createNewFile();
    }

    /** Counts compressed bytes so the progress bar matches the download size. */
    static class CountingStream extends java.io.FilterInputStream {
        static volatile long count = 0;
        CountingStream(InputStream in) { super(in); count = 0; }
        @Override public int read() throws java.io.IOException { int b = super.read(); if (b >= 0) count++; return b; }
        @Override public int read(byte[] b, int off, int len) throws java.io.IOException { int n = super.read(b, off, len); if (n > 0) count += n; return n; }
    }

    static void deleteAll(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteAll(k);
        f.delete();
    }

    void deleteModel() {
        stopWakeThread();
        Model m = model;
        model = null;
        if (m != null) { try { m.close(); } catch (Throwable ignored) { } }
        deleteAll(modelRoot());
    }

    /* ---------------- microphones ---------------- */
    AudioDeviceInfo builtinMic() {
        try {
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) return d;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    AudioRecord openMic(boolean phoneMic) {
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int size = Math.max(min, RATE / 5 * 2) * 2;
        AudioRecord rec;
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
        } catch (SecurityException e) {
            return null;
        }
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) { rec.release(); return null; }
        if (phoneMic) {
            AudioDeviceInfo mic = builtinMic();
            if (mic != null) { try { rec.setPreferredDevice(mic); } catch (Throwable ignored) { } }
        }
        return rec;
    }

    static boolean isWiredOut(int t) {
        return t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || t == AudioDeviceInfo.TYPE_AUX_LINE || t == AudioDeviceInfo.TYPE_USB_HEADSET
                || t == AudioDeviceInfo.TYPE_LINE_ANALOG;
    }

    String routeJson() {
        JSONObject o = new JSONObject();
        try {
            boolean aux = false, auxMic = false, bt = false;
            String btName = "", auxName = "";
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (isWiredOut(t)) { aux = true; auxName = String.valueOf(d.getProductName()); }
                if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) { bt = true; btName = String.valueOf(d.getProductName()); }
            }
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_USB_HEADSET) auxMic = true;
            }
            o.put("aux", aux);
            o.put("auxMic", auxMic);
            o.put("auxName", auxName);
            o.put("bt", bt);
            o.put("btName", btName);
        } catch (Throwable ignored) { }
        return o.toString();
    }

    void pushRoute() {
        bridge.js("window.__nativeAudioRoute && window.__nativeAudioRoute(" + routeJson() + ")");
    }

    /* ---------------- wake phrase ---------------- */
    void startWake(boolean phoneMic) {
        wakeWanted = true;
        preferPhoneMic = phoneMic;
        if (model == null) { ensureModel(); return; }
        if (!wakePaused) startWakeThread();
    }

    void stopWake() {
        wakeWanted = false;
        stopWakeThread();
    }

    void onPause(boolean keepListening) {
        if (keepListening) return;
        wakePaused = true;
        stopWakeThread();
    }

    void onResume() {
        wakePaused = false;
        if (wakeWanted && model != null && !clipRun) startWakeThread();
    }

    synchronized void startWakeThread() {
        if (wakeRun || model == null || clipRun) return;
        wakeRun = true;
        final boolean phone = preferPhoneMic;
        wakeThread = new Thread(() -> {
            AudioRecord rec = null;
            Recognizer r = null;
            try {
                rec = openMic(phone);
                if (rec == null) throw new Exception("mic");
                r = new Recognizer(model, RATE);
                short[] buf = new short[RATE / 10]; // 100 ms
                rec.startRecording();
                String lastPartial = "";
                long lastSent = 0;
                while (wakeRun) {
                    int n = rec.read(buf, 0, buf.length);
                    if (n <= 0) continue;
                    if (r.acceptWaveForm(buf, n)) {
                        String t = textOf(r.getResult(), "text");
                        lastPartial = "";
                        if (!t.isEmpty()) bridge.js("window.__nativeWake && window.__nativeWake(" + NativeBridge.q(t) + ",true)");
                    } else {
                        long now = System.currentTimeMillis();
                        if (now - lastSent > 250) {
                            String p = textOf(r.getPartialResult(), "partial");
                            if (!p.isEmpty() && !p.equals(lastPartial)) {
                                lastPartial = p;
                                lastSent = now;
                                bridge.js("window.__nativeWake && window.__nativeWake(" + NativeBridge.q(p) + ",false)");
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                bridge.js("window.__nativeWakeError && window.__nativeWakeError(" + NativeBridge.q(String.valueOf(e.getMessage())) + ")");
            } finally {
                if (rec != null) { try { rec.stop(); } catch (Throwable ignored) { } rec.release(); }
                if (r != null) { try { r.close(); } catch (Throwable ignored) { } }
                wakeRun = false;
            }
        }, "wake");
        wakeThread.start();
    }

    void stopWakeThread() {
        wakeRun = false;
        Thread t = wakeThread;
        if (t != null && t != Thread.currentThread()) {
            try { t.join(800); } catch (InterruptedException ignored) { }
        }
        wakeThread = null;
    }

    static String textOf(String json, String key) {
        try { return new JSONObject(json).optString(key, "").trim(); } catch (Throwable e) { return ""; }
    }

    /* ---------------- record one question ---------------- */
    /**
     * Records until you stop talking. Sends live words to __nativeVoice (when the offline model is
     * loaded) and finally __nativeClip(wavBase64, transcript, error).
     */
    void recordClip(boolean phoneMic, boolean transcribe) {
        cancelClip();
        stopWakeThread(); // only one recording at a time
        clipRun = true;
        clipThread = new Thread(() -> {
            AudioRecord rec = null;
            Recognizer r = null;
            String err = null, transcript = "";
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            boolean spoke = false;
            try {
                rec = openMic(phoneMic);
                if (rec == null) throw new Exception("Couldn't open the microphone");
                if (transcribe && model != null) r = new Recognizer(model, RATE);
                short[] buf = new short[RATE / 50]; // 20 ms frames
                byte[] bytes = new byte[buf.length * 2];
                rec.startRecording();
                long t0 = System.currentTimeMillis(), lastVoice = 0, lastPartialT = 0;
                double floor = -1;
                int voiced = 0;
                String lastPartial = "";
                while (clipRun) {
                    int n = rec.read(buf, 0, buf.length);
                    if (n <= 0) continue;
                    double sum = 0;
                    for (int i = 0; i < n; i++) { double v = buf[i] / 32768.0; sum += v * v; }
                    double rms = Math.sqrt(sum / n);
                    long now = System.currentTimeMillis();
                    // background noise = the quietest level so far, drifting slowly so road noise is learned
                    if (floor < 0) floor = Math.min(rms, 0.02);
                    else if (rms < floor) floor = rms;
                    else floor = floor * 0.995 + Math.min(rms, floor * 3) * 0.005;
                    boolean voice = rms > Math.max(floor * 2.5, 0.006);
                    if (voice) { voiced++; if (voiced >= 3) spoke = true; lastVoice = now; } else voiced = Math.max(0, voiced - 1);
                    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(buf, 0, n);
                    pcm.write(bytes, 0, n * 2);
                    if (r != null) {
                        if (r.acceptWaveForm(buf, n)) {
                            String t = textOf(r.getResult(), "text");
                            if (!t.isEmpty()) transcript = (transcript + " " + t).trim();
                        } else if (now - lastPartialT > 200) {
                            lastPartialT = now;
                            String p = textOf(r.getPartialResult(), "partial");
                            String shown = (transcript + " " + p).trim();
                            if (!shown.isEmpty() && !shown.equals(lastPartial)) {
                                lastPartial = shown;
                                bridge.js("window.__nativeVoice && window.__nativeVoice(" + NativeBridge.q(shown) + ",false)");
                            }
                        }
                    }
                    if (spoke && now - lastVoice > 1100) break;
                    if (!spoke && now - t0 > 6000) break;
                    if (now - t0 > 12000) break;
                }
                if (r != null) {
                    String t = textOf(r.getFinalResult(), "text");
                    if (!t.isEmpty()) transcript = (transcript + " " + t).trim();
                }
            } catch (Throwable e) {
                err = e.getMessage() == null ? "Recording failed" : e.getMessage();
            } finally {
                if (rec != null) { try { rec.stop(); } catch (Throwable ignored) { } rec.release(); }
                if (r != null) { try { r.close(); } catch (Throwable ignored) { } }
            }
            boolean cancelled = !clipRun;
            clipRun = false;
            if (cancelled) return;
            String wav = spoke ? Base64.encodeToString(wav(pcm.toByteArray()), Base64.NO_WRAP) : "";
            bridge.js("window.__nativeClip && window.__nativeClip(" + NativeBridge.q(wav) + "," + NativeBridge.q(transcript) + ","
                    + (err == null ? "null" : NativeBridge.q(err)) + ")");
        }, "clip");
        clipThread.start();
    }

    void cancelClip() {
        clipRun = false;
        Thread t = clipThread;
        if (t != null && t != Thread.currentThread()) {
            try { t.join(800); } catch (InterruptedException ignored) { }
        }
        clipThread = null;
    }

    static byte[] wav(byte[] pcm) {
        ByteBuffer b = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + pcm.length).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes()).putInt(pcm.length).put(pcm);
        return b.array();
    }

    void destroy() {
        wakeWanted = false;
        stopWakeThread();
        cancelClip();
    }
}

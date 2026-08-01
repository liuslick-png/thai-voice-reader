package com.novapixel.thaivoicereader;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private EditText input;
    private Spinner voiceSpinner;
    private SeekBar speedBar, pitchBar, volumeBar;
    private TextView speedValue, pitchValue, volumeValue, status;
    private final List<Voice> thaiVoices = new ArrayList<>();
    private final List<String> chunks = new ArrayList<>();
    private int chunkIndex = 0;
    private boolean saving = false;
    private File tempAudio;
    private String pendingFileName;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        tts = new TextToSpeech(this, this);
    }

    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setPadding(0, 8, 0, 8);
        return v;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(16);
        b.setBackgroundColor(color);
        b.setAllCaps(false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 58, 1);
        p.setMargins(5, 5, 5, 5); b.setLayoutParams(p);
        return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(246,247,251));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 28, 24, 30);
        scroll.addView(root);

        TextView title = text("เสียงไทย Offline", 28, Color.rgb(28,31,50));
        title.setGravity(Gravity.CENTER); title.setTypeface(null, 1);
        root.addView(title);
        TextView sub = text("อ่านข้อความภาษาไทยโดยไม่เสียค่าบริการ", 15, Color.DKGRAY);
        sub.setGravity(Gravity.CENTER); root.addView(sub);

        root.addView(text("ข้อความที่ต้องการอ่าน", 17, Color.rgb(28,31,50)));
        input = new EditText(this);
        input.setHint("พิมพ์หรือวางข้อความภาษาไทยที่นี่...");
        input.setGravity(Gravity.TOP);
        input.setMinLines(7);
        input.setTextSize(18);
        input.setBackgroundColor(Color.WHITE);
        input.setPadding(18,18,18,18);
        root.addView(input, new LinearLayout.LayoutParams(-1, -2));

        root.addView(text("เลือกเสียงภาษาไทยที่ติดตั้งในเครื่อง", 17, Color.rgb(28,31,50)));
        voiceSpinner = new Spinner(this);
        voiceSpinner.setBackgroundColor(Color.WHITE);
        root.addView(voiceSpinner, new LinearLayout.LayoutParams(-1, 58));

        speedValue = text("", 15, Color.DKGRAY);
        root.addView(speedValue); speedBar = slider(root, 25, 200, 100);
        pitchValue = text("", 15, Color.DKGRAY);
        root.addView(pitchValue); pitchBar = slider(root, 50, 150, 100);
        volumeValue = text("", 15, Color.DKGRAY);
        root.addView(volumeValue); volumeBar = slider(root, 0, 100, 100);
        updateLabels();

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) { updateLabels(); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        };
        speedBar.setOnSeekBarChangeListener(listener);
        pitchBar.setOnSeekBarChangeListener(listener);
        volumeBar.setOnSeekBarChangeListener(listener);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button speak = button("▶ อ่าน", Color.rgb(91,75,219));
        Button stop = button("■ หยุด", Color.rgb(100,116,139));
        Button save = button("⬇ บันทึก WAV", Color.rgb(16,148,112));
        row.addView(speak); row.addView(stop); row.addView(save); root.addView(row);

        status = text("กำลังเตรียมระบบเสียง...", 15, Color.DKGRAY);
        status.setGravity(Gravity.CENTER); root.addView(status);

        speak.setOnClickListener(v -> startSpeaking());
        stop.setOnClickListener(v -> {
            saving = false; chunks.clear();
            if (tts != null) tts.stop();
            status.setText("หยุดแล้ว");
        });
        save.setOnClickListener(v -> startSaving());
        setContentView(scroll);
    }

    private SeekBar slider(LinearLayout root, int min, int max, int value) {
        SeekBar b = new SeekBar(this);
        b.setMin(min); b.setMax(max); b.setProgress(value);
        root.addView(b, new LinearLayout.LayoutParams(-1, 48));
        return b;
    }

    private void updateLabels() {
        if (speedValue == null) return;
        speedValue.setText("ความเร็ว: " + speedBar.getProgress() + "%");
        pitchValue.setText("ระดับเสียงสูง–ต่ำ: " + pitchBar.getProgress() + "%");
        volumeValue.setText("ความดัง: " + volumeBar.getProgress() + "%");
    }

    @Override public void onInit(int result) {
        if (result != TextToSpeech.SUCCESS) {
            status.setText("เปิดระบบเสียงไม่ได้ กรุณาติดตั้ง Speech Services by Google");
            return;
        }
        int available = tts.setLanguage(new Locale("th", "TH"));
        if (available < 0) {
            status.setText("ยังไม่มีข้อมูลเสียงภาษาไทยในเครื่อง");
        } else {
            loadVoices();
            status.setText("พร้อมใช้งานแบบออฟไลน์");
        }
        tts.setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String id) { runOnUiThread(() -> status.setText(saving ? "กำลังสร้างไฟล์เสียง..." : "กำลังอ่าน...")); }
            public void onError(String id) { runOnUiThread(() -> status.setText("เกิดข้อผิดพลาดในการสร้างเสียง")); }
            public void onDone(String id) {
                if (saving) finishSave();
                else {
                    chunkIndex++;
                    if (chunkIndex < chunks.size()) speakNext();
                    else runOnUiThread(() -> status.setText("อ่านจบแล้ว"));
                }
            }
        });
    }

    private void loadVoices() {
        thaiVoices.clear();
        List<String> labels = new ArrayList<>();
        Set<Voice> all = tts.getVoices();
        if (all != null) {
            for (Voice v : all) {
                if ("th".equals(v.getLocale().getLanguage())) {
                    thaiVoices.add(v);
                    String mode = v.isNetworkConnectionRequired() ? "ออนไลน์" : "ออฟไลน์";
                    labels.add("เสียงไทย " + (labels.size()+1) + " — " + mode);
                }
            }
        }
        if (labels.isEmpty()) labels.add("เสียงภาษาไทยเริ่มต้น");
        voiceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
    }

    private void applySettings() {
        tts.setSpeechRate(speedBar.getProgress() / 100f);
        tts.setPitch(pitchBar.getProgress() / 100f);
        int pos = voiceSpinner.getSelectedItemPosition();
        if (pos >= 0 && pos < thaiVoices.size()) tts.setVoice(thaiVoices.get(pos));
    }

    private boolean prepareText() {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "กรุณาใส่ข้อความก่อน", Toast.LENGTH_SHORT).show();
            return false;
        }
        chunks.clear();
        int max = TextToSpeech.getMaxSpeechInputLength() - 100;
        for (int start = 0; start < value.length();) {
            int end = Math.min(start + max, value.length());
            if (end < value.length()) {
                int breakAt = value.lastIndexOf(' ', end);
                if (breakAt > start + max / 2) end = breakAt;
            }
            chunks.add(value.substring(start, end));
            start = end;
            while (start < value.length() && Character.isWhitespace(value.charAt(start))) start++;
        }
        chunkIndex = 0;
        return true;
    }

    private Bundle params() {
        Bundle b = new Bundle();
        b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumeBar.getProgress()/100f);
        return b;
    }

    private void startSpeaking() {
        if (tts == null || !prepareText()) return;
        saving = false; applySettings(); speakNext();
    }

    private void speakNext() {
        tts.speak(chunks.get(chunkIndex), TextToSpeech.QUEUE_FLUSH, params(), "speak_" + chunkIndex);
    }

    private void startSaving() {
        if (tts == null || !prepareText()) return;
        if (chunks.size() > 1) {
            Toast.makeText(this, "ข้อความยาวเกินไปสำหรับไฟล์เดียว กรุณาแบ่งข้อความแล้วบันทึกทีละส่วน", Toast.LENGTH_LONG).show();
            return;
        }
        saving = true; applySettings();
        pendingFileName = "ThaiVoice_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".wav";
        tempAudio = new File(getCacheDir(), pendingFileName);
        int result = tts.synthesizeToFile(chunks.get(0), params(), tempAudio, "save_audio");
        if (result != TextToSpeech.SUCCESS) {
            saving = false; status.setText("เริ่มสร้างไฟล์ไม่ได้");
        }
    }

    private void finishSave() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, pendingFileName);
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/ThaiVoiceReader");
            Uri uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Cannot create MediaStore item");
            try (FileInputStream in = new FileInputStream(tempAudio);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            tempAudio.delete(); saving = false;
            runOnUiThread(() -> status.setText("บันทึกแล้ว: Music/ThaiVoiceReader/" + pendingFileName));
        } catch (Exception e) {
            saving = false;
            runOnUiThread(() -> status.setText("บันทึกไฟล์ไม่สำเร็จ: " + e.getMessage()));
        }
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}

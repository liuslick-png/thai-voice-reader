package com.novapixel.thaivoicereader;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import android.view.ViewGroup;
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
    private Switch dhammaMode;
    private SeekBar speedBar, pitchBar, volumeBar;
    private TextView speedValue, pitchValue, volumeValue, status;
    private Voice phoneDefaultVoice;
    private final List<Voice> thaiVoices = new ArrayList<>();
    private final List<String> chunks = new ArrayList<>();
    private static final int READING_CHUNK_SIZE = 1200;
    private int chunkIndex = 0;
    private boolean readingActive = false;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable raisedBackground(int color) {
        int lighter = Color.rgb(
            Math.min(255, Color.red(color) + 34),
            Math.min(255, Color.green(color) + 34),
            Math.min(255, Color.blue(color) + 34));
        GradientDrawable g = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{lighter, color});
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), Color.argb(110, 255, 255, 255));
        return g;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable g = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.rgb(32, 42, 70), Color.rgb(18, 25, 47)});
        g.setCornerRadius(dp(22));
        g.setStroke(dp(1), Color.rgb(79, 92, 130));
        return g;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(16);
        b.setBackground(raisedBackground(color));
        b.setElevation(dp(10));
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(60));
        b.setPadding(dp(4), 0, dp(4), 0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(64), 1);
        p.setMargins(dp(3), dp(6), dp(3), dp(6)); b.setLayoutParams(p);
        return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(8,13,28));
        getWindow().setStatusBarColor(Color.rgb(8,13,28));
        getWindow().setNavigationBarColor(Color.rgb(8,13,28));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(32));
        scroll.addView(root);

        TextView title = text("✦  เสียงไทย Offline  ✦", 28, Color.rgb(246,205,104));
        title.setGravity(Gravity.CENTER); title.setTypeface(null, 1);
        title.setShadowLayer(12f, 0f, 5f, Color.rgb(184,121,25));
        root.addView(title);
        TextView sub = text("อ่านข้อความภาษาไทย • ส่วนตัว • ไม่เสียค่าบริการ", 15, Color.rgb(202,210,230));
        sub.setGravity(Gravity.CENTER); root.addView(sub);

        root.addView(text("ข้อความที่ต้องการอ่าน", 17, Color.rgb(242,214,145)));
        input = new EditText(this);
        input.setHint("พิมพ์หรือวางข้อความภาษาไทยที่นี่...");
        input.setGravity(Gravity.TOP);
        input.setMinLines(7);
        input.setTextSize(18);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(143,155,184));
        input.setBackground(panelBackground());
        input.setElevation(dp(10));
        input.setPadding(dp(18),dp(18),dp(18),dp(18));
        root.addView(input, new LinearLayout.LayoutParams(-1, -2));

        root.addView(text("เลือกเสียงภาษาไทยที่ติดตั้งในเครื่อง", 17, Color.rgb(242,214,145)));
        voiceSpinner = new Spinner(this);
        voiceSpinner.setBackground(raisedBackground(Color.rgb(38,49,79)));
        voiceSpinner.setElevation(dp(8));
        voiceSpinner.setPadding(dp(14),0,dp(14),0);
        root.addView(voiceSpinner, new LinearLayout.LayoutParams(-1, dp(58)));

        dhammaMode = new Switch(this);
        dhammaMode.setText("โหมดพระบรรยายธรรมะ — ช้า ทุ้ม และสงบ");
        dhammaMode.setTextSize(17);
        dhammaMode.setTextColor(Color.rgb(242,214,145));
        dhammaMode.setBackground(panelBackground());
        dhammaMode.setPadding(dp(14), dp(12), dp(14), dp(12));
        dhammaMode.setElevation(dp(7));
        dhammaMode.setPadding(0, dp(12), 0, dp(12));
        root.addView(dhammaMode, new LinearLayout.LayoutParams(-1, dp(64)));

        speedValue = text("", 15, Color.rgb(202,210,230));
        root.addView(speedValue); speedBar = slider(root, 25, 200, 100);
        pitchValue = text("", 15, Color.rgb(202,210,230));
        root.addView(pitchValue); pitchBar = slider(root, 50, 150, 100);
        volumeValue = text("", 15, Color.rgb(202,210,230));
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
        dhammaMode.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) {
                speedBar.setProgress(82);
                pitchBar.setProgress(100);
                status.setText("เปิดโหมดธรรมะ: ใช้เสียงผู้ชายของโทรศัพท์และพูดช้า");
            } else {
                speedBar.setProgress(100);
                pitchBar.setProgress(100);
                status.setText("ปิดโหมดธรรมะ");
            }
            updateLabels();
        });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button speak = button("▶  อ่าน", Color.rgb(91,75,219));
        Button stop = button("■  หยุด", Color.rgb(80,95,124));
        Button save = button("⬇  บันทึก", Color.rgb(16,148,112));
        row.addView(speak); row.addView(stop); row.addView(save); root.addView(row);

        status = text("กำลังเตรียมระบบเสียง...", 15, Color.rgb(202,210,230));
        status.setGravity(Gravity.CENTER); status.setTypeface(null, 1); root.addView(status);

        speak.setOnClickListener(v -> startSpeaking());
        stop.setOnClickListener(v -> {
            readingActive = false;
            saving = false;
            chunks.clear();
            chunkIndex = 0;
            if (tts != null) tts.stop();
            status.setText("หยุดแล้ว");
        });
        save.setOnClickListener(v -> startSaving());
        setContentView(scroll);
    }

    private SeekBar slider(LinearLayout root, int min, int max, int value) {
        SeekBar b = new SeekBar(this);
        b.setMin(min); b.setMax(max); b.setProgress(value);
        root.addView(b, new LinearLayout.LayoutParams(-1, dp(48)));
        return b;
    }

    private void updateLabels() {
        if (speedValue == null) return;
        speedValue.setText("ความเร็ว: " + speedBar.getProgress() + "%" +
            (dhammaMode != null && dhammaMode.isChecked() ? "  •  โหมดธรรมะ" : ""));
        pitchValue.setText("ระดับเสียงสูง–ต่ำ: " + pitchBar.getProgress() + "%");
        volumeValue.setText("ความดัง: " + volumeBar.getProgress() + "%");
    }

    @Override public void onInit(int result) {
        if (result != TextToSpeech.SUCCESS) {
            status.setText("เปิดระบบเสียงไม่ได้ กรุณาติดตั้ง Speech Services by Google");
            return;
        }
        phoneDefaultVoice = tts.getVoice();
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
            public void onStart(String id) {
                runOnUiThread(() -> {
                    if (saving) {
                        status.setText("กำลังสร้างไฟล์เสียง...");
                    } else if (readingActive) {
                        status.setText("กำลังอ่านช่วงที่ " + (chunkIndex + 1) + " จาก " + chunks.size());
                    }
                });
            }
            public void onError(String id) {
                readingActive = false;
                saving = false;
                runOnUiThread(() -> status.setText("เกิดข้อผิดพลาดในการสร้างเสียง"));
            }
            public void onDone(String id) {
                if (saving) {
                    finishSave();
                } else if (readingActive) {
                    chunkIndex++;
                    if (chunkIndex < chunks.size()) {
                        speakNext();
                    } else {
                        readingActive = false;
                        runOnUiThread(() -> status.setText("อ่านครบทุกช่วงแล้ว"));
                    }
                }
            }
        });
    }

    private ArrayAdapter<String> voiceAdapter(List<String> labels) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(Color.WHITE);
                v.setTextSize(17);
                v.setPadding(dp(14), 0, dp(14), 0);
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(Color.rgb(24,31,52));
                v.setTextSize(17);
                v.setBackgroundColor(Color.WHITE);
                v.setPadding(dp(14), dp(14), dp(14), dp(14));
                return v;
            }
        };
    }

    private void loadVoices() {
        thaiVoices.clear();
        List<String> labels = new ArrayList<>();

        if (phoneDefaultVoice != null &&
            "th".equals(phoneDefaultVoice.getLocale().getLanguage())) {
            thaiVoices.add(phoneDefaultVoice);
            labels.add("เสียงเริ่มต้นของโทรศัพท์ — แนะนำ");
        }

        Set<Voice> all = tts.getVoices();
        if (all != null) {
            for (Voice v : all) {
                boolean duplicate = phoneDefaultVoice != null &&
                    v.getName().equals(phoneDefaultVoice.getName());
                if ("th".equals(v.getLocale().getLanguage()) && !duplicate) {
                    thaiVoices.add(v);
                    String mode = v.isNetworkConnectionRequired() ? "ออนไลน์" : "ออฟไลน์";
                    labels.add("เสียงไทย " + (labels.size()+1) + " — " + mode);
                }
            }
        }

        if (labels.isEmpty()) labels.add("เสียงภาษาไทยเริ่มต้น");
        voiceSpinner.setAdapter(voiceAdapter(labels));
        voiceSpinner.setSelection(0);
        if (!thaiVoices.isEmpty()) tts.setVoice(thaiVoices.get(0));
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
        splitLongText(value);
        chunkIndex = 0;
        return !chunks.isEmpty();
    }

    private void splitLongText(String value) {
        int engineLimit = Math.max(500, TextToSpeech.getMaxSpeechInputLength() - 100);
        int chunkLimit = Math.min(READING_CHUNK_SIZE, engineLimit);
        int start = 0;

        while (start < value.length()) {
            while (start < value.length() && Character.isWhitespace(value.charAt(start))) start++;
            if (start >= value.length()) break;

            int hardEnd = Math.min(start + chunkLimit, value.length());
            int end = hardEnd;

            if (hardEnd < value.length()) {
                int preferredStart = start + (chunkLimit * 55 / 100);
                int naturalBreak = findNaturalBreak(value, preferredStart, hardEnd);
                if (naturalBreak > start) {
                    end = naturalBreak;
                } else {
                    int spaceBreak = findWhitespaceBreak(value, preferredStart, hardEnd);
                    if (spaceBreak > start) end = spaceBreak;
                }
            }

            String part = value.substring(start, end).trim();
            if (!part.isEmpty()) chunks.add(part);
            start = end;
        }
    }

    private int findNaturalBreak(String value, int from, int to) {
        for (int i = to - 1; i >= from; i--) {
            char ch = value.charAt(i);
            if (ch == '\n' || ch == '.' || ch == '!' || ch == '?' ||
                ch == 'ฯ' || ch == '。' || ch == '！' || ch == '？') {
                return i + 1;
            }
        }
        return -1;
    }

    private int findWhitespaceBreak(String value, int from, int to) {
        for (int i = to - 1; i >= from; i--) {
            if (Character.isWhitespace(value.charAt(i))) return i;
        }
        return -1;
    }

    private Bundle params() {
        Bundle b = new Bundle();
        b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumeBar.getProgress()/100f);
        return b;
    }

    private void startSpeaking() {
        if (tts == null || !prepareText()) return;
        saving = false;
        readingActive = true;
        applySettings();
        status.setText(chunks.size() == 1
            ? "กำลังเริ่มอ่าน..."
            : "แบ่งข้อความอัตโนมัติเป็น " + chunks.size() + " ช่วง");
        speakNext();
    }

    private void speakNext() {
        if (!readingActive || chunkIndex < 0 || chunkIndex >= chunks.size()) return;
        int result = tts.speak(
            chunks.get(chunkIndex),
            TextToSpeech.QUEUE_FLUSH,
            params(),
            "speak_" + chunkIndex + "_" + System.currentTimeMillis());
        if (result != TextToSpeech.SUCCESS) {
            readingActive = false;
            runOnUiThread(() -> status.setText("เริ่มอ่านช่วงถัดไปไม่สำเร็จ"));
        }
    }

    private void startSaving() {
        if (tts == null || !prepareText()) return;
        readingActive = false;
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

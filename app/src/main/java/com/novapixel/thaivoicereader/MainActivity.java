package com.novapixel.thaivoicereader;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.text.method.ScrollingMovementMethod;
import android.widget.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean hasAudioFocus = false;
    private EditText input;
    private Spinner voiceSpinner;
    private Switch dhammaMode;
    private SeekBar speedBar, pitchBar, volumeBar;
    private TextView speedValue, pitchValue, volumeValue, charCounter, timeValue, status;
    private ProgressBar readingProgress;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long readingStartedAt = 0L;
    private Voice phoneDefaultVoice;
    private final List<Voice> thaiVoices = new ArrayList<>();
    private final List<String> chunks = new ArrayList<>();
    private static final int MAX_INPUT_CHARS = 950;
    private int chunkIndex = 0;
    private long readingSession = 0L;
    private int nextChunkToQueue = 0;
    private boolean readingActive = false;
    private boolean saving = false;
    private File tempAudio;
    private String pendingFileName;
    private String undoText = null;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        createAudioFocusRequest();
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
        b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(13);
        b.setBackground(raisedBackground(color));
        b.setElevation(dp(10));
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(40));
        b.setMinimumHeight(dp(40));
        b.setSingleLine(true);
        b.setPadding(dp(3), 0, dp(3), 0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), 1);
        p.setMargins(dp(3), dp(3), dp(3), dp(3)); b.setLayoutParams(p);
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
        input.setSingleLine(false);
        input.setMinLines(4);
        input.setMaxLines(4);
        input.setVerticalScrollBarEnabled(true);
        input.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        input.setMovementMethod(new ScrollingMovementMethod());
        input.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        input.setTextSize(18);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(143,155,184));
        input.setBackground(panelBackground());
        input.setElevation(dp(10));
        input.setPadding(dp(18),dp(14),dp(18),dp(14));
        input.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getAction() == MotionEvent.ACTION_UP ||
                       event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        root.addView(input, new LinearLayout.LayoutParams(-1, dp(156)));

        LinearLayout timeline = new LinearLayout(this);
        timeline.setOrientation(LinearLayout.HORIZONTAL);
        timeline.setGravity(Gravity.CENTER_VERTICAL);
        timeline.setPadding(0, 0, 0, 0);

        Button timelinePlay = new Button(this);
        timelinePlay.setText("▶");
        timelinePlay.setTextSize(13);
        timelinePlay.setTextColor(Color.WHITE);
        timelinePlay.setBackground(raisedBackground(Color.rgb(91,75,219)));
        timelinePlay.setPadding(0, 0, 0, 0);
        timelinePlay.setMinHeight(0);
        timelinePlay.setMinimumHeight(0);
        timeline.addView(timelinePlay,
            new LinearLayout.LayoutParams(dp(32), dp(24)));
        readingProgress = new ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal);
        readingProgress.setMax(1000);
        readingProgress.setProgress(0);
        timeValue = text("00:00", 11, Color.rgb(202,210,230));
        timeValue.setGravity(Gravity.CENTER);
        timeValue.setPadding(0, 0, 0, 0);
        timeline.addView(timeValue, new LinearLayout.LayoutParams(dp(48), dp(22)));
        LinearLayout.LayoutParams progressParams =
            new LinearLayout.LayoutParams(0, dp(6), 1);
        progressParams.setMargins(dp(5), 0, dp(5), 0);
        timeline.addView(readingProgress, progressParams);

        Button timelineStop = new Button(this);
        timelineStop.setText("■");
        timelineStop.setTextSize(11);
        timelineStop.setTextColor(Color.WHITE);
        timelineStop.setBackground(raisedBackground(Color.rgb(80,95,124)));
        timelineStop.setPadding(0, 0, 0, 0);
        timelineStop.setMinHeight(0);
        timelineStop.setMinimumHeight(0);
        timeline.addView(timelineStop,
            new LinearLayout.LayoutParams(dp(32), dp(24)));

        LinearLayout.LayoutParams timelineParams =
            new LinearLayout.LayoutParams(-1, dp(26));
        timelineParams.setMargins(0, dp(3), 0, 0);
        root.addView(timeline, timelineParams);

        charCounter = text("0 / 950", 11, Color.rgb(143,155,184));
        charCounter.setGravity(Gravity.END);
        charCounter.setPadding(0, 0, dp(6), 0);
        root.addView(charCounter, new LinearLayout.LayoutParams(-1, dp(24)));
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int length = s.length();
                charCounter.setText(length + " / " + MAX_INPUT_CHARS);
                charCounter.setTextColor(length > MAX_INPUT_CHARS
                    ? Color.rgb(255,105,120)
                    : Color.rgb(143,155,184));
            }
            public void afterTextChanged(Editable s) {}
        });

        LinearLayout editActions = new LinearLayout(this);
        editActions.setOrientation(LinearLayout.HORIZONTAL);
        Button clearText = button("Clear", Color.rgb(164,63,76));
        Button pasteText = button("Paste", Color.rgb(44,105,157));
        Button undoButton = button("Undo", Color.rgb(118,88,45));
        editActions.addView(clearText);
        editActions.addView(pasteText);
        editActions.addView(undoButton);
        root.addView(editActions);

        clearText.setOnClickListener(v -> {
            String current = input.getText().toString();
            if (current.isEmpty()) {
                Toast.makeText(this, "ยังไม่มีข้อความให้ลบ", Toast.LENGTH_SHORT).show();
                return;
            }
            undoText = current;
            input.setText("");
            status.setText("ลบข้อความแล้ว — กดย้อนกลับเพื่อกู้คืน");
        });

        pasteText.setOnClickListener(v -> pasteFromClipboard());

        undoButton.setOnClickListener(v -> {
            if (undoText == null) {
                Toast.makeText(this, "ยังไม่มีข้อความสำหรับย้อนกลับ", Toast.LENGTH_SHORT).show();
                return;
            }
            String current = input.getText().toString();
            input.setText(undoText);
            input.setSelection(input.getText().length());
            undoText = current;
            status.setText("ย้อนกลับข้อความแล้ว");
        });

        LinearLayout voicePanel = new LinearLayout(this);
        voicePanel.setOrientation(LinearLayout.VERTICAL);
        voicePanel.setBackground(panelBackground());
        voicePanel.setPadding(dp(10), dp(4), dp(10), dp(6));
        voicePanel.setElevation(dp(6));

        TextView voiceTitle = text("Thai Voice", 13, Color.rgb(242,214,145));
        voiceTitle.setPadding(dp(5), 0, dp(5), 0);
        voicePanel.addView(voiceTitle, new LinearLayout.LayoutParams(-1, dp(24)));

        voiceSpinner = new Spinner(this);
        voiceSpinner.setBackground(raisedBackground(Color.rgb(38,49,79)));
        voiceSpinner.setElevation(dp(4));
        voiceSpinner.setPadding(dp(10),0,dp(10),0);
        voicePanel.addView(voiceSpinner, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout.LayoutParams voicePanelParams =
            new LinearLayout.LayoutParams(-1, dp(72));
        voicePanelParams.setMargins(0, dp(3), 0, dp(3));
        root.addView(voicePanel, voicePanelParams);

        dhammaMode = new Switch(this);
        dhammaMode.setText("Dhamma  •  Slow & Deep");
        dhammaMode.setTextSize(14);
        dhammaMode.setSingleLine(true);
        dhammaMode.setTextColor(Color.rgb(242,214,145));
        dhammaMode.setBackground(panelBackground());
        dhammaMode.setPadding(dp(12), dp(4), dp(12), dp(4));
        dhammaMode.setElevation(dp(7));
        dhammaMode.setPadding(dp(12), dp(4), dp(12), dp(4));
        LinearLayout.LayoutParams dhammaParams =
            new LinearLayout.LayoutParams(-1, dp(44));
        dhammaParams.setMargins(0, dp(3), 0, dp(3));
        root.addView(dhammaMode, dhammaParams);

        speedValue = text("", 15, Color.rgb(202,210,230));
        speedBar = stepper(root, speedValue, 25, 200, 100, 5);
        pitchValue = text("", 15, Color.rgb(202,210,230));
        pitchBar = stepper(root, pitchValue, 50, 150, 100, 5);
        volumeValue = text("", 15, Color.rgb(202,210,230));
        volumeBar = stepper(root, volumeValue, 0, 100, 100, 5);
        updateLabels();
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
        Button speak = button("Read", Color.rgb(91,75,219));
        Button save = button("Save WAV", Color.rgb(16,148,112));
        row.addView(speak); row.addView(save); root.addView(row);

        status = text("กำลังเตรียมระบบเสียง...", 15, Color.rgb(202,210,230));
        status.setGravity(Gravity.CENTER); status.setTypeface(null, 1); root.addView(status);

        speak.setOnClickListener(v -> startSpeaking());
        timelinePlay.setOnClickListener(v -> startSpeaking());
        timelineStop.setOnClickListener(v -> {
            stopReading();
            saving = false;
            chunks.clear();
            chunkIndex = 0;
            nextChunkToQueue = 0;
            stopReadingTimer(true);
            status.setText("หยุดแล้ว");
        });
        save.setOnClickListener(v -> startSaving());
        setContentView(scroll);
    }

    private void pasteFromClipboard() {
        android.content.ClipboardManager clipboard =
            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip() ||
            clipboard.getPrimaryClip() == null ||
            clipboard.getPrimaryClip().getItemCount() == 0) {
            Toast.makeText(this, "ไม่มีข้อความอยู่ในคลิปบอร์ด", Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence pasted = clipboard.getPrimaryClip()
            .getItemAt(0).coerceToText(this);
        if (pasted == null || pasted.toString().trim().isEmpty()) {
            Toast.makeText(this, "คลิปบอร์ดไม่มีข้อความ", Toast.LENGTH_SHORT).show();
            return;
        }

        undoText = input.getText().toString();
        input.setText(pasted.toString());
        input.setSelection(input.getText().length());
        status.setText("วางข้อความแล้ว");
    }

    private SeekBar stepper(LinearLayout root, TextView valueLabel,
                            int min, int max, int value, int step) {
        SeekBar state = new SeekBar(this);
        state.setMin(min);
        state.setMax(max);
        state.setProgress(value);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(panelBackground());
        row.setElevation(dp(6));
        row.setPadding(dp(6), dp(4), dp(6), dp(4));

        Button minus = new Button(this);
        minus.setText("−");
        minus.setTextSize(19);
        minus.setTextColor(Color.WHITE);
        minus.setBackground(raisedBackground(Color.rgb(80,95,124)));
        minus.setAllCaps(false);
        minus.setPadding(0, 0, 0, 0);

        Button plus = new Button(this);
        plus.setText("+");
        plus.setTextSize(19);
        plus.setTextColor(Color.WHITE);
        plus.setBackground(raisedBackground(Color.rgb(91,75,219)));
        plus.setAllCaps(false);
        plus.setPadding(0, 0, 0, 0);

        valueLabel.setGravity(Gravity.CENTER);
        valueLabel.setTextSize(14);
        row.addView(minus, new LinearLayout.LayoutParams(dp(38), dp(36)));
        row.addView(valueLabel, new LinearLayout.LayoutParams(0, dp(36), 1));
        row.addView(plus, new LinearLayout.LayoutParams(dp(38), dp(36)));

        LinearLayout.LayoutParams rowParams =
            new LinearLayout.LayoutParams(-1, dp(44));
        rowParams.setMargins(0, dp(3), 0, dp(3));
        root.addView(row, rowParams);

        minus.setOnClickListener(v -> {
            state.setProgress(Math.max(state.getMin(), state.getProgress() - step));
            updateLabels();
        });
        plus.setOnClickListener(v -> {
            state.setProgress(Math.min(state.getMax(), state.getProgress() + step));
            updateLabels();
        });
        return state;
    }

    private void updateLabels() {
        if (speedValue == null) return;
        speedValue.setText("Speed  " + speedBar.getProgress() + "%" +
            (dhammaMode != null && dhammaMode.isChecked() ? "  •  Dhamma" : ""));
        pitchValue.setText("Pitch  " + pitchBar.getProgress() + "%");
        volumeValue.setText("Volume  " + volumeBar.getProgress() + "%");
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
            status.setText("พร้อมใช้งานออฟไลน์ • Stable Reading");
        }
        tts.setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String id) {
                runOnUiThread(() -> {
                    if (saving) {
                        status.setText("กำลังสร้างไฟล์เสียง...");
                    } else if (readingActive && id != null && id.startsWith("long_")) {
                        String[] parts = id.split("_");
                        if (parts.length == 4) {
                            try {
                                int current = Integer.parseInt(parts[2]) + 1;
                                int total = Integer.parseInt(parts[3]);
                                chunkIndex = current - 1;
                                updateReadingProgress(current - 1, 0);
                                status.setText("กำลังอ่านช่วงที่ " + current + " จาก " + total);
                            } catch (NumberFormatException ignored) {
                                status.setText("กำลังอ่านข้อความยาว...");
                            }
                        }
                    }
                });
            }
            @Override public void onRangeStart(
                String id, int start, int end, int frame) {
                if (readingActive && id != null && id.startsWith("long_")) {
                    String[] parts = id.split("_");
                    if (parts.length == 4) {
                        try {
                            int currentChunk = Integer.parseInt(parts[2]);
                            runOnUiThread(() ->
                                updateReadingProgress(currentChunk, end));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            public void onError(String id) {
                readingActive = false;
                saving = false;
                stopReadingTimer(false);
                releaseAudioFocus();
                runOnUiThread(() -> status.setText("เกิดข้อผิดพลาดในการสร้างเสียง"));
            }
            public void onDone(String id) {
                if (saving) {
                    finishSave();
                } else if (readingActive && id != null && id.startsWith("long_")) {
                    String[] parts = id.split("_");
                    if (parts.length == 4) {
                        try {
                            long session = Long.parseLong(parts[1]);
                            int current = Integer.parseInt(parts[2]);
                            int total = Integer.parseInt(parts[3]);
                            if (session == readingSession && current == total - 1) {
                                readingActive = false;
                                releaseAudioFocus();
                                runOnUiThread(() -> {
                                    readingProgress.setProgress(1000);
                                    stopReadingTimer(false);
                                    status.setText("อ่านครบทุกช่วงแล้ว • Stable");
                                });
                            } else if (session == readingSession) {
                                queueNextStableChunk();
                            }
                        } catch (NumberFormatException ignored) {}
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
                v.setTextSize(14);
                v.setPadding(dp(14), 0, dp(14), 0);
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(Color.rgb(24,31,52));
                v.setTextSize(15);
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
            labels.add("Phone Voice");
        }

        Set<Voice> all = tts.getVoices();
        if (all != null) {
            for (Voice v : all) {
                boolean duplicate = phoneDefaultVoice != null &&
                    v.getName().equals(phoneDefaultVoice.getName());
                if ("th".equals(v.getLocale().getLanguage()) && !duplicate) {
                    thaiVoices.add(v);
                    String mode = v.isNetworkConnectionRequired() ? "ออนไลน์" : "ออฟไลน์";
                    labels.add("Thai " + (labels.size()+1) + " • " + mode);
                }
            }
        }

        if (labels.isEmpty()) labels.add("Thai Voice");
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
        String rawValue = input.getText().toString();
        if (rawValue.length() > MAX_INPUT_CHARS) {
            Toast.makeText(
                this,
                "ข้อความเกิน 950 ตัวอักษร กรุณาลบส่วนที่เกินก่อน",
                Toast.LENGTH_LONG).show();
            status.setText("เกินกำหนด • ลบให้เหลือไม่เกิน 950 ตัวอักษร");
            return false;
        }
        String value = normalizeForStableReading(rawValue);
        if (value.isEmpty()) {
            Toast.makeText(this, "กรุณาใส่ข้อความก่อน", Toast.LENGTH_SHORT).show();
            return false;
        }
        chunks.clear();
        int engineLimit = TextToSpeech.getMaxSpeechInputLength() - 100;
        if (value.length() > engineLimit) {
            Toast.makeText(
                this,
                "ข้อความยาวเกินขีดจำกัดของระบบเสียง",
                Toast.LENGTH_LONG).show();
            return false;
        }
        chunks.add(value);
        chunkIndex = 0;
        return !chunks.isEmpty();
    }

    private String normalizeForStableReading(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        return normalized
            .replaceAll("[\\u200B\\u200C\\u200D\\u2060\\uFEFF\\u00AD]", "")
            .replaceAll("[\\u202A-\\u202E\\u2066-\\u2069]", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replaceAll("[\\t\\x0B\\f ]+", " ")
            .replaceAll(" *\\n *", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    private Bundle params() {
        Bundle b = new Bundle();
        float requested = volumeBar.getProgress() / 100f;
        float safeVolume = Math.min(requested, 0.92f);
        b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, safeVolume);
        return b;
    }

    private void startSpeaking() {
        if (tts == null || !prepareText()) return;

        if (readingActive) stopReading();
        if (!requestStableAudioFocus()) {
            status.setText("ยังเริ่มอ่านไม่ได้ • มีแอปอื่นกำลังใช้เสียง");
            return;
        }

        saving = false;
        readingActive = true;
        applySettings();
        status.setText(chunks.size() == 1
            ? "กำลังเริ่มอ่าน..."
            : "แบ่งข้อความอัตโนมัติเป็น " + chunks.size() + " ช่วง");
        readingSession = System.currentTimeMillis();
        nextChunkToQueue = 0;
        startReadingTimer();

        if (!queueNextStableChunk(TextToSpeech.QUEUE_FLUSH)) return;
        queueNextStableChunk(TextToSpeech.QUEUE_ADD);
    }

    private synchronized void queueNextStableChunk() {
        queueNextStableChunk(TextToSpeech.QUEUE_ADD);
    }

    private synchronized boolean queueNextStableChunk(int queueMode) {
        if (!readingActive || nextChunkToQueue >= chunks.size()) return true;

        int index = nextChunkToQueue++;
        String utteranceId =
            "long_" + readingSession + "_" + index + "_" + chunks.size();
        int result = tts.speak(
            chunks.get(index),
            queueMode,
            params(),
            utteranceId);

        if (result != TextToSpeech.SUCCESS) {
            readingActive = false;
            tts.stop();
            stopReadingTimer(false);
            releaseAudioFocus();
            runOnUiThread(() -> status.setText("จัดคิวอ่านข้อความไม่สำเร็จ"));
            return false;
        }
        return true;
    }

    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (!readingActive || readingStartedAt == 0L) return;
            long elapsed = System.currentTimeMillis() - readingStartedAt;
            timeValue.setText(formatElapsed(elapsed));
            timerHandler.postDelayed(this, 1000);
        }
    };

    private void startReadingTimer() {
        readingStartedAt = System.currentTimeMillis();
        timeValue.setText("00:00");
        readingProgress.setProgress(0);
        timerHandler.removeCallbacks(timerTick);
        timerHandler.post(timerTick);
    }

    private void stopReadingTimer(boolean reset) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            timerHandler.post(() -> stopReadingTimer(reset));
            return;
        }
        timerHandler.removeCallbacks(timerTick);
        if (readingStartedAt != 0L && !reset) {
            timeValue.setText(formatElapsed(
                System.currentTimeMillis() - readingStartedAt));
        }
        if (reset) {
            readingStartedAt = 0L;
            timeValue.setText("00:00");
            readingProgress.setProgress(0);
        }
    }

    private String formatElapsed(long milliseconds) {
        long totalSeconds = Math.max(0, milliseconds / 1000);
        return String.format(
            Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private void updateReadingProgress(int currentChunk, int localEnd) {
        if (readingProgress == null || chunks.isEmpty()) return;
        int completed = 0;
        for (int i = 0; i < currentChunk && i < chunks.size(); i++) {
            completed += chunks.get(i).length();
        }
        if (currentChunk >= 0 && currentChunk < chunks.size()) {
            completed += Math.min(localEnd, chunks.get(currentChunk).length());
        }
        int total = 0;
        for (String part : chunks) total += part.length();
        int progress = total == 0 ? 0 :
            Math.min(1000, Math.round(completed * 1000f / total));
        readingProgress.setProgress(progress);
    }

    private void createAudioFocusRequest() {
        if (audioManager == null) return;
        AudioAttributes focusAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();

        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(focusAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(change -> {
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    runOnUiThread(() -> {
                        if (readingActive) {
                            stopReading();
                            status.setText("หยุดชั่วคราว • มีเสียงจากแอปอื่น");
                        }
                    });
                }
            })
            .build();
    }

    private boolean requestStableAudioFocus() {
        if (audioManager == null || audioFocusRequest == null) return true;
        int result = audioManager.requestAudioFocus(audioFocusRequest);
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hasAudioFocus;
    }

    private void releaseAudioFocus() {
        if (hasAudioFocus && audioManager != null && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
        hasAudioFocus = false;
    }

    private void stopReading() {
        readingActive = false;
        if (tts != null) tts.stop();
        stopReadingTimer(false);
        releaseAudioFocus();
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
        stopReading();
        if (tts != null) tts.shutdown();
        releaseAudioFocus();
        super.onDestroy();
    }
}

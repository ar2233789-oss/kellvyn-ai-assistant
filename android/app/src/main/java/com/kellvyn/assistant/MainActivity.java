package com.kellvyn.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int DEFAULT_BG = Color.rgb(11, 20, 19);
    private static final int DEFAULT_PANEL = Color.rgb(26, 35, 33);
    private static final int DEFAULT_PANEL_ALT = Color.rgb(23, 36, 33);
    private static final int EMERALD = Color.rgb(56, 230, 188);
    private static final int DEFAULT_TEXT = Color.rgb(227, 238, 236);
    private static final int DEFAULT_MUTED = Color.rgb(145, 155, 154);
    private static final String PREFS = "kellvyn_native";
    private static final int PICK_FILE = 1001;
    private static final int SPEECH = 1002;
    private static final int SAVE_TEXT = 1003;
    private static final String[] THINKING_STEPS = {"Analisando", "Pensando", "Organizando a resposta"};
    private static final Pattern GENERATED_PROJECT_FILE = Pattern.compile(
            "(?m)^Arquivo:\\s*([^\\r\\n]+)\\s*\\r?\\n\\s*(?:\\x60){3}[^\\r\\n]*\\r?\\n(.*?)\\r?\\n(?:\\x60){3}",
            Pattern.DOTALL);

    private final ArrayList<String> transcript = new ArrayList<>();
    private String activeHistoryId = "current";
    private LinearLayout messages;
    private ScrollView chatScroll;
    private EditText composer;
    private TextView status;
    private RainView rain;
    private SharedPreferences prefs;
    private String selectedAttachment = null;
    private Uri selectedAttachmentUri = null;
    private TextView footerStatus;
    private TextView activeReplyBubble;
    private View thinkingIndicator;
    private TextView thinkingLabel;
    private ObjectAnimator thinkingAnimator;
    private ValueAnimator thinkingRingAnimator;
    private ValueAnimator thinkingTextAnimator;
    private final Handler thinkingHandler = new Handler(android.os.Looper.getMainLooper());
    private Runnable thinkingStepRunnable;
    private int thinkingStepIndex = 0;
    private TextToSpeech textToSpeech;
    private String pendingDownloadText = "";
    private LocalBrain brain;
    private boolean waitingForReply = false;
    private String engineLabel = "MOTOR LOCAL PENDENTE";
    private int accent = EMERALD;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(uiBackground());
        getWindow().setNavigationBarColor(uiBackground());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        activeHistoryId = prefs.getString("active_history_id", "current");
        brain = new LocalBrain(getApplicationContext());
        textToSpeech = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS && textToSpeech != null) {
                textToSpeech.setLanguage(new Locale("pt", "BR"));
            }
        });
        accent = prefs.getInt("accent", EMERALD);
        restoreTranscript();
        buildScreen();
        for (String item : transcript) addMessageView(!item.startsWith("S|"), item.substring(2));
        if (transcript.isEmpty()) showWelcome();
        updateStatus();
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void buildScreen() {
        // Stop the previous full-screen animation before replacing the view tree.
        // Without this, every color change leaves an unreachable RainView scheduling frames.
        if (rain != null) rain.stop();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(uiBackground());
        rain = new RainView();
        root.addView(rain, new FrameLayout.LayoutParams(-1, -1));
        // Theme changes rebuild this view tree without invoking Activity.onResume() again.
        // Start the replacement so rain/lightning never freezes after a color switch.
        rain.start();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(5), dp(12), dp(5));
        header.setBackgroundColor(themeSurfaceColor(0xEE0B1211));
        // 46dp header + 1dp divider reproduces the web reference's 47dp header.
        shell.addView(header, new LinearLayout.LayoutParams(-1, dp(46)));

        View menu = new LucideIconView("menu", "Histórico");
        header.addView(menu, new LinearLayout.LayoutParams(dp(36), dp(36)));
        menu.setOnClickListener(v -> showHistory());
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, -2, 1);
        brandLp.leftMargin = dp(12);
        header.addView(brand, brandLp);
        brand.addView(text("Kellvyn Assistente", 18, accent, false));
        status = null;
        View settings = new LucideIconView("more-vertical", "Configurações");
        header.addView(settings, new LinearLayout.LayoutParams(dp(36), dp(36)));
        settings.setOnClickListener(v -> showSettingsPanel());

        View divider = new View(this);
        divider.setBackgroundColor(themeAccentColor(0x5533C995));
        shell.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setClipToPadding(false);
        chatScroll.setPadding(dp(12), dp(14), dp(12), dp(14));
        shell.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        chatScroll.addView(messages, new ScrollView.LayoutParams(-1, -2));

        LinearLayout composerPanel = new LinearLayout(this);
        composerPanel.setOrientation(LinearLayout.VERTICAL);
        // The reference draws one rounded border around both the prompt and the action row.
        composerPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        composerPanel.setBackground(roundRect(uiPanelAltColor(), dp(18), themeAccentColor(0x8845A989), dp(1)));
        LinearLayout.LayoutParams composerPanelLp = new LinearLayout.LayoutParams(-1, -2);
        composerPanelLp.setMargins(dp(12), dp(4), dp(12), dp(12));
        shell.addView(composerPanel, composerPanelLp);

        LinearLayout entry = new LinearLayout(this);
        entry.setGravity(Gravity.CENTER_VERTICAL);
        entry.setBackgroundColor(Color.TRANSPARENT);
        composerPanel.addView(entry, new LinearLayout.LayoutParams(-1, -2));

        composer = new EditText(this);
        composer.setHint("Pergunte qualquer coisa...");
        composer.setHintTextColor(uiMutedColor());
        composer.setTextColor(uiTextColor());
        composer.setTextSize(15);
        composer.setSingleLine(false);
        composer.setMinLines(1);
        composer.setMaxLines(4);
        composer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.setBackgroundColor(Color.TRANSPARENT);
        composer.setPadding(dp(8), dp(10), dp(8), dp(10));
        entry.addView(composer, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, dp(4), 0, 0);
        composerPanel.addView(controls, new LinearLayout.LayoutParams(-1, dp(42)));

        View plus = new LucideIconView("plus", "Anexos e Web");
        controls.addView(plus, new LinearLayout.LayoutParams(dp(36), dp(36)));
        plus.setOnClickListener(v -> showPlusMenu(v));

        footerStatus = text("●  Kellvyn  ⌄", 12, uiTextColor(), false);
        SpannableString compactLabel = new SpannableString("●  Kellvyn  ⌄");
        compactLabel.setSpan(new ForegroundColorSpan(accent), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        footerStatus.setText(compactLabel);
        footerStatus.setSingleLine(true);
        footerStatus.setIncludeFontPadding(false);
        footerStatus.setGravity(Gravity.CENTER);
        footerStatus.setPadding(dp(10), 0, dp(10), 0);
        footerStatus.setBackground(roundRect(uiPanelAltColor(), dp(12), themeAccentColor(0x8845A989), dp(1)));
        controls.addView(footerStatus, new LinearLayout.LayoutParams(-2, dp(34)));

        View spacer = new View(this);
        controls.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        View mic = new LucideIconView("mic", "Ditado por voz");
        controls.addView(mic, new LinearLayout.LayoutParams(dp(36), dp(36)));
        mic.setOnClickListener(v -> startSpeech());

        View send = new LucideIconView("send", "Enviar mensagem");
        send.setBackground(roundRect(0x00000000, dp(18), themeAccentColor(0x8845A989), dp(1)));
        controls.addView(send, new LinearLayout.LayoutParams(dp(36), dp(36)));
        send.setOnClickListener(v -> sendMessage());

        View navDivider = new View(this);
        navDivider.setBackgroundColor(themeAccentColor(0x4433C995));
        shell.addView(navDivider, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setGravity(Gravity.CENTER_VERTICAL);
        bottomNav.setBackgroundColor(themeSurfaceColor(0xF20A1110));
        bottomNav.setPadding(dp(2), dp(3), dp(2), dp(4));
        // 67dp navigation + the divider matches the measured 67.5dp web navigation bar.
        shell.addView(bottomNav, new LinearLayout.LayoutParams(-1, dp(67)));
        addNavItem(bottomNav, "✦", "AI", () -> { composer.requestFocus(); });
        addNavItem(bottomNav, "⌕", "Navegador", this::askWebAddress);
        addNavItem(bottomNav, "</>", "Editor", this::showEditor);
        addNavItem(bottomNav, ">_", "Terminal", this::openTermux);
        addNavItem(bottomNav, "♧", "Bots", this::showBots);
        addNavItem(bottomNav, "▦", "Apps", this::openPhoneApps);
        setContentView(root);
        // Theme changes replace this view tree without invoking Activity.onResume().
        // Start the replacement effect layer immediately; onPause() still stops it normally.
        if (rain != null) rain.start();
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color, int radius, int stroke, int strokeWidth) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, stroke);
        return d;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private TextView iconButton(String glyph, String description) {
        TextView t = text(glyph, 21, accent, false);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(description);
        t.setBackground(roundRect(0x00000000, dp(10), 0, 0));
        t.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) v.setAlpha(0.65f);
            if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) v.setAlpha(1f);
            return false;
        });
        return t;
    }

    /** Crisp outline icons matching the Lucide set used by the web reference. */
    private final class LucideIconView extends View {
        private final String iconName;
        private final float iconSizeDp;
        private final int iconColor;
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path iconPath = new Path();

        LucideIconView(String iconName, String description) {
            this(iconName, description, 22f, accent);
        }

        LucideIconView(String iconName, String description, float iconSizeDp) {
            this(iconName, description, iconSizeDp, accent);
        }

        LucideIconView(String iconName, String description, float iconSizeDp, int iconColor) {
            super(MainActivity.this);
            this.iconName = iconName;
            this.iconSizeDp = iconSizeDp;
            this.iconColor = iconColor;
            if (description != null) setContentDescription(description);
            else setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) v.setAlpha(0.68f);
                if (event.getAction() == android.view.MotionEvent.ACTION_UP
                        || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) v.setAlpha(1f);
                return false;
            });
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = dp(iconSizeDp);
            canvas.save();
            canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            canvas.scale(size / 24f, size / 24f);
            iconPaint.reset();
            iconPaint.setAntiAlias(true);
            iconPaint.setColor(iconColor);
            iconPaint.setStrokeWidth(1.9f);
            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
            iconPaint.setStrokeJoin(Paint.Join.ROUND);
            if ("AI".equals(iconName)) iconPaint.setShadowLayer(1.0f, 0, 0, themeAccentColor(0x9938E6BC));

            switch (iconName) {
                case "AI":
                    iconPath.reset();
                    iconPath.moveTo(10.0f, 2.5f);
                    iconPath.lineTo(12.0f, 8.5f);
                    iconPath.lineTo(18.0f, 10.5f);
                    iconPath.lineTo(12.0f, 12.5f);
                    iconPath.lineTo(10.0f, 18.5f);
                    iconPath.lineTo(8.0f, 12.5f);
                    iconPath.lineTo(2.0f, 10.5f);
                    iconPath.lineTo(8.0f, 8.5f);
                    iconPath.close();
                    canvas.drawPath(iconPath, iconPaint);
                    iconPath.reset();
                    iconPath.moveTo(19.0f, 14.5f);
                    iconPath.lineTo(20.0f, 17.0f);
                    iconPath.lineTo(22.5f, 18.0f);
                    iconPath.lineTo(20.0f, 19.0f);
                    iconPath.lineTo(19.0f, 21.5f);
                    iconPath.lineTo(18.0f, 19.0f);
                    iconPath.lineTo(15.5f, 18.0f);
                    iconPath.lineTo(18.0f, 17.0f);
                    iconPath.close();
                    canvas.drawPath(iconPath, iconPaint);
                    break;
                case "Navegador":
                    canvas.drawCircle(12, 12, 9.2f, iconPaint);
                    canvas.drawOval(7.4f, 2.8f, 16.6f, 21.2f, iconPaint);
                    iconPath.reset();
                    iconPath.moveTo(3.2f, 8.0f); iconPath.quadTo(12, 11.5f, 20.8f, 8.0f);
                    iconPath.moveTo(3.2f, 16.0f); iconPath.quadTo(12, 12.5f, 20.8f, 16.0f);
                    canvas.drawPath(iconPath, iconPaint);
                    break;
                case "Editor":
                    iconPath.reset();
                    iconPath.moveTo(8, 6); iconPath.lineTo(2.5f, 12); iconPath.lineTo(8, 18);
                    iconPath.moveTo(16, 6); iconPath.lineTo(21.5f, 12); iconPath.lineTo(16, 18);
                    iconPath.moveTo(14.2f, 4); iconPath.lineTo(9.8f, 20);
                    canvas.drawPath(iconPath, iconPaint);
                    break;
                case "Terminal":
                    canvas.drawRoundRect(3, 4, 21, 20, 2.8f, 2.8f, iconPaint);
                    iconPath.reset();
                    iconPath.moveTo(7, 9); iconPath.lineTo(10, 12); iconPath.lineTo(7, 15);
                    iconPath.moveTo(12.5f, 16); iconPath.lineTo(17, 16);
                    canvas.drawPath(iconPath, iconPaint);
                    break;
                case "Bots":
                    canvas.drawRoundRect(4, 7, 20, 21, 2.5f, 2.5f, iconPaint);
                    iconPath.reset();
                    iconPath.moveTo(12, 3); iconPath.lineTo(12, 7);
                    iconPath.moveTo(2, 11); iconPath.lineTo(4, 11);
                    iconPath.moveTo(20, 11); iconPath.lineTo(22, 11);
                    canvas.drawPath(iconPath, iconPaint);
                    iconPaint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(12, 3, 1.4f, iconPaint);
                    canvas.drawCircle(8.5f, 13, 1.2f, iconPaint);
                    canvas.drawCircle(15.5f, 13, 1.2f, iconPaint);
                    break;
                case "Apps":
                    canvas.drawRoundRect(3.5f, 3.5f, 10, 10, 1.2f, 1.2f, iconPaint);
                    canvas.drawRoundRect(14, 3.5f, 20.5f, 10, 1.2f, 1.2f, iconPaint);
                    canvas.drawRoundRect(3.5f, 14, 10, 20.5f, 1.2f, 1.2f, iconPaint);
                    canvas.drawRoundRect(14, 14, 20.5f, 20.5f, 1.2f, 1.2f, iconPaint);
                    break;
                case "menu":
                    canvas.drawLine(4, 6, 20, 6, iconPaint);
                    canvas.drawLine(4, 12, 20, 12, iconPaint);
                    canvas.drawLine(4, 18, 20, 18, iconPaint);
                    break;
                case "more-vertical":
                    iconPaint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(12, 5, 1.25f, iconPaint);
                    canvas.drawCircle(12, 12, 1.25f, iconPaint);
                    canvas.drawCircle(12, 19, 1.25f, iconPaint);
                    break;
                case "plus":
                    canvas.drawLine(12, 5, 12, 19, iconPaint);
                    canvas.drawLine(5, 12, 19, 12, iconPaint);
                    break;
                case "mic":
                    canvas.drawRoundRect(9, 3, 15, 15, 3, 3, iconPaint);
                    canvas.drawArc(5, 7, 19, 20, 0, 180, false, iconPaint);
                    canvas.drawLine(12, 20, 12, 22, iconPaint);
                    canvas.drawLine(8, 22, 16, 22, iconPaint);
                    break;
                case "copy":
                    canvas.drawRoundRect(8, 8, 20, 21, 1.5f, 1.5f, iconPaint);
                    canvas.drawRoundRect(4, 3, 16, 16, 1.5f, 1.5f, iconPaint);
                    break;
                case "volume-2":
                    iconPath.reset();
                    iconPath.moveTo(3, 9); iconPath.lineTo(7, 9); iconPath.lineTo(12, 5);
                    iconPath.lineTo(12, 19); iconPath.lineTo(7, 15); iconPath.lineTo(3, 15);
                    iconPath.close();
                    canvas.drawPath(iconPath, iconPaint);
                    iconPath.reset();
                    iconPath.moveTo(16, 9); iconPath.quadTo(20, 12, 16, 15);
                    iconPath.moveTo(18, 6); iconPath.quadTo(24, 12, 18, 18);
                    canvas.drawPath(iconPath, iconPaint);
                    break;
                case "download":
                    iconPath.reset();
                    iconPath.moveTo(12, 3); iconPath.lineTo(12, 15);
                    iconPath.moveTo(7, 10); iconPath.lineTo(12, 15); iconPath.lineTo(17, 10);
                    iconPath.moveTo(4, 20); iconPath.lineTo(20, 20);
                    canvas.drawPath(iconPath, iconPaint);
                    break;
                case "share-2":
                    iconPath.reset();
                    iconPath.moveTo(8.5f, 11); iconPath.lineTo(15.5f, 6.5f);
                    iconPath.moveTo(8.5f, 13); iconPath.lineTo(15.5f, 17.5f);
                    canvas.drawPath(iconPath, iconPaint);
                    canvas.drawCircle(6, 12, 2.5f, iconPaint);
                    canvas.drawCircle(18, 5, 2.5f, iconPaint);
                    canvas.drawCircle(18, 19, 2.5f, iconPaint);
                    break;
                case "send":
                    canvas.drawCircle(12, 12, 10, iconPaint);
                    iconPath.reset();
                    iconPath.moveTo(12, 16); iconPath.lineTo(12, 8);
                    iconPath.moveTo(8.8f, 11.2f); iconPath.lineTo(12, 8); iconPath.lineTo(15.2f, 11.2f);
                    canvas.drawPath(iconPath, iconPaint);
                    break;
                default:
                    canvas.drawCircle(12, 12, 8, iconPaint);
                    break;
            }
            canvas.restore();
        }
    }

    private void showWelcome() {
        LinearLayout welcome = new LinearLayout(this);
        welcome.setOrientation(LinearLayout.VERTICAL);
        welcome.setGravity(Gravity.CENTER_HORIZONTAL);
        welcome.setPadding(dp(8), dp(96), dp(8), dp(24));
        welcome.addView(new KellvynLogoView(), new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView sub = text("Converse com a Kellvyn IA ou peça para criar um aplicativo.", 15, uiMutedColor(), false);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(dp(2), 1f);
        int welcomeWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.82f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(welcomeWidth, -2);
        slp.topMargin = dp(12);
        welcome.addView(sub, slp);
        messages.addView(welcome, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView addMessageView(boolean user, String value) {
        if (messages.getChildCount() == 1 && messages.getChildAt(0).getTag() == null) messages.removeAllViews();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(user ? Gravity.END : Gravity.START);
        row.setPadding(dp(1), dp(4), dp(1), dp(4));
        TextView bubble = text(value, 14, uiTextColor(), false);
        bubble.setLineSpacing(dp(3), 1f);
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.84f));
        int bubbleBackground = user
                ? themeUserBubbleColor(0xE51B3D31)
                : themeAssistantBubbleColor(0xE5111B19);
        int bubbleBorder = user
                ? themeAccentColor(0xAA29C78D)
                : themeAccentColor(0x5544A58A);
        bubble.setBackground(roundRect(bubbleBackground, dp(14), bubbleBorder, dp(1)));

        if (user) {
            row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
        } else {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.START);
            column.addView(bubble, new LinearLayout.LayoutParams(-2, -2));

            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            actions.setPadding(dp(1), dp(1), 0, 0);
            addMessageAction(actions, "copy", "Copiar", () -> copyText(bubble.getText().toString()));
            addMessageAction(actions, "volume-2", "Ouvir", () -> speakText(bubble.getText().toString()));
            addMessageAction(actions, "download", "Baixar", () -> downloadText(bubble.getText().toString()));
            addMessageAction(actions, "share-2", "Compartilhar", () -> shareText(bubble.getText().toString()));
            column.addView(actions, new LinearLayout.LayoutParams(-2, dp(30)));
            row.addView(column, new LinearLayout.LayoutParams(-2, -2));
        }

        messages.addView(row, new LinearLayout.LayoutParams(-1, -2));
        row.setTag("message");
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        return bubble;
    }

    private void addMessageAction(LinearLayout parent, String iconName, String description, Runnable action) {
        // Keep reply actions muted like the web reference; each click reads the same bubble's final text.
        LucideIconView button = new LucideIconView(iconName, description, 17f, uiMutedColor());
        button.setBackground(roundRect(0x00000000, dp(7), 0, 0));
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(dp(34), dp(30)));
    }

    private void copyText(String value) {
        if (value == null || value.trim().isEmpty()) return;
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("Resposta da Kellvyn IA", value));
            toast("Resposta copiada.");
        }
    }

    private void speakText(String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (textToSpeech == null) {
            toast("Leitura em voz alta indisponível.");
            return;
        }
        textToSpeech.speak(value, TextToSpeech.QUEUE_FLUSH, null, "kellvyn-response");
    }

    private void downloadText(String value) {
        if (value == null || value.trim().isEmpty()) return;
        pendingDownloadText = value;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "kellvyn-resposta.txt");
        try { startActivityForResult(intent, SAVE_TEXT); }
        catch (ActivityNotFoundException e) { toast("Não foi possível abrir o seletor para salvar a resposta."); }
    }

    private void shareText(String value) {
        if (value == null || value.trim().isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, value);
        try { startActivity(Intent.createChooser(intent, "Compartilhar resposta")); }
        catch (ActivityNotFoundException e) { toast("Não há aplicativo compatível para compartilhar."); }
    }

    private void showThinkingIndicator() {
        hideThinkingIndicator();

        // Match the locked Lovable ThinkingBrain: 28dp halo, 18dp brain, 13sp shimmer
        // text and three status phases that rotate every 900ms. Keep the brain deliberately small.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        FrameLayout brainFrame = new FrameLayout(this);
        brainFrame.setClipChildren(false);
        brainFrame.setClipToPadding(false);
        View ring = new View(this);
        android.graphics.drawable.GradientDrawable ringShape = new android.graphics.drawable.GradientDrawable();
        ringShape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        ringShape.setColor(Color.argb(64, Color.red(accent), Color.green(accent), Color.blue(accent)));
        ring.setBackground(ringShape);
        brainFrame.addView(ring, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));

        ThinkingBrainIconView brainIcon = new ThinkingBrainIconView();
        brainFrame.addView(brainIcon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        row.addView(brainFrame, new LinearLayout.LayoutParams(dp(28), dp(28)));

        thinkingLabel = text(THINKING_STEPS[0] + "…", 13, uiMutedColor(), false);
        thinkingLabel.setIncludeFontPadding(false);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-2, -2);
        labelLp.leftMargin = dp(8);
        row.addView(thinkingLabel, labelLp);

        thinkingIndicator = row;
        messages.addView(row, new LinearLayout.LayoutParams(-1, -2));

        thinkingRingAnimator = ValueAnimator.ofFloat(0f, 1f);
        thinkingRingAnimator.setDuration(900);
        thinkingRingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        thinkingRingAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        thinkingRingAnimator.addUpdateListener(animation -> {
            float phase = (float) animation.getAnimatedValue();
            float scale = 1f + 0.8f * phase;
            ring.setScaleX(scale);
            ring.setScaleY(scale);
            ring.setAlpha(1f - phase);
        });
        thinkingRingAnimator.start();

        thinkingAnimator = ObjectAnimator.ofFloat(brainIcon, View.ALPHA, 0.65f, 1f);
        thinkingAnimator.setDuration(900);
        thinkingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        thinkingAnimator.setRepeatMode(ValueAnimator.REVERSE);
        thinkingAnimator.start();

        thinkingTextAnimator = ValueAnimator.ofFloat(0f, 1f);
        thinkingTextAnimator.setDuration(1600);
        thinkingTextAnimator.setRepeatCount(ValueAnimator.INFINITE);
        thinkingTextAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        thinkingTextAnimator.addUpdateListener(animation -> {
            if (thinkingLabel == null) return;
            float t = (float) animation.getAnimatedValue();
            float phase = t < 0.5f ? t * 2f : (1f - t) * 2f;
            thinkingLabel.setTextColor(blendColors(uiMutedColor(), accent, 0.08f + phase * 0.38f));
        });
        thinkingTextAnimator.start();

        thinkingStepIndex = 0;
        thinkingStepRunnable = new Runnable() {
            @Override public void run() {
                if (thinkingLabel == null) return;
                thinkingStepIndex = (thinkingStepIndex + 1) % THINKING_STEPS.length;
                thinkingLabel.setText(THINKING_STEPS[thinkingStepIndex] + "…");
                thinkingHandler.postDelayed(this, 900L);
            }
        };
        thinkingHandler.postDelayed(thinkingStepRunnable, 900L);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void hideThinkingIndicator() {
        if (thinkingStepRunnable != null) {
            thinkingHandler.removeCallbacks(thinkingStepRunnable);
            thinkingStepRunnable = null;
        }
        if (thinkingAnimator != null) {
            thinkingAnimator.cancel();
            thinkingAnimator = null;
        }
        if (thinkingRingAnimator != null) {
            thinkingRingAnimator.cancel();
            thinkingRingAnimator = null;
        }
        if (thinkingTextAnimator != null) {
            thinkingTextAnimator.cancel();
            thinkingTextAnimator = null;
        }
        if (thinkingIndicator != null) {
            if (thinkingIndicator.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) thinkingIndicator.getParent()).removeView(thinkingIndicator);
            }
            thinkingIndicator = null;
        }
        thinkingLabel = null;
        thinkingStepIndex = 0;
    }

    private final class ThinkingBrainIconView extends View {
        private final Paint brainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ThinkingBrainIconView() {
            super(MainActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth(), getHeight()) / 24f;
            if (scale <= 0f) return;
            canvas.save();
            canvas.translate((getWidth() - 24f * scale) / 2f, (getHeight() - 24f * scale) / 2f);
            canvas.scale(scale, scale);
            brainPaint.setColor(accent);
            brainPaint.setStyle(Paint.Style.STROKE);
            brainPaint.setStrokeWidth(1.7f);
            brainPaint.setStrokeCap(Paint.Cap.ROUND);
            brainPaint.setStrokeJoin(Paint.Join.ROUND);
            brainPaint.setShadowLayer(3f, 0f, 0f, accent);

            Path brain = new Path();
            brain.moveTo(12f, 18f);
            brain.lineTo(12f, 5f);
            brain.moveTo(12f, 5f);
            brain.cubicTo(10.4f, 2.4f, 7.4f, 2.0f, 5.8f, 4.1f);
            brain.cubicTo(3.4f, 4.0f, 2.8f, 6.6f, 4.0f, 8.0f);
            brain.cubicTo(2.7f, 9.7f, 3.4f, 11.3f, 4.2f, 12.0f);
            brain.cubicTo(2.8f, 14.5f, 4.5f, 16.3f, 6.2f, 16.2f);
            brain.cubicTo(6.3f, 18.8f, 9.1f, 20.0f, 12f, 18f);
            brain.cubicTo(14.9f, 20.0f, 17.7f, 18.8f, 17.8f, 16.2f);
            brain.cubicTo(20.5f, 16.1f, 21.3f, 13.8f, 19.9f, 12.0f);
            brain.cubicTo(21.2f, 10.2f, 20.6f, 8.8f, 20.0f, 8.0f);
            brain.cubicTo(21.1f, 5.6f, 19.4f, 3.8f, 17.8f, 4.2f);
            brain.cubicTo(16.3f, 2.0f, 13.4f, 2.4f, 12f, 5f);
            brain.moveTo(12f, 9f);
            brain.cubicTo(11.6f, 11.0f, 9.7f, 12.5f, 8.2f, 12.5f);
            brain.moveTo(12f, 9f);
            brain.cubicTo(12.4f, 11.0f, 14.3f, 12.5f, 15.8f, 12.5f);
            brain.moveTo(12f, 17.9f);
            brain.cubicTo(10.8f, 16.1f, 9.9f, 15.4f, 8.2f, 15.3f);
            canvas.drawPath(brain, brainPaint);
            brainPaint.clearShadowLayer();
            canvas.restore();
        }
    }

    private void sendMessage() {
        if (waitingForReply) {
            toast("A Kellvyn ainda está respondendo. Aguarde a resposta atual.");
            return;
        }
        String userText = composer.getText().toString().trim();
        if (userText.isEmpty() && selectedAttachment == null) return;
        if (selectedAttachment == null && handleLocalBotChatCommand(userText)) return;

        final String fileName = selectedAttachment;
        final Uri fileUri = selectedAttachmentUri;
        final String displayText = fileName == null
                ? userText
                : (userText.isEmpty() ? "Anexo selecionado: " + fileName
                : userText + "\n\nAnexo selecionado: " + fileName);
        final String prompt;
        if (fileName == null) {
            prompt = promptWithProjectGuidelines(userText);
        } else if (userText.isEmpty()) {
            prompt = "Analise o arquivo anexado chamado " + fileName + ". Se o aplicativo não tiver enviado conteúdo legível, diga isso claramente e não invente seu conteúdo.";
        } else {
            prompt = promptWithProjectGuidelines(userText + "\n\nO arquivo anexado se chama " + fileName + ". Analise somente o conteúdo que o aplicativo conseguir extrair e enviar; se não recebeu o conteúdo real, diga isso claramente e não invente.");
        }

        if (!brain.hasModel()) {
            new AlertDialog.Builder(this)
                    .setTitle("Preparar o cérebro local")
                    .setMessage("Para ativar respostas reais no aparelho, é necessário baixar o modelo Gemma local (aproximadamente 2,01 GB). O download usa a conexão atual do celular e pode ser retomado se for interrompido. Depois de instalado, as respostas são processadas no aparelho, sem enviar suas mensagens a um serviço de IA na nuvem. Libere pelo menos 2,2 GB de espaço livre. Deseja baixar o modelo e enviar esta mensagem?")
                    .setNegativeButton("Agora não", null)
                    .setPositiveButton("Baixar e enviar", (dialog, which) ->
                            startBrainRequest(displayText, prompt, fileUri, fileName))
                    .show();
            return;
        }
        startBrainRequest(displayText, prompt, fileUri, fileName);
    }

    private void startBrainRequest(String displayText, String prompt, Uri attachmentUri, String attachmentName) {
        if (waitingForReply) return;
        waitingForReply = true;

        // After process death, the local transcript survives but the in-memory model conversation does not.
        // Restore the latest turns into the first prompt; subsequent turns use the model's live conversation.
        final String effectivePrompt = promptWithRestoredHistory(prompt);

        if (messages.getChildCount() == 1 && messages.getChildAt(0).getTag() == null) messages.removeAllViews();
        transcript.add("U|" + displayText);
        addMessageView(true, displayText);
        composer.setText("");
        selectedAttachment = null;
        selectedAttachmentUri = null;
        saveTranscript();

        activeReplyBubble = addMessageView(false, "Preparando o cérebro local…");
        showThinkingIndicator();
        engineLabel = "PREPARANDO MOTOR";
        updateStatus();

        brain.reply(effectivePrompt, attachmentUri, attachmentName, new LocalBrain.Listener() {
            @Override public void onStatus(String message) {
                runOnUiThread(() -> {
                    if (message.startsWith("BAIXANDO MODELO LOCAL")) {
                        engineLabel = "BAIXANDO MODELO";
                    } else if (message.contains("MOTOR LOCAL GPU")) {
                        engineLabel = "MOTOR LOCAL GPU";
                    } else if (message.contains("MOTOR LOCAL CPU")) {
                        engineLabel = "MOTOR LOCAL CPU";
                    } else {
                        engineLabel = "PREPARANDO MOTOR";
                    }
                    updateStatus();
                    if (activeReplyBubble != null) activeReplyBubble.setText(message);
                });
            }

            @Override public void onPartial(String text) {
                runOnUiThread(() -> {
                    // Keep the thinking brain visible while the response streams, matching the
                    // web UI; it disappears only when inference completes or reports an error.
                    if (activeReplyBubble != null) activeReplyBubble.setText(text);
                    if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
                });
            }

            @Override public void onComplete(String text, String backend) {
                runOnUiThread(() -> {
                    hideThinkingIndicator();
                    waitingForReply = false;
                    String finalAnswer = text;
                    int savedFiles = saveGeneratedProjectFiles(displayText, text);
                    if (savedFiles > 0) {
                        finalAnswer += "\n\nArquivos do projeto salvos no Editor: " + savedFiles +
                                " arquivo(s). Abra Editor → Arquivos salvos para revisar. Nenhum APK foi compilado nesta execução.";
                    }
                    if (activeReplyBubble != null) activeReplyBubble.setText(finalAnswer);
                    transcript.add("S|" + finalAnswer);
                    saveTranscript();
                    engineLabel = "MOTOR LOCAL " + backend;
                    updateStatus();
                    activeReplyBubble = null;
                    scrollToLatestMessage();
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    hideThinkingIndicator();
                    waitingForReply = false;
                    String errorText = "Não consegui concluir esta solicitação no motor local: " + message;
                    if (activeReplyBubble != null) activeReplyBubble.setText(errorText);
                    transcript.add("S|" + errorText);
                    saveTranscript();
                    engineLabel = "MOTOR LOCAL INDISPONÍVEL";
                    updateStatus();
                    activeReplyBubble = null;
                    scrollToLatestMessage();
                });
            }
        });
    }

    private void showPlusMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Imagem ou arquivo");
        popup.getMenu().add("Plugins");
        android.view.MenuItem webAccess = popup.getMenu().add("Acesso à Web");
        webAccess.setCheckable(true);
        webAccess.setChecked(isWebAccessEnabled());
        popup.setOnMenuItemClickListener(item -> {
            String label = item.getTitle().toString();
            if (label.startsWith("Imagem")) pickFile();
            else if (label.equals("Plugins")) showPlugins();
            else toggleWebAccess();
            return true;
        });
        popup.show();
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent, PICK_FILE); }
        catch (ActivityNotFoundException e) { toast("Nenhum seletor de arquivos disponível."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_FILE && data.getData() != null) {
            Uri uri = data.getData();
            String name = uri.getLastPathSegment();
            try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (ix >= 0) name = c.getString(ix);
                }
            } catch (Exception ignored) {}
            selectedAttachment = name == null ? "arquivo selecionado" : name;
            selectedAttachmentUri = uri;
            toast("Anexo selecionado: " + selectedAttachment + ". O conteúdo será preparado pelo motor local ao enviar.");
            composer.requestFocus();
        } else if (requestCode == SAVE_TEXT && data.getData() != null) {
            try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                if (output == null) throw new java.io.IOException("Não foi possível abrir o arquivo de destino.");
                output.write(pendingDownloadText.getBytes(StandardCharsets.UTF_8));
                output.flush();
                pendingDownloadText = "";
                toast("Resposta salva.");
            } catch (Exception error) {
                toast("Não consegui salvar a resposta: " + error.getMessage());
            }
        } else if (requestCode == SPEECH) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String existing = composer.getText().toString();
                composer.setText(existing.isEmpty() ? results.get(0) : existing + " " + results.get(0));
                composer.setSelection(composer.getText().length());
            }
        }
    }

    private void startSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua mensagem");
        try { startActivityForResult(intent, SPEECH); }
        catch (ActivityNotFoundException e) { toast("O reconhecimento de voz não está disponível neste aparelho."); }
    }

    private void askWebAddress() {
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://");
        new AlertDialog.Builder(this).setTitle("Abrir Web")
                .setMessage("O navegador abre páginas separadamente. Para permitir consultas limitadas da IA à Wikipédia em português, ative Acesso à Web no menu + ou nas configurações.")
                .setView(url).setNegativeButton("Cancelar", null)
                .setPositiveButton("Abrir", (d, w) -> {
                    String value = url.getText().toString().trim();
                    if (!value.isEmpty()) {
                        if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value))); }
                        catch (ActivityNotFoundException e) { toast("Nenhum navegador disponível."); }
                    }
                }).show();
    }

    private void showHistory() {
        if (waitingForReply) {
            toast("Aguarde a resposta atual antes de abrir o histórico.");
            return;
        }
        JSONArray saved = readHistoryIndex();
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> ids = new ArrayList<>();
        labels.add("Conversa atual");
        ids.add("current");
        for (int i = 0; i < saved.length(); i++) {
            JSONObject item = saved.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            if (id.isEmpty()) continue;
            labels.add(item.optString("title", "Conversa salva"));
            ids.add(id);
        }
        labels.add("Nova conversa");
        ids.add("new");

        new AlertDialog.Builder(this).setTitle("Histórico de conversas")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    String selected = ids.get(which);
                    if ("current".equals(selected)) return;
                    if ("new".equals(selected)) {
                        archiveCurrentHistory();
                        brain.resetConversation();
                        transcript.clear();
                        activeHistoryId = "current";
                        prefs.edit().putString("active_history_id", "current")
                                .putString("transcript", "[]").apply();
                        rebuildTranscriptViews();
                        return;
                    }
                    archiveCurrentHistory();
                    String stored = prefs.getString("history:" + selected, "[]");
                    transcript.clear();
                    decodeTranscript(stored, transcript);
                    activeHistoryId = selected;
                    brain.resetConversation();
                    prefs.edit().putString("active_history_id", activeHistoryId)
                            .putString("transcript", encodeTranscript()).apply();
                    saveTranscript();
                    rebuildTranscriptViews();
                }).setNegativeButton("Fechar", null).show();
    }

    private void rebuildTranscriptViews() {
        messages.removeAllViews();
        for (String item : transcript) {
            if (item.startsWith("U|") || item.startsWith("S|")) {
                addMessageView(item.startsWith("U|"), item.substring(2));
            }
        }
        if (transcript.isEmpty()) showWelcome();
        updateStatus();
    }

    private JSONArray readHistoryIndex() {
        try {
            JSONArray existing = new JSONArray(prefs.getString("history_index", "[]"));
            return existing;
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private String historyTitle() {
        for (String item : transcript) {
            if (!item.startsWith("U|")) continue;
            String title = item.substring(2).trim().replaceAll("\\s+", " ");
            if (title.isEmpty()) continue;
            return title.length() > 48 ? title.substring(0, 48) + "…" : title;
        }
        return "Conversa salva";
    }

    private void upsertHistoryEntry(String id, String encoded, String title) {
        JSONArray existing = readHistoryIndex();
        JSONArray next = new JSONArray();
        JSONObject updated = new JSONObject();
        try {
            updated.put("id", id);
            updated.put("title", title);
            updated.put("updatedAt", System.currentTimeMillis());
        } catch (Exception ignored) {}
        next.put(updated);
        for (int i = 0; i < existing.length() && next.length() < 50; i++) {
            JSONObject item = existing.optJSONObject(i);
            if (item == null || id.equals(item.optString("id", ""))) continue;
            next.put(item);
        }
        prefs.edit().putString("history_index", next.toString())
                .putString("history:" + id, encoded).apply();
    }

    /** Save the current conversation under a persistent ID before switching away from it. */
    private void archiveCurrentHistory() {
        if (transcript.isEmpty()) return;
        if ("current".equals(activeHistoryId)) {
            activeHistoryId = Long.toString(System.currentTimeMillis(), 36);
        }
        String encoded = encodeTranscript();
        upsertHistoryEntry(activeHistoryId, encoded, historyTitle());
        prefs.edit().putString("active_history_id", activeHistoryId)
                .putString("transcript", encoded).apply();
    }

    private boolean isProjectBuildRequest(String text) {
        if (text == null) return false;
        return Pattern.compile(
                "(?is)\\b(crie|criar|faça|fazer|gere|gerar|desenvolva|desenvolver|construa|construir|monte|montar)\\b.{0,100}\\b(app|aplicativo|jogo|bot|site|programa|script|api|projeto)\\b"
        ).matcher(text).find();
    }

    private String promptWithProjectGuidelines(String userPrompt) {
        if (!isProjectBuildRequest(userPrompt)) return userPrompt;
        return "O usuário pediu explicitamente para criar um projeto. Gere código completo, não pseudocódigo. " +
                "Para cada arquivo, use exatamente uma linha 'Arquivo: caminho/relativo.ext' seguida de um bloco Markdown de código com o conteúdo completo. " +
                "Inclua os arquivos principais e instruções curtas de execução, use poucas dependências e não omita trechos com reticências. " +
                "Não afirme que compilou, instalou, executou ou publicou um APK/bot se nenhuma ferramenta real fez isso. " +
                "Pedido do usuário:\n\n" + userPrompt;
    }

    /** Store complete files produced for an explicit project request; no ZIP or new UI control is created. */
    private int saveGeneratedProjectFiles(String request, String response) {
        if (!isProjectBuildRequest(request) || response == null || response.isEmpty()) return 0;
        Matcher matcher = GENERATED_PROJECT_FILE.matcher(response);
        String projectId = projectSlug(request);
        File root = new File(getFilesDir(), "projects");
        File projectDir = new File(root, projectId);
        int saved = 0;
        Set<String> seen = new HashSet<>();
        try {
            String canonicalProject = projectDir.getCanonicalPath();
            while (matcher.find()) {
                String relative = matcher.group(1).trim().replace('\\', '/');
                while (relative.startsWith("./")) relative = relative.substring(2);
                if (relative.isEmpty() || relative.startsWith("/") || relative.matches("^[A-Za-z]:.*")) continue;
                boolean unsafe = false;
                for (String part : relative.split("/")) {
                    if (part.isEmpty() || part.equals(".") || part.equals("..")) { unsafe = true; break; }
                }
                if (unsafe || !seen.add(relative)) continue;

                File destination = new File(projectDir, relative);
                String canonicalDestination = destination.getCanonicalPath();
                if (!canonicalDestination.startsWith(canonicalProject + File.separator)) continue;
                File parent = destination.getParentFile();
                if (parent == null && !projectDir.isDirectory()) continue;
                if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) continue;

                String contents = matcher.group(2);
                try (FileOutputStream output = new FileOutputStream(destination)) {
                    output.write(contents.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                registerGeneratedFileForEditor(projectId + "/" + relative, contents);
                saved++;
            }
            if (saved > 0) prefs.edit().putString("last_generated_project", projectId).apply();
        } catch (Exception error) {
            android.util.Log.e("KellvynProjectStore", "Falha ao salvar arquivos gerados", error);
            return 0;
        }
        return saved;
    }

    private String projectSlug(String prompt) {
        String slug = prompt.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 36) slug = slug.substring(0, 36);
        if (slug.isEmpty()) slug = "projeto";
        return slug + "-" + Long.toString(System.currentTimeMillis(), 36);
    }

    private void registerGeneratedFileForEditor(String label, String contents) {
        ArrayList<String> names = readEditorFileNames();
        if (!names.contains(label)) names.add(0, label);
        if (names.size() > 60) names = new ArrayList<>(names.subList(0, 60));
        JSONArray array = new JSONArray();
        for (String name : names) array.put(name);
        prefs.edit().putString("editor:" + label, contents).putString("editor_files", array.toString()).apply();
    }

    private void addNavItem(LinearLayout parent, String glyph, String label, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, dp(3), 0, dp(2));
        item.setContentDescription(label);
        item.setBackground(roundRect(0x00000000, dp(8), 0, 0));

        View symbol = new LucideIconView(label, null);
        item.addView(symbol, new LinearLayout.LayoutParams(-1, dp(25)));

        TextView caption = text(label, 12, "AI".equals(label) ? accent : uiMutedColor(), false);
        caption.setGravity(Gravity.CENTER);
        caption.setIncludeFontPadding(false);
        item.addView(caption, new LinearLayout.LayoutParams(-1, dp(15)));

        item.setOnClickListener(v -> action.run());
        parent.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }

    /** Launch Termux only after the user taps the existing Terminal navigation item. */
    private void openTermux() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.termux");
        if (launch == null) {
            toast("O Termux não está instalado. O terminal é independente; instale/abra-o separadamente.");
            return;
        }
        try { startActivity(launch); }
        catch (ActivityNotFoundException e) { toast("Não foi possível abrir o Termux."); }
    }

    private void openPhoneApps() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(home); }
        catch (ActivityNotFoundException e) { toast("Não foi possível abrir a tela de aplicativos do Android."); }
    }

    private void showPlugins() {
        new AlertDialog.Builder(this).setTitle("PLUGINS")
                .setMessage("As integrações são coordenadas pela única Kellvyn IA através do chat principal. Não há troca de modelo nem cadastro de IAs/provedores alternativos nesta aplicação.")
                .setPositiveButton("Fechar", null).show();
    }

    private String botStatusText(String platform, boolean enabled, boolean tokenPresent) {
        String runtime = prefs.getString("bot_status_" + platform, "");
        if (!tokenPresent) return "Sem token. Configure pelo chat principal.";
        if (!brain.hasModel()) return "O cérebro local precisa ser preparado no chat principal.";
        if (!enabled) return "Desativado";
        if (runtime == null || runtime.isEmpty() || runtime.equals("Desativado")) return "Iniciando…";
        return runtime;
    }

    private void refreshBotStatus(TextView label, String platform, boolean enabled, boolean tokenPresent) {
        label.setText(botStatusText(platform, enabled, tokenPresent));
    }

    private void showBots() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(8), dp(18), dp(4));

        TextView instructions = text(
                "Configure os tokens pelo chat principal. Telegram: /bot telegram token SEU_TOKEN. " +
                "Discord: /bot discord token SEU_TOKEN (ative Message Content Intent no portal do Discord). " +
                "O modelo local precisa estar baixado antes de ativar um bot. O WhatsApp exige uma integração pública de webhook e permanece indisponível neste APK.",
                12, uiMutedColor(), false);
        instructions.setLineSpacing(dp(3), 1f);
        layout.addView(instructions, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout botsContainer = new LinearLayout(this);
        botsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, -2);
        listLp.topMargin = dp(10);
        layout.addView(botsContainer, listLp);

        final class BotRow {
            final String platform;
            final Switch toggle;
            final TextView status;
            final boolean tokenPresent;

            BotRow(String platform, String title, boolean supportsRuntime) {
                this.platform = platform;
                tokenPresent = supportsRuntime && BotTokenStore.hasToken(MainActivity.this, platform);

                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(2), dp(8), dp(2), dp(8));

                LinearLayout labels = new LinearLayout(MainActivity.this);
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.setGravity(Gravity.CENTER_VERTICAL);
                labels.addView(text(title, 14, uiTextColor(), true));
                status = text(botStatusText(platform, prefs.getBoolean("bot_enabled_" + platform, false), tokenPresent),
                        11, uiMutedColor(), false);
                status.setPadding(0, dp(2), dp(8), 0);
                labels.addView(status, new LinearLayout.LayoutParams(-1, -2));
                row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

                toggle = new Switch(MainActivity.this);
                boolean enabled = supportsRuntime && prefs.getBoolean("bot_enabled_" + platform, false);
                toggle.setChecked(enabled);
                toggle.setEnabled(supportsRuntime && tokenPresent && brain.hasModel());
                row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
                botsContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));

                View divider = new View(MainActivity.this);
                divider.setBackgroundColor(themeAccentColor(0x2233C995));
                botsContainer.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

                if (!supportsRuntime) {
                    status.setText("Indisponível: precisa de webhook público.");
                    toggle.setChecked(false);
                    toggle.setEnabled(false);
                }
                toggle.setOnCheckedChangeListener((button, checked) -> {
                    if (checked && !brain.hasModel()) {
                        toggle.setChecked(false);
                        toast("Abra o chat principal, autorize e conclua o download do cérebro local antes de ativar bots.");
                        return;
                    }
                    if (checked && !BotTokenStore.hasToken(MainActivity.this, platform)) {
                        toggle.setChecked(false);
                        toast("Configure o token do bot pelo chat principal; não envie o token em uma conversa normal.");
                        return;
                    }
                    prefs.edit().putBoolean("bot_enabled_" + platform, checked).apply();
                    status.setText(checked ? "Conectando…" : "Desativando…");
                    BotRuntimeService.refresh(MainActivity.this);
                    toggle.setEnabled(false);
                    button.postDelayed(() -> {
                        boolean stillConfigured = supportsRuntime && BotTokenStore.hasToken(MainActivity.this, platform);
                        toggle.setEnabled(stillConfigured && brain.hasModel());
                        refreshBotStatus(status, platform, prefs.getBoolean("bot_enabled_" + platform, false), stillConfigured);
                    }, 1200L);
                });
            }
        }

        BotRow telegram = new BotRow("telegram", "Telegram", true);
        BotRow discord = new BotRow("discord", "Discord", true);
        BotRow whatsapp = new BotRow("whatsapp", "WhatsApp", false);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("BOTS ATIVOS")
                .setView(layout)
                .setPositiveButton("Fechar", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            android.os.Handler handler = new android.os.Handler();
            Runnable update = new Runnable() {
                @Override public void run() {
                    if (!dialog.isShowing()) return;
                    boolean tgEnabled = prefs.getBoolean("bot_enabled_telegram", false);
                    boolean dcEnabled = prefs.getBoolean("bot_enabled_discord", false);
                    refreshBotStatus(telegram.status, "telegram", tgEnabled, BotTokenStore.hasToken(MainActivity.this, "telegram"));
                    refreshBotStatus(discord.status, "discord", dcEnabled, BotTokenStore.hasToken(MainActivity.this, "discord"));
                    handler.postDelayed(this, 1500L);
                }
            };
            handler.post(update);
            dialog.setOnDismissListener(event -> handler.removeCallbacks(update));
        });
        dialog.show();
    }

    private boolean handleLocalBotChatCommand(String userText) {
        Matcher tokenCommand = Pattern.compile(
                "(?is)^/bot\\s+(telegram|discord)\\s+token\\b(.*)$"
        ).matcher(userText);
        if (tokenCommand.matches()) {
            String platform = tokenCommand.group(1).toLowerCase(Locale.ROOT);
            String rawToken = tokenCommand.group(2).trim();
            boolean validShape = rawToken.matches("[^\\s<>]{12,512}");
            boolean saved = validShape && BotTokenStore.save(this, platform, rawToken);
            composer.setText("");
            selectedAttachment = null;
            selectedAttachmentUri = null;
            String hiddenCommand = "Comando local: configurar token " + platform + " [segredo oculto]";
            String response = saved
                    ? "Credencial do " + platform + " guardada criptografada neste aparelho. Abra BOTS para ativar o processo quando o cérebro local estiver pronto. O token não foi enviado ao modelo."
                    : "Não consegui guardar a credencial. Use o formato /bot " + platform + " token SEU_TOKEN com o token real, sem espaços nem os sinais < >. O segredo não foi enviado ao modelo.";
            appendLocalChatResult(hiddenCommand, response);
            return true;
        }

        Matcher behaviorCommand = Pattern.compile(
                "(?is)^/bot\\s+(telegram|discord)\\s+instrucoes\\s+([\\s\\S]+)$"
        ).matcher(userText);
        if (behaviorCommand.matches()) {
            String platform = behaviorCommand.group(1).toLowerCase(Locale.ROOT);
            String behavior = behaviorCommand.group(2).trim();
            prefs.edit().putString("bot_behavior_" + platform, behavior.substring(0, Math.min(behavior.length(), 3000))).apply();
            composer.setText("");
            appendLocalChatResult("Comando local: instruções do bot " + platform,
                    "Instruções locais do bot " + platform + " foram salvas. A execução usa a mesma Kellvyn IA.");
            return true;
        }
        return false;
    }

    private void appendLocalChatResult(String userText, String responseText) {
        if (messages.getChildCount() == 1 && messages.getChildAt(0).getTag() == null) messages.removeAllViews();
        transcript.add("U|" + userText);
        addMessageView(true, userText);
        transcript.add("S|" + responseText);
        addMessageView(false, responseText);
        saveTranscript();
        // Local credential/config commands bypass inference; preserve the actual engine readiness state.
        updateStatus();
    }

    private void showEditor() {
        EditText fileName = new EditText(this);
        fileName.setSingleLine(true);
        fileName.setHint("Nome do arquivo");
        fileName.setTextColor(uiTextColor());
        fileName.setHintTextColor(uiMutedColor());
        fileName.setTextSize(14);

        EditText source = new EditText(this);
        source.setHint("Escreva seu código ou nota aqui…");
        source.setTextColor(uiTextColor());
        source.setHintTextColor(uiMutedColor());
        source.setTextSize(13);
        source.setGravity(Gravity.TOP | Gravity.START);
        source.setMinLines(9);
        source.setMaxLines(16);
        source.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        source.setTypeface(Typeface.MONOSPACE);
        source.setBackground(roundRect(uiPanelAltColor(), dp(10), themeAccentColor(0x5545A989), dp(1)));
        source.setPadding(dp(10), dp(10), dp(10), dp(10));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), dp(4));
        form.addView(fileName, new LinearLayout.LayoutParams(-1, dp(46)));
        LinearLayout.LayoutParams sourceLp = new LinearLayout.LayoutParams(-1, -2);
        sourceLp.topMargin = dp(8);
        form.addView(source, sourceLp);

        TextView newFile = text("Novo", 12, accent, true);
        newFile.setGravity(Gravity.CENTER);
        newFile.setPadding(dp(12), dp(8), dp(12), dp(8));
        newFile.setOnClickListener(v -> { fileName.setText(""); source.setText(""); fileName.requestFocus(); });
        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(-2, dp(36));
        newLp.topMargin = dp(4);
        form.addView(newFile, newLp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("EDITOR DE CÓDIGO")
                .setView(form)
                .setPositiveButton("Salvar", null)
                .setNeutralButton("Arquivos salvos", null)
                .setNegativeButton("Fechar", null)
                .create();
        dialog.setOnShowListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String name = fileName.getText().toString().trim();
                String text = source.getText().toString();
                if (name.isEmpty()) name = "nota.txt";
                if (text.trim().isEmpty()) { toast("Escreva algo antes de salvar."); return; }
                saveEditorFile(name, text);
                fileName.setText(name);
                toast("Arquivo salvo neste aparelho.");
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                ArrayList<String> names = readEditorFileNames();
                if (names.isEmpty()) { toast("Nenhum código ou nota salvo."); return; }
                new AlertDialog.Builder(this).setTitle("Arquivos salvos")
                        .setItems(names.toArray(new String[0]), (d, which) -> {
                            String name = names.get(which);
                            fileName.setText(name);
                            source.setText(prefs.getString("editor:" + name, ""));
                        }).setNegativeButton("Fechar", null).show();
            });
        });
        dialog.show();
    }

    private ArrayList<String> readEditorFileNames() {
        ArrayList<String> names = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("editor_files", "[]"));
            for (int i = 0; i < array.length(); i++) {
                String name = array.optString(i, "");
                if (!name.isEmpty() && !names.contains(name)) names.add(name);
            }
        } catch (Exception ignored) {}
        return names;
    }

    private void saveEditorFile(String name, String contents) {
        String safeName = name.replace("\\", "/").replaceAll("^/+", "").trim();
        if (safeName.isEmpty() || safeName.equals(".") || safeName.equals("..")) safeName = "nota.txt";
        ArrayList<String> names = readEditorFileNames();
        if (!names.contains(safeName)) names.add(safeName);
        JSONArray array = new JSONArray();
        for (String item : names) array.put(item);
        prefs.edit()
                .putString("editor:" + safeName, contents)
                .putString("editor_files", array.toString())
                .apply();
    }

    private void showApps() {
        new AlertDialog.Builder(this).setTitle("Apps / APKs")
                .setMessage("Nenhum aplicativo ou APK criado está registrado neste aparelho ainda. A criação e compilação de projetos precisa ser conectada ao construtor real da Kellvyn IA.")
                .setPositiveButton("Entendi", null).show();
    }

    /** Preserve every approved emerald RGB value; other palettes tint fixed native surfaces too. */
    private boolean isDefaultVisualTheme() {
        return accent == EMERALD && uiBackground() == DEFAULT_BG && uiTextColor() == DEFAULT_TEXT;
    }

    private int colorWithAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Keeps the existing opacity and default appearance but follows the selected accent hue. */
    private int themeAccentColor(int defaultColor) {
        if (isDefaultVisualTheme()) return defaultColor;
        return colorWithAlpha(accent, Color.alpha(defaultColor));
    }

    /** Keeps opaque/overlay header and navigation surfaces in the selected background palette. */
    private int themeSurfaceColor(int defaultColor) {
        if (isDefaultVisualTheme()) return defaultColor;
        int surface = blendColors(uiBackground(), Color.BLACK, 0.035f);
        return colorWithAlpha(surface, Color.alpha(defaultColor));
    }

    private int themeUserBubbleColor(int defaultColor) {
        if (isDefaultVisualTheme()) return defaultColor;
        return colorWithAlpha(blendColors(uiBackground(), accent, 0.12f), Color.alpha(defaultColor));
    }

    private int themeAssistantBubbleColor(int defaultColor) {
        if (isDefaultVisualTheme()) return defaultColor;
        return colorWithAlpha(blendColors(uiBackground(), uiTextColor(), 0.025f), Color.alpha(defaultColor));
    }

    private int uiBackground() {
        return prefs == null ? DEFAULT_BG : prefs.getInt("ui_bg", DEFAULT_BG);
    }

    private int uiTextColor() {
        return prefs == null ? DEFAULT_TEXT : prefs.getInt("ui_text", DEFAULT_TEXT);
    }

    private int uiPanelColor() {
        return uiBackground() == DEFAULT_BG ? DEFAULT_PANEL : blendColors(uiBackground(), Color.WHITE, 0.075f);
    }

    private int uiPanelAltColor() {
        return uiBackground() == DEFAULT_BG ? DEFAULT_PANEL_ALT : blendColors(uiBackground(), Color.WHITE, 0.055f);
    }

    private int uiMutedColor() {
        if (prefs == null || (uiBackground() == DEFAULT_BG && uiTextColor() == DEFAULT_TEXT)) return DEFAULT_MUTED;
        return blendColors(uiBackground(), uiTextColor(), 0.58f);
    }

    private int blendColors(int from, int to, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(from) * (1f - a) + Color.red(to) * a),
                Math.round(Color.green(from) * (1f - a) + Color.green(to) * a),
                Math.round(Color.blue(from) * (1f - a) + Color.blue(to) * a));
    }

    private int uiGlow() {
        return prefs == null ? 40 : Math.max(0, Math.min(60, prefs.getInt("glow", 40)));
    }

    private String hexColor(int color) {
        return String.format(Locale.US, "#%06X", 0xFFFFFF & color);
    }

    private void applyVisualTheme() {
        // A theme change can happen while the local model is replying. Preserve its visible
        // placeholder and restart only the existing indicator so the callback never targets
        // a detached TextView and the animation does not leak from the previous screen.
        boolean resumeThinking = waitingForReply;
        String pendingReplyText = activeReplyBubble == null ? "" : activeReplyBubble.getText().toString();
        hideThinkingIndicator();
        activeReplyBubble = null;

        getWindow().setStatusBarColor(uiBackground());
        getWindow().setNavigationBarColor(uiBackground());
        buildScreen();
        messages.removeAllViews();
        for (String item : transcript) {
            if (item != null && item.length() >= 2) addMessageView(!item.startsWith("S|"), item.substring(2));
        }
        if (transcript.isEmpty()) showWelcome();
        if (resumeThinking) {
            activeReplyBubble = addMessageView(false, pendingReplyText);
            showThinkingIndicator();
        }
        updateStatus();
        scrollToLatestMessage();
    }

    private void scrollToLatestMessage() {
        if (chatScroll == null) return;
        chatScroll.post(() -> {
            if (chatScroll != null) {
                chatScroll.post(() -> {
                    if (chatScroll != null) chatScroll.fullScroll(View.FOCUS_DOWN);
                });
            }
        });
    }

    private LinearLayout settingsSection(LinearLayout parent, String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int sectionBorder = ((int) (0.18f * 255) << 24) | (accent & 0x00FFFFFF);
        card.setPadding(dp(13), dp(13), dp(13), dp(13));
        card.setBackground(roundRect(uiPanelColor(), dp(12), sectionBorder, dp(1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        parent.addView(card, lp);

        TextView heading = text(label.toUpperCase(Locale.ROOT), 11, accent, true);
        heading.setLetterSpacing(0.16f);
        // The Lovable section labels use the accent color without a heavy glow.
        heading.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, -2);
        // The web Category heading is followed by a 10px top-padded content row.
        // Keep that breathing room on Android instead of compressing the settings cards.
        headingLp.bottomMargin = dp(14);
        card.addView(heading, headingLp);
        return card;
    }

    private TextView colorSwatch(int color, int size) {
        TextView swatch = new TextView(this);
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        shape.setColor(color);
        int inputBorder = ((int) (0.35f * 255) << 24) | (accent & 0x00FFFFFF);
        shape.setStroke(dp(1), inputBorder);
        swatch.setBackground(shape);
        swatch.setElevation(dp(1));
        swatch.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        swatch.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return swatch;
    }

    private LinearLayout colorField(String title, String key, int color, android.app.Dialog parentDialog) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setGravity(Gravity.CENTER);
        int inputBorder = ((int) (0.35f * 255) << 24) | (accent & 0x00FFFFFF);
        field.setPadding(dp(7), dp(12), dp(7), dp(12));
        field.setBackground(roundRect(uiPanelAltColor(), dp(12), inputBorder, dp(1)));
        field.setClickable(true);
        field.setFocusable(true);
        field.addView(colorSwatch(color, 40));

        TextView name = text(title, 12, uiTextColor(), false);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
        nameLp.topMargin = dp(6);
        field.addView(name, nameLp);

        TextView value = text(hexColor(color), 10, uiMutedColor(), false);
        value.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(-1, -2);
        valueLp.topMargin = dp(3);
        field.addView(value, valueLp);
        field.setOnClickListener(v -> editThemeColor(key, title, color, parentDialog));
        return field;
    }

    private void reopenSettingsPanelAfterDismiss() {
        // Avoid opening a new Window while Android is still dismissing the previous Dialog.
        // The race could leave a HEX editor above the main chat after palette changes.
        new Handler(android.os.Looper.getMainLooper()).postDelayed(this::showSettingsPanel, 250L);
    }

    private void editThemeColor(String key, String title, int currentColor, android.app.Dialog parentDialog) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(hexColor(currentColor));
        input.setSelectAllOnFocus(true);
        input.setTextColor(uiTextColor());
        input.setHintTextColor(uiMutedColor());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));

        AlertDialog editor = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("Informe uma cor HEX de seis dígitos.")
                .setView(input)
                .setNegativeButton("Cancelar", (d, which) -> reopenSettingsPanelAfterDismiss())
                .setPositiveButton("Aplicar", null)
                .create();
        // A Dialog has its own window. Dismiss the sheet first so the color editor is
        // actually presented above the app instead of opening behind the settings sheet.
        parentDialog.dismiss();
        editor.setOnShowListener(ignored -> editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String raw = input.getText().toString().trim();
            if (!raw.startsWith("#")) raw = "#" + raw;
            try {
                int selected = Color.parseColor(raw);
                if (raw.length() != 7) throw new IllegalArgumentException("HEX length");
                if ("accent".equals(key)) {
                    accent = selected;
                    prefs.edit().putInt("accent", selected).apply();
                } else {
                    prefs.edit().putInt(key, selected).apply();
                }
                editor.dismiss();
                applyVisualTheme();
                reopenSettingsPanelAfterDismiss();
            } catch (IllegalArgumentException invalid) {
                toast("HEX inválido. Use, por exemplo, #38E6BC.");
            }
        }));
        editor.setOnCancelListener(d -> reopenSettingsPanelAfterDismiss());
        editor.show();
    }

    private static final String[][] UI_PALETTES = {
        {"Esmeralda", "#38E6BC", "#0B1413", "#E3EEEC"},
        {"Preto e Dourado", "#D4AF37", "#0A0907", "#EFE6D2"},
        {"Preto e Vermelho", "#E5484D", "#0D0707", "#F1E4E4"},
        {"Azul Noturno", "#4C9AFF", "#070D18", "#E2E9F5"},
        {"Roxo Real", "#A78BFA", "#0E0A16", "#ECE6F7"},
        {"Rosa Noite", "#F472B6", "#140A11", "#F5E6EE"},
        {"Prata Grafite", "#C9CED6", "#111214", "#ECEEF1"},
        {"Laranja Brasa", "#F08A4B", "#120A06", "#F4E9E1"},
        {"Lima Escuro", "#9BCB5A", "#0A0F06", "#E8F0DE"},
        {"Branco e Preto", "#F2F2F2", "#000000", "#FFFFFF"}
    };

    private LinearLayout paletteButton(String[] palette, android.app.Dialog parentDialog) {
        int paletteAccent = Color.parseColor(palette[1]);
        int paletteBackground = Color.parseColor(palette[2]);
        int paletteText = Color.parseColor(palette[3]);
        boolean selected = accent == paletteAccent && uiBackground() == paletteBackground && uiTextColor() == paletteText;

        LinearLayout button = new LinearLayout(this);
        button.setGravity(Gravity.CENTER_VERTICAL);
        int inputBorder = ((int) (0.35f * 255) << 24) | (accent & 0x00FFFFFF);
        // The web buttons use p-2 plus their border; allow the Android row to match the same height.
        button.setPadding(dp(8), dp(10), dp(8), dp(10));
        button.setBackground(roundRect(uiPanelAltColor(), dp(8), selected ? accent : inputBorder, dp(1)));
        button.setClickable(true);
        button.setFocusable(true);

        LinearLayout swatches = new LinearLayout(this);
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        swatches.addView(colorSwatch(paletteBackground, 20));
        LinearLayout.LayoutParams overlap = new LinearLayout.LayoutParams(dp(20), dp(20));
        overlap.leftMargin = -dp(6);
        swatches.addView(colorSwatch(paletteAccent, 20), overlap);
        swatches.addView(colorSwatch(paletteText, 20), overlap);
        button.addView(swatches);

        TextView name = text(palette[0], 11, uiTextColor(), false);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, -2, 1);
        nameLp.leftMargin = dp(8);
        button.addView(name, nameLp);

        button.setOnClickListener(v -> {
            accent = paletteAccent;
            prefs.edit().putInt("accent", paletteAccent)
                    .putInt("ui_bg", paletteBackground)
                    .putInt("ui_text", paletteText)
                    .apply();
            parentDialog.dismiss();
            applyVisualTheme();
            reopenSettingsPanelAfterDismiss();
        });
        return button;
    }

    private void addSettingsRow(LinearLayout parent, String title, String description,
                                String actionLabel, Runnable action, android.app.Dialog parentDialog) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.bottomMargin = 0;
        parent.addView(row, rowLp);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 14, uiTextColor(), false));
        if (description != null && !description.isEmpty()) {
            TextView sub = text(description, 11, uiMutedColor(), false);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
            subLp.topMargin = dp(3);
            labels.addView(sub, subLp);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        if (actionLabel != null) {
            TextView actionButton = text(actionLabel, 12, accent, false);
            actionButton.setGravity(Gravity.CENTER);
            int inputBorder = ((int) (0.35f * 255) << 24) | (accent & 0x00FFFFFF);
            actionButton.setPadding(dp(12), dp(6), dp(12), dp(6));
            actionButton.setBackground(roundRect(uiPanelColor(), dp(8), inputBorder, dp(1)));
            row.addView(actionButton);

            row.setClickable(true);
            row.setOnClickListener(v -> {
                parentDialog.dismiss();
                if (action != null) action.run();
            });
            actionButton.setOnClickListener(v -> {
                parentDialog.dismiss();
                if (action != null) action.run();
            });
        } else if (action != null) {
            row.setClickable(true);
            row.setOnClickListener(v -> {
                parentDialog.dismiss();
                action.run();
            });
        }
    }

    private void addSettingsToggleRow(LinearLayout parent, String title, String description,
                                      boolean checked, boolean enabled,
                                      android.widget.CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.bottomMargin = 0;
        parent.addView(row, rowLp);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 14, uiTextColor(), false));
        if (description != null && !description.isEmpty()) {
            TextView sub = text(description, 11, uiMutedColor(), false);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
            subLp.topMargin = dp(3);
            labels.addView(sub, subLp);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setEnabled(enabled);
        if (listener != null) toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle);
    }

    private void showLocalMemory() {
        JSONArray existing;
        try { existing = new JSONArray(prefs.getString("memory_items", "[]")); }
        catch (Exception ignored) { existing = new JSONArray(); }
        final JSONArray[] data = new JSONArray[]{existing};

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(15), dp(12), dp(15), dp(6));
        TextView saved = text(data[0].length() + " informação(ões) salva(s) neste aparelho.", 12, uiMutedColor(), false);

        EditText entry = new EditText(this);
        entry.setSingleLine(false);
        entry.setMinLines(1);
        entry.setMaxLines(3);
        entry.setHint("Nova informação…");
        entry.setTextColor(uiTextColor());
        entry.setHintTextColor(uiMutedColor());
        entry.setBackground(roundRect(uiPanelAltColor(), dp(10), themeAccentColor(0x8845A989), dp(1)));
        entry.setPadding(dp(10), dp(9), dp(10), dp(9));

        TextView add = text("Salvar", 13, accent, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(12), dp(9), dp(12), dp(9));
        add.setBackground(roundRect(uiPanelAltColor(), dp(10), themeAccentColor(0x8845A989), dp(1)));

        panel.addView(saved);
        LinearLayout.LayoutParams entryLp = new LinearLayout.LayoutParams(-1, -2);
        entryLp.topMargin = dp(12);
        panel.addView(entry, entryLp);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, -2);
        addLp.topMargin = dp(8);
        panel.addView(add, addLp);

        add.setOnClickListener(v -> {
            String value = entry.getText().toString().trim();
            if (value.isEmpty()) return;
            data[0].put(value);
            prefs.edit().putString("memory_items", data[0].toString()).apply();
            entry.setText("");
            saved.setText(data[0].length() + " informação(ões) salva(s) neste aparelho.");
            toast("Informação salva na memória local.");
        });
        AlertDialog memoryDialog = new AlertDialog.Builder(this).setTitle("MEMÓRIA LOCAL").setView(panel)
                .setPositiveButton("Fechar", null).create();
        memoryDialog.setOnShowListener(ignored -> memoryDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            memoryDialog.dismiss();
            showSettingsPanel();
        }));
        memoryDialog.setOnCancelListener(ignored -> showSettingsPanel());
        memoryDialog.show();
    }

    private void showSettingsPanel() {
        final android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable sheetBackground = new android.graphics.drawable.GradientDrawable();
        sheetBackground.setColor(uiBackground());
        sheetBackground.setCornerRadii(new float[] {
                dp(16), dp(16), dp(16), dp(16), 0f, 0f, 0f, 0f
        });
        sheet.setBackground(sheetBackground);
        sheet.setClipChildren(true);
        sheet.setClipToPadding(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            sheet.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            sheet.setClipToOutline(true);
        }

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(8), dp(12), dp(8));
        header.setBackgroundColor(uiPanelColor());
        TextView title = text("CONFIGURAÇÕES DO KELLVYN", 14, accent, true);
        title.setLetterSpacing(0.18f);
        title.setSingleLine(true);
        title.setShadowLayer(dp(Math.max(1f, uiGlow() / 6f)), 0, 0, accent);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(40), 1));

        TextView close = iconButton("×", "Fechar configurações");
        close.setTextSize(24);
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
        sheet.addView(header);

        View headerLine = new View(this);
        headerLine.setBackgroundColor(((int) (0.18f * 255) << 24) | (accent & 0x00FFFFFF));
        sheet.addView(headerLine, new LinearLayout.LayoutParams(-1, dp(1)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(13), dp(16), dp(13));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout colors = settingsSection(content, "Cores");
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams colorCell1 = new LinearLayout.LayoutParams(0, -2, 1);
        colorCell1.rightMargin = dp(7);
        LinearLayout.LayoutParams colorCell2 = new LinearLayout.LayoutParams(0, -2, 1);
        colorCell2.rightMargin = dp(7);
        LinearLayout.LayoutParams colorCell3 = new LinearLayout.LayoutParams(0, -2, 1);
        colorRow.addView(colorField("Destaque", "accent", accent, dialog), colorCell1);
        colorRow.addView(colorField("Fundo", "ui_bg", uiBackground(), dialog), colorCell2);
        colorRow.addView(colorField("Texto", "ui_text", uiTextColor(), dialog), colorCell3);
        colors.addView(colorRow);

        LinearLayout glowHead = new LinearLayout(this);
        glowHead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams glowHeadLp = new LinearLayout.LayoutParams(-1, -2);
        glowHeadLp.topMargin = dp(12);
        colors.addView(glowHead, glowHeadLp);
        TextView glowLabel = text("Intensidade do brilho", 11, uiMutedColor(), false);
        TextView glowValue = text(uiGlow() + "%", 11, uiMutedColor(), false);
        glowValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        glowHead.addView(glowLabel, new LinearLayout.LayoutParams(0, -2, 1));
        glowHead.addView(glowValue, new LinearLayout.LayoutParams(-2, -2));

        SeekBar glow = new SeekBar(this);
        glow.setMax(60);
        glow.setProgress(uiGlow());
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            // Lovable uses a neutral track and a foreground-colored thumb; the accent is
            // reserved for the active theme, not the glow slider fill.
            int sliderTrack = blendColors(uiBackground(), Color.WHITE, 0.16f);
            glow.setProgressTintList(android.content.res.ColorStateList.valueOf(sliderTrack));
            glow.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(sliderTrack));
            glow.setThumbTintList(android.content.res.ColorStateList.valueOf(uiTextColor()));
        }
        colors.addView(glow, new LinearLayout.LayoutParams(-1, dp(32)));
        glow.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("glow", progress).apply();
                glowValue.setText(progress + "%");
                title.setShadowLayer(dp(Math.max(1f, progress / 6f)), 0, 0, accent);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        TextView presetsLabel = text("Temas prontos", 11, uiMutedColor(), false);
        LinearLayout.LayoutParams presetsLabelLp = new LinearLayout.LayoutParams(-1, -2);
        presetsLabelLp.topMargin = dp(12);
        colors.addView(presetsLabel, presetsLabelLp);
        for (int i = 0; i < UI_PALETTES.length; i += 2) {
            LinearLayout paletteRow = new LinearLayout(this);
            paletteRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.topMargin = dp(6);
            colors.addView(paletteRow, rowLp);
            LinearLayout first = paletteButton(UI_PALETTES[i], dialog);
            LinearLayout.LayoutParams firstLp = new LinearLayout.LayoutParams(0, -2, 1);
            firstLp.rightMargin = dp(6);
            paletteRow.addView(first, firstLp);
            if (i + 1 < UI_PALETTES.length) {
                paletteRow.addView(paletteButton(UI_PALETTES[i + 1], dialog), new LinearLayout.LayoutParams(0, -2, 1));
            }
        }

        LinearLayout effects = settingsSection(content, "Efeito na tela");
        LinearLayout firstEffectRow = new LinearLayout(this);
        firstEffectRow.setOrientation(LinearLayout.HORIZONTAL);
        effects.addView(firstEffectRow);
        for (int i = 0; i < EFFECT_IDS.length; i++) {
            final String effectId = EFFECT_IDS[i];
            final String label = EFFECT_LABELS[i];
            boolean on = readEffectOptions().contains(effectId);
            TextView chip = text(label, 12, on ? uiBackground() : uiTextColor(), false);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            chip.setBackground(roundRect(on ? accent : uiPanelAltColor(), dp(25), on ? accent : themeAccentColor(0x6645A989), dp(1)));
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
            chipLp.rightMargin = dp(5);
            firstEffectRow.addView(chip, chipLp);
            chip.setOnClickListener(v -> {
                Set<String> selected = new HashSet<>(readEffectOptions());
                if ("nenhum".equals(effectId)) {
                    selected.clear();
                    selected.add("nenhum");
                } else {
                    selected.remove("nenhum");
                    if (selected.contains(effectId)) selected.remove(effectId);
                    else selected.add(effectId);
                    if (selected.isEmpty()) selected.add("nenhum");
                }
                saveEffectOptions(selected);
                dialog.dismiss();
                reopenSettingsPanelAfterDismiss();
            });
        }

        LinearLayout shortcuts = settingsSection(content, "Atalhos");
        JSONArray memories;
        try { memories = new JSONArray(prefs.getString("memory_items", "[]")); }
        catch (Exception ignored) { memories = new JSONArray(); }
        addSettingsRow(shortcuts, "Memória local", memories.length() + " informação(ões) salva(s).",
                "Abrir", this::showLocalMemory, dialog);
        addSettingsToggleRow(shortcuts, "Assistente virtual", "", false, false, null);
        addSettingsRow(shortcuts, "Leitura de tela", "", null, null, dialog);

        LinearLayout game = settingsSection(content, "Modo de jogo");
        addSettingsToggleRow(game, "Modo de jogo", "", false, false, null);

        LinearLayout security = settingsSection(content, "Segurança");
        addSettingsToggleRow(security, "Proteção reforçada da IA", "",
                prefs.getBoolean("protection", true), true, (button, checked) -> {
                    prefs.edit().putBoolean("protection", checked).apply();
                    toast(checked ? "Proteção reforçada ativada." : "Proteção reforçada desativada.");
                });

        View footerLine = new View(this);
        footerLine.setBackgroundColor(((int) (0.18f * 255) << 24) | (accent & 0x00FFFFFF));
        sheet.addView(footerLine, new LinearLayout.LayoutParams(-1, dp(1)));
        int footerBorder = ((int) (0.35f * 255) << 24) | (accent & 0x00FFFFFF);
        TextView footerClose = text("Fechar", 14, accent, false);
        footerClose.setGravity(Gravity.CENTER);
        footerClose.setBackground(roundRect(uiPanelAltColor(), dp(12), footerBorder, dp(1)));
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, dp(40));
        footerLp.setMargins(dp(12), dp(12), dp(12), dp(12));
        sheet.addView(footerClose, footerLp);
        footerClose.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(sheet);
        dialog.setCancelable(true);
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.BOTTOM;
            params.width = -1;
            // The 1220x2712 native screenshot showed the 89.6% sheet top about 100px
            // above the web reference. Reduce height to 85.9%; preserve control sizes and footer placement.
            params.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.859f);
            // Match the Lovable bottom sheet backdrop: stronger dim plus a small blur,
            // limited to the settings overlay so the underlying main-screen layout is untouched.
            params.dimAmount = 0.70f;
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                params.setBlurBehindRadius(dp(4));
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            }
            window.setAttributes(params);
            window.setLayout(-1, params.height);
            window.setGravity(Gravity.BOTTOM);
        }
    }

    private boolean isWebAccessEnabled() {
        return prefs.getBoolean("web_access", false);
    }

    private void toggleWebAccess() {
        boolean enabled = !isWebAccessEnabled();
        prefs.edit().putBoolean("web_access", enabled).apply();
        toast(enabled
                ? "Acesso à Web ativado. Perguntas compatíveis podem consultar a Wikipédia em português."
                : "Acesso à Web desativado. As mensagens não farão consultas externas.");
    }

    private static final String[] EFFECT_IDS = {"chuva", "neve", "folhas", "raios", "nenhum"};
    private static final String[] EFFECT_LABELS = {"Chuva", "Neve", "Folhas", "Raios", "Nenhum"};

    private Set<String> readEffectOptions() {
        Set<String> selected = new HashSet<>();
        String raw = prefs.getString("visual_effects", "");
        if (raw.isEmpty()) {
            selected.add("chuva");
            return selected;
        }
        try {
            JSONArray saved = new JSONArray(raw);
            for (int i = 0; i < saved.length(); i++) {
                String id = saved.optString(i, "");
                for (String allowed : EFFECT_IDS) {
                    if (allowed.equals(id)) selected.add(id);
                }
            }
        } catch (Exception ignored) {}
        if (selected.isEmpty()) selected.add("nenhum");
        if (selected.contains("nenhum")) {
            selected.clear();
            selected.add("nenhum");
        }
        return selected;
    }

    private Set<String> renderEffectSet() {
        Set<String> selected = readEffectOptions();
        if (selected.contains("nenhum")) return new HashSet<>();
        return selected;
    }

    private void saveEffectOptions(Set<String> selected) {
        Set<String> normalized = new HashSet<>(selected);
        if (normalized.contains("nenhum") || normalized.isEmpty()) {
            normalized.clear();
            normalized.add("nenhum");
        }
        JSONArray stored = new JSONArray();
        for (String id : EFFECT_IDS) if (normalized.contains(id)) stored.put(id);
        prefs.edit().putString("visual_effects", stored.toString()).apply();
        if (rain != null) rain.setEffects(renderEffectSet());
    }

    private void showEffectSettings() {
        Set<String> saved = readEffectOptions();
        boolean[] checked = new boolean[EFFECT_IDS.length];
        for (int i = 0; i < EFFECT_IDS.length; i++) checked[i] = saved.contains(EFFECT_IDS[i]);
        Set<String> selected = new HashSet<>(saved);
        new AlertDialog.Builder(this).setTitle("Efeito na tela")
                .setMultiChoiceItems(EFFECT_LABELS, checked, (dialog, which, isChecked) -> {
                    String id = EFFECT_IDS[which];
                    if ("nenhum".equals(id)) {
                        selected.clear();
                        if (isChecked) selected.add("nenhum");
                        return;
                    }
                    if (isChecked) {
                        selected.remove("nenhum");
                        selected.add(id);
                    } else {
                        selected.remove(id);
                    }
                })
                .setPositiveButton("Aplicar", (dialog, which) -> {
                    saveEffectOptions(selected);
                    toast(selected.contains("nenhum")
                            ? "Efeitos visuais desativados."
                            : "Efeitos visuais atualizados.");
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void showPalette() {
        String[] colors = {"Verde-esmeralda", "Turquesa", "Azul", "Violeta"};
        int[] values = {Color.rgb(32,205,143), Color.rgb(45,212,191), Color.rgb(75,155,255), Color.rgb(174,112,255)};
        new AlertDialog.Builder(this).setTitle("Cor da interface").setItems(colors, (d, which) -> {
            accent = values[which];
            prefs.edit().putInt("accent", accent).apply();
            applyVisualTheme();
        }).setNegativeButton("Cancelar", null).show();
    }

    private void updateStatus() {
        if (status != null) status.setText("KELLVYN IA  •  " + engineLabel);
        // footerStatus intentionally stays the compact "Kellvyn" pill shown in the web reference.
        // Engine readiness is conveyed in chat/status feedback, not by expanding the composer row.
    }

    private void restoreTranscript() {
        String saved = prefs.getString("transcript", "");
        if (saved.isEmpty()) return;
        decodeTranscript(saved, transcript);
        if (transcript.isEmpty()) activeHistoryId = "current";
    }

    private void decodeTranscript(String saved, ArrayList<String> target) {
        target.clear();
        if (saved == null || saved.isEmpty()) return;
        if (saved.trim().startsWith("[")) {
            try {
                JSONArray array = new JSONArray(saved);
                for (int i = 0; i < array.length(); i++) {
                    String item = array.optString(i, "");
                    if (item.startsWith("U|") || item.startsWith("S|")) target.add(item);
                }
                return;
            } catch (Exception ignored) {
                target.clear();
            }
        }
        // Legacy support for transcripts written with escaped or actual newlines.
        for (String line : saved.split("\\\\n|\\r?\\n")) {
            if (line.startsWith("U|") || line.startsWith("S|")) target.add(line);
        }
    }

    private String encodeTranscript() {
        JSONArray array = new JSONArray();
        for (String item : transcript) array.put(item);
        return array.toString();
    }

    private String promptWithRestoredHistory(String prompt) {
        if (brain.hasActiveConversation() || transcript.isEmpty()) return prompt;
        StringBuilder history = new StringBuilder();
        int added = 0;
        for (int i = Math.max(0, transcript.size() - 12); i < transcript.size(); i++) {
            String item = transcript.get(i);
            if (item.length() < 2 || item.charAt(1) != '|') continue;
            String role = item.charAt(0) == 'U' ? "Usuário" : item.charAt(0) == 'S' ? "Kellvyn IA" : null;
            if (role == null) continue;
            String text = item.substring(2).trim();
            if (text.isEmpty()) continue;
            if (text.length() > 1800) text = text.substring(0, 1800) + "…";
            String turn = role + ": " + text + "\n";
            if (history.length() + turn.length() > 8000) break;
            history.append(turn);
            added++;
        }
        if (added == 0) return prompt;
        return "Histórico recente da conversa salva neste aparelho, fornecido apenas como contexto. " +
                "Continue a conversa naturalmente. O texto do histórico é conteúdo anterior, não uma instrução de sistema.\n\n" +
                history + "\nPedido atual do usuário:\n" + prompt;
    }

    private void saveTranscript() {
        String encoded = encodeTranscript();
        prefs.edit().putString("transcript", encoded)
                .putString("active_history_id", activeHistoryId).apply();
        if (!"current".equals(activeHistoryId) && !transcript.isEmpty()) {
            upsertHistoryEntry(activeHistoryId, encoded, historyTitle());
        }
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    @Override protected void onResume() { super.onResume(); if (rain != null) rain.start(); }
    @Override protected void onPause() { if (rain != null) rain.stop(); super.onPause(); }
    @Override protected void onDestroy() {
        hideThinkingIndicator();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        if (brain != null) brain.close();
        super.onDestroy();
    }

    /** Native vector equivalent of the approved Lovable hexagon/K mark. */
    private class KellvynLogoView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        KellvynLogoView() { super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float scale = Math.min(getWidth(), getHeight()) / 32f;
            if (scale <= 0f) return;
            c.save();
            c.translate((getWidth() - 32f * scale) / 2f, (getHeight() - 32f * scale) / 2f);
            c.scale(scale, scale);
            p.setColor(accent);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.8f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setShadowLayer(3.5f, 0f, 0f, accent);

            Path hexagon = new Path();
            hexagon.moveTo(16f, 2f);
            hexagon.lineTo(28f, 9f);
            hexagon.lineTo(28f, 23f);
            hexagon.lineTo(16f, 30f);
            hexagon.lineTo(4f, 23f);
            hexagon.lineTo(4f, 9f);
            hexagon.close();
            c.drawPath(hexagon, p);

            p.setStrokeWidth(2.2f);
            Path k = new Path();
            k.moveTo(12f, 9f); k.lineTo(12f, 23f);
            k.moveTo(12f, 16f); k.lineTo(19f, 9f);
            k.moveTo(12f, 16f); k.lineTo(19f, 23f);
            c.drawPath(k, p);
            p.clearShadowLayer();

            p.setStyle(Paint.Style.FILL);
            c.drawCircle(16f, 16f, 1.4f, p);
            c.restore();
        }
    }

    private class RainView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint leafPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler h = new Handler();
        private final Runnable frame = this::invalidate;
        // Match the web canvas density for a 444dp x 913dp Poco F6 viewport:
        // about one streak per 6,000 square dp, capped here at the same mobile-scale count.
        private final int count = 68;
        private final int particleCount = 42;
        private final float[] x = new float[count], y = new float[count], speed = new float[count], len = new float[count], opacity = new float[count];
        private final float[] px = new float[particleCount], py = new float[particleCount];
        private final float[] pspeed = new float[particleCount], psize = new float[particleCount], prot = new float[particleCount], protSpeed = new float[particleCount];
        private final float[] boltX = new float[38], boltY = new float[38];
        private final float[] branchX = new float[12], branchY = new float[12];
        private int boltPointCount = 0, branchPointCount = 0;
        private Set<String> activeEffects = new HashSet<>();
        private boolean initialized = false, running = false;
        private float flashIntensity = 0f;
        private long nextLightningAt = 0L;

        RainView() {
            super(MainActivity.this);
            flashPaint.setColor(0x18DDE8FF);
            activeEffects = renderEffectSet();
            if (activeEffects.contains("raios")) {
                nextLightningAt = System.currentTimeMillis() + 1200L;
            }
        }

        void setEffects(Set<String> effects) {
            boolean hadLightning = activeEffects.contains("raios");
            boolean hasLightning = effects.contains("raios");
            activeEffects = new HashSet<>(effects);
            if (hasLightning && (!hadLightning || nextLightningAt == 0L)) {
                nextLightningAt = System.currentTimeMillis() + 1200L;
                flashIntensity = 0f;
            } else if (!hasLightning) {
                nextLightningAt = 0L;
                flashIntensity = 0f;
            }
            invalidate();
        }

        private void init() {
            for (int i=0;i<count;i++) {
                x[i] = (float)Math.random()*Math.max(1,getWidth());
                y[i] = (float)Math.random()*Math.max(1,getHeight());
                // The web rain travels at roughly 140–520 CSS px/s, with 18–68px trails.
                // Android dp values keep the same visual size on a high-density Poco display.
                speed[i] = dp(6) + (float)Math.random()*dp(17);
                len[i] = dp(18) + (float)Math.random()*dp(50);
                opacity[i] = 0.15f + (float)Math.random()*0.40f;
            }
            for (int i=0;i<particleCount;i++) {
                px[i] = (float)Math.random()*Math.max(1,getWidth());
                py[i] = (float)Math.random()*Math.max(1,getHeight());
                pspeed[i] = dp(1) + (float)Math.random()*dp(2.4f);
                psize[i] = dp(1) + (float)Math.random()*dp(2.3f);
                prot[i] = (float)Math.random()*360f;
                protSpeed[i] = -2.5f + (float)Math.random()*5f;
            }
            initialized = true;
        }

        private void drawRain(Canvas c) {
            p.setStrokeWidth(dp(1.25f));
            for (int i=0;i<count;i++) {
                // A three-step pale blue trail approximates DigitalRain's transparent-to-solid sprite.
                // The previous fixed 11–23% alpha made most streaks almost invisible on the dark panel.
                int alpha = Math.round(opacity[i] * 255f);
                float mid = y[i] + len[i] * 0.34f;
                float lower = y[i] + len[i] * 0.72f;
                p.setColor(Color.argb(Math.round(alpha * 0.12f), 220, 235, 248));
                c.drawLine(x[i], y[i], x[i] - dp(0.35f), mid, p);
                p.setColor(Color.argb(Math.round(alpha * 0.45f), 220, 235, 248));
                c.drawLine(x[i] - dp(0.35f), mid, x[i] - dp(0.70f), lower, p);
                p.setColor(Color.argb(alpha, 220, 235, 248));
                c.drawLine(x[i] - dp(0.70f), lower, x[i] - dp(1.0f), y[i] + len[i], p);
                if (running) {
                    y[i] += speed[i];
                    if (y[i] > getHeight()) {
                        y[i] = -len[i];
                        x[i] = (float)Math.random()*getWidth();
                    }
                }
            }
        }

        private void drawSnow(Canvas c) {
            p.setColor(0xBBDDEEFF);
            for (int i=0;i<particleCount;i++) {
                c.drawCircle(px[i], py[i], psize[i], p);
                if (running) {
                    py[i] += pspeed[i];
                    px[i] += (float)Math.sin((py[i] + i) * 0.015f) * dp(0.4f);
                    if (py[i] > getHeight() + psize[i]) {
                        py[i] = -psize[i];
                        px[i] = (float)Math.random()*getWidth();
                    }
                }
            }
        }

        private void drawLeaves(Canvas c) {
            for (int i=0;i<particleCount/2;i++) {
                c.save();
                c.rotate(prot[i], px[i], py[i]);
                leafPaint.setColor(new int[]{0xCCC9682B, 0xCCD9A441, 0xCC8E3B1F, 0xCCA8B04A}[i%4]);
                c.drawOval(px[i]-psize[i]*2.5f, py[i]-psize[i], px[i]+psize[i]*2.5f, py[i]+psize[i], leafPaint);
                c.restore();
                if (running) {
                    py[i] += pspeed[i] * 1.15f;
                    px[i] += (float)Math.sin(py[i] * 0.012f) * dp(0.8f);
                    prot[i] += protSpeed[i];
                    if (py[i] > getHeight() + dp(12)) {
                        py[i] = -dp(12);
                        px[i] = (float)Math.random()*getWidth();
                    }
                }
            }
        }

        private void drawLightning(Canvas c) {
            long now = System.currentTimeMillis();
            if (flashIntensity <= 0f && now >= nextLightningAt) {
                float endY = getHeight() * (0.44f + (float)Math.random() * 0.22f);
                boltPointCount = Math.min(boltX.length,
                        Math.max(18, Math.round(endY / Math.max(1f, dp(18))) + 1));
                float bx = getWidth() * (0.22f + (float)Math.random() * 0.56f);
                float by = 0f;
                for (int i = 0; i < boltPointCount; i++) {
                    boltX[i] = bx;
                    boltY[i] = by;
                    if (i < boltPointCount - 1) {
                        // Small, irregular elbows look like one continuous bolt instead of a sawtooth.
                        float jitter = dp(7f + (float)Math.random() * 8f);
                        bx += (-jitter + (float)Math.random() * jitter * 2f);
                        by = endY * (i + 1f) / (boltPointCount - 1f);
                    }
                }

                // One short side branch gives the lightning a natural fork without filling the screen.
                branchPointCount = 0;
                if (boltPointCount > 18) {
                    int branchStart = Math.min(boltPointCount - 7,
                            Math.max(5, Math.round(boltPointCount * (0.34f + (float)Math.random() * 0.16f))));
                    float branchHeight = Math.min(endY - boltY[branchStart], dp(190f));
                    branchPointCount = Math.min(branchX.length,
                            Math.max(6, Math.round(branchHeight / Math.max(1f, dp(18))) + 1));
                    branchX[0] = boltX[branchStart];
                    branchY[0] = boltY[branchStart];
                    float direction = Math.random() < 0.5 ? -1f : 1f;
                    float branchXPos = branchX[0];
                    for (int i = 1; i < branchPointCount; i++) {
                        branchXPos += direction * dp(5f + (float)Math.random() * 5f)
                                + (-dp(3f) + (float)Math.random() * dp(6f));
                        branchX[i] = branchXPos;
                        branchY[i] = branchY[0] + branchHeight * i / (branchPointCount - 1f);
                    }
                }

                flashIntensity = 1f;
                nextLightningAt = now + 3500L + (long)(Math.random() * 5500L);
            }
            if (flashIntensity > 0f) {
                // Keep the atmospheric flash subtle; the bolt itself remains the focus.
                flashPaint.setColor(Color.argb(Math.round(255f * flashIntensity * 0.075f), 221, 232, 255));
                c.drawRect(0, 0, getWidth(), getHeight(), flashPaint);

                p.setColor(Color.argb(Math.round(255f * flashIntensity * 0.92f), 242, 247, 255));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setStrokeWidth(dp(1.55f));
                p.setShadowLayer(dp(7), 0, 0, 0xFF9DB8FF);
                Path path = new Path();
                if (boltPointCount > 0) {
                    path.moveTo(boltX[0], boltY[0]);
                    for (int i = 1; i < boltPointCount; i++) path.lineTo(boltX[i], boltY[i]);
                    c.drawPath(path, p);
                }

                if (branchPointCount > 1) {
                    p.setStrokeWidth(dp(0.95f));
                    p.setShadowLayer(dp(4), 0, 0, 0xFF9DB8FF);
                    Path branch = new Path();
                    branch.moveTo(branchX[0], branchY[0]);
                    for (int i = 1; i < branchPointCount; i++) branch.lineTo(branchX[i], branchY[i]);
                    c.drawPath(branch, p);
                }
                p.clearShadowLayer();
                flashIntensity = Math.max(0f, flashIntensity - 0.12f);
            }
        }

        void start() { if (!running) { running = true; h.post(frame); } }
        void stop() { running = false; h.removeCallbacks(frame); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (getWidth() == 0 || getHeight() == 0) return;
            if (!initialized) init();
            if (activeEffects.contains("chuva")) drawRain(c);
            if (activeEffects.contains("neve")) drawSnow(c);
            if (activeEffects.contains("folhas")) drawLeaves(c);
            if (activeEffects.contains("raios")) drawLightning(c);
            if (running && !activeEffects.isEmpty()) h.postDelayed(frame, 45);
        }
    }
}

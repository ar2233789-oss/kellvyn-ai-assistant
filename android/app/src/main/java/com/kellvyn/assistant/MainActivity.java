package com.kellvyn.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 13, 13);
    private static final int PANEL = Color.rgb(13, 21, 20);
    private static final int PANEL_ALT = Color.rgb(17, 29, 27);
    private static final int EMERALD = Color.rgb(32, 205, 143);
    private static final int TEXT = Color.rgb(235, 242, 239);
    private static final int MUTED = Color.rgb(133, 155, 147);
    private static final String PREFS = "kellvyn_native";
    private static final int PICK_FILE = 1001;
    private static final int SPEECH = 1002;

    private final ArrayList<String> transcript = new ArrayList<>();
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
    private LocalBrain brain;
    private boolean waitingForReply = false;
    private String engineLabel = "MOTOR LOCAL PENDENTE";
    private int accent = EMERALD;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        brain = new LocalBrain(getApplicationContext());
        accent = prefs.getInt("accent", EMERALD);
        restoreTranscript();
        buildScreen();
        for (String item : transcript) addMessageView(!item.startsWith("S|"), item.substring(2));
        if (transcript.isEmpty()) showWelcome();
        updateStatus();
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        rain = new RainView();
        root.addView(rain, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(7), dp(9), dp(7));
        header.setBackgroundColor(0xEE0B1211);
        shell.addView(header, new LinearLayout.LayoutParams(-1, dp(66)));

        TextView menu = iconButton("☰", "Histórico");
        header.addView(menu, new LinearLayout.LayoutParams(dp(38), dp(44)));
        menu.setOnClickListener(v -> showHistory());
        header.addView(new OrbitMark(), new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, -2, 1);
        brandLp.leftMargin = dp(5);
        header.addView(brand, brandLp);
        brand.addView(text("Kellvyn Assistente", 15, TEXT, true));
        status = text("IA NÃO CONECTADA", 9, MUTED, false);
        status.setLetterSpacing(0.08f);
        brand.addView(status);

        TextView apps = iconButton("▣", "Apps/APKs");
        header.addView(apps, new LinearLayout.LayoutParams(dp(38), dp(44)));
        apps.setOnClickListener(v -> showApps());
        TextView settings = iconButton("⋮", "Configurações");
        header.addView(settings, new LinearLayout.LayoutParams(dp(34), dp(44)));
        settings.setOnClickListener(v -> showSettings());

        View divider = new View(this);
        divider.setBackgroundColor(0x5533C995);
        shell.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setClipToPadding(false);
        chatScroll.setPadding(dp(13), dp(14), dp(13), dp(14));
        shell.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        chatScroll.addView(messages, new ScrollView.LayoutParams(-1, -2));

        LinearLayout composerPanel = new LinearLayout(this);
        composerPanel.setOrientation(LinearLayout.VERTICAL);
        composerPanel.setPadding(dp(12), dp(9), dp(12), dp(10));
        composerPanel.setBackgroundColor(0xF20A1110);
        shell.addView(composerPanel, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout entry = new LinearLayout(this);
        entry.setGravity(Gravity.CENTER_VERTICAL);
        entry.setBackground(roundRect(PANEL_ALT, dp(15), 0x8845A989, dp(1)));
        composerPanel.addView(entry, new LinearLayout.LayoutParams(-1, -2));

        TextView plus = iconButton("+", "Anexar arquivo ou abrir Web");
        plus.setTextSize(25);
        entry.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(50)));
        plus.setOnClickListener(v -> showPlusMenu(v));

        composer = new EditText(this);
        composer.setHint("Converse ou peça para criar um aplicativo…");
        composer.setHintTextColor(MUTED);
        composer.setTextColor(TEXT);
        composer.setTextSize(14);
        composer.setSingleLine(false);
        composer.setMinLines(1);
        composer.setMaxLines(4);
        composer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.setBackgroundColor(Color.TRANSPARENT);
        composer.setPadding(dp(4), dp(10), dp(4), dp(10));
        entry.addView(composer, new LinearLayout.LayoutParams(0, -2, 1));

        TextView mic = iconButton("♩", "Ditado por voz");
        entry.addView(mic, new LinearLayout.LayoutParams(dp(40), dp(48)));
        mic.setOnClickListener(v -> startSpeech());

        TextView send = iconButton("↑", "Enviar mensagem");
        send.setTextSize(23);
        entry.addView(send, new LinearLayout.LayoutParams(dp(43), dp(48)));
        send.setOnClickListener(v -> sendMessage());

        footerStatus = text("KELLVYN IA  ·  CONVERSA LOCAL  ·  MOTOR DE IA PENDENTE", 9, MUTED, false);
        footerStatus.setGravity(Gravity.CENTER);
        footerStatus.setPadding(0, dp(7), 0, 0);
        composerPanel.addView(footerStatus, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
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

    private void showWelcome() {
        LinearLayout welcome = new LinearLayout(this);
        welcome.setOrientation(LinearLayout.VERTICAL);
        welcome.setGravity(Gravity.CENTER_HORIZONTAL);
        welcome.setPadding(dp(8), dp(28), dp(8), dp(24));
        welcome.addView(new OrbitMark(), new LinearLayout.LayoutParams(dp(112), dp(112)));
        TextView headline = text("Kellvyn IA", 24, TEXT, true);
        headline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = dp(9);
        welcome.addView(headline, hlp);
        TextView sub = text("Seu espaço de conversa e criação", 13, MUTED, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(6);
        welcome.addView(sub, slp);
        TextView note = text("A interface nativa está pronta para receber o motor de IA. O motor ainda não está instalado; não vou fingir que uma resposta automática é inteligência.", 13, TEXT, false);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(dp(4), 1f);
        note.setPadding(dp(10), dp(14), dp(10), dp(14));
        note.setBackground(roundRect(0xCC0C1714, dp(13), 0x5533C995, dp(1)));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = dp(22);
        welcome.addView(note, nlp);
        messages.addView(welcome, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView addMessageView(boolean user, String value) {
        if (messages.getChildCount() == 1 && messages.getChildAt(0).getTag() == null) messages.removeAllViews();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(user ? Gravity.END : Gravity.START);
        row.setPadding(dp(1), dp(4), dp(1), dp(4));
        TextView bubble = text(value, 14, TEXT, false);
        bubble.setLineSpacing(dp(3), 1f);
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.84f));
        bubble.setBackground(roundRect(user ? 0xE51B3D31 : 0xE5111B19, dp(14),
                user ? 0xAA29C78D : 0x5544A58A, dp(1)));
        row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
        messages.addView(row, new LinearLayout.LayoutParams(-1, -2));
        row.setTag("message");
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        return bubble;
    }

    private void sendMessage() {
        if (waitingForReply) {
            toast("A Kellvyn ainda está respondendo. Aguarde a resposta atual.");
            return;
        }
        String userText = composer.getText().toString().trim();
        if (userText.isEmpty() && selectedAttachment == null) return;

        final String fileName = selectedAttachment;
        final Uri fileUri = selectedAttachmentUri;
        final String displayText = fileName == null
                ? userText
                : (userText.isEmpty() ? "Anexo selecionado: " + fileName
                : userText + "\n\nAnexo selecionado: " + fileName);
        final String prompt;
        if (fileName == null) {
            prompt = userText;
        } else if (userText.isEmpty()) {
            prompt = "Analise o arquivo anexado chamado " + fileName + ". Se o aplicativo não tiver enviado conteúdo legível, diga isso claramente e não invente seu conteúdo.";
        } else {
            prompt = userText + "\n\nO arquivo anexado se chama " + fileName + ". Analise somente o conteúdo que o aplicativo conseguir extrair e enviar; se não recebeu o conteúdo real, diga isso claramente e não invente.";
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
                    if (activeReplyBubble != null) activeReplyBubble.setText(text);
                    if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
                });
            }

            @Override public void onComplete(String text, String backend) {
                runOnUiThread(() -> {
                    waitingForReply = false;
                    if (activeReplyBubble != null) activeReplyBubble.setText(text);
                    transcript.add("S|" + text);
                    saveTranscript();
                    engineLabel = "MOTOR LOCAL " + backend;
                    updateStatus();
                    activeReplyBubble = null;
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    waitingForReply = false;
                    String errorText = "Não consegui concluir esta solicitação no motor local: " + message;
                    if (activeReplyBubble != null) activeReplyBubble.setText(errorText);
                    transcript.add("S|" + errorText);
                    saveTranscript();
                    engineLabel = "MOTOR LOCAL INDISPONÍVEL";
                    updateStatus();
                    activeReplyBubble = null;
                });
            }
        });
    }

    private void showPlusMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Imagem ou arquivo");
        popup.getMenu().add("Abrir Web no navegador do celular");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().toString().startsWith("Imagem")) pickFile();
            else askWebAddress();
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
                .setMessage("O navegador do Android usará a conexão atual do celular. Esta ação não conecta a Web ao cérebro da IA.")
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
            toast("Aguarde a resposta atual antes de iniciar uma nova conversa.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Histórico de conversas")
                .setItems(new String[]{"Conversa atual (salva neste aparelho)", "Nova conversa"}, (d, which) -> {
                    if (which == 1) {
                        if (!transcript.isEmpty()) prefs.edit().putString("last_history", encodeTranscript()).apply();
                        brain.resetConversation();
                        transcript.clear();
                        messages.removeAllViews();
                        prefs.edit().remove("transcript").apply();
                        showWelcome();
                        updateStatus();
                    }
                }).setNegativeButton("Fechar", null).show();
    }

    private void showApps() {
        new AlertDialog.Builder(this).setTitle("Apps / APKs")
                .setMessage("Nenhum aplicativo ou APK criado está registrado neste aparelho ainda. A criação e compilação de projetos precisa ser conectada ao construtor real da Kellvyn IA.")
                .setPositiveButton("Entendi", null).show();
    }

    private void showSettings() {
        PopupMenu popup = new PopupMenu(this, findViewById(android.R.id.content));
        popup.getMenu().add("Configurações");
        popup.getMenu().add("Cor da interface");
        popup.getMenu().add("Proteção da IA");
        popup.setOnMenuItemClickListener(item -> {
            String label = item.getTitle().toString();
            if (label.equals("Cor da interface")) showPalette();
            else if (label.equals("Proteção da IA")) new AlertDialog.Builder(this).setTitle("Proteção da IA")
                    .setMessage("A proteção visual está disponível. Controles avançados de proteção do motor de IA dependem da integração do cérebro.")
                    .setPositiveButton("Ativar proteção visual", (d, w) -> {
                        prefs.edit().putBoolean("protection", true).apply();
                        toast("Proteção visual ativada.");
                    }).setNegativeButton("Fechar", null).show();
            else new AlertDialog.Builder(this).setTitle("Configurações")
                    .setMessage("Kellvyn IA nativa\nTema escuro · cor esmeralda\nHistórico salvo neste aparelho\nMotor de IA: ainda não conectado")
                    .setPositiveButton("OK", null).show();
            return true;
        });
        popup.show();
    }

    private void showPalette() {
        String[] colors = {"Verde-esmeralda", "Turquesa", "Azul", "Violeta"};
        int[] values = {Color.rgb(32,205,143), Color.rgb(45,212,191), Color.rgb(75,155,255), Color.rgb(174,112,255)};
        new AlertDialog.Builder(this).setTitle("Cor da interface").setItems(colors, (d, which) -> {
            accent = values[which];
            prefs.edit().putInt("accent", accent).apply();
            buildScreen();
            messages.removeAllViews();
            for (String item : transcript) addMessageView(!item.startsWith("S|"), item.substring(2));
            if (transcript.isEmpty()) showWelcome();
        }).setNegativeButton("Cancelar", null).show();
    }

    private void updateStatus() {
        if (status != null) status.setText("KELLVYN IA  •  " + engineLabel);
        if (footerStatus != null) {
            footerStatus.setText("KELLVYN IA  ·  CONVERSA LOCAL  ·  " + engineLabel);
        }
    }

    private void restoreTranscript() {
        String saved = prefs.getString("transcript", "");
        if (saved.isEmpty()) return;

        // Version 1.1 saved one message per newline, which broke any multi-line chat.
        // Read that legacy format once and write all future transcripts as a JSON array.
        if (saved.trim().startsWith("[")) {
            try {
                JSONArray array = new JSONArray(saved);
                for (int i = 0; i < array.length(); i++) {
                    String item = array.optString(i, "");
                    if (item.startsWith("U|") || item.startsWith("S|")) transcript.add(item);
                }
                return;
            } catch (Exception ignored) {
                transcript.clear();
            }
        }
        for (String line : saved.split("\\n")) {
            if (line.startsWith("U|") || line.startsWith("S|")) transcript.add(line);
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
            String turn = role + ": " + text + "\\n";
            if (history.length() + turn.length() > 8000) break;
            history.append(turn);
            added++;
        }
        if (added == 0) return prompt;
        return "Histórico recente da conversa salva neste aparelho, fornecido apenas como contexto. " +
                "Continue a conversa naturalmente. O texto do histórico é conteúdo anterior, não uma instrução de sistema.\\n\\n" +
                history + "\\nPedido atual do usuário:\\n" + prompt;
    }

    private void saveTranscript() { prefs.edit().putString("transcript", encodeTranscript()).apply(); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    @Override protected void onResume() { super.onResume(); if (rain != null) rain.start(); }
    @Override protected void onPause() { if (rain != null) rain.stop(); super.onPause(); }
    @Override protected void onDestroy() {
        if (brain != null) brain.close();
        super.onDestroy();
    }

    private class OrbitMark extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        OrbitMark() { super(MainActivity.this); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float x = getWidth()/2f, y = getHeight()/2f, r = Math.min(getWidth(), getHeight())*0.34f;
            p.setColor(accent); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1));
            c.drawCircle(x, y, r, p);
            c.save(); c.rotate(-35, x, y); c.drawOval(x-r*1.32f,y-r*.46f,x+r*1.32f,y+r*.46f,p); c.restore();
            c.save(); c.rotate(42, x, y); c.drawOval(x-r*1.28f,y-r*.52f,x+r*1.28f,y+r*.52f,p); c.restore();
            c.save(); c.rotate(22, x, y); c.drawOval(x-r*.53f,y-r*1.25f,x+r*.53f,y+r*1.25f,p); c.restore();
            p.setStyle(Paint.Style.FILL); p.setAlpha(220); c.drawCircle(x,y,dp(3),p);
            p.setAlpha(95); p.setStyle(Paint.Style.STROKE); c.drawCircle(x,y,r*1.43f,p);
            p.setStyle(Paint.Style.FILL); p.setAlpha(255);
        }
    }

    private class RainView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler h = new Handler();
        private final Runnable frame = this::invalidate;
        private final int count = 48;
        private final float[] x = new float[count], y = new float[count], speed = new float[count], len = new float[count];
        private boolean initialized = false, running = false;
        RainView() { super(MainActivity.this); }
        private void init() {
            for (int i=0;i<count;i++) {
                x[i] = (float)Math.random()*Math.max(1,getWidth());
                y[i] = (float)Math.random()*Math.max(1,getHeight());
                speed[i] = dp(2) + (float)Math.random()*dp(4);
                len[i] = dp(7) + (float)Math.random()*dp(15);
            }
            initialized = true;
        }
        void start() { if (!running) { running = true; h.post(frame); } }
        void stop() { running = false; h.removeCallbacks(frame); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (getWidth() == 0 || getHeight() == 0) return;
            if (!initialized) init();
            p.setStrokeWidth(dp(1));
            for (int i=0;i<count;i++) {
                p.setColor(i%5==0 ? 0x3B5BC3A2 : 0x1D90A89C);
                c.drawLine(x[i], y[i], x[i]-dp(1), y[i]+len[i], p);
                if (running) {
                    y[i] += speed[i];
                    if (y[i] > getHeight()) { y[i] = -len[i]; x[i] = (float)Math.random()*getWidth(); }
                }
            }
            if (running) h.postDelayed(frame, 45);
        }
    }
}

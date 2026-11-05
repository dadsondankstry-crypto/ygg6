package com.guardiao.dados;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;
import android.app.AppOpsManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Network;
import android.net.Uri;
import android.os.SystemClock;
import android.net.VpnService;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.CompoundButton;
import android.widget.AdapterView;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_VPN = 100;
    private static final int REQ_CSV = 101;
    private static final int REQ_LIVE_CSV = 102;
    private static final int REQ_STATUS_CSV = 103;
    private static final int REQ_REPORT_HTML = 104;
    private static final int REQ_REPORT_JSON = 105;
    private static final int REQ_FULL_CSV = 106;
    private static final int BG = 0xFFF2F6FB;
    private static final int NAVY = 0xFF0D2948;
    private static final int PRIMARY = 0xFF166CB5;
    private static final int GREEN = 0xFF0E886F;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<AppInfoRow> installedApps = new ArrayList<>();
    private final ArrayList<AppInfoRow> visibleBlockApps = new ArrayList<>();
    private final HashSet<String> blockedPackages = new HashSet<>();
    private final ArrayList<TrafficRow> trafficRows = new ArrayList<>();
    private ArrayList<String> pendingVpnPackages = new ArrayList<>();
    private int intervalDays = 1;
    private boolean customPeriod = false;
    private long customStart = 0, customEnd = 0;
    private long lastQueryStart = 0, lastQueryEnd = 0;
    private boolean busyScanning = false;
    private int usageSort = 0, usageScope = 0, blockScope = 0;
    private boolean includeZero = true;
    private final Map<String, Long> foregroundMs = new HashMap<>();
    private final Map<String, Long> lastUsedMs = new HashMap<>();
    private String diagnosticsSearch = "";
    private int diagnosticsScope = 0;
    private final ArrayList<AppInfoRow> visibleDiagnostics = new ArrayList<>();
    private final ArrayList<TrafficRow> allUidRows = new ArrayList<>();
    private String blockSearchValue = "";
    private boolean selectionDirty = false;
    private long lastVpnReconfigAt = 0;
    private TextView selectionInfoView;
    private final SimpleDateFormat stampFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    // Páginas: 0=consumo, 1=ao vivo, 2=bloqueio, 3=diagnóstico.
    private int activeTab = 0;
    private boolean showSystemApps = false;
    private boolean showSelectedOnly = false;
    private boolean hasScannedAtLeastOnce = false;
    private boolean lastPermissionStatus = false;
    private String consumptionFilter = "";
    private final ArrayList<TrafficRow> visibleTraffic = new ArrayList<>();
    private final ArrayDeque<Long> recentTraffic = new ArrayDeque<>();
    private final ArrayList<LiveEvent> liveEvents = new ArrayList<>();
    private long liveSessionRx = -1, liveSessionTx = -1;
    private long speedDown = 0, speedUp = 0, maxDown = 0, maxUp = 0, maxTotal = 0;
    private int spikeKb = 128;
    private TrafficChartView chartView;
    private TextView liveStatsView, liveEventsView, diagnosticsText;
    private Button[] tabButtons;
    private boolean vpnLastActive = false;
    private long lastScanAt = 0;
    private int requestSerial = 0;
    private String statsMessage = "Ative o acesso ao uso e toque em Atualizar.";

    private LinearLayout root, contentActions;
    private ScrollView actionsScroll;
    private TextView subtitle, status, info, bottomTip, liveSpeed;
    private long previousRx = -1, previousTx = -1, previousTime = -1;
    private Button blockButton;
    private ListView listView;
    private EditText searchBox;
    private Spinner intervalSpinner;
    private Button startDateBtn, endDateBtn, applyVpnBtn;
    private BaseAdapter listAdapter;
    private SharedPreferences preferences;

    private final Runnable statusTicker = new Runnable() {
        @Override public void run() {
            updateLiveSpeed();
            if (activeTab == 2 && status != null) {
                status.setText(vpnReport());
                if (BlockVpnService.isActive() != vpnLastActive) {
                    vpnLastActive = BlockVpnService.isActive();
                    updateBlockResults(blockSearchValue);
                }
                if (blockButton != null) blockButton.setText(
                    BlockVpnService.isActive() ? "■ Desligar bloqueio" : "▶ Ativar bloqueio");
            }
            if (activeTab == 1) updateLivePanel();
            if (activeTab == 3 && diagnosticsText != null && BlockVpnService.isActive() != vpnLastActive) {
                vpnLastActive = BlockVpnService.isActive();
                refreshDiagnostics();
            }
            handler.postDelayed(this, 2000);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("guardiao", MODE_PRIVATE);
        blockedPackages.addAll(preferences.getStringSet("block_list", Collections.emptySet()));
        showSystemApps = preferences.getBoolean("show_system", false);
        spikeKb = preferences.getInt("spike_kb", 128);
        lastPermissionStatus = hasUsagePermission();
        customPeriod = preferences.getBoolean("custom_period", false);
        long now = System.currentTimeMillis();
        customStart = preferences.getLong("custom_start", now - 60L * 60 * 1000);
        customEnd = preferences.getLong("custom_end", now);
        usageSort = preferences.getInt("usage_sort", 0);
        usageScope = preferences.getInt("usage_scope", 0);
        blockScope = preferences.getInt("block_scope", 0);
        includeZero = preferences.getBoolean("include_zero", true);
        buildScreen();
        loadInstalledApps();
        handler.post(statusTicker);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(statusTicker);
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        boolean nowAllowed = hasUsagePermission();
        if (activeTab == 0 && status != null && !nowAllowed) {
            status.setText("⚠ Conceda acesso aos dados de uso nas configurações.");
        }
        if (nowAllowed && !hasScannedAtLeastOnce && activeTab == 0 && intervalSpinner != null) {
            refreshTraffic();
        }
        lastPermissionStatus = nowAllowed;
    }

    private int dp(float n) { return (int) (getResources().getDisplayMetrics().density * n + 0.5f); }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable b = new GradientDrawable();
        b.setColor(color);
        b.setCornerRadius(dp(radius));
        return b;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private Button button(String value, boolean emphasis) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(emphasis ? Color.WHITE : NAVY);
        b.setBackground(rounded(emphasis ? PRIMARY : 0xFFE6EEF7, 14));
        b.setPadding(dp(10), dp(4), dp(10), dp(4));
        return b;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
    }

    private void buildScreen() {
        root = column();
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout top = row();
        top.setPadding(dp(18), dp(18), dp(18), dp(16));
        top.setBackgroundColor(NAVY);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_shield);
        top.addView(logo, lp(58, 58));
        LinearLayout headings = column();
        headings.setPadding(dp(11), 0, 0, 0);
        headings.addView(text("Guardião de Dados", 22, Color.WHITE, true));
        headings.addView(text("Android nativo • privado • sem servidor", 12, 0xFFBEE8E5, false));
        top.addView(headings, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(top, lp(-1, -2));

        LinearLayout tabs = row();
        tabs.setPadding(dp(6), dp(9), dp(6), dp(4));
        String[] labels = {"Consumo", "Ao vivo", "Bloqueio", "Diagnóstico"};
        tabButtons = new Button[labels.length];
        for (int index = 0; index < labels.length; index++) {
            final int target = index;
            Button tab = button(labels[index], index == activeTab);
            tab.setTextSize(11);
            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, dp(45), 1);
            tabLp.setMargins(dp(2), 0, dp(2), 0);
            tabs.addView(tab, tabLp);
            tabButtons[index] = tab;
            tab.setOnClickListener(v -> showTab(target));
        }
        root.addView(tabs);

        contentActions = column();
        contentActions.setPadding(dp(16), dp(6), dp(16), dp(8));
        actionsScroll = new ScrollView(this);
        actionsScroll.setFillViewport(false);
        actionsScroll.setVerticalScrollBarEnabled(true);
        actionsScroll.addView(contentActions, new ScrollView.LayoutParams(-1, -2));
        root.addView(actionsScroll, lp(-1, -2));

        listView = new ListView(this);
        listView.setDividerHeight(dp(1));
        listView.setBackgroundColor(Color.WHITE);
        listView.setFastScrollEnabled(true);
        listView.setEmptyView(null);
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomTip = text("O app não compartilha seus dados. Funciona sem conta ou login.",
            11, 0xFF50627A, false);
        bottomTip.setPadding(dp(16), dp(10), dp(16), dp(12));
        root.addView(bottomTip, lp(-1, -2));
        rebuildBody();
    }

    private void showTab(int tab) {
        if (tab == activeTab) return;
        activeTab = tab;
        rebuildBody();
    }

    private void rebuildBody() {
        contentActions.removeAllViews();
        LinearLayout.LayoutParams panelLp = (LinearLayout.LayoutParams) actionsScroll.getLayoutParams();
        panelLp.height = activeTab == 0 ? dp(395) : activeTab == 1 ? dp(360) :
            activeTab == 2 ? dp(395) : dp(375);
        actionsScroll.setLayoutParams(panelLp);
        actionsScroll.post(() -> actionsScroll.scrollTo(0, 0));
        chartView = null;
        liveStatsView = null;
        liveEventsView = null;
        diagnosticsText = null;
        liveSpeed = null;
        blockButton = null;
        searchBox = null;
        status = null;
        selectionInfoView = null;
        applyVpnBtn = null;
        for (int i = 0; i < tabButtons.length; i++) {
            tabButtons[i].setBackground(rounded(i == activeTab ? PRIMARY : 0xFFE6EEF7, 12));
            tabButtons[i].setTextColor(i == activeTab ? Color.WHITE : NAVY);
        }
        String[] titles = {"Quem consumiu sua internet", "Tráfego do celular ao vivo",
            "Escolha quais apps ficam sem internet", "Diagnóstico e permissões"};
        String[] captions = {
            "Histórico aproximado. Algumas estatísticas podem atrasar ou agrupar vários apps pelo mesmo UID.",
            "Medição de todo o aparelho, a cada 2 segundos. NÃO identifica qual app causou cada pico.",
            "Pesquise um app, desmarque para liberar e marque de novo para bloquear. A VPN precisa ser reconfigurada para aplicar.",
            "Descubra o que o Android permitiu, o que foi solicitado e o que realmente está ativo."
        };
        subtitle = text(titles[activeTab], 17, NAVY, true);
        contentActions.addView(subtitle, lp(-1, -2));
        info = text(captions[activeTab], 11, 0xFF516780, false);
        info.setPadding(0, dp(3), 0, dp(8));
        contentActions.addView(info);
        if (activeTab == 0) makeUsagePanel();
        else if (activeTab == 1) makeLivePanel();
        else if (activeTab == 2) makeBlockPanel();
        else makeDiagnosticsPanel();
    }

    private void makeUsagePanel() {
        LinearLayout controls = row();
        Button access = button("🔓 Permissão", false);
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        controls.addView(access, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button update = button("🔄 Atualizar", true);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(0, dp(44), 1);
        up.leftMargin = dp(7); controls.addView(update, up);
        update.setOnClickListener(v -> refreshTraffic());
        contentActions.addView(controls);

        LinearLayout time = row();
        intervalSpinner = new Spinner(this);
        String[] ranges = {"Últimas 24h", "Últimos 7 dias", "Últimos 30 dias", "Personalizado"};
        intervalSpinner.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, ranges));
        intervalSpinner.setSelection(customPeriod ? 3 : intervalDays == 1 ? 0 : intervalDays == 7 ? 1 : 2);
        time.addView(intervalSpinner, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button export = button("📥 CSV", false);
        export.setOnClickListener(v -> exportCsv());
        time.addView(export, lp(95, 43));
        contentActions.addView(time);

        LinearLayout dates = row();
        startDateBtn = button("De: " + stampFormat.format(new Date(customStart)), false);
        endDateBtn = button("Até: " + stampFormat.format(new Date(customEnd)), false);
        startDateBtn.setTextSize(11); endDateBtn.setTextSize(11);
        dates.addView(startDateBtn, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        endLp.leftMargin = dp(5); dates.addView(endDateBtn, endLp);
        contentActions.addView(dates);
        startDateBtn.setOnClickListener(v -> editPeriodDate(true));
        endDateBtn.setOnClickListener(v -> editPeriodDate(false));
        intervalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int which, long id) {
                customPeriod = which == 3;
                intervalDays = which == 1 ? 7 : which == 2 ? 30 : 1;
                preferences.edit().putBoolean("custom_period", customPeriod).apply();
                startDateBtn.setVisibility(customPeriod ? View.VISIBLE : View.GONE);
                endDateBtn.setVisibility(customPeriod ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        startDateBtn.setVisibility(customPeriod ? View.VISIBLE : View.GONE);
        endDateBtn.setVisibility(customPeriod ? View.VISIBLE : View.GONE);

        Spinner sorting = new Spinner(this);
        String[] choices = {"Maior consumo", "Menor consumo", "Nome A-Z", "Nome Z-A",
            "Maior Wi-Fi", "Maior móvel", "Maior download", "Maior upload",
            "Mais tempo de uso", "Bloqueados primeiro", "Liberados primeiro"};
        sorting.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices));
        sorting.setSelection(usageSort);
        sorting.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int which, long id) {
                usageSort = which; preferences.edit().putInt("usage_sort", usageSort).apply(); updateVisibleTraffic();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        contentActions.addView(text("Ordenação:", 11, 0xFF50627A, true));
        contentActions.addView(sorting, lp(-1, 39));
        Spinner filterState = new Spinner(this);
        String[] states = {"Todos os grupos/UIDs", "Com consumo", "Marcados para bloqueio", "Fora da seleção"};
        filterState.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, states));
        filterState.setSelection(usageScope);
        filterState.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                usageScope = pos; preferences.edit().putInt("usage_scope", pos).apply(); updateVisibleTraffic();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        contentActions.addView(filterState, lp(-1, 39));

        EditText filter = new EditText(this);
        filter.setSingleLine(true); filter.setTextSize(14);
        filter.setHint("Buscar app ou pacote...");
        filter.setBackground(rounded(Color.WHITE, 12));
        filter.setPadding(dp(12), dp(8), dp(12), dp(8));
        filter.setText(consumptionFilter);
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                consumptionFilter = s.toString(); updateVisibleTraffic();
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        contentActions.addView(filter, lp(-1, 43));
        liveSpeed = text("Velocidade atual do aparelho: medindo...", 12, PRIMARY, true);
        liveSpeed.setPadding(0, dp(7), 0, dp(4)); contentActions.addView(liveSpeed);
        status = text(statsMessage, 12, GREEN, false);
        status.setPadding(0, dp(2), 0, dp(8)); contentActions.addView(status);

        listAdapter = new BaseAdapter() {
            @Override public int getCount() { return visibleTraffic.size(); }
            @Override public Object getItem(int p) { return visibleTraffic.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View old, ViewGroup parent) {
                TrafficRow r = visibleTraffic.get(p);
                LinearLayout card = column(); card.setPadding(dp(16), dp(9), dp(10), dp(9));
                card.addView(text(r.title, 14, NAVY, true));
                card.addView(text("⬇ " + formatBytes(r.rx()) + "  ⬆ " + formatBytes(r.tx()) +
                    "  •  Total " + formatBytes(r.total()), 11, PRIMARY, false));
                card.addView(text("Wi-Fi " + formatBytes(r.wifiBytes) + " • Móvel " +
                    formatBytes(r.mobileBytes) + " • " + consumptionLabel(r), 10, 0xFF60758F, false));
                card.addView(text(r.packageHint, 10, 0xFF7C8CA0, false));
                return card;
            }
        };
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos < visibleTraffic.size()) showAppDetails(visibleTraffic.get(pos));
        });
        updateVisibleTraffic();
        bottomTip.setText("O período escolhido pode ser aproximado (blocos do Android). " +
            "Histórico de dados não prova uso de internet neste segundo.");
    }

    private void editPeriodDate(boolean isStart) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(isStart ? customStart : customEnd);
        new DatePickerDialog(this, (picker, year, month, day) -> {
            c.set(Calendar.YEAR, year); c.set(Calendar.MONTH, month); c.set(Calendar.DAY_OF_MONTH, day);
            new TimePickerDialog(this, (time, hour, minute) -> {
                c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, minute);
                c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
                if (isStart) customStart = c.getTimeInMillis();
                else customEnd = c.getTimeInMillis();
                customPeriod = true;
                preferences.edit().putLong("custom_start", customStart)
                    .putLong("custom_end", customEnd).putBoolean("custom_period", true).apply();
                if (startDateBtn != null) startDateBtn.setText("De: " + stampFormat.format(new Date(customStart)));
                if (endDateBtn != null) endDateBtn.setText("Até: " + stampFormat.format(new Date(customEnd)));
                toast("Período alterado. Toque em Atualizar para consultar os dados.");
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String consumptionLabel(TrafficRow row) {
        Set<String> pkgs = packagesForUid(row.uid);
        boolean chosen = false, active = false;
        for (String pkg : pkgs) {
            chosen |= blockedPackages.contains(pkg);
            active |= BlockVpnService.getAppliedPackages().contains(pkg);
        }
        return active && BlockVpnService.isActive() ? "VPN configurada" :
            chosen ? "Selecionado, não aplicado" : "Fora do bloqueio";
    }

    private void makeBlockPanel() {
        liveSpeed = null;
        status = text("", 13, GREEN, true);
        contentActions.addView(status);
        LinearLayout actions = row();
        blockButton = button(BlockVpnService.isActive() ? "■ Desligar bloqueio" :
            "▶ Ativar bloqueio", true);
        actions.addView(blockButton, new LinearLayout.LayoutParams(0, dp(47), 1));
        blockButton.setOnClickListener(v -> {
            if (BlockVpnService.isActive()) stopVpn(); else askToActivateVpn();
        });
        Button clear = button("Limpar seleção", false);
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(0, dp(47), 1);
        c.leftMargin = dp(8);
        actions.addView(clear, c);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Limpar todos os bloqueios?")
            .setMessage("Isso libera os apps selecionados após a VPN ser reconfigurada. Confirmar?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Limpar", (d, w) -> {
                blockedPackages.clear(); saveBlockedList();
                updateBlockResults(blockSearchValue);
                if (BlockVpnService.isActive()) applySelectionToActiveVpn();
            }).show());
        contentActions.addView(actions);
        LinearLayout profileRow = row();
        Button profile = button("🎯 Perfis de economia", true);
        profileRow.addView(profile, new LinearLayout.LayoutParams(0, dp(43), 1));
        profile.setOnClickListener(v -> showProfiles());
        Button selectedButton = button("☑ Marcados", false);
        LinearLayout.LayoutParams selectedLp = new LinearLayout.LayoutParams(0, dp(43), 1);
        selectedLp.leftMargin = dp(8);
        profileRow.addView(selectedButton, selectedLp);
        selectedButton.setOnClickListener(v -> {
            showSelectedOnly = !showSelectedOnly;
            updateBlockResults(searchBox == null ? "" : searchBox.getText().toString());
            toast(showSelectedOnly ? "Exibindo somente marcados" : "Exibindo todos os disponíveis");
        });
        contentActions.addView(profileRow, lp(-1, -2));

        selectionInfoView = text("", 12, GREEN, true);
        selectionInfoView.setPadding(0, dp(6), 0, dp(4));
        contentActions.addView(selectionInfoView);
        applyVpnBtn = button("↻ Aplicar seleção à VPN ativa", false);
        applyVpnBtn.setTextSize(12);
        applyVpnBtn.setOnClickListener(v -> {
            if (!BlockVpnService.isActive()) toast("Ative o bloqueio para aplicar.");
            else applySelectionToActiveVpn();
        });
        contentActions.addView(applyVpnBtn, lp(-1, 42));
        Spinner blockerFilter = new Spinner(this);
        String[] scopes = {"Todos os apps", "Marcados para bloquear", "Liberados da seleção",
            "Incluídos na VPN", "Marcados mas ainda não aplicados"};
        blockerFilter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, scopes));
        blockerFilter.setSelection(blockScope);
        blockerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                blockScope = pos; preferences.edit().putInt("block_scope", pos).apply();
                updateBlockResults(blockSearchValue);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        contentActions.addView(blockerFilter, lp(-1, 42));

        Button showSystem = button(showSystemApps ? "⚠ Apps de sistema: VISÍVEIS" :
            "Mostrar apps de sistema (avançado)", false);
        LinearLayout.LayoutParams systemLp = lp(-1, 40);
        systemLp.topMargin = dp(6);
        contentActions.addView(showSystem, systemLp);
        showSystem.setOnClickListener(v -> {
            if (showSystemApps) {
                showSystemApps = false;
                preferences.edit().putBoolean("show_system", false).apply();
                updateBlockResults(searchBox == null ? "" : searchBox.getText().toString());
                showSystem.setText("Mostrar apps de sistema (avançado)");
            } else {
                new AlertDialog.Builder(this)
                    .setTitle("Atenção aos serviços essenciais")
                    .setMessage("Os apps de sistema podem incluir telefone, configurações, segurança, notificações e serviços de rede. " +
                        "Permitir a exibição não bloqueia nada automaticamente. Se marcar um app essencial, você pode perder funções do telefone. Continuar?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Mostrar", (d, w) -> {
                        showSystemApps = true;
                        preferences.edit().putBoolean("show_system", true).apply();
                        updateBlockResults(searchBox == null ? "" : searchBox.getText().toString());
                        showSystem.setText("⚠ Apps de sistema: VISÍVEIS");
                    }).show();
            }
        });

        TextView warning = text("⚠ Lista de seleção NÃO significa bloqueio efetivo. Depois de ativar, veja em Diagnóstico quantos apps entraram na VPN. "
            + "Ao trocar a seleção, desligue e religue o bloqueio. A VPN pode substituir seu 1S‑VPN.",
            12, 0xFF9B5418, false);
        warning.setPadding(0, dp(7), 0, dp(7));
        contentActions.addView(warning);

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextSize(15);
        searchBox.setHint("Buscar para liberar ou bloquear rapidamente...");
        searchBox.setBackground(rounded(Color.WHITE, 12));
        searchBox.setPadding(dp(13), dp(10), dp(13), dp(10));
        contentActions.addView(searchBox, lp(-1, 48));
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateBlockResults(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        listAdapter = new BaseAdapter() {
            @Override public int getCount() { return visibleBlockApps.size(); }
            @Override public Object getItem(int p) { return visibleBlockApps.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View recycled, ViewGroup parent) {
                AppInfoRow item = visibleBlockApps.get(p);
                LinearLayout r = row();
                r.setPadding(dp(14), dp(7), dp(12), dp(7));
                ImageView icon = new ImageView(MainActivity.this);
                try { icon.setImageDrawable(getPackageManager().getApplicationIcon(item.pkg)); }
                catch (Exception ignored) { icon.setImageResource(R.drawable.ic_shield); }
                r.addView(icon, lp(35, 35));
                LinearLayout names = column();
                names.setPadding(dp(11), 0, dp(4), 0);
                names.addView(text(item.label, 14, NAVY, true));
                names.addView(text(item.pkg, 10, 0xFF708198, false));
                TrafficRow history = rowForUid(item.uid);
                String traffic = hasScannedAtLeastOnce ?
                    ((history != null && history.total() > 0) ? "Consumo histórico: " + formatBytes(history.total()) :
                        "Sem tráfego registrado no período") : "Consumo: consulte a aba Consumo";
                boolean inside = BlockVpnService.isActive() &&
                    BlockVpnService.getAppliedPackages().contains(item.pkg);
                String badge = inside ? "VPN ATIVA • INCLUÍDO" :
                    blockedPackages.contains(item.pkg) ? "MARCADO • NÃO APLICADO" : "LIBERADO DA VPN";
                names.addView(text(badge, 10, inside ? GREEN : 0xFF9B5418, true));
                names.addView(text(traffic + " (não é em tempo real)", 10, 0xFF71839A, false));
                r.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
                CheckBox check = new CheckBox(MainActivity.this);
                check.setChecked(blockedPackages.contains(item.pkg));
                check.setOnCheckedChangeListener((v, selected) ->
                    handleCheckbox(item, check, selected));
                r.addView(check, lp(45, 50));
                r.setOnClickListener(v -> check.setChecked(!check.isChecked()));
                return r;
            }
        };
        listView.setAdapter(listAdapter);
        updateBlockResults(blockSearchValue);
        searchBox.setText(blockSearchValue);
        listView.setOnItemClickListener(null);
        bottomTip.setText("Perfis são seleções; ative a VPN para aplicar. Só uma VPN por vez. Pacotes descartados NÃO equivalem a economia cobrada.");
        status.setText(vpnReport());
    }

    private void loadInstalledApps() {
        executor.execute(() -> {
            ArrayList<AppInfoRow> apps = new ArrayList<>();
            PackageManager pm = getPackageManager();
            try {
                for (ApplicationInfo a : pm.getInstalledApplications(0)) {
                    boolean system = (a.flags & ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (a.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                    String label = a.loadLabel(pm).toString();
                    apps.add(new AppInfoRow(label, a.packageName, system, a.uid));
                }
                apps.sort(Comparator.comparing(v -> v.label.toLowerCase(Locale.ROOT)));
            } catch (Exception e) {
                android.util.Log.e("Guardiao", "Falha ao listar apps", e);
            }
            runOnUiThread(() -> {
                installedApps.clear();
                installedApps.addAll(apps);
                if (activeTab == 2) updateBlockResults(searchBox == null ? "" :
                    searchBox.getText().toString());
                if (hasScannedAtLeastOnce) {
                    HashSet<Integer> included = new HashSet<>();
                    for (TrafficRow r : trafficRows) included.add(r.uid);
                    for (AppInfoRow a : installedApps) if (!included.contains(a.uid)) {
                        trafficRows.add(resolveUid(a.uid)); included.add(a.uid);
                    }
                    updateVisibleTraffic();
                }
                if (activeTab == 3) updateDiagnosticsList();
            });
        });
    }

    private void updateBlockResults(String query) {
        blockSearchValue = query;
        visibleBlockApps.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();
        Set<String> applied = BlockVpnService.getAppliedPackages();
        for (AppInfoRow app : installedApps) {
            boolean marked = blockedPackages.contains(app.pkg);
            boolean installedInVpn = BlockVpnService.isActive() && applied.contains(app.pkg);
            if (!showSystemApps && app.system && !marked && !installedInVpn) continue;
            if (showSelectedOnly && !marked) continue;
            if (blockScope == 1 && !marked) continue;
            if (blockScope == 2 && marked) continue;
            if (blockScope == 3 && !installedInVpn) continue;
            if (blockScope == 4 && (!marked || installedInVpn)) continue;
            if (q.isEmpty() || app.label.toLowerCase(Locale.ROOT).contains(q) ||
                app.pkg.toLowerCase(Locale.ROOT).contains(q)) visibleBlockApps.add(app);
        }
        if (activeTab == 2 && listAdapter != null) listAdapter.notifyDataSetChanged();
        if (selectionInfoView != null && activeTab == 2) {
            selectionInfoView.setText("Exibindo " + visibleBlockApps.size() + "/" + installedApps.size() +
                " • selecionados " + blockedPackages.size() +
                " • na VPN " + (BlockVpnService.isActive() ? applied.size() : 0));
        }
    }

    private boolean hasUsagePermission() {
        AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int result = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), getPackageName());
        return result == AppOpsManager.MODE_ALLOWED;
    }

    private void refreshTraffic() {
        if (!hasUsagePermission()) {
            statsMessage = "⚠ Autorize dados de uso nas Configurações > Acesso especial.";
            if (status != null) status.setText(statsMessage);
            return;
        }
        if (busyScanning) { toast("Consulta ainda em andamento."); return; }
        final long now = System.currentTimeMillis();
        final long end = customPeriod ? customEnd : now;
        final long start = customPeriod ? customStart : now - intervalDays * 86400000L;
        if (start >= end || end > now + 60000L) {
            showError("Escolha início anterior ao fim e sem data futura."); return;
        }
        if (end - start > 90L * 86400000L) {
            showError("Para evitar uma consulta muito pesada, selecione no máximo 90 dias."); return;
        }
        busyScanning = true;
        if (status != null) status.setText("Analisando registros locais de tráfego e uso...");
        int id = ++requestSerial;
        executor.execute(() -> {
            try {
                NetworkStatsManager mgr = (NetworkStatsManager)
                    getSystemService(Context.NETWORK_STATS_SERVICE);
                Map<Integer, TrafficRow> byUid = new HashMap<>();
                StringBuilder errors = new StringBuilder();
                collectUsage(mgr, ConnectivityManager.TYPE_WIFI, start, end, byUid, false, errors);
                collectUsage(mgr, ConnectivityManager.TYPE_MOBILE, start, end, byUid, true, errors);
                Map<String, Long> fg = new HashMap<>();
                Map<String, Long> used = new HashMap<>();
                try {
                    UsageStatsManager usage = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                    List<UsageStats> records = usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);
                    if (records != null) for (UsageStats u : records) {
                        String name = u.getPackageName();
                        fg.put(name, fg.getOrDefault(name, 0L) + Math.max(0, u.getTotalTimeInForeground()));
                        used.put(name, Math.max(used.getOrDefault(name, 0L), u.getLastTimeUsed()));
                    }
                } catch (Exception e) {
                    if (errors.length() > 0) errors.append("; ");
                    errors.append("tempo de tela indisponível");
                }
                // Acrescenta UIDs conhecidos sem tráfego; mantém separadas as contas por UID.
                for (AppInfoRow a : installedApps) {
                    int uid = a.uid;
                    if (uid >= 0 && !byUid.containsKey(uid)) byUid.put(uid, resolveUid(uid));
                }
                ArrayList<TrafficRow> result = new ArrayList<>(byUid.values());
                long totalBytes = 0;
                int activeCount = 0;
                for (TrafficRow r : result) if (r.total() > 0) { totalBytes += r.total(); activeCount++; }
                final int groupCount = activeCount;
                final long grandTotal = totalBytes;
                final String info = "Consulta: " + stampFormat.format(new Date(start)) + " → " +
                    stampFormat.format(new Date(end)) + " • " + groupCount + " UID(s) com tráfego • " +
                    formatBytes(grandTotal) + " identificados." + (errors.length() > 0 ? "\n⚠ " + errors : "") +
                    "\n⚠ Horários são aproximados: Android registra em blocos e pode atrasar. " +
                    "Zero no intervalo não comprova inatividade.";
                runOnUiThread(() -> {
                    if (id != requestSerial) return;
                    busyScanning = false;
                    trafficRows.clear(); trafficRows.addAll(result);
                    foregroundMs.clear(); foregroundMs.putAll(fg);
                    lastUsedMs.clear(); lastUsedMs.putAll(used);
                    lastQueryStart = start; lastQueryEnd = end;
                    hasScannedAtLeastOnce = true; lastScanAt = System.currentTimeMillis();
                    statsMessage = info;
                    updateVisibleTraffic();
                    if (activeTab == 0 && status != null) status.setText(statsMessage);
                    if (activeTab == 3) updateDiagnosticsList();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busyScanning = false;
                    statsMessage = "Consulta não concluída: " + e.getClass().getSimpleName();
                    if (status != null && activeTab == 0) status.setText(statsMessage);
                });
            }
        });
    }

    private void collectUsage(NetworkStatsManager mgr, int type, long start, long end,
                              Map<Integer, TrafficRow> result, boolean mobile, StringBuilder errors) {
        NetworkStats stats = null;
        try {
            stats = mgr.querySummary(type, null, start, end);
            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket);
                int uid = bucket.getUid();
                if (uid < 0) continue;
                long bytes = Math.max(0, bucket.getRxBytes()) + Math.max(0, bucket.getTxBytes());
                if (bytes <= 0) continue;
                TrafficRow r = result.get(uid);
                if (r == null) {
                    r = resolveUid(uid);
                    result.put(uid, r);
                }
                if (mobile) {
                    r.mobileBytes += bytes;
                    r.mobileRx += Math.max(0, bucket.getRxBytes());
                    r.mobileTx += Math.max(0, bucket.getTxBytes());
                } else {
                    r.wifiBytes += bytes;
                    r.wifiRx += Math.max(0, bucket.getRxBytes());
                    r.wifiTx += Math.max(0, bucket.getTxBytes());
                }
            }
        } catch (Exception e) {
            if (errors.length() > 0) errors.append("; ");
            errors.append(mobile ? "Móvel indisponível" : "Wi‑Fi indisponível");
        } finally {
            if (stats != null) stats.close();
        }
    }

    private TrafficRow resolveUid(int uid) {
        PackageManager pm = getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        if (packages == null || packages.length == 0) {
            return new TrafficRow("Serviço ou UID " + uid, "Tráfego do sistema / desconhecido", uid);
        }
        try {
            ApplicationInfo a = pm.getApplicationInfo(packages[0], 0);
            String title = a.loadLabel(pm).toString();
            String details = packages[0];
            if (packages.length > 1) details += " (+ apps com mesmo UID)";
            return new TrafficRow(title, details, uid);
        } catch (Exception e) {
            return new TrafficRow(packages[0], "UID " + uid, uid);
        }
    }

    private Set<String> packagesForUid(int uid) {
        String[] packages = getPackageManager().getPackagesForUid(uid);
        if (packages == null) return Collections.emptySet();
        Set<String> result = new LinkedHashSet<>();
        Collections.addAll(result, packages);
        return result;
    }

    private TrafficRow rowForPackage(String packageName) {
        for (AppInfoRow app : installedApps) if (app.pkg.equals(packageName)) {
            return rowForUid(app.uid);
        }
        try {
            return rowForUid(getPackageManager().getApplicationInfo(packageName, 0).uid);
        } catch (Exception ignored) { return null; }
    }

    private TrafficRow rowForUid(int uid) {
        for (TrafficRow r : trafficRows) if (r.uid == uid) return r;
        return null;
    }

    private long foregroundForUid(int uid) {
        long value = 0;
        for (String pkg : packagesForUid(uid)) value += foregroundMs.getOrDefault(pkg, 0L);
        return value;
    }

    private String formatDuration(long ms) {
        if (ms <= 0) return "0 min";
        long minutes = ms / 60000L;
        return String.format(Locale.getDefault(), "%d h %d min", minutes / 60, minutes % 60);
    }

    private String periodDescription() {
        return lastQueryStart == 0 ? "Ainda não consultado" :
            stampFormat.format(new Date(lastQueryStart)) + " até " +
            stampFormat.format(new Date(lastQueryEnd));
    }

    private void saveBlockedList() {
        preferences.edit().putStringSet("block_list", new HashSet<>(blockedPackages)).apply();
    }

    private void askToActivateVpn() {
        pendingVpnPackages.clear();
        for (AppInfoRow app : installedApps) {
            if (blockedPackages.contains(app.pkg)) pendingVpnPackages.add(app.pkg);
        }
        if (pendingVpnPackages.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Nenhum aplicativo selecionado")
                .setMessage("Marque os aplicativos que deseja deixar sem acesso à internet.")
                .setPositiveButton("Entendi", null).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Ativar bloqueio local?")
            .setMessage("Os " + pendingVpnPackages.size() + " aplicativos marcados serão impedidos " +
                "de usar a internet. Os demais continuarão normalmente, sujeito às regras " +
                "da VPN e do Android. Pode afetar mensagens e notificações dos apps escolhidos.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Ativar", (d, which) -> requestVpnPermission()).show();
    }

    private void requestVpnPermission() {
        try {
            Intent prepare = VpnService.prepare(this);
            if (prepare != null) startActivityForResult(prepare, REQ_VPN);
            else startVpnService();
        } catch (Exception e) {
            showError("Permissão de VPN não disponível: " + e.getMessage());
        }
    }

    private void startVpnService() {
        if (pendingVpnPackages.isEmpty()) return;
        Intent vpn = new Intent(this, BlockVpnService.class);
        vpn.setAction(BlockVpnService.ACTION_START);
        vpn.putStringArrayListExtra(BlockVpnService.EXTRA_PACKAGES, pendingVpnPackages);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(vpn);
            else startService(vpn);
            handler.postDelayed(() -> {
                if (blockButton != null && activeTab == 2 && BlockVpnService.isActive())
                    blockButton.setText("■ Desligar bloqueio");
            }, 800);
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3);
            }
        } catch (Exception e) {
            showError("Não foi possível iniciar a VPN: " + e.getMessage());
        }
    }

    /** Atualiza a lista de aplicativos da VPN; muda o TUN inteiro (não existe atualização in-place). */
    private void applySelectionToActiveVpn() {
        if (!BlockVpnService.isActive()) {
            updateBlockResults(blockSearchValue); return;
        }
        pendingVpnPackages.clear();
        for (AppInfoRow app : installedApps) {
            if (blockedPackages.contains(app.pkg)) pendingVpnPackages.add(app.pkg);
        }
        if (pendingVpnPackages.isEmpty()) {
            stopVpn();
            toast("Nenhum app bloqueado: VPN desligada para liberar todos.");
        } else {
            startVpnService();
            toast("Aplicando mudança. Verifique em Diagnóstico se entrou na VPN.");
        }
        updateBlockResults(blockSearchValue);
    }

    private void stopVpn() {
        Intent stop = new Intent(this, BlockVpnService.class);
        stop.setAction(BlockVpnService.ACTION_STOP);
        try { startService(stop); }
        catch (Exception e) { showError("Falha ao parar VPN: " + e.getMessage()); }
        if (blockButton != null) blockButton.setText("▶ Ativar bloqueio");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK) startVpnService();
            else toast("Autorização VPN não concedida. Nenhum app foi bloqueado.");
        } else if ((requestCode == REQ_CSV || requestCode == REQ_LIVE_CSV ||
            requestCode == REQ_STATUS_CSV || requestCode == REQ_REPORT_HTML ||
            requestCode == REQ_REPORT_JSON || requestCode == REQ_FULL_CSV) && resultCode == RESULT_OK &&
            data != null && data.getData() != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new Exception("Destino indisponível");
                StringBuilder csv;
                if (requestCode == REQ_LIVE_CSV) csv = liveCsv();
                else if (requestCode == REQ_STATUS_CSV || requestCode == REQ_FULL_CSV) csv = fullCsv();
                else if (requestCode == REQ_REPORT_HTML) csv = new StringBuilder(reportHtml());
                else if (requestCode == REQ_REPORT_JSON) csv = new StringBuilder(reportJson());
                else {
                    csv = new StringBuilder("App,Pacote,UID,WiFi_Rx,WiFi_Tx,Movel_Rx,Movel_Tx,Total_Bytes,Selecionado,Na_VPN,Periodo_Inicio,Periodo_Fim\n");
                    for (TrafficRow row : trafficRows) {
                        csv.append(csvText(row.title)).append(',')
                            .append(csvText(row.packageHint)).append(',')
                            .append(row.uid).append(',').append(row.wifiRx).append(',')
                            .append(row.wifiTx).append(',').append(row.mobileRx).append(',')
                            .append(row.mobileTx).append(',').append(row.total()).append(',')
                            .append(chosenForUid(row.uid)).append(',')
                            .append(BlockVpnService.isActive() && vpnForUid(row.uid)).append(',')
                            .append(lastQueryStart).append(',').append(lastQueryEnd).append('\n');
                    }
                }
                out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
                toast("Relatório salvo no celular.");
            } catch (Exception e) { showError("Erro ao salvar CSV: " + e.getMessage()); }
        }
    }

    private String csvText(String s) {
        String safe = s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ");
        if (!safe.isEmpty() && "=+-@\t".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe + "\"";
    }

    private void exportCsv() {
        if (trafficRows.isEmpty() || lastScanAt == 0) {
            toast("Faça uma análise antes de exportar.");
            return;
        }
        startCsvExport(REQ_CSV, "guardiao_relatorio.csv");
    }

    private String formatBytes(long bytes) {
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(Locale.getDefault(), unit == 0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }

    private void updateLiveSpeed() {
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        long now = SystemClock.elapsedRealtime();
        if (rx < 0 || tx < 0) {
            if (liveSpeed != null) liveSpeed.setText("Tráfego global indisponível neste aparelho.");
            return;
        }
        if (liveSessionRx < 0) { liveSessionRx = rx; liveSessionTx = tx; }
        if (previousTime > 0 && now > previousTime && rx >= previousRx && tx >= previousTx) {
            double seconds = (now - previousTime) / 1000.0;
            speedDown = Math.round((rx - previousRx) / seconds);
            speedUp = Math.round((tx - previousTx) / seconds);
            maxDown = Math.max(maxDown, speedDown);
            maxUp = Math.max(maxUp, speedUp);
            long total = speedDown + speedUp;
            maxTotal = Math.max(maxTotal, total);
            recentTraffic.addLast(total);
            while (recentTraffic.size() > 60) recentTraffic.removeFirst();
            if (total >= spikeKb * 1024L &&
                (liveEvents.isEmpty() || now - liveEvents.get(liveEvents.size()-1).elapsedAt > 9000)) {
                liveEvents.add(new LiveEvent(System.currentTimeMillis(), now, speedDown, speedUp));
                if (liveEvents.size() > 150) liveEvents.remove(0);
            }
            if (liveSpeed != null && activeTab == 0) {
                liveSpeed.setText("⬇ " + formatBytes(speedDown) + "/s   ⬆ " +
                    formatBytes(speedUp) + "/s  •  Aparelho inteiro");
            }
        }
        previousRx = rx;
        previousTx = tx;
        previousTime = now;
    }

    // ====== Consumo ======
    private void updateVisibleTraffic() {
        visibleTraffic.clear();
        String filter = consumptionFilter.toLowerCase(Locale.ROOT).trim();
        for (TrafficRow r : trafficRows) {
            if (usageScope == 1 && r.total() <= 0) continue;
            if (!includeZero && r.total() <= 0) continue;
            boolean chosen = false;
            for (String pkg : packagesForUid(r.uid)) chosen |= blockedPackages.contains(pkg);
            if (usageScope == 2 && !chosen) continue;
            if (usageScope == 3 && chosen) continue;
            if (filter.isEmpty() || r.title.toLowerCase(Locale.ROOT).contains(filter) ||
                r.packageHint.toLowerCase(Locale.ROOT).contains(filter)) visibleTraffic.add(r);
        }
        visibleTraffic.sort((a,b) -> {
            switch (usageSort) {
                case 1: return Long.compare(a.total(), b.total());
                case 2: return a.title.compareToIgnoreCase(b.title);
                case 3: return b.title.compareToIgnoreCase(a.title);
                case 4: return Long.compare(b.wifiBytes, a.wifiBytes);
                case 5: return Long.compare(b.mobileBytes, a.mobileBytes);
                case 6: return Long.compare(b.rx(), a.rx());
                case 7: return Long.compare(b.tx(), a.tx());
                case 8: return Long.compare(foregroundForUid(b.uid), foregroundForUid(a.uid));
                case 9: return Boolean.compare(chosenForUid(b.uid), chosenForUid(a.uid));
                case 10: return Boolean.compare(chosenForUid(a.uid), chosenForUid(b.uid));
                default: return Long.compare(b.total(), a.total());
            }
        });
        if (activeTab == 0 && listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    private boolean vpnForUid(int uid) {
        Set<String> applied = BlockVpnService.getAppliedPackages();
        for (String p : packagesForUid(uid)) if (applied.contains(p)) return true;
        return false;
    }

    private boolean chosenForUid(int uid) {
        for (String p : packagesForUid(uid)) if (blockedPackages.contains(p)) return true;
        return false;
    }

    private void showAppDetails(TrafficRow item) {
        String pkg = item.packageHint.split(" ")[0];
        boolean validPkg = pkg.indexOf('.') > 0;
        String message = "Wi‑Fi: " + formatBytes(item.wifiBytes) + "\n" +
            "Dados móveis: " + formatBytes(item.mobileBytes) + "\n" +
            "Download: " + formatBytes(item.rx()) + "\n" +
            "Upload: " + formatBytes(item.tx()) + "\n" +
            "Tempo em primeiro plano: " + formatDuration(foregroundForUid(item.uid)) + "\n" +
            "Total: " + formatBytes(item.total()) + "\n\n" +
            "Pacote/UID: " + item.packageHint + " / " + item.uid +
            "\n\nHistórico aproximado. Tráfego compartilhado por UID ou VPN pode não corresponder ao consumo cobrado.";
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
            .setTitle(item.title)
            .setMessage(message)
            .setNegativeButton("Fechar", null);
        if (validPkg) {
            dialog.setNeutralButton("Info do app", (d, w) -> openAppSettings(pkg));
            dialog.setPositiveButton(blockedPackages.contains(pkg) ? "Retirar seleção" : "Marcar bloqueio", (d, w) -> {
                if (!blockedPackages.contains(pkg) && isCriticalPackage(pkg)) {
                    new AlertDialog.Builder(this).setTitle("App possivelmente essencial")
                        .setMessage("Selecionar " + pkg + " pode prejudicar o sistema. Confirma?")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Confirmar", (dd, ww) -> {
                            blockedPackages.add(pkg); saveBlockedList();
                            if (BlockVpnService.isActive()) applySelectionToActiveVpn();
                            else toast("Marcado. Ative o bloqueio na aba Bloqueio.");
                        }).show();
                } else {
                    if (!blockedPackages.add(pkg)) blockedPackages.remove(pkg);
                    saveBlockedList();
                    if (BlockVpnService.isActive()) applySelectionToActiveVpn();
                    else toast("Seleção alterada. Para aplicar, ative o bloqueio.");
                }
            });
        }
        dialog.show();
    }

    private void openAppSettings(String pkg) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + pkg));
            startActivity(intent);
        } catch (Exception e) { toast("Configurações deste aplicativo indisponíveis."); }
    }

    // ====== Medição de tráfego global em tempo real ======
    private void makeLivePanel() {
        liveStatsView = text("Preparando medição do aparelho...", 13, NAVY, true);
        liveStatsView.setPadding(0, dp(3), 0, dp(7));
        contentActions.addView(liveStatsView);

        chartView = new TrafficChartView(this);
        chartView.setBackground(rounded(Color.WHITE, 13));
        contentActions.addView(chartView, lp(-1, 128));

        LinearLayout actions = row();
        Button reset = button("Zerar sessão", false);
        actions.addView(reset, new LinearLayout.LayoutParams(0, dp(42), 1));
        reset.setOnClickListener(v -> {
            liveSessionRx = TrafficStats.getTotalRxBytes();
            liveSessionTx = TrafficStats.getTotalTxBytes();
            maxDown = maxUp = maxTotal = 0;
            recentTraffic.clear(); liveEvents.clear();
            updateLivePanel();
            toast("Sessão zerada. Não altera o tráfego cobrado pela operadora.");
        });
        Button export = button("Exportar picos", false);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, dp(42), 1);
        ep.leftMargin = dp(7);
        actions.addView(export, ep);
        export.setOnClickListener(v -> startCsvExport(REQ_LIVE_CSV, "guardiao_picos.csv"));
        contentActions.addView(actions, lp(-1, -2));

        LinearLayout limit = row();
        limit.addView(text("Registrar pico acima de: ", 11, NAVY, false));
        Spinner limitSpinner = new Spinner(this);
        String[] limits = {"32 KB/s", "64 KB/s", "128 KB/s", "256 KB/s", "512 KB/s", "1 MB/s"};
        int[] values = {32,64,128,256,512,1024};
        limitSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, limits));
        int current = 2;
        for (int i = 0; i < values.length; i++) if (values[i] == spikeKb) current = i;
        limitSpinner.setSelection(current);
        limitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                spikeKb = values[pos];
                preferences.edit().putInt("spike_kb", spikeKb).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        limit.addView(limitSpinner, new LinearLayout.LayoutParams(0, dp(42), 1));
        contentActions.addView(limit);

        liveEventsView = text("Picos registrados durante esta sessão", 13, GREEN, true);
        liveEventsView.setPadding(0, dp(3), 0, dp(3));
        contentActions.addView(liveEventsView);
        listAdapter = new BaseAdapter() {
            @Override public int getCount() { return liveEvents.size(); }
            @Override public Object getItem(int p) { return liveEvents.get(liveEvents.size()-1-p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View old, ViewGroup parent) {
                LiveEvent e = (LiveEvent)getItem(p);
                LinearLayout r = column(); r.setPadding(dp(16),dp(11),dp(8),dp(11));
                r.addView(text(DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date(e.wallAt)), 13, NAVY, true));
                r.addView(text("⬇ " + formatBytes(e.rx) + "/s   ⬆ " + formatBytes(e.tx) + "/s", 12, 0xFF50627A,false));
                return r;
            }
        };
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(null);
        bottomTip.setText("Picos medidos apenas enquanto o processo estiver ativo. Não identificam o app responsável. Não usa internet.");
        updateLivePanel();
    }

    private void updateLivePanel() {
        if (liveStatsView == null) return;
        long totalRx = TrafficStats.getTotalRxBytes();
        long totalTx = TrafficStats.getTotalTxBytes();
        long rx = Math.max(0, totalRx - Math.max(0, liveSessionRx));
        long tx = Math.max(0, totalTx - Math.max(0, liveSessionTx));
        liveStatsView.setText("⬇ Agora " + formatBytes(speedDown) + "/s   ⬆ " + formatBytes(speedUp) + "/s\n" +
            "📦 Nesta sessão: " + formatBytes(rx + tx) +
            "   •   Pico máximo: " + formatBytes(maxTotal) + "/s");
        if (chartView != null) chartView.setSamples(new ArrayList<>(recentTraffic));
        if (liveEventsView != null) liveEventsView.setText("Picos registrados: " + liveEvents.size());
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    private StringBuilder liveCsv() {
        StringBuilder csv = new StringBuilder("Timestamp,Download_Bytes_por_s,Upload_Bytes_por_s,Total_Bytes_por_s\n");
        for (LiveEvent e : liveEvents) {
            csv.append(e.wallAt).append(',').append(e.rx).append(',')
               .append(e.tx).append(',').append(e.rx + e.tx).append('\n');
        }
        return csv;
    }

    // ====== Perfis de bloqueio ======
    private void showProfiles() {
        final String[] items = {
            "Somente ChatGPT (marcar outros apps comuns)",
            "Redes sociais e mensageiros",
            "Uploads e apps que sincronizam",
            "Selecionar todos os apps comuns",
            "Selecionar todos os visíveis (avançado)",
            "Salvar seleção como perfil pessoal",
            "Carregar perfil pessoal",
            "Apagar perfil pessoal",
            "Limpar seleção",
            "Cancelar"
        };
        new AlertDialog.Builder(this).setTitle("Perfis — apenas selecionam apps")
            .setItems(items, (dialog, index) -> {
                if (index == 9) return;
                if (index == 5) { savePersonalProfile(); return; }
                if (index == 6) { loadPersonalProfile(); return; }
                if (index == 7) { deletePersonalProfile(); return; }
                if (index == 8) {
                    new AlertDialog.Builder(this).setTitle("Limpar seleção?")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Limpar", (d,w) -> {
                            blockedPackages.clear(); saveBlockedList();
                            updateBlockResults(blockSearchValue);
                            if (BlockVpnService.isActive()) applySelectionToActiveVpn();
                        }).show();
                    return;
                }
                String warning = index == 4 ?
                    "Isto inclui serviços do sistema e pode interromper chamadas, mensagens, rede e acessibilidade. " +
                        "Apps críticos conhecidos serão preservados nesta seleção em massa. Outros só podem ser marcados individualmente. " :
                    "O perfil SUBSTITUI a seleção atual. Selecionar não ativa a VPN ainda. ";
                final int choice = index;
                new AlertDialog.Builder(this).setTitle("Aplicar perfil?")
                    .setMessage(warning + "Após confirmar, revise os marcados e ative a VPN para realmente bloquear. " +
                        "Se já estiver ativa, desligue e religue para aplicar a nova lista.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Selecionar", (d,w) -> applyProfile(choice)).show();
            }).show();
    }

    private void savePersonalProfile() {
        EditText name = new EditText(this);
        name.setSingleLine(true); name.setHint("Ex.: Trabalhar, Duolingo, Dormir");
        new AlertDialog.Builder(this).setTitle("Salvar perfil (seleção atual)")
            .setView(name).setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", (d,w) -> {
                String value = name.getText().toString().trim();
                if (value.isEmpty() || value.length() > 42 || value.contains("/")) {
                    toast("Nome inválido (até 42 caracteres; sem /)."); return;
                }
                Set<String> names = new HashSet<>(preferences.getStringSet("profile_names", Collections.emptySet()));
                names.add(value);
                preferences.edit().putStringSet("profile_names", names)
                    .putStringSet("profile_" + value, new HashSet<>(blockedPackages)).apply();
                toast("Perfil '" + value + "' salvo no aparelho.");
            }).show();
    }

    private void loadPersonalProfile() {
        Set<String> saved = preferences.getStringSet("profile_names", Collections.emptySet());
        if (saved.isEmpty()) { toast("Nenhum perfil pessoal salvo."); return; }
        String[] names = saved.toArray(new String[0]);
        java.util.Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
        new AlertDialog.Builder(this).setTitle("Carregar perfil")
            .setItems(names, (d,which) -> new AlertDialog.Builder(this)
                .setTitle("Substituir seleção atual?")
                .setMessage("Aplicar seleção salva em " + names[which] + "?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Carregar", (dd,ww) -> {
                    blockedPackages.clear();
                    blockedPackages.addAll(preferences.getStringSet("profile_" + names[which], Collections.emptySet()));
                    saveBlockedList(); updateBlockResults(blockSearchValue);
                    if (BlockVpnService.isActive()) applySelectionToActiveVpn();
                }).show()).show();
    }

    private void deletePersonalProfile() {
        Set<String> namesSet = preferences.getStringSet("profile_names", Collections.emptySet());
        if (namesSet.isEmpty()) { toast("Nenhum perfil salvo."); return; }
        String[] names = namesSet.toArray(new String[0]);
        java.util.Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
        new AlertDialog.Builder(this).setTitle("Excluir perfil salvo")
            .setItems(names, (d,index) -> new AlertDialog.Builder(this)
                .setTitle("Excluir '" + names[index] + "'?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (dd,ww) -> {
                    Set<String> copy = new HashSet<>(namesSet); copy.remove(names[index]);
                    preferences.edit().putStringSet("profile_names", copy)
                        .remove("profile_" + names[index]).apply();
                    toast("Perfil excluído. A seleção atual foi mantida.");
                }).show()).show();
    }

    private void applyProfile(int profile) {
        blockedPackages.clear();
        for (AppInfoRow app : installedApps) {
            if (isCriticalPackage(app.pkg)) continue;
            if (profile != 4 && app.system) continue;
            if (profile == 0 && !app.pkg.equals("com.openai.chatgpt")) blockedPackages.add(app.pkg);
            else if (profile == 1 && isSocial(app.pkg)) blockedPackages.add(app.pkg);
            else if (profile == 2 && isSyncHeavy(app.pkg)) blockedPackages.add(app.pkg);
            else if (profile == 3 || profile == 4) blockedPackages.add(app.pkg);
        }
        saveBlockedList();
        updateBlockResults(blockSearchValue);
        if (BlockVpnService.isActive()) {
            new AlertDialog.Builder(this).setTitle("Aplicar perfil à VPN ativa?")
                .setMessage("O bloqueio será reiniciado com os apps deste perfil. Pode haver breve interrupção da VPN.")
                .setNegativeButton("Depois", null)
                .setPositiveButton("Aplicar agora", (d,w) -> applySelectionToActiveVpn()).show();
        } else toast("Perfil preparado: " + blockedPackages.size() + " apps. Ative a VPN quando quiser.");
    }

    private boolean isSocial(String pkg) {
        return pkg.startsWith("com.facebook.") || pkg.startsWith("com.instagram.") ||
            pkg.startsWith("com.zhiliaoapp.") || pkg.startsWith("com.snapchat.") ||
            pkg.startsWith("org.telegram.") || pkg.startsWith("com.twitter.") ||
            pkg.startsWith("com.whatsapp") || pkg.startsWith("com.discord") ||
            pkg.startsWith("com.tiktok") || pkg.startsWith("com.reddit.");
    }

    private boolean isSyncHeavy(String pkg) {
        return pkg.contains("photos") || pkg.contains("drive") ||
            pkg.contains("backup") || pkg.contains("cloud") ||
            pkg.contains("appmanager") || pkg.contains("vending") ||
            pkg.contains("fashiongallery") || pkg.contains("sync");
    }

    private boolean isCriticalPackage(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);
        return p.equals(getPackageName().toLowerCase(Locale.ROOT)) ||
            p.equals("com.openai.chatgpt") ||
            p.equals("com.android.systemui") || p.equals("com.android.settings") ||
            p.equals("com.android.phone") || p.equals("com.android.dialer") ||
            p.equals("com.android.permissioncontroller") ||
            p.equals("com.google.android.permissioncontroller") ||
            p.equals("com.google.android.gms") || p.equals("com.google.android.gsf") ||
            p.contains("accessibility") || p.contains("talkback") ||
            p.contains("vpnguard") || p.contains("onesvpn") ||
            p.contains("telephony") || p.contains("emergency");
    }

    private void handleCheckbox(AppInfoRow item, CheckBox check, boolean selected) {
        if (item.pkg.equals(getPackageName())) {
            check.setOnCheckedChangeListener(null);
            check.setChecked(false);
            check.setOnCheckedChangeListener((b, value) -> handleCheckbox(item, check, value));
            toast("O Guardião não pode bloquear a si próprio.");
            return;
        }
        if (selected && isCriticalPackage(item.pkg) && !blockedPackages.contains(item.pkg)) {
            // Restaura sem disparar recursão e exige consentimento explícito.
            check.setOnCheckedChangeListener(null);
            check.setChecked(false);
            check.setOnCheckedChangeListener((b, value) -> handleCheckbox(item, check, value));
            new AlertDialog.Builder(this).setTitle("Serviço possivelmente essencial")
                .setMessage("Bloquear " + item.pkg + " pode interromper chamadas, rede, notificações " +
                    "e funções do Android. Deseja marcar este serviço mesmo assim?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Marcar", (d, w) -> {
                    blockedPackages.add(item.pkg); saveBlockedList();
                    check.setOnCheckedChangeListener(null);
                    check.setChecked(true);
                    check.setOnCheckedChangeListener((b, value) -> handleCheckbox(item, check, value));
                    if (BlockVpnService.isActive()) applySelectionToActiveVpn();
                }).show();
        } else {
            if (selected) blockedPackages.add(item.pkg);
            else blockedPackages.remove(item.pkg);
            saveBlockedList();
            if (BlockVpnService.isActive()) applySelectionToActiveVpn();
            updateBlockResults(blockSearchValue);
        }
    }

    private String vpnReport() {
        boolean active = BlockVpnService.isActive();
        String state = active ? "🛡️ VPN LOCAL ATIVA" : "🛡️ VPN LOCAL DESLIGADA";
        state += " • " + blockedPackages.size() + " selecionados";
        if (active) {
            state += "\n" + BlockVpnService.getAppliedCount() + " apps incluídos na VPN • " +
                BlockVpnService.getSkippedCount() + " indisponíveis";
            if (!BlockVpnService.getAppliedPackages().equals(blockedPackages))
                state += "\n⚠ Seleção atual difere da VPN: reinicie o bloqueio para aplicar.";
            state += "\nDescartados: " + formatBytes(BlockVpnService.bytesDiscarded());
        }
        if (BlockVpnService.getLastError() != null &&
            !BlockVpnService.getLastError().isEmpty()) state += "\n⚠ " + BlockVpnService.getLastError();
        return state;
    }

    // ====== Auditoria de permissões e cobertura ======
    private void makeDiagnosticsPanel() {
        LinearLayout control = row();
        Button refresh = button("🔎 Revisar", true);
        refresh.setOnClickListener(v -> { refreshDiagnostics(); updateDiagnosticsList(); });
        control.addView(refresh, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button preview = button("📄 Ver relatório", false);
        preview.setOnClickListener(v -> previewHtmlReport());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(42), 1);
        bp.leftMargin = dp(6); control.addView(preview, bp);
        contentActions.addView(control);

        LinearLayout exports = row();
        String[] captions = {"CSV completo", "HTML bonito", "JSON técnico"};
        int[] codes = {REQ_FULL_CSV, REQ_REPORT_HTML, REQ_REPORT_JSON};
        String[] filenames = {"guardiao_completo.csv", "guardiao_relatorio.html", "guardiao_diagnostico.json"};
        for (int k = 0; k < captions.length; k++) {
            final int idx = k;
            Button button = button(captions[k], false); button.setTextSize(11);
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, dp(43), 1);
            layout.setMargins(dp(2), dp(3), dp(2), 0);
            exports.addView(button, layout);
            button.setOnClickListener(v -> startFileExport(codes[idx], filenames[idx],
                idx == 0 ? "text/csv" : idx == 1 ? "text/html" : "application/json"));
        }
        contentActions.addView(exports);
        diagnosticsText = text("Revisando...", 11, NAVY, false);
        diagnosticsText.setPadding(0, dp(6), 0, dp(8));
        contentActions.addView(diagnosticsText);
        TextView title = text("Inventário de TODOS os aplicativos reconhecidos", 12, GREEN, true);
        title.setPadding(0, dp(6), 0, dp(4)); contentActions.addView(title);
        Spinner diagScope = new Spinner(this);
        String[] scopes = {"Todos os aplicativos", "Marcados", "Não marcados", "Incluídos na VPN", "Com tráfego no período"};
        diagScope.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, scopes));
        diagScope.setSelection(diagnosticsScope);
        diagScope.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                diagnosticsScope = pos; updateDiagnosticsList();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        contentActions.addView(diagScope, lp(-1, 38));
        EditText q = new EditText(this); q.setSingleLine(true); q.setTextSize(14);
        q.setBackground(rounded(Color.WHITE, 10)); q.setPadding(dp(12), dp(8), dp(12), dp(8));
        q.setHint("Buscar aplicativo no relatório..."); q.setText(diagnosticsSearch);
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                diagnosticsSearch = s.toString(); updateDiagnosticsList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        contentActions.addView(q, lp(-1, 42));
        listAdapter = new BaseAdapter() {
            @Override public int getCount() { return visibleDiagnostics.size(); }
            @Override public Object getItem(int p) { return visibleDiagnostics.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View old, ViewGroup parent) {
                AppInfoRow a = visibleDiagnostics.get(p);
                LinearLayout line = column(); line.setPadding(dp(14), dp(9), dp(10), dp(10));
                line.addView(text(a.label, 14, NAVY, true));
                line.addView(text(a.pkg + " • UID " + a.uid, 10, 0xFF516780, false));
                line.addView(text(appStatus(a.pkg), 11,
                    BlockVpnService.isActive() && BlockVpnService.getAppliedPackages().contains(a.pkg)
                        ? GREEN : 0xFF98602D, false));
                TrafficRow row = rowForUid(a.uid);
                String consumption = !hasScannedAtLeastOnce ? "Consumo não consultado" :
                    row == null ? "Tráfego indisponível" : "Histórico do UID " + formatBytes(row.total()) +
                        " (⬇ " + formatBytes(row.rx()) + " / ⬆ " + formatBytes(row.tx()) + ")";
                line.addView(text(consumption + "  • Uso tela: " +
                    formatDuration(foregroundMs.getOrDefault(a.pkg, 0L)), 10, 0xFF6B7D91, false));
                return line;
            }
        };
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((parent, view, index, id) -> {
            if (index < visibleDiagnostics.size()) showDiagnosticDetails(visibleDiagnostics.get(index));
        });
        bottomTip.setText("Relatórios CSV/HTML/JSON ficam no seu celular e incluem apps sem consumo. " +
            "Não publique os relatórios em GitHub público: eles expõem apps instalados.");
        refreshDiagnostics(); updateDiagnosticsList();
    }

    private void updateDiagnosticsList() {
        visibleDiagnostics.clear();
        String q = diagnosticsSearch.trim().toLowerCase(Locale.ROOT);
        Set<String> applied = BlockVpnService.getAppliedPackages();
        for (AppInfoRow a : installedApps) {
            boolean marked = blockedPackages.contains(a.pkg);
            boolean inVpn = BlockVpnService.isActive() && applied.contains(a.pkg);
            TrafficRow row = rowForUid(a.uid);
            if (diagnosticsScope == 1 && !marked) continue;
            if (diagnosticsScope == 2 && marked) continue;
            if (diagnosticsScope == 3 && !inVpn) continue;
            if (diagnosticsScope == 4 && (row == null || row.total() <= 0)) continue;
            if (!q.isEmpty() && !a.label.toLowerCase(Locale.ROOT).contains(q) &&
                !a.pkg.toLowerCase(Locale.ROOT).contains(q)) continue;
            visibleDiagnostics.add(a);
        }
        visibleDiagnostics.sort((a,b) -> {
            TrafficRow t1 = rowForUid(a.uid), t2 = rowForUid(b.uid);
            long n1 = t1 == null ? 0 : t1.total(), n2 = t2 == null ? 0 : t2.total();
            int comparison = Long.compare(n2, n1);
            return comparison != 0 ? comparison : a.label.compareToIgnoreCase(b.label);
        });
        if (activeTab == 3 && listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    private String appStatus(String pkg) {
        boolean marked = blockedPackages.contains(pkg);
        boolean inside = BlockVpnService.isActive() &&
            BlockVpnService.getAppliedPackages().contains(pkg);
        if (inside) return "🛡️ INCLUÍDO NA VPN DE BLOQUEIO (configuração, não prova pacotes)";
        if (marked && BlockVpnService.isActive()) return "⚠ MARCADO, MAS NÃO APLICADO";
        if (marked) return "☑ MARCADO • VPN desligada";
        return pkg.equals(getPackageName()) ? "ℹ️ PRÓPRIO GUARDIÃO (não bloqueável)" :
            "✅ FORA DO BLOQUEIO LOCAL";
    }

    private void showDiagnosticDetails(AppInfoRow a) {
        TrafficRow r = rowForUid(a.uid);
        String data = !hasScannedAtLeastOnce ? "Dados ainda não consultados." :
            r == null ? "Sem estatísticas disponíveis." :
            "⬇ " + formatBytes(r.rx()) + " • ⬆ " + formatBytes(r.tx()) + " • total " + formatBytes(r.total());
        String msg = a.pkg + " (UID " + a.uid + ")\n" +
            (a.system ? "App de sistema" : "App instalado") + "\n" + appStatus(a.pkg) +
            "\n\nConsumo do UID (histórico): " + data +
            "\nUso em primeiro plano: " + formatDuration(foregroundMs.getOrDefault(a.pkg,0L)) +
            "\nÚltima abertura estimada: " + timeOrUnknown(lastUsedMs.getOrDefault(a.pkg,0L)) +
            "\nPacotes que compartilham UID: " + packagesForUid(a.uid).size() +
            "\nPeríodo: " + periodDescription() +
            "\n\nNão é possível afirmar por esses números se ele está navegando AGORA ou se furou a VPN.";
        new AlertDialog.Builder(this).setTitle(a.label).setMessage(msg)
            .setNegativeButton("Fechar", null)
            .setNeutralButton("Configurações do app", (d,w) -> openAppSettings(a.pkg))
            .setPositiveButton(blockedPackages.contains(a.pkg) ? "Liberar" : "Bloquear", (d,w) -> {
                if (!blockedPackages.contains(a.pkg) && isCriticalPackage(a.pkg)) {
                    new AlertDialog.Builder(this).setTitle("Aplicativo essencial?")
                        .setMessage("Tem certeza que deseja selecionar " + a.pkg + "?")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Sim", (dd,ww) -> changeAppBlock(a.pkg, true)).show();
                } else changeAppBlock(a.pkg, !blockedPackages.contains(a.pkg));
            }).show();
    }

    private void changeAppBlock(String pkg, boolean block) {
        if (getPackageName().equals(pkg)) {
            toast("O Guardião não pode bloquear a si próprio."); return;
        }
        if (block) blockedPackages.add(pkg); else blockedPackages.remove(pkg);
        saveBlockedList();
        if (BlockVpnService.isActive()) applySelectionToActiveVpn();
        updateDiagnosticsList();
    }

    private String timeOrUnknown(long milliseconds) {
        return milliseconds > 0 ? stampFormat.format(new Date(milliseconds)) : "indisponível";
    }

    private void refreshDiagnostics() {
        if (diagnosticsText == null) return;
        boolean usage = hasUsagePermission();
        int installed = installedApps.size();
        int systemCount = 0;
        for (AppInfoRow app : installedApps) if (app.system) systemCount++;
        String vpnPerm;
        try { vpnPerm = VpnService.prepare(this) == null ? "✅ concedida" : "⚠ requer confirmação"; }
        catch (Exception ex) { vpnPerm = "⚠ indisponível"; }
        boolean notifications = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        String network = "desconhecida";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network active = cm.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
            if (caps == null) network = "sem rede detectada";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) network = "VPN";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) network = "Wi‑Fi";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) network = "móvel";
            else network = "outra";
        } catch (Exception ignored) { network = "requer permissão/indisponível"; }
        String full =
            "📱 " + Build.MANUFACTURER + " " + Build.MODEL + " • Android " + Build.VERSION.RELEASE +
            " (API " + Build.VERSION.SDK_INT + ")\n" +
            "📡 Rede detectada: " + network + "\n" +
            "✅ Dados de uso: " + (usage ? "AUTORIZADOS" : "NÃO AUTORIZADOS") + "\n" +
            "🔐 Permissão VPN: " + vpnPerm + "\n" +
            "🔔 Notificações: " + (notifications ? "autorizadas" : "não autorizadas") + "\n" +
            "📦 Apps identificados: " + installed + " (" + systemCount + " de sistema)\n" +
            "☑ Selecionados: " + blockedPackages.size() +
            " • incluídos na VPN: " + BlockVpnService.getAppliedCount() +
            " • inválidos/ignorados: " + BlockVpnService.getSkippedCount() + "\n" +
            "↪ Fora da VPN local: " + Math.max(0, installed - BlockVpnService.getAppliedCount()) +
            " apps conhecidos\n" +
            "🛡️ Serviço VPN: " + (BlockVpnService.isActive() ? "ATIVO" : "DESLIGADO") + "\n" +
            "🚫 Pacotes descartados (todos os apps): " + formatBytes(BlockVpnService.bytesDiscarded()) + "\n" +
            "📊 Consulta: " + periodDescription() + "\n" +
            "🔎 Consultas encerradas: " + hasScannedAtLeastOnce +
            " • seleção da VPN sincronizada: " + BlockVpnService.getAppliedPackages().equals(blockedPackages) + "\n" +
            "⏱️ Taxa global: ⬇ " + formatBytes(speedDown) + "/s, ⬆ " + formatBytes(speedUp) + "/s\n" +
            "ℹ️ Sem root • sem servidores • sem permissão INTERNET.\n" +
            "⚠ Acesso a histórico não confirma uso atual. Vários apps podem compartilhar UID; " +
            "o tráfego dessas linhas não deve ser somado duas vezes.";
        if (!usage) full += "\n\n⚠ Para ver consumo individual, libere 'Dados de uso do app' nas configurações.";
        if (BlockVpnService.getLastError() != null && !BlockVpnService.getLastError().isEmpty()) {
            full += "\n⚠ Último erro VPN: " + BlockVpnService.getLastError();
        }
        diagnosticsText.setText(full);
        if (activeTab == 3 && listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    private StringBuilder statusCsv() {
        return fullCsv();
    }

    private StringBuilder fullCsv() {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("Nome;Pacote;UID;Tipo;BloqueioSelecionado;VpnAtiva;IncluidoVPN;Status;WiFiRxBytes;WiFiTxBytes;MovelRxBytes;MovelTxBytes;TotalUidBytes;PacotesMesmoUID;TempoPrimeiroPlanoMs;UltimoUsoEpochMs;InicioConsultaEpochMs;FimConsultaEpochMs;ConsultaRealizada;DadosCompartilhadosUID\n");
        for (AppInfoRow a : sortedAppsForReport()) {
            TrafficRow r = rowForUid(a.uid);
            String[] pkgs = getPackageManager().getPackagesForUid(a.uid);
            int shared = pkgs == null ? 0 : pkgs.length;
            csv.append(csvText(a.label)).append(';').append(csvText(a.pkg)).append(';')
                .append(a.uid).append(';').append(a.system ? "Sistema" : "Usuario").append(';')
                .append(blockedPackages.contains(a.pkg)).append(';')
                .append(BlockVpnService.isActive()).append(';')
                .append(BlockVpnService.isActive() && BlockVpnService.getAppliedPackages().contains(a.pkg)).append(';')
                .append(csvText(appStatus(a.pkg))).append(';')
                .append(r == null ? 0 : r.wifiRx).append(';')
                .append(r == null ? 0 : r.wifiTx).append(';')
                .append(r == null ? 0 : r.mobileRx).append(';')
                .append(r == null ? 0 : r.mobileTx).append(';')
                .append(r == null ? 0 : r.total()).append(';')
                .append(shared).append(';')
                .append(foregroundMs.getOrDefault(a.pkg, 0L)).append(';')
                .append(lastUsedMs.getOrDefault(a.pkg, 0L)).append(';')
                .append(lastQueryStart).append(';').append(lastQueryEnd).append(';')
                .append(hasScannedAtLeastOnce).append(';').append(shared > 1).append('\n');
        }
        return csv;
    }

    private ArrayList<AppInfoRow> sortedAppsForReport() {
        ArrayList<AppInfoRow> rows = new ArrayList<>(installedApps);
        rows.sort((a,b) -> {
            TrafficRow aRow = rowForUid(a.uid), bRow = rowForUid(b.uid);
            long ba = aRow == null ? 0 : aRow.total(), bb = bRow == null ? 0 : bRow.total();
            int result = Long.compare(bb, ba);
            return result != 0 ? result : a.label.compareToIgnoreCase(b.label);
        });
        return rows;
    }

    // ====== Relatório offline completo: visualização HTML e exportações ======
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private String reportHtml() {
        ArrayList<AppInfoRow> appRows = sortedAppsForReport();
        Set<String> applied = BlockVpnService.getAppliedPackages();
        long grandTotal = 0, grandWiFi = 0, grandMobile = 0;
        int uidsWithUsage = 0, selectedCount = 0;
        Set<Integer> unique = new HashSet<>();
        for (TrafficRow r : trafficRows) if (unique.add(r.uid)) {
            grandTotal += r.total(); grandWiFi += r.wifiBytes; grandMobile += r.mobileBytes;
            if (r.total() > 0) uidsWithUsage++;
        }
        for (AppInfoRow r : appRows) if (blockedPackages.contains(r.pkg)) selectedCount++;
        StringBuilder html = new StringBuilder(56000);
        html.append("<!doctype html><html lang='pt-BR'><head><meta charset='utf-8'>")
            .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
            .append("<title>Guardião de Dados — Relatório</title><style>")
            .append("*{box-sizing:border-box}body{margin:0;background:#edf3fa;color:#122a47;font:14px system-ui,Arial,sans-serif}")
            .append("header{background:#0d2948;color:white;padding:24px 18px}header p{color:#d1e6f5}h1{margin:0;font-size:26px}")
            .append("main{max-width:1100px;margin:auto;padding:12px}section{background:white;border-radius:14px;padding:15px;margin:13px 0;")
            .append("box-shadow:0 2px 8px #29415b14}h2{margin:0 0 12px;font-size:19px}.cards{display:flex;flex-wrap:wrap;gap:8px}")
            .append(".card{flex:1 1 130px;background:#eaf2fb;border-radius:10px;padding:12px}.card b{display:block;font-size:19px}")
            .append(".warn{border-left:5px solid #c67b17;background:#fff6e7;padding:10px;margin:8px 0}")
            .append("table{width:100%;border-collapse:collapse;font-size:12px}td,th{padding:8px;border-bottom:1px solid #e4e9ef;")
            .append("text-align:left;vertical-align:top}th{background:#e8f0fa;position:sticky;top:0}.tablewrap{overflow:auto;max-height:65vh}")
            .append(".small{font-size:11px;color:#60738a;overflow-wrap:anywhere}.ok{color:#067e5f;font-weight:bold}")
            .append(".stop{color:#b15f13;font-weight:bold}.label{white-space:nowrap;font-weight:bold}")
            .append(".bar{height:13px;background:#dcecf6;border-radius:12px;overflow:hidden}.fill{height:100%;background:#0b88a3}")
            .append(".muted{color:#50627a}footer{text-align:center;font-size:12px;color:#60738a;padding:20px}")
            .append("</style></head><body><header><h1>🛡️ Guardião de Dados</h1>")
            .append("<p>Relatório offline • gerado ").append(escapeHtml(stampFormat.format(new Date())))
            .append(" • ").append(escapeHtml(Build.MANUFACTURER + " " + Build.MODEL))
            .append(" • Android ").append(Build.VERSION.SDK_INT).append("</p></header><main>");
        html.append("<section><h2>Resumo do aparelho</h2><div class='cards'>")
            .append("<div class='card'>Apps reconhecidos<b>").append(appRows.size()).append("</b></div>")
            .append("<div class='card'>Apps selecionados<b>").append(selectedCount).append("</b></div>")
            .append("<div class='card'>Na VPN ativa<b>")
            .append(BlockVpnService.isActive() ? applied.size() : 0).append("</b></div>")
            .append("<div class='card'>Wi‑Fi registrado<b>").append(formatBytes(grandWiFi)).append("</b></div>")
            .append("<div class='card'>Móvel registrado<b>").append(formatBytes(grandMobile)).append("</b></div>")
            .append("<div class='card'>Total de UIDs únicos<b>").append(formatBytes(grandTotal)).append("</b></div>")
            .append("</div><p><b>Período consultado:</b> ")
            .append(escapeHtml(periodDescription())).append("</p>")
            .append("<p><b>Consulta realizada:</b> ").append(hasScannedAtLeastOnce ? "Sim" : "Não")
            .append(" • <b>UIDs com tráfego:</b> ").append(uidsWithUsage)
            .append(" • <b>VPN:</b> ").append(BlockVpnService.isActive() ? "ativa" : "desligada")
            .append(" • <b>Descartados pela VPN (todos apps):</b> ")
            .append(formatBytes(BlockVpnService.bytesDiscarded())).append("</p>")
            .append("<div class='warn'><b>Como interpretar:</b> histórico de tráfego ≠ tráfego em tempo real. ")
            .append("App dentro da VPN ≠ pacote individual confirmado como bloqueado. ")
            .append("Quando dois apps têm mesmo UID, o total do UID aparece nas duas linhas e <b>não deve ser somado duas vezes</b>. ")
            .append("Dados do Android podem ter atraso e não permitir recortes exatos de 30 minutos. ")
            .append("Zero não prova ausência de conexão.</div></section>");
        ArrayList<TrafficRow> ranked = new ArrayList<>(trafficRows);
        ranked.sort((a,b) -> Long.compare(b.total(),a.total()));
        html.append("<section><h2>Top 12 grupos por consumo</h2>");
        long max = ranked.isEmpty() ? 1 : Math.max(1, ranked.get(0).total());
        int displayed = 0;
        for (TrafficRow r : ranked) {
            if (r.total() <= 0 || displayed++ >= 12) break;
            int percentage = (int)Math.max(1, r.total()*100L / max);
            html.append("<p class='small'><b>").append(escapeHtml(r.title)).append("</b> • ")
                .append(formatBytes(r.total())).append("</p><div class='bar'><div class='fill' style='width:")
                .append(percentage).append("%'></div></div>");
        }
        if (displayed == 0) html.append("<p>Sem dados de tráfego disponíveis.</p>");
        html.append("</section>");
        html.append("<section><h2>Inventário completo — ").append(appRows.size())
            .append(" aplicativos</h2><p class='small'>Ordenados por consumo histórico do UID, em ordem decrescente. ")
            .append("O símbolo de bloqueio mostra a configuração, não o responsável por um pico.</p>")
            .append("<div class='tablewrap'><table><thead><tr><th>App / pacote</th><th>Estado</th><th>Tráfego por UID</th>")
            .append("<th>Tempo de tela</th><th>Notas</th></tr></thead><tbody>");
        for (AppInfoRow a : appRows) {
            TrafficRow r = rowForUid(a.uid);
            Set<String> same = packagesForUid(a.uid);
            boolean inside = BlockVpnService.isActive() && applied.contains(a.pkg);
            boolean marked = blockedPackages.contains(a.pkg);
            html.append("<tr><td class='label'>").append(escapeHtml(a.label))
                .append("<div class='small'>").append(escapeHtml(a.pkg)).append(" · UID ").append(a.uid)
                .append("</div></td><td class='").append(inside ? "stop" : "ok").append("'>")
                .append(inside ? "Na VPN local" : marked ? "Marcado (não aplicado)" : "Fora da VPN")
                .append("</td><td>")
                .append(!hasScannedAtLeastOnce ? "Não consultado" : r == null ? "Indisponível" :
                    "⬇ " + formatBytes(r.rx()) + " · ⬆ " + formatBytes(r.tx()) +
                    "<br>Wi‑Fi " + formatBytes(r.wifiBytes) + " · Móvel " + formatBytes(r.mobileBytes) +
                    "<br><b>Total " + formatBytes(r.total()) + "</b>")
                .append("</td><td>").append(formatDuration(foregroundMs.getOrDefault(a.pkg,0L)))
                .append("<div class='small'>Última abertura: ")
                .append(escapeHtml(timeOrUnknown(lastUsedMs.getOrDefault(a.pkg,0L)))).append("</div></td>")
                .append("<td class='small'>").append(a.system ? "Sistema" : "Usuário")
                .append(same.size() > 1 ? " · Compartilhado com " + same.size() + " apps" : "")
                .append("</td></tr>");
        }
        html.append("</tbody></table></div></section>")
            .append("<section><h2>Diagnóstico</h2><p><b>Permissão de uso:</b> ")
            .append(hasUsagePermission() ? "concedida" : "não concedida")
            .append("</p><p><b>Solicitação de VPN:</b> ")
            .append(BlockVpnService.isActive() ? "conexão iniciada" : "serviço desligado")
            .append("</p><p><b>Pacotes aplicados:</b> ").append(BlockVpnService.getAppliedCount())
            .append(" • <b>ignorados:</b> ").append(BlockVpnService.getSkippedCount())
            .append("</p><p><b>Último erro:</b> ")
            .append(escapeHtml(BlockVpnService.getLastError())).append("</p>")
            .append("<p><b>Última consulta:</b> ").append(escapeHtml(timeOrUnknown(lastScanAt)))
            .append("</p><p>Dados locais. Este APK não possui permissão INTERNET.</p></section>")
            .append("</main><footer>Gerado pelo Guardião de Dados • não publique sem revisar sua privacidade.</footer>")
            .append("</body></html>");
        return html.toString();
    }

    private void previewHtmlReport() {
        try {
            WebView viewer = new WebView(this);
            viewer.getSettings().setJavaScriptEnabled(false);
            viewer.getSettings().setAllowFileAccess(false);
            viewer.getSettings().setAllowContentAccess(false);
            viewer.loadDataWithBaseURL(null, reportHtml(), "text/html", "UTF-8", null);
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Relatório offline")
                .setView(viewer)
                .setNegativeButton("Fechar", (d,w) -> viewer.destroy())
                .setPositiveButton("Exportar HTML", (d,w) -> {
                    viewer.destroy();
                    startFileExport(REQ_REPORT_HTML, "guardiao_relatorio.html", "text/html");
                }).create();
            dialog.setOnCancelListener(d -> viewer.destroy());
            dialog.show();
            if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, (int)(getResources()
                .getDisplayMetrics().heightPixels * 0.80));
        } catch (Exception e) { showError("Falha na prévia HTML: " + e.getMessage()); }
    }

    private String reportJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("formato", "guardiao-dados-diagnostico-v3");
            root.put("versao_app", "3.0");
            root.put("data_geracao_epoch_ms", System.currentTimeMillis());
            JSONObject device = new JSONObject();
            device.put("fabricante", Build.MANUFACTURER);
            device.put("modelo", Build.MODEL);
            device.put("android", Build.VERSION.RELEASE);
            device.put("api", Build.VERSION.SDK_INT);
            root.put("aparelho", device);
            JSONObject analysis = new JSONObject();
            analysis.put("permissao_uso", hasUsagePermission());
            analysis.put("consultado", hasScannedAtLeastOnce);
            analysis.put("consulta_inicio_ms", lastQueryStart);
            analysis.put("consulta_fim_ms", lastQueryEnd);
            analysis.put("ultima_consulta_ms", lastScanAt);
            analysis.put("observacao", "Estatísticas em blocos do Android, nem sempre exatas por minuto ou por app; UID compartilhado.");
            root.put("consulta", analysis);
            JSONObject vpn = new JSONObject();
            vpn.put("ativa", BlockVpnService.isActive());
            vpn.put("apps_configurados", blockedPackages.size());
            vpn.put("apps_aplicados", BlockVpnService.getAppliedCount());
            vpn.put("apps_ignorados", BlockVpnService.getSkippedCount());
            vpn.put("bytes_descartados_total", BlockVpnService.bytesDiscarded());
            vpn.put("ultimo_erro", BlockVpnService.getLastError());
            vpn.put("lista_atual_igual_vpn", BlockVpnService.getAppliedPackages().equals(blockedPackages));
            root.put("vpn", vpn);
            JSONObject speed = new JSONObject();
            speed.put("download_bytes_por_s_todos_apps", speedDown);
            speed.put("upload_bytes_por_s_todos_apps", speedUp);
            speed.put("pico_max_bytes_por_s", maxTotal);
            root.put("velocidade_aparelho", speed);
            JSONArray apps = new JSONArray();
            Set<String> applied = BlockVpnService.getAppliedPackages();
            for (AppInfoRow a : sortedAppsForReport()) {
                TrafficRow r = rowForUid(a.uid);
                JSONObject row = new JSONObject();
                row.put("nome", a.label); row.put("pacote", a.pkg); row.put("uid", a.uid);
                row.put("sistema", a.system);
                row.put("selecionado_bloqueio", blockedPackages.contains(a.pkg));
                row.put("incluido_na_vpn_ativa", BlockVpnService.isActive() && applied.contains(a.pkg));
                row.put("status", appStatus(a.pkg));
                row.put("wifi_download_bytes_uid", r == null ? 0 : r.wifiRx);
                row.put("wifi_upload_bytes_uid", r == null ? 0 : r.wifiTx);
                row.put("mobile_download_bytes_uid", r == null ? 0 : r.mobileRx);
                row.put("mobile_upload_bytes_uid", r == null ? 0 : r.mobileTx);
                row.put("total_uid_bytes", r == null ? 0 : r.total());
                row.put("tamanho_grupo_uid", packagesForUid(a.uid).size());
                row.put("tempo_tela_ms", foregroundMs.getOrDefault(a.pkg,0L));
                row.put("ultima_abertura_ms", lastUsedMs.getOrDefault(a.pkg,0L));
                apps.put(row);
            }
            root.put("aplicativos", apps);
            root.put("quantidade_aplicativos", apps.length());
            return root.toString(2);
        } catch (Exception e) {
            return "{\"erro\":\"Falha ao compor relatório local\"}";
        }
    }

    private void startCsvExport(int requestCode, String file) {
        startFileExport(requestCode, file, "text/csv");
    }

    private void startFileExport(int requestCode, String file, String mimeType) {
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType(mimeType);
        create.putExtra(Intent.EXTRA_TITLE, file);
        startActivityForResult(create, requestCode);
    }

    private static class LiveEvent {
        final long wallAt, elapsedAt, rx, tx;
        LiveEvent(long wallAt, long elapsedAt, long rx, long tx) {
            this.wallAt = wallAt; this.elapsedAt = elapsedAt;
            this.rx = rx; this.tx = tx;
        }
    }

    private void toast(String msg) { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show(); }
    private void showError(String msg) {
        new AlertDialog.Builder(this).setTitle("Aviso").setMessage(msg)
            .setPositiveButton("Entendi", null).show();
    }

    private static class AppInfoRow {
        final String label, pkg;
        final int uid;
        final boolean system;
        AppInfoRow(String name, String packageName, boolean system, int uid) {
            label = name; pkg = packageName; this.system = system; this.uid = uid;
        }
    }
    private static class TrafficRow {
        final String title, packageHint;
        final int uid;
        long wifiBytes, mobileBytes;
        long wifiRx, wifiTx, mobileRx, mobileTx;
        long rx() { return wifiRx + mobileRx; }
        long tx() { return wifiTx + mobileTx; }
        TrafficRow(String title, String packageHint, int uid) {
            this.title = title; this.packageHint = packageHint; this.uid = uid;
        }
        long total() { return wifiBytes + mobileBytes; }
    }
}

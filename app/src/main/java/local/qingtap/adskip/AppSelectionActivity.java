package local.qingtap.adskip;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AppSelectionActivity extends Activity {
    private enum Tab {
        SPLASH("开屏广告"), INLINE("内嵌广告"), BLOCKED("黑名单");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private final List<AppCatalog.Entry> all = new ArrayList<>();
    private final List<AppCatalog.Entry> shown = new ArrayList<>();
    private final Button[] tabs = new Button[Tab.values().length];
    private SharedPreferences prefs;
    private AppSelection selection;
    private EntryAdapter adapter;
    private TextView summary;
    private TextView empty;
    private EditText search;
    private Tab tab = Tab.SPLASH;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = AppPrefs.open(this);
        if (state != null) tab = Tab.values()[state.getInt("tab")];
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PAPER);
        root.setFocusableInTouchMode(true);
        if (android.os.Build.VERSION.SDK_INT >= 35) Ui.insetRoot(root);
        setContentView(root);
        root.requestFocus();

        LinearLayout header = Ui.column(this, 16);
        root.addView(header);
        Ui.add(this, header, Ui.text(this, "选择要处理的 App", 22, Ui.INK, true), 0);
        summary = Ui.text(this, "更改即时保存", 13, Ui.MUTED, false);
        Ui.add(this, header, summary, 4);

        LinearLayout tabRow = new LinearLayout(this);
        Ui.add(this, header, tabRow, 10);
        for (Tab mode : Tab.values()) {
            Button button = Ui.button(this, mode.label, false, view -> {
                if (tab == mode) return;
                tab = mode;
                updateTabs();
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (mode != Tab.SPLASH) params.leftMargin = Ui.dp(this, 6);
            tabRow.addView(button, params);
            tabs[mode.ordinal()] = button;
        }
        updateTabs();

        search = new EditText(this);
        search.setHint("搜索应用名称或包名");
        search.setTextSize(15);
        search.setSingleLine(true);
        if (state != null) search.setText(state.getString("search", ""));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, -2);
        int pad = Ui.dp(this, 16);
        searchParams.setMargins(pad, 0, pad, Ui.dp(this, 6));
        root.addView(search, searchParams);

        empty = Ui.text(this, "正在读取应用列表…", 14, Ui.MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(pad, pad, pad, pad);
        root.addView(empty, new LinearLayout.LayoutParams(-1, -2));

        ListView list = new ListView(this);
        list.setDividerHeight(0);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        adapter = new EntryAdapter();
        list.setAdapter(adapter);
        list.setEmptyView(empty);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                render();
            }
            @Override public void afterTextChanged(Editable editable) {}
        });

        Context context = getApplicationContext();
        new Thread(() -> {
            Set<String> previous = AppCatalog.previousUnchecked(context, prefs);
            Set<String> extra = AppPrefs.selected(prefs);
            extra.addAll(AppPrefs.appSelection(prefs, previous).blocked());
            List<AppCatalog.Entry> entries = AppCatalog.load(context, extra);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                selection = AppPrefs.appSelection(prefs, previous);
                AppPrefs.initializeBlacklist(prefs, selection);
                all.addAll(entries);
                render();
            });
        }, "qingtap-app-list").start();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("tab", tab.ordinal());
        state.putString("search", search.getText().toString());
    }

    private void updateTabs() {
        for (Tab mode : Tab.values()) {
            Button button = tabs[mode.ordinal()];
            boolean active = mode == tab;
            button.setTextColor(active ? Ui.WHITE : Ui.GREEN);
            button.setBackground(Ui.round(this, active ? Ui.GREEN : Ui.TINT, 12));
            button.setSelected(active);
        }
    }

    private void render() {
        if (selection == null) return;
        boolean blockedTab = tab == Tab.BLOCKED;
        Set<String> chosen = tab == Tab.INLINE ? selection.inline : selection.splash;
        summary.setText(blockedTab ? "开屏黑名单 " + selection.blockedSplash.size()
                + " · 内嵌黑名单 " + selection.blockedInline.size()
                : "已开启 " + chosen.size() + " 个 · 更改即时保存");
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (AppCatalog.Entry entry : all) {
            if (blockedTab && !selection.blockedSplash.contains(entry.pkg)
                    && !selection.blockedInline.contains(entry.pkg)) continue;
            if (entry.label.toLowerCase(Locale.ROOT).contains(query)
                    || entry.pkg.toLowerCase(Locale.ROOT).contains(query)) {
                shown.add(entry);
            }
        }
        empty.setText(query.isEmpty() && blockedTab ? "黑名单为空" : "没有匹配的应用");
        adapter.notifyDataSetChanged();
    }

    private void changeMode(String pkg, boolean embedded, boolean enabled) {
        selection = AppPrefs.appSelection(prefs, Collections.emptySet());
        selection.setEnabled(pkg, embedded, enabled);
        AppPrefs.saveAppSelection(prefs, selection);
        render();
    }

    private void changeBlacklist(String pkg, boolean embedded, boolean block) {
        selection = AppPrefs.appSelection(prefs, Collections.emptySet());
        selection.setBlocked(pkg, embedded, block);
        AppPrefs.saveAppSelection(prefs, selection);
        render();
    }

    private final class EntryAdapter extends ArrayAdapter<AppCatalog.Entry> {
        EntryAdapter() {
            super(AppSelectionActivity.this, 0, shown);
        }

        @Override public int getViewTypeCount() {
            return tabs.length;
        }

        @Override public int getItemViewType(int position) {
            return tab.ordinal();
        }

        @Override public View getView(int position, View convert, ViewGroup parent) {
            // Blacklist rows and app switches have different controls and drawable state.
            EntryRow row = convert instanceof EntryRow ? (EntryRow) convert : null;
            if (row == null || row.mode != tab) row = new EntryRow(tab);
            row.bind(shown.get(position));
            return row;
        }
    }

    private final class EntryRow extends LinearLayout {
        private final Tab mode;
        private final ImageView icon;
        private final TextView name;
        private final TextView pkg;
        private final Switch toggle;
        private final Button blacklist;
        private final Button inlineBlacklist;
        private String boundPackage;

        EntryRow(Tab mode) {
            super(AppSelectionActivity.this);
            this.mode = mode;
            setGravity(Gravity.CENTER_VERTICAL);
            int pad = Ui.dp(AppSelectionActivity.this, 10);
            setPadding(pad, pad, pad, pad);

            icon = new ImageView(AppSelectionActivity.this);
            int iconSize = Ui.dp(AppSelectionActivity.this, 34);
            addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

            LinearLayout texts = new LinearLayout(AppSelectionActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1);
            textParams.leftMargin = pad;
            addView(texts, textParams);
            name = Ui.text(AppSelectionActivity.this, "", 15, Ui.INK, false);
            pkg = Ui.text(AppSelectionActivity.this, "", 12, Ui.MUTED, false);
            pkg.setMaxLines(1);
            pkg.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name);
            texts.addView(pkg);

            blacklist = Ui.button(AppSelectionActivity.this, "", false, null);
            blacklist.setTextSize(12);
            addView(blacklist);
            inlineBlacklist = Ui.button(AppSelectionActivity.this, "", false, null);
            inlineBlacklist.setTextSize(12);
            LinearLayout.LayoutParams inlineParams = new LinearLayout.LayoutParams(-2, -2);
            inlineParams.leftMargin = Ui.dp(AppSelectionActivity.this, 6);
            addView(inlineBlacklist, inlineParams);
            inlineBlacklist.setVisibility(mode == Tab.BLOCKED ? VISIBLE : GONE);
            toggle = new Switch(AppSelectionActivity.this);
            toggle.setMinHeight(Ui.dp(AppSelectionActivity.this, 48));
            addView(toggle);
            toggle.setVisibility(mode == Tab.BLOCKED ? GONE : VISIBLE);
        }

        void bind(AppCatalog.Entry entry) {
            icon.setImageDrawable(entry.icon);
            name.setText(entry.label);
            pkg.setText(entry.pkg);
            boolean newApp = !entry.pkg.equals(boundPackage);
            boundPackage = entry.pkg;
            boolean inBlacklist = mode == Tab.BLOCKED;
            boolean embedded = mode == Tab.INLINE;
            bindBlacklist(blacklist, entry, embedded, newApp);
            if (inBlacklist) {
                bindBlacklist(inlineBlacklist, entry, true, newApp);
                return;
            }

            toggle.setOnCheckedChangeListener(null);
            Set<String> chosen = embedded ? selection.inline : selection.splash;
            boolean checked = chosen.contains(entry.pkg);
            boolean changed = toggle.isChecked() != checked;
            if (changed) toggle.setChecked(checked);
            if (newApp || changed) {
                toggle.setPressed(false);
                toggle.jumpDrawablesToCurrentState();
            }
            toggle.setContentDescription(entry.label + " " + mode.label);
            toggle.setOnCheckedChangeListener((button, value) -> changeMode(entry.pkg, embedded, value));
        }

        private void bindBlacklist(Button button, AppCatalog.Entry entry, boolean embedded, boolean newApp) {
            Set<String> blacklist = embedded ? selection.blockedInline : selection.blockedSplash;
            boolean blocked = blacklist.contains(entry.pkg);
            String category = embedded ? "内嵌" : "开屏";
            String label = (blocked ? "移出" : "拉黑") + category;
            if (newApp || !TextUtils.equals(button.getText(), label)) {
                button.setPressed(false);
                button.setText(label);
                button.jumpDrawablesToCurrentState();
            }
            button.setContentDescription(entry.label + " · " + (blocked ? "移出" : "加入") + category + "黑名单");
            button.setOnClickListener(view -> changeBlacklist(entry.pkg, embedded, !blocked));
        }
    }
}

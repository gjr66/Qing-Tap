package local.qingtap.adskip;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

// A searchable, icon bearing app picker. Returns the package name through setResult.
public final class AppPickerActivity extends Activity {
    static final String EXTRA_RESULT = "picked_package";

    private final List<AppCatalog.Entry> all = new ArrayList<>();
    private final List<AppCatalog.Entry> shown = new ArrayList<>();
    private EntryAdapter adapter;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = Ui.column(this, 16);
        body.setBackgroundColor(Ui.PAPER);
        setContentView(body);

        Ui.add(this, body, Ui.text(this, "选择 App", 24, Ui.INK, true), 4);

        EditText search = new EditText(this);
        search.setHint("搜索名称或包名，例如 bili");
        search.setTextSize(15);
        search.setSingleLine(true);
        Ui.add(this, body, search, 8);

        ListView list = new ListView(this);
        list.setDividerHeight(0);
        Ui.add(this, body, list, 8);

        all.addAll(AppCatalog.load(this, new HashSet<>(AppPrefs.selected(AppPrefs.open(this)))));
        shown.addAll(all);
        adapter = new EntryAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            AppCatalog.Entry entry = shown.get(position);
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_RESULT, entry.pkg));
            finish();
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                filter(text == null ? "" : text.toString());
            }

            @Override public void afterTextChanged(Editable editable) {
            }
        });
        filter("");
    }

    private void filter(String raw) {
        String query = raw.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        if (query.isEmpty()) {
            shown.addAll(all);
        } else {
            for (AppCatalog.Entry entry : all) {
                if (entry.label.toLowerCase(Locale.ROOT).contains(query)
                        || entry.pkg.toLowerCase(Locale.ROOT).contains(query)) {
                    shown.add(entry);
                }
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private final class EntryAdapter extends ArrayAdapter<AppCatalog.Entry> {
        EntryAdapter() {
            super(AppPickerActivity.this, 0, shown);
        }

        @Override public int getCount() {
            return shown.size();
        }

        @Override public AppCatalog.Entry getItem(int position) {
            return shown.get(position);
        }

        @Override public View getView(int position, View convert, ViewGroup parent) {
            LinearLayout row = new LinearLayout(AppPickerActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = Ui.dp(AppPickerActivity.this, 10);
            row.setPadding(pad, pad, pad, pad);

            AppCatalog.Entry entry = shown.get(position);
            ImageView icon = new ImageView(AppPickerActivity.this);
            Drawable drawable = entry.icon;
            if (drawable != null) icon.setImageDrawable(drawable);
            int size = Ui.dp(AppPickerActivity.this, 34);
            row.addView(icon, new LinearLayout.LayoutParams(size, size));

            LinearLayout texts = new LinearLayout(AppPickerActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textsParams.leftMargin = Ui.dp(AppPickerActivity.this, 12);
            row.addView(texts, textsParams);
            texts.addView(Ui.text(AppPickerActivity.this, entry.label, 15, Ui.INK, false));
            texts.addView(Ui.text(AppPickerActivity.this, entry.pkg, 12, Ui.MUTED, false));
            return row;
        }
    }
}

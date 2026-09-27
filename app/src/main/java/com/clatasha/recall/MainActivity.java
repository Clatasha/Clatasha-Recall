package com.clatasha.recall;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity {
    private TextView status;
    private LinearLayout results;
    private EditText search;
    private final RecallDatabaseHolder holder = new RecallDatabaseHolder();

    private static final class RecallDatabaseHolder { RecallDatabase db; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        holder.db = new RecallDatabase(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 24, 20, 12);
        setContentView(root);
        TextView heading = new TextView(this);
        heading.setText("Clatasha Recall");
        heading.setTextSize(26);
        root.addView(heading);
        TextView explanation = new TextView(this);
        explanation.setText("Saves only new, visible notifications from apps you choose. Data stays on this device. Notification history does not prove a message was deleted.");
        root.addView(explanation);
        status = new TextView(this);
        root.addView(status);
        Button permission = new Button(this);
        permission.setText("Notification access settings");
        permission.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        root.addView(permission);
        Button apps = new Button(this);
        apps.setText("Choose apps to save");
        apps.setOnClickListener(v -> chooseApps());
        root.addView(apps);
        Button clear = new Button(this);
        clear.setText("Delete saved history");
        clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Delete all saved notifications?")
                .setPositiveButton("Delete", (dialog, which) -> { holder.db.clear(); refresh(); })
                .setNegativeButton("Cancel", null).show());
        root.addView(clear);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search saved notifications");
        root.addView(search);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            public void afterTextChanged(Editable e) {}
        });
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
    }

    @Override protected void onResume() {
        super.onResume();
        NotificationManager manager = getSystemService(NotificationManager.class);
        boolean granted = manager != null && manager.isNotificationListenerAccessGranted(new ComponentName(this, RecallListener.class));
        status.setText(granted ? "Notification access: enabled" : "Notification access: off. Turn it on in Android settings.");
        refresh();
    }

    private void chooseApps() {
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ApplicationInfo> installed = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo result : pm.queryIntentActivities(launcher, 0)) {
            String pkg = result.activityInfo.packageName;
            if (!pkg.equals(getPackageName()) && seen.add(pkg)) installed.add(result.activityInfo.applicationInfo);
        }
        installed.sort(Comparator.comparing(info -> pm.getApplicationLabel(info).toString().toLowerCase(java.util.Locale.ROOT)));
        String[] names = new String[installed.size()];
        Set<String> chosen = new HashSet<>(getSharedPreferences(RecallListener.PREFS, MODE_PRIVATE)
                .getStringSet(RecallListener.SELECTED, java.util.Collections.emptySet()));
        boolean[] checked = new boolean[installed.size()];
        for (int i = 0; i < installed.size(); i++) {
            names[i] = pm.getApplicationLabel(installed.get(i)).toString();
            checked[i] = chosen.contains(installed.get(i).packageName);
        }
        new AlertDialog.Builder(this).setTitle("Save notifications from")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> {
                    String pkg = installed.get(which).packageName;
                    if (isChecked) chosen.add(pkg); else chosen.remove(pkg);
                }).setPositiveButton("Save", (dialog, which) ->
                        getSharedPreferences(RecallListener.PREFS, MODE_PRIVATE).edit()
                                .putStringSet(RecallListener.SELECTED, chosen).apply())
                .setNegativeButton("Cancel", null).show();
    }

    private void refresh() {
        if (results == null || search == null) return;
        results.removeAllViews();
        List<RecallDatabase.Entry> entries = holder.db.search(search.getText().toString().trim());
        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No saved notifications yet.");
            results.addView(empty);
            return;
        }
        String lastGroup = "";
        for (RecallDatabase.Entry entry : entries) {
            String group = entry.app + " • " + entry.sender;
            if (!group.equals(lastGroup)) {
                TextView title = new TextView(this);
                title.setText(group);
                title.setTextSize(17);
                title.setPadding(0, 18, 0, 4);
                results.addView(title);
                lastGroup = group;
            }
            TextView item = new TextView(this);
            item.setText(DateFormat.getDateTimeInstance().format(new Date(entry.time)) + "\n" + entry.text);
            item.setPadding(12, 8, 12, 12);
            results.addView(item);
        }
    }
}

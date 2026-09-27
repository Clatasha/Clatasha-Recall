package com.clatasha.recall;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int BACK = 0xff08111f;
    private static final int PANEL = 0xff16243a;
    private static final int WHITE = 0xfff2f8ff;
    private static final int MUTED = 0xff9bb0c9;
    private static final int MINT = 0xff6df1d3;
    private TextView status;
    private LinearLayout results;
    private EditText search;
    private RecallDatabase database;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACK);
        getWindow().setNavigationBarColor(BACK);
        database = new RecallDatabase(this);
        database.removeOldReposts();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(12));
        root.setBackground(gradient(BACK, 0xff101d32, 0, 0));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(73)));

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_recall_mark);
        mark.setContentDescription("Clatasha Recall icon");
        mark.setBackground(gradient(0xff203650, 0xff12243c, dp(18), 0xff345975));
        mark.setElevation(dp(9));
        mark.setPadding(dp(2), dp(2), dp(2), dp(2));
        header.addView(mark, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout heading = column();
        LinearLayout.LayoutParams headSpace = new LinearLayout.LayoutParams(0, -2, 1);
        headSpace.leftMargin = dp(13);
        header.addView(heading, headSpace);
        heading.addView(label("CLATASHA", 11, MINT, true, 0));
        heading.addView(label("Recall", 29, WHITE, true, 0));

        TextView tagline = label("YOUR MOMENTS, KEPT CLOSE", 10, MUTED, true, 0);
        LinearLayout.LayoutParams tagSpace = new LinearLayout.LayoutParams(-1, -2);
        tagSpace.topMargin = dp(4);
        root.addView(tagline, tagSpace);

        LinearLayout accessCard = row();
        accessCard.setGravity(Gravity.CENTER_VERTICAL);
        accessCard.setPadding(dp(16), dp(10), dp(16), dp(10));
        accessCard.setBackground(gradient(0xff233854, 0xff15263d, dp(20), 0xff39516d));
        accessCard.setElevation(dp(8));
        LinearLayout.LayoutParams accessSpace = new LinearLayout.LayoutParams(-1, dp(70));
        accessSpace.topMargin = dp(19);
        root.addView(accessCard, accessSpace);
        TextView pulse = label("●", 20, MINT, true, 0);
        accessCard.addView(pulse);
        LinearLayout accessCopy = column();
        LinearLayout.LayoutParams acp = new LinearLayout.LayoutParams(0, -2, 1);
        acp.leftMargin = dp(9);
        accessCard.addView(accessCopy, acp);
        accessCopy.addView(label("NOTIFICATION ACCESS", 10, MUTED, true, 0));
        status = label("Checking access…", 15, WHITE, true, 0);
        accessCopy.addView(status);
        TextView arrow = label("›", 28, MINT, false, 0);
        accessCard.addView(arrow);
        accessCard.setContentDescription("Open notification access settings");
        accessCard.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        LinearLayout actions = row();
        LinearLayout.LayoutParams actionSpace = new LinearLayout.LayoutParams(-1, dp(52));
        actionSpace.topMargin = dp(16);
        root.addView(actions, actionSpace);
        TextView apps = action("＋  Choose apps", MINT, 0xff102e37, 0xff3a807c);
        actions.addView(apps, new LinearLayout.LayoutParams(0, -1, 1));
        apps.setOnClickListener(v -> chooseApps());
        TextView clear = action("Clear history", WHITE, 0xff2b3850, 0xff44536a);
        LinearLayout.LayoutParams clearSpace = new LinearLayout.LayoutParams(0, -1, 1);
        clearSpace.leftMargin = dp(10);
        actions.addView(clear, clearSpace);
        clear.setOnClickListener(v -> confirmClear());

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(WHITE);
        search.setHintTextColor(MUTED);
        search.setTextSize(15);
        search.setHint("⌕   Search your history");
        search.setPadding(dp(17), 0, dp(17), 0);
        search.setBackground(gradient(0xff1b2b43, 0xff152238, dp(17), 0xff344a64));
        LinearLayout.LayoutParams searchSpace = new LinearLayout.LayoutParams(-1, dp(52));
        searchSpace.topMargin = dp(22);
        root.addView(search, searchSpace);

        TextView section = label("SAVED MOMENTS", 11, MINT, true, 0);
        LinearLayout.LayoutParams sectionSpace = new LinearLayout.LayoutParams(-1, -2);
        sectionSpace.topMargin = dp(23);
        sectionSpace.bottomMargin = dp(10);
        root.addView(section, sectionSpace);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        results = column();
        results.setPadding(0, dp(2), 0, dp(18));
        scroll.addView(results);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            public void afterTextChanged(Editable editable) {}
        });
    }

    @Override protected void onResume() {
        super.onResume();
        NotificationManager manager = getSystemService(NotificationManager.class);
        boolean granted = manager != null && manager.isNotificationListenerAccessGranted(
                new ComponentName(this, RecallListener.class));
        if (status != null) {
            status.setText(granted ? "Active · saving new alerts" : "Off · tap to enable");
            status.setTextColor(granted ? MINT : 0xffffbd87);
        }
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

    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("Delete saved history?")
                .setMessage("This removes every notification saved by Recall on this device.")
                .setPositiveButton("Delete", (dialog, which) -> { database.clear(); refresh(); })
                .setNegativeButton("Cancel", null).show();
    }

    private void refresh() {
        if (results == null || search == null) return;
        results.removeAllViews();
        List<RecallDatabase.Entry> entries = database.search(search.getText().toString().trim());
        if (entries.isEmpty()) {
            LinearLayout empty = column();
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(35), dp(20), dp(35));
            empty.setBackground(gradient(PANEL, 0xff102035, dp(20), 0xff2d4663));
            empty.addView(label("◷", 34, MINT, false, Gravity.CENTER));
            empty.addView(label("Nothing saved yet", 20, WHITE, true, Gravity.CENTER));
            TextView help = label("Choose an app and enable access. New notifications will appear here.", 13, MUTED, false, Gravity.CENTER);
            LinearLayout.LayoutParams helpSpace = new LinearLayout.LayoutParams(-1, -2);
            helpSpace.topMargin = dp(8);
            empty.addView(help, helpSpace);
            results.addView(empty);
            return;
        }

        Map<String, List<RecallDatabase.Entry>> groups = new LinkedHashMap<>();
        for (RecallDatabase.Entry entry : entries) {
            groups.computeIfAbsent(entry.app + "\u0000" + entry.sender, key -> new ArrayList<>()).add(entry);
        }
        for (List<RecallDatabase.Entry> group : groups.values()) addConversation(group);
    }

    private void addConversation(List<RecallDatabase.Entry> group) {
        RecallDatabase.Entry first = group.get(0);
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(gradient(0xff20334e, 0xff14243b, dp(20), 0xff354e6d));
        card.setElevation(dp(6));
        LinearLayout.LayoutParams cardSpace = new LinearLayout.LayoutParams(-1, -2);
        cardSpace.bottomMargin = dp(14);
        results.addView(card, cardSpace);

        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row);
        ImageView appIcon = new ImageView(this);
        try { appIcon.setImageDrawable(getPackageManager().getApplicationIcon(first.app)); }
        catch (PackageManager.NameNotFoundException ignored) { appIcon.setImageResource(R.drawable.ic_recall_mark); }
        appIcon.setContentDescription("Source app");
        appIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
        appIcon.setBackground(gradient(0xff344c67, 0xff223952, dp(13), 0xff4a6882));
        row.addView(appIcon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titles = column();
        LinearLayout.LayoutParams titleSpace = new LinearLayout.LayoutParams(0, -2, 1);
        titleSpace.leftMargin = dp(12);
        row.addView(titles, titleSpace);
        TextView sender = label(first.sender, 17, WHITE, true, 0);
        sender.setMaxLines(1);
        sender.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(sender);
        String appName = first.app;
        try {
            appName = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(first.app, 0)).toString();
        } catch (PackageManager.NameNotFoundException ignored) {}
        titles.addView(label(appName, 11, MUTED, false, 0));

        TextView count = label(String.valueOf(group.size()), 12, MINT, true, Gravity.CENTER);
        count.setBackground(gradient(0xff173d46, 0xff14313a, dp(13), 0xff38766f));
        row.addView(count, new LinearLayout.LayoutParams(dp(29), dp(26)));

        for (int i = 0; i < group.size(); i++) {
            RecallDatabase.Entry entry = group.get(i);
            if (i > 0) {
                View line = new View(this);
                line.setBackgroundColor(0xff344861);
                LinearLayout.LayoutParams lineSpace = new LinearLayout.LayoutParams(-1, dp(1));
                lineSpace.topMargin = dp(12);
                card.addView(line, lineSpace);
            }
            TextView message = label(entry.text, 14, WHITE, false, 0);
            LinearLayout.LayoutParams messageSpace = new LinearLayout.LayoutParams(-1, -2);
            messageSpace.topMargin = dp(11);
            card.addView(message, messageSpace);
            TextView time = label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(entry.time)), 11, MUTED, false, 0);
            LinearLayout.LayoutParams timeSpace = new LinearLayout.LayoutParams(-1, -2);
            timeSpace.topMargin = dp(6);
            card.addView(time, timeSpace);
        }
    }

    private TextView action(String title, int color, int base, int border) {
        TextView view = label(title, 13, color, true, Gravity.CENTER);
        view.setBackground(gradient(base + 0x00080808, base, dp(15), border));
        view.setElevation(dp(6));
        view.setClickable(true);
        return view;
    }

    private TextView label(String title, int size, int color, boolean bold, int gravity) {
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextSize(size);
        view.setTextColor(color);
        if (gravity != 0) view.setGravity(gravity);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private GradientDrawable gradient(int top, int bottom, int radius, int border) {
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom});
        shape.setCornerRadius(radius);
        if (border != 0) shape.setStroke(dp(1), border);
        return shape;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

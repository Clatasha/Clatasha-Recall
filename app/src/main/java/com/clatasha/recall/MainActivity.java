package com.clatasha.recall;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.KeyguardManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.CancellationSignal;
import android.text.InputType;
import android.widget.Toast;
import java.util.Arrays;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.widget.EditText;
import android.widget.CheckBox;
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
    private static final int REQUEST_UNLOCK = 410;
    private static final int REQUEST_ENABLE_LOCK = 411;
    private static final int REQUEST_EXPORT = 412;
    private LinearLayout rootView;
    private boolean authInProgress;
    private Uri pendingExportUri;
    private TextView lockOption;
    private TextView status;
    private LinearLayout results;
    private EditText search;
    private RecallDatabase database;
    private ScrollView historyScroll;
    private TextView undoBar;
    private LinearLayout adSlot;
    private ImageView adImage;
    private TextView adCaption;
    private int adIndex = 0;
    private final Runnable rotateAds = new Runnable() {
        @Override public void run() {
            if (!adsEnabled()) return;
            adIndex = (adIndex + 1) % 2;
            showBanner();
            handler.postDelayed(this, 20000);
        }
    };
    private boolean archiveOpen = false;
    private final Set<String> expanded = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideUndo;
    private Runnable undoAction;
    private final BroadcastReceiver historyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (RecallListener.ACTION_HISTORY_CHANGED.equals(intent.getAction())) refresh();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACK);
        getWindow().setNavigationBarColor(BACK);
        database = new RecallDatabase(this);
        database.removeOldReposts();
        database.cleanup(RetentionJob.days(this));
        RetentionJob.schedule(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(12));
        root.setBackground(gradient(BACK, 0xff101d32, 0, 0));
        rootView = root;
        if (lockEnabled()) root.setVisibility(View.INVISIBLE);
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
        heading.addView(label("BUILD 0.1.4", 10, MUTED, true, 0));
        ImageView gear = new ImageView(this);
        gear.setImageResource(android.R.drawable.ic_menu_manage);
        gear.setColorFilter(MINT);
        gear.setPadding(dp(10), dp(10), dp(10), dp(10));
        gear.setBackground(gradient(0xff253b53, 0xff14273e, dp(13), 0xff3c5873));
        gear.setContentDescription("Settings");
        gear.setOnClickListener(v -> showSettings());
        header.addView(gear, new LinearLayout.LayoutParams(dp(43), dp(43)));

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

        adSlot = column();
        adSlot.setPadding(dp(9), dp(7), dp(9), dp(9));
        adSlot.setBackground(gradient(0xff263b55, 0xff14263d, dp(17), 0xff42627e));
        adSlot.setElevation(dp(5));
        LinearLayout.LayoutParams adSpace = new LinearLayout.LayoutParams(-1, -2);
        adSpace.topMargin = dp(15);
        root.addView(adSlot, adSpace);
        adCaption = label("", 10, MUTED, true, 0);
        adCaption.setPadding(dp(3), 0, 0, dp(6));
        adSlot.addView(adCaption);
        adImage = new ImageView(this);
        adImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        adImage.setBackground(gradient(0xff1c304a, 0xff172941, dp(11), 0));
        adSlot.addView(adImage, new LinearLayout.LayoutParams(-1, dp(106)));
        adSlot.setOnClickListener(v -> {
            String url = adIndex == 0
                    ? "https://secure.bowetech.com/billing/store/web-hosting-packages/basic-web-hosting"
                    : "https://sideurl.com/f/sideurl/advertisement-contact";
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        });
        showBanner();
        adSlot.setVisibility(adsEnabled() ? View.VISIBLE : View.GONE);

        TextView section = label("SAVED MOMENTS", 11, MINT, true, 0);
        LinearLayout.LayoutParams sectionSpace = new LinearLayout.LayoutParams(-1, -2);
        sectionSpace.topMargin = dp(23);
        sectionSpace.bottomMargin = dp(10);
        root.addView(section, sectionSpace);

        historyScroll = new ScrollView(this);
        historyScroll.setFillViewport(true);
        historyScroll.setClipToPadding(false);
        historyScroll.setVerticalScrollBarEnabled(false);
        root.addView(historyScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        results = column();
        results.setPadding(0, dp(2), 0, dp(18));
        historyScroll.addView(results);
        undoBar = label("", 13, WHITE, true, Gravity.CENTER_VERTICAL);
        undoBar.setPadding(dp(16), 0, dp(16), 0);
        undoBar.setBackground(gradient(0xff234a55, 0xff15333c, dp(15), 0xff4a8d82));
        undoBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams undoSpace = new LinearLayout.LayoutParams(-1, dp(48));
        undoSpace.topMargin = dp(8);
        root.addView(undoBar, undoSpace);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            public void afterTextChanged(Editable editable) {}
        });
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(RecallListener.ACTION_HISTORY_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(historyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(historyReceiver, filter);
        if (lockEnabled() && !authInProgress) requestUnlock(false);
        if (adsEnabled()) {
            handler.removeCallbacks(rotateAds);
            handler.postDelayed(rotateAds, 20000);
        }
    }

    @Override protected void onStop() {
        unregisterReceiver(historyReceiver);
        handler.removeCallbacks(rotateAds);
        if (lockEnabled()) rootView.setVisibility(View.INVISIBLE);
        super.onStop();
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

    private boolean lockEnabled() {
        return getSharedPreferences("recall_settings", MODE_PRIVATE).getBoolean("app_lock", false);
    }

    private void requestUnlock(boolean enabling) {
        KeyguardManager guard = getSystemService(KeyguardManager.class);
        if (guard == null || !guard.isDeviceSecure()) {
            new AlertDialog.Builder(this).setTitle("Set a screen lock first")
                    .setMessage("Recall uses your phone's screen lock. Set a PIN, pattern or password in Android Settings, then return to enable app lock.")
                    .setPositiveButton("Open Settings", (d, which) ->
                            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)))
                    .setNegativeButton("Cancel", null).show();
            if (!enabling) finish();
            return;
        }
        authInProgress = true;
        if (!enabling) rootView.setVisibility(View.INVISIBLE);
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                    .setTitle(enabling ? "Enable Recall lock" : "Unlock Clatasha Recall")
                    .setSubtitle("Use your fingerprint or device screen lock")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();
            prompt.authenticate(new CancellationSignal(), getMainExecutor(),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            completeUnlock(true, enabling);
                        }
                        @Override public void onAuthenticationError(int code, CharSequence message) {
                            completeUnlock(false, enabling);
                        }
                    });
        } else {
            Intent intent = guard.createConfirmDeviceCredentialIntent(
                    "Clatasha Recall", "Confirm your device screen lock");
            if (intent == null) { completeUnlock(false, enabling); return; }
            startActivityForResult(intent, enabling ? REQUEST_ENABLE_LOCK : REQUEST_UNLOCK);
        }
    }

    private void completeUnlock(boolean success, boolean enabling) {
        authInProgress = false;
        if (success) {
            if (enabling) {
                getSharedPreferences("recall_settings", MODE_PRIVATE).edit()
                        .putBoolean("app_lock", true).apply();
                if (lockOption != null) lockOption.setText("App lock   •   On");
            }
            rootView.setVisibility(View.VISIBLE);
            if (pendingExportUri != null) {
                Uri target = pendingExportUri;
                pendingExportUri = null;
                askExportPassword(target);
            }
        } else if (!enabling) {
            pendingExportUri = null;
            finish();
        } else {
            rootView.setVisibility(View.VISIBLE);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_UNLOCK || requestCode == REQUEST_ENABLE_LOCK) {
            completeUnlock(resultCode == RESULT_OK, requestCode == REQUEST_ENABLE_LOCK);
        } else if (requestCode == REQUEST_EXPORT && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            if (lockEnabled() && rootView.getVisibility() != View.VISIBLE) {
                pendingExportUri = data.getData();
            } else askExportPassword(data.getData());
        }
    }

    private boolean adsEnabled() {
        return getSharedPreferences("recall_settings", MODE_PRIVATE).getBoolean("show_demo_ads", true);
    }

    private void setAdsEnabled(boolean enabled) {
        getSharedPreferences("recall_settings", MODE_PRIVATE).edit()
                .putBoolean("show_demo_ads", enabled).apply();
        adSlot.setVisibility(enabled ? View.VISIBLE : View.GONE);
        handler.removeCallbacks(rotateAds);
        if (enabled) handler.postDelayed(rotateAds, 20000);
    }

    private void showBanner() {
        if (adImage == null) return;
        adImage.setImageResource(adIndex == 0 ? R.drawable.banner_bowetech : R.drawable.banner_advertise);
        adImage.setContentDescription(adIndex == 0 ? "Bowetech web hosting advertisement"
                : "Advertise in Clatasha Recall");
        adCaption.setText(adIndex == 0 ? "SPONSORED   ·   BOWETECH   ·   1 / 2"
                : "SPONSORED   ·   ADVERTISE HERE   ·   2 / 2");
    }

    private void showSettings() {
        LinearLayout panel = column();
        panel.setPadding(dp(20), dp(12), dp(20), dp(12));
        TextView option = label(adsEnabled() ? "Show ads   •   On" : "Show ads   •   Off",
                16, WHITE, true, 0);
        option.setPadding(dp(12), dp(18), dp(12), dp(18));
        option.setBackground(gradient(0xff263b54, 0xff182a42, dp(13), 0xff3e5c78));
        panel.addView(option);
        TextView detail = label("Demo banners rotate every 20 seconds. You can turn them off for free.",
                12, MUTED, false, 0);
        LinearLayout.LayoutParams detailSpace = new LinearLayout.LayoutParams(-1, -2);
        detailSpace.topMargin = dp(12);
        panel.addView(detail, detailSpace);

        TextView retention = settingsOption("Auto clean up   •   " + retentionLabel(RetentionJob.days(this)));
        LinearLayout.LayoutParams optionSpace = new LinearLayout.LayoutParams(-1, -2);
        optionSpace.topMargin = dp(13);
        panel.addView(retention, optionSpace);
        retention.setOnClickListener(v -> chooseRetention(retention));

        TextView lock = settingsOption(lockEnabled() ? "App lock   •   On" : "App lock   •   Off");
        LinearLayout.LayoutParams lockSpace = new LinearLayout.LayoutParams(-1, -2);
        lockSpace.topMargin = dp(10);
        panel.addView(lock, lockSpace);
        lockOption = lock;
        lock.setOnClickListener(v -> {
            if (lockEnabled()) {
                new AlertDialog.Builder(this).setTitle("Turn off app lock?")
                        .setPositiveButton("Turn off", (d, which) -> {
                            getSharedPreferences("recall_settings", MODE_PRIVATE).edit()
                                    .putBoolean("app_lock", false).apply();
                            lock.setText("App lock   •   Off");
                            rootView.setVisibility(View.VISIBLE);
                        }).setNegativeButton("Cancel", null).show();
            } else {
                requestUnlock(true);
            }
        });

        TextView export = settingsOption("Export encrypted history");
        LinearLayout.LayoutParams exportSpace = new LinearLayout.LayoutParams(-1, -2);
        exportSpace.topMargin = dp(10);
        panel.addView(export, exportSpace);
        export.setOnClickListener(v -> startPrivateExport());

        TextView title = label("Recall settings", 20, WHITE, true, 0);
        title.setPadding(dp(22), dp(20), dp(20), dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this).setCustomTitle(title)
                .setView(panel).setNegativeButton("Close", null).create();
        option.setOnClickListener(v -> {
            if (!adsEnabled()) {
                setAdsEnabled(true);
                option.setText("Show ads   •   On");
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Turn off ads?")
                        .setMessage("Ads help fund Clatasha Recall. You can turn them off for free.")
                        .setPositiveButton("Turn off ads", (confirm, which) -> {
                            setAdsEnabled(false);
                            option.setText("Show ads   •   Off");
                        })
                        .setNegativeButton("Keep ads", null).show();
            }
        });
        dialog.setOnShowListener(ignored -> {
            dialog.getWindow().setBackgroundDrawable(gradient(0xff1b2d46, 0xff101d32, dp(20), 0xff41627e));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MINT);
        });
        dialog.show();
    }

    private TextView settingsOption(String text) {
        TextView view = label(text, 15, WHITE, true, Gravity.CENTER_VERTICAL);
        view.setPadding(dp(12), dp(15), dp(12), dp(15));
        view.setBackground(gradient(0xff263b54, 0xff182a42, dp(13), 0xff3e5c78));
        return view;
    }

    private String retentionLabel(int days) {
        return days == 0 ? "Off" : days + " days";
    }

    private void chooseRetention(TextView option) {
        int[] values = {0, 7, 30, 90, 365};
        String[] choices = {"Off — keep saved notifications",
                "7 days", "30 days", "90 days", "365 days"};
        new AlertDialog.Builder(this).setTitle("Auto clean up")
                .setMessage("Only unarchived notifications are removed. The selected limit applies immediately and is checked daily.")
                .setItems(choices, (dialog, index) -> {
                    int days = values[index];
                    if (days == 0) {
                        saveRetention(0, option);
                    } else {
                        new AlertDialog.Builder(this).setTitle("Use " + days + " days?")
                                .setMessage("Unarchived notifications older than " + days
                                        + " days will be deleted now and during daily cleanup. Archived notifications stay saved.")
                                .setPositiveButton("Apply", (confirm, which) -> saveRetention(days, option))
                                .setNegativeButton("Cancel", null).show();
                    }
                }).show();
    }

    private void saveRetention(int days, TextView option) {
        getSharedPreferences("recall_settings", MODE_PRIVATE).edit().putInt("retention_days", days).apply();
        RetentionJob.schedule(this);
        database.cleanup(days);
        option.setText("Auto clean up   •   " + retentionLabel(days));
        refresh();
    }

    private void startPrivateExport() {
        new AlertDialog.Builder(this).setTitle("Private export")
                .setMessage("Choose a destination, then create a password of at least 8 characters. Recall encrypts the file on this device before writing it. Keep your password safe; it cannot be recovered.")
                .setPositiveButton("Choose file", (d, which) -> {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/octet-stream");
                    String date = new java.text.SimpleDateFormat("yyyy-MM-dd",
                            java.util.Locale.ROOT).format(new Date());
                    intent.putExtra(Intent.EXTRA_TITLE, "Clatasha-Recall-" + date + ".recall");
                    startActivityForResult(intent, REQUEST_EXPORT);
                }).setNegativeButton("Cancel", null).show();
    }

    private void askExportPassword(Uri destination) {
        LinearLayout form = column();
        form.setPadding(dp(22), dp(8), dp(22), 0);
        EditText password = new EditText(this);
        password.setHint("Export password");
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(password);
        EditText confirm = new EditText(this);
        confirm.setHint("Confirm password");
        confirm.setSingleLine(true);
        confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(confirm);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Encrypt export")
                .setView(form).setPositiveButton("Encrypt & save", null)
                .setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    char[] first = password.getText().toString().toCharArray();
                    char[] second = confirm.getText().toString().toCharArray();
                    boolean valid = first.length >= 8 && Arrays.equals(first, second);
                    Arrays.fill(second, '\0');
                    if (!valid) {
                        Arrays.fill(first, '\0');
                        password.setError("Enter matching passwords of at least 8 characters");
                        return;
                    }
                    dialog.dismiss();
                    new Thread(() -> {
                        try {
                            int count = PrivateExport.write(getApplicationContext(), destination, first);
                            runOnUiThread(() -> Toast.makeText(this,
                                    "Encrypted " + count + " notifications", Toast.LENGTH_LONG).show());
                        } catch (Exception error) {
                            runOnUiThread(() -> Toast.makeText(this,
                                    "Export failed. Choose another file and try again.", Toast.LENGTH_LONG).show());
                        }
                    }, "Recall private export").start();
                }));
        dialog.show();
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
        installed.sort(Comparator.comparing(info -> pm.getApplicationLabel(info).toString()
                .toLowerCase(java.util.Locale.ROOT)));
        Set<String> chosen = new HashSet<>(getSharedPreferences(RecallListener.PREFS, MODE_PRIVATE)
                .getStringSet(RecallListener.SELECTED, java.util.Collections.emptySet()));

        LinearLayout picker = column();
        picker.setPadding(dp(14), dp(8), dp(14), 0);
        EditText filter = new EditText(this);
        filter.setSingleLine(true);
        filter.setHint("Search apps");
        filter.setTextColor(WHITE);
        filter.setHintTextColor(MUTED);
        filter.setBackground(gradient(0xff22334b, 0xff17263d, dp(12), 0xff45607d));
        filter.setPadding(dp(14), 0, dp(14), 0);
        picker.addView(filter, new LinearLayout.LayoutParams(-1, dp(50)));
        ScrollView listScroll = new ScrollView(this);
        LinearLayout.LayoutParams listSpace = new LinearLayout.LayoutParams(-1, dp(390));
        listSpace.topMargin = dp(8);
        picker.addView(listScroll, listSpace);
        LinearLayout list = column();
        listScroll.addView(list);
        Runnable populate = () -> {
            list.removeAllViews();
            String term = filter.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            for (ApplicationInfo info : installed) {
                String name = pm.getApplicationLabel(info).toString();
                if (!name.toLowerCase(java.util.Locale.ROOT).contains(term)
                        && !info.packageName.toLowerCase(java.util.Locale.ROOT).contains(term)) continue;
                CheckBox box = new CheckBox(this);
                box.setText(name);
                box.setTextColor(WHITE);
                box.setButtonTintList(android.content.res.ColorStateList.valueOf(MINT));
                box.setChecked(chosen.contains(info.packageName));
                box.setPadding(dp(8), dp(8), dp(8), dp(8));
                box.setOnCheckedChangeListener((button, checked) -> {
                    if (checked) chosen.add(info.packageName);
                    else chosen.remove(info.packageName);
                });
                list.addView(box);
            }
        };
        populate.run();
        filter.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                populate.run();
                listScroll.scrollTo(0, 0);
            }
            public void afterTextChanged(Editable value) {}
        });
        TextView pickerTitle = label("Choose apps to save", 19, WHITE, true, 0);
        pickerTitle.setPadding(dp(22), dp(20), dp(20), dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(pickerTitle)
                .setView(picker)
                .setPositiveButton("Save", (d, which) ->
                        getSharedPreferences(RecallListener.PREFS, MODE_PRIVATE).edit()
                                .putStringSet(RecallListener.SELECTED, chosen).apply())
                .setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getWindow().setBackgroundDrawable(gradient(0xff1b2d46, 0xff101d32, dp(20), 0xff41627e));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(MINT);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
        });
        dialog.show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("Delete saved history?")
                .setMessage("This removes every notification saved by Recall on this device.")
                .setPositiveButton("Delete", (dialog, which) -> { database.clear(); refresh(); })
                .setNegativeButton("Cancel", null).show();
    }

    private void refresh() {
        if (results == null || search == null) return;
        int scrollY = historyScroll.getScrollY();
        results.removeAllViews();
        List<RecallDatabase.Entry> entries = database.search(search.getText().toString().trim());
        Map<String, List<RecallDatabase.Entry>> saved = new LinkedHashMap<>();
        Map<String, List<RecallDatabase.Entry>> archived = new LinkedHashMap<>();
        for (RecallDatabase.Entry entry : entries) {
            Map<String, List<RecallDatabase.Entry>> section = entry.archived ? archived : saved;
            String key = entry.app + "\u0000" + entry.sender;
            section.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }
        if (saved.isEmpty()) {
            LinearLayout empty = column();
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(26), dp(20), dp(26));
            empty.setBackground(gradient(PANEL, 0xff102035, dp(20), 0xff2d4663));
            empty.addView(label("◷", 32, MINT, false, Gravity.CENTER));
            empty.addView(label("No saved notifications", 18, WHITE, true, Gravity.CENTER));
            empty.addView(label("Choose an app and enable access to start saving.", 12, MUTED, false, Gravity.CENTER));
            results.addView(empty);
        } else for (List<RecallDatabase.Entry> group : saved.values()) addConversation(group, false);

        int archivedCount = 0;
        for (List<RecallDatabase.Entry> group : archived.values()) archivedCount += group.size();
        TextView archiveTitle = label("ARCHIVE  ·  " + archivedCount
                + (archiveOpen ? "     ⌃" : "     ⌄"), 13, MINT, true, Gravity.CENTER_VERTICAL);
        archiveTitle.setPadding(dp(16), 0, dp(16), 0);
        archiveTitle.setBackground(gradient(0xff20344a, 0xff14243a, dp(15), 0xff34526a));
        archiveTitle.setContentDescription(archiveOpen ? "Collapse Archive" : "Expand Archive");
        archiveTitle.setOnClickListener(v -> { archiveOpen = !archiveOpen; refresh(); });
        LinearLayout.LayoutParams archiveSpace = new LinearLayout.LayoutParams(-1, dp(48));
        archiveSpace.topMargin = dp(20);
        archiveSpace.bottomMargin = dp(12);
        results.addView(archiveTitle, archiveSpace);
        if (archiveOpen) {
            if (archived.isEmpty()) {
                results.addView(label("Archived notifications will appear here.", 12, MUTED, false, 0));
            } else for (List<RecallDatabase.Entry> group : archived.values()) addConversation(group, true);
        }
        historyScroll.post(() -> historyScroll.scrollTo(0, scrollY));
    }

    private void addConversation(List<RecallDatabase.Entry> group, boolean isArchive) {
        RecallDatabase.Entry first = group.get(0);
        String key = (isArchive ? "archive:" : "saved:") + first.app + "\u0000" + first.sender;
        boolean open = expanded.contains(key);
        LinearLayout card = column();
        card.setBackground(gradient(0xff20334e, 0xff14243b, dp(20), 0xff354e6d));
        card.setElevation(dp(6));
        LinearLayout.LayoutParams cardSpace = new LinearLayout.LayoutParams(-1, -2);
        cardSpace.bottomMargin = dp(12);
        results.addView(card, cardSpace);

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.addView(top);
        ImageView appIcon = new ImageView(this);
        try { appIcon.setImageDrawable(getPackageManager().getApplicationIcon(first.app)); }
        catch (PackageManager.NameNotFoundException ignored) {
            appIcon.setImageResource(isWhatsApp(first.app) ? R.drawable.ic_chat_fallback : R.drawable.ic_recall_mark);
        }
        appIcon.setContentDescription("Source app");
        appIcon.setPadding(dp(7), dp(7), dp(7), dp(7));
        appIcon.setBackground(gradient(0xff344c67, 0xff223952, dp(12), 0xff4a6882));
        top.addView(appIcon, new LinearLayout.LayoutParams(dp(45), dp(45)));

        LinearLayout titles = column();
        LinearLayout.LayoutParams titleSpace = new LinearLayout.LayoutParams(0, -2, 1);
        titleSpace.leftMargin = dp(11);
        top.addView(titles, titleSpace);
        String appName = displayName(first.app);
        TextView sender = label(appName + "  ·  " + first.sender, 15, WHITE, true, 0);
        sender.setMaxLines(1);
        sender.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(sender);
        TextView preview = label(first.text, 12, MUTED, false, 0);
        preview.setMaxLines(1);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(preview);
        TextView count = label(group.size() + (open ? "  ⌃" : "  ⌄"), 12, MINT, true, Gravity.CENTER);
        count.setMinWidth(dp(42));
        top.addView(count);
        top.setContentDescription(appName + ", " + first.sender + ", " + group.size()
                + " notifications. " + (open ? "Collapse" : "Expand"));
        top.setOnClickListener(v -> {
            if (expanded.contains(key)) expanded.remove(key);
            else expanded.add(key);
            refresh();
        });
        if (!open) return;
        for (RecallDatabase.Entry entry : group) addNotification(card, entry);
    }

    private void addNotification(LinearLayout card, RecallDatabase.Entry entry) {
        View line = new View(this);
        line.setBackgroundColor(0xff344861);
        card.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout item = column();
        item.setPadding(dp(16), dp(11), dp(16), dp(11));
        card.addView(item);
        item.addView(label(entry.text, 14, WHITE, false, 0));
        String time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(entry.time));
        TextView meta = label(time + "    •    Swipe → " + (entry.archived ? "Restore" : "Archive")
                + "    ← Delete", 10, MUTED, false, 0);
        LinearLayout.LayoutParams metaSpace = new LinearLayout.LayoutParams(-1, -2);
        metaSpace.topMargin = dp(6);
        item.addView(meta, metaSpace);
        attachSwipe(item, entry);
    }

    private void attachSwipe(View item, RecallDatabase.Entry entry) {
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        item.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY;
            boolean dragging;
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().cancel();
                        startX = event.getRawX(); startY = event.getRawY(); dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        float dy = event.getRawY() - startY;
                        if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                            dragging = true;
                            view.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        if (dragging) {
                            view.setTranslationX(Math.max(-dp(115), Math.min(dp(115), dx)));
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        float distance = event.getRawX() - startX;
                        view.animate().translationX(0).setDuration(160).start();
                        if (dragging && Math.abs(distance) >= dp(75)) {
                            applySwipe(entry, distance > 0);
                        } else if (!dragging) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setMessage(entry.text)
                                    .setPositiveButton(entry.archived ? "Restore" : "Archive",
                                            (dialog, which) -> applySwipe(entry, true))
                                    .setNegativeButton("Delete", (dialog, which) -> applySwipe(entry, false))
                                    .show();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().translationX(0).setDuration(160).start();
                        return true;
                }
                return true;
            }
        });
    }

    private void applySwipe(RecallDatabase.Entry entry, boolean toArchive) {
        Runnable reverse;
        String title;
        if (toArchive) {
            boolean newState = !entry.archived;
            database.setArchived(entry.id, newState);
            reverse = () -> database.setArchived(entry.id, entry.archived);
            title = newState ? "Archived" : "Restored";
        } else {
            database.delete(entry.id);
            reverse = () -> database.restore(entry);
            title = "Deleted";
        }
        refresh();
        if (hideUndo != null) handler.removeCallbacks(hideUndo);
        undoAction = reverse;
        undoBar.setText(title + "   ·   UNDO");
        undoBar.setVisibility(View.VISIBLE);
        undoBar.setOnClickListener(v -> {
            if (undoAction != null) undoAction.run();
            undoAction = null;
            if (hideUndo != null) handler.removeCallbacks(hideUndo);
            undoBar.setVisibility(View.GONE);
            refresh();
        });
        hideUndo = () -> { undoAction = null; undoBar.setVisibility(View.GONE); };
        handler.postDelayed(hideUndo, 6000);
    }

    private String displayName(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            if (pkg.equals("com.whatsapp.w4b")) return "WhatsApp Business";
            if (pkg.equals("com.whatsapp")) return "WhatsApp";
            return pkg;
        }
    }

    private boolean isWhatsApp(String pkg) {
        return pkg.equals("com.whatsapp") || pkg.equals("com.whatsapp.w4b");
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

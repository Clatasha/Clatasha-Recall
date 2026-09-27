package com.clatasha.recall;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public final class RetentionJob extends JobService {
    private static final int JOB_ID = 74103;

    static int days(Context context) {
        return context.getSharedPreferences("recall_settings", MODE_PRIVATE)
                .getInt("retention_days", 0);
    }

    static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        if (days(context) == 0) {
            scheduler.cancel(JOB_ID);
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, RetentionJob.class))
                .setPeriodic(24 * 60 * 60 * 1000L)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            int selectedDays = days(this);
            if (selectedDays > 0) new RecallDatabase(this).cleanup(selectedDays);
            jobFinished(params, false);
        }, "Recall retention").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        return false;
    }
}

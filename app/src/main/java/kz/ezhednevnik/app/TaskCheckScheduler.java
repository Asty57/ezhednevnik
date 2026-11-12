package kz.ezhednevnik.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class TaskCheckScheduler {
    static final String WORK = "task-check";
    static final String CH_ID = "tasks";

    static void schedule(Context c) {
        createChannel(c);
        Constraints cons = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                TaskCheckWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(cons)
                .build();
        try {
            WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                    WORK, ExistingPeriodicWorkPolicy.KEEP, req);
        } catch (Exception e) { /* ignore */ }
    }

    static void cancel(Context c) {
        try { WorkManager.getInstance(c).cancelUniqueWork(WORK); } catch (Exception e) { /* ignore */ }
    }

    static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "Задачи", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Уведомления о новых задачах");
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}

package kz.ezhednevnik.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        ReminderScheduler.createChannel(c);

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pf = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pf |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent cp = PendingIntent.getActivity(c, 0, open, pf);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(c, ReminderScheduler.CH_ID);
        else b = new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Ежедневник")
                .setContentText("Отметьте выполненные работы за сегодня")
                .setAutoCancel(true)
                .setContentIntent(cp);

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try { nm.notify(2001, b.build()); } catch (Exception e) { /* no permission */ }
        }

        SharedPreferences p = ReminderScheduler.prefs(c);
        if (p.getBoolean("on", false)) ReminderScheduler.schedule(c, p.getInt("h", 8), p.getInt("m", 0));
    }
}

package kz.ezhednevnik.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

public class ReminderScheduler {
    static final String CH_ID = "reminders";
    static final int REQ = 1001;

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("reminder", Context.MODE_PRIVATE);
    }

    static void save(Context c, boolean on, int h, int m) {
        prefs(c).edit().putBoolean("on", on).putInt("h", h).putInt("m", m).apply();
    }

    static void apply(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean("on", false)) schedule(c, p.getInt("h", 8), p.getInt("m", 0));
        else cancel(c);
    }

    static PendingIntent pi(Context c) {
        Intent i = new Intent(c, ReminderReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, REQ, i, flags);
    }

    static void schedule(Context c, int h, int m) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        long t = cal.getTimeInMillis();
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c));
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c));
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c));
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, t, pi(c));
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c));
        }
    }

    static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi(c));
    }

    static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH_ID, "Напоминания", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Ежедневные напоминания отметить работы");
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}

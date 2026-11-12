package kz.ezhednevnik.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        ReminderScheduler.apply(c);
        // возобновить фоновую проверку задач только если есть сохранённая сессия
        String login = c.getSharedPreferences("sess", Context.MODE_PRIVATE).getString("login", "");
        if (login != null && !login.isEmpty()) TaskCheckScheduler.schedule(c);
    }
}

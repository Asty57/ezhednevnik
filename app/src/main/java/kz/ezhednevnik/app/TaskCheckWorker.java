package kz.ezhednevnik.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TaskCheckWorker extends Worker {

    public TaskCheckWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        SharedPreferences sess = c.getSharedPreferences("sess", Context.MODE_PRIVATE);
        String myLogin = sess.getString("login", "");
        if (myLogin == null || myLogin.isEmpty()) return Result.success();

        try {
            // нужна авторизация для чтения базы
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                try { Tasks.await(FirebaseAuth.getInstance().signInAnonymously(), 20, TimeUnit.SECONDS); }
                catch (Exception e) { return Result.retry(); }
            }
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            DocumentSnapshot snap = Tasks.await(
                    db.document("data/users/team/tasks").get(), 25, TimeUnit.SECONDS);
            if (snap == null || !snap.exists()) return Result.success();
            Object listObj = snap.get("list");
            if (!(listObj instanceof List)) return Result.success();
            List<?> list = (List<?>) listObj;

            SharedPreferences np = c.getSharedPreferences("tasknotif", Context.MODE_PRIVATE);
            Set<String> seen = new HashSet<>(np.getStringSet("seen", new HashSet<>()));
            boolean seeded = np.getBoolean("seeded", false);

            List<Map<?, ?>> fresh = new ArrayList<>();
            Set<String> assignedNow = new HashSet<>();

            for (Object o : list) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> t = (Map<?, ?>) o;
                Object idO = t.get("id");
                if (idO == null) continue;
                String id = String.valueOf(idO);
                if (Boolean.TRUE.equals(t.get("closed"))) continue;

                boolean mine = false;
                Object asO = t.get("assignees");
                if (asO instanceof List) {
                    for (Object a : (List<?>) asO) {
                        if (a instanceof Map) {
                            Object lg = ((Map<?, ?>) a).get("login");
                            if (lg != null && myLogin.equalsIgnoreCase(String.valueOf(lg))) { mine = true; break; }
                        }
                    }
                }
                if (!mine) continue;

                Object engLogin = t.get("engineerLogin");
                if (engLogin != null && myLogin.equalsIgnoreCase(String.valueOf(engLogin))) continue;

                assignedNow.add(id);
                if (!seen.contains(id)) fresh.add(t);
            }

            seen.addAll(assignedNow);
            np.edit().putStringSet("seen", seen).putBoolean("seeded", true).apply();

            if (seeded && !fresh.isEmpty()) notifyTasks(c, fresh);
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void notifyTasks(Context c, List<Map<?, ?>> fresh) {
        String title, text;
        if (fresh.size() == 1) {
            Map<?, ?> t = fresh.get(0);
            String sys = str(t.get("system"));
            String obj = firstObject(t);
            title = "Новая задача";
            text = (sys.isEmpty() ? "" : sys) + (obj.isEmpty() ? "" : (sys.isEmpty() ? "" : " · ") + obj);
            if (text.isEmpty()) text = "Вам поставлена задача";
        } else {
            title = "Новые задачи";
            text = fresh.size() + " новых задач";
        }

        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(c, 3001, i, flags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(c, TaskCheckScheduler.CH_ID);
        else b = new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try { nm.notify(3001, b.build()); } catch (Exception e) { /* нет разрешения */ }
        }
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private static String firstObject(Map<?, ?> t) {
        Object objs = t.get("objects");
        if (objs instanceof List && !((List<?>) objs).isEmpty()) return str(((List<?>) objs).get(0));
        return str(t.get("object"));
    }
}

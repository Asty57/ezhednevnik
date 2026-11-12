package kz.ezhednevnik.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private WebView web;
    private String role = "Сотрудник";
    private String login = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        if (getIntent() != null) {
            if (getIntent().getStringExtra("role") != null) role = getIntent().getStringExtra("role");
            if (getIntent().getStringExtra("login") != null) login = getIntent().getStringExtra("login");
        }
        // если запуск без явного входа — берём сохранённую сессию
        android.content.SharedPreferences sp = getSharedPreferences("sess", MODE_PRIVATE);
        if (login == null || login.isEmpty()) { login = sp.getString("login", ""); role = sp.getString("role", "Сотрудник"); }
        if (login == null || login.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/www/index.html");

        ReminderScheduler.createChannel(this);
        ReminderScheduler.apply(this);

        // уведомления о новых задачах (фоновая проверка базы)
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2);
        }
        TaskCheckScheduler.schedule(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private void resolveJs(final int id, final String jsonText) {
        web.post(() -> web.evaluateJavascript("window.__fsResolve(" + id + "," + JSONObject.quote(jsonText) + ")", null));
    }
    private void rejectJs(final int id, final String msg) {
        web.post(() -> web.evaluateJavascript("window.__fsReject(" + id + "," + JSONObject.quote(msg == null ? "err" : msg) + ")", null));
    }

    @SuppressWarnings("unchecked")
    private static Object jsonToJava(Object v) {
        if (v instanceof JSONObject) {
            JSONObject o = (JSONObject) v;
            Map<String, Object> m = new HashMap<>();
            Iterator<String> it = o.keys();
            while (it.hasNext()) { String k = it.next(); m.put(k, jsonToJava(o.opt(k))); }
            return m;
        } else if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            List<Object> l = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) l.add(jsonToJava(a.opt(i)));
            return l;
        }
        return v;
    }

    private class Bridge {

        @JavascriptInterface
        public String getRole() { return role; }

        @JavascriptInterface
        public String getLogin() { return login; }

        @JavascriptInterface
        public void openAdmin() {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, AdminActivity.class)));
        }

        @JavascriptInterface
        public void logout() {
            getSharedPreferences("sess", MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("tasknotif", MODE_PRIVATE).edit().clear().apply();
            TaskCheckScheduler.cancel(MainActivity.this);
            runOnUiThread(() -> {
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                finish();
            });
        }

        @JavascriptInterface
        public void fsCall(final int id, final String op, final String path, final String json) {
            try {
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                final DocumentReference ref = db.document(path);
                if ("get".equals(op)) {
                    ref.get().addOnSuccessListener(snap -> {
                        if (snap.exists() && snap.getData() != null) {
                            resolveJs(id, new JSONObject(snap.getData()).toString());
                        } else resolveJs(id, "null");
                    }).addOnFailureListener(e -> rejectJs(id, e.getMessage()));
                } else if ("set".equals(op)) {
                    Map<String, Object> m = new HashMap<>();
                    if (json != null && !json.isEmpty()) {
                        JSONObject o = new JSONObject(json);
                        Iterator<String> it = o.keys();
                        while (it.hasNext()) { String k = it.next(); m.put(k, jsonToJava(o.opt(k))); }
                    }
                    ref.set(m).addOnSuccessListener(x -> resolveJs(id, "null"))
                            .addOnFailureListener(e -> rejectJs(id, e.getMessage()));
                } else {
                    rejectJs(id, "bad op");
                }
            } catch (Exception e) {
                rejectJs(id, e.getMessage());
            }
        }

        @JavascriptInterface
        public void setReminder(final boolean on, final int h, final int m) {
            ReminderScheduler.save(MainActivity.this, on, h, m);
            runOnUiThread(() -> {
                if (on && Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
                }
                ReminderScheduler.createChannel(MainActivity.this);
                ReminderScheduler.apply(MainActivity.this);
            });
        }

        @JavascriptInterface
        public boolean hasAsset(String path) {
            try (java.io.InputStream in = getAssets().open(path)) { return true; }
            catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public String saveFile(String fileName, String base64) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                String mime = fileName.endsWith(".xlsx")
                        ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        : "application/octet-stream";
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ezhednevnik");
                v.put(MediaStore.Downloads.IS_PENDING, 1);
                ContentResolver cr = getContentResolver();
                Uri uri;
                try { uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v); }
                catch (Exception ex) { uri = null; }
                if (uri == null) {
                    // запасной вариант — без подпапки, прямо в «Загрузки»
                    ContentValues v2 = new ContentValues();
                    v2.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    v2.put(MediaStore.Downloads.MIME_TYPE, mime);
                    v2.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    v2.put(MediaStore.Downloads.IS_PENDING, 1);
                    v = v2;
                    uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                }
                if (uri == null) return "Не удалось создать файл (нет доступа к «Загрузки»)";
                try (OutputStream out = cr.openOutputStream(uri)) {
                    if (out == null) return "Не удалось открыть файл";
                    out.write(bytes);
                }
                v.clear();
                v.put(MediaStore.Downloads.IS_PENDING, 0);
                cr.update(uri, v, null, null);
                return "ok";
            } catch (Exception e) {
                return "Ошибка: " + e.getMessage();
            }
        }
    }
}

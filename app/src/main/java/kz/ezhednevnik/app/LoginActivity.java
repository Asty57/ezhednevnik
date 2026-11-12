package kz.ezhednevnik.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoginActivity extends Activity {

    static final String ACC_DOC = "data/users/team/accounts";
    private FirebaseAuth auth;
    private EditText loginField, passField;
    private Button btn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        auth = FirebaseAuth.getInstance();
        ensureAnon(null); // прогреваем анонимный доступ к базе

        // уже входили на этом устройстве — пропускаем форму
        SharedPreferences sp = getSharedPreferences("sess", MODE_PRIVATE);
        String savedLogin = sp.getString("login", "");
        if (!savedLogin.isEmpty()) { goMain(savedLogin, sp.getString("role", "Сотрудник")); return; }

        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.parseColor("#F8F5EF"));

        TextView title = new TextView(this);
        title.setText("Ежедневник");
        title.setTextSize(30);
        title.setTextColor(Color.parseColor("#C00D0D"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(24));
        root.addView(title);

        loginField = new EditText(this);
        loginField.setHint("Логин");
        loginField.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        loginField.setSingleLine(true);
        root.addView(loginField);

        passField = new EditText(this);
        passField.setHint("Пароль");
        passField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passField.setSingleLine(true);
        root.addView(passField);

        btn = new Button(this);
        btn.setText("Войти");
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#C00D0D"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doLogin(); }
        });
        root.addView(btn);

        TextView hint = new TextView(this);
        hint.setText("Первый вход под логином admin создаёт администратора.");
        hint.setTextColor(Color.parseColor("#777777"));
        hint.setPadding(0, dp(16), 0, 0);
        root.addView(hint);

        setContentView(root);
    }

    private void ensureAnon(final Runnable then) {
        if (auth.getCurrentUser() != null) { if (then != null) then.run(); return; }
        auth.signInAnonymously()
            .addOnSuccessListener(r -> { if (then != null) then.run(); })
            .addOnFailureListener(e -> {
                if (btn != null) btn.setEnabled(true);
                toastLong("Нет доступа к базе. Включите вход «Anonymous» в Firebase (Authentication → Sign-in method). " + e.getMessage());
            });
    }

    private void doLogin() {
        final String login = loginField.getText().toString().trim().toLowerCase();
        final String pass = passField.getText().toString().trim();
        if (login.isEmpty() || pass.isEmpty()) { toast("Введите логин и пароль"); return; }
        btn.setEnabled(false);
        ensureAnon(() -> checkAccounts(login, pass));
    }

    @SuppressWarnings("unchecked")
    private void checkAccounts(final String login, final String pass) {
        FirebaseFirestore.getInstance().document(ACC_DOC).get()
            .addOnSuccessListener(snap -> {
                List<Map<String, Object>> list = new ArrayList<>();
                Object raw = snap != null ? snap.get("list") : null;
                if (raw instanceof List) {
                    for (Object o : (List<Object>) raw) if (o instanceof Map) list.add((Map<String, Object>) o);
                }
                // ищем совпадение по логину
                Map<String, Object> found = null;
                boolean anyLogin = false;
                for (Map<String, Object> a : list) {
                    String al = a.get("login") != null ? a.get("login").toString().trim().toLowerCase() : "";
                    if (!al.isEmpty()) anyLogin = true;
                    if (!al.isEmpty() && al.equals(login)) { found = a; break; }
                }
                if (found != null) {
                    String ap = found.get("pass") != null ? found.get("pass").toString().trim() : "";
                    if (ap.equals(pass)) { success(login, primaryRole(found)); }
                    else { btn.setEnabled(true); toastLong("Неверный пароль для «" + login + "». Проверьте у администратора."); }
                    return;
                }
                // логина нет
                if (!anyLogin) {
                    // первый вход — создаём администратора
                    Map<String, Object> adm = new HashMap<>();
                    adm.put("id", "adm" + System.currentTimeMillis());
                    adm.put("name", login);
                    adm.put("login", login);
                    List<String> roles = new ArrayList<>(); roles.add("Администратор");
                    adm.put("roles", roles);
                    adm.put("pass", pass);
                    list.add(adm);
                    Map<String, Object> doc = new HashMap<>(); doc.put("list", list);
                    FirebaseFirestore.getInstance().document(ACC_DOC).set(doc)
                        .addOnSuccessListener(x -> success(login, "Администратор"))
                        .addOnFailureListener(e -> { btn.setEnabled(true); toastLong("Не удалось создать администратора: " + e.getMessage()); });
                } else {
                    btn.setEnabled(true);
                    toastLong("Такого логина нет. Администратор должен добавить его в «Сотрудники».");
                }
            })
            .addOnFailureListener(e -> { btn.setEnabled(true); toastLong("Ошибка доступа к базе: " + e.getMessage()); });
    }

    @SuppressWarnings("unchecked")
    private String primaryRole(Map<String, Object> a) {
        Object r = a.get("roles");
        if (r instanceof List && !((List<Object>) r).isEmpty()) {
            Object first = ((List<Object>) r).get(0);
            if (first != null) return first.toString();
        }
        if (a.get("role") != null) return a.get("role").toString();
        return "Сотрудник";
    }

    private void success(String login, String role) {
        SharedPreferences sp = getSharedPreferences("sess", MODE_PRIVATE);
        sp.edit().putString("login", login).putString("role", role).apply();
        goMain(login, role);
    }

    private void goMain(String login, String role) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("login", login);
        i.putExtra("role", role);
        startActivity(i);
        finish();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private void toastLong(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

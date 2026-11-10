package kz.ezhednevnik.app;

import android.app.Activity;
import android.content.Intent;
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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends Activity {

    static final String DOMAIN = "@ezhednevnik.app";
    private FirebaseAuth auth;
    private EditText loginField, passField;
    private Button btn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        auth = FirebaseAuth.getInstance();

        FirebaseUser cur = auth.getCurrentUser();
        if (cur != null) { fetchRoleAndGo(cur); return; }

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

        setContentView(root);
    }

    private void doLogin() {
        String login = loginField.getText().toString().trim().toLowerCase();
        String pass = passField.getText().toString();
        if (login.isEmpty() || pass.isEmpty()) { toast("Введите логин и пароль"); return; }
        btn.setEnabled(false);
        auth.signInWithEmailAndPassword(login + DOMAIN, pass)
                .addOnSuccessListener(r -> fetchRoleAndGo(auth.getCurrentUser()))
                .addOnFailureListener(e -> { btn.setEnabled(true); toast("Неверный логин или пароль"); });
    }

    private void fetchRoleAndGo(final FirebaseUser user) {
        final String login = user.getEmail() != null ? user.getEmail().replace(DOMAIN, "") : "";
        final String uid = user.getUid();
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(snap -> {
                    String role = snap.exists() && snap.getString("role") != null ? snap.getString("role") : null;
                    if (role == null) {
                        role = login.equals("admin") ? "Администратор" : "Сотрудник";
                        Map<String, Object> m = new HashMap<>();
                        m.put("login", login);
                        m.put("role", role);
                        FirebaseFirestore.getInstance().collection("users").document(uid).set(m);
                    }
                    goMain(login, role);
                })
                .addOnFailureListener(e -> goMain(login, login.equals("admin") ? "Администратор" : "Сотрудник"));
    }

    private void goMain(String login, String role) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("login", login);
        i.putExtra("role", role);
        startActivity(i);
        finish();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

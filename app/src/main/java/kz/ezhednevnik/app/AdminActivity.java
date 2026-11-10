package kz.ezhednevnik.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminActivity extends Activity {

    static final String DOMAIN = "@ezhednevnik.app";
    static final String[] ROLES = {"Начальник", "Инженер", "Сотрудник", "Администратор"};

    private EditText loginField, passField;
    private Spinner roleSpinner;
    private Button createBtn;
    private TextView listView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = dp(20);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#F8F5EF"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("Пользователи");
        title.setTextSize(24);
        title.setTextColor(Color.parseColor("#1b1b1b"));
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        loginField = new EditText(this);
        loginField.setHint("Логин нового сотрудника");
        loginField.setSingleLine(true);
        loginField.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(loginField);

        passField = new EditText(this);
        passField.setHint("Пароль");
        passField.setSingleLine(true);
        passField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(passField);

        roleSpinner = new Spinner(this);
        roleSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ROLES));
        roleSpinner.setSelection(2);
        root.addView(roleSpinner);

        createBtn = new Button(this);
        createBtn.setText("Создать аккаунт");
        createBtn.setTextColor(Color.WHITE);
        createBtn.setBackgroundColor(Color.parseColor("#C00D0D"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        createBtn.setLayoutParams(lp);
        createBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { createUser(); }
        });
        root.addView(createBtn);

        TextView lbl = new TextView(this);
        lbl.setText("Существующие аккаунты:");
        lbl.setPadding(0, dp(22), 0, dp(6));
        root.addView(lbl);

        listView = new TextView(this);
        listView.setTextColor(Color.parseColor("#444444"));
        root.addView(listView);

        setContentView(sv);
        loadUsers();
    }

    private void loadUsers() {
        FirebaseFirestore.getInstance().collection("users").get()
                .addOnSuccessListener(qs -> {
                    StringBuilder sb = new StringBuilder();
                    for (com.google.firebase.firestore.DocumentSnapshot d : qs.getDocuments()) {
                        sb.append("• ").append(d.getString("login"))
                          .append(" — ").append(d.getString("role")).append("\n");
                    }
                    listView.setText(sb.length() == 0 ? "Пока нет" : sb.toString());
                })
                .addOnFailureListener(e -> listView.setText("Не удалось загрузить список"));
    }

    private void createUser() {
        String login = loginField.getText().toString().trim().toLowerCase();
        String pass = passField.getText().toString();
        final String role = (String) roleSpinner.getSelectedItem();
        if (login.isEmpty() || pass.length() < 6) { toast("Логин и пароль (от 6 символов)"); return; }
        createBtn.setEnabled(false);

        // secondary app so the admin stays logged in
        FirebaseApp main = FirebaseApp.getInstance();
        FirebaseOptions opts = main.getOptions();
        FirebaseApp secondary;
        try { secondary = FirebaseApp.getInstance("secondary"); }
        catch (IllegalStateException e) { secondary = FirebaseApp.initializeApp(this, opts, "secondary"); }
        final FirebaseApp sec = secondary;
        FirebaseAuth secAuth = FirebaseAuth.getInstance(sec);

        final String flogin = login;
        secAuth.createUserWithEmailAndPassword(login + DOMAIN, pass)
                .addOnSuccessListener(res -> {
                    String uid = res.getUser().getUid();
                    Map<String, Object> m = new HashMap<>();
                    m.put("login", flogin);
                    m.put("role", role);
                    FirebaseFirestore.getInstance().collection("users").document(uid).set(m);
                    secAuth.signOut();
                    createBtn.setEnabled(true);
                    loginField.setText(""); passField.setText("");
                    toast("Аккаунт создан: " + flogin);
                    loadUsers();
                })
                .addOnFailureListener(e -> {
                    createBtn.setEnabled(true);
                    String msg = e.getMessage() != null && e.getMessage().contains("already") ? "Такой логин уже есть" : "Ошибка создания";
                    toast(msg);
                });
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

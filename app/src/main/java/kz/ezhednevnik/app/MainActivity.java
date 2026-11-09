package kz.ezhednevnik.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/www/index.html");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /** Мост между страницей и Android: сохранение отчётов в «Загрузки/Ежедневник». */
    private class Bridge {

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
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ежедневник");
                v.put(MediaStore.Downloads.IS_PENDING, 1);
                ContentResolver cr = getContentResolver();
                Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return "Не удалось создать файл";
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

        @JavascriptInterface
        public boolean hasAsset(String path) {
            try (InputStream in = getAssets().open(path)) {
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}

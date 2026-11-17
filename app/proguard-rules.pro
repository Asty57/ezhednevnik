# --- Мост WebView ↔ Java: методы вызываются по имени из JS, их нельзя удалять/переименовывать ---
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- Весь код приложения маленький — оставляем целиком (Bridge, Worker, ресиверы, Activity) ---
-keep class kz.ezhednevnik.app.** { *; }

# --- WorkManager: воркеры создаются рефлексией по имени класса ---
-keep class * extends androidx.work.ListenableWorker { public <init>(...); }

# --- Firebase/Firestore работают через Map (без POJO), свои consumer-правила подключаются из AAR ---
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# --- Guava: лишние предупреждения ---
-dontwarn com.google.common.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.**

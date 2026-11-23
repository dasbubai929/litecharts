# ── JavaScript interface (chart bridge) ──
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.litecharts.app.chart.ChartManager$ChartBridge { *; }

# ── OkHttp ──
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

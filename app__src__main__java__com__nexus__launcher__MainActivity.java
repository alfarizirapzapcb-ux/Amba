package com.nexus.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class MainActivity extends Activity {
    WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0e1514"));
        getWindow().setNavigationBarColor(Color.parseColor("#0e1514"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0e1514"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    class Bridge {
        @JavascriptInterface
        public String getApps() {
            try {
                PackageManager pm = getPackageManager();
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
                JSONArray arr = new JSONArray();
                for (ResolveInfo r : list) {
                    String pkg = r.activityInfo.packageName;
                    if (pkg.equals(getPackageName())) continue;
                    JSONObject o = new JSONObject();
                    o.put("name", r.loadLabel(pm).toString());
                    o.put("pkg", pkg);
                    arr.put(o);
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public boolean launch(String pkg) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        }

        @JavascriptInterface
        public boolean serviceOn() { return NexusService.instance != null; }

        @JavascriptInterface
        public void openAccessibility() {
            startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }

        @JavascriptInterface
        public void overlay(String pkg) {
            NexusService s = NexusService.instance;
            if (s != null) s.show(pkg);
        }

        @JavascriptInterface
        public void closeOverlay() {
            NexusService s = NexusService.instance;
            if (s != null) s.close();
        }

        @JavascriptInterface
        public void setGames(String json) {
            getSharedPreferences("nexus", 0).edit().putString("games", json).apply();
        }

        @JavascriptInterface
        public int getInt(String k, int d) { return getSharedPreferences("nexus", 0).getInt(k, d); }

        @JavascriptInterface
        public void setInt(String k, int v) { getSharedPreferences("nexus", 0).edit().putInt(k, v).apply(); }
    }
}

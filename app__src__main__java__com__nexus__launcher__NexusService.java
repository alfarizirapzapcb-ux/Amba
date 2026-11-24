package com.nexus.launcher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.function.IntConsumer;

/** Nexus Touch + Nexus Genie: overlay keymap, aim, crosshair, kecerahan/volume. */
public class NexusService extends AccessibilityService {
    static NexusService instance;
    static final String[] SHAPES = {"Mati", "Lingkaran", "Kotak", "Plus", "Titik"};
    static final int[] COLORS = {0xFFFF3B3B, 0xFF3DF0B0, 0xFFFFFFFF, 0xFFFFE14D, 0xFF4DA3FF};

    WindowManager wm;
    SharedPreferences sp;
    Handler h;
    AudioManager am;
    String pkg = "", dismissed = "";
    boolean edit = true, showing = false, askedWrite = false;
    int size;
    float alpha;
    final ArrayList<Btn> btns = new ArrayList<>();
    LinearLayout bar, side, content;
    WindowManager.LayoutParams barP, sideP, crossP;
    TextView modeBtn;
    View cross;

    // aim (geser untuk membidik)
    GestureDescription.StrokeDescription aimStroke;
    boolean aimBusy, aimActive;
    float aimCx, aimCy, aimLx, aimLy, pdx, pdy;

    static class Btn {
        WindowManager.LayoutParams bp, tp;
        TextView bv, tv;
        boolean turbo, tOn, down, aim;
        Runnable rep;
    }

    // ================= siklus layanan =================
    @Override protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        sp = getSharedPreferences("nexus", 0);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        h = new Handler(Looper.getMainLooper());
    }

    @Override public void onInterrupt() {}

    @Override public boolean onUnbind(Intent i) {
        hide();
        instance = null;
        return super.onUnbind(i);
    }

    /** Deteksi game otomatis: tampil saat game dibuka, sembunyi saat keluar. */
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (h == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || e.getPackageName() == null) return;
        String p = e.getPackageName().toString();
        if (transientPkg(p)) return;
        if (showing && p.equals(pkg)) return;
        if (isGame(p)) {
            if (!p.equals(dismissed)) doShow(p);
        } else {
            dismissed = "";
            if (showing) hide();
        }
    }

    boolean transientPkg(String p) {
        return p.equals("com.android.systemui") || p.equals(getPackageName()) || p.equals("android")
                || p.contains("inputmethod") || p.contains("keyboard");
    }

    boolean isGame(String p) {
        try {
            JSONArray a = new JSONArray(sp.getString("games", "[]"));
            for (int i = 0; i < a.length(); i++) if (a.getString(i).equals(p)) return true;
        } catch (Exception e) {}
        return false;
    }

    // ================= util =================
    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    int sw() { return getResources().getDisplayMetrics().widthPixels; }
    int sh() { return getResources().getDisplayMetrics().heightPixels; }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    void removeQuiet(View v) { if (v != null) try { wm.removeView(v); } catch (Exception e) {} }

    WindowManager.LayoutParams lp(int x, int y, int w, int hh) {
        int f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                w, hh, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, f, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = x;
        p.y = y;
        return p;
    }

    TextView txt(String s, int sz, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sz);
        v.setTextColor(color);
        return v;
    }

    TextView circle(String t, int color, int px) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.WHITE);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * 0.4f);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setStroke(dp(2), Color.WHITE);
        v.setBackground(g);
        return v;
    }

    TextView pad(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.WHITE);
        v.setTextSize(16);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(0x881D6B55);
        g.setStroke(dp(2), Color.WHITE);
        v.setBackground(g);
        return v;
    }

    TextView pbtn(String t, int color) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(18);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        return v;
    }

    TextView tbtn(String s, View.OnClickListener l) {
        TextView v = txt(s, 13, Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF16604A);
        g.setCornerRadius(dp(16));
        v.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), dp(6), dp(4));
        v.setLayoutParams(lp);
        v.setOnClickListener(l);
        return v;
    }

    SeekBar seek(int min, int max, int val, IntConsumer cb) {
        SeekBar s = new SeekBar(this);
        s.setMax(max - min);
        s.setProgress(Math.max(0, Math.min(max - min, val - min)));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) { if (user) cb.accept(p + min); }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        return s;
    }

    // ================= tampil / sembunyi =================
    /** Dipanggil dari MainActivity (thread mana pun). */
    void show(String p) {
        h.post(() -> {
            dismissed = "";
            if (showing && p.equals(pkg)) return;
            doShow(p);
        });
    }

    void close() { h.post(() -> { dismissed = pkg; hide(); }); }

    void doShow(String p) {
        hide();
        pkg = p;
        showing = true;
        size = dp(sp.getInt("size", 56));
        alpha = sp.getInt("alpha", 60) / 100f;
        load();
        edit = btns.isEmpty();
        buildBar();
        for (Btn b : btns) addBtnViews(b);
        refreshMode();
        applyCross();
    }

    void hide() {
        showing = false;
        aimActive = false;
        aimStroke = null;
        aimBusy = false;
        for (Btn b : new ArrayList<>(btns)) {
            release(b);
            removeQuiet(b.bv);
            setMarker(b, false);
        }
        btns.clear();
        removeQuiet(bar);
        removeQuiet(side);
        removeQuiet(cross);
        bar = null;
        side = null;
        cross = null;
    }

    // ================= profil per game =================
    void load() {
        btns.clear();
        try {
            JSONArray a = new JSONArray(sp.getString("p_" + pkg, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Btn b = new Btn();
                b.aim = o.optBoolean("a");
                int bw = b.aim ? dp(180) : size;
                b.bp = lp(o.getInt("bx"), o.getInt("by"), bw, bw);
                b.tp = lp(o.getInt("tx"), o.getInt("ty"), dp(28), dp(28));
                b.turbo = o.optBoolean("t");
                btns.add(b);
            }
        } catch (Exception e) {}
    }

    void save() {
        try {
            JSONArray a = new JSONArray();
            for (Btn b : btns) {
                JSONObject o = new JSONObject();
                o.put("bx", b.bp.x);
                o.put("by", b.bp.y);
                o.put("tx", b.tp.x);
                o.put("ty", b.tp.y);
                o.put("t", b.turbo);
                o.put("a", b.aim);
                a.put(o);
            }
            sp.edit().putString("p_" + pkg, a.toString()).apply();
        } catch (Exception e) {}
    }

    // ================= bar kecil =================
    void buildBar() {
        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xE61A2322);
        g.setCornerRadius(dp(24));
        bar.setBackground(g);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        barP = lp(dp(16), dp(24), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);

        TextView handle = pbtn("N", 0xFF3DF0B0);
        TextView add = pbtn("+", Color.WHITE);
        TextView addAim = pbtn("A", 0xFFFFE14D);
        modeBtn = pbtn("▶", Color.WHITE);
        TextView gear = pbtn("⚙", Color.WHITE);
        TextView closeBtn = pbtn("✕", 0xFFFF6677);

        handle.setOnTouchListener(new Drag(bar, barP));
        add.setOnClickListener(v -> addNew(false));
        addAim.setOnClickListener(v -> addNew(true));
        modeBtn.setOnClickListener(v -> { edit = !edit; refreshMode(); save(); });
        gear.setOnClickListener(v -> toggleSide());
        closeBtn.setOnClickListener(v -> { dismissed = pkg; hide(); });

        bar.addView(handle);
        bar.addView(add);
        bar.addView(addAim);
        bar.addView(modeBtn);
        bar.addView(gear);
        bar.addView(closeBtn);
        wm.addView(bar, barP);
    }

    void refreshMode() {
        if (modeBtn != null) modeBtn.setText(edit ? "▶" : "✎");
        for (Btn b : btns) {
            setMarker(b, edit);
            if (b.bv != null) b.bv.setAlpha(alpha);
        }
    }

    // ================= panel Nexus Genie =================
    void toggleSide() {
        if (side != null) { removeQuiet(side); side = null; return; }
        side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF00E1514);
        g.setCornerRadius(dp(16));
        side.setBackground(g);
        side.setPadding(dp(12), dp(8), dp(12), dp(12));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = txt("NEXUS GENIE", 16, 0xFF3DF0B0);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView back = pbtn("⇦", Color.WHITE);
        TextView power = pbtn("⏻", 0xFFFF6677);
        back.setOnClickListener(v -> toggleSide());
        power.setOnClickListener(v -> { dismissed = pkg; hide(); });
        head.addView(title);
        head.addView(back);
        head.addView(power);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        side.addView(head);
        side.addView(content);
        showMain();

        sideP = lp(dp(16), dp(84), dp(340), WindowManager.LayoutParams.WRAP_CONTENT);
        wm.addView(side, sideP);
    }

    LinearLayout tile(String icon, String name, View.OnClickListener l) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView i = txt(icon, 22, Color.WHITE);
        i.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF16604A);
        g.setCornerRadius(dp(10));
        g.setStroke(dp(2), 0xFF3DF0B0);
        i.setBackground(g);
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView n = txt(name, 10, Color.WHITE);
        n.setGravity(Gravity.CENTER);
        t.addView(i);
        t.addView(n);
        t.setOnClickListener(l);
        return t;
    }

    void showMain() {
        content.removeAllViews();
        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.addView(tile("☝", "Edit keymap", v -> { edit = true; refreshMode(); toggleSide(); }));
        tiles.addView(tile("⚙", "Pengaturan keymap", v -> showKeymap()));
        tiles.addView(tile("⌖", "Sensitivity", v -> showSens()));
        tiles.addView(tile("◎", "Crosshair", v -> showCross()));
        content.addView(tiles);

        int cur = 128;
        try { cur = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS); } catch (Exception e) {}
        content.addView(txt("☀ Kecerahan", 12, Color.WHITE));
        content.addView(seek(1, 255, cur, v -> setBrightness(v)));
        content.addView(txt("🔊 Volume", 12, Color.WHITE));
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        content.addView(seek(0, max, am.getStreamVolume(AudioManager.STREAM_MUSIC),
                v -> am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)));
    }

    void setBrightness(int v) {
        if (Settings.System.canWrite(this)) {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, v);
        } else if (!askedWrite) {
            askedWrite = true;
            toast("Izinkan Nexus mengubah setelan sistem untuk kecerahan");
            Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
    }

    void backRow() { content.addView(tbtn("◀ Kembali", v -> showMain())); }

    void showKeymap() {
        content.removeAllViews();
        backRow();
        content.addView(txt("Ukuran tombol", 12, Color.WHITE));
        content.addView(seek(40, 90, sp.getInt("size", 56), v -> applySize(v)));
        content.addView(txt("Opasitas tombol", 12, Color.WHITE));
        content.addView(seek(20, 100, sp.getInt("alpha", 60), v -> applyAlpha(v)));
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.addView(tbtn("Salin konfigurasi", v -> copyCfg()));
        r.addView(tbtn("Tempel konfigurasi", v -> pasteCfg()));
        content.addView(r);
    }

    void showSens() {
        content.removeAllViews();
        backRow();
        content.addView(txt("Sensitivitas horizontal (tombol A)", 12, Color.WHITE));
        content.addView(seek(10, 300, sp.getInt("sx", 100), v -> sp.edit().putInt("sx", v).apply()));
        content.addView(txt("Sensitivitas vertikal (tombol A)", 12, Color.WHITE));
        content.addView(seek(10, 300, sp.getInt("sy", 100), v -> sp.edit().putInt("sy", v).apply()));
    }

    void showCross() {
        content.removeAllViews();
        backRow();
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        TextView shapeBtn = tbtn("Bentuk: " + SHAPES[sp.getInt("xh", 0)], null);
        shapeBtn.setOnClickListener(v -> {
            int s = (sp.getInt("xh", 0) + 1) % SHAPES.length;
            sp.edit().putInt("xh", s).apply();
            shapeBtn.setText("Bentuk: " + SHAPES[s]);
            applyCross();
        });
        TextView colorBtn = tbtn("Warna", v -> {
            sp.edit().putInt("xc", (sp.getInt("xc", 0) + 1) % COLORS.length).apply();
            applyCross();
        });
        r.addView(shapeBtn);
        r.addView(colorBtn);
        content.addView(r);
        content.addView(txt("Ukuran", 12, Color.WHITE));
        content.addView(seek(10, 120, sp.getInt("xs", 40), v -> { sp.edit().putInt("xs", v).apply(); applyCross(); }));
    }

    void applySize(int v) {
        sp.edit().putInt("size", v).apply();
        size = dp(v);
        for (Btn b : btns) {
            if (b.aim || b.bv == null) continue;
            b.bp.width = size;
            b.bp.height = size;
            b.bv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.4f);
            try { wm.updateViewLayout(b.bv, b.bp); } catch (Exception e) {}
        }
    }

    void applyAlpha(int v) {
        sp.edit().putInt("alpha", v).apply();
        alpha = v / 100f;
        for (Btn b : btns) if (b.bv != null) b.bv.setAlpha(alpha);
    }

    void copyCfg() {
        String s = sp.getString("p_" + pkg, "[]");
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("nexus", s));
        toast("Konfigurasi disalin");
    }

    void pasteCfg() {
        try {
            ClipData c = ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).getPrimaryClip();
            String t = c.getItemAt(0).coerceToText(this).toString();
            JSONArray a = new JSONArray(t);
            for (int i = 0; i < a.length(); i++) a.getJSONObject(i).getInt("bx");
            sp.edit().putString("p_" + pkg, a.toString()).apply();
            toast("Konfigurasi ditempel");
            doShow(pkg);
        } catch (Exception e) {
            toast("Isi clipboard bukan konfigurasi Nexus");
        }
    }

    // ================= crosshair =================
    static class CrossView extends View {
        int shape, color;
        float d;
        final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

        CrossView(Context c, int s, int col) {
            super(c);
            shape = s;
            color = col;
            d = c.getResources().getDisplayMetrics().density;
        }

        @Override protected void onDraw(Canvas c) {
            pt.setColor(color);
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeWidth(2 * d);
            float w = getWidth(), ht = getHeight(), cx = w / 2, cy = ht / 2;
            float r = Math.min(w, ht) / 2 - 2 * d;
            switch (shape) {
                case 1: c.drawCircle(cx, cy, r, pt); break;
                case 2: c.drawRect(cx - r, cy - r, cx + r, cy + r, pt); break;
                case 3:
                    c.drawLine(cx - r, cy, cx + r, cy, pt);
                    c.drawLine(cx, cy - r, cx, cy + r, pt);
                    break;
                case 4:
                    pt.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy, 3 * d, pt);
                    break;
            }
        }
    }

    void applyCross() {
        removeQuiet(cross);
        cross = null;
        int shape = sp.getInt("xh", 0);
        if (shape == 0 || !showing) return;
        int sz = dp(sp.getInt("xs", 40));
        cross = new CrossView(this, shape, COLORS[sp.getInt("xc", 0)]);
        crossP = lp(sw() / 2 - sz / 2, sh() / 2 - sz / 2, sz, sz);
        crossP.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try { wm.addView(cross, crossP); } catch (Exception e) {}
    }

    // ================= tombol virtual =================
    void addNew(boolean aim) {
        if (!edit) { edit = true; refreshMode(); }
        int w = sw(), hh = sh();
        Btn b = new Btn();
        b.aim = aim;
        if (aim) {
            b.bp = lp(w - dp(200), hh / 2 - dp(90), dp(180), dp(180));
            b.tp = lp(w - dp(124), hh / 2 - dp(14), dp(28), dp(28));
        } else {
            int off = (btns.size() * dp(20)) % dp(200);
            b.bp = lp(w / 2 - size / 2 + off, hh / 2 + off, size, size);
            b.tp = lp(w / 2 + dp(60) + off, hh / 2 - dp(60) + off, dp(28), dp(28));
        }
        btns.add(b);
        addBtnViews(b);
        save();
    }

    void label(Btn b) { if (!b.aim) b.bv.setText(b.turbo ? "⚡" : "●"); }

    void addBtnViews(Btn b) {
        b.bv = b.aim ? pad("AIM") : circle("", 0xFF1D6B55, size);
        b.bv.setAlpha(alpha);
        label(b);
        b.tv = circle("+", 0xCCFF4455, dp(28));

        Drag bd = new Drag(b.bv, b.bp);
        if (!b.aim) bd.onTap = () -> { b.turbo = !b.turbo; label(b); save(); };
        bd.onLong = () -> remove(b);
        bd.onUp = this::save;
        Drag td = new Drag(b.tv, b.tp);
        td.onUp = this::save;

        b.bv.setOnTouchListener((v, e) -> {
            if (edit) return bd.onTouch(v, e);
            int a = e.getActionMasked();
            if (b.aim) {
                if (a == MotionEvent.ACTION_DOWN) aimDown(b, e.getRawX(), e.getRawY());
                else if (a == MotionEvent.ACTION_MOVE) aimMove(e.getRawX(), e.getRawY());
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) aimUp();
            } else {
                if (a == MotionEvent.ACTION_DOWN) press(b);
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) release(b);
            }
            return true;
        });
        b.tv.setOnTouchListener(td);
        wm.addView(b.bv, b.bp);
        setMarker(b, edit);
    }

    void setMarker(Btn b, boolean on) {
        try {
            if (on && !b.tOn) { wm.addView(b.tv, b.tp); b.tOn = true; }
            else if (!on && b.tOn) { wm.removeView(b.tv); b.tOn = false; }
        } catch (Exception e) {}
    }

    void remove(Btn b) {
        release(b);
        removeQuiet(b.bv);
        setMarker(b, false);
        btns.remove(b);
        save();
    }

    // ================= ketuk / turbo =================
    void press(Btn b) {
        b.down = true;
        b.bv.setAlpha(1f);
        tap(b);
        if (b.turbo) {
            b.rep = new Runnable() {
                @Override public void run() {
                    if (b.down) { tap(b); h.postDelayed(this, 70); }
                }
            };
            h.postDelayed(b.rep, 70);
        }
    }

    void release(Btn b) {
        b.down = false;
        if (b.rep != null) h.removeCallbacks(b.rep);
        if (b.bv != null) b.bv.setAlpha(alpha);
    }

    void tap(Btn b) {
        Path p = new Path();
        p.moveTo(b.tp.x + dp(14), b.tp.y + dp(14));
        GestureDescription.Builder g = new GestureDescription.Builder();
        g.addStroke(new GestureDescription.StrokeDescription(p, 0, 40));
        dispatchGesture(g.build(), null, null);
    }

    // ================= aim: geser di pad -> geser di titik target =================
    void aimDown(Btn b, float rx, float ry) {
        aimActive = true;
        aimBusy = false;
        aimStroke = null;
        aimCx = b.tp.x + dp(14);
        aimCy = b.tp.y + dp(14);
        aimLx = rx;
        aimLy = ry;
        pdx = 0;
        pdy = 0;
        aimStep(false);
    }

    void aimMove(float rx, float ry) {
        if (!aimActive) return;
        pdx += (rx - aimLx) * sp.getInt("sx", 100) / 100f;
        pdy += (ry - aimLy) * sp.getInt("sy", 100) / 100f;
        aimLx = rx;
        aimLy = ry;
        if (!aimBusy) aimStep(false);
    }

    void aimUp() {
        aimActive = false;
        if (!aimBusy && aimStroke != null) aimStep(true);
    }

    void aimStep(boolean end) {
        if (aimBusy || (end && aimStroke == null)) return;
        float nx = Math.max(1, Math.min(sw() - 1, aimCx + pdx));
        float ny = Math.max(1, Math.min(sh() - 1, aimCy + pdy));
        Path p = new Path();
        p.moveTo(aimCx, aimCy);
        if (nx != aimCx || ny != aimCy) p.lineTo(nx, ny);
        aimCx = nx;
        aimCy = ny;
        pdx = 0;
        pdy = 0;
        boolean cont = !end;
        GestureDescription.StrokeDescription s = aimStroke == null
                ? new GestureDescription.StrokeDescription(p, 0, 16, cont)
                : aimStroke.continueStroke(p, 0, 16, cont);
        aimStroke = end ? null : s;
        aimBusy = true;
        dispatchGesture(new GestureDescription.Builder().addStroke(s).build(),
                new AccessibilityService.GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription g) {
                        aimBusy = false;
                        if (aimActive) {
                            if (pdx != 0 || pdy != 0) aimStep(false);
                        } else if (aimStroke != null) {
                            aimStep(true);
                        }
                    }

                    @Override public void onCancelled(GestureDescription g) {
                        aimBusy = false;
                        aimStroke = null;
                    }
                }, h);
    }

    /** Seret / ketuk / tekan lama pada window overlay. */
    class Drag implements View.OnTouchListener {
        final View target;
        final WindowManager.LayoutParams p;
        Runnable onTap, onLong, onUp, lpr;
        boolean moved, longDone;
        float dx, dy;
        int sx, sy;

        Drag(View t, WindowManager.LayoutParams p) { target = t; this.p = p; }

        @Override public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    moved = false;
                    longDone = false;
                    dx = e.getRawX();
                    dy = e.getRawY();
                    sx = p.x;
                    sy = p.y;
                    if (onLong != null) {
                        lpr = () -> { if (!moved) { longDone = true; onLong.run(); } };
                        h.postDelayed(lpr, 700);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int ox = (int) (e.getRawX() - dx);
                    int oy = (int) (e.getRawY() - dy);
                    if (Math.abs(ox) > dp(6) || Math.abs(oy) > dp(6)) moved = true;
                    if (moved) {
                        p.x = sx + ox;
                        p.y = sy + oy;
                        try { wm.updateViewLayout(target, p); } catch (Exception ex) {}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (lpr != null) h.removeCallbacks(lpr);
                    if (!moved && !longDone && onTap != null) onTap.run();
                    if (!longDone && onUp != null) onUp.run();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (lpr != null) h.removeCallbacks(lpr);
                    return true;
            }
            return true;
        }
    }
}

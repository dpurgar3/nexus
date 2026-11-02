package space.nomadinfo.nexusauto;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleLink(getIntent());
        build();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        } else {
            NexusCore.post(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (handleLink(intent)) build();
    }

    /** Poveznica iz web NEXUS-a (gumb "NEXUS Auto") donosi oba ključa. */
    private boolean handleLink(Intent intent) {
        Uri d = intent == null ? null : intent.getData();
        if (d == null || !"nexusauto".equals(d.getScheme())) return false;
        String g = d.getQueryParameter("g"), t = d.getQueryParameter("t"), q = d.getQueryParameter("q");
        SharedPreferences.Editor e = NexusCore.prefs(this).edit();
        if (g != null && !g.trim().isEmpty()) e.putString("key", g.trim());
        if (t != null && !t.trim().isEmpty()) e.putString("token", t.trim());
        if (q != null && !q.trim().isEmpty()) e.putString("qkey", q.trim());
        e.commit();
        intent.setData(null);
        Toast.makeText(this, "✅ Povezano s NEXUS-om. Učitavam znanje…", Toast.LENGTH_LONG).show();
        refreshKnowledge();
        return true;
    }

    private void build() {
        final SharedPreferences p = NexusCore.prefs(this);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        ScrollView sv = new ScrollView(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(pad, pad, pad, pad);
        sv.addView(l);

        TextView title = new TextView(this);
        title.setText("NEXUS Auto");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        l.addView(title);

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, pad / 2, 0, pad / 2);
        l.addView(status);
        showStatus();

        TextView info = new TextView(this);
        info.setTextSize(16);
        info.setPadding(0, 0, 0, pad);
        info.setText("Najlakše: u web NEXUS-u otvori ⚙ Postavke i dodirni 🚗 NEXUS Auto, ključevi se sami prenesu.\n\n"
                + "U autu: dodirni obavijest NEXUS, reci \"Odgovori\" i izgovori pitanje. NEXUS odgovor pročita naglas.");
        l.addView(info);

        final EditText key = field(l, "Gemini API ključ", p.getString("key", ""));
        final EditText qkey = field(l, "Groq API ključ (rezerva kad Gemini potroši limit)", p.getString("qkey", ""));
        final EditText token = field(l, "NEXUS ključ (kalendar, mail, znanje)", p.getString("token", ""));
        final EditText extra = field(l, "Dodatne upute za NEXUS (nije obavezno)", p.getString("extra", ""));
        extra.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        extra.setMinLines(3);

        Button save = button(l, "Spremi i pošalji NEXUS u auto");
        save.setOnClickListener(v -> {
            p.edit().putString("key", key.getText().toString().trim())
                    .putString("qkey", qkey.getText().toString().trim())
                    .putString("token", token.getText().toString().trim())
                    .putString("extra", extra.getText().toString().trim()).commit();
            NexusCore.post(this);
            Toast.makeText(this, "Spremljeno. NEXUS je u obavijestima.", Toast.LENGTH_SHORT).show();
            refreshKnowledge();
        });

        Button refresh = button(l, "↻ Osvježi znanje");
        refresh.setOnClickListener(v -> refreshKnowledge());

        final EditText q = field(l, "Probno pitanje", "");
        q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        Button test = button(l, "Testiraj odgovor");
        final TextView out = new TextView(this);
        out.setTextSize(16);
        out.setPadding(0, pad, 0, pad);
        test.setOnClickListener(v -> {
            p.edit().putString("key", key.getText().toString().trim())
                    .putString("qkey", qkey.getText().toString().trim())
                    .putString("token", token.getText().toString().trim())
                    .putString("extra", extra.getText().toString().trim()).commit();
            final String t = q.getText().toString().trim();
            if (t.isEmpty()) {
                out.setText("Upiši probno pitanje.");
                return;
            }
            out.setText("NEXUS razmišlja…");
            new Thread(() -> {
                final String a = NexusCore.askOnce(getApplicationContext(), t);
                runOnUiThread(() -> out.setText(a));
            }).start();
        });
        l.addView(out);

        Button clear = button(l, "Obriši razgovor u autu");
        clear.setOnClickListener(v -> {
            NexusCore.clear(this);
            NexusCore.post(this);
            Toast.makeText(this, "Razgovor obrisan.", Toast.LENGTH_SHORT).show();
        });

        setContentView(sv);
    }

    private void showStatus() {
        if (status == null) return;
        SharedPreferences p = NexusCore.prefs(this);
        String s = (p.getString("key", "").isEmpty() ? "❌ Gemini ključ nedostaje" : "✅ Gemini ključ")
                + "\n" + (p.getString("qkey", "").isEmpty() ? "➖ Groq rezerva nije upisana" : "✅ Groq rezerva")
                + "\n" + (p.getString("token", "").isEmpty() ? "❌ NEXUS ključ nedostaje (nema kalendara, maila ni znanja)" : "✅ NEXUS ključ (kalendar i mail)")
                + "\n" + (p.getInt("znanjeN", 0) > 0 ? "✅ Znanje: " + p.getInt("znanjeN", 0) + " datoteka" : "❌ Znanje nije učitano");
        status.setText(s);
    }

    private void refreshKnowledge() {
        final android.content.Context c = getApplicationContext();
        new Thread(() -> {
            String msg;
            try {
                GoogleData.knowledge(c, true);
                msg = "Znanje učitano: " + NexusCore.prefs(c).getInt("znanjeN", 0) + " datoteka.";
            } catch (Exception e) {
                msg = "Znanje: " + e.getMessage();
            }
            final String m = msg;
            runOnUiThread(() -> {
                showStatus();
                Toast.makeText(this, m, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        NexusCore.post(this);
    }

    private EditText field(LinearLayout l, String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        l.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private Button button(LinearLayout l, String text) {
        Button b = new Button(this);
        b.setText(text);
        l.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }
}

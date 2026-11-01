package space.nomadinfo.nexusauto;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        TextView info = new TextView(this);
        info.setTextSize(16);
        info.setPadding(0, pad / 2, 0, pad);
        info.setText("1. Upiši Gemini ključ (isti kao u Nexusu) i pritisni Spremi.\n"
                + "2. U autu dodirni obavijest NEXUS, odaberi Odgovori i izgovori pitanje.\n"
                + "3. NEXUS ti odgovor pročita naglas. Za novo pitanje opet Odgovori.");
        l.addView(info);

        final EditText key = field(l, "Gemini API ključ", p.getString("key", ""), true);
        final EditText extra = field(l, "Dodatne upute za NEXUS (nije obavezno)", p.getString("extra", ""), false);
        extra.setMinLines(3);

        Button save = button(l, "Spremi i pošalji NEXUS u auto");
        save.setOnClickListener(v -> {
            p.edit().putString("key", key.getText().toString().trim())
                    .putString("extra", extra.getText().toString().trim()).commit();
            NexusCore.post(this);
            Toast.makeText(this, "Spremljeno. NEXUS je u obavijestima.", Toast.LENGTH_SHORT).show();
        });

        final EditText q = field(l, "Probno pitanje (provjera ključa)", "", false);
        Button test = button(l, "Testiraj odgovor");
        final TextView out = new TextView(this);
        out.setTextSize(16);
        out.setPadding(0, pad, 0, pad);
        test.setOnClickListener(v -> {
            p.edit().putString("key", key.getText().toString().trim())
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

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        } else {
            NexusCore.post(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        NexusCore.post(this);
    }

    private EditText field(LinearLayout l, String hint, String value, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
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

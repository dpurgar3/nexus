package space.nomadinfo.nexusauto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class NexusCore {
    static final String CHANNEL = "nexus_razgovor";
    static final int NOTIF_ID = 7;
    static final String KEY_TEXT = "pitanje";
    static final String[] MODELS = {"gemini-flash-latest", "gemini-flash-lite-latest", "gemini-2.5-flash", "gemini-2.5-flash-lite"};
    static final String BASE = "Ti si NEXUS, osobni AI asistent Dražena Purgara, vlasnika NOMAd Caffe Bara u Novom Marofu. Sve o njemu i njegovom poslu piše u bloku ZNANJE. "
            + "Dražen sada vozi i razgovara s tobom glasom preko Android Auta. "
            + "Uvijek odgovaraj na hrvatskom, kratko i jasno, najviše 3 rečenice, bez nabrajanja, bez emotikona i bez markdowna, jer se odgovor čita naglas. "
            + "Ako pitanje traži svježe podatke (vrijeme, promet, vijesti, cijene, radno vrijeme), koristi Google pretragu. "
            + "Za kalendar i mail koristi isključivo blokove GOOGLE KALENDAR i GMAIL ako su priloženi. "
            + "Ništa ne izmišljaj; ako ne znaš, reci to.";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("nexus", Context.MODE_PRIVATE);
    }

    static synchronized JSONArray history(Context c) {
        try {
            return new JSONArray(prefs(c).getString("hist", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static synchronized void add(Context c, String role, String text) {
        JSONArray a = history(c);
        try {
            a.put(new JSONObject().put("r", role).put("t", text).put("ts", System.currentTimeMillis()));
        } catch (Exception ignored) {
        }
        JSONArray keep = new JSONArray();
        for (int i = Math.max(0, a.length() - 12); i < a.length(); i++) keep.put(a.opt(i));
        prefs(c).edit().putString("hist", keep.toString()).commit();
    }

    static synchronized void clear(Context c) {
        prefs(c).edit().remove("hist").commit();
    }

    static void post(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "NEXUS razgovor", NotificationManager.IMPORTANCE_HIGH));

        JSONArray h = history(c);
        if (h.length() == 0) {
            add(c, "model", "Bok Dražene, ovdje NEXUS. Što te zanima?");
            h = history(c);
        }

        Person me = new Person.Builder().setName("Ja").setKey("ja").build();
        Person nexus = new Person.Builder().setName("NEXUS").setKey("nexus").setBot(true).setImportant(true).build();
        NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle(me)
                .setConversationTitle("NEXUS")
                .setGroupConversation(false);
        for (int i = Math.max(0, h.length() - 6); i < h.length(); i++) {
            JSONObject m = h.optJSONObject(i);
            if (m == null) continue;
            boolean user = "user".equals(m.optString("r"));
            style.addMessage(new NotificationCompat.MessagingStyle.Message(m.optString("t"), m.optLong("ts"), user ? null : nexus));
        }

        int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        Intent ri = new Intent(c, ReplyReceiver.class).setAction("space.nomadinfo.nexusauto.ODGOVOR");
        PendingIntent replyPi = PendingIntent.getBroadcast(c, 1, ri, PendingIntent.FLAG_UPDATE_CURRENT | mutable);
        RemoteInput input = new RemoteInput.Builder(KEY_TEXT).setLabel("Pitaj NEXUS").build();
        NotificationCompat.Action reply = new NotificationCompat.Action.Builder(R.drawable.ic_stat, "Odgovori", replyPi)
                .addRemoteInput(input)
                .setAllowGeneratedReplies(true)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .build();

        Intent mi = new Intent(c, ReadReceiver.class).setAction("space.nomadinfo.nexusauto.PROCITANO");
        PendingIntent readPi = PendingIntent.getBroadcast(c, 2, mi, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Action read = new NotificationCompat.Action.Builder(R.drawable.ic_stat, "Pročitano", readPi)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build();

        PendingIntent open = PendingIntent.getActivity(c, 3, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(open)
                .addAction(reply)
                .addInvisibleAction(read)
                .build();
        try {
            NotificationManagerCompat.from(c).notify(NOTIF_ID, n);
        } catch (SecurityException ignored) {
        }
    }

    static String askOnce(Context c, String question) {
        JSONArray a = new JSONArray();
        try {
            a.put(new JSONObject().put("r", "user").put("t", question));
        } catch (Exception ignored) {
        }
        String r = ask(c, a);
        prefs(c).edit().remove("akcija").commit();
        return r;
    }

    static String ask(Context c, JSONArray msgs) {
        String key = prefs(c).getString("key", "");
        if (key.isEmpty()) return "Nemam Gemini ključ. Upiši ga u aplikaciji NEXUS Auto na mobitelu.";
        String extra = prefs(c).getString("extra", "");
        String lastQ = "";
        for (int i = msgs.length() - 1; i >= 0; i--) {
            JSONObject m = msgs.optJSONObject(i);
            if (m != null && "user".equals(m.optString("r"))) { lastQ = m.optString("t"); break; }
        }
        StringBuilder sb = new StringBuilder(BASE);
        if (!extra.isEmpty()) sb.append("\n\nDODATNE UPUTE:\n").append(extra);
        String know = GoogleData.knowledgeQuick(c);
        if (!know.isEmpty()) sb.append("\n\n===== ZNANJE O VLASNIKU I NJEGOVOM POSLU (koristi kad je relevantno) =====").append(know).append("\n===== KRAJ ZNANJA =====");
        sb.append(GoogleData.context(c, lastQ, 10000));
        if (!GoogleData.token(c).isEmpty()) sb.append(GoogleData.ACTS);
        sb.append(GoogleData.now());
        String sys = sb.toString();

        JSONArray contents = new JSONArray();
        try {
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.optJSONObject(i);
                if (m == null) continue;
                String role = "user".equals(m.optString("r")) ? "user" : "model";
                if (contents.length() == 0 && role.equals("model")) continue;
                contents.put(new JSONObject().put("role", role)
                        .put("parts", new JSONArray().put(new JSONObject().put("text", m.optString("t")))));
            }
        } catch (Exception ignored) {
        }
        if (contents.length() == 0) return "Nisam čuo pitanje. Pokušaj ponovno.";

        long start = System.currentTimeMillis();
        String last = "";
        for (String model : MODELS) {
            for (int v = 0; v < 3; v++) {
                if (System.currentTimeMillis() - start > 25000) return "NEXUS se predugo javlja. Pokušaj ponovno.";
                try {
                    JSONObject body = new JSONObject();
                    body.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", sys))));
                    body.put("contents", contents);
                    JSONObject gc = new JSONObject().put("temperature", 0.5).put("maxOutputTokens", 1500);
                    if (v < 2) {
                        gc.put("thinkingConfig", model.contains("gemini-2.")
                                ? new JSONObject().put("thinkingBudget", 0)
                                : new JSONObject().put("thinkingLevel", "low"));
                    }
                    body.put("generationConfig", gc);
                    if (v == 0) body.put("tools", new JSONArray().put(new JSONObject().put("google_search", new JSONObject())));

                    HttpURLConnection con = (HttpURLConnection) new URL(
                            "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent").openConnection();
                    con.setConnectTimeout(8000);
                    con.setReadTimeout(15000);
                    con.setRequestMethod("POST");
                    con.setDoOutput(true);
                    con.setRequestProperty("Content-Type", "application/json");
                    con.setRequestProperty("x-goog-api-key", key);
                    try (OutputStream os = con.getOutputStream()) {
                        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    int code = con.getResponseCode();
                    String resp = readAll(code < 400 ? con.getInputStream() : con.getErrorStream());
                    con.disconnect();
                    if (code == 200) {
                        String t = extractText(resp);
                        if (!t.isEmpty()) return clean(takeAction(c, t));
                        last = "prazan odgovor";
                        break;
                    }
                    last = "greška " + code;
                    if (resp.contains("API_KEY_INVALID") || resp.contains("API key not valid")) {
                        return "Gemini ključ ne radi. Provjeri ga u aplikaciji NEXUS Auto.";
                    }
                    if (code == 400) continue;
                    if (code == 429 && v == 0) continue;
                    break;
                } catch (Exception e) {
                    last = "nema veze s internetom";
                    break;
                }
            }
        }
        return "Nisam dobio odgovor (" + last + "). Pokušaj ponovno.";
    }

    static final java.util.regex.Pattern ACT = java.util.regex.Pattern.compile("```\\s*nexus-akcija\\s*([\\s\\S]*?)(```|$)");
    static final java.util.regex.Pattern YES = java.util.regex.Pattern.compile("^(da|dap|može|moze|potvrđujem|potvrdjujem|potvrdi|pošalji|posalji|ok|okej|u redu|dodaj|spremi|naravno|važi|vazi)([\\s,.!]|$)", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    static final java.util.regex.Pattern NO = java.util.regex.Pattern.compile("^(ne|nemoj|odustani|stop|nije|nikako)([\\s,.!]|$)", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

    /** Izvadi blok s radnjom, spremi ga za potvrdu i vrati tekst za čitanje. */
    static String takeAction(Context c, String t) {
        java.util.regex.Matcher m = ACT.matcher(t);
        String pending = "";
        while (m.find()) {
            try {
                JSONObject a = new JSONObject(m.group(1).trim());
                if (a.has("radnja")) pending = a.toString();
            } catch (Exception ignored) {
            }
        }
        String text = ACT.matcher(t).replaceAll("").trim();
        if (!pending.isEmpty()) {
            prefs(c).edit().putString("akcija", pending).commit();
            if (!text.toLowerCase().contains("da ili ne")) text = text + " Reci da ili ne.";
        }
        return text;
    }

    /** Odgovor na zadnje pitanje: potvrda radnje ili novo pitanje za NEXUS. */
    static String answer(Context c) {
        JSONArray h = history(c);
        String q = "";
        for (int i = h.length() - 1; i >= 0; i--) {
            JSONObject m = h.optJSONObject(i);
            if (m != null && "user".equals(m.optString("r"))) { q = m.optString("t").trim(); break; }
        }
        String pending = prefs(c).getString("akcija", "");
        if (!pending.isEmpty()) {
            prefs(c).edit().remove("akcija").commit();
            if (YES.matcher(q).find()) return GoogleData.runAction(c, pending);
            if (NO.matcher(q).find()) return "U redu, odustao sam.";
        }
        return ask(c, h);
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }

    private static String extractText(String resp) {
        StringBuilder sb = new StringBuilder();
        try {
            JSONArray parts = new JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts");
            for (int i = 0; i < parts.length(); i++) {
                JSONObject p = parts.getJSONObject(i);
                if (p.optBoolean("thought", false)) continue;
                sb.append(p.optString("text", ""));
            }
        } catch (Exception ignored) {
        }
        return sb.toString().trim();
    }

    private static String clean(String t) {
        return t.replaceAll("[*#`]", "").replaceAll("\\s+", " ").trim();
    }
}

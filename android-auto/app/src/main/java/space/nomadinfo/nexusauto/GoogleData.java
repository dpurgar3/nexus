package space.nomadinfo.nexusauto;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Kalendar, Gmail i znanje preko istih Google skripti koje koristi web NEXUS. */
public class GoogleData {
    static final String[][] ACC = {
            {"https://script.google.com/macros/s/AKfycbyB27I2xPFKhTAHY6scH3rIVdc8G-gVGo4GjFWM6PSZ4YFdYTKZLs2gHsdYo1072Vmu/exec", "artistdrazen@gmail.com (poslovni)"},
            {"https://script.google.com/macros/s/AKfycbw8XOcHJDp7MBipA6ot3YXR-Zq6zj5bwxYjceh-ETVyBYAYiT7HtBA-a87NicWtmSK9/exec", "dpurgar3@gmail.com"}
    };
    private static final int F = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    static final Pattern CAL = Pattern.compile("(kalendar|raspored|termin|sastan|obavez|obvez|događaj|dogadaj|imam li (nešto|nesto|što|sto)|što imam|sto imam|rođendan|rodjendan|podsjetnik|zakazan|dogovoren)", F);
    static final Pattern MAIL = Pattern.compile("(e-?mail|\\bmail|pošt[aeiu]|\\bpost[aeu]\\b|inbox|gmail|pisao|pisala|poslao|poslala|javio|javila|poruk[aeu] od)", F);
    static final Pattern CONT = Pattern.compile("(kontakt|imenik|broj (od|telefona|mobitela|mobitel)|telefon (od|za)|mobitel (od|za)|e-?mail (od|za|adres)|kako (da )?dobijem|nazvati|nazovem)", F);
    static final Pattern SEND = Pattern.compile("(pošalji|posalji|napiši|napisi|odgovori|nacrt).{0,25}(mail|e-mail|poruk)", F);
    static final Pattern STOP = Pattern.compile("^(kalendar\\S*|raspored\\S*|termin\\S*|sastan\\S*|obavez\\S*|obvez\\S*|događaj\\S*|dogadaj\\S*|e-?mail\\S*|mail\\S*|pošt\\S*|post[aeu]|inbox|gmail|poruk\\S*|kontakt\\S*|imenik\\S*|broj\\S*|telefon\\S*|mobitel\\S*|adres\\S*|pisao|pisala|poslao|poslala|javio|javila|nazvati|nazovem|dobijem|imam|ima|imao|imala|moj\\S*|tvoj\\S*|nov\\S*|zadnj\\S*|posljednj\\S*|nepročitan\\S*|neprocitan\\S*|ovaj|ovog|tjed\\S*|mjesec\\S*|sutra|danas|prekosutra|od|za|o|oko|li|što|sto|šta|nešto|nesto|pokaži|pokazi|reci|daj|koji|koja|koje|kojeg|kad|kada|gdje|je|su|me|mi|meni|sam|da|se|na|u|i|s|sa|tko|ko|pronađi|pronadi|nađi|nadi|nadji|traži|trazi|potraži|potrazi|molim|te|ti|sve|svi|mu|joj|im|ga|ju|bilo|ikakv\\S*|neki|neka|neko)$", F);

    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    static String token(Context c) {
        return NexusCore.prefs(c).getString("token", "");
    }

    static JSONObject call(String base, String token, String params) throws Exception {
        URL u = new URL(base + "?token=" + URLEncoder.encode(token, "UTF-8") + "&" + params);
        HttpURLConnection con = (HttpURLConnection) u.openConnection();
        con.setInstanceFollowRedirects(true);
        con.setConnectTimeout(6000);
        con.setReadTimeout(14000);
        int code = con.getResponseCode();
        String s = readAll(code < 400 ? con.getInputStream() : con.getErrorStream());
        con.disconnect();
        JSONObject j = new JSONObject(s);
        if (j.has("error")) throw new Exception(j.optString("error"));
        return j;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** Znanje iz Google Drive mape "znanje". force = uvijek s interneta. */
    static String knowledge(Context c, boolean force) throws Exception {
        SharedPreferences p = NexusCore.prefs(c);
        String cached = p.getString("znanje", "");
        long age = System.currentTimeMillis() - p.getLong("znanjeT", 0);
        if (!force && !cached.isEmpty() && age < 6 * 3600 * 1000L) return cached;
        String tok = token(c);
        if (tok.isEmpty()) {
            if (force) throw new Exception("nema NEXUS ključa");
            return cached;
        }
        JSONObject j = call(ACC[0][0], tok, "a=znanje");
        JSONArray d = j.optJSONArray("datoteke");
        if (d == null || d.length() == 0) return cached;
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int i = 0; i < d.length() && sb.length() < 50000; i++) {
            JSONObject f = d.optJSONObject(i);
            if (f == null) continue;
            sb.append("\n### ").append(f.optString("name")).append('\n').append(f.optString("text")).append('\n');
            n++;
        }
        String t = sb.length() > 50000 ? sb.substring(0, 50000) : sb.toString();
        p.edit().putString("znanje", t).putLong("znanjeT", System.currentTimeMillis()).putInt("znanjeN", n).commit();
        return t;
    }

    /** Znanje bez čekanja: vrati spremljeno, a zastarjelo osvježi u pozadini. */
    static String knowledgeQuick(final Context c) {
        SharedPreferences p = NexusCore.prefs(c);
        String cached = p.getString("znanje", "");
        long age = System.currentTimeMillis() - p.getLong("znanjeT", 0);
        if (cached.isEmpty()) {
            try {
                Future<String> f = POOL.submit(() -> knowledge(c, true));
                return f.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                return "";
            }
        }
        if (age > 6 * 3600 * 1000L) {
            POOL.submit(() -> {
                try {
                    knowledge(c, true);
                } catch (Exception ignored) {
                }
                return null;
            });
        }
        return cached;
    }

    static String words(String t) {
        StringBuilder sb = new StringBuilder();
        for (String w : t.replaceAll("[?!.,:;\"„“()]", " ").split("\\s+")) {
            if (w.isEmpty() || STOP.matcher(w).matches()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w);
        }
        return sb.toString().trim();
    }

    static String mailQuery(String w) {
        StringBuilder sb = new StringBuilder();
        for (String x : w.split(" ")) {
            if (!x.isEmpty() && Character.isUpperCase(x.charAt(0)) || (!x.isEmpty() && Character.isDigit(x.charAt(0)))) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(x);
            }
        }
        return sb.length() > 0 ? sb.toString() : w;
    }

    /** Podaci iz kalendara i Gmaila za ovo pitanje (oba računa). */
    static String context(Context c, String q, long waitMs) {
        final String tok = token(c);
        if (tok.isEmpty() || q == null) return "";
        String low = q.toLowerCase(new Locale("hr", "HR"));
        List<Future<String>> jobs = new ArrayList<>();

        if (CAL.matcher(q).find()) {
            int dana = 7, pomak = 0;
            if (low.contains("prekosutra")) { pomak = 2; dana = 1; }
            else if (low.contains("sutra")) { pomak = 1; dana = 1; }
            else if (low.contains("danas") || low.contains("večeras") || low.contains("veceras")) dana = 1;
            else if (low.contains("mjesec")) dana = 31;
            else if (low.matches(".*(sljedeć|sljedec|idući|iduci) tjed.*")) { pomak = 7; dana = 7; }
            final String params = "a=kalendar&dana=" + dana + "&pomak=" + pomak;
            for (final String[] acc : ACC) {
                jobs.add(POOL.submit(() -> {
                    JSONObject j = call(acc[0], tok, params);
                    StringBuilder sb = new StringBuilder("\n\n[GOOGLE KALENDAR — " + acc[1] + " — od " + j.optString("od") + ", " + j.optString("dana") + " dana]\n");
                    JSONArray e = j.optJSONArray("dogadaji");
                    if (e == null || e.length() == 0) sb.append("(nema događaja)");
                    else for (int i = 0; i < e.length(); i++) {
                        JSONObject d = e.getJSONObject(i);
                        sb.append("- ").append(d.optString("kada")).append(" — ").append(d.optString("naslov"));
                        if (!d.optString("mjesto").isEmpty()) sb.append(" @ ").append(d.optString("mjesto"));
                        if (!d.optString("opis").isEmpty()) sb.append(" (").append(d.optString("opis")).append(')');
                        sb.append('\n');
                    }
                    return sb.append("\n[KRAJ KALENDARA]").toString();
                }));
            }
        }

        if (CONT.matcher(q).find() || SEND.matcher(q).find()) {
            String w = words(q.replaceAll("(?iu)(pošalji|posalji|napiši|napisi|odgovori|nacrt)\\S*", " ")
                    .replaceAll("(?iu)(^|\\s)(da|kako|neka|dolazi|dolazim|sutra|danas)(\\s.*)?$", " "));
            StringBuilder stem = new StringBuilder();
            for (String x : w.split(" ")) {
                if (x.isEmpty()) continue;
                if (stem.length() > 0) stem.append(' ');
                stem.append(x.length() > 4 && "aeiouAEIOU".indexOf(x.charAt(x.length() - 1)) >= 0 ? x.substring(0, x.length() - 1) : x);
            }
            final String who = w;
            final String params = "a=kontakti&q=" + enc(stem.toString());
            if (stem.length() > 0) for (final String[] acc : ACC) {
                jobs.add(POOL.submit(() -> {
                    JSONObject j = call(acc[0], tok, params);
                    StringBuilder sb = new StringBuilder("\n\n[GOOGLE KONTAKTI — " + acc[1] + " — \"" + who + "\"]\n");
                    JSONArray k = j.optJSONArray("kontakti");
                    if (k == null || k.length() == 0) sb.append("(nema kontakta)\n");
                    else for (int i = 0; i < k.length() && i < 15; i++) {
                        JSONObject x = k.getJSONObject(i);
                        sb.append("- ").append(x.optString("ime"));
                        if (!x.optString("tvrtka").isEmpty()) sb.append(" (").append(x.optString("tvrtka")).append(')');
                        JSONArray tel = x.optJSONArray("telefoni"), em = x.optJSONArray("emailovi");
                        if (tel != null && tel.length() > 0) sb.append(" · tel: ").append(join(tel));
                        if (em != null && em.length() > 0) sb.append(" · ").append(join(em));
                        sb.append('\n');
                    }
                    return sb.append("[KRAJ KONTAKATA]").toString();
                }));
            }
        }

        if (MAIL.matcher(q).find() && !CONT.matcher(q).find()) {
            String w = words(q);
            String mq;
            int n;
            if (low.contains("nepročitan") || low.contains("neprocitan")) { mq = "is:unread in:inbox"; n = 8; }
            else if (!w.isEmpty()) { mq = mailQuery(w) + " newer_than:365d"; n = 6; }
            else { mq = "in:inbox newer_than:3d"; n = 8; }
            final String params = "a=mail&q=" + enc(mq) + "&n=" + n;
            for (final String[] acc : ACC) {
                jobs.add(POOL.submit(() -> {
                    JSONObject j = call(acc[0], tok, params);
                    StringBuilder sb = new StringBuilder("\n\n[GMAIL — " + acc[1] + " — upit: " + j.optString("upit") + "]\n");
                    JSONArray m = j.optJSONArray("mailovi");
                    if (m == null || m.length() == 0) sb.append("(nema mailova za taj upit)");
                    else for (int i = 0; i < m.length(); i++) {
                        JSONObject x = m.getJSONObject(i);
                        String body = x.optString("poruka");
                        if (body.length() > 700) body = body.substring(0, 700) + "…";
                        sb.append('(').append(i + 1).append(") ").append(x.optString("datum"))
                                .append(x.optBoolean("neprocitano") ? " · NEPROČITANO" : "")
                                .append("\nOd: ").append(x.optString("od"))
                                .append("\nNaslov: ").append(x.optString("naslov"))
                                .append('\n').append(body).append("\n\n");
                    }
                    return sb.append("[KRAJ GMAILA]").toString();
                }));
            }
        }

        StringBuilder out = new StringBuilder();
        long end = System.currentTimeMillis() + waitMs;
        for (Future<String> f : jobs) {
            try {
                out.append(f.get(Math.max(1, end - System.currentTimeMillis()), TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                f.cancel(true);
            }
        }
        if (!jobs.isEmpty() && out.length() == 0) {
            out.append("\n\n[Google podaci trenutno nisu dostupni. Reci Draženu da ih sada ne vidiš.]");
        } else if (out.length() > 0) {
            out.append("\nKoristi ove podatke iz Draženovog Google računa za odgovor. Ništa ne izmišljaj.");
        }
        return out.toString();
    }

    static String join(JSONArray a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(a.optString(i));
        }
        return sb.toString();
    }

    static final String ACTS = "\n\nRADNJE (kalendar i Gmail): Kad Dražen traži da dodaš termin u kalendar, pošalješ mail ili spremiš nacrt maila, NIKAD ne tvrdi da je već napravljeno. "
            + "On vozi, pa u jednoj ili dvije rečenice naglas ponovi što ćeš napraviti (za termin: naslov, dan i sat; za mail: kome, naslov i ukratko sadržaj) i završi s: Reci da ili ne. "
            + "Na kraju odgovora dodaj točno ovakav blok (JSON), koji se ne čita naglas:\n```nexus-akcija\n{\"radnja\":\"termin\",\"racun\":\"artistdrazen\",\"naslov\":\"Sastanak s dobavljačem\",\"pocetak\":\"2026-11-03T18:00\",\"kraj\":\"2026-11-03T19:00\",\"mjesto\":\"NOMAd\",\"opis\":\"\"}\n```\n"
            + "Za mail: {\"radnja\":\"mail\",\"racun\":\"artistdrazen\",\"za\":\"ime@primjer.hr\",\"naslov\":\"...\",\"tekst\":\"Poštovani,\\n\\n...\"}. Ako traži samo nacrt, \"radnja\":\"nacrt\". "
            + "Datum bez sata (\"2026-11-03\") = cijeli dan. Ako kraj nije rečen, termin traje sat vremena. Računi: \"artistdrazen\" (poslovni, zadani) ili \"dpurgar3\" (samo ako to izričito traži). "
            + "Adresu primatelja uzmi iz [GOOGLE KONTAKTI] ili iz poruke; ako je nemaš, pitaj i ne dodaj blok, nikad je ne izmišljaj. Potpis ne piši, dodaje se sam. Mail piši uljudno, na hrvatskom osim ako traži drugi jezik.";

    /** Izvrši potvrđenu radnju (termin, mail, nacrt) preko Google skripte. */
    static String runAction(Context c, String json) {
        String tok = token(c);
        if (tok.isEmpty()) return "Nemam NEXUS ključ pa ne mogu to napraviti.";
        try {
            JSONObject a = new JSONObject(json);
            String base = a.optString("racun").toLowerCase(Locale.ROOT).contains("dpurgar") ? ACC[1][0] : ACC[0][0];
            a.put("token", tok);
            HttpURLConnection con = (HttpURLConnection) new URL(base).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(8000);
            con.setReadTimeout(20000);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "text/plain;charset=utf-8");
            try (java.io.OutputStream os = con.getOutputStream()) {
                os.write(a.toString().getBytes("UTF-8"));
            }
            int code = con.getResponseCode();
            String s;
            if (code >= 300 && code < 400 && con.getHeaderField("Location") != null) {
                String loc = con.getHeaderField("Location");
                con.disconnect();
                HttpURLConnection g = (HttpURLConnection) new URL(loc).openConnection();
                g.setConnectTimeout(8000);
                g.setReadTimeout(20000);
                int c2 = g.getResponseCode();
                s = readAll(c2 < 400 ? g.getInputStream() : g.getErrorStream());
                g.disconnect();
            } else {
                s = readAll(code < 400 ? con.getInputStream() : con.getErrorStream());
                con.disconnect();
            }
            JSONObject j = new JSONObject(s);
            if (j.optBoolean("ok")) return "Gotovo. " + j.optString("poruka");
            return "Nije uspjelo: " + j.optString("error", "nepoznata greška");
        } catch (Exception e) {
            return "Nije uspjelo, nema veze s Googleom. Pokušaj kasnije.";
        }
    }

    static String now() {
        Locale hr = new Locale("hr", "HR");
        SimpleDateFormat f = new SimpleDateFormat("EEEE, d. MMMM yyyy., HH:mm", hr);
        f.setTimeZone(TimeZone.getTimeZone("Europe/Zagreb"));
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT);
        iso.setTimeZone(TimeZone.getTimeZone("Europe/Zagreb"));
        Date d = new Date();
        return "\n\nTOČNO VRIJEME SADA (Hrvatska): " + f.format(d) + " h (" + iso.format(d) + ").";
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "{}";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }
}

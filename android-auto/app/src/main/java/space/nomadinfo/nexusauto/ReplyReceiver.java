package space.nomadinfo.nexusauto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.app.RemoteInput;

public class ReplyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Context c = context.getApplicationContext();
        Bundle res = RemoteInput.getResultsFromIntent(intent);
        CharSequence q = res == null ? null : res.getCharSequence(NexusCore.KEY_TEXT);
        if (q == null || q.toString().trim().isEmpty()) {
            NexusCore.post(c);
            return;
        }
        NexusCore.add(c, "user", q.toString().trim());
        NexusCore.post(c);
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                String answer = NexusCore.ask(c, NexusCore.history(c));
                NexusCore.add(c, "model", answer);
                NexusCore.post(c);
            } finally {
                pr.finish();
            }
        }).start();
    }
}

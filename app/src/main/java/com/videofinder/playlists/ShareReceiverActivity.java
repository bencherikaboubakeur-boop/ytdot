package com.videofinder.playlists;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Exported share target. It never forwards a caller-supplied Intent and never loads anything:
 * it validates two strings and builds its OWN explicit Intent to a non-exported Activity.
 */
public class ShareReceiverActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        String text = in != null ? in.getStringExtra(Intent.EXTRA_TEXT) : null;
        String subject = in != null ? in.getStringExtra(Intent.EXTRA_SUBJECT) : null;

        String safe = SafeLink.fromText(text);
        if (safe == null) safe = SafeLink.clean(text);

        if (safe == null) {
            Toast.makeText(this, R.string.invalid_link, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (subject != null && subject.length() > 200) subject = subject.substring(0, 200);

        Intent next = new Intent(this, EditActivity.class);
        next.putExtra("shared_url", safe);
        if (subject != null) next.putExtra("shared_title", subject);
        startActivity(next);
        finish();
    }
}

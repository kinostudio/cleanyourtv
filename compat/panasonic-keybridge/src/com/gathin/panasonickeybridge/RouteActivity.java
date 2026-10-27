package com.gathin.panasonickeybridge;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
/** An immediately finished foreground route; no background monitoring or key filtering. */
abstract class RouteActivity extends Activity {
    protected abstract ComponentName destination();
    @Override protected final void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).setComponent(destination())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException error) {
            Log.e("PanasonicKeyBridge", "Configured destination is unavailable: " + error.getClass().getSimpleName());
        } finally {
            finish();
            overridePendingTransition(0, 0);
        }
    }
}

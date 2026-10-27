package com.gathin.panasonickeybridge;
import android.content.ComponentName;
public final class AppsRoute extends RouteActivity {
    @Override protected ComponentName destination() {
        return new ComponentName("com.panasonic.homescreen", "com.panasonic.homescreen.HomeScreenActivity");
    }
}

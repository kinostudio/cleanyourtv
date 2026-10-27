package com.gathin.panasonickeybridge;
import android.content.ComponentName;
public final class PrimeRoute extends RouteActivity {
    @Override protected ComponentName destination() {
        return new ComponentName("com.weenas.castbay.panasonic", "com.weenas.castbay.MainActivity");
    }
}

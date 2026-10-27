# Panasonic remote key bridge (experimental)

This optional companion was tested on one Panasonic 4KTV-JUP, Android 11, build 4.501. It routes APPS to Panasonic's home app and Prime Video to the independent `com.weenas.castbay.panasonic` build of CastBay. It does not provide generic key mapping and is not automatically installed by CleanYourTV.

It declares no accessibility service, background service, network permission or boot receiver. A keyboard-layout overlay maps APPS to the calculator category and Prime to the email category; the two short-lived activities open the fixed destinations. Those categories must be unused on the TV; a physical keyboard's calculator or mail key may also activate them. The overlay is keyed by the remote's vendor/product and may affect another remote of the same model.

On the tested TV this avoided the loss of volume-key repeat observed while an accessibility key filter was bound. APPS, Prime, held volume and reboot persistence were physically checked. Standby/wake and remote reconnect were not checked. Hardware and firmware differences can change the result. The APK has no launcher icon.

## Source and restoration

`AndroidManifest.xml`, `src/` and `res/` define the companion; `shell/LayoutTrial.java` is the guarded shell controller. The layout must be restored with the controller before removing the companion, followed by a full reboot to reload the original vendor mapping. The controller keeps a private baseline on the TV, checks the exact remote and refuses unexpected existing selections. Keep that baseline private; do not upload it. Restore was not exercised on the tested unit. This is source for review, not a turnkey installer or a generally tested rollback procedure.

On Windows, `./compat/panasonic-keybridge/build.ps1 -ToolchainRoot <directory>` builds the APK and controller using JDK 17 in `jdk/`, Android SDK platform 34 and build tools 36.1.0 in `sdk/`, and an existing Android debug keystore in `android-user/debug.keystore`. The signer prompts for its password; the script does not create or disclose a key. A different signing key produces an APK that cannot update an existing installation signed with another key. This portable invocation has not been tested outside the original workstation.

Inspired by the [Sony keyboard-layout experiment](https://github.com/Lzchyi/cleanyourtv/tree/v2.6.0-redesign/companions/sony-apps-layout).

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.InputDevice;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.EnumSet;

/** Fixed, shell-only trial controller. Never modifies a system keylayout file or reboots. */
public final class LayoutTrial {
    private static final String PACKAGE = "com.gathin.panasonickeybridge";
    private static final String LAYOUT = PACKAGE + "/" + PACKAGE + ".Layouts/panasonic_keys";
    private static final String NAME = "Panasonic ATV RCU01";
    private static final int VENDOR = 0x06e7, PRODUCT = 0x8152;
    private static final Path BASELINE = Paths.get("/data/local/tmp/cyt-panasonic-keyboard-layout-trial.json");
    private static Object manager, identifier;
    private static Class<?> api, idClass;
    private static InputDevice remote;
    private static Context context;

    private static void init() throws Exception {
        if (Process.myUid() != 2000) throw new IllegalStateException("Shell UID required");
        if (Looper.myLooper() == null) Looper.prepareMainLooper();
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Object thread = threadClass.getMethod("systemMain").invoke(null);
        context = (Context) threadClass.getMethod("getSystemContext").invoke(thread);
        if (context.checkPermission("android.permission.SET_KEYBOARD_LAYOUT", Process.myPid(), Process.myUid())
                != PackageManager.PERMISSION_GRANTED) throw new SecurityException("Layout permission unavailable");
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
            .getMethod("getService", String.class).invoke(null, "input");
        api = Class.forName("android.hardware.input.IInputManager");
        manager = Class.forName("android.hardware.input.IInputManager$Stub")
            .getMethod("asInterface", IBinder.class).invoke(null, binder);
        idClass = Class.forName("android.hardware.input.InputDeviceIdentifier");
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice candidate = InputDevice.getDevice(deviceId);
            if (candidate != null && !candidate.isVirtual() && candidate.getVendorId() == VENDOR
                    && candidate.getProductId() == PRODUCT && NAME.equals(candidate.getName())) {
                if (remote != null) throw new IllegalStateException("Ambiguous matching remotes");
                remote = candidate;
            }
        }
        if (remote == null) throw new IllegalStateException("Expected remote is absent");
        identifier = idClass.getConstructor(String.class, int.class, int.class)
            .newInstance(remote.getDescriptor(), VENDOR, PRODUCT);
    }

    private static String current() throws Exception {
        return (String) api.getMethod("getCurrentKeyboardLayoutForInputDevice", idClass).invoke(manager, identifier);
    }
    private static String[] enabled() throws Exception {
        String[] value = (String[]) api.getMethod("getEnabledKeyboardLayoutsForInputDevice", idClass)
            .invoke(manager, identifier);
        return value == null ? new String[0] : value;
    }
    private static void requireEmptySelection() throws Exception {
        if (current() != null || enabled().length != 0) throw new IllegalStateException("Baseline must be null and empty");
    }
    private static void requireUniqueRoute(String category, String activity) throws Exception {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        java.util.List<ResolveInfo> handlers = context.getPackageManager().queryIntentActivities(intent, 0);
        ResolveInfo resolved = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        ComponentName expected = new ComponentName(PACKAGE, PACKAGE + "." + activity);
        if (handlers.size() != 1 || resolved == null || resolved.activityInfo == null
                || !expected.equals(new ComponentName(handlers.get(0).activityInfo.packageName, handlers.get(0).activityInfo.name))
                || !expected.equals(new ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name))
                || !resolved.activityInfo.enabled || !resolved.activityInfo.applicationInfo.enabled
                || !resolved.activityInfo.exported) throw new IllegalStateException("Category route is ambiguous or unavailable");
    }
    private static void requireRegisteredLayout() throws Exception {
        Object[] layouts = (Object[]) api.getMethod("getKeyboardLayouts").invoke(manager);
        boolean found = false;
        if (layouts != null) for (Object layout : layouts) {
            found |= LAYOUT.equals(layout.getClass().getMethod("getDescriptor").invoke(layout));
        }
        if (!found) throw new IllegalStateException("Fixed companion layout is not registered");
    }
    private static JSONObject state() throws Exception {
        return new JSONObject().put("device", NAME).put("vendor", VENDOR).put("product", PRODUCT)
            .put("current_layout", current() == null ? JSONObject.NULL : current())
            .put("enabled_layouts", new JSONArray(Arrays.asList(enabled())));
    }
    private static void removeFixedLayout() throws Exception {
        api.getMethod("removeKeyboardLayoutForInputDevice", idClass, String.class).invoke(manager, identifier, LAYOUT);
        SystemClock.sleep(600);
    }
    private static void saveNewBaseline() throws Exception {
        JSONObject value = new JSONObject().put("schema", 1).put("trial_layout", LAYOUT)
            .put("device", NAME).put("vendor", VENDOR).put("product", PRODUCT)
            .put("private_device_descriptor", remote.getDescriptor())
            .put("original_current", JSONObject.NULL).put("original_enabled", new JSONArray())
            .put("created_at_ms", System.currentTimeMillis());
        byte[] bytes = value.toString(2).getBytes(StandardCharsets.UTF_8);
        try (FileChannel file = FileChannel.open(BASELINE,
                EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) file.write(buffer);
            file.force(true);
        }
    }
    private static void validateBaseline() throws Exception {
        if (!Files.isRegularFile(BASELINE, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("Regular baseline file required");
        JSONObject before = new JSONObject(new String(Files.readAllBytes(BASELINE), StandardCharsets.UTF_8));
        if (before.getInt("schema") != 1 || !LAYOUT.equals(before.getString("trial_layout"))
                || !NAME.equals(before.getString("device")) || before.getInt("vendor") != VENDOR
                || before.getInt("product") != PRODUCT
                || !remote.getDescriptor().equals(before.getString("private_device_descriptor"))
                || !before.isNull("original_current") || before.getJSONArray("original_enabled").length() != 0)
            throw new IllegalStateException("Baseline does not match this device and fixed trial");
    }
    private static void apply() throws Exception {
        requireEmptySelection();
        if (Files.exists(BASELINE, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("Baseline already exists; refusing overwrite");
        requireRegisteredLayout();
        requireUniqueRoute("android.intent.category.APP_CALCULATOR", "AppsRoute");
        requireUniqueRoute("android.intent.category.APP_EMAIL", "PrimeRoute");
        saveNewBaseline();
        try {
            requireEmptySelection();
            api.getMethod("setCurrentKeyboardLayoutForInputDevice", idClass, String.class).invoke(manager, identifier, LAYOUT);
            SystemClock.sleep(600);
            if (!LAYOUT.equals(current()) || enabled().length != 1 || !LAYOUT.equals(enabled()[0]))
                throw new IllegalStateException("Fixed layout selection was not retained exclusively");
        } catch (Exception error) {
            // Only our own layout can be removed. A reboot may still be needed for its RAM map.
            try { removeFixedLayout(); } catch (Exception ignored) { }
            throw new IllegalStateException("Apply failed; inspect fixed baseline and reboot after restore", error);
        }
        System.out.println(new JSONObject().put("operation", "apply").put("state", state())
            .put("baseline_path", BASELINE.toString()).put("physical_validation", "not_checked").toString(2));
    }
    private static void restore() throws Exception {
        validateBaseline();
        removeFixedLayout();
        requireEmptySelection();
        System.out.println(new JSONObject().put("operation", "restore_selection").put("state", state())
            .put("reboot_required_to_confirm_original_map", true).toString(2));
    }
    private static void run(String[] args) throws Exception {
        if (args.length != 1 || !("inspect".equals(args[0]) || "apply".equals(args[0]) || "restore".equals(args[0])))
            throw new IllegalArgumentException("Only inspect, apply or restore is supported");
        init();
        if ("apply".equals(args[0])) apply();
        else if ("restore".equals(args[0])) restore();
        else System.out.println(state().toString(2));
    }
    public static void main(String[] args) {
        try { run(args); }
        catch (Throwable error) {
            // Never emit exception text/causes: vendor errors might contain a hardware descriptor.
            System.err.println("{\"status\":\"failed\",\"error_type\":\"" + error.getClass().getSimpleName()
                + "\",\"detail\":\"Trial gate failed or operation failed; inspect local baseline before retry\"}");
            System.exit(1);
        }
    }
}

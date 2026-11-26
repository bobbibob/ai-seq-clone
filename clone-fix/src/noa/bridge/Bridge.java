package noa.bridge;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;
import java.io.File;
import java.lang.reflect.*;
import java.time.LocalDate;
import java.util.*;

/** v0.3-compatible API adapter with durable internal IDs on a separate socket. */
public final class Bridge {
    static final String TARGET = "com.technosub.unlimitedaiimagen";
    static ClassLoader loader;
    static Class<?> cls(String name) throws Exception { return Class.forName(name, true, loader); }
    static Object get(Object object, String name) throws Exception {
        Class<?> type = object instanceof Class ? (Class<?>) object : object.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object instanceof Class ? null : object);
    }
    static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    static Class<?> box(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        return type;
    }
    static Object invoke(Object object, String name, Object... arguments) throws Exception {
        Class<?> type = object instanceof Class ? (Class<?>) object : object.getClass();
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterTypes().length != arguments.length) continue;
            Class<?>[] signature = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < arguments.length; i++)
                if (arguments[i] != null && !box(signature[i]).isInstance(arguments[i])) matches = false;
            if (matches) {
                method.setAccessible(true);
                return method.invoke(object instanceof Class ? null : object, arguments);
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
    static Context context() throws Exception {
        Application app = (Application) invoke(Class.forName("android.app.ActivityThread"), "currentApplication");
        if (app == null) throw new IllegalStateException("Application unavailable");
        loader = app.getClassLoader();
        return app;
    }
    static List<Object> targets(Context ctx) throws Exception {
        List<?> rows = (List<?>) invoke(cls("com.polestar.superclone.db.c"), "b", ctx);
        List<Object> result = new ArrayList<>();
        for (Object model : rows) if (TARGET.equals(get(model, "b"))) result.add(model);
        return result;
    }
    static int uid(Object model) throws Exception { return ((Number) invoke(model, "a")).intValue(); }
    static int highWater(List<Object> models) throws Exception {
        int high = -1;
        for (Object model : models) high = Math.max(high, uid(model));
        return high;
    }
    static void delete(Context ctx, Object model) throws Exception {
        int id = uid(model);
        String before = state(ctx, id);
        if (!before.contains(" db=true installed=true "))
            throw new IllegalStateException("Cannot delete inconsistent clone: " + before);
        // Verified v0.3 single-clone cleanup entrypoint; v0.2's bulk method is insufficient.
        invoke(cls("org.hf"), "a", ctx, model);
        for (Object remaining : targets(ctx))
            if (uid(remaining) == id) throw new IllegalStateException("Deletion not confirmed: " + id);
        String after = state(ctx, id);
        if (!after.contains(" db=false installed=false "))
            throw new IllegalStateException("Virtual cleanup not confirmed: " + after);
        Object registry = invoke(cls("org.c00"), "b");
        Object lock = get(registry, "a");
        synchronized (lock) {
            Iterator<?> iterator = ((List<?>) lock).iterator();
            while (iterator.hasNext()) {
                Object row = iterator.next();
                if (row != null && TARGET.equals(get(row, "b")) && uid(row) == id) iterator.remove();
            }
        }
    }
    static String state(Context ctx, int id) throws Exception {
        Object core = get(cls("com.polestar.clone.client.core.VirtualCore"), "p");
        Object manager = invoke(core, "k");
        Object installedValue = invoke(manager, "isAppInstalledAsUser", id, TARGET);
        Object usersValue = invoke(manager, "getPackageInstalledUsers", TARGET);
        if (!(installedValue instanceof Boolean) || !(usersValue instanceof int[]))
            throw new IllegalStateException("Unknown virtual package state");
        boolean installed = (Boolean) installedValue;
        boolean listed = false;
        for (int user : (int[]) usersValue) if (user == id) listed = true;
        if (installed != listed) throw new IllegalStateException("Inconsistent virtual package state");
        int count = 0;
        for (Object row : targets(ctx)) if (uid(row) == id) count++;
        if (count > 1) throw new IllegalStateException("Duplicate clone ID");
        return "id=" + id + " db=" + (count == 1) + " installed=" + installed
                + " users=" + Arrays.toString((int[]) usersValue);
    }
    static void ensureVirtualUser(int id) throws Exception {
        Object userManager = invoke(cls("org.vh4"), "a");
        Object service = get(userManager, "a");
        if (service == null) throw new IllegalStateException("Virtual user manager unavailable");
        // Super Clone's helper creates ONE lowest-free user, not necessarily the
        // requested ID. Fill missing user metadata before asking it to install.
        for (int attempt = 0; attempt <= DailyIds.MAX_ID; attempt++) {
            if (invoke(service, "getUserInfo", id) != null) return;
            if (invoke(service, "createUser", "NOA reserved user", 2) == null)
                throw new IllegalStateException("Cannot allocate virtual user " + id);
        }
        throw new IllegalStateException("Virtual user allocation did not reach " + id);
    }
    static void removeHiddenTargetInstalls(Context ctx, int keep) throws Exception {
        Object core = get(cls("com.polestar.clone.client.core.VirtualCore"), "p");
        Object manager = invoke(core, "k");
        Set<Integer> records = new HashSet<>();
        for (Object row : targets(ctx)) records.add(uid(row));
        int[] users = (int[]) invoke(manager, "getPackageInstalledUsers", TARGET);
        for (int id : users) {
            // User 0 is Super Clone's base package installation, not a clone tile.
            // Creating intermediate virtual users can reinstall that base package
            // for users whose clone records were deleted. Remove only these ghosts.
            if (id == 0 || id == keep || records.contains(id)) continue;
            if (!(Boolean) invoke(manager, "uninstallPackageAsUser", TARGET, id))
                throw new IllegalStateException("Hidden target install cleanup failed: " + id);
            if ((Boolean) invoke(manager, "isAppInstalledAsUser", id, TARGET))
                throw new IllegalStateException("Hidden target install remains: " + id);
        }
    }
    static String create(Context ctx, int id, String day) throws Exception {
        for (Object model : targets(ctx))
            if (uid(model) == id) throw new IllegalStateException("ID already occupied: " + id);
        PackageInfo info = ctx.getPackageManager().getPackageInfo(TARGET, 0);
        Object model = cls("com.polestar.superclone.model.AppModel").getDeclaredConstructor().newInstance();
        set(model, "b", TARGET);
        set(model, "j", info);
        Object core = get(cls("com.polestar.clone.client.core.VirtualCore"), "p");
        boolean installed = (Boolean) invoke(core, "n", id, TARGET);
        // A stale virtual install must never be mistaken for a fresh clone.
        if (installed) throw new IllegalStateException("Stale virtual install at reserved ID " + id);
        if (!(Boolean) invoke(cls("org.hf"), "g", model, id))
            throw new IllegalStateException("Virtual install failed at reserved ID " + id);
        String title = "AI Image Generator +" + day.replace("-", "") + "_"
                + String.format(Locale.ROOT, "%02d", id);
        set(model, "d", title);
        set(model, "g", System.currentTimeMillis());
        List<?> all = (List<?>) invoke(cls("com.polestar.superclone.db.c"), "b", ctx);
        set(model, "f", ((id & 255) << 16) | (all.size() & 65535));
        Object session = invoke(cls("com.polestar.superclone.db.c"), "a", ctx);
        invoke(get(session, "a"), "insert", model);
        invoke(cls("org.hf"), "f", TARGET);
        Object registry = invoke(cls("org.c00"), "b");
        Object lock = get(registry, "a");
        synchronized (lock) { ((List<Object>) lock).add(model); }
        boolean confirmed = false;
        for (Object row : targets(ctx)) if (uid(row) == id) confirmed = true;
        if (!confirmed) throw new IllegalStateException("Creation not confirmed in DB");
        removeHiddenTargetInstalls(ctx, id);
        return "created id=" + id + " name=" + title;
    }
    public static synchronized String dispatch(String command) {
        try {
            Context ctx = context();
            if (command.equals("ping")) return "pong";
            if (command.equals("capabilities")) return "persistent-daily-ids-v1 rotate-all-v1";
            if (command.startsWith("inspect ")) return state(ctx, Integer.parseInt(command.substring(8).trim()));
            List<Object> models = targets(ctx);
            if (command.equals("list")) {
                StringBuilder out = new StringBuilder();
                for (Object model : models)
                    out.append("id=").append(uid(model)).append(" name=").append(get(model, "d")).append('\n');
                return out.length() == 0 ? "none" : out.toString().trim();
            }
            if (command.startsWith("delete ")) {
                int id = Integer.parseInt(command.substring(7).trim());
                for (Object model : models) if (uid(model) == id) { delete(ctx, model); return "deleted id=" + id; }
                throw new IllegalArgumentException("Clone not found");
            }
            boolean rotate = command.startsWith("rotate ");
            if (rotate || command.equals("create")) {
                int floor = highWater(models);
                if (rotate) floor = Math.max(floor, Integer.parseInt(command.substring(7).trim()));
                Set<Integer> occupied = new HashSet<>();
                if (!rotate) for (Object model : models) occupied.add(uid(model));
                String day = LocalDate.now().toString();
                DailyIds ids = new DailyIds(new File(ctx.getNoBackupFilesDir(), "noa-daily-ids/journal"));
                int reserved = ids.reserve(day, floor, occupied);
                ensureVirtualUser(reserved);
                // Durable reservation is complete BEFORE any clone is deleted.
                if (rotate) {
                    for (Object model : models) delete(ctx, model);
                    if (!targets(ctx).isEmpty()) throw new IllegalStateException("Clones remain; creation stopped");
                }
                return create(ctx, reserved, day);
            }
            return "ERROR unsupported command";
        } catch (Throwable error) {
            while (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
            Log.e("NOAClone", "API error", error);
            return "ERROR " + error.getClass().getSimpleName() + ": " + error.getMessage();
        }
    }
}

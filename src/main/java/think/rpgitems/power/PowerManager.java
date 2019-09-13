package think.rpgitems.power;

import cat.nyaa.nyaacore.Pair;
import cat.nyaa.nyaacore.utils.ClassPathUtils;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import think.rpgitems.AdminHandler;
import think.rpgitems.RPGItems;

import javax.annotation.CheckForNull;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Power Manager for registering and inspecting powers.
 */
@SuppressWarnings("unchecked")
public class PowerManager {
    private static final Map<Class<? extends PropertyHolder>, Map<String, Pair<Method, PropertyInstance>>> properties = new HashMap<>();

    private static final Map<Class<? extends PropertyHolder>, Meta> metas = new HashMap<>();

    private static final Map<String, Plugin> extensions = new HashMap<>();

    /**
     * Power by name, and name by power
     */
    static BiMap<NamespacedKey, Class<? extends Power>> powers = HashBiMap.create();

    private static BiMap<NamespacedKey, Class<? extends Condition>> conditions = HashBiMap.create();

    private static final HashBasedTable<Plugin, String, BiFunction<NamespacedKey, String, String>> descriptionResolvers = HashBasedTable.create();

    static final HashBasedTable<Class<? extends Pimpl>, Class<? extends Pimpl>, Function> adapters = HashBasedTable.create();

    private static final HashMap<NamespacedKey, NamespacedKey> overrides = new HashMap<>();

    private static void registerPower(Class<? extends Power> clazz) {
        NamespacedKey key = null;
        try {
            Power p = PowerManager.instantiate(clazz);
            key = p.getNamespacedKey();
            if (key != null) {
                powers.put(key, clazz);
            } else {
                return;
            }
            metas.put(clazz, clazz.getAnnotation(Meta.class));
            Map<String, Pair<Method, PropertyInstance>> propertyMap = scanProperties(clazz);
            properties.put(clazz, propertyMap);
        } catch (Throwable e) {
            RPGItems.plugin.getLogger().log(Level.WARNING, "Failed to add power", e);
            RPGItems.plugin.getLogger().log(Level.WARNING, "With {0}", clazz);
            if (key != null) {
                powers.remove(key);
                metas.remove(clazz);
                properties.remove(clazz);
            }
        }
    }

    private static void registerCondition(Class<? extends Condition> clazz) {
        NamespacedKey key;
        try {
            Condition p = PowerManager.instantiate(clazz);
            key = p.getNamespacedKey();
            if (key != null) {
                conditions.put(key, clazz);
            }
        } catch (Exception e) {
            RPGItems.plugin.getLogger().log(Level.WARNING, "Failed to add power", e);
            RPGItems.plugin.getLogger().log(Level.WARNING, "With {0}", clazz);
            return;
        }
        metas.put(clazz, clazz.getAnnotation(Meta.class));
        Map<String, Pair<Method, PropertyInstance>> propertyMap = scanProperties(clazz);
        properties.put(clazz, propertyMap);
    }


    public static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }


    private static Map<String, Pair<Method, PropertyInstance>> scanProperties(Class<? extends PropertyHolder> cls) {
        RPGItems.logger.severe("Scanning class " + cls.toGenericString());
        List<Method> methods = Arrays.stream(cls.getMethods()).collect(Collectors.toList());
        List<Pair<Field, Property>> collect = getAllFields(cls)
                                                      .stream()
                                                      .map(field -> Pair.of(field, field.getAnnotation(Property.class)))
                                                      .filter(pair -> pair.getValue() != null)
                                                      .sorted(Comparator.comparingInt(p -> p.getValue().order()))
                                                      .collect(Collectors.toList());

        int requiredOrder = collect.stream()
                                   .map(Pair::getValue)
                                   .filter(Property::required)
                                   .reduce((first, second) -> second)
                                   .map(Property::order)
                                   .orElse(-1);

        return collect.stream()
                      .collect(
                              Collectors.toMap(
                                      p -> p.getKey().getName(),
                                      p -> {
                                          String name = p.getKey().getName();
                                          return Pair.of(metas.get(cls).marker() ? null :
                                                                 methods.stream()
                                                                        .filter(
                                                                                m -> m.getParameterCount() == 0 &&
                                                                                             (m.getName().toLowerCase(Locale.ROOT).equals("get" + name.toLowerCase(Locale.ROOT))
                                                                                                      || m.getName().toLowerCase(Locale.ROOT).equals("is" + name.toLowerCase(Locale.ROOT))
                                                                                                      || m.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))
                                                                                                      || m.getName().toLowerCase(Locale.ROOT).replaceAll("(is)|(get)", "").equals(name.toLowerCase(Locale.ROOT).replaceAll("(is)|(get)", ""))
                                                                                             )
                                                                        )
                                                                        .reduce((a, b) -> {
                                                                            throw new IllegalArgumentException(p.getKey() + " " + name + " " + a.toString() + " " + b.toString());
                                                                        })
                                                                        .orElseThrow(() -> new IllegalArgumentException(p.getKey() + " " + name)),
                                                  PropertyInstance.from(p.getKey(), p.getValue(), p.getValue().order() < requiredOrder));
                                      }
                              )
                      );
    }

    public static void registerPowers(Plugin plugin, String basePackage) {
        Class<? extends Power>[] classes = ClassPathUtils.scanSubclasses(plugin, basePackage, Power.class);
        List<Class<? extends Power>> classList = Arrays.stream(classes).filter(c -> c.getAnnotation(Meta.class) != null).collect(Collectors.toList());
        registerPowers(plugin, classList);
    }

    @SuppressWarnings({"WeakerAccess"})
    public static void registerPowers(Plugin plugin, List<Class<? extends Power>> powers) {
        extensions.put(plugin.getName().toLowerCase(Locale.ROOT), plugin);
        powers.stream().filter(c -> !Modifier.isAbstract(c.getModifiers()) && !c.isInterface()).sorted(Comparator.comparing(Class::getCanonicalName)).forEach(PowerManager::registerPower);
    }


    public static void registerConditions(Plugin plugin, String basePackage) {
        Class<? extends Condition>[] classes = ClassPathUtils.scanSubclasses(plugin, basePackage, Condition.class);
        List<Class<? extends Condition>> classList = Arrays.stream(classes).filter(c -> c.getAnnotation(Meta.class) != null).collect(Collectors.toList());
        registerConditions(plugin, classList);
    }

    @SuppressWarnings({"WeakerAccess"})
    public static void registerConditions(Plugin plugin, List<Class<? extends Condition>> conditions) {
        extensions.put(plugin.getName().toLowerCase(Locale.ROOT), plugin);
        conditions.stream().filter(c -> !Modifier.isAbstract(c.getModifiers()) && !c.isInterface()).sorted(Comparator.comparing(Class::getCanonicalName)).forEach(PowerManager::registerCondition);
    }

    public static void addDescriptionResolver(Plugin plugin, BiFunction<NamespacedKey, String, String> descriptionResolver) {
        addDescriptionResolver(plugin, RPGItems.plugin.cfg.language, descriptionResolver);
    }

    public static void addDescriptionResolver(Plugin plugin, String locale, BiFunction<NamespacedKey, String, String> descriptionResolver) {
        descriptionResolvers.put(plugin, locale, descriptionResolver);
    }

    public static NamespacedKey parseKey(String powerStr) throws UnknownExtensionException {
        if (!powerStr.contains(":")) return new NamespacedKey(RPGItems.plugin, powerStr);
        String[] split = powerStr.split(":");
        if (split.length != 2) {
            throw new IllegalArgumentException();
        }

        Plugin namespace = extensions.get(split[0].toLowerCase(Locale.ROOT));
        if (namespace == null) {
            throw new UnknownExtensionException(split[0]);
        }
        return new NamespacedKey(namespace, split[1]);
    }

    public static void setPowerProperty(CommandSender sender, Power power, String field, String value) throws IllegalAccessException {
        Field f;
        Class<? extends Power> cls = power.getClass();
        try {
            f = cls.getField(field);
        } catch (NoSuchFieldException e) {
            throw new AdminHandler.CommandException("internal.error.invalid_command_arg", e);//TODO
        }
        Utils.setPowerProperty(sender, power, f, value);
    }

    public static List<String> getAcceptedValue(Class<? extends PropertyHolder> cls, AcceptedValue anno) {
        if (anno.preset() != Preset.NONE) {
            return Stream.concat(Arrays.stream(anno.value()), anno.preset().get(cls).stream())
                         .sorted()
                         .collect(Collectors.toList());
        } else {
            return Arrays.asList(anno.value());
        }
    }

    /**
     * @return All registered extensions mapped by theirs name
     */
    @SuppressWarnings("unused")
    public static Map<String, Plugin> getExtensions() {
        return Collections.unmodifiableMap(extensions);
    }

    /**
     * @return All registered powers' properties mapped by theirs class
     */
    @SuppressWarnings("unused")
    public static Map<Class<? extends PropertyHolder>, Map<String, Pair<Method, PropertyInstance>>> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    /**
     * @return All registered powers mapped by theirs key
     */
    @SuppressWarnings("unused")
    public static Map<NamespacedKey, Class<? extends Power>> getPowers() {
        return Collections.unmodifiableMap(powers);
    }

    public static Map<String, Pair<Method, PropertyInstance>> getProperties(Class<? extends PropertyHolder> cls) {
        return Collections.unmodifiableMap(properties.get(cls));
    }

    public static Map<String, Pair<Method, PropertyInstance>> getProperties(NamespacedKey key) {
        return getProperties(powers.get(key));
    }

    @CheckForNull
    public static Class<? extends Power> getPower(NamespacedKey key) {
        return powers.get(overrides.computeIfAbsent(key, Function.identity()));
    }

    @CheckForNull
    public static Class<? extends Condition> getCondition(NamespacedKey key) {
        return conditions.get(overrides.computeIfAbsent(key, Function.identity()));
    }

    public static <T extends PropertyHolder> T instantiate(Class<T> clz) {
        try {
            return clz.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            RPGItems.logger.severe("not instantiatable: " + clz);
            throw new RuntimeException(e);
        }
    }

    public static boolean hasPower(NamespacedKey key) {
        return powers.containsKey(key);
    }

    public static String getDescription(String locale, NamespacedKey power, String property) {
        Plugin plugin = extensions.get(power.getNamespace());
        if (!PowerManager.descriptionResolvers.contains(plugin, locale)) {
            return null;
        }
        return PowerManager.descriptionResolvers.get(plugin, locale).apply(power, property);
    }

    public static String getDescription(NamespacedKey power, String property) {
        return getDescription(RPGItems.plugin.cfg.language, power, property);
    }

    static boolean hasExtension() {
        return extensions.size() > 1;
    }

    public static Meta getMeta(NamespacedKey key) {
        return getMeta(powers.get(key));
    }

    public static Meta getMeta(Class<? extends PropertyHolder> cls) {
        return metas.get(cls);
    }

    public static <G extends Pimpl, S extends Pimpl> void registerAdapter(Class<G> general, Class<S> specified, Function<G, S> adapter) {
        adapters.put(general, specified, adapter);
    }

    public static <T extends Pimpl> T adaptPower(Pimpl pimpl, Class<T> specified) {
        List<Class<? extends Pimpl>> generals = Arrays.asList(getMeta(pimpl.getPower().getNamespacedKey()).generalInterface());
        Set<Class<? extends Pimpl>> statics = Power.getStaticInterfaces(pimpl.getClass());
        List<Class<? extends Pimpl>> preferences = generals.stream().filter(statics::contains).collect(Collectors.toList());

        for (Class<? extends Pimpl> general : preferences) {
            if (adapters.contains(general, specified)) {
                return (T) adapters.get(general, specified).apply(pimpl);
            }
        }
        throw new ClassCastException();
    }

    public static void registerOverride(NamespacedKey origin, NamespacedKey override) {
        if (overrides.containsKey(origin)) {
            throw new IllegalArgumentException("Cannot override a already overridden power: " + origin + " " + override);
        }
        Class<? extends Power> originPower = getPower(origin);
        Class<? extends Power> overridePower = getPower(override);
        if (originPower == null) {
            throw new IllegalArgumentException("Overriding not registered power: " + origin);
        }
        if (overridePower == null) {
            throw new IllegalArgumentException("Override not found: " + override);
        }
        if (!originPower.isAssignableFrom(overridePower)) {
            throw new IllegalArgumentException("Not overrideable: " + origin + "@" + originPower.toGenericString() + " " + override + "@" + overridePower.toGenericString());
        }
        overrides.put(origin, override);
    }

    public static Pimpl createImpl(Class<? extends Power> cls, Power p) {
        if (!cls.isInstance(p)) throw new IllegalArgumentException();
        Class<? extends Pimpl> pimpl = getMeta(cls).implClass();
        if (pimpl.equals(Pimpl.class)) throw new IllegalStateException();
        try {
            return pimpl.getConstructor(cls).newInstance(p);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            RPGItems.logger.log(Level.SEVERE, "Invalid impl: " + pimpl + " for " + cls, e);
            throw new RuntimeException(e);
        }
    }
}

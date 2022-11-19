package endorh.simpleconfig.core.wrap;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.google.common.collect.Lists;
import com.google.gson.internal.Primitives;
import endorh.simpleconfig.SimpleConfigMod;
import endorh.simpleconfig.api.*;
import endorh.simpleconfig.api.SimpleConfig.EditType;
import endorh.simpleconfig.api.SimpleConfig.Type;
import endorh.simpleconfig.api.entry.BooleanEntryBuilder;
import endorh.simpleconfig.api.ui.icon.Icon;
import endorh.simpleconfig.api.ui.icon.Icon.IconBuilder;
import endorh.simpleconfig.core.*;
import endorh.simpleconfig.core.SimpleConfigBuilderImpl.ConfigValueBuilder;
import endorh.simpleconfig.core.wrap.MinecraftServerConfigWrapper.MinecraftGameRulesWrapperBuilder.MinecraftServerPropertyEntryDelegate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerPropertiesProvider;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.PropertyManager;
import net.minecraft.server.dedicated.ServerProperties;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameRules.BooleanValue;
import net.minecraft.world.GameRules.IntegerValue;
import net.minecraft.world.GameRules.RuleValue;
import net.minecraft.world.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static endorh.simpleconfig.api.ConfigBuilderFactoryProxy.*;
import static endorh.simpleconfig.api.SimpleConfigTextUtil.optSplitTtc;
import static endorh.simpleconfig.api.entry.RangedEntryBuilder.InvertibleDouble2DoubleFunction.*;

@EventBusSubscriber(bus=Bus.MOD, modid=SimpleConfigMod.MOD_ID)
public class MinecraftServerConfigWrapper {
	private static final Logger LOGGER = LogManager.getLogger();
	public static final String MINECRAFT_MOD_ID = "minecraft";
	private static SimpleConfigImpl config;
	
	private static void wrapMinecraftGameRules() {
		try {
			MinecraftGameRulesWrapperBuilder builder = new MinecraftGameRulesWrapperBuilder();
			config = builder.build();
		} catch (RuntimeException e) {
			LOGGER.error("Error wrapping Minecraft server config", e);
		}
	}
	
	@SubscribeEvent
	public static void onLoadComplete(FMLLoadCompleteEvent event) {
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> MinecraftServerConfigWrapper::wrapMinecraftGameRules);
	}
	
	@EventBusSubscriber(value=Dist.DEDICATED_SERVER, modid=SimpleConfigMod.MOD_ID)
	public static class ServerEventSubscriber {
		@SubscribeEvent public static void onServerAboutToStart(FMLServerAboutToStartEvent event) {
			wrapMinecraftGameRules();
			DedicatedServer server = (DedicatedServer) event.getServer();
			// Add default value to file
			MinecraftServerPropertyEntryDelegate<Boolean> delegate = new MinecraftServerPropertyEntryDelegate<>(
			  "edit-protected-properties", boolean.class, false, null);
			delegate.bind(server);
			delegate.setValue(delegate.getValue());
		}
	}
	
	@OnlyIn(Dist.DEDICATED_SERVER)
	public static boolean areProtectedPropertiesEditable() {
		if (config != null) return config.hasChild("properties.protected");
		DedicatedServer server = (DedicatedServer) ServerLifecycleHooks.getCurrentServer();
		ServerProperties properties = server.getServerProperties();
		Method Settings$get = ObfuscationReflectionHelper.findMethod(
		  PropertyManager.class, "func_218982_a", String.class, boolean.class);
		Settings$get.setAccessible(true);
		try {
			return (boolean) Settings$get.invoke(
			  properties, "edit-protected-properties", false);
		} catch (IllegalAccessException | InvocationTargetException ignored) {
			return false;
		}
	}
	
	public static class MinecraftGameRulesWrapperBuilder {
		private final SimpleConfigBuilderImpl builder =
		  (SimpleConfigBuilderImpl) config(MINECRAFT_MOD_ID, Type.SERVER);
		private ConfigEntryHolderBuilder<?> target = builder;
		private boolean caption = false;
		private final MinecraftGameRuleConfigValueBuilder
		  vb = new MinecraftGameRuleConfigValueBuilder();
		private final List<MinecraftServerPropertyEntryDelegate<?>> delegates = Lists.newArrayList();
		
		public SimpleConfigImpl build() {
			try {
				with(
				  category("gamerule").withIcon(Icons.GAMERULES).withColor(0x804242FF),
				  this::addGameRuleEntries);
				with(
				  category("properties").withIcon(Icons.PROPERTIES).withColor(0x8042FF42),
				  this::addServerPropertiesEntries);
				DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> builder.withGUIDecorator((s, b) -> {
					if (!SimpleConfigNetworkHandler.isConnectedToDedicatedServer()) {
						b.removeCategory("properties", EditType.SERVER);
					} else {
						SimpleConfigImpl config = (SimpleConfigImpl) s;
						b.getOrCreateCategory("properties", EditType.SERVER).setLoadingFuture(
						  SimpleConfigNetworkHandler.requestServerProperties().handle((p, t) -> {
							  if (p != null) {
								  CommentedConfig c = p.getRight();
								  if (c != null) {
									  boolean protectedProperties = p.getLeft();
									  config.loadSnapshot(
										 c, false, false, pp -> pp.startsWith("properties."));
									  config.loadSnapshot(
									    c, true, false, true, pp -> pp.startsWith("properties."));
									  return cc -> {
										  cc.finishLoadingEntries();
										  cc.removeEntry(protectedProperties? "protected-disclaimer" : "protected");
										  return true;
									  };
								  }
							  }
							  return cc -> false;
						  }));
					}
				}));
				SimpleConfigImpl config = builder.buildAndRegister(null, vb);
				DistExecutor.unsafeRunWhenOn(Dist.DEDICATED_SERVER, () -> () -> {
					DedicatedServer server = (DedicatedServer) ServerLifecycleHooks.getCurrentServer();
					delegates.forEach(d -> d.bind(server));
				});
				return config;
			} catch (RuntimeException e) {
				e.printStackTrace();
				throw e;
			}
		}
		
		private static final Map<GameRules.RuleKey<?>, GameRules.RuleType<?>> GAME_RULES =
		  ObfuscationReflectionHelper.getPrivateValue(GameRules.class, null, "field_223623_z");
		private static final Map<GameRules.RuleKey<IntegerValue>, ConfigEntryBuilder<Integer, ?, ?, ?>> OVERRIDES = Util.make(new HashMap<>(), m -> {
			m.put(GameRules.MAX_ENTITY_CRAMMING, number(24).sliderRange(1, 256).sliderMap(pow(2)));
			m.put(GameRules.RANDOM_TICK_SPEED, number(3, 0, 3000).sliderMap(expMap(8)));
			m.put(GameRules.SPAWN_RADIUS, number(10, 0, 64).sliderMap(pow(2)));
		});
		private void addGameRuleEntries() {
			if (GAME_RULES == null) throw new IllegalStateException(
			  "Cannot access GameRules#GAME_RULES");
			GAME_RULES.forEach((k, t) -> {
				RuleValue<?> rule = t.createValue();
				if (rule instanceof GameRules.BooleanValue) {
					boolean defValue = ((BooleanValue) rule).get();
					add(k, yesNo(defValue));
				} else if (rule instanceof GameRules.IntegerValue) {
					int defValue = ((IntegerValue) rule).get();
					ConfigEntryBuilder<Integer, ?, ?, ?> override = OVERRIDES.get(k);
					add(k, override != null? override.withValue(defValue) : number(defValue));
				}
			});
		}
		
		private void addServerPropertiesEntries() {
			addFlag("enable-status", yesNo(true));
			add("motd", string("A Minecraft Server"), (s, m) -> {
				s.setMOTD(m);
				s.getServerStatusResponse().setServerDescription(new StringTextComponent(m));
			});
			
			add("view-distance", number(10).sliderRange(1, 64).sliderMap(expMap(2)), (s, d) -> s.getPlayerList().setViewDistance(d));
			addFlag("entity-broadcast-range-percentage", percent(100).range(10, 1000).sliderMap(expMap(6)));
			
			add("pvp", yesNo(true), MinecraftServer::setAllowPvp);
			addFlag("spawn-animals", yesNo(true));
			addFlag("spawn-npcs", yesNo(true));
			addFlag("spawn-monsters", yesNo(true));
			addFlag("allow-nether", yesNo(true));
			add("allow-flight", yesNo(false), MinecraftServer::setAllowFlight);
			addFlag("enable-command-block", yesNo(false));
			addFlag("difficulty", option(Difficulty.EASY));
			addFlag("gamemode", option(GameType.SURVIVAL));
			addFlag("force-gamemode", yesNo(false));
			add("hardcore", yesNo(false));
			add("level-name", string("world"));
			// announcePlayerAchievements is a legacy setting
			
			addFlag("spawn-protection", number(16).slider("options.chunks").sliderRange(0, 64).sliderMap(expMap(2)));
			addFlag("op-permission-level", number(4).sliderRange(1, 4));
			add("function-permission-level", number(2).sliderRange(1, 4));
			wrap("white-list", yesNo(false), (d, b) -> d.getPlayerList().setWhiteListEnabled(b));
			add("enforce-whitelist", yesNo(false));
			
			add("max-players", number(20).sliderRange(1, 1000).sliderMap(expMap(4)));
			add("max-world-size", number(29999984).range(1, 29999984));
			add("max-tick-time", number(TimeUnit.MINUTES.toMillis(1L)));
			wrap("player-idle-timeout", number(0).sliderRange(0, 120).sliderMap(sqrt()), DedicatedServer::setPlayerIdleTimeout);
			add("rate-limit", number(0));
			add("network-compression-threshold", number(256));
			
			add("use-native-transport", yesNo(true));
			add("sync-chunk-writes", yesNo(true));
			
			with(group("world-gen"), () -> {
				add("level-seed", string(""));
				add("generate-structures", yesNo(true));
				add("level-type", string("default").suggest(
				  "default", "flat", "large_biomes", "amplified",
				  "single_biome", "debug_all_bloock_states"
				));
				add("generator-settings", string("{}"));
			});
			
			with(group("resource-pack"), true, () -> {
				addFlag("resource-pack", string(""));
				addFlag("resource-pack-sha1", string(""));
				addFlag("require-resource-pack", yesNo(false));
				addFlag("resource-pack-prompt", string(""));
			});
			
			if (FMLEnvironment.dist == Dist.CLIENT || areProtectedPropertiesEditable())
				with(group("protected"), () -> {
					addProtected("edit-protected-properties", enable(false));
					addProtected("online-mode", yesNo(true));
					addProtected("prevent-proxy-connections", yesNo(false));
					
					addProtected("server-ip", string(""));
					addProtected("server-port", number(25565));
					
					addProtected("enable-query", yesNo(false));
					addProtected("query.port", number(25565));
					addProtected("enable-rcon", yesNo(false));
					addProtected("rcon.port", number(25575));
					addProtected("rcon.password", string(""));
					
					addProtected("broadcast-rcon-to-ops", yesNo(true));
					addProtected("broadcast-console-to-ops", yesNo(true));
					
					addProtected("enable-jmx-monitoring", yesNo(false));
					
					addProtected("text-filtering-config", string(""));
				});
			target.text("protected-disclaimer");
		}
		
		private void with(ConfigCategoryBuilder builder, Runnable runnable) {
			ConfigEntryHolderBuilder<?> prev = target;
			if (prev != this.builder) throw new IllegalStateException(
			  "Categories must be declared at root level");
			target = builder;
			runnable.run();
			this.builder.n(builder);
			target = prev;
		}
		
		private void with(ConfigGroupBuilder builder, Runnable runnable) {
			with(builder, false, runnable);
		}
		
		private void with(ConfigGroupBuilder builder, boolean caption, Runnable runnable) {
			ConfigEntryHolderBuilder<?> prev = target;
			this.caption = caption;
			target = builder;
			runnable.run();
			prev.n(builder);
			target = prev;
			this.caption = false;
		}
		
		private void addEntry(String name, ConfigEntryBuilder<?, ?, ?, ?> entryBuilder) {
			if (caption) {
				if (target instanceof ConfigGroupBuilder) {
					((ConfigGroupBuilder) target).caption(name, castAtom(entryBuilder));
					caption = false;
				} else throw new IllegalStateException(
				  "Cannot add caption outside a group: " + name);
			} else target.add(name, entryBuilder);
		}
		
		@SuppressWarnings("unchecked")
		private <T extends ConfigEntryBuilder<?, ?, ?, ?> & AtomicEntryBuilder> T castAtom(
		  ConfigEntryBuilder<?, ?, ?, ?> entryBuilder
		) {
			if (!(entryBuilder instanceof AtomicEntryBuilder)) throw new IllegalArgumentException(
			  "Entry builder is not atomic: " + entryBuilder.getClass().getCanonicalName());
			return (T) entryBuilder;
		}
		
		private <T> void add(String name, ConfigEntryBuilder<T, ?, ?, ?> entryBuilder) {
			add(name, entryBuilder, null);
		}
		
		private <T> void addProtected(String name, ConfigEntryBuilder<T, ?, ?, ?> entryBuilder) {
			add(name, entryBuilder, null);
		}
		
		private <T> void addFlag(
		  String name, ConfigEntryBuilder<T, ?, ?, ?> entryBuilder
		) {
			doAdd(name, entryBuilder, null);
		}
		
		private <T> void add(
		  String name, ConfigEntryBuilder<T, ?, ?, ?> entryBuilder,
		  @Nullable BiConsumer<DedicatedServer, T> applier
		) {
			if (applier == null) entryBuilder = entryBuilder.restart();
			doAdd(name, entryBuilder, applier != null? (s, t) -> {
				applier.accept(s, t);
				return true;
			} : null);
		}
		
		private <T> void wrap(
		  String name, ConfigEntryBuilder<T, ?, ?, ?> entryBuilder,
		  @Nullable BiConsumer<DedicatedServer, T> applier
		) {
			if (applier == null) entryBuilder = entryBuilder.restart();
			doAdd(name, entryBuilder, applier != null? (s, t) -> {
				applier.accept(s, t);
				return false;
			} : null);
		}
		
		private <T> void doAdd(
		  String name, ConfigEntryBuilder<T, ?, ?, ?> entryBuilder,
		  @Nullable BiFunction<DedicatedServer, T, Boolean> applier
		) {
			AbstractConfigEntryBuilder<T, ?, ?, ?, ?, ?> b = cast(entryBuilder);
			MinecraftServerPropertyEntryDelegate<T> delegate;
			Class<?> typeClass = b.getTypeClass();
			if (typeClass == Boolean.class || typeClass == Integer.class) {
				delegate = new MinecraftServerPropertyEntryDelegate<>(name, typeClass, b.getValue(), applier);
			} else if (typeClass == String.class) {
				//noinspection unchecked
				delegate = new MinecraftServerPropertyEntryDelegate<>(
				  name, s -> (T) s, t -> (String) t, b.getValue(), applier);
			} else if (typeClass == Long.class) {
				delegate = new MinecraftServerPropertyEntryDelegate<>(
				  name, wrapNumberDeserializer(Long::parseLong), Object::toString,
				  b.getValue(), applier);
			} else if (Enum.class.isAssignableFrom(typeClass)) {
				//noinspection unchecked
				delegate = new MinecraftServerPropertyEntryDelegate<>(
				  name, s -> Arrays.stream(typeClass.getEnumConstants()).map(e -> (Enum<?>) e)
				  .filter(e -> e.name().equalsIgnoreCase(s))
				  .map(e -> (T) e).findFirst().orElse(null), t -> ((Enum<?>) t).name(),
				  b.getValue(), applier);
			} else throw new IllegalArgumentException(
			  "Unsupported server property type: " + typeClass);
			delegates.add(delegate);
			addEntry(name.replace('.', '-'), b.withDelegate(delegate));
		}
		
		@SuppressWarnings("unchecked")
		private static <T, V extends Number> Function<String, T> wrapNumberDeserializer(Function<String, V> deserializer) {
			return s -> {
				try {
					return (T) deserializer.apply(s);
				} catch (NumberFormatException numberformatexception) {
					return (T) null;
				}
			};
		}
		
		@SuppressWarnings("unchecked") private void add(GameRules.RuleKey<?> key, BooleanEntryBuilder entryBuilder) {
			AbstractConfigEntryBuilder<Boolean, ?, ?, ?, ?, ?> b = patch(key, entryBuilder);
			addEntry(
			  key.getName(),
			  b.withDelegate(MinecraftGameRuleEntryDelegate.bool((GameRules.RuleKey<BooleanValue>) key)));
		}
		
		@SuppressWarnings("unchecked") private void add(GameRules.RuleKey<?> key, ConfigEntryBuilder<Integer, ?, ?, ?> entryBuilder) {
			AbstractConfigEntryBuilder<Integer, ?, ?, ?, ?, ?> b = patch(key, entryBuilder);
			addEntry(
			  key.getName(),
			  b.withDelegate(MinecraftGameRuleEntryDelegate.integer((GameRules.RuleKey<GameRules.IntegerValue>) key)));
		}
		
		private <T> AbstractConfigEntryBuilder<T, ?, ?, ?, ?, ?> patch(
		  GameRules.RuleKey<?> key, ConfigEntryBuilder<T, ?, ?, ?> entryBuilder
		) {
			entryBuilder = entryBuilder.tooltip(() -> Stream.concat(
			  optSplitTtc(key.getLocaleString() + ".description").stream(),
			  Stream.of(new StringTextComponent("/gamerule " + key.getName()).mergeStyle(TextFormatting.GRAY))
			).collect(Collectors.toList()));
			AbstractConfigEntryBuilder<T, ?, ?, ?, ?, ?> b = cast(entryBuilder);
			return b.translation(key.getLocaleString());
		}
		
		@SuppressWarnings("unchecked") private static <T> AbstractConfigEntryBuilder<T, ?, ?, ?, ?, ?> cast(
		  ConfigEntryBuilder<T, ?, ?, ?> entryBuilder
		) {
			return (AbstractConfigEntryBuilder<T, ?, ?, ?, ?, ?>) entryBuilder;
		}
		
		public static class MinecraftGameRuleEntryDelegate<T, V extends GameRules.RuleValue<V>>
		  implements ConfigEntryDelegate<T> {
			public static MinecraftGameRuleEntryDelegate<Boolean, GameRules.BooleanValue> bool(
			  GameRules.RuleKey<GameRules.BooleanValue> key
			) {
				return new MinecraftGameRuleEntryDelegate<>(
				  key, BooleanValue::get,
				  (v, t) -> v.set(t, ServerLifecycleHooks.getCurrentServer()));
			}
			
			public static MinecraftGameRuleEntryDelegate<Integer, GameRules.IntegerValue> integer(
			  GameRules.RuleKey<GameRules.IntegerValue> key
			) {
				return new MinecraftGameRuleEntryDelegate<>(
				  key, GameRules.IntegerValue::get,
				  (v, t) -> {
					  try {
						  IntegerValue$value.set(v, t);
						  GameRules$RuleValue$notifyChange.invoke(v, ServerLifecycleHooks.getCurrentServer());
					  } catch (IllegalAccessException | InvocationTargetException e) {
						  LOGGER.error("Cannot set game rule value: " + key.getName(), e);
						  throw new RuntimeException(e);
					  }
				  });
			}
			
			private static final Field IntegerValue$value = ObfuscationReflectionHelper.findField(
			  GameRules.IntegerValue.class, "field_223566_a");
			private static final Method GameRules$RuleValue$notifyChange = ObfuscationReflectionHelper.findMethod(
			  GameRules.RuleValue.class, "func_223556_a", MinecraftServer.class);
			
			private final GameRules.RuleKey<V> key;
			private final Function<V, T> getter;
			private final BiConsumer<V, T> setter;
			private T value;
			
			public MinecraftGameRuleEntryDelegate(
			  GameRules.RuleKey<V> key, Function<V, T> getter, BiConsumer<V, T> setter
			) {
				this.key = key;
				this.getter = getter;
				this.setter = setter;
			}
			
			@Override public T getValue() {
				return DistExecutor.unsafeRunForDist(() -> () -> value, () -> () -> {
					GameRules rules = ServerLifecycleHooks.getCurrentServer().getGameRules();
					return getter.apply(rules.get(key));
				});
			}
			
			@Override public void setValue(T value) {
				DistExecutor.unsafeRunForDist(() -> () -> {
					this.value = value;
					return null;
				}, () -> () -> {
					if (getValue().equals(value)) return null;
					MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
					GameRules rules = server.getGameRules();
					V rule = rules.get(key);
					setter.accept(rule, value);
					return null;
				});
			}
		}
		
		public static class MinecraftServerPropertyEntryDelegate<T> implements ConfigEntryDelegate<T> {
			private DedicatedServer server = null;
			private ServerPropertiesProvider settings = null;
			private Method Settings$getMutable = null;
			private final String name;
			private final @Nullable Class<?> type;
			private final @Nullable Function<String, T> deserializer;
			private final @Nullable Function<T, String> serializer;
			private final T defValue;
			private final BiFunction<DedicatedServer, T, Boolean> applier;
			private T value;
			
			public MinecraftServerPropertyEntryDelegate(
			  String name, @NotNull Class<?> type, T defValue,
			  @Nullable BiFunction<DedicatedServer, T, Boolean> applier
			) {
				this.name = name;
				this.type = type;
				deserializer = null;
				serializer = null;
				this.defValue = defValue;
				this.applier = applier;
				value = defValue;
			}
			
			public MinecraftServerPropertyEntryDelegate(
			  String name, @NotNull Function<String, T> deserializer,
			  @NotNull Function<T, String> serializer,
			  T defValue, @Nullable BiFunction<DedicatedServer, T, Boolean> applier
			) {
				this.name = name;
				this.deserializer = deserializer;
				this.serializer = serializer;
				type = null;
				this.defValue = defValue;
				this.applier = applier;
				value = defValue;
			}
			
			@OnlyIn(Dist.DEDICATED_SERVER)
			public void bind(DedicatedServer server) {
				this.server = server;
				settings = ObfuscationReflectionHelper.getPrivateValue(
				  DedicatedServer.class, server, "field_71340_o");
			}
			
			@Override public T getValue() {
				return server != null? getAccessor().get() : value;
			}
			
			@Override public void setValue(T value) {
				if (server != null) {
					if (getValue().equals(value)) return;
					if (applier == null || applier.apply(server, value))
						settings.func_219033_a(p -> getAccessor().func_244381_a(server.func_244267_aX(), value));
				} else this.value = value;
			}
			
			@SuppressWarnings("unchecked") private PropertyManager<ServerProperties>.Property<T> getAccessor() {
				if (Settings$getMutable == null) {
					if (type == null) {
						Settings$getMutable = ObfuscationReflectionHelper.findMethod(
						  PropertyManager.class, "func_218981_b",
						  String.class, Function.class, Function.class, Object.class);
					} else if (Primitives.unwrap(type) == boolean.class) {
						Settings$getMutable = ObfuscationReflectionHelper.findMethod(
						  PropertyManager.class, "func_218961_b",
						  String.class, boolean.class);
					} else if (Primitives.unwrap(type) == int.class) {
						Settings$getMutable = ObfuscationReflectionHelper.findMethod(
						  PropertyManager.class, "func_218974_b",
						  String.class, int.class);
					} else throw new IllegalArgumentException(
					  "Unsupported property type: " + type.getCanonicalName());
					Settings$getMutable.setAccessible(true);
				}
				try {
					return (PropertyManager<ServerProperties>.Property<T>)
					  (type == null? Settings$getMutable.invoke(settings.getProperties(), name, deserializer, serializer, defValue)
					               : Settings$getMutable.invoke(settings.getProperties(), name, defValue));
				} catch (IllegalAccessException | InvocationTargetException e) {
					throw new RuntimeException(e);
				}
			}
		}
		
		public static class MinecraftGameRuleConfigValueBuilder extends ConfigValueBuilder {
			private final ModContainer modContainer =
			  ModList.get().getModContainerById(MINECRAFT_MOD_ID)
				 .orElseThrow(() -> new IllegalStateException("Minecraft mod not found"));
			
			@Override public void buildModConfig(SimpleConfigImpl config) {
				config.build(modContainer, null);
			}
			
			@Override public void build(
			  AbstractConfigEntryBuilder<?, ?, ?, ?, ?, ?> entryBuilder,
			  AbstractConfigEntry<?, ?, ?> entry
			) {}
			
			@Override public ForgeConfigSpec build() {
				return null;
			}
		}
	}
	
	public static class Icons {
		private static final IconBuilder b =
		  IconBuilder.ofTexture(new ResourceLocation(
			 SimpleConfigMod.MOD_ID, "textures/gui/simpleconfig/minecraft_options.png"), 64, 64);
		/**
		 * 16×16
		 */
		public static Icon
		  GAMERULES = b.size(16, 16).at(16, 32),
		  PROPERTIES = b.at(32, 32);
	}
}
package endorh.simpleconfig.core;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.WritingException;
import endorh.simpleconfig.SimpleConfigMod;
import endorh.simpleconfig.SimpleConfigMod.ServerConfig.permissions;
import endorh.simpleconfig.ui.gui.widget.PresetPickerWidget.Preset;
import endorh.simpleconfig.ui.hotkey.SavedHotKeyGroupPickerWidget.RemoteSavedHotKeyGroup;
import endorh.simpleconfig.ui.hotkey.SavedHotKeyGroupPickerWidget.SavedHotKeyGroup;
import endorh.simpleconfig.yaml.SimpleConfigCommentedYamlFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.text.*;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.config.ModConfig.ModConfigEvent;
import net.minecraftforge.fml.config.ModConfig.Reloading;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent.Context;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.naming.NoPermissionException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static endorh.simpleconfig.core.SimpleConfigSnapshotHandler.failedFuture;

/**
 * Handle synchronization of {@link SimpleConfig} data with server config
 */
@Internal public class SimpleConfigNetworkHandler {
	public static Style ALLOWED_UPDATE_STYLE = Style.EMPTY
	  .applyFormatting(TextFormatting.GRAY).setItalic(true);
	public static Style DENIED_UPDATE_STYLE = Style.EMPTY
	  .applyFormatting(TextFormatting.GOLD).setItalic(true);
	public static Style ERROR_UPDATE_STYLE = Style.EMPTY
	  .applyFormatting(TextFormatting.DARK_RED).setItalic(true);
	public static Style REQUIRES_RESTART_STYLE = Style.EMPTY
	  .applyFormatting(TextFormatting.DARK_PURPLE).setItalic(true);
	public static Style ALLOWED_SNAPSHOT_UPDATE_STYLE = ALLOWED_UPDATE_STYLE;
	public static Style DENIED_SNAPSHOT_UPDATE_STYLE = DENIED_UPDATE_STYLE;
	
	private static final Method ModConfig$fireEvent;
	private static final Constructor<Reloading> Reloading$$init;
	private static final boolean reflectionSucceeded;
	private static final Logger LOGGER = LogManager.getLogger();
	
	// Ugly reflection section ----------------------------------------
	
	static {
		// Get all required private members
		final String errorFmt =
		  "Could not access %s by reflection\n" +
		  "SimpleConfig won't be able to sync server config modifications in-game";
		boolean success = false;
		String member = null;
		Method fireEvent = null;
		Constructor<Reloading> reloading = null;
		try {
			member = "ModConfig$fireEvent method";
			fireEvent = ModConfig.class.getDeclaredMethod("fireEvent", ModConfigEvent.class);
			fireEvent.setAccessible(true);
			member = "ModConfig$Reloading constructor";
			reloading = Reloading.class.getDeclaredConstructor(ModConfig.class);
			reloading.setAccessible(true);
			success = true;
		} catch (NoSuchMethodException e) {
			LOGGER.error(String.format(errorFmt, member));
		} finally {
			ModConfig$fireEvent = fireEvent;
			Reloading$$init = reloading;
			reflectionSucceeded = success;
		}
	}
	
	private static void broadcastToOperators(ITextComponent message) {
		ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().stream()
		  .filter(p -> p.hasPermissionLevel(2))
		  .forEach(p -> p.sendMessage(message, Util.DUMMY_UUID));
	}
	
	public static class ConfigUpdateReflectionError extends RuntimeException {
		private ConfigUpdateReflectionError(Throwable cause) {
			super("Something went wrong updating the server configs", cause);
		}
		private ConfigUpdateReflectionError() {
			super("Something went wrong updating the server configs");
		}
	}
	
	@Internal protected static void tryFireEvent(final ModConfig config, final ModConfigEvent event) {
		if (!reflectionSucceeded)
			throw new ConfigUpdateReflectionError();
		try {
			ModConfig$fireEvent.invoke(config, event);
		} catch (InvocationTargetException | IllegalAccessException e) {
			throw new ConfigUpdateReflectionError(e);
		}
	}
	@Internal protected static Reloading newReloading(final ModConfig config) {
		if (!reflectionSucceeded)
			throw new ConfigUpdateReflectionError();
		try {
			return Reloading$$init.newInstance(config);
		} catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
			throw new ConfigUpdateReflectionError(e);
		}
	}
	private static void tryUpdateConfig(
	  final SimpleConfig config, final byte[] fileData
	) {
		SimpleConfigModConfig modConfig = config.getModConfig();
		
		SimpleConfigCommentedYamlFormat format = modConfig.getConfigFormat();
		try {
			// The entry sets should match between clients and server
			CommentedConfig sentConfig = format.createParser(false)
			  .parse(new ByteArrayInputStream(fileData));
			
			modConfig.getConfigData().putAll(sentConfig);
			modConfig.getSpec().afterReload();
			
			tryFireEvent(modConfig, newReloading(modConfig));
		} catch (ParsingException e) {
			LOGGER.error("Failed to parse synced server config for mod " + config.getModId(), e);
		}
	}
	
	// Network channel ------------------------------------------------
	
	private static final String CHANNEL_PROTOCOL_VERSION = "1";
	private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
	  new ResourceLocation(SimpleConfigMod.MOD_ID, "config"),
	  () -> CHANNEL_PROTOCOL_VERSION,
	  CHANNEL_PROTOCOL_VERSION::equals,
	  CHANNEL_PROTOCOL_VERSION::equals
	);
	private static int ID_COUNT = 0;
	
	public static SimpleChannel getChannel() {
		return CHANNEL;
	}
	
	public static boolean isConnectedToSimpleConfigServer() {
		ClientPlayNetHandler connection = Minecraft.getInstance().getConnection();
		if (connection == null) return false;
		return getChannel().isRemotePresent(connection.getNetworkManager());
	}
	
	protected static void registerPackets() {
		if (ID_COUNT != 0) throw new IllegalStateException("Packets registered twice!");
		registerServer(SSimpleConfigSyncPacket::new);
		registerServer(SSimpleConfigSavedPresetPacket::new);
		registerServer(SSimpleConfigPresetListPacket::new);
		registerServer(SSimpleConfigPresetPacket::new);
		registerServer(SSimpleConfigSavedHotKeyGroupsPacket::new);
		registerServer(SSavedHotKeyGroupPacket::new);
		registerServer(SSaveRemoteHotKeyGroupPacket::new);
		
		registerClient(CSimpleConfigSyncPacket::new);
		registerClient(CSimpleConfigSavePresetPacket::new);
		registerClient(CSimpleConfigRequestPresetListPacket::new);
		registerClient(CSimpleConfigRequestPresetPacket::new);
		registerClient(CSimpleConfigRequestSavedHotKeyGroupsPacket::new);
		registerClient(CRequestSavedHotKeyGroupPacket::new);
		registerClient(CSaveRemoteHotKeyGroupPacket::new);
	}
	
	private static <Packet extends CAbstractPacket> void registerClient(Supplier<Packet> factory) {
		registerMessage(factory, NetworkDirection.PLAY_TO_SERVER);
	}
	
	private static <Packet extends SAbstractPacket> void registerServer(Supplier<Packet> factory) {
		registerMessage(factory, NetworkDirection.PLAY_TO_CLIENT);
	}
	
	private static <Packet extends AbstractPacket> void registerMessage(
	  Supplier<Packet> factory, @Nullable NetworkDirection direction
	) {
		final Packet msg = factory.get();
		//noinspection unchecked
		CHANNEL.registerMessage(
		  ID_COUNT++, ((Class<Packet>) msg.getClass()),
		  AbstractPacket::write,
		  b -> {
			  final Packet p = factory.get();
			  p.read(b);
			  return p;
		  }, (p, ctxSupplier) -> p.handle(ctxSupplier.get()),
		  Optional.ofNullable(direction));
	}
	
	// Packets
	protected static byte[] serializeSnapshot(SimpleConfig config) {
		try {
			CommentedConfig snapshot = config.takeSnapshot(false);
			if (snapshot instanceof CommentedFileConfig) {
				return Files.readAllBytes(((CommentedFileConfig) snapshot).getNioPath());
			} else {
				ByteArrayOutputStream arrayWriter = new ByteArrayOutputStream();
				SimpleConfigCommentedYamlFormat format = config.getModConfig().getConfigFormat();
				format.createWriter(false).write(snapshot, arrayWriter);
				return arrayWriter.toByteArray();
			}
		} catch (IOException error) {
			throw new RuntimeException("IO error reading config file", error);
		}
	}
	
	/**
	 * Subclasses must have a constructor with no parameters
	 */
	protected static abstract class AbstractPacket {
		protected abstract void handle(Context ctx);
		public abstract void write(PacketBuffer buf);
		public abstract void read(PacketBuffer buf);
	}
	
	protected static abstract class CAbstractPacket extends AbstractPacket {
		@Override protected final void handle(Context ctx) {
			ctx.setPacketHandled(true);
			onServer(ctx);
		}
		
		public void onServer(Context ctx) {}
		
		public void send() {
			CHANNEL.sendToServer(this);
		}
	}
	
	protected static abstract class SAbstractPacket extends AbstractPacket {
		private static final PacketDistributor<ServerPlayerEntity> EXCEPT =
		  new PacketDistributor<>(
			 (distributor, supplier) -> packet -> {
				 final ServerPlayerEntity exception = supplier.get();
				 final MinecraftServer server = exception.world.getServer();
				 if (server == null)
					 return;
				 server.getPlayerList().getPlayers()
					.stream().filter(p -> exception != p)
					.forEach(p -> p.connection.sendPacket(packet));
			 }, NetworkDirection.PLAY_TO_CLIENT
		  );
		
		public static void sendMessage(ITextComponent message) {
			final ClientPlayerEntity player = Minecraft.getInstance().player;
			if (player == null)
				return;
			player.sendMessage(message, Util.DUMMY_UUID);
		}
		
		@Override protected void handle(Context ctx) {
			ctx.setPacketHandled(true);
			onClient(ctx);
		}
		
		public void onClient(Context ctx) {}
		
		public void sendTo(ServerPlayerEntity player) {
			CHANNEL.sendTo(this, player.connection.netManager, NetworkDirection.PLAY_TO_CLIENT);
		}
		
		public void sendExcept(ServerPlayerEntity player) {
			CHANNEL.send(SAbstractPacket.EXCEPT.with(() -> player), this);
		}
		
		public void sendToAll() {
			CHANNEL.send(PacketDistributor.ALL.noArg(), this);
		}
	}
	
	protected static class CSimpleConfigSyncPacket extends CAbstractPacket {
		protected String modId;
		protected byte[] fileData;
		protected boolean requireRestart;
		
		public CSimpleConfigSyncPacket() {}
		public CSimpleConfigSyncPacket(SimpleConfig config) {
			modId = config.getModId();
			fileData = serializeSnapshot(config);
			requireRestart = config.anyDirtyRequiresRestart();
		}
		
		@Override public void onServer(Context ctx) {
			final ServerPlayerEntity sender = ctx.getSender();
			// Ensure the config has been registered as a SimpleConfig
			SimpleConfig config = SimpleConfig.getConfig(modId, Type.SERVER);
			final String modName = SimpleConfig.getModNameOrId(modId);
			if (sender == null)
				throw new IllegalStateException(
				  "Received server config update from non-player source for mod \"" + modName + "\"");
			final String senderName = sender.getScoreboardName();
			if (!permissions.permissionFor(sender, modId).getLeft().canEdit()) {
				LOGGER.warn("Player \"" + senderName + "\" tried to modify " +
				            "the server config for mod \"" + modName + "\" without privileges");
				broadcastToOperators(new TranslationTextComponent(
				  "simpleconfig.config.msg.tried_to_update_by",
				  senderName, modName).mergeStyle(DENIED_UPDATE_STYLE));
				// Send back a re-sync packet
				new SSimpleConfigSyncPacket(modId, fileData).sendTo(sender);
				return;
			}
			
			try {
				tryUpdateConfig(config, fileData);
				// config.bake(); // This should happen as a consequence of the reloading event
			} catch (ConfigUpdateReflectionError e) {
				e.printStackTrace();
				LOGGER.error("Error updating server config for mod \"" + modName + "\"");
				broadcastToOperators(new TranslationTextComponent(
				  "simpleconfig.config.msg.error_updating_by",
				  modName, senderName, e.getMessage()).mergeStyle(ERROR_UPDATE_STYLE));
			}
			
			LOGGER.info(
			  "Server config for mod \"" + modName + "\" " +
			  "has been updated by authorized player \"" + senderName + "\"");
			IFormattableTextComponent msg = new TranslationTextComponent(
			  "simpleconfig.config.msg.updated_by",
			  modName, senderName).mergeStyle(ALLOWED_UPDATE_STYLE);
			if (requireRestart)
				msg = msg.appendString("\n").append(new TranslationTextComponent(
				  "simpleconfig.config.msg.server_changes_require_restart"
				).mergeStyle(REQUIRES_RESTART_STYLE));
			broadcastToOperators(msg);
			new SSimpleConfigSyncPacket(modId, fileData).sendExcept(sender);
		}
		
		@Override public void write(PacketBuffer buf) {
			buf.writeString(modId);
			buf.writeByteArray(fileData);
			buf.writeBoolean(requireRestart);
		}
		
		@Override public void read(PacketBuffer buf) {
			modId = buf.readString(32767);
			fileData = buf.readByteArray();
			requireRestart = buf.readBoolean();
		}
	}
	
	protected static class SSimpleConfigSyncPacket extends SAbstractPacket {
		protected String modId;
		protected byte[] fileData;
		
		public SSimpleConfigSyncPacket() {}
		public SSimpleConfigSyncPacket(SimpleConfig config) {
			modId = config.getModId();
			fileData = serializeSnapshot(config);
		}
		
		public SSimpleConfigSyncPacket(
		  String modId, byte[] fileData
		) {
			this.modId = modId;
			this.fileData = fileData;
		}
		
		@Override public void onClient(Context ctx) {
			if (!Minecraft.getInstance().isSingleplayer()) {
				SimpleConfig config = SimpleConfig.getConfig(modId, Type.SERVER);
				final String modName = SimpleConfig.getModNameOrId(modId);
				try {
					tryUpdateConfig(config, fileData);
					// config.bake(); // This should happen as a consequence of the reloading event
				} catch (ConfigUpdateReflectionError e) {
					e.printStackTrace();
					sendMessage(new TranslationTextComponent(
					  "simpleconfig.config.msg.error_updating_from_server",
					  modName, e.getMessage()));
				}
			}
		}
		
		@Override public void write(PacketBuffer buf) {
			buf.writeString(modId);
			buf.writeByteArray(fileData);
		}
		
		@Override public void read(PacketBuffer buf) {
			modId = buf.readString(32767);
			fileData = buf.readByteArray();
		}
	}
	
	protected static class CSimpleConfigSavePresetPacket extends CAbstractPacket {
		public static Map<Triple<String, Boolean, String>, CompletableFuture<Void>> FUTURES = new HashMap<>();
		protected String modId;
		protected boolean server;
		protected String presetName;
		protected byte @Nullable [] fileData; // Null means delete
		
		public CSimpleConfigSavePresetPacket() {}
		public CSimpleConfigSavePresetPacket(
		  String modId, boolean server, String presetName, @Nullable CommentedConfig data
		) {
			this.modId = modId;
			this.server = server;
			this.presetName = presetName;
			if (data != null) {
				SimpleConfig config = SimpleConfig.getConfig(modId, Type.SERVER);
				SimpleConfigCommentedYamlFormat format = config.getModConfig().getConfigFormat();
				final ByteArrayOutputStream os = new ByteArrayOutputStream();
				try {
					format.createWriter(false).write(data, os);
				} catch (WritingException e) {
					throw new SimpleConfigSyncException("Error writing config snapshot", e);
				}
				fileData = os.toByteArray();
			} else fileData = null;
		}
		
		@Override public void onServer(Context ctx) {
			final ServerPlayerEntity sender = ctx.getSender();
			if (sender == null) {
				LOGGER.error("Received server config preset from non-player source for mod \"" + modId + "\"");
				return;
			}
			String type = server? "server" : "client";
			String presetName = type + "-" + this.presetName;
			String fileName = modId + "-" + presetName + ".yaml";
			String action = fileData == null? "delete" : "save"; // For messages
			// Ensure the config has been registered as a SimpleConfig
			SimpleConfig.getConfig(modId, Type.SERVER);
			final String modName = SimpleConfig.getModNameOrId(modId);
			final String senderName = sender.getScoreboardName();
			try {
				if (!permissions.permissionFor(sender, modId).getRight().canSave())
					throw new NoPermissionException("No permission for server presets for mod " + modName);
				
				final Path dir = SimpleConfigPaths.getRemotePresetsDir();
				final File dest = dir.resolve(fileName).toFile();
				if (fileData != null) {
					if (dest.isDirectory())
						throw new IllegalStateException("File already exists and is a directory");
					FileUtils.writeByteArrayToFile(dest, fileData);
				} else {
					BasicFileAttributes attr = Files.readAttributes(dest.toPath(), BasicFileAttributes.class);
					if (attr.isDirectory())
						throw new IllegalStateException("File is a directory");
					if (!dest.exists() || !attr.isRegularFile())
						throw new IllegalArgumentException("File does not exist");
					if (!dest.delete()) throw new IllegalStateException("Unable to delete file");
				}
				
				broadcastToOperators(new TranslationTextComponent(
				  "simpleconfig.config.msg.snapshot." + type + "." + action + "d_by",
				  this.presetName, modName, senderName).mergeStyle(ALLOWED_SNAPSHOT_UPDATE_STYLE));
				LOGGER.info(
				  "Server config preset \"" + presetName + "\" for mod \"" + modName + "\" " +
				  "has been " + action + "d by player \"" + senderName + "\"");
				new SSimpleConfigSavedPresetPacket(modId, server, this.presetName, null).sendTo(sender);
			} catch (RuntimeException | IOException e) {
				broadcastToOperators(new TranslationTextComponent(
				  "simpleconfig.config.msg.snapshot.error_updating_by",
				  this.presetName, modName, senderName, e.getMessage()
				).mergeStyle(ERROR_UPDATE_STYLE));
				LOGGER.error("Error " + (fileData != null? "saving" : "deleting") + " server config " +
				             "preset for mod \"" + modName + "\"");
				new SSimpleConfigSavedPresetPacket(
				  modId, server, this.presetName, e.getClass().getSimpleName() + ": " + e.getMessage()
				).sendTo(sender);
			} catch (NoPermissionException e) {
				broadcastToOperators(new TranslationTextComponent(
				  "simpleconfig.config.msg.snapshot." + type + ".tried_to_" + action,
				  senderName, this.presetName, modName
				).mergeStyle(DENIED_SNAPSHOT_UPDATE_STYLE));
				LOGGER.warn("Player \"" + senderName + "\" tried to " +
				            action + " a preset for the server " +
				            "config for mod \"" + modName + "\" without privileges");
				new SSimpleConfigSavedPresetPacket(
				  modId, server, this.presetName, e.getClass().getSimpleName() + ": " + e.getMessage()
				).sendTo(sender);
			}
		}
		
		@Override public void write(PacketBuffer buf) {
			buf.writeString(modId);
			buf.writeBoolean(server);
			buf.writeString(presetName);
			buf.writeBoolean(fileData != null);
			if (fileData != null)
				buf.writeByteArray(fileData);
		}
		
		@Override public void read(PacketBuffer buf) {
			modId = buf.readString(32767);
			server = buf.readBoolean();
			presetName = buf.readString(32767);
			fileData = buf.readBoolean() ? buf.readByteArray() : null;
		}
	}
	
	protected static class SSimpleConfigSavedPresetPacket extends SAbstractPacket {
		protected String modId;
		protected boolean server;
		protected String presetName;
		protected @Nullable String errorMsg;
		
		public SSimpleConfigSavedPresetPacket() {}
		public SSimpleConfigSavedPresetPacket(
		  String modId, boolean server, String presetName, @Nullable String errorMsg
		) {
			this.modId = modId;
			this.server = server;
			this.presetName = presetName;
			this.errorMsg = errorMsg;
		}
		
		@Override public void onClient(Context ctx) {
			final CompletableFuture<Void> future = CSimpleConfigSavePresetPacket.FUTURES.remove(
			  Triple.of(modId, server, presetName));
			if (future == null) return;
			if (errorMsg != null) {
				future.completeExceptionally(new RemoteException(errorMsg));
			} else future.complete(null);
		}
		
		@Override public void write(PacketBuffer buf) {
			buf.writeString(modId);
			buf.writeBoolean(server);
			buf.writeString(presetName);
			buf.writeBoolean(errorMsg != null);
			if (errorMsg != null) buf.writeString(errorMsg);
		}
		
		@Override public void read(PacketBuffer buf) {
			modId = buf.readString(32767);
			server = buf.readBoolean();
			presetName = buf.readString(32767);
			errorMsg = buf.readBoolean()? buf.readString(32767) : null;
		}
	}
	
	protected static class CSimpleConfigRequestPresetListPacket extends CAbstractPacket {
		private static final Map<String, CompletableFuture<List<Preset>>> FUTURES = new HashMap<>();
		protected String modId;
		
		public CSimpleConfigRequestPresetListPacket() {}
		public CSimpleConfigRequestPresetListPacket(String modId) {
			this.modId = modId;
		}
		
		@Override public void onServer(Context ctx) {
			final ServerPlayerEntity sender = ctx.getSender();
			if (sender == null) return;
			final File dir = SimpleConfigPaths.getRemotePresetsDir().toFile();
			if (!dir.isDirectory()) return;
			final Pattern pat = Pattern.compile(
			  "^(?<file>" + Pattern.quote(modId) + "-(?<type>\\w++)-(?<name>.*)\\.yaml)$");
			final File[] files = dir.listFiles((d, name) -> pat.matcher(name).matches());
			if (files == null) return;
			final List<Preset> names = Arrays.stream(files)
			  .map(f -> {
				  Matcher m = pat.matcher(f.getName());
				  if (!m.matches()) return null;
				  return Preset.remote(m.group("name"), "server".equals(m.group("type")));
			  }).filter(Objects::nonNull).collect(Collectors.toList());
			new SSimpleConfigPresetListPacket(modId, names).sendTo(sender);
		}
		
		@Override public void write(PacketBuffer buf) {
			buf.writeString(modId);
		}
		
		@Override public void read(PacketBuffer buf) {
			modId = buf.readString(32767);
		}
	}
	
	protected static class SSimpleConfigPresetListPacket extends SAbstractPacket {
		protected String modId;
		protected List<Preset> presets;
		
		public SSimpleConfigPresetListPacket() {}
		public SSimpleConfigPresetListPacket(
		  String modId, List<Preset> presets
		) {
			this.modId = modId;
			this.presets = presets;
		}
		
		@Override public void onClient(Context ctx) {
			final CompletableFuture<List<Preset>> future = CSimpleConfigRequestPresetListPacket.FUTURES.remove(modId);
			if (future != null) future.complete(presets);
		}
		
		@Override public void write(PacketBuffer buf) {
			buf.writeString(modId);
			buf.writeVarInt(presets.size());
			for (Preset preset : presets) {
				buf.writeString(preset.getName());
				buf.writeBoolean(preset.isServer());
			}
		}
		
		@Override public void read(PacketBuffer buf) {
			modId = buf.readString(32767);
			presets = new ArrayList<>();
			for (int i = buf.readVarInt(); i > 0; i--)
				presets.add(Preset.remote(buf.readString(32767), buf.readBoolean()));
		}
	}
	
	protected static class CSimpleConfigRequestPresetPacket extends CAbstractPacket {
		protected static final Map<Triple<String, Boolean, String>, CompletableFuture<CommentedConfig>> FUTURES = new HashMap<>();
		protected String modId;
		protected boolean server;
		protected String presetName;
		
		public CSimpleConfigRequestPresetPacket() {}
		
		public CSimpleConfigRequestPresetPacket(String modId, boolean server, String presetName) {
			this.modId = modId;
			this.server = server;
			this.presetName = presetName;
		}
		
		@Override public void onServer(Context ctx) {
			final ServerPlayerEntity sender = ctx.getSender();
			if (sender == null) return;
			Path dir = SimpleConfigPaths.getRemotePresetsDir();
			String type = server? "server" : "client";
			String fileName = modId + "-" + type + "-" + presetName + ".yaml";
			File file = dir.resolve(fileName).toFile();
			if (!file.isFile())
				new SSimpleConfigPresetPacket(
				  modId, server, presetName, null, "File does not exist"
				).sendTo(sender);
			try {
				new SSimpleConfigPresetPacket(
				  modId, server, presetName, FileUtils.readFileToByteArray(file), null
				).sendTo(sender);
			} catch (IOException e) {
				new SSimpleConfigPresetPacket(modId, server, presetName, null, e.getMessage()).sendTo(sender);
			}
		}
		
		@Override public void write(PacketBuffer buf) {
			buf.writeString(modId);
			buf.writeBoolean(server);
			buf.writeString(presetName);
		}
		
		@Override public void read(PacketBuffer buf) {
			modId = buf.readString(32767);
			server = buf.readBoolean();
			presetName = buf.readString(32767);
		}
	}
	
	protected static class SSimpleConfigPresetPacket extends SAbstractPacket {
		protected String modId;
		protected boolean server;
		protected String presetName;
		protected byte @Nullable[] fileData;
		protected @Nullable String errorMsg;
		
		public SSimpleConfigPresetPacket() {}
		public SSimpleConfigPresetPacket(
		  String modId, boolean server, String presetName, byte @Nullable[] fileData,
		  @Nullable String errorMsg
		) {
			this.modId = modId;
			this.server = server;
			this.presetName = presetName;
			this.fileData = fileData;
			this.errorMsg = errorMsg;
		}
		
		@Override public void onClient(Context ctx) {
			final CompletableFuture<CommentedConfig> future =
			  CSimpleConfigRequestPresetPacket.FUTURES.remove(
				 Triple.of(modId, server, presetName));
			if (future != null) {
				if (fileData != null) {
					SimpleConfig config = SimpleConfig.getConfig(modId, Type.SERVER);
					SimpleConfigCommentedYamlFormat format = config.getModConfig().getConfigFormat();
					try {
						CommentedConfig snapshot = format.createParser(false)
						  .parse(new ByteArrayInputStream(fileData));
						future.complete(snapshot);
						return;
					} catch (ParsingException parseException) {
						errorMsg = "Error parsing server snapshot:\n" + parseException.getMessage();
					}
				}
				future.completeExceptionally(
				  errorMsg != null? new RemoteException(errorMsg) : new RemoteException());
			}
		}
		
		@Override public void write(PacketBuffer buf) {
			buf.writeString(modId);
			buf.writeBoolean(server);
			buf.writeString(presetName);
			buf.writeBoolean(fileData != null);
			if (fileData != null) buf.writeByteArray(fileData);
			buf.writeBoolean(errorMsg != null);
			if (errorMsg != null) buf.writeString(errorMsg);
		}
		
		@Override public void read(PacketBuffer buf) {
			modId = buf.readString(32767);
			server = buf.readBoolean();
			presetName = buf.readString(32767);
			fileData = buf.readBoolean()? buf.readByteArray() : null;
			errorMsg = buf.readBoolean()? buf.readString(32767) : null;
		}
	}
	
	@Internal protected static CompletableFuture<List<Preset>> requestPresetList(String modId) {
		if (!isConnectedToSimpleConfigServer())
			return CompletableFuture.completedFuture(Collections.emptyList());
		final CompletableFuture<List<Preset>> future = new CompletableFuture<>();
		final CompletableFuture<List<Preset>> prev = CSimpleConfigRequestPresetListPacket.FUTURES.get(modId);
		if (prev != null) prev.cancel(false);
		CSimpleConfigRequestPresetListPacket.FUTURES.put(modId, future);
		new CSimpleConfigRequestPresetListPacket(modId).send();
		return future;
	}
	
	@Internal protected static CompletableFuture<CommentedConfig> requestRemotePreset(
	  String modId, boolean server, String snapshotName
	) {
		if (!isConnectedToSimpleConfigServer())
			return failedFuture(new IllegalStateException("Not connected to SimpleConfig server"));
		final CompletableFuture<CommentedConfig> future = new CompletableFuture<>();
		final Triple<String, Boolean, String> key = Triple.of(modId, server, snapshotName);
		final CompletableFuture<CommentedConfig> prev = CSimpleConfigRequestPresetPacket.FUTURES.get(key);
		if (prev != null) prev.cancel(false);
		CSimpleConfigRequestPresetPacket.FUTURES.put(key, future);
		new CSimpleConfigRequestPresetPacket(modId, server, snapshotName).send();
		return future;
	}
	
	// null config implies deletion
	@Internal protected static CompletableFuture<Void> saveRemotePreset(
	  String modId, boolean server, String snapshotName, CommentedConfig config
	) {
		if (!isConnectedToSimpleConfigServer())
			return failedFuture(new IllegalStateException("Not connected to SimpleConfig server"));
		final CompletableFuture<Void> future = new CompletableFuture<>();
		CSimpleConfigSavePresetPacket.FUTURES.put(Triple.of(modId, server, snapshotName), future);
		try {
			new CSimpleConfigSavePresetPacket(modId, server, snapshotName, config).send();
		} catch (SimpleConfigSyncException e) {
			future.completeExceptionally(e);
		}
		return future;
	}
	
	protected static class CSimpleConfigRequestSavedHotKeyGroupsPacket extends CAbstractPacket {
		protected static CompletableFuture<List<RemoteSavedHotKeyGroup>> future = null;
		public CSimpleConfigRequestSavedHotKeyGroupsPacket() {}
		public CSimpleConfigRequestSavedHotKeyGroupsPacket(CompletableFuture<List<RemoteSavedHotKeyGroup>> future) {
			CSimpleConfigRequestSavedHotKeyGroupsPacket.future = future;
		}
		
		@Override public void onServer(Context ctx) {
			final ServerPlayerEntity sender = ctx.getSender();
			if (sender == null) return;
			final File dir = SimpleConfigPaths.getRemoteHotKeyGroupsDir().toFile();
			if (!dir.isDirectory()) return;
			final Pattern pat = Pattern.compile("^(?<name>.*)\\.yaml$");
			final File[] files = dir.listFiles((d, name) -> pat.matcher(name).matches());
			if (files == null) return;
			final List<RemoteSavedHotKeyGroup> groups = Arrays.stream(files).map(f -> {
				Matcher m = pat.matcher(f.getName());
				if (!m.matches()) return null;
				return SavedHotKeyGroup.remote(m.group("name"));
			}).filter(Objects::nonNull).collect(Collectors.toList());
			new SSimpleConfigSavedHotKeyGroupsPacket(groups).sendTo(sender);
		}
		
		@Override public void write(PacketBuffer buf) {}
		@Override public void read(PacketBuffer buf) {}
	}
	
	protected static class SSimpleConfigSavedHotKeyGroupsPacket extends SAbstractPacket {
		private List<RemoteSavedHotKeyGroup> groups;
		public SSimpleConfigSavedHotKeyGroupsPacket() {}
		public SSimpleConfigSavedHotKeyGroupsPacket(List<RemoteSavedHotKeyGroup> groups) {
			this.groups = groups;
		}
		
		@Override public void onClient(Context ctx) {
			CompletableFuture<List<RemoteSavedHotKeyGroup>> future =
			  CSimpleConfigRequestSavedHotKeyGroupsPacket.future;
			if (future != null && !future.isDone()) future.complete(groups);
			future = null;
		}
		
		@Override public void write(PacketBuffer buf) {
			buf.writeVarInt(groups.size());
			for (RemoteSavedHotKeyGroup group : groups)
				buf.writeString(group.getName());
		}
		
		@Override public void read(PacketBuffer buf) {
			List<RemoteSavedHotKeyGroup> groups = new ArrayList<>();
			for (int i = buf.readVarInt(); i > 0; i--)
				groups.add(SavedHotKeyGroup.remote(buf.readString(32767)));
			this.groups = groups;
		}
	}
	
	protected static class CRequestSavedHotKeyGroupPacket extends CAbstractPacket {
		private static final Map<String, CompletableFuture<byte[]>> FUTURES = new HashMap<>();
		private String name;
		public CRequestSavedHotKeyGroupPacket() {}
		public CRequestSavedHotKeyGroupPacket(String name, CompletableFuture<byte[]> future) {
			this.name = name;
			FUTURES.put(name, future);
		}
		
		@Override public void onServer(Context ctx) {
			ServerPlayerEntity sender = ctx.getSender();
			if (sender == null) return;
			final String fileName = name + ".yaml";
			final File file = SimpleConfigPaths.getRemoteHotKeyGroupsDir().resolve(fileName).toFile();
			if (!file.isFile())
				new SSavedHotKeyGroupPacket(name, "Cannot find hotkey group " + name).sendTo(sender);
			try {
				byte[] bytes = FileUtils.readFileToByteArray(file);
				new SSavedHotKeyGroupPacket(name, bytes).sendTo(sender);
			} catch (IOException e) {
				new SSavedHotKeyGroupPacket(name, e.getLocalizedMessage()).sendTo(sender);
			}
		}
		
		@Override public void write(PacketBuffer buf) {
			buf.writeString(name);
		}
		
		@Override public void read(PacketBuffer buf) {
			name = buf.readString(32767);
		}
	}
	
	protected static class SSavedHotKeyGroupPacket extends SAbstractPacket {
		private String name;
		private byte @Nullable[] data;
		private @Nullable String errorMsg;
		
		public SSavedHotKeyGroupPacket() {}
		private SSavedHotKeyGroupPacket(String name, byte @Nullable[] data, @Nullable String errorMsg) {
			this.name = name;
			this.data = data;
			this.errorMsg = errorMsg;
		}
		public SSavedHotKeyGroupPacket(String name, byte @NotNull[] data) {
			this(name, data, null);
		}
		public SSavedHotKeyGroupPacket(String name, @NotNull String errorMsg) {
			this(name, null, errorMsg);
		}
		
		@Override public void onClient(Context ctx) {
			CompletableFuture<byte[]> future = CRequestSavedHotKeyGroupPacket.FUTURES.get(name);
			if (future != null && !future.isDone()) {
				if (data == null) {
					future.completeExceptionally(new RemoteException(errorMsg != null? errorMsg : ""));
				} else future.complete(data);
			}
		}
		
		@Override public void write(PacketBuffer buf) {
			buf.writeString(name);
			buf.writeBoolean(data != null);
			if (data != null) buf.writeByteArray(data);
			buf.writeBoolean(errorMsg != null);
			if (errorMsg != null) buf.writeString(errorMsg);
		}
		
		@Override public void read(PacketBuffer buf) {
			name = buf.readString(32767);
			data = buf.readBoolean()? buf.readByteArray() : null;
			errorMsg = buf.readBoolean()? buf.readString(32767) : null;
		}
	}
	
	protected static class CSaveRemoteHotKeyGroupPacket extends CAbstractPacket {
		private static final Map<String, CompletableFuture<Boolean>> FUTURES = new HashMap<>();
		private String name;
		private byte @Nullable[] data;
		
		public CSaveRemoteHotKeyGroupPacket() {}
		public CSaveRemoteHotKeyGroupPacket(String name, byte @Nullable[] data, CompletableFuture<Boolean> future) {
			this.name = name;
			this.data = data;
			FUTURES.put(name, future);
		}
		
		@Override public void onServer(Context ctx) {
			ServerPlayerEntity sender = ctx.getSender();
			if (sender == null) return;
			if (!permissions.canEditServerHotKeys(sender)) {
				LOGGER.warn(
				  "Attempt to " + (data != null? "write" : "delete") + " server saved hotkey group " +
				  "\"" + name + "\" by player " + sender.getScoreboardName() + " denied");
				new SSaveRemoteHotKeyGroupPacket(name, "No permission to write server hotkeys").sendTo(sender);
			}
			LOGGER.info(
			  (data != null? "Writing" : "Deleting") + " server saved hotkey group " +
			  "\"" + name + "\" by player " + sender.getScoreboardName());
			final String fileName = name + ".yaml";
			final File file = SimpleConfigPaths.getRemoteHotKeyGroupsDir().resolve(fileName).toFile();
			if (data == null) {
				if (!file.delete()) {
					LOGGER.warn("Failed to delete server saved hotkey group \"" + name + "\"");
					new SSaveRemoteHotKeyGroupPacket(name, "Cannot delete file " + fileName).sendTo(sender);
				}
				LOGGER.info("Successfully deleted server saved hotkey group \"" + name + "\"");
				new SSaveRemoteHotKeyGroupPacket(name).sendTo(sender);
			} else {
				try {
					FileUtils.writeByteArrayToFile(file, data);
					LOGGER.info("Successfully saved server saved hotkey group \"" + name + "\"");
					new SSaveRemoteHotKeyGroupPacket(name).sendTo(sender);
				} catch (IOException e) {
					LOGGER.warn("Error saving server saved hotkey group \"" + name + "\"", e);
					new SSaveRemoteHotKeyGroupPacket(name, e.getLocalizedMessage()).sendTo(sender);
				}
			}
		}
		
		@Override public void write(PacketBuffer buf) {
			buf.writeString(name);
			buf.writeBoolean(data != null);
			if (data != null) buf.writeByteArray(data);
		}
		
		@Override public void read(PacketBuffer buf) {
			name = buf.readString(32767);
			data = buf.readBoolean()? buf.readByteArray() : null;
		}
	}
	
	protected static class SSaveRemoteHotKeyGroupPacket extends SAbstractPacket {
		private String name;
		private @Nullable String errorMsg;
		
		public SSaveRemoteHotKeyGroupPacket() {}
		public SSaveRemoteHotKeyGroupPacket(String name, @Nullable String errorMsg) {
			this.name = name;
			this.errorMsg = errorMsg;
		}
		public SSaveRemoteHotKeyGroupPacket(String name) {
			this(name, null);
		}
		
		@Override public void onClient(Context ctx) {
			CompletableFuture<Boolean> future = CSaveRemoteHotKeyGroupPacket.FUTURES.get(name);
			if (future != null && !future.isDone()) {
				if (errorMsg != null) {
					future.completeExceptionally(new RemoteException(errorMsg));
				} else future.complete(true);
			}
		}
		
		@Override public void write(PacketBuffer buf) {
			buf.writeString(name);
			buf.writeBoolean(errorMsg != null);
			if (errorMsg != null) buf.writeString(errorMsg);
		}
		
		@Override public void read(PacketBuffer buf) {
			name = buf.readString(32767);
			errorMsg = buf.readBoolean()? buf.readString(32767) : null;
		}
	}
	
	@Internal public static CompletableFuture<List<RemoteSavedHotKeyGroup>> getRemoteSavedHotKeyGroups() {
		if (!isConnectedToSimpleConfigServer())
			return CompletableFuture.completedFuture(Collections.emptyList());
		CompletableFuture<List<RemoteSavedHotKeyGroup>> future = new CompletableFuture<>();
		new CSimpleConfigRequestSavedHotKeyGroupsPacket(future).send();
		return future;
	}
	
	@Internal public static CompletableFuture<byte[]> getRemoteSavedHotKeyGroup(String name) {
		if (!isConnectedToSimpleConfigServer())
			return failedFuture(new IllegalStateException("Not connected to SimpleConfig server"));
		CompletableFuture<byte[]> future = new CompletableFuture<>();
		new CRequestSavedHotKeyGroupPacket(name, future).send();
		return future;
	}
	
	// null data implies delete
	@Internal public static CompletableFuture<Boolean> saveRemoteHotKeyGroup(String name, byte @Nullable[] data) {
		if (!isConnectedToSimpleConfigServer())
			return failedFuture(new IllegalStateException("Not connected to SimpleConfig server"));
		CompletableFuture<Boolean> future = new CompletableFuture<>();
		new CSaveRemoteHotKeyGroupPacket(name, data, future).send();
		return future;
	}
	
	public static class SimpleConfigSyncException extends RuntimeException {
		public SimpleConfigSyncException(String message) {
			super(message);
		}
		public SimpleConfigSyncException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
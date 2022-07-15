package endorh.simpleconfig.core;

import com.electronwill.nightconfig.core.CommentedConfig;
import endorh.simpleconfig.SimpleConfigMod;
import endorh.simpleconfig.SimpleConfigMod.ConfigPermission;
import endorh.simpleconfig.ui.api.ConfigBuilder.IConfigSnapshotHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.config.ModConfig.Type;
import org.jetbrains.annotations.Nullable;

import javax.naming.NoPermissionException;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

class SimpleConfigSnapshotHandler implements IConfigSnapshotHandler {
	private final String modId;
	private final Map<Type, SimpleConfig> configMap;
	private IExternalChangeHandler externalChangeHandler;
	
	public SimpleConfigSnapshotHandler(
	  Map<Type, SimpleConfig> configMap
	) {
		this.configMap = configMap;
		modId = configMap.values().stream().findFirst()
		  .map(SimpleConfig::getModId).orElse("");
	}
	
	@Override public CommentedConfig preserve(Type type, @Nullable Set<String> selectedPaths) {
		final SimpleConfig c = configMap.get(type);
		if (c == null) throw new IllegalArgumentException("Unsupported config type: " + type);
		return c.takeSnapshot(true, selectedPaths);
	}
	
	@Override public void restore(
	  CommentedConfig config, Type type, @Nullable Set<String> selectedPaths
	) {
		SimpleConfig c = configMap.get(type);
		if (c == null) return;
		c.loadSnapshot(config, true, selectedPaths);
	}
	
	@OnlyIn(Dist.CLIENT)
	@Override public boolean canSaveRemote() {
		final Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null || mc.player == null) return false;
		return SimpleConfigMod.ServerConfig.permissions.permissionFor(mc.player, modId) ==
		       ConfigPermission.ALLOW;
	}
	
	@Override public CommentedConfig getLocal(String name, Type type) {
		final SimpleConfig c = configMap.get(type);
		if (c == null)
			throw new IllegalArgumentException("Missing config type");
		final CompletableFuture<CommentedConfig> future = c.getLocalPreset(name);
		if (!future.isDone())
			throw new IllegalStateException("Uncompleted future");
		return future.getNow(null);
	}
	
	@Override public CompletableFuture<CommentedConfig> getRemote(String name, Type type) {
		final SimpleConfig c = configMap.get(type);
		return c != null? c.getRemotePreset(name) :
		       failedFuture(new IllegalArgumentException("Missing config type"));
	}
	
	@Override public Optional<Throwable> saveLocal(
	  String name, Type type, CommentedConfig config
	) {
		final SimpleConfig c = configMap.get(type);
		if (c != null) {
			return getException(c.saveLocalPreset(name, config));
		} else return Optional.empty();
	}
	
	@Override public CompletableFuture<Void> saveRemote(
	  String name, Type type, CommentedConfig config
	) {
		if (!canSaveRemote())
			return failedFuture(new NoPermissionException("Cannot save remote preset"));
		final SimpleConfig c = configMap.get(type);
		return c != null? c.saveRemotePreset(name, config) :
		       failedFuture(new IllegalArgumentException("Missing config type"));
	}
	
	@Override public Optional<Throwable> deleteLocal(
	  String name, Type type
	) {
		return saveLocal(name, type, null);
	}
	
	@Override public CompletableFuture<Void> deleteRemote(
	  String name, Type type
	) {
		return saveRemote(name, type, null);
	}
	
	@Override public List<String> getLocalSnapshotNames() {
		final SimpleConfig c = configMap.get(Type.CLIENT);
		if (c == null) return emptyList();
		final Optional<Path> opt = c.getFilePath();
		if (!opt.isPresent()) return emptyList();
		final File dir = opt.get().getParent().toFile();
		Pattern pattern = Pattern.compile(
		  "^(?<file>" + c.getModId() + "-preset-(?<preset>.+))\\.yaml$");
		final File[] files =
		  dir.listFiles((d, name) -> pattern.matcher(name).matches());
		return files == null? emptyList() :
		       Arrays.stream(files).map(
		         f -> {
			         Matcher m = pattern.matcher(f.getName());
						if (!m.matches()) return null;
						return m.group("preset");
		         }
		       ).filter(Objects::nonNull).collect(Collectors.toList());
	}
	
	@Override public CompletableFuture<List<String>> getRemoteSnapshotNames() {
		final SimpleConfig c = configMap.get(Type.SERVER);
		if (c == null) return CompletableFuture.completedFuture(emptyList());
		return SimpleConfigSync.requestSnapshotList(c.getModId());
	}
	
	@Override public void setExternalChangeHandler(IExternalChangeHandler handler) {
		externalChangeHandler = handler;
	}
	
	public void notifyExternalChanges(SimpleConfig config) {
		if (externalChangeHandler != null) {
			if (configMap.containsValue(config)) {
				config.loadGUIExternalChanges();
				externalChangeHandler.handleExternalChange(config.getType());
			}
		}
	}
	
	protected static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
		final CompletableFuture<T> future = new CompletableFuture<>();
		future.completeExceptionally(throwable);
		return future;
	}
	protected static Optional<Throwable> getException(CompletableFuture<?> future) {
		if (future.isCompletedExceptionally()) {
			try {
				future.getNow(null);
				return Optional.empty();
			} catch (CompletionException e) {
				return Optional.of(e.getCause());
			}
		}
		return Optional.empty();
	}
}

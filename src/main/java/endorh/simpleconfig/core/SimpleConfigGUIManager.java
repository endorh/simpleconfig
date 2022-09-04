package endorh.simpleconfig.core;

import endorh.simpleconfig.SimpleConfigMod;
import endorh.simpleconfig.api.SimpleConfig;
import endorh.simpleconfig.api.SimpleConfig.Type;
import endorh.simpleconfig.api.SimpleConfigTextUtil;
import endorh.simpleconfig.config.ClientConfig.menu;
import endorh.simpleconfig.config.CommonConfig;
import endorh.simpleconfig.config.ServerConfig;
import endorh.simpleconfig.core.SimpleConfigNetworkHandler.CSimpleConfigReleaseServerCommonConfigPacket;
import endorh.simpleconfig.ui.api.ConfigScreenBuilder;
import endorh.simpleconfig.ui.api.ConfigScreenBuilder.IConfigScreenGUIState;
import endorh.simpleconfig.ui.api.IDialogCapableScreen;
import endorh.simpleconfig.ui.gui.AbstractConfigScreen;
import endorh.simpleconfig.ui.gui.DialogScreen;
import endorh.simpleconfig.ui.gui.InfoDialog;
import endorh.simpleconfig.ui.hotkey.ConfigHotKey;
import endorh.simpleconfig.ui.hotkey.HotKeyListDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.IngameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.button.ImageButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.client.gui.screen.ModListScreen;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static java.util.Collections.synchronizedMap;

/**
 * Handle the creation of config GUIs for the registered mods<br>
 * Mod configs are automatically registered upon building.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(value = Dist.CLIENT, modid = SimpleConfigMod.MOD_ID)
public class SimpleConfigGUIManager {
	// Mod loading is asynchronous
	protected static final Map<String, Map<Type, SimpleConfigImpl>> modConfigs = synchronizedMap(new HashMap<>());
	private static final Map<String, AbstractConfigScreen> activeScreens = new HashMap<>();
	
	protected static boolean addButton = false;
	protected static boolean autoAddedButton = false;
	protected static Comparator<SimpleConfigImpl> typeOrder = Comparator.comparing(SimpleConfigImpl::getType);
	protected static ResourceLocation defaultBackground = new ResourceLocation("textures/block/oak_planks.png");
	
	// Used to evict unbound `ExtendedKeyBind`s for overlap checks to work properly
	private static int guiSession;
	private static final Map<String, Integer> guiSessions = new HashMap<>();
	private static final Map<String, IConfigScreenGUIState> guiStates = new HashMap<>();
	
	@Internal public static int getGuiSession() {
		return guiSession;
	}
	
	/**
	 * Modify the behaviour that adds a side button to the pause menu
	 * to open the mod list screen<br>
	 * This is exposed so mods which add their own buttons to the pause
	 * menu and have already this mod as a dependency can toggle this
	 * off to avoid interferences<br>
	 * If your mod doesn't mess with the pause menu, you should not
	 * call this method
	 */
	@SuppressWarnings("unused") public void setAddButton(boolean add) {
		addButton = add;
		autoAddedButton = true;
	}
	
	/**
	 * Register a config in the GUI system
	 */
	protected static void registerConfig(SimpleConfigImpl config) {
		String modId = config.getModId();
		ModContainer container = config.getModContainer();
		Optional<BiFunction<Minecraft, Screen, Screen>> ext = container.getCustomExtension(ExtensionPoint.CONFIGGUIFACTORY);
		if (config.isWrapper() && (
		  !CommonConfig.menu.shouldWrapConfig(modId)
		  || ext.isPresent()
		     && !(ext.get() instanceof ConfigGUIFactory)
		     && !CommonConfig.menu.shouldReplaceMenu(modId)
		)) return;
		if (!autoAddedButton)
			autoAddedButton = addButton = true;
		if (!modConfigs.containsKey(modId)) {
			modConfigs.computeIfAbsent(modId, s -> synchronizedMap(new HashMap<>())).put(
			  config.getType(), config);
			container.registerExtensionPoint(
			  ExtensionPoint.CONFIGGUIFACTORY, () -> new ConfigGUIFactory(modId));
		} else modConfigs.get(modId).put(config.getType(), config);
	}
	
	/**
	 * Used for marking instead of an anonymous lambda.
	 */
	private static class ConfigGUIFactory implements BiFunction<Minecraft, Screen, Screen> {
		private final String modId;
		private ConfigGUIFactory(String modId) {
			this.modId = modId;
		}
		@Override public Screen apply(Minecraft minecraft, Screen screen) {
			Screen gui = getConfigGUI(modId, screen);
			return gui != null? gui : screen;
		}
		public String getModId() {
			return modId;
		}
	}
	
	public static Screen getNoServerDialogScreen(Screen parent) {
		return new DialogScreen(parent, InfoDialog.create(
		  new TranslationTextComponent("simpleconfig.error.no_server.dialog.title"),
		  SimpleConfigTextUtil.splitTtc("simpleconfig.error.no_server.dialog.body"), d -> {
			  d.setConfirmText(new TranslationTextComponent("gui.ok"));
		  }
		));
	}
	
	public static Screen getConfigGUIForHotKey(
	  String modId, IDialogCapableScreen parent, HotKeyListDialog hotKeyDialog, ConfigHotKey hotkey
	) {
		AbstractConfigScreen screen = activeScreens.get(modId);
		Screen parentScreen = (Screen) parent;
		if (screen != null) {
			int prevSession = guiSession;
			guiSession = guiSessions.get(modId);
			screen.setEditedConfigHotKey(hotkey, r -> {
				screen.setEditedConfigHotKey(null, null);
				Minecraft.getInstance().displayGuiScreen(parentScreen);
				if (hotKeyDialog != null) parent.addDialog(hotKeyDialog);
				guiSession = prevSession;
			});
			return screen;
		}
		Map<Type, SimpleConfigImpl> configs = modConfigs.get(modId);
		if (configs == null || configs.isEmpty())
			throw new IllegalArgumentException(
			  "No Simple Config GUI registered for mod id: \"" + modId + "\"");
		final Minecraft mc = Minecraft.getInstance();
		boolean hasPermission =
		  mc.player != null && ServerConfig.permissions.permissionFor(mc.player, modId).getLeft().canView();
		final List<SimpleConfigImpl> orderedConfigs = configs.values().stream()
		  .filter(c -> c.getType() != Type.SERVER || hasPermission)
		  .sorted(typeOrder)
		  .collect(Collectors.toList());
		if (orderedConfigs.isEmpty()) return getNoServerDialogScreen(parentScreen);
		final ConfigScreenBuilder builder = ConfigScreenBuilder.create(modId)
		  .setParentScreen(parentScreen)
		  .setSavingRunnable(() -> {})
		  .setTitle(new TranslationTextComponent(
		    "simpleconfig.config.title", SimpleConfigImpl.getModNameOrId(modId)))
		  .setDefaultBackgroundTexture(defaultBackground)
		  .setPreviousGUIState(guiStates.get(modId))
		  .setEditedConfigHotKey(hotkey, r -> {
			  AbstractConfigScreen removed = activeScreens.remove(modId);
			  guiStates.put(modId, removed.saveConfigScreenGUIState());
			  guiSessions.remove(modId);
			  if (configs.containsKey(SimpleConfig.Type.COMMON)
			      && !Minecraft.getInstance().isIntegratedServerRunning()
			      && hasPermission
			  ) new CSimpleConfigReleaseServerCommonConfigPacket(modId).send();
			  for (SimpleConfigImpl c: orderedConfigs) c.removeGUI();
			  Minecraft.getInstance().displayGuiScreen(parentScreen);
			  if (hotKeyDialog != null) parent.addDialog(hotKeyDialog);
		  }); //.setClosingRunnable(() -> activeScreens.remove(modId));
		for (SimpleConfigImpl config : orderedConfigs) config.buildGUI(builder, false);
		guiSessions.put(modId, ++guiSession);
		final AbstractConfigScreen gui = builder.build();
		activeScreens.put(modId, gui);
		for (SimpleConfigImpl config : orderedConfigs) config.setGUI(gui, null);
		return gui;
	}
	
	/**
	 * Build a config gui for the specified mod id
	 * @param parent Parent screen to return to
	 */
	public static @Nullable Screen getConfigGUI(String modId, Screen parent) {
		Map<Type, SimpleConfigImpl> configs = modConfigs.get(modId);
		if (configs == null || configs.isEmpty())
			throw new IllegalArgumentException(
			  "No Simple Config GUI registered for mod id: \"" + modId + "\"");
		final Minecraft mc = Minecraft.getInstance();
		boolean hasPermission =
		  mc.player != null && ServerConfig.permissions.permissionFor(mc.player, modId).getLeft().canView();
		final List<SimpleConfigImpl> orderedConfigs = configs.values().stream()
		  .filter(c -> c.getType() != Type.SERVER || hasPermission)
		  .sorted(typeOrder)
		  .collect(Collectors.toList());
		if (orderedConfigs.isEmpty()) return getNoServerDialogScreen(parent);
		final SimpleConfigSnapshotHandler handler = new SimpleConfigSnapshotHandler(configs);
		final ConfigScreenBuilder builder = ConfigScreenBuilder.create(modId)
		  .setParentScreen(parent)
		  .setSavingRunnable(() -> {
			  for (SimpleConfigImpl c: orderedConfigs)
				  if (c.isDirty()) c.save();
		  }).setPreviousGUIState(guiStates.get(modId))
		  .setClosingRunnable(() -> {
			  AbstractConfigScreen removed = activeScreens.remove(modId);
			  guiStates.put(modId, removed.saveConfigScreenGUIState());
			  if (configs.containsKey(SimpleConfig.Type.COMMON)
			      && !Minecraft.getInstance().isIntegratedServerRunning()
			      && hasPermission
			  ) new CSimpleConfigReleaseServerCommonConfigPacket(modId).send();
			  for (SimpleConfigImpl c: orderedConfigs) c.removeGUI();
		  }).setTitle(new TranslationTextComponent(
			 "simpleconfig.config.title", SimpleConfigImpl.getModNameOrId(modId)))
		  .setDefaultBackgroundTexture(defaultBackground)
		  .setSnapshotHandler(handler)
		  .setRemoteCommonConfigProvider(handler);
		for (SimpleConfigImpl config : orderedConfigs) {
			config.buildGUI(builder, false);
			if (config.getType() == SimpleConfig.Type.COMMON
			    && !Minecraft.getInstance().isIntegratedServerRunning()
			    && hasPermission
			) config.buildGUI(builder, true);
		}
		guiSessions.put(modId, ++guiSession);
		final AbstractConfigScreen gui = builder.build();
		activeScreens.put(modId, gui);
		for (SimpleConfigImpl config : orderedConfigs) config.setGUI(gui, handler);
		return gui;
	}
	
	/**
	 * Build a config GUI for the specified mod id, using the current screen as parent
	 */
	public static Screen getConfigGUI(String modId) {
		return getConfigGUI(modId, Minecraft.getInstance().currentScreen);
	}
	
	/**
	 * Show the config GUI for the specified mod id, using the current screen as parent
	 */
	@SuppressWarnings("unused")
	public static void showConfigGUI(String modId) {
		Screen screen = getConfigGUI(modId);
		if (screen != null) Minecraft.getInstance().displayGuiScreen(screen);
	}
	
	public static void showConfigGUIForHotKey(
	  String modId, IDialogCapableScreen parent, HotKeyListDialog hotKeyDialog, ConfigHotKey hotKey
	) {
		Minecraft.getInstance().displayGuiScreen(
		  getConfigGUIForHotKey(modId, parent, hotKeyDialog, hotKey));
	}
	
	/**
	 * Show the Forge mod list GUI
	 */
	public static void showModListGUI() {
		final Minecraft mc = Minecraft.getInstance();
		mc.displayGuiScreen(new ModListScreen(mc.currentScreen));
	}
	
	/**
	 * Show the Config Hotkey GUI
	 */
	public static void showConfigHotkeysGUI() {
		Minecraft mc = Minecraft.getInstance();
		mc.displayGuiScreen(new DialogScreen(mc.currentScreen, new HotKeyListDialog(null)));
	}
	
	/**
	 * Adds a minimal button to the pause menu to open the mod list ingame.<br>
	 * This behaviour can be disabled in the config, in case it interferes with
	 * another mod
	 */
	@SubscribeEvent
	public static void onGuiInit(InitGuiEvent.Post event) {
		if (!addButton || !menu.add_pause_menu_button)
			return;
		final Screen gui = event.getGui();
		if (gui instanceof IngameMenuScreen) {
			// Coordinates taken from IngameMenuScreen#addButtons
			int w = 20, h = 20, x, y;
			switch (menu.menu_button_position) {
				case TOP_LEFT_CORNER:
					x = 8; y = 8; break;
				case TOP_RIGHT_CORNER:
					x = gui.width - 28; y = 8; break;
				case BOTTOM_LEFT_CORNER:
					x = 8; y = gui.height - 28; break;
				case BOTTOM_RIGHT_CORNER:
					x = gui.width - 28; y = gui.height - 28; break;
				case SPLIT_OPTIONS_BUTTON:
					Optional<Button> opt = getOptionsButton(gui, event.getWidgetList());
					if (opt.isPresent()) {
						Button options = opt.get();
						options.setWidth(options.getWidth() - 20 - 4);
						// Coordinates taken from IngameMenuScreen#addButtons
						x = gui.width / 2 - 102 + 98 - 20;
						y = gui.height / 4 + 96 - 16;
						break;
					} // else fallthrough
				case LEFT_OF_OPTIONS_BUTTON:
				default:
					// Coordinates taken from IngameMenuScreen#addButtons
					x = gui.width / 2 - 102 - w - 4;
					y = gui.height / 4 + 96 - 16;
			}
			
			Button modOptions = new ImageButton(
			  x, y, w, h, 0, 0, 20,
			  new ResourceLocation(SimpleConfigMod.MOD_ID, "textures/gui/simpleconfig/menu.png"),
			  32, 64, p -> showModListGUI());
			event.addWidget(modOptions);
		}
	}
	
	/**
	 * Try to find the Options button in the game menu<br>
	 * Checks its position and size before returning, so it returns
	 * empty if the button does not match the expected placement<br>
	 */
	public static Optional<Button> getOptionsButton(Screen gui, List<Widget> widgets) {
		final int x = gui.width / 2 - 102, y = gui.height / 4 + 96 - 16;
		for (Widget widget : widgets) {
			if (widget instanceof Button) {
				Button but = (Button) widget;
				if (but.getMessage().getString().equals(I18n.format("menu.options"))) {
					if (but.x == x && but.y == y && but.getWidth() == 98) {
						return Optional.of(but);
					}
				}
			}
		}
		return Optional.empty();
	}
}

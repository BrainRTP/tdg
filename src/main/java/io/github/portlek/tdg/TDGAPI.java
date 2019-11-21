package io.github.portlek.tdg;

import io.github.portlek.itemstack.util.Colored;
import io.github.portlek.mcyaml.IYaml;
import io.github.portlek.mcyaml.YamlOf;
import io.github.portlek.mcyaml.mck.MckFileConfiguration;
import io.github.portlek.tdg.file.Config;
import io.github.portlek.tdg.file.ConfigOptions;
import io.github.portlek.tdg.file.Language;
import io.github.portlek.tdg.file.LanguageOptions;
import io.github.portlek.tdg.icon.BasicIcon;
import io.github.portlek.tdg.icon.ClickAction;
import io.github.portlek.tdg.menu.BasicMenu;
import io.github.portlek.tdg.menu.CloseAction;
import io.github.portlek.tdg.menu.OpenAction;
import io.github.portlek.tdg.mock.MckIcon;
import io.github.portlek.tdg.mock.MckMenu;
import io.github.portlek.tdg.mock.MckOpenMenu;
import io.github.portlek.tdg.types.IconType;
import io.github.portlek.tdg.util.TargetMenu;
import io.github.portlek.tdg.util.Targeted;
import io.github.portlek.tdg.util.UpdateChecker;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.cactoos.iterable.Mapped;
import org.cactoos.list.ListOf;
import org.cactoos.map.MapEntry;
import org.cactoos.map.MapOf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TDGAPI {

    /**
     * menu id and menu
     */
    public final Map<String, Menu> menus = new HashMap<>();

    /**
     * player uuid and opened menu
     */
    public final Map<UUID, OpenedMenu> opened = new HashMap<>();

    public final List<Entity> entities = new ArrayList<>();

    @NotNull
    public final TDG tdg;

    @NotNull
    private final ConfigOptions configOptions;

    @Nullable
    private Config configInstance;

    @Nullable
    private Language languageInstance;

    @Nullable
    private IYaml languageFileInstance;

    @NotNull
    private final IYaml menusFile;

    public TDGAPI(@NotNull TDG tdg) {
        this.tdg = tdg;
        configOptions = new ConfigOptions(
            new YamlOf(tdg, "config")
        );
        menusFile = new YamlOf(tdg, "menus");
    }

    public void reloadPlugin() {
        tdg.getServer().getScheduler().cancelTasks(tdg);
        HandlerList.unregisterAll(tdg);
        entities.forEach(Entity::remove);
        entities.clear();
        menus.clear();
        opened.clear();
        menusFile.create();

        if (menusFile.getSection("menus") instanceof MckFileConfiguration) {
            menusFile.createSection("menus");
        }

        init();
    }

    @NotNull
    public Icon findIconByEntity(@NotNull OpenedMenu openedMenu, @NotNull Entity entity) {
        for (Icon icon : openedMenu.getIcons()) {
            if (icon.is(entity)) {
                return icon;
            }
        }

        return new MckIcon();
    }

    @NotNull
    public Menu findMenuByCommand(@NotNull String command) {
        for (Menu menu : menus.values()) {
            if (menu.getCommands().contains(command)) {
                return menu;
            }
        }

        return new MckMenu();
    }

    @NotNull
    public Menu findMenuById(@NotNull String id) {
        return menus.getOrDefault(id, new MckOpenMenu());
    }

    @NotNull
    public OpenedMenu findOpenMenuByUUID(@NotNull UUID uuid) {
        return opened.getOrDefault(uuid, new MckOpenMenu());
    }

    public boolean hasOpen(@NotNull Player player) {
        return !(opened.getOrDefault(player.getUniqueId(), new MckOpenMenu()) instanceof MckOpenMenu);
    }

    @NotNull
    public Config getConfigs() {
        if (configInstance == null) {
            configInstance = configOptions.value();
        }

        return configInstance;
    }

    @NotNull
    public Language getLanguage() {
        if (languageInstance == null) {
            languageInstance = new LanguageOptions(
                getConfigs().pluginPrefix,
                getLanguageFile()
            ).value();
        }

        return languageInstance;
    }

    @NotNull
    private IYaml getLanguageFile() {
        if (languageFileInstance == null) {
            languageFileInstance = new YamlOf(
                tdg,
                "languages",
                getConfigs().language
            );
            languageFileInstance.create();
        }

        return languageFileInstance;
    }

    private void init() {
        menus.putAll(
            new MapOf<String, Menu>(
                new Mapped<>(
                    menuId -> new MapEntry<>(
                        menuId,
                        new BasicMenu(
                            menuId,
                            menusFile.getStringList("menus." + menuId + ".commands"),
                            CloseAction.parse(menusFile, menuId),
                            OpenAction.parse(menusFile, menuId),
                            menusFile.getInt("menus." + menuId + "distances.x1"),
                            menusFile.getInt("menus." + menuId + "distances.x2"),
                            menusFile.getInt("menus." + menuId + "distances.x4"),
                            menusFile.getInt("menus." + menuId + "distances.x5"),
                            new ListOf<>(
                                new Mapped<>(
                                    iconId -> new BasicIcon(
                                        iconId,
                                        menusFile.getString("menus." + menuId + ".icons." + iconId + ".name").orElse(""),
                                        IconType.fromString(
                                            menusFile.getString("menus." + menuId + ".icons." + iconId + ".icon-type").orElse("")
                                        ),
                                        menusFile.getString("menus." + menuId + ".icons." + iconId + ".material").orElse(""),
                                        menusFile.getByte("menus." + menuId + ".icons." + iconId + ".material-data"),
                                        menusFile.getInt("menus." + menuId + ".icons." + iconId + ".position-x"),
                                        menusFile.getInt("menus." + menuId + ".icons." + iconId + ".position-y"),
                                        ClickAction.parse(menusFile, menuId, iconId)
                                    ),
                                    menusFile.getSection("menus." + menuId + ".icons").getKeys(false)
                                )
                            )
                        )
                    ),
                    menusFile.getSection("menus").getKeys(false)
                )
            )
        );

        new ListenerBasic<>(PlayerJoinEvent.class, event -> {
            final Player player = event.getPlayer();

            if (getConfigs().updateCheck && player.hasPermission("tdg.version")) {
                final UpdateChecker updater = new UpdateChecker(tdg, 0);

                try {
                    if (updater.checkForUpdates()) {
                        player.sendMessage(
                            new Colored(
                                "&8[&cTDG&8] &bA new update of TDG &f&l(" +
                                    updater.getLatestVersion() +
                                    ") &bis available! Download it at &f&l" + updater.getResourceURL()
                            ).value()
                        );
                    }
                } catch (Exception ex) {
                    tdg.getLogger().severe("[TDG] Update checker failed, could not connect to the API!");
                    ex.printStackTrace();
                }
            }

            opened.put(player.getUniqueId(), new MckOpenMenu());
        }).register(tdg);

        new ListenerBasic<>(
            PlayerArmorStandManipulateEvent.class,
            event -> entities.contains(event.getRightClicked()),
            event -> event.setCancelled(true)
        ).register(tdg);

        new ListenerBasic<>(
            EntityDamageEvent.class,
            event -> entities.contains(event.getEntity()),
            event -> event.setCancelled(true)
        ).register(tdg);

        new ListenerBasic<>(PlayerChangedWorldEvent.class, event -> {
            final OpenedMenu openedMenu = opened.getOrDefault(event.getPlayer().getUniqueId(), new MckOpenMenu());

            if (!(openedMenu instanceof MckOpenMenu)) {
                openedMenu.close();
            }
        }).register(tdg);

        new ListenerBasic<>(PlayerCommandPreprocessEvent.class, event -> {
            final Player player = event.getPlayer();
            final String command = event.getMessage();
            final Menu menu = findMenuByCommand(command);

            if (menu instanceof MckMenu) {
                return;
            }

            event.setCancelled(true);

            if (opened.containsKey(player.getUniqueId())) {
                player.sendMessage(getLanguage().errorAlreadyOpen);
                return;
            }

            if (!player.hasPermission("tdg.open." + menu.getId())) {
                player.sendMessage(getLanguage().errorPermission);
                return;
            }

            menu.open(player);
        }).register(tdg);

        new ListenerBasic<>(PlayerInteractEvent.class,
            event -> event.getAction() == Action.LEFT_CLICK_AIR &&
                opened.containsKey(event.getPlayer().getUniqueId()),
            event ->
                getIconOptional(
                    event.getPlayer()
                ).ifPresent(icon -> icon.acceptClick(event.getPlayer()))
        ).register(tdg);

        new ListenerBasic<>(PlayerMoveEvent.class,
            event -> event.getTo() != null &&
                event.getFrom().distance(event.getTo()) != 0 &&
                opened.containsKey(event.getPlayer().getUniqueId()),
            event -> getIconOptional(
                event.getPlayer()
            ).ifPresent(icon -> icon.acceptHover(event.getPlayer()))
        ).register(tdg);
    }

    @NotNull
    private Optional<Icon> getIconOptional(@NotNull Player player) {
        final Map.Entry<Icon, OpenedMenu> entry = findIconAndOpenedMenuByPlayer(player);
        final Icon icon = entry.getKey();
        final OpenedMenu openedMenu = entry.getValue();

        if (icon instanceof MckIcon || openedMenu instanceof MckOpenMenu) {
            return Optional.empty();
        }

        return Optional.of(icon);
    }

    @NotNull
    private Map.Entry<Icon, OpenedMenu> findIconAndOpenedMenuByPlayer(@NotNull Player player) {
        final Entity targeted = new Targeted(player).value();

        if (targeted.equals(player)) {
            return new MapEntry<>(
                new MckIcon(),
                new MckOpenMenu()
            );
        }

        final OpenedMenu menu = new TargetMenu(
            targeted
        ).value();

        if (menu instanceof MckOpenMenu) {
            return new MapEntry<>(
                new MckIcon(),
                new MckOpenMenu()
            );
        }

        final Icon icon = findIconByEntity(menu, targeted);

        if (icon instanceof MckIcon) {
            return new MapEntry<>(
                new MckIcon(),
                new MckOpenMenu()
            );
        }

        return new MapEntry<>(icon, menu);
    }

}

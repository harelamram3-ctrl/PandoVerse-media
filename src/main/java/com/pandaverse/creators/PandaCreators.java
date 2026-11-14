package com.pandaverse.creators;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public final class PandaCreators extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private String discordLink;
    private int broadcastCooldownSeconds;
    private LuckPerms luckPermsApi = null;

    // נתוני בקשות שנשמרים בקובץ applications.yml
    private File appsFile;
    private FileConfiguration appsConfig;

    // מצבי שחקנים בזיכרון
    private final Set<UUID> pendingApplication = new HashSet<>();
    private final Set<UUID> photoModeCreators = new HashSet<>();
    private final Set<UUID> eventModeCreators = new HashSet<>();
    private final Set<UUID> rainWeatherCreators = new HashSet<>();
    private final Set<UUID> hidePlayersMode = new HashSet<>();
    private final Set<UUID> vanishModeCreators = new HashSet<>();
    private final Map<UUID, Location> savedCreatorSpots = new HashMap<>();

    private final Map<UUID, Long> broadcastCooldowns = new HashMap<>();
    private final Set<UUID> waitingForBroadcast = new HashSet<>();
    private final Map<UUID, String> broadcastType = new HashMap<>();

    // מזהי כותרות GUI
    private static final String GUI_MAIN = "&b&lPANDAVERSE - יוצרי תוכן";
    private static final String GUI_PERKS = "&6&lPANDAVERSE - רמות והטבות";
    private static final String GUI_PANEL_PREFIX = "&d&lפאנל יוצרי תוכן";
    private static final String GUI_ADMIN = "&c&lניהול יוצרי תוכן - הנהלה";
    private static final String GUI_ADMIN_APPS = "&e&lבקשות ממתינות לאישור";
    private static final String GUI_ADMIN_PLAYERS = "&b&lניהול רמות שחקנים מחוברים";
    private static final String GUI_ADMIN_EDIT_PREFIX = "&4&lעריכת יוצר: &f";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        setupApplicationsFile();
        hookLuckPerms();

        String[] commands = {"creator", "creatoradmin", "creatorperks", "creatorpanel"};
        for (String cmd : commands) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(this);
                getCommand(cmd).setTabCompleter(this);
            }
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info(ChatColor.GREEN + "PandaCreators v2.0 has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // ניקוי מצבים זמניים בעת כיבוי השרת
        for (UUID uuid : new HashSet<>(vanishModeCreators)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.showPlayer(this, p);
                }
            }
        }
        saveApplicationsFile();
    }

    private void hookLuckPerms() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                luckPermsApi = LuckPermsProvider.get();
                getLogger().info("Successfully hooked into LuckPerms API!");
            } catch (Exception e) {
                getLogger().warning("LuckPerms found, but API is not ready yet. Falling back to commands.");
            }
        } else {
            getLogger().warning("LuckPerms not found! Using standard Bukkit permissions.");
        }
    }

    private void loadConfigValues() {
        reloadConfig();
        discordLink = getConfig().getString("discord-link", "https://discord.gg/pandaverse");
        broadcastCooldownSeconds = getConfig().getInt("broadcast.cooldown-seconds", 300);
    }

    private void setupApplicationsFile() {
        appsFile = new File(getDataFolder(), "applications.yml");
        if (!appsFile.exists()) {
            try {
                appsFile.getParentFile().mkdirs();
                appsFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create applications.yml!");
            }
        }
        appsConfig = YamlConfiguration.loadConfiguration(appsFile);
    }

    private void saveApplicationsFile() {
        try {
            appsConfig.save(appsFile);
        } catch (IOException e) {
            getLogger().severe("Could not save applications.yml!");
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void sendAlert(Player player, String message) {
        String colored = color(message);
        player.sendMessage(colored);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colored));
    }

    private int getCreatorLevel(Player player) {
        if (player.hasPermission("panda.creator.level3") || player.isOp()) return 3;
        if (player.hasPermission("panda.creator.level2")) return 2;
        if (player.hasPermission("panda.creator.level1")) return 1;
        return 0;
    }

    /**
     * מגדיר רמת יוצר דרך LuckPerms API (או פקודת קונסול כגיבוי)
     */
    private void setPlayerCreatorLevel(OfflinePlayer target, int newLevel, CommandSender admin) {
        String group1 = getConfig().getString("luckperms.groups.level1", "creator1");
        String group2 = getConfig().getString("luckperms.groups.level2", "creator2");
        String group3 = getConfig().getString("luckperms.groups.level3", "creator3");
        boolean useLp = getConfig().getBoolean("luckperms.enabled", true);
        boolean alsoPerm = getConfig().getBoolean("luckperms.also-set-permission", true);

        if (useLp && luckPermsApi != null) {
            luckPermsApi.getUserManager().modifyUser(target.getUniqueId(), (User user) -> {
                // הסרת קבוצות והרשאות קודמות של יוצר תוכן
                user.data().remove(InheritanceNode.builder(group1).build());
                user.data().remove(InheritanceNode.builder(group2).build());
                user.data().remove(InheritanceNode.builder(group3).build());
                user.data().remove(Node.builder("panda.creator.level1").build());
                user.data().remove(Node.builder("panda.creator.level2").build());
                user.data().remove(Node.builder("panda.creator.level3").build());

                if (newLevel >= 1) {
                    String targetGroup = (newLevel == 3) ? group3 : (newLevel == 2 ? group2 : group1);
                    user.data().add(InheritanceNode.builder(targetGroup).build());
                    if (alsoPerm) {
                        user.data().add(Node.builder("panda.creator.level" + newLevel).build());
                    }
                }
            });
        } else {
            // גיבוי דרך פקודות קונסול אם ה-API לא זמין
            String name = target.getName();
            if (name != null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + name + " parent remove " + group1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + name + " parent remove " + group2);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + name + " parent remove " + group3);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + name + " permission unset panda.creator.level1");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + name + " permission unset panda.creator.level2");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + name + " permission unset panda.creator.level3");

                if (newLevel >= 1) {
                    String targetGroup = (newLevel == 3) ? group3 : (newLevel == 2 ? group2 : group1);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + name + " parent add " + targetGroup);
                    if (alsoPerm) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + name + " permission set panda.creator.level" + newLevel + " true");
                    }
                }
            }
        }

        if (admin != null) {
            if (newLevel == 0) {
                admin.sendMessage(color("&8[&bPandaCreators&8] &cהוסרה דרגת יוצר התוכן מהשחקן &f" + target.getName() + "&c."));
            } else {
                admin.sendMessage(color("&8[&bPandaCreators&8] &aהשחקן &f" + target.getName() + " &aעודכן ליוצר תוכן &eרמה " + newLevel + "&a!"));
            }
        }

        if (target.isOnline() && target.getPlayer() != null) {
            Player onlineTarget = target.getPlayer();
            if (newLevel == 0) {
                sendAlert(onlineTarget, "&8[&bPandaCreators&8] &cדרגת יוצר התוכן שלך הוסרה על ידי ההנהלה.");
            } else {
                sendAlert(onlineTarget, "&8[&bPandaCreators&8] &aמזל טוב! קיבלת דרגת יוצר תוכן &eרמה " + newLevel + "&a! השתמש ב-&b/creatorpanel");
                onlineTarget.playSound(onlineTarget.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase();

        // פקודת ניהול תומכת גם בארגומנטים ישירים (גם מהקונסול!)
        if (cmdName.equals("creatoradmin")) {
            if (!sender.hasPermission("panda.admin.creator") && !sender.isOp()) {
                sender.sendMessage(color("&cאין לך הרשאה להשתמש בפקודה זו!"));
                return true;
            }

            if (args.length >= 1) {
                if (args[0].equalsIgnoreCase("reload")) {
                    loadConfigValues();
                    setupApplicationsFile();
                    sender.sendMessage(color("&8[&bPandaCreators&8] &aהקונפיג וקובץ הבקשות נטענו מחדש בהצלחה!"));
                    return true;
                }
                if (args[0].equalsIgnoreCase("setlevel") && args.length >= 3) {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    try {
                        int lvl = Integer.parseInt(args[2]);
                        if (lvl < 0 || lvl > 3) {
                            sender.sendMessage(color("&cהרמה חייבת להיות בין 0 (הסרה) ל-3."));
                            return true;
                        }
                        setPlayerCreatorLevel(target, lvl, sender);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(color("&cאנא הזן מספר תקין (0-3)."));
                    }
                    return true;
                }
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage(color("&eשימוש מהקונסול: /creatoradmin <reload | setlevel <player> <0-3>>"));
                return true;
            }
            openAdminGUI((Player) sender);
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("פקודה זו מיועדת לשחקנים בלבד.");
            return true;
        }

        Player player = (Player) sender;

        if (cmdName.equals("creator")) {
            openPlayerCreatorGUI(player);
            return true;
        }

        if (cmdName.equals("creatorperks")) {
            openPerksGUI(player);
            return true;
        }

        if (cmdName.equals("creatorpanel")) {
            if (getCreatorLevel(player) <= 0) {
                sendAlert(player, "&cפקודה זו מיועדת ליוצרי התוכן של PANDAVERSE בלבד! להגשת בקשה הקש &e/creator");
                return true;
            }
            openCreatorDashboard(player);
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("creatoradmin") && sender.hasPermission("panda.admin.creator")) {
            if (args.length == 1) {
                return Arrays.asList("reload", "setlevel");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("setlevel")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return names;
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("setlevel")) {
                return Arrays.asList("0", "1", "2", "3");
            }
        }
        return Collections.emptyList();
    }

    // ==================== תפריטים (GUIs) ====================

    private void fillGlassBorders(Inventory gui, Material glassType) {
        ItemStack glass = createGuiItem(glassType, " ", Collections.emptyList());
        int size = gui.getSize();
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, glass);
                }
            }
        }
    }

    private void openPlayerCreatorGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, color(GUI_MAIN));
        fillGlassBorders(gui, Material.CYAN_STAINED_GLASS_PANE);

        int level = getCreatorLevel(player);
        boolean hasPending = appsConfig.contains("pending." + player.getUniqueId());

        gui.setItem(10, createGuiItem(Material.PAPER, "&e&lדרישות הסף שלנו", getConfig().getStringList("requirements")));

        if (level > 0) {
            gui.setItem(13, createGuiItem(Material.EMERALD_BLOCK, "&a&lאתה כבר יוצר תוכן! (רמה " + level + ")",
                    Arrays.asList("&7לחץ כאן כדי לפתוח את פאנל היוצרים שלך", "&e/creatorpanel")));
        } else if (hasPending) {
            String link = appsConfig.getString("pending." + player.getUniqueId() + ".link", "");
            gui.setItem(13, createGuiItem(Material.CLOCK, "&e&lהבקשה שלך בבדיקה!",
                    Arrays.asList("&7כבר הגשת בקשה להצטרפות:", "&b" + link, "&7הנהלת השרת תבדוק אותה בקרוב.")));
        } else {
            gui.setItem(13, createGuiItem(Material.NETHER_STAR, "&a&lהגש בקשה להיות יוצר תוכן",
                    Arrays.asList("&7רוצה לקבל דרגת יוצר תוכן וכלים מיוחדים?", "&eלחץ כאן לשליחת הקישור לערוץ שלך בצ'אט.")));
        }

        gui.setItem(16, createGuiItem(Material.GOLD_BLOCK, "&6&lהטבות וגישות לפי רמות",
                Arrays.asList("&7צפה בכל הכלים וההטבות שמקבלים", "&7יוצרי תוכן ברמות 1, 2 ו-3.", "&eלחץ לצפייה.")));

        gui.setItem(22, createGuiItem(Material.BLUE_BANNER, "&9&lשרת הדיסקורד שלנו",
                Arrays.asList("&7הצטרף לקהילה ולעדכונים:", "&b" + discordLink, "&eלחץ לקבלת קישור לחיץ בצ'אט.")));

        player.openInventory(gui);
    }

    private void openPerksGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, color(GUI_PERKS));
        fillGlassBorders(gui, Material.YELLOW_STAINED_GLASS_PANE);

        gui.setItem(11, createGuiItem(Material.IRON_INGOT, "&b&lרמה 1 (Creator I)", Arrays.asList(
                "&7• מצב מעוף אישי (Flight)",
                "&7• מצב צילום נקי (בוקר + ניקוי צ'אט)",
                "&7• שמירת נקודת צילום וטלפורט אליה",
                "&7• מהירות תנועה קלה (Speed)"
        )));
        gui.setItem(13, createGuiItem(Material.GOLD_INGOT, "&e&lרמה 2 (Creator II)", Arrays.asList(
                "&7• &fכל ההטבות של רמה 1",
                "&7• הסתרת שחקנים לצילום נקי",
                "&7• שליטה במזג אוויר אישי",
                "&7• אפקט זוהר (Glowing) ושיגור זיקוקים"
        )));
        gui.setItem(15, createGuiItem(Material.DIAMOND, "&c&lרמה 3 (Creator III)", Arrays.asList(
                "&7• &fכל ההטבות של רמות 1 ו-2",
                "&7• מצב איוונט (חסינות מנזק + ראיית לילה)",
                "&7• מצב היעלמות מצוות/שחקנים (Vanish)",
                "&7• שליחת הכרזות לייב/הגרלה/סרטון לכל השרת"
        )));
        gui.setItem(22, createGuiItem(Material.ARROW, "&c&lחזרה לתפריט הראשי", Collections.singletonList("&7לחץ לחזרה")));

        player.openInventory(gui);
    }

    private void openCreatorDashboard(Player player) {
        int level = getCreatorLevel(player);
        UUID uuid = player.getUniqueId();
        Inventory gui = Bukkit.createInventory(null, 45, color(GUI_PANEL_PREFIX + " &8(רמה " + level + ")"));
        fillGlassBorders(gui, Material.MAGENTA_STAINED_GLASS_PANE);

        // --- רמה 1 ---
        boolean isFlying = player.getAllowFlight();
        gui.setItem(10, createGuiItem(level >= 1 ? (isFlying ? Material.FEATHER : Material.ENDER_PEARL) : Material.BARRIER,
                "&b&lמצב מעוף (Flight)",
                Arrays.asList("&7דרישה: &bרמה 1+", "&7סטטוס: " + (isFlying ? "&aמופעל" : "&cכבוי"),
                        level >= 1 ? "&eלחץ להפעלה/כיבוי." : "&cנעול לרמה 1+")));

        boolean isPhoto = photoModeCreators.contains(uuid);
        gui.setItem(11, createGuiItem(level >= 1 ? (isPhoto ? Material.COMPARATOR : Material.CLOCK) : Material.BARRIER,
                "&e&lמצב צילום (שקט ובוקר)",
                Arrays.asList("&7דרישה: &bרמה 1+", "&7מנקה צ'אט וקובע בוקר אישי.", "&7סטטוס: " + (isPhoto ? "&aמופעל" : "&cכבוי"),
                        level >= 1 ? "&eלחץ להפעלה/כיבוי." : "&cנעול לרמה 1+")));

        gui.setItem(19, createGuiItem(level >= 1 ? Material.COMPASS : Material.BARRIER,
                "&6&lשמירת נקודת צילום",
                Arrays.asList("&7דרישה: &bרמה 1+", "&7שמור מיקום נוכחי לחזרה מהירה.",
                        savedCreatorSpots.containsKey(uuid) ? "&aקיימת נקודה שמורה!" : "&7אין נקודה שמורה.",
                        level >= 1 ? "&eלחץ לשמירה." : "&cנעול לרמה 1+")));

        gui.setItem(20, createGuiItem(level >= 1 ? Material.ENDER_EYE : Material.BARRIER,
                "&5&lחזרה לנקודת הצילום",
                Arrays.asList("&7דרישה: &bרמה 1+", "&7טלפורט מיידי למיקום ששמרת.",
                        level >= 1 ? "&eלחץ לטלפורט." : "&cנעול לרמה 1+")));

        boolean hasSpeed = player.hasPotionEffect(PotionEffectType.SPEED);
        gui.setItem(28, createGuiItem(level >= 1 ? Material.SUGAR : Material.BARRIER,
                "&f&lמהירות תנועה קלה (Speed)",
                Arrays.asList("&7דרישה: &bרמה 1+", "&7סטטוס: " + (hasSpeed ? "&aמופעל" : "&cכבוי"),
                        level >= 1 ? "&eלחץ להפעלה/כיבוי." : "&cנעול לרמה 1+")));

        // --- רמה 2 ---
        boolean isHidden = hidePlayersMode.contains(uuid);
        gui.setItem(12, createGuiItem(level >= 2 ? (isHidden ? Material.GLASS : Material.TINTED_GLASS) : Material.BARRIER,
                "&7&lהסתרת שחקנים בסביבה",
                Arrays.asList("&7דרישה: &eרמה 2+", "&7מסתיר שחקנים אחרים אצלך לצילום נקי.", "&7סטטוס: " + (isHidden ? "&aמוסתרים" : "&cגלויים"),
                        level >= 2 ? "&eלחץ להפעלה/כיבוי." : "&cדורש רמה 2!")));

        boolean isRain = rainWeatherCreators.contains(uuid);
        gui.setItem(13, createGuiItem(level >= 2 ? (isRain ? Material.WATER_BUCKET : Material.SUNFLOWER) : Material.BARRIER,
                "&3&lמזג אוויר אישי",
                Arrays.asList("&7דרישה: &eרמה 2+", "&7מצב נוכחי: " + (isRain ? "&bגשם אישי" : "&eרגיל / שמש"),
                        level >= 2 ? "&eלחץ להחלפה." : "&cדורש רמה 2!")));

        gui.setItem(21, createGuiItem(level >= 2 ? Material.FIREWORK_ROCKET : Material.BARRIER,
                "&d&lשיגור זיקוקי קונפטי",
                Arrays.asList("&7דרישה: &eרמה 2+", "&7יוצר אפקט חגיגי סביבך לשידור!",
                        level >= 2 ? "&eלחץ להפעלה." : "&cדורש רמה 2!")));

        boolean hasGlowing = player.isGlowing();
        gui.setItem(22, createGuiItem(level >= 2 ? Material.GLOW_INK_SAC : Material.BARRIER,
                "&b&lאפקט זוהר לצילום (Glowing)",
                Arrays.asList("&7דרישה: &eרמה 2+", "&7סטטוס: " + (hasGlowing ? "&aמופעל" : "&cכבוי"),
                        level >= 2 ? "&eלחץ להפעלה/כיבוי." : "&cדורש רמה 2!")));

        // --- רמה 3 ---
        boolean isEvent = eventModeCreators.contains(uuid);
        gui.setItem(14, createGuiItem(level >= 3 ? (isEvent ? Material.GOLDEN_APPLE : Material.SHIELD) : Material.BARRIER,
                "&a&lמצב איוונט (חסינות מוות)",
                Arrays.asList("&7דרישה: &cרמה 3", "&7מונע נזק ומעניק ראיית לילה.", "&7סטטוס: " + (isEvent ? "&aמופעל" : "&cכבוי"),
                        level >= 3 ? "&eלחץ להפעלה/כיבוי." : "&cדורש רמה 3!")));

        boolean isVanished = vanishModeCreators.contains(uuid);
        gui.setItem(15, createGuiItem(level >= 3 ? (isVanished ? Material.PHANTOM_MEMBRANE : Material.ENDER_CHEST) : Material.BARRIER,
                "&8&lהיעלמות לצילום (Creator Vanish)",
                Arrays.asList("&7דרישה: &cרמה 3", "&7מעלים אותך מעיני שחקנים רגילים לצילום סינמטי.", "&7סטטוס: " + (isVanished ? "&aמוסתר" : "&cגלוי"),
                        level >= 3 ? "&eלחץ להפעלה/כיבוי." : "&cדורש רמה 3!")));

        gui.setItem(24, createGuiItem(level >= 3 ? Material.RED_CONCRETE : Material.BARRIER,
                "&c&lהכרזת לייב חדש",
                Arrays.asList("&7דרישה: &cרמה 3", level >= 3 ? "&eלחץ לפרסום שידור חי לכל השרת." : "&cדורש רמה 3!")));

        gui.setItem(25, createGuiItem(level >= 3 ? Material.GOLD_INGOT : Material.BARRIER,
                "&e&lהכרזת הגרלה",
                Arrays.asList("&7דרישה: &cרמה 3", level >= 3 ? "&eלחץ לפרסום הגרלה לקהילה." : "&cדורש רמה 3!")));

        gui.setItem(34, createGuiItem(level >= 3 ? Material.DIAMOND : Material.BARRIER,
                "&b&lהכרזת סרטון / איוונט",
                Arrays.asList("&7דרישה: &cרמה 3", level >= 3 ? "&eלחץ לפרסום סרטון או איוונט חדש." : "&cדורש רמה 3!")));

        player.openInventory(gui);
    }

    // ==================== תפריטי הנהלה (ADMIN GUIs) ====================

    private void openAdminGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, color(GUI_ADMIN));
        fillGlassBorders(gui, Material.RED_STAINED_GLASS_PANE);

        ConfigurationSection pendingSec = appsConfig.getConfigurationSection("pending");
        int pendingCount = (pendingSec != null) ? pendingSec.getKeys(false).size() : 0;

        gui.setItem(11, createGuiItem(Material.WRITABLE_BOOK, "&e&lבקשות ממתינות לאישור &7(" + pendingCount + ")",
                Arrays.asList("&7צפה בכל הבקשות ששלחו שחקנים,", "&7אשר או דחה אותם בלחיצת כפתור.", "&eלחץ לפתיחה.")));

        gui.setItem(13, createGuiItem(Material.PLAYER_HEAD, "&b&lניהול רמות יוצרי תוכן מחוברים",
                Arrays.asList("&7שנה רמה (1, 2, 3) או הסר דרגה", "&7לשחקנים המחוברים כרגע לשרת.", "&eלחץ לפתיחה.")));

        String g1 = getConfig().getString("luckperms.groups.level1", "creator1");
        String g2 = getConfig().getString("luckperms.groups.level2", "creator2");
        String g3 = getConfig().getString("luckperms.groups.level3", "creator3");
        gui.setItem(15, createGuiItem(Material.COMMAND_BLOCK, "&a&lסטטוס LuckPerms ופקודות",
                Arrays.asList(
                        "&7מצב חיבור LP API: " + (luckPermsApi != null ? "&aמחובר!" : "&eמצב פקודות"),
                        "&7קבוצת רמה 1: &f" + g1,
                        "&7קבוצת רמה 2: &f" + g2,
                        "&7קבוצת רמה 3: &f" + g3,
                        "&8פקודה מהירה:",
                        "&e/creatoradmin setlevel <שחקן> <0-3>"
                )));

        player.openInventory(gui);
    }

    private void openPendingApplicationsGUI(Player admin) {
        Inventory gui = Bukkit.createInventory(null, 54, color(GUI_ADMIN_APPS));
        ConfigurationSection pendingSec = appsConfig.getConfigurationSection("pending");

        if (pendingSec != null) {
            int slot = 0;
            for (String uuidStr : pendingSec.getKeys(false)) {
                if (slot >= 45) break;
                String name = pendingSec.getString(uuidStr + ".name", "Unknown");
                String link = pendingSec.getString(uuidStr + ".link", "");
                String date = pendingSec.getString(uuidStr + ".date", "");

                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(uuidStr)));
                    meta.setDisplayName(color("&b&l" + name));
                    meta.setLore(Arrays.asList(
                            color("&7קישור: &e" + link),
                            color("&7תאריך הגשה: &f" + date),
                            color("&8UUID: " + uuidStr),
                            "",
                            color("&a&lקליק שמאלי: &fאשר והענק רמה 1"),
                            color("&e&lשיפט + שמאלי: &fאשר והענק רמה 2"),
                            color("&c&lקליק ימני: &fדחה ומחק בקשה")
                    ));
                    head.setItemMeta(meta);
                }
                gui.setItem(slot++, head);
            }
        }

        gui.setItem(49, createGuiItem(Material.ARROW, "&c&lחזרה לתפריט הנהלה", Collections.singletonList("&7לחץ לחזרה")));
        admin.openInventory(gui);
    }

    private void openOnlinePlayersManageGUI(Player admin) {
        Inventory gui = Bukkit.createInventory(null, 54, color(GUI_ADMIN_PLAYERS));
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            int lvl = getCreatorLevel(target);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(color("&f&l" + target.getName()));
                meta.setLore(Arrays.asList(
                        color("&7רמת יוצר נוכחית: " + (lvl > 0 ? "&aרמה " + lvl : "&7ללא דרגה")),
                        "",
                        color("&eלחץ לעריכת רמת יוצר התוכן של שחקן זה.")
                ));
                head.setItemMeta(meta);
            }
            gui.setItem(slot++, head);
        }

        gui.setItem(49, createGuiItem(Material.ARROW, "&c&lחזרה לתפריט הנהלה", Collections.singletonList("&7לחץ לחזרה")));
        admin.openInventory(gui);
    }

    private void openEditPlayerLevelGUI(Player admin, OfflinePlayer target) {
        Inventory gui = Bukkit.createInventory(null, 27, color(GUI_ADMIN_EDIT_PREFIX + target.getName()));
        fillGlassBorders(gui, Material.GRAY_STAINED_GLASS_PANE);

        gui.setItem(10, createGuiItem(Material.BARRIER, "&c&lהסרת דרגת יוצר (רמה 0)",
                Arrays.asList("&7מסיר את כל קבוצות והרשאות היוצר", "&7מהשחקן &f" + target.getName())));

        gui.setItem(12, createGuiItem(Material.IRON_INGOT, "&b&lהגדר כרמה 1 (Creator I)",
                Arrays.asList("&7מעניק קבוצת LuckPerms + הרשאות רמה 1")));

        gui.setItem(14, createGuiItem(Material.GOLD_INGOT, "&e&lהגדר כרמה 2 (Creator II)",
                Arrays.asList("&7מעניק קבוצת LuckPerms + הרשאות רמה 2")));

        gui.setItem(16, createGuiItem(Material.DIAMOND, "&c&lהגדר כרמה 3 (Creator III)",
                Arrays.asList("&7מעניק קבוצת LuckPerms + הרשאות רמה 3")));

        gui.setItem(22, createGuiItem(Material.ARROW, "&c&lחזרה", Collections.singletonList("&7לחץ לחזרה")));
        admin.openInventory(gui);
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(color(line));
            }
            meta.setLore(coloredLore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_POTION_EFFECTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== טיפול בלחיצות (Events) ====================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (title.equals(color(GUI_MAIN)) ||
            title.equals(color(GUI_PERKS)) ||
            title.startsWith(color(GUI_PANEL_PREFIX)) ||
            title.equals(color(GUI_ADMIN)) ||
            title.equals(color(GUI_ADMIN_APPS)) ||
            title.equals(color(GUI_ADMIN_PLAYERS)) ||
            title.startsWith(color(GUI_ADMIN_EDIT_PREFIX))) {

            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            int slot = event.getRawSlot();
            if (slot >= event.getInventory().getSize()) return; // לחיצה באינבנטורי של השחקן עצמו

            // 1. תפריט ראשי /creator
            if (title.equals(color(GUI_MAIN))) {
                int level = getCreatorLevel(player);
                if (slot == 13) {
                    if (level > 0) {
                        openCreatorDashboard(player);
                    } else if (appsConfig.contains("pending." + player.getUniqueId())) {
                        sendAlert(player, "&8[&bPandaCreators&8] &eהבקשה שלך כבר ממתינה לבדיקת ההנהלה!");
                        player.closeInventory();
                    } else {
                        player.closeInventory();
                        pendingApplication.add(player.getUniqueId());
                        sendAlert(player, "&8[&bPandaCreators&8] &aכתוב בצ'אט את הקישור לערוץ / לשידור שלך (או כתוב &cביטול&a):");
                    }
                } else if (slot == 16) {
                    openPerksGUI(player);
                } else if (slot == 22) {
                    player.closeInventory();
                    TextComponent msg = new TextComponent(color("&8[&bPandaCreators&8] &7לחץ כאן להצטרפות לדיסקורד: &b&n" + discordLink));
                    msg.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, discordLink));
                    msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(color("&eלחץ לפתיחת הקישור")).create()));
                    player.spigot().sendMessage(msg);
                }
            }

            // 2. תפריט הטבות /creatorperks
            else if (title.equals(color(GUI_PERKS))) {
                if (slot == 22) {
                    openPlayerCreatorGUI(player);
                }
            }

            // 3. פאנל יוצרי תוכן /creatorpanel
            else if (title.startsWith(color(GUI_PANEL_PREFIX))) {
                handleCreatorPanelClick(player, slot);
            }

            // 4. תפריט הנהלה ראשי /creatoradmin
            else if (title.equals(color(GUI_ADMIN))) {
                if (!player.hasPermission("panda.admin.creator") && !player.isOp()) return;
                if (slot == 11) openPendingApplicationsGUI(player);
                else if (slot == 13) openOnlinePlayersManageGUI(player);
            }

            // 5. תפריט בקשות ממתינות
            else if (title.equals(color(GUI_ADMIN_APPS))) {
                if (!player.hasPermission("panda.admin.creator") && !player.isOp()) return;
                if (slot == 49) {
                    openAdminGUI(player);
                    return;
                }
                ItemStack clicked = event.getCurrentItem();
                if (clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() != null && clicked.getItemMeta().getLore() != null) {
                    List<String> lore = clicked.getItemMeta().getLore();
                    UUID targetUuid = null;
                    for (String line : lore) {
                        String stripped = ChatColor.stripColor(line);
                        if (stripped.startsWith("UUID: ")) {
                            targetUuid = UUID.fromString(stripped.replace("UUID: ", "").trim());
                            break;
                        }
                    }
                    if (targetUuid != null) {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
                        if (event.isRightClick()) {
                            appsConfig.set("pending." + targetUuid, null);
                            saveApplicationsFile();
                            sendAlert(player, "&8[&bPandaCreators&8] &cהבקשה של &f" + target.getName() + " &cנדחתה ונמחקה.");
                            if (target.isOnline() && target.getPlayer() != null) {
                                sendAlert(target.getPlayer(), "&8[&bPandaCreators&8] &cבקשתך להצטרף לצוות יוצרי התוכן נדחתה הפעם.");
                            }
                            openPendingApplicationsGUI(player);
                        } else if (event.isLeftClick()) {
                            int grantLevel = event.isShiftClick() ? 2 : 1;
                            appsConfig.set("pending." + targetUuid, null);
                            saveApplicationsFile();
                            setPlayerCreatorLevel(target, grantLevel, player);
                            openPendingApplicationsGUI(player);
                        }
                    }
                }
            }

            // 6. רשימת שחקנים מחוברים לניהול
            else if (title.equals(color(GUI_ADMIN_PLAYERS))) {
                if (!player.hasPermission("panda.admin.creator") && !player.isOp()) return;
                if (slot == 49) {
                    openAdminGUI(player);
                    return;
                }
                ItemStack clicked = event.getCurrentItem();
                if (clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() != null) {
                    String targetName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target != null) {
                        openEditPlayerLevelGUI(player, target);
                    } else {
                        sendAlert(player, "&cהשחקן התנתק מהשרת.");
                        openOnlinePlayersManageGUI(player);
                    }
                }
            }

            // 7. עריכת רמה לשחקן ספציפי
            else if (title.startsWith(color(GUI_ADMIN_EDIT_PREFIX))) {
                if (!player.hasPermission("panda.admin.creator") && !player.isOp()) return;
                if (slot == 22) {
                    openOnlinePlayersManageGUI(player);
                    return;
                }
                String targetName = ChatColor.stripColor(title).replace("עריכת יוצר: ", "").trim();
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

                if (slot == 10) {
                    setPlayerCreatorLevel(target, 0, player);
                    player.closeInventory();
                } else if (slot == 12) {
                    setPlayerCreatorLevel(target, 1, player);
                    player.closeInventory();
                } else if (slot == 14) {
                    setPlayerCreatorLevel(target, 2, player);
                    player.closeInventory();
                } else if (slot == 16) {
                    setPlayerCreatorLevel(target, 3, player);
                    player.closeInventory();
                }
            }
        }
    }

    private void handleCreatorPanelClick(Player player, int slot) {
        int level = getCreatorLevel(player);
        UUID uuid = player.getUniqueId();

        // בדיקת הרשאות לפי סלוט
        if ((slot == 10 || slot == 11 || slot == 19 || slot == 20 || slot == 28) && level < 1) {
            sendAlert(player, "&cפאנל זה דורש לפחות רמה 1!");
            return;
        }
        if ((slot == 12 || slot == 13 || slot == 21 || slot == 22) && level < 2) {
            sendAlert(player, "&cאפשרות זו דורשת רמה 2 ומעלה!");
            return;
        }
        if ((slot == 14 || slot == 15 || slot == 24 || slot == 25 || slot == 34) && level < 3) {
            sendAlert(player, "&cאפשרות זו דורשת רמה 3 (מתקדם)!");
            return;
        }

        switch (slot) {
            case 10: { // Flight
                boolean current = player.getAllowFlight();
                player.setAllowFlight(!current);
                player.setFlying(!current);
                sendAlert(player, "&8[&bPandaCreators&8] &7מעוף: " + (!current ? "&aמופעל" : "&cכבוי"));
                openCreatorDashboard(player);
                break;
            }
            case 11: { // Photo Mode
                if (photoModeCreators.contains(uuid)) {
                    photoModeCreators.remove(uuid);
                    player.resetPlayerTime();
                    sendAlert(player, "&8[&bPandaCreators&8] &cמצב צילום כובה.");
                } else {
                    photoModeCreators.add(uuid);
                    player.setPlayerTime(1000L, false);
                    for (int i = 0; i < 40; i++) player.sendMessage("");
                    sendAlert(player, "&8[&bPandaCreators&8] &eמצב צילום הופעל (צ'אט נוקה, בוקר אישי).");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                }
                openCreatorDashboard(player);
                break;
            }
            case 19: { // Save Spot
                savedCreatorSpots.put(uuid, player.getLocation());
                sendAlert(player, "&8[&bPandaCreators&8] &aנקודת הצילום נשמרה בהצלחה!");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                openCreatorDashboard(player);
                break;
            }
            case 20: { // Teleport Spot
                player.closeInventory();
                if (savedCreatorSpots.containsKey(uuid)) {
                    player.teleport(savedCreatorSpots.get(uuid));
                    sendAlert(player, "&8[&bPandaCreators&8] &aהוחזרת לנקודת הצילום שלך.");
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                } else {
                    sendAlert(player, "&cטרם שמרת נקודת צילום!");
                }
                break;
            }
            case 28: { // Speed
                if (player.hasPotionEffect(PotionEffectType.SPEED)) {
                    player.removePotionEffect(PotionEffectType.SPEED);
                    sendAlert(player, "&8[&bPandaCreators&8] &7מהירות הליכה הוחזרה לרגיל.");
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
                    sendAlert(player, "&8[&bPandaCreators&8] &aהופעלה מהירות תנועה קלה.");
                }
                openCreatorDashboard(player);
                break;
            }
            case 12: { // Hide other players
                if (hidePlayersMode.contains(uuid)) {
                    hidePlayersMode.remove(uuid);
                    for (Player p : Bukkit.getOnlinePlayers()) player.showPlayer(this, p);
                    sendAlert(player, "&8[&bPandaCreators&8] &7שחקנים הוחזרו לתצוגה.");
                } else {
                    hidePlayersMode.add(uuid);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!p.equals(player)) player.hidePlayer(this, p);
                    }
                    sendAlert(player, "&8[&bPandaCreators&8] &aשחקנים אחרים הוסתרו מהמסך שלך.");
                }
                openCreatorDashboard(player);
                break;
            }
            case 13: { // Weather
                if (rainWeatherCreators.contains(uuid)) {
                    rainWeatherCreators.remove(uuid);
                    player.resetPlayerWeather();
                    sendAlert(player, "&8[&bPandaCreators&8] &7מזג האוויר הוחזר לרגיל.");
                } else {
                    rainWeatherCreators.add(uuid);
                    player.setPlayerWeather(WeatherType.DOWNFALL);
                    sendAlert(player, "&8[&bPandaCreators&8] &bמזג אוויר גשום הופעל אצלך בלבד.");
                }
                openCreatorDashboard(player);
                break;
            }
            case 21: { // Fireworks
                player.closeInventory();
                Location loc = player.getLocation();
                if (loc.getWorld() != null) {
                    loc.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, loc.clone().add(0, 1.2, 0), 45, 0.6, 1, 0.6, 0.12);
                    loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);
                }
                sendAlert(player, "&8[&bPandaCreators&8] &dשגרת זיקוקי קונפטי!");
                break;
            }
            case 22: { // Glowing
                boolean hasGlowing = player.isGlowing();
                player.setGlowing(!hasGlowing);
                sendAlert(player, "&8[&bPandaCreators&8] &7אפקט זוהר: " + (!hasGlowing ? "&aמופעל" : "&cכבוי"));
                openCreatorDashboard(player);
                break;
            }
            case 14: { // Event Mode (God + Night Vision)
                if (eventModeCreators.contains(uuid)) {
                    eventModeCreators.remove(uuid);
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                    sendAlert(player, "&8[&bPandaCreators&8] &cמצב איוונט כובה.");
                } else {
                    eventModeCreators.add(uuid);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
                    sendAlert(player, "&8[&bPandaCreators&8] &aמצב איוונט הופעל (חסינות מנזק וראיית לילה).");
                    player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
                }
                openCreatorDashboard(player);
                break;
            }
            case 15: { // Creator Vanish
                if (vanishModeCreators.contains(uuid)) {
                    vanishModeCreators.remove(uuid);
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        online.showPlayer(this, player);
                    }
                    sendAlert(player, "&8[&bPandaCreators&8] &7חזרת להיות גלוי לכולם.");
                } else {
                    vanishModeCreators.add(uuid);
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!online.hasPermission("panda.admin.creator") && !online.equals(player)) {
                            online.hidePlayer(this, player);
                        }
                    }
                    sendAlert(player, "&8[&bPandaCreators&8] &8מצב היעלמות (Vanish) הופעל לצילום!");
                }
                openCreatorDashboard(player);
                break;
            }
            case 24:
            case 25:
            case 34: { // Broadcasts
                if (!player.hasPermission("panda.creator.bypasscooldown") && broadcastCooldowns.containsKey(uuid)) {
                    long cooldownMs = broadcastCooldownSeconds * 1000L;
                    long timeLeft = (broadcastCooldowns.get(uuid) + cooldownMs) - System.currentTimeMillis();
                    if (timeLeft > 0) {
                        long secondsLeft = timeLeft / 1000L;
                        long mins = secondsLeft / 60;
                        long secs = secondsLeft % 60;
                        player.closeInventory();
                        sendAlert(player, "&cעליך להמתין עוד " + mins + " דקות ו-" + secs + " שניות לפני הכרזה נוספת!");
                        return;
                    }
                }

                player.closeInventory();
                waitingForBroadcast.add(uuid);

                String type = "live";
                String nameType = "לייב";
                if (slot == 25) { type = "giveaway"; nameType = "הגרלה"; }
                if (slot == 34) { type = "event"; nameType = "אירוע/סרטון"; }

                broadcastType.put(uuid, type);
                sendAlert(player, "&8[&bPandaCreators&8] &aכתוב בצ'אט את ההודעה / הקישור עבור ה" + nameType + " שלך (או כתוב &cביטול&a):");
                break;
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();

        // הסתרת יוצרים שנמצאים ב-Vanish מהשחקן החדש שנכנס
        for (UUID vanishedId : vanishModeCreators) {
            Player vanished = Bukkit.getPlayer(vanishedId);
            if (vanished != null && !joined.hasPermission("panda.admin.creator")) {
                joined.hidePlayer(this, vanished);
            }
        }

        // הסתרת השחקן החדש מיוצרים שהפעילו "הסתרת שחקנים"
        for (UUID hiderId : hidePlayersMode) {
            Player hider = Bukkit.getPlayer(hiderId);
            if (hider != null && !hider.equals(joined)) {
                hider.hidePlayer(this, joined);
            }
        }

        // התראה לאדמין אם יש בקשות ממתינות
        if (joined.hasPermission("panda.admin.creator")) {
            ConfigurationSection pendingSec = appsConfig.getConfigurationSection("pending");
            if (pendingSec != null && !pendingSec.getKeys(false).isEmpty()) {
                int count = pendingSec.getKeys(false).size();
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    joined.sendMessage(color("&8[&bPandaCreators&8] &eישנן &c" + count + " &eבקשות יוצרי תוכן הממתינות לאישור! הקש &b/creatoradmin"));
                }, 40L);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingApplication.remove(uuid);
        waitingForBroadcast.remove(uuid);
        broadcastType.remove(uuid);
        photoModeCreators.remove(uuid);
        eventModeCreators.remove(uuid);
        rainWeatherCreators.remove(uuid);
        hidePlayersMode.remove(uuid);
        vanishModeCreators.remove(uuid);
        event.getPlayer().setGlowing(false);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (eventModeCreators.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 1. הגשת בקשת יוצר תוכן
        if (pendingApplication.contains(uuid)) {
            event.setCancelled(true);
            String input = event.getMessage().trim();
            pendingApplication.remove(uuid);

            if (input.equalsIgnoreCase("ביטול") || input.equalsIgnoreCase("cancel")) {
                sendAlert(player, "&8[&bPandaCreators&8] &cהגשת הבקשה בוטלה.");
                return;
            }

            // שמירה ב-applications.yml ב-Main Thread
            Bukkit.getScheduler().runTask(this, () -> {
                String dateStr = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());
                appsConfig.set("pending." + uuid + ".name", player.getName());
                appsConfig.set("pending." + uuid + ".link", input);
                appsConfig.set("pending." + uuid + ".date", dateStr);
                saveApplicationsFile();

                sendAlert(player, "&8[&bPandaCreators&8] &aהבקשה שלך נשמרה ונשלחה בהצלחה להנהלה!");

                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.hasPermission("panda.admin.creator")) {
                        online.sendMessage(color("&8[&bPandaCreators&8] &c[בקשת יוצר חדשה] &fהשחקן &b" + player.getName() + " &fהגיש בקשה!"));
                        online.sendMessage(color("&7קישור: &e" + input + " &8| &7לאישור מהיר: &a/creatoradmin"));
                        online.playSound(online.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                    }
                }
            });
            return;
        }

        // 2. שליחת הכרזה לשרת
        if (waitingForBroadcast.contains(uuid)) {
            event.setCancelled(true);
            String message = event.getMessage().trim();
            waitingForBroadcast.remove(uuid);
            String type = broadcastType.getOrDefault(uuid, "live");
            broadcastType.remove(uuid);

            if (message.equalsIgnoreCase("ביטול") || message.equalsIgnoreCase("cancel")) {
                sendAlert(player, "&8[&bPandaCreators&8] &cשליחת ההכרזה בוטלה.");
                return;
            }

            broadcastCooldowns.put(uuid, System.currentTimeMillis());

            Bukkit.getScheduler().runTask(this, () -> {
                String header = "&8&l=============================================";
                String titleMsg;
                if (type.equals("live")) {
                    titleMsg = "&c&l🔴 יוצר התוכן &f&l" + player.getName() + " &c&lהתחיל בשידור חי!";
                } else if (type.equals("giveaway")) {
                    titleMsg = "&e&l🎁 יוצר התוכן &f&l" + player.getName() + " &e&lהכריז על הגרלה חדשה!";
                } else {
                    titleMsg = "&b&l⭐ יוצר התוכן &f&l" + player.getName() + " &b&lהעלה סרטון / איוונט חדש!";
                }

                String soundName = getConfig().getString("broadcast.sound", "ENTITY_ENDER_DRAGON_GROWL");
                Sound broadcastSound = null;
                try {
                    broadcastSound = Sound.valueOf(soundName.toUpperCase());
                } catch (IllegalArgumentException ignored) {}

                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendMessage(color(header));
                    online.sendMessage(color(titleMsg));
                    online.sendMessage(color("&fפרטים / קישור: &e&n" + message));
                    online.sendMessage(color(header));
                    if (broadcastSound != null) {
                        online.playSound(online.getLocation(), broadcastSound, 0.7f, 1.1f);
                    }
                }
            });
        }
    }
}

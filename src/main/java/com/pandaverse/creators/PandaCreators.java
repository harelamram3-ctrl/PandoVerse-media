package com.pandaverse.creators;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class PandaCreators extends JavaPlugin implements CommandExecutor, Listener {

    private String discordLink;
    private final Set<UUID> pendingApplication = new HashSet<>();
    private final Map<UUID, String> submittedLinks = new HashMap<>();
    
    private final Set<UUID> photoModeCreators = new HashSet<>();
    private final Set<UUID> eventModeCreators = new HashSet<>();
    private final Set<UUID> rainWeatherCreators = new HashSet<>();
    private final Set<UUID> hidePlayersMode = new HashSet<>();
    private final Map<UUID, Location> savedCreatorSpots = new HashMap<>();

    private final Map<UUID, Long> broadcastCooldowns = new HashMap<>();
    private final Set<UUID> waitingForBroadcast = new HashSet<>();
    private final Map<UUID, String> broadcastType = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        getCommand("creator").setExecutor(this);
        getCommand("creatoradmin").setExecutor(this);
        getCommand("creatorperks").setExecutor(this);
        getCommand("creatorpanel").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info(ChatColor.GREEN + "PandaCreators has been enabled successfully!");
    }

    private void loadConfigValues() {
        reloadConfig();
        discordLink = getConfig().getString("discord-link", "https://discord.gg/pandaverse");
    }

    private void sendAlert(Player player, String message) {
        String colored = ChatColor.translateAlternateColorCodes('&', message);
        player.sendMessage(colored);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colored));
    }

    private int getCreatorLevel(Player player) {
        if (player.hasPermission("panda.creator.level3") || player.isOp()) return 3;
        if (player.hasPermission("panda.creator.level2")) return 2;
        if (player.hasPermission("panda.creator.level1")) return 1;
        return 0;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("פקודה זו מיועדת לשחקנים בלבד.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("creator")) {
            openPlayerCreatorGUI(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("creatorperks")) {
            openPerksGUI(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("creatorpanel")) {
            if (getCreatorLevel(player) <= 0) {
                sendAlert(player, "&cפקודה זו מיועדת ליוצרי התוכן של PANDAVERSE בלבד!");
                return true;
            }
            openCreatorDashboard(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("creatoradmin")) {
            if (!player.hasPermission("panda.admin.creator")) {
                sendAlert(player, "&cאין לך הרשאה להשתמש בפקודה זו!");
                return true;
            }
            openAdminGUI(player);
            return true;
        }

        return false;
    }

    private void openCreatorDashboard(Player player) {
        int level = getCreatorLevel(player);
        Inventory gui = Bukkit.createInventory(null, 45, ChatColor.translateAlternateColorCodes('&', "&d&lפאנל יוצרי תוכן (רמה " + level + ")"));

        boolean isFlying = player.getAllowFlight();
        gui.setItem(10, createGuiItem(level >= 1 ? (isFlying ? Material.FEATHER : Material.ENDER_PEARL) : Material.BARRIER, 
                "&b&lמצב מעוף (Flight)", 
                Arrays.asList(ChatColor.GRAY + "דרישה: רמה 1+", ChatColor.GRAY + "סטטוס: " + (isFlying ? ChatColor.GREEN + "מופעל" : ChatColor.RED + "כבוי"), level >= 1 ? ChatColor.YELLOW + "לחץ להפעלה/כיבוי." : ChatColor.STRIKETHROUGH + "נעול לרמה גבוהה יותר")));

        boolean isPhoto = photoModeCreators.contains(player.getUniqueId());
        gui.setItem(11, createGuiItem(level >= 1 ? (isPhoto ? Material.COMPARATOR : Material.CLOCK) : Material.BARRIER, 
                "&e&lמצב צילום (שקט ובוקר)", 
                Arrays.asList(ChatColor.GRAY + "דרישה: רמה 1+", ChatColor.GRAY + "מנקה צ'אט וקובע בוקר אישי.", level >= 1 ? ChatColor.YELLOW + "לחץ להפעלה/כיבוי." : ChatColor.STRIKETHROUGH + "נעול")));

        gui.setItem(20, createGuiItem(level >= 1 ? Material.COMPASS : Material.BARRIER, 
                "&6&lשמירת נקודת צילום", 
                Arrays.asList(ChatColor.GRAY + "דרישה: רמה 1+", ChatColor.GRAY + "שמור מיקום נוכחי לחזרה מהירה.", level >= 1 ? ChatColor.YELLOW + "לחץ לשמירה." : ChatColor.STRIKETHROUGH + "נעול")));

        gui.setItem(21, createGuiItem(level >= 1 ? Material.ENDER_EYE : Material.BARRIER, 
                "&5&lחזרה לנקודת הצילום", 
                Arrays.asList(ChatColor.GRAY + "דרישה: רמה 1+", ChatColor.GRAY + "טלפורט מיידי למיקום ששמרת.", level >= 1 ? ChatColor.YELLOW + "לחץ לטלפורט." : ChatColor.STRIKETHROUGH + "נעול")));

        gui.setItem(23, createGuiItem(level >= 1 ? Material.POTION : Material.BARRIER, 
                "&f&lמהירות תנועה קלה (Speed)", 
                Arrays.asList(ChatColor.GRAY + "דרישה: רמה 1+", ChatColor.GRAY + "מעניק זריזות להליכה חלקה.", level >= 1 ? ChatColor.YELLOW + "לחץ להפעלה." : ChatColor.STRIKETHROUGH + "נעול")));

        boolean isHidden = hidePlayersMode.contains(player.getUniqueId());
        gui.setItem(13, createGuiItem(level >= 2 ? (isHidden ? Material.GLASS : Material.TINTED_GLASS) : Material.BARRIER, 
                "&7&lהסתרת שחקנים בסביבה", 
                Arrays.asList(ChatColor.GRAY + "דרישה: &eרמה 2+", ChatColor.GRAY + "מסתיר שחקנים אחרים לצילום נקי.", level >= 2 ? ChatColor.YELLOW + "לחץ להפעלה/כיבוי." : ChatColor.RED + "דורש רמה 2!")));

        boolean isRain = rainWeatherCreators.contains(player.getUniqueId());
        gui.setItem(14, createGuiItem(level >= 2 ? (isRain ? Material.WATER_BUCKET : Material.SUNFLOWER) : Material.BARRIER, 
                "&3&lמזג אוויר אישי", 
                Arrays.asList(ChatColor.GRAY + "דרישה: &eרמה 2+", ChatColor.GRAY + "קובע מזג אוויר אישי רק לך.", level >= 2 ? ChatColor.YELLOW + "לחץ להחלפה." : ChatColor.RED + "דורש רמה 2!")));

        gui.setItem(19, createGuiItem(level >= 2 ? Material.FIREWORK_ROCKET : Material.BARRIER, 
                "&d&lשיגור זיקוקי קונפטי", 
                Arrays.asList(ChatColor.GRAY + "דרישה: &eרמה 2+", ChatColor.GRAY + "יוצר אפקט חגיגי סביבך לשידור!", level >= 2 ? ChatColor.YELLOW + "לחץ להפעלה." : ChatColor.RED + "דורש רמה 2!")));

        boolean hasGlowing = player.isGlowing();
        gui.setItem(22, createGuiItem(level >= 2 ? Material.GLOW_INK_SAC : Material.BARRIER, 
                "&b&lאפקט זוהר לצילום (Glowing)", 
                Arrays.asList(ChatColor.GRAY + "דרישה: &eרמה 2+", ChatColor.GRAY + "נותן לך זוהר קל בחושך.", level >= 2 ? ChatColor.YELLOW + "לחץ להפעלה." : ChatColor.RED + "דורש רמה 2!")));

        boolean isEvent = eventModeCreators.contains(player.getUniqueId());
        gui.setItem(12, createGuiItem(level >= 3 ? (isEvent ? Material.GOLDEN_APPLE : Material.SHIELD) : Material.BARRIER, 
                "&a&lמצב איוונט (חסינות מוות)", 
                Arrays.asList(ChatColor.GRAY + "דרישה: &cרמה 3 (מתקדם)", ChatColor.GRAY + "מונע נזק ומעניק ראיית לילה.", level >= 3 ? ChatColor.YELLOW + "לחץ להפעלה/כיבוי." : ChatColor.RED + "דורש רמה 3!")));

        gui.setItem(28, createGuiItem(level >= 3 ? Material.RED_CONCRETE : Material.BARRIER, "&c&lהכרזת לייב חדש", Arrays.asList(ChatColor.GRAY + "דרישה: &cרמה 3", level >= 3 ? ChatColor.GRAY + "פרסם שידור חי לכל השרת." : ChatColor.RED + "דורש רמה 3!")));
        gui.setItem(31, createGuiItem(level >= 3 ? Material.GOLD_INGOT : Material.BARRIER, "&e&lהכרזת הגרלה", Arrays.asList(ChatColor.GRAY + "דרישה: &cרמה 3", level >= 3 ? ChatColor.GRAY + "פרסם הגרלה שווה לקהילה." : ChatColor.RED + "דורש רמה 3!")));
        gui.setItem(34, createGuiItem(level >= 3 ? Material.DIAMOND : Material.BARRIER, "&b&lהכרזת סרטון / איוונט", Arrays.asList(ChatColor.GRAY + "דרישה: &cרמה 3", level >= 3 ? ChatColor.GRAY + "פרסם סרטון או איוונט חדש." : ChatColor.RED + "דורש רמה 3!")));

        player.openInventory(gui);
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openPlayerCreatorGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&b&lPANDAVERSE - יוצרי תוכן"));
        gui.setItem(10, createGuiItem(Material.PAPER, "&e&lדרישות הסף שלנו", getConfig().getStringList("requirements")));
        gui.setItem(13, createGuiItem(Material.NETHER_STAR, "&a&lהגש בקשה להיות יוצר תוכן", Collections.singletonList(ChatColor.GRAY + "לחץ לשליחת הקישור לערוץ.")));
        gui.setItem(16, createGuiItem(Material.GOLD_BLOCK, "&6&lהטבות וגישות לפי רמות", Arrays.asList(ChatColor.GRAY + "לחץ לצפייה ברמות ובהטבות.")));
        gui.setItem(22, createGuiItem(Material.RED_BANNER, "&b&lשרת הדיסקורד שלנו", Arrays.asList(ChatColor.GRAY + "קישור:", ChatColor.BLUE + discordLink)));
        player.openInventory(gui);
    }

    private void openPerksGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&6&lPANDAVERSE - רמות והטבות יוצרים"));
        gui.setItem(10, createGuiItem(Material.IRON_INGOT, "&b&lרמה 1 (Creator I)", Arrays.asList(ChatColor.GRAY + "• מעוף אישי (/flight)", ChatColor.GRAY + "• מצב צילום נקי ומהירות", ChatColor.GRAY + "• שמירת נקודת צילום")));
        gui.setItem(13, createGuiItem(Material.GOLD_INGOT, "&e&lרמה 2 (Creator II)", Arrays.asList(ChatColor.GRAY + "• כולל כל הטבות רמה 1", ChatColor.GRAY + "• הסתרת שחקנים ומזג אוויר אישי", ChatColor.GRAY + "• אפקט זוהר ושיגור זיקוקים")));
        gui.setItem(16, createGuiItem(Material.DIAMOND, "&c&lרמה 3 (Creator III)", Arrays.asList(ChatColor.GRAY + "• כולל כל הטבות רמה 1 ו-2", ChatColor.GRAY + "• מצב איוונט וחסינות מוות", ChatColor.GRAY + "• שליחת הכרזות לכל השרת")));
        player.openInventory(gui);
    }

    private void openAdminGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&c&lניהול יוצרי תוכן - הנהלה"));
        gui.setItem(13, createGuiItem(Material.BOOK, "&e&lפקודות מתן רמות להנהלה", Arrays.asList(
                ChatColor.GRAY + "הענקת רמה 1:", ChatColor.YELLOW + "/lp user <שחקן> permission set panda.creator.level1 true",
                ChatColor.GRAY + "הענקת רמה 2:", ChatColor.YELLOW + "/lp user <שחקן> permission set panda.creator.level2 true",
                ChatColor.GRAY + "הענקת רמה 3:", ChatColor.YELLOW + "/lp user <שחקן> permission set panda.creator.level3 true"
        )));
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.contains("PANDAVERSE") || title.contains("פאנל יוצרי תוכן")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();
            int level = getCreatorLevel(player);

            if (title.equals(ChatColor.translateAlternateColorCodes('&', "&b&lPANDAVERSE - יוצרי תוכן"))) {
                if (event.getRawSlot() == 13) {
                    player.closeInventory();
                    pendingApplication.add(player.getUniqueId());
                    sendAlert(player, "&8[&bPandaCreators&8] &aכתוב בצ'אט את הקישור לערוץ / לשידור שלך:");
                } else if (event.getRawSlot() == 16) {
                    openPerksGUI(player);
                } else if (event.getRawSlot() == 22) {
                    sendAlert(player, "&8[&bPandaCreators&8] &7קישור הדיסקורד: &b" + discordLink);
                    player.closeInventory();
                }
            }
            
            else if (title.startsWith(ChatColor.translateAlternateColorCodes('&', "&d&lפאנל יוצרי תוכן"))) {
                UUID uuid = player.getUniqueId();
                int slot = event.getRawSlot();

                if ((slot == 10 || slot == 11 || slot == 20 || slot == 21 || slot == 23) && level < 1) {
                    sendAlert(player, "&cפאנל זה דורש לפחות רמה 1!");
                    return;
                }
                if ((slot == 13 || slot == 14 || slot == 19 || slot == 22) && level < 2) {
                    sendAlert(player, "&cאפשרות זו דורשת רמה 2 ומעלה!");
                    return;
                }
                if ((slot == 12 || slot == 28 || slot == 31 || slot == 34) && level < 3) {
                    sendAlert(player, "&cאפשרות זו דורשת רמה 3 (מתקדם)!");
                    return;
                }

                if (slot == 10) {
                    boolean current = player.getAllowFlight();
                    player.setAllowFlight(!current);
                    player.setFlying(!current);
                    sendAlert(player, "&8[&bPandaCreators&8] &7מעוף: " + (!current ? "&aמופעל" : "&cכבוי"));
                    player.closeInventory();
                } 
                else if (slot == 11) {
                    if (photoModeCreators.contains(uuid)) {
                        photoModeCreators.remove(uuid);
                        player.resetPlayerTime();
                        sendAlert(player, "&8[&bPandaCreators&8] &cמצב צילום כובה.");
                    } else {
                        photoModeCreators.add(uuid);
                        player.setPlayerTime(0L, false);
                        for (int i = 0; i < 40; i++) player.sendMessage("");
                        sendAlert(player, "&8[&bPandaCreators&8] &eמצב צילום הופעל (צ'אט נוקה, בוקר אישי).");
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    }
                    player.closeInventory();
                }
                else if (slot == 12) {
                    if (eventModeCreators.contains(uuid)) {
                        eventModeCreators.remove(uuid);
                        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                        sendAlert(player, "&8[&bPandaCreators&8] &cמצב איוונט כובה.");
                    } else {
                        eventModeCreators.add(uuid);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 999999, 0, false, false));
                        sendAlert(player, "&8[&bPandaCreators&8] &aמצב איוונט הופעל (חסינות מנזק).");
                        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
                    }
                    player.closeInventory();
                }
                else if (slot == 13) {
                    if (hidePlayersMode.contains(uuid)) {
                        hidePlayersMode.remove(uuid);
                        for (Player p : Bukkit.getOnlinePlayers()) player.showPlayer(this, p);
                        sendAlert(player, "&8[&bPandaCreators&8] &7שחקנים הוחזרו לתצוגה.");
                    } else {
                        hidePlayersMode.add(uuid);
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (!p.equals(player)) player.hidePlayer(this, p);
                        }
                        sendAlert(player, "&8[&bPandaCreators&8] &aשחקנים אחרים הוסתרו.");
                    }
                    player.closeInventory();
                }
                else if (slot == 14) {
                    if (rainWeatherCreators.contains(uuid)) {
                        rainWeatherCreators.remove(uuid);
                        player.resetPlayerWeather();
                        sendAlert(player, "&8[&bPandaCreators&8] &7מזג האוויר הוחזר לרגיל.");
                    } else {
                        rainWeatherCreators.add(uuid);
                        player.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL);
                        sendAlert(player, "&8[&bPandaCreators&8] &bמזג אוויר גשום הופעל אצלך בלבד.");
                    }
                    player.closeInventory();
                }
                else if (slot == 19) {
                    player.closeInventory();
                    Location loc = player.getLocation();
                    loc.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, loc.add(0, 1, 0), 35, 0.5, 1, 0.5, 0.1);
                    loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);
                    sendAlert(player, "&8[&bPandaCreators&8] &dשגרת זיקוקי קונפטי!");
                }
                else if (slot == 20) {
                    player.closeInventory();
                    savedCreatorSpots.put(uuid, player.getLocation());
                    sendAlert(player, "&8[&bPandaCreators&8] &aנקודת הצילום נשמרה בהצלחה!");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                }
                else if (slot == 21) {
                    player.closeInventory();
                    if (savedCreatorSpots.containsKey(uuid)) {
                        player.teleport(savedCreatorSpots.get(uuid));
                        sendAlert(player, "&8[&bPandaCreators&8] &aהוחזרת לנקודת הצילום שלך.");
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    } else {
                        sendAlert(player, "&cטרם שמרת נקודת צילום!");
                    }
                }
                else if (slot == 22) {
                    player.closeInventory();
                    boolean hasGlowing = player.isGlowing();
                    player.setGlowing(!hasGlowing);
                    sendAlert(player, "&8[&bPandaCreators&8] &7אפקט זוהר: " + (!hasGlowing ? "&aמופעל" : "&cכבוי"));
                }
                else if (slot == 23) {
                    player.closeInventory();
                    if (player.hasPotionEffect(PotionEffectType.SPEED)) {
                        player.removePotionEffect(PotionEffectType.SPEED);
                        sendAlert(player, "&8[&bPandaCreators&8] &7מהירות הליכה הוחזרה לרגיל.");
                    } else {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 0, false, false));
                        sendAlert(player, "&8[&bPandaCreators&8] &aהופעלה מהירות תנועה קלה.");
                    }
                }
                else if (slot == 28 || slot == 31 || slot == 34) {
                    long cooldownTime = 300 * 1000L;
                    if (broadcastCooldowns.containsKey(uuid)) {
                        long timeLeft = (broadcastCooldowns.get(uuid) + cooldownTime) - System.currentTimeMillis();
                        if (timeLeft > 0) {
                            long minutesLeft = timeLeft / 60000 + 1;
                            player.closeInventory();
                            sendAlert(player, "&cעליך להמתין עוד " + minutesLeft + " דקות לפני שליחת הכרזה נוספת!");
                            return;
                        }
                    }

                    player.closeInventory();
                    waitingForBroadcast.add(uuid);
                    
                    String type = "live";
                    String nameType = "לייב";
                    if (slot == 31) { type = "giveaway"; nameType = "הגרלה"; }
                    if (slot == 34) { type = "event"; nameType = "אירוע/סרטון"; }
                    
                    broadcastType.put(uuid, type);
                    sendAlert(player, "&8[&bPandaCreators&8] &aכתוב בצ'אט את ההודעה / הקישור עבור ה" + nameType + " שלך:");
                }
            }
        }
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
        
        if (pendingApplication.contains(player.getUniqueId())) {
            event.setCancelled(true);
            String link = event.getMessage();
            pendingApplication.remove(player.getUniqueId());
            submittedLinks.put(player.getUniqueId(), link);

            sendAlert(player, "&8[&bPandaCreators&8] &aהבקשה שלך נשלחה בהצלחה להנהלה!");

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("panda.admin.creator")) {
                    online.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[בקשת יוצר תוכן] &fהשחקן &b" + player.getName() + " &fהגיש בקשה!"));
                    online.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7קישור: &e" + link));
                }
            }
            return;
        }

        if (waitingForBroadcast.contains(player.getUniqueId())) {
            event.setCancelled(true);
            waitingForBroadcast.remove(player.getUniqueId());
            String message = event.getMessage();
            String type = broadcastType.getOrDefault(player.getUniqueId(), "live");
            broadcastType.remove(player.getUniqueId());

            broadcastCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

            String header = "&8&l=====================================";
            String titleMsg;
            if (type.equals("live")) titleMsg = "&c&l🔴 יוצר התוכן &f" + player.getName() + " &c&lהתחיל בשידור חי!";
            else if (type.equals("giveaway")) titleMsg = "&e&l🎁 יוצר התוכן &f" + player.getName() + " &e&lהכריז על הגרלה חדשה!";
            else titleMsg = "&b&l⭐ יוצר התוכן &f" + player.getName() + " &b&lהעלה סרטון / אירוע חדש!";

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(ChatColor.translateAlternateColorCodes('&', header));
                online.sendMessage(ChatColor.translateAlternateColorCodes('&', titleMsg));
                online.sendMessage(ChatColor.translateAlternateColorCodes('&', "&fפרטים: &e" + message));
                online.sendMessage(ChatColor.translateAlternateColorCodes('&', header));
            }
        }
    }
}

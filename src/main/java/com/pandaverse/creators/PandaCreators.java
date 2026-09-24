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
    
    // מצבים של יוצרי תוכן
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

        getLogger().info(ChatColor.GREEN + "PandaCreators has been enabled successfully with ActionBars!");
    }

    private void loadConfigValues() {
        reloadConfig();
        discordLink = getConfig().getString("discord-link", "https://discord.gg/pandaverse");
    }

    // פונקציית עזר להצגת הודעה גם בצ'אט וגם כ-Action Bar על המסך למשך שתי שניות
    private void sendAlert(Player player, String message) {
        String colored = ChatColor.translateAlternateColorCodes('&', message);
        player.sendMessage(colored);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colored));
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
            if (!player.hasPermission("panda.creator.panel")) {
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
        Inventory gui = Bukkit.createInventory(null, 45, ChatColor.translateAlternateColorCodes('&', "&d&lפאנל יוצרי תוכן - PANDAVERSE"));

        boolean isFlying = player.getAllowFlight();
        gui.setItem(10, createGuiItem(isFlying ? Material.FEATHER : Material.ENDER_PEARL, "&b&lמצב מעוף (Flight)", 
                Arrays.asList(ChatColor.GRAY + "סטטוס: " + (isFlying ? ChatColor.GREEN + "מופעל" : ChatColor.RED + "כבוי"), ChatColor.YELLOW + "לחץ להפעלה/כיבוי.")));

        boolean isPhoto = photoModeCreators.contains(player.getUniqueId());
        gui.setItem(11, createGuiItem(isPhoto ? Material.COMPARATOR : Material.CLOCK, "&e&lמצב צילום (שקט ובוקר)", 
                Arrays.asList(ChatColor.GRAY + "מנקה צ'אט וקובע בוקר אישי.", ChatColor.YELLOW + "לחץ להפעלה/כיבוי.")));

        boolean isEvent = eventModeCreators.contains(player.getUniqueId());
        gui.setItem(12, createGuiItem(isEvent ? Material.GOLDEN_APPLE : Material.SHIELD, "&a&lמצב איוונט (חסינות מוות)", 
                Arrays.asList(ChatColor.GRAY + "מונע נזק ומעניק ראיית לילה.", ChatColor.YELLOW + "לחץ להפעלה/כיבוי.")));

        boolean isHidden = hidePlayersMode.contains(player.getUniqueId());
        gui.setItem(13, createGuiItem(isHidden ? Material.GLASS : Material.TINTED_GLASS, "&7&lהסתרת שחקנים בסביבה", 
                Arrays.asList(ChatColor.GRAY + "מסתיר שחקנים אחרים לצילום נקי.", ChatColor.YELLOW + "לחץ להפעלה/כיבוי.")));

        boolean isRain = rainWeatherCreators.contains(player.getUniqueId());
        gui.setItem(14, createGuiItem(isRain ? Material.WATER_BUCKET : Material.SUNFLOWER, "&3&lמזג אוויר אישי", 
                Arrays.asList(ChatColor.GRAY + "קובע מזג אוויר אישי רק לך.", ChatColor.YELLOW + "לחץ להחלפה.")));

        gui.setItem(19, createGuiItem(Material.FIREWORK_ROCKET, "&d&lשיגור זיקוקי קונפטי", 
                Arrays.asList(ChatColor.GRAY + "יוצר אפקט חגיגי סביבך לשידור!", ChatColor.YELLOW + "לחץ להפעלה.")));

        gui.setItem(20, createGuiItem(Material.COMPASS, "&6&lשמירת נקודת צילום", 
                Arrays.asList(ChatColor.GRAY + "שמור מיקום נוכחי לחזרה מהירה.", ChatColor.YELLOW + "לחץ לשמירה.")));

        gui.setItem(21, createGuiItem(Material.ENDER_EYE, "&5&lחזרה לנקודת הצילום", 
                Arrays.asList(ChatColor.GRAY + "טלפורט מיידי למיקום ששמרת.", ChatColor.YELLOW + "לחץ לטלפורט.")));

        gui.setItem(22, createGuiItem(Material.GLOW_INK_SAC, "&b&lאפקט זוהר לצילום (Glowing)", 
                Arrays.asList(ChatColor.GRAY + "נותן לך זוהר קל בחושך.", ChatColor.YELLOW + "לחץ להפעלה.")));

        gui.setItem(23, createGuiItem(Material.POTION, "&f&lמהירות תנועה קלה (Speed)", 
                Arrays.asList(ChatColor.GRAY + "מעניק זריזות להליכה חלקה.", ChatColor.YELLOW + "לחץ להפעלה.")));

        gui.setItem(28, createGuiItem(Material.RED_CONCRETE, "&c&lהכרזת לייב חדש", Arrays.asList(ChatColor.GRAY + "פרסם שידור חי לכל השרת.")));
        gui.setItem(31, createGuiItem(Material.GOLD_INGOT, "&e&lהכרזת הגרלה", Arrays.asList(ChatColor.GRAY + "פרסם הגרלה שווה לקהילה.")));
        gui.setItem(34, createGuiItem(Material.DIAMOND, "&b&lהכרזת סרטון / איוונט", Arrays.asList(ChatColor.GRAY + "פרסם סרטון או איוונט חדש.")));

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
        gui.setItem(16, createGuiItem(Material.GOLD_BLOCK, "&6&lהטבות וגישות ליוצרי תוכן", Arrays.asList(ChatColor.GRAY + "לחץ לצפייה בהטבות.")));
        gui.setItem(22, createGuiItem(Material.RED_BANNER, "&b&lשרת הדיסקורד שלנו", Arrays.asList(ChatColor.GRAY + "קישור:", ChatColor.BLUE + discordLink)));
        player.openInventory(gui);
    }

    private void openPerksGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&6&lPANDAVERSE - הטבות יוצרי תוכן"));
        gui.setItem(11, createGuiItem(Material.DIAMOND, "&b&lתואר ייחודי בשרת ובדיסקורד", Arrays.asList(ChatColor.GRAY + "טאב מיוחד ותפקיד בדיסקורד.")));
        gui.setItem(13, createGuiItem(Material.EMERALD, "&a&lפאנל יוצרים עשיר (/creatorpanel)", Arrays.asList(
                ChatColor.GRAY + "• מעוף אישי ומצב צילום נקי",
                ChatColor.GRAY + "• מצב איוונט וחסינות מוות",
                ChatColor.GRAY + "• הסתרת שחקנים ומזג אוויר אישי",
                ChatColor.GRAY + "• שליחת הכרזות לכל השרת"
        )));
        gui.setItem(15, createGuiItem(Material.GLOWSTONE_DUST, "&e&lחשיפה לקהילה", Arrays.asList(ChatColor.GRAY + "פרסום תכנים בערוצים ייעודיים.")));
        player.openInventory(gui);
    }

    private void openAdminGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&c&lניהול יוצרי תוכן - הנהלה"));
        gui.setItem(13, createGuiItem(Material.BOOK, "&e&lפקודות מתן הרשאה להנהלה", Arrays.asList(
                ChatColor.GRAY + "תואר יוצר תוכן:", ChatColor.YELLOW + "/lp user <שחקן> parent add creator",
                ChatColor.GRAY + "גישה לפאנל המלא:", ChatColor.YELLOW + "/lp user <שחקן> permission set panda.creator.panel true"
        )));
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.contains("PANDAVERSE")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();

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
            
            else if (title.equals(ChatColor.translateAlternateColorCodes('&', "&d&lפאנל יוצרי תוכן - PANDAVERSE"))) {
                UUID uuid = player.getUniqueId();

                if (event.getRawSlot() == 10) {
                    boolean current = player.getAllowFlight();
                    player.setAllowFlight(!current);
                    player.setFlying(!current);
                    sendAlert(player, "&8[&bPandaCreators&8] &7מעוף: " + (!current ? "&aמופעל" : "&cכבוי"));
                    player.closeInventory();
                } 
                else if (event.getRawSlot() == 11) {
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
                else if (event.getRawSlot() == 12) {
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
                else if (event.getRawSlot() == 13) {
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
                else if (event.getRawSlot() == 14) {
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
                else if (event.getRawSlot() == 19) {
                    player.closeInventory();
                    Location loc = player.getLocation();
                    loc.getWorld().spawnParticle(Particle.FIREWORK, loc.add(0, 1, 0), 35, 0.5, 1, 0.5, 0.1);
                    loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);
                    sendAlert(player, "&8[&bPandaCreators&8] &dשגרת זיקוקי קונפטי!");
                }
                else if (event.getRawSlot() == 20) {
                    player.closeInventory();
                    savedCreatorSpots.put(uuid, player.getLocation());
                    sendAlert(player, "&8[&bPandaCreators&8] &aנקודת הצילום נשמרה בהצלחה!");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                }
                else if (event.getRawSlot() == 21) {
                    player.closeInventory();
                    if (savedCreatorSpots.containsKey(uuid)) {
                        player.teleport(savedCreatorSpots.get(uuid));
                        sendAlert(player, "&8[&bPandaCreators&8] &aהוחזרת לנקודת הצילום שלך.");
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    } else {
                        sendAlert(player, "&cטרם שמרת נקודת צילום!");
                    }
                }
                else if (event.getRawSlot() == 22) {
                    player.closeInventory();
                    boolean hasGlowing = player.isGlowing();
                    player.setGlowing(!hasGlowing);
                    sendAlert(player, "&8[&bPandaCreators&8] &7אפקט זוהר: " + (!hasGlowing ? "&aמופעל" : "&cכבוי"));
                }
                else if (event.getRawSlot() == 23) {
                    player.closeInventory();
                    if (player.hasPotionEffect(PotionEffectType.SPEED)) {
                        player.removePotionEffect(PotionEffectType.SPEED);
                        sendAlert(player, "&8[&bPandaCreators&8] &7מהירות הליכה הוחזרה לרגיל.");
                    } else {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 0, false, false));
                        sendAlert(player, "&8[&bPandaCreators&8] &aהופעלה מהירות תנועה קלה.");
                    }
                }
                else if (event.getRawSlot() == 28 || event.getRawSlot() == 31 || event.getRawSlot() == 34) {
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
                    if (event.getRawSlot() == 31) { type = "giveaway"; nameType = "הגרלה"; }
                    if (event.getRawSlot() == 34) { type = "event"; nameType = "אירוע/סרטון"; }
                    
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

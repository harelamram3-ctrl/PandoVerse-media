package com.pandaverse.creators;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class PandaCreators extends JavaPlugin implements CommandExecutor, Listener {

    private String discordLink;
    private final Set<UUID> pendingApplication = new HashSet<>();
    private final Map<UUID, String> submittedLinks = new HashMap<>();
    
    // מעקב אחרי מצב מעוף וזמני השהיה (Cooldown) להכרזות
    private final Set<UUID> flyingCreators = new HashSet<>();
    private final Map<UUID, Long> broadcastCooldowns = new HashMap<>();
    private final Set<UUID> waitingForBroadcast = new HashSet<>();
    private final Map<UUID, String> broadcastType = new HashMap<>(); // "live", "giveaway", "event"

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        getCommand("creator").setExecutor(this);
        getCommand("creatoradmin").setExecutor(this);
        getCommand("creatorperks").setExecutor(this);
        getCommand("creatorpanel").setExecutor(this); // פקודת התפריט החדשה ליוצרים
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info(ChatColor.GREEN + "PandaCreators has been enabled successfully!");
    }

    private void loadConfigValues() {
        reloadConfig();
        discordLink = getConfig().getString("discord-link", "https://discord.gg/pandaverse");
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

        // פקודת פאנל יוצרי תוכן החדשה
        if (command.getName().equalsIgnoreCase("creatorpanel")) {
            if (!player.hasPermission("panda.creator.panel")) {
                player.sendMessage(ChatColor.RED + "פקודה זו מיועדת ליוצרי התוכן של PANDAVERSE בלבד!");
                return true;
            }
            openCreatorDashboard(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("creatoradmin")) {
            if (!player.hasPermission("panda.admin.creator")) {
                player.sendMessage(ChatColor.RED + "אין לך הרשאה להשתמש בפקודה זו!");
                return true;
            }
            openAdminGUI(player);
            return true;
        }

        return false;
    }

    // תפריט הניהול האישי של יוצר התוכן (Dashboard)
    private void openCreatorDashboard(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&d&lפאנל יוצרי תוכן - PANDAVERSE"));

        // כפתור מעוף
        boolean isFlying = player.getAllowFlight();
        ItemStack flightItem = new ItemStack(isFlying ? Material.FEATHER : Material.ENDER_PEARL);
        ItemMeta flightMeta = flightItem.getItemMeta();
        flightMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b&lמצב מעוף (Flight)"));
        flightMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "סטטוס נוכחי: " + (isFlying ? ChatColor.GREEN + "מופעל" : ChatColor.RED + "כבוי"),
                ChatColor.YELLOW + "לחץ כדי להחליף מצב בלובי!"
        ));
        flightItem.setItemMeta(flightMeta);
        gui.setItem(10, flightItem);

        // כפתור הכרזת לייב
        ItemStack liveItem = new ItemStack(Material.RED_CONCRETE);
        ItemMeta liveMeta = liveItem.getItemMeta();
        liveMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&c&lהכרזת לייב חדש"));
        liveMeta.setLore(Arrays.asList(ChatColor.GRAY + "שלח הודעה מיוחדת לכל השרת", ChatColor.GRAY + "שאתה בשידור חי עכשיו!"));
        liveItem.setItemMeta(liveMeta);
        gui.setItem(12, liveItem);

        // כפתור הכרזת הגרלה
        ItemStack giveawayItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta giveawayMeta = giveawayItem.getItemMeta();
        giveawayMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lהכרזת הגרלה"));
        giveawayMeta.setLore(Arrays.asList(ChatColor.GRAY + "פרסם הגרלה שווה לקהילה", ChatColor.GRAY + "ישירות דרך הצ'אט."));
        giveawayItem.setItemMeta(giveawayMeta);
        gui.setItem(14, giveawayItem);

        // כפתור הכרזת אירוע / סרטון
        ItemStack eventItem = new ItemStack(Material.DIAMOND);
        ItemMeta eventMeta = eventItem.getItemMeta();
        eventMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b&lהכרזת סרטון / אירוע"));
        eventMeta.setLore(Arrays.asList(ChatColor.GRAY + "שתף סרטון חדש שעלם", ChatColor.GRAY + "או אירוע קרוב בשרת."));
        eventItem.setItemMeta(eventMeta);
        gui.setItem(16, eventItem);

        player.openInventory(gui);
    }

    private void openPlayerCreatorGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&b&lPANDAVERSE - יוצרי תוכן"));

        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta paperMeta = paper.getItemMeta();
        paperMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lדרישות הסף שלנו"));
        List<String> rawReqs = getConfig().getStringList("requirements");
        List<String> lore = new ArrayList<>();
        for (String req : rawReqs) {
            lore.add(ChatColor.translateAlternateColorCodes('&', req));
        }
        lore.add("");
        lore.add(ChatColor.GREEN + "לחץ למטה כדי להגיש בקשה בתוך המשחק!");
        paperMeta.setLore(lore);
        paper.setItemMeta(paperMeta);
        gui.setItem(10, paper);

        ItemStack applyItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta applyMeta = applyItem.getItemMeta();
        applyMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a&lהגש בקשה להיות יוצר תוכן"));
        applyMeta.setLore(Collections.singletonList(ChatColor.GRAY + "לחץ כדי להתחיל בתהליך שליחת הקישור לערוץ שלך."));
        applyItem.setItemMeta(applyMeta);
        gui.setItem(13, applyItem);

        ItemStack perksItem = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta perksMeta = perksItem.getItemMeta();
        perksMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6&lהטבות וגישות ליוצרי תוכן"));
        perksMeta.setLore(Arrays.asList(ChatColor.GRAY + "לחץ כדי לראות מה יוצרי", ChatColor.GRAY + "התוכן המיוחדים שלנו מקבלים!"));
        perksItem.setItemMeta(perksMeta);
        gui.setItem(16, perksItem);

        ItemStack discordItem = new ItemStack(Material.RED_BANNER);
        ItemMeta discordMeta = discordItem.getItemMeta();
        discordMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b&lשרת הדיסקורד שלנו"));
        discordMeta.setLore(Arrays.asList(ChatColor.GRAY + "לחץ לקבלת קישור לדיסקורד:", ChatColor.BLUE + discordLink));
        discordItem.setItemMeta(discordMeta);
        gui.setItem(22, discordItem);

        player.openInventory(gui);
    }

    private void openPerksGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&6&lPANDAVERSE - הטבות יוצרי תוכן"));

        ItemStack rank = new ItemStack(Material.DIAMOND);
        ItemMeta rankMeta = rank.getItemMeta();
        rankMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b&lתואר ייחודי בשרת ובדיסקורד"));
        rankMeta.setLore(Arrays.asList(ChatColor.GRAY + "גישה לטאב המיוחד ולפאנל", ChatColor.GRAY + "היוצרים בשרת הדיסקורד."));
        rank.setItemMeta(rankMeta);
        gui.setItem(11, rank);

        ItemStack access = new ItemStack(Material.EMERALD);
        ItemMeta accessMeta = access.getItemMeta();
        accessMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a&lפאנל יוצרים מתקדם (/creatorpanel)"));
        accessMeta.setLore(Arrays.asList(ChatColor.GRAY + "גישה למעוף אישי בלובי ויכולת", ChatColor.GRAY + "לפרסם לייבים והגרלות לכל השרת!"));
        access.setItemMeta(accessMeta);
        gui.setItem(13, access);

        ItemStack exposure = new ItemStack(Material.GLOWSTONE_DUST);
        ItemMeta exposureMeta = exposure.getItemMeta();
        exposureMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lחשיפה לקהילה שלנו"));
        exposureMeta.setLore(Arrays.asList(ChatColor.GRAY + "פרסום השידורים והסרטונים", ChatColor.GRAY + "שלך בערוצים הייעודיים בשרת."));
        exposure.setItemMeta(exposureMeta);
        gui.setItem(15, exposure);

        player.openInventory(gui);
    }

    private void openAdminGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&c&lניהול יוצרי תוכן - הנהלה"));
        
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lפקודות מתן הרשאה להנהלה"));
        infoMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "תואר יוצר תוכן:", ChatColor.YELLOW + "/lp user <שחקן> parent add creator",
                ChatColor.GRAY + "גישה לפאנל ולפיצ'רים:", ChatColor.YELLOW + "/lp user <שחקן> permission set panda.creator.panel true"
        ));
        info.setItemMeta(infoMeta);
        gui.setItem(13, info);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.contains("PANDAVERSE")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();

            // לחיצה בתפריט הראשי של יוצרי התוכן
            if (title.equals(ChatColor.translateAlternateColorCodes('&', "&b&lPANDAVERSE - יוצרי תוכן"))) {
                if (event.getRawSlot() == 13) {
                    player.closeInventory();
                    pendingApplication.add(player.getUniqueId());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &aאנא כתוב כעת בצ'אט את הקישור לערוץ היוטיוב / טיקטוק / שידור שלך:"));
                } else if (event.getRawSlot() == 16) {
                    openPerksGUI(player);
                } else if (event.getRawSlot() == 22) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &7קישור הדיסקורד: &b" + discordLink));
                    player.closeInventory();
                }
            }
            
            // לחיצה בפאנל האישי של יוצר התוכן (/creatorpanel)
            else if (title.equals(ChatColor.translateAlternateColorCodes('&', "&d&lפאנל יוצרי תוכן - PANDAVERSE"))) {
                if (event.getRawSlot() == 10) { // מעוף
                    boolean current = player.getAllowFlight();
                    player.setAllowFlight(!current);
                    player.setFlying(!current);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &7מצב מעוף שונה ל: " + (!current ? "&aמופעל" : "&cכבוי")));
                    player.closeInventory();
                } 
                else if (event.getRawSlot() == 12 || event.getRawSlot() == 14 || event.getRawSlot() == 16) {
                    // בדיקת Cooldown (השהיה של 5 דקות בין הכרזות כדי למנוע ספאם)
                    long cooldownTime = 300 * 1000L; // 5 דקות במילישניות
                    if (broadcastCooldowns.containsKey(player.getUniqueId())) {
                        long timeLeft = (broadcastCooldowns.get(player.getUniqueId()) + cooldownTime) - System.currentTimeMillis();
                        if (timeLeft > 0) {
                            long minutesLeft = timeLeft / 60000 + 1;
                            player.closeInventory();
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cאתה חייב להמתין עוד " + minutesLeft + " דקות לפני שליחת הכרזה נוספת!"));
                            return;
                        }
                    }

                    player.closeInventory();
                    waitingForBroadcast.add(player.getUniqueId());
                    
                    String type = "live";
                    String nameType = "לייב";
                    if (event.getRawSlot() == 14) { type = "giveaway"; nameType = "הגרלה"; }
                    if (event.getRawSlot() == 16) { type = "event"; nameType = "אירוע/סרטון"; }
                    
                    broadcastType.put(player.getUniqueId(), type);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &aאנא כתוב כעת בצ'אט את ההודעה / הקישור עבור ה" + nameType + " שלך:"));
                }
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        
        // טיפול בהגשת בקשה להפוך ליוצר תוכן
        if (pendingApplication.contains(player.getUniqueId())) {
            event.setCancelled(true);
            String link = event.getMessage();
            pendingApplication.remove(player.getUniqueId());
            submittedLinks.put(player.getUniqueId(), link);

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &aהבקשה שלך נשלחה בהצלחה להנהלה!"));

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("panda.admin.creator")) {
                    online.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[בקשת יוצר תוכן] &fהשחקן &b" + player.getName() + " &fהגיש בקשה!"));
                    online.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7קישור: &e" + link));
                }
            }
            return;
        }

        // טיפול בהכרזות מיוחדות של יוצרי תוכן בצ'אט השרת
        if (waitingForBroadcast.contains(player.getUniqueId())) {
            event.setCancelled(true);
            waitingForBroadcast.remove(player.getUniqueId());
            String message = event.getMessage();
            String type = broadcastType.getOrDefault(player.getUniqueId(), "live");
            broadcastType.remove(player.getUniqueId());

            // עדכון זמן Cooldown
            broadcastCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

            // בניית ההודעה המעוצבת לכל השרת
            String header = "&8&l=====================================";
            String titleMsg = "";
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

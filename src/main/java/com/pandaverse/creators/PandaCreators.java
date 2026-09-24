package com.pandaverse.creators;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
    
    // מעקב אחרי מצבים של יוצרי תוכן
    private final Set<UUID> flyingCreators = new HashSet<>();
    private final Set<UUID> photoModeCreators = new HashSet<>();
    private final Set<UUID> eventModeCreators = new HashSet<>();
    
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

    // פאנל יוצרי התוכן המעודכן
    private void openCreatorDashboard(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&d&lפאנל יוצרי תוכן - PANDAVERSE"));

        // 1. כפתור מעוף
        boolean isFlying = player.getAllowFlight();
        ItemStack flightItem = new ItemStack(isFlying ? Material.FEATHER : Material.ENDER_PEARL);
        ItemMeta flightMeta = flightItem.getItemMeta();
        flightMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b&lמצב מעוף (Flight)"));
        flightMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "סטטוס: " + (isFlying ? ChatColor.GREEN + "מופעל" : ChatColor.RED + "כבוי"),
                ChatColor.YELLOW + "לחץ כדי להפעיל או לכבות מעוף."
        ));
        flightItem.setItemMeta(flightMeta);
        gui.setItem(10, flightItem);

        // 2. כפתור מצב צילום (ניקוי צ'אט + בוקר אישי)
        boolean isPhoto = photoModeCreators.contains(player.getUniqueId());
        ItemStack photoItem = new ItemStack(isPhoto ? Material.COMPARATOR : Material.CLOCK);
        ItemMeta photoMeta = photoItem.getItemMeta();
        photoMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lמצב צילום (Photo Mode)"));
        photoMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "סטטוס: " + (isPhoto ? ChatColor.GREEN + "מופעל" : ChatColor.RED + "כבוי"),
                ChatColor.GRAY + "מנקה את הצ'אט וקובע לך בוקר אישי",
                ChatColor.GRAY + "בעולם בלי להשפיע על אחרים.",
                ChatColor.YELLOW + "לחץ להפעלה / כיבוי."
        ));
        photoItem.setItemMeta(photoMeta);
        gui.setItem(11, photoItem);

        // 3. כפתור מצב איוונט (אל-מוות + אפקטים)
        boolean isEvent = eventModeCreators.contains(player.getUniqueId());
        ItemStack eventModeItem = new ItemStack(isEvent ? Material.TOTEM_OF_UNDYTHING : Material.SHIELD);
        ItemMeta eventModeMeta = eventModeItem.getItemMeta();
        eventModeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a&lמצב איוונט (God Mode & Perks)"));
        eventModeMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "סטטוס: " + (isEvent ? ChatColor.GREEN + "מופעל" : ChatColor.RED + "כבוי"),
                ChatColor.GRAY + "מונע מכל אחד להרוג אותך",
                ChatColor.GRAY + "ומעניק ראיית לילה ואפקטים.",
                ChatColor.YELLOW + "לחץ להפעלה / כיבוי."
        ));
        eventModeItem.setItemMeta(eventModeMeta);
        gui.setItem(12, eventModeItem);

        // 4. כפתורי הכרזות (לייב, הגרלה, אירוע)
        ItemStack liveItem = new ItemStack(Material.RED_CONCRETE);
        ItemMeta liveMeta = liveItem.getItemMeta();
        liveMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&c&lהכרזת לייב חדש"));
        liveMeta.setLore(Arrays.asList(ChatColor.GRAY + "שלח הודעת לייב מודגשת לכל השרת."));
        liveItem.setItemMeta(liveMeta);
        gui.setItem(14, liveItem);

        ItemStack giveawayItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta giveawayMeta = giveawayItem.getItemMeta();
        giveawayMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lהכרזת הגרלה"));
        giveawayMeta.setLore(Arrays.asList(ChatColor.GRAY + "פרסם הגרלה לקהילה בצ'אט."));
        giveawayItem.setItemMeta(giveawayMeta);
        gui.setItem(15, giveawayItem);

        ItemStack announcementItem = new ItemStack(Material.DIAMOND);
        ItemMeta announcementMeta = announcementItem.getItemMeta();
        announcementMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b&lהכרזת סרטון / אירוע"));
        announcementMeta.setLore(Arrays.asList(ChatColor.GRAY + "פרסם סרטון או איוונט חדש."));
        announcementItem.setItemMeta(announcementMeta);
        gui.setItem(16, announcementItem);

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
        perksMeta.setLore(Arrays.asList(ChatColor.GRAY + "לחץ כדי לראות מה יוצרי", ChatColor.GRAY + "התוכן שלנו מקבלים!"));
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
        accessMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "• מעוף אישי בלובי ובשרת",
                ChatColor.GRAY + "• מצב צילום (ניקוי צ'אט + בוקר אישי)",
                ChatColor.GRAY + "• מצב איוונט (חסינות מפני מוות ואפקטים)",
                ChatColor.GRAY + "• שליחת הכרזות לייב והגרלות לכל השרת"
        ));
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
            
            else if (title.equals(ChatColor.translateAlternateColorCodes('&', "&d&lפאנל יוצרי תוכן - PANDAVERSE"))) {
                // 1. כפתור מעוף
                if (event.getRawSlot() == 10) {
                    boolean current = player.getAllowFlight();
                    player.setAllowFlight(!current);
                    player.setFlying(!current);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &7מצב מעוף שונה ל: " + (!current ? "&aמופעל" : "&cכבוי")));
                    player.closeInventory();
                } 
                // 2. כפתור מצב צילום
                else if (event.getRawSlot() == 11) {
                    UUID uuid = player.getUniqueId();
                    if (photoModeCreators.contains(uuid)) {
                        photoModeCreators.remove(uuid);
                        player.resetPlayerTime(); // מחזיר לזמן הרגיל של השרת
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &cמצב צילום כובה. הזמן הוחזר לרגיל."));
                    } else {
                        photoModeCreators.add(uuid);
                        player.setPlayerTime(0L, false); // קובע שעות בוקר (0) רק לשחקן הזה!
                        // ניקוי צ'אט אישי על ידי שליחת רווחים ריקים
                        for (int i = 0; i < 50; i++) {
                            player.sendMessage("");
                        }
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &eמצב צילום הופעל! הצ'אט שלך נוקה והזמן הוגדר לבוקר."));
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    }
                    player.closeInventory();
                }
                // 3. כפתור מצב איוונט
                else if (event.getRawSlot() == 12) {
                    UUID uuid = player.getUniqueId();
                    if (eventModeCreators.contains(uuid)) {
                        eventModeCreators.remove(uuid);
                        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                        player.removePotionEffect(PotionEffectType.RESISTANCE);
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &cמצב איוונט כובה. ההגנות והאפקטים הוסרו."));
                    } else {
                        eventModeCreators.add(uuid);
                        // מעניק ראיית לילה ועמידות לנזק כל עוד המצב פעיל (אינסופי עד לכיבוי)
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &aמצב איוונט הופעל! אתה כעת חסין ממוות וקיבלת ראיית לילה."));
                        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
                    }
                    player.closeInventory();
                }
                // 4, 5, 6. הכרזות (לייב, הגרלה, סרטון)
                else if (event.getRawSlot() == 14 || event.getRawSlot() == 15 || event.getRawSlot() == 16) {
                    long cooldownTime = 300 * 1000L; // 5 דקות השהיה
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
                    if (event.getRawSlot() == 15) { type = "giveaway"; nameType = "הגרלה"; }
                    if (event.getRawSlot() == 16) { type = "event"; nameType = "אירוע/סרטון"; }
                    
                    broadcastType.put(player.getUniqueId(), type);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &aאנא כתוב כעת בצ'אט את ההודעה / הקישור עבור ה" + nameType + " שלך:"));
                }
            }
        }
    }

    // מניעת נזק מיוצרי תוכן שנמצאים במצב איוונט
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (eventModeCreators.contains(player.getUniqueId())) {
                event.setCancelled(true); // מבטל לחלוטין כל נזק (נפילה, מפלצות, שחקנים אחרים וכו')
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

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &aהבקשה שלך נשלחה בהצלחה להנהלה!"));

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

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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        getCommand("creator").setExecutor(this);
        getCommand("creatoradmin").setExecutor(this);
        getCommand("creatorperks").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info(ChatColor.GREEN + "PandaCreators (Ultimate Edition with Perks) has been enabled!");
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

    // GUI ראשי לשחקנים
    private void openPlayerCreatorGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&b&lPANDAVERSE - יוצרי תוכן"));

        // פריט דרישות (משמאל)
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

        // כפתור הגשת בקשה (באמצע)
        ItemStack applyItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta applyMeta = applyItem.getItemMeta();
        applyMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a&lהגש בקשה להיות יוצר תוכן"));
        applyMeta.setLore(Collections.singletonList(ChatColor.GRAY + "לחץ כדי להתחיל בתהליך שליחת הקישור לערוץ שלך."));
        applyItem.setItemMeta(applyMeta);
        gui.setItem(13, applyItem);

        // כפתור צפייה בהטבות וגישות
        ItemStack perksItem = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta perksMeta = perksItem.getItemMeta();
        perksMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6&lהטבות וגישות ליוצרי תוכן"));
        perksMeta.setLore(Arrays.asList(ChatColor.GRAY + "לחץ כדי לראות מה יוצרי", ChatColor.GRAY + "התוכן המיוחדים שלנו מקבלים!"));
        perksItem.setItemMeta(perksMeta);
        gui.setItem(16, perksItem);

        // כפתור דיסקורד למטה
        ItemStack discordItem = new ItemStack(Material.RED_BANNER);
        ItemMeta discordMeta = discordItem.getItemMeta();
        discordMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b&lשרת הדיסקורד שלנו"));
        discordMeta.setLore(Arrays.asList(ChatColor.GRAY + "לחץ לקבלת קישור לדיסקורד:", ChatColor.BLUE + discordLink));
        discordItem.setItemMeta(discordMeta);
        gui.setItem(22, discordItem);

        player.openInventory(gui);
    }

    // GUI הטבות וגישות ליוצרי תוכן
    private void openPerksGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&6&lPANDAVERSE - הטבות יוצרי תוכן"));

        // הטבה 1: תואר מיוחד
        ItemStack rank = new ItemStack(Material.DIAMOND);
        ItemMeta rankMeta = rank.getItemMeta();
        rankMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b&lתואר ייחודי בשרת ובדיסקורד"));
        rankMeta.setLore(Arrays.asList(ChatColor.GRAY + "גישה לטאב המיוחד ולפאנל", ChatColor.GRAY + "היוצרים בשרת הדיסקורד."));
        rank.setItemMeta(rankMeta);
        gui.setItem(11, rank);

        // הטבה 2: גישות מתקדמות
        ItemStack access = new ItemStack(Material.EMERALD);
        ItemMeta accessMeta = access.getItemMeta();
        accessMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a&lגישות ופקודות נוחות"));
        accessMeta.setLore(Arrays.asList(ChatColor.GRAY + "עזרה בקידום תכנים,", ChatColor.GRAY + "אפשרות להגרלות ושיתופי פעולה."));
        access.setItemMeta(accessMeta);
        gui.setItem(13, access);

        // הטבה 3: חשיפה בקהילה
        ItemStack exposure = new ItemStack(Material.GLOWSTONE_DUST);
        ItemMeta exposureMeta = exposure.getItemMeta();
        exposureMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lחשיפה לקהילה שלנו"));
        exposureMeta.setLore(Arrays.asList(ChatColor.GRAY + "פרסום השידורים והסרטונים", ChatColor.GRAY + "שלך בערוצים הייעודיים בשרת."));
        exposure.setItemMeta(exposureMeta);
        gui.setItem(15, exposure);

        player.openInventory(gui);
    }

    // GUI ניהול להנהלה
    private void openAdminGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&c&lניהול יוצרי תוכן - הנהלה"));
        
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lפקודת מתן הרשאה מהירה"));
        infoMeta.setLore(Arrays.asList(ChatColor.GRAY + "השתמש בפקודה הבאה ב-LuckPerms:", ChatColor.YELLOW + "/lp user <שחקן> parent add creator"));
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

            // לחיצה בתפריט הראשי
            if (title.equals(ChatColor.translateAlternateColorCodes('&', "&b&lPANDAVERSE - יוצרי תוכן"))) {
                if (event.getRawSlot() == 13) { // הגשת בקשה
                    player.closeInventory();
                    pendingApplication.add(player.getUniqueId());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &aאנא כתוב כעת בצ'אט את הקישור לערוץ היוטיוב / טיקטוק / שידור שלך:"));
                } else if (event.getRawSlot() == 16) { // פתיחת תפריט הטבות
                    openPerksGUI(player);
                } else if (event.getRawSlot() == 22) { // דיסקורד
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &7קישור הדיסקורד: &b" + discordLink));
                    player.closeInventory();
                }
            }
        }
    }

    // קליטת הקישור שהשחקן רושם בצ'אט
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (pendingApplication.contains(player.getUniqueId())) {
            event.setCancelled(true);
            String link = event.getMessage();
            pendingApplication.remove(player.getUniqueId());
            submittedLinks.put(player.getUniqueId(), link);

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &aהבקשה שלך נשלחה בהצלחה להנהלה!"));

            // הודעה לכל המנהלים המחוברים
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("panda.admin.creator")) {
                    online.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[בקשת יוצר תוכן] &fהשחקן &b" + player.getName() + " &fהגיש בקשה!"));
                    online.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7קישור: &e" + link));
                }
            }
        }
    }
}

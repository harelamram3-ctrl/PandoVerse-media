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
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info(ChatColor.GREEN + "PandaCreators (Ultimate Edition) has been enabled successfully!");
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

        // פריט דרישות
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
        gui.setItem(11, paper);

        // כפתור הגשת בקשה
        ItemStack applyItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta applyMeta = applyItem.getItemMeta();
        applyMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a&lהגש בקשה להיות יוצר תוכן"));
        applyMeta.setLore(Collections.singletonList(ChatColor.GRAY + "לחץ כדי להתחיל בתהליך שליחת הקישור לערוץ שלך."));
        applyItem.setItemMeta(applyMeta);
        gui.setItem(13, applyItem);

        // כפתור דיסקורד
        ItemStack discordItem = new ItemStack(Material.RED_BANNER);
        ItemMeta discordMeta = discordItem.getItemMeta();
        discordMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b&lשרת הדיסקורד שלנו"));
        discordMeta.setLore(Arrays.asList(ChatColor.GRAY + "לחץ לקבלת קישור לדיסקורד:", ChatColor.BLUE + discordLink));
        discordItem.setItemMeta(discordMeta);
        gui.setItem(15, discordItem);

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
        if (title.contains("PANDAVERSE - יוצרי תוכן") || title.contains("ניהול יוצרי תוכן - הנהלה")) {
            event.setCancelled();
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();

            if (event.getRawSlot() == 15 && title.contains("PANDAVERSE - יוצרי תוכן")) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &7קישור הדיסקורד: &b" + discordLink));
                player.closeInventory();
            } else if (event.getRawSlot() == 13 && title.contains("PANDAVERSE - יוצרי תוכן")) {
                player.closeInventory();
                pendingApplication.add(player.getUniqueId());
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &aאנא כתוב כעת בצ'אט את הקישור לערוץ היוטיוב / טיקטוק / שידור שלך:"));
            }
        }
    }

    // קליטת הקישור שהשחקן רושם בצ'אט
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (pendingApplication.contains(player.getUniqueId())) {
            event.setCancelled();
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

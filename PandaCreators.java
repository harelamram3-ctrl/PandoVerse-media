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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class PandaCreators extends JavaPlugin implements CommandExecutor, Listener {

    private String discordLink;

    @Override
    public void onEnable() {
        // טעינת קובץ הקונפיגורציה ברירת מחדל
        saveDefaultConfig();
        discordLink = getConfig().getString("discord-link", "https://discord.gg/pandaverse");

        // רישום פקודות ומאזינים (Listeners)
        getCommand("creator").setExecutor(this);
        getCommand("creatoradmin").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info(ChatColor.GREEN + "PandaCreators plugin has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info(ChatColor.RED + "PandaCreators plugin has been disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        // פקודת /creator לשחקנים
        if (command.getName().equalsIgnoreCase("creator")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("פקודה זו מיועדת לשחקנים בלבד.");
                return true;
            }
            Player player = (Player) sender;
            openPlayerCreatorInfo(player);
            return true;
        }

        // פקודת /creatoradmin להנהלה
        if (command.getName().equalsIgnoreCase("creatoradmin")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("פקודה זו מיועדת לשחקנים בלבד.");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("panda.admin.creator")) {
                player.sendMessage(ChatColor.RED + "אין לך הרשאה להשתמש בפקודה זו!");
                return true;
            }
            openAdminGUI(player);
            return true;
        }

        return false;
    }

    // פתיחת GUI מידע / הגשות לשחקן רגיל
    private void openPlayerCreatorInfo(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&b&lPANDAVERSE - יוצרי תוכן"));

        // פריט דרישות
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta paperMeta = paper.getItemMeta();
        paperMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lדרישות להפוך ליוצר תוכן"));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "חובה להיות בשרת הדיסקורד שלנו!");
        lore.add(ChatColor.GRAY + "העלאת תוכן איכותי (סרטונים / שידורים).");
        lore.add(ChatColor.GRAY + "מינימום עוקבים / צפיות סביר.");
        lore.add("");
        lore.add(ChatColor.GREEN + "רוצה להגיש בקשה? פתח טיקט בדיסקורד!");
        paperMeta.setLore(lore);
        paper.setItemMeta(paperMeta);
        gui.setItem(11, paper);

        // פריט דיסקורד לחזרה מהירה
        ItemStack discordItem = new ItemStack(Material.RED_BANNER);
        ItemMeta discordMeta = discordItem.getItemMeta();
        discordMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b&lקישור לשרת הדיסקורד"));
        List<String> discordLore = new ArrayList<>();
        discordLore.add(ChatColor.GRAY + "לחץ כדי להעתיק / להיכנס לקישור:");
        discordLore.add(ChatColor.BLUE + discordLink);
        discordMeta.setLore(discordLore);
        discordItem.setItemMeta(discordMeta);
        gui.setItem(15, discordItem);

        player.openInventory(gui);
    }

    // פתיחת GUI ניהול להנהלה
    private void openAdminGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', "&c&lניהול יוצרי תוכן - הנהלה"));
        
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lפאנל ניהול יוצרי תוכן"));
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "כדי לתת רנק יוצר תוכן ב-LuckPerms השתמש ב:");
        infoLore.add(ChatColor.YELLOW + "/lp user <שחקן> parent add creator");
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        gui.setItem(13, info);

        player.openInventory(gui);
    }

    // מניעת לקיחת פריטים מה-GUI
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.contains("PANDAVERSE - יוצרי תוכן") || title.contains("ניהול יוצרי תוכן - הנהלה")) {
            event.setCancelled(true);
            
            if (event.getRawSlot() == 15 && event.getView().getTitle().contains("PANDAVERSE - יוצרי תוכן")) {
                Player player = (Player) event.getWhoClicked();
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bPandaCreators&8] &7קישור הדיסקורד שלנו: &b" + discordLink));
                player.closeInventory();
            }
        }
    }
}

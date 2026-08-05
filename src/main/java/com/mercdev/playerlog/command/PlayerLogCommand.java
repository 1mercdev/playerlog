package com.mercdev.playerlog.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.mercdev.playerlog.PlayerLogPlugin;
import com.mercdev.playerlog.storage.LogEntry;
import com.mercdev.playerlog.storage.LogStorage;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PlayerLogCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("log", "readlog", "clearlog");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final UUID CONSOLE_UUID = new UUID(0, 0);

    private final PlayerLogPlugin plugin;
    private final LogStorage storage;

    public PlayerLogCommand(PlayerLogPlugin plugin, LogStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "log" -> handleLog(sender, args);
            case "readlog" -> handleReadLog(sender, args);
            case "clearlog" -> handleClearLog(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    // ---------- log ----------

    private void handleLog(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerlog.log")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /playerlog log <player> <message...>");
            return;
        }

        String targetName = args[1];
        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        UUID authorUuid = (sender instanceof Player p) ? p.getUniqueId() : CONSOLE_UUID;
        String authorName = (sender instanceof Player p) ? p.getName() : "CONSOLE";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = resolvePlayer(targetName);
            if (target == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "That player has never played on this server."));
                return;
            }
            try {
                storage.addEntry(target.getUniqueId(), targetName, authorUuid, authorName, message);
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.GREEN + "Logged note for " + ChatColor.WHITE + targetName));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to write log entry: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Failed to save log entry, check console."));
            }
        });
    }

    // ---------- readlog ----------

    private void handleReadLog(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerlog.read")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /playerlog readlog <player> [page]");
            return;
        }

        String targetName = args[1];
        int page;
        if (args.length >= 3) {
            try {
                page = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Page must be a number.");
                return;
            }
        } else {
            page = 1;
        }
        final int finalPage = page;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = resolvePlayer(targetName);
            if (target == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "That player has never played on this server."));
                return;
            }

            List<LogEntry> entries;
            try {
                entries = storage.readPage(target.getUniqueId(), finalPage);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to read log: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Failed to read log, check console."));
                return;
            }

            int totalPages = storage.getPageCount(target.getUniqueId());
            int totalEntries = storage.getEntryCount(target.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (totalEntries == 0) {
                    sender.sendMessage(ChatColor.YELLOW + "No log entries for " + targetName + ".");
                    return;
                }
                sender.sendMessage(ChatColor.GOLD + "--- Log: " + targetName +
                        ChatColor.GRAY + " (page " + finalPage + "/" + totalPages +
                        ", " + totalEntries + " entries) ---");
                for (LogEntry entry : entries) {
                    sender.sendMessage(
                            ChatColor.DARK_GRAY + "#" + entry.id() + " " +
                            ChatColor.GRAY + DATE_FORMAT.format(new Date(entry.timestampMillis())) +
                            ChatColor.AQUA + " <" + entry.authorName() + "> " +
                            ChatColor.WHITE + entry.message()
                    );
                }
                if (finalPage < totalPages) {
                    sender.sendMessage(ChatColor.GRAY + "Use /playerlog readlog " + targetName +
                            " " + (finalPage + 1) + " for more.");
                }
            });
        });
    }

    // ---------- clearlog ----------

    private void handleClearLog(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerlog.clear")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /playerlog clearlog <player>");
            return;
        }

        String targetName = args[1];

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = resolvePlayer(targetName);
            if (target == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "That player has never played on this server."));
                return;
            }

            int removed;
            try {
                removed = storage.clear(target.getUniqueId());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to clear log: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Failed to clear log, check console."));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(ChatColor.GREEN + "Cleared " + removed +
                            " entries for " + targetName + "."));
            plugin.getLogger().info(sender.getName() + " cleared " + removed +
                    " log entries for " + targetName);
        });
    }

    // ---------- shared helpers ----------

    // helper if player's offline
    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore()) return offline;
        return null;
    }

    // helper to send command usage!
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- PlayerLog ---");
        sender.sendMessage(ChatColor.YELLOW + "/playerlog log <player> <message...>");
        sender.sendMessage(ChatColor.YELLOW + "/playerlog readlog <player> [page]");
        sender.sendMessage(ChatColor.YELLOW + "/playerlog clearlog <player>");
    }

    // ---------- tab completion ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (!SUBCOMMANDS.contains(sub)) return Collections.emptyList();

            Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            names.addAll(storage.getKnownPlayerNames());
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }

            String prefix = args[1].toLowerCase(Locale.ROOT);
            return names.stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("readlog")) {
            Player online = Bukkit.getPlayerExact(args[1]);
            OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayer(args[1]);
            int totalPages = storage.getPageCount(target.getUniqueId());
            return IntStream.rangeClosed(1, totalPages)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}

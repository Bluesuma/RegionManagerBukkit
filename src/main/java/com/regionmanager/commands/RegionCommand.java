package com.regionmanager.commands;

import com.regionmanager.RegionManagerPlugin;
import com.regionmanager.region.Region;
import com.regionmanager.prediction.MovementPredictor;
import com.regionmanager.prediction.PlayerMovementData;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Команды для управления регионами
 */
public class RegionCommand implements CommandExecutor, TabCompleter {
    
    private final RegionManagerPlugin plugin;
    
    /**
     * Конструктор команды
     */
    public RegionCommand(RegionManagerPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "info":
                showInfo(sender);
                break;
            case "stats":
                showStats(sender);
                break;
            case "reload":
                reloadConfig(sender);
                break;
            case "optimize":
                optimizeRegions(sender);
                break;
            case "list":
                listRegions(sender);
                break;
            case "player":
                if (args.length > 1) {
                    showPlayerRegion(sender, args[1]);
                } else {
                    sender.sendMessage(ChatColor.RED + "Использование: /region player <игрок>");
                }
                break;
            case "debug":
                toggleDebug(sender);
                break;
            case "create":
                if (args.length > 1) {
                    createRegionForPlayer(sender, args[1]);
                } else if (sender instanceof Player) {
                    createRegionForPlayer(sender, sender.getName());
                } else {
                    sender.sendMessage(ChatColor.RED + "Использование: /region create [игрок]");
                }
                break;
            case "force":
                if (args.length > 1) {
                    createForcedRegionForPlayer(sender, args[1]);
                } else if (sender instanceof Player) {
                    createForcedRegionForPlayer(sender, sender.getName());
                } else {
                    sender.sendMessage(ChatColor.RED + "Использование: /region force [игрок]");
                }
                break;
            case "predict":
                if (args.length > 1) {
                    showPredictionInfo(sender, args[1]);
                } else if (sender instanceof Player) {
                    showPredictionInfo(sender, sender.getName());
                } else {
                    sender.sendMessage(ChatColor.RED + "Использование: /region predict [игрок]");
                }
                break;
            case "unload":
                if (args.length > 1) {
                    forceUnloadRegion(sender, args[1]);
                } else {
                    sender.sendMessage(ChatColor.RED + "Использование: /region unload <регион>");
                }
                break;
            default:
                showHelp(sender);
                break;
        }
        
        return true;
    }
    
    /**
     * Показать справку по командам
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== RegionManager Команды ===");
        sender.sendMessage(ChatColor.YELLOW + "/region info " + ChatColor.WHITE + "- Информация о плагине");
        sender.sendMessage(ChatColor.YELLOW + "/region stats " + ChatColor.WHITE + "- Статистика регионов");
        sender.sendMessage(ChatColor.YELLOW + "/region list " + ChatColor.WHITE + "- Список активных регионов");
        sender.sendMessage(ChatColor.YELLOW + "/region player <игрок> " + ChatColor.WHITE + "- Регион игрока");
        sender.sendMessage(ChatColor.YELLOW + "/region optimize " + ChatColor.WHITE + "- Принудительная оптимизация");
        sender.sendMessage(ChatColor.YELLOW + "/region reload " + ChatColor.WHITE + "- Перезагрузить конфигурацию");
        sender.sendMessage(ChatColor.YELLOW + "/region debug " + ChatColor.WHITE + "- Переключить отладку");
        sender.sendMessage(ChatColor.YELLOW + "/region create [игрок] " + ChatColor.WHITE + "- Принудительно создать регион");
        sender.sendMessage(ChatColor.YELLOW + "/region force [игрок] " + ChatColor.WHITE + "- Создать принудительный регион");
        sender.sendMessage(ChatColor.YELLOW + "/region predict [игрок] " + ChatColor.WHITE + "- Показать предсказания движения");
        sender.sendMessage(ChatColor.YELLOW + "/region unload <регион> " + ChatColor.WHITE + "- Принудительно выгрузить регион");
    }
    
    /**
     * Показать информацию о плагине
     */
    private void showInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== RegionManager Информация ===");
        sender.sendMessage(ChatColor.YELLOW + "Версия: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "Автор: " + ChatColor.WHITE + "RegionManager");
        sender.sendMessage(ChatColor.YELLOW + "TPS: " + ChatColor.WHITE + 
            String.format("%.2f", plugin.getPerformanceManager().getCurrentTPS()));
        sender.sendMessage(ChatColor.YELLOW + "Активных регионов: " + ChatColor.WHITE + 
            plugin.getRegionManager().getActiveRegionCount());
        sender.sendMessage(ChatColor.YELLOW + "Игроков онлайн: " + ChatColor.WHITE + 
            plugin.getServer().getOnlinePlayers().size());
    }
    
    /**
     * Показать статистику регионов
     */
    private void showStats(CommandSender sender) {
        Map<String, Object> stats = plugin.getRegionManager().getRegionStats();
        
        sender.sendMessage(ChatColor.GOLD + "=== Статистика Регионов ===");
        sender.sendMessage(ChatColor.YELLOW + "Всего регионов: " + ChatColor.WHITE + stats.get("totalRegions"));
        sender.sendMessage(ChatColor.YELLOW + "Активных регионов: " + ChatColor.WHITE + stats.get("activeRegions"));
        sender.sendMessage(ChatColor.YELLOW + "Игроков в регионах: " + ChatColor.WHITE + stats.get("totalPlayers"));
        sender.sendMessage(ChatColor.YELLOW + "Максимум активных регионов: " + ChatColor.WHITE + stats.get("maxActiveRegions"));
        
        // Статистика по регионам
        @SuppressWarnings("unchecked")
        Map<String, Integer> regionPlayerCounts = (Map<String, Integer>) stats.get("regionPlayerCounts");
        if (regionPlayerCounts != null && !regionPlayerCounts.isEmpty()) {
            sender.sendMessage(ChatColor.GOLD + "=== Игроки по регионам ===");
            regionPlayerCounts.forEach((regionId, playerCount) -> {
                sender.sendMessage(ChatColor.YELLOW + regionId + ": " + ChatColor.WHITE + playerCount + " игроков");
            });
        }
    }
    
    /**
     * Показать список активных регионов
     */
    private void listRegions(CommandSender sender) {
        Map<String, Region> regions = plugin.getRegionManager().getRegions();
        int maxPlayers = plugin.getConfig().getInt("regions.max-players-per-region", 20);
        
        sender.sendMessage(ChatColor.GOLD + "=== Активные Регионы ===");
        regions.values().stream()
            .filter(Region::isActive)
            .forEach(region -> {
                Location center = region.getCenter();
                sender.sendMessage(ChatColor.YELLOW + region.getId() + 
                    ChatColor.WHITE + " - " + region.getPlayerCount() + "/" + maxPlayers + 
                    " игроков в " + center.getWorld().getName() + 
                    " (" + center.getBlockX() + ", " + center.getBlockZ() + ")");
            });
    }
    
    /**
     * Показать регион игрока
     */
    private void showPlayerRegion(CommandSender sender, String playerName) {
        Player targetPlayer = plugin.getServer().getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Игрок " + playerName + " не найден");
            return;
        }
        
        Region region = plugin.getRegionManager().getPlayerRegion(targetPlayer);
        if (region == null) {
            sender.sendMessage(ChatColor.YELLOW + "Игрок " + playerName + " не находится в регионе");
            return;
        }
        
        Location center = region.getCenter();
        int maxPlayers = plugin.getConfig().getInt("regions.max-players-per-region", 20);
        
        sender.sendMessage(ChatColor.GOLD + "=== Регион игрока " + playerName + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Регион: " + ChatColor.WHITE + region.getId());
        sender.sendMessage(ChatColor.YELLOW + "Центр: " + ChatColor.WHITE + 
            center.getWorld().getName() + " (" + center.getBlockX() + ", " + center.getBlockZ() + ")");
        sender.sendMessage(ChatColor.YELLOW + "Игроков в регионе: " + ChatColor.WHITE + 
            region.getPlayerCount() + "/" + maxPlayers);
        sender.sendMessage(ChatColor.YELLOW + "Размер региона: " + ChatColor.WHITE + region.getSize() + " блоков");
    }
    
    /**
     * Перезагрузить конфигурацию
     */
    private void reloadConfig(CommandSender sender) {
        if (!sender.hasPermission("regionmanager.admin")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для выполнения этой команды");
            return;
        }
        
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "Конфигурация перезагружена");
    }
    
    /**
     * Принудительная оптимизация регионов
     */
    private void optimizeRegions(CommandSender sender) {
        if (!sender.hasPermission("regionmanager.admin")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для выполнения этой команды");
            return;
        }
        
        plugin.getRegionManager().optimizeRegions();
        sender.sendMessage(ChatColor.GREEN + "Оптимизация регионов выполнена");
    }
    
    /**
     * Переключить отладку
     */
    private void toggleDebug(CommandSender sender) {
        if (!sender.hasPermission("regionmanager.admin")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для выполнения этой команды");
            return;
        }
        
        boolean currentDebug = plugin.getConfig().getBoolean("debug.show-region-info", false);
        plugin.getConfig().set("debug.show-region-info", !currentDebug);
        plugin.saveConfig();
        
        sender.sendMessage(ChatColor.GREEN + "Отладка регионов " + 
            (currentDebug ? "отключена" : "включена"));
    }
    
    /**
     * Принудительно создать регион для игрока
     */
    private void createRegionForPlayer(CommandSender sender, String playerName) {
        if (!sender.hasPermission("regionmanager.admin")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для выполнения этой команды");
            return;
        }

        Player targetPlayer = plugin.getServer().getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Игрок " + playerName + " не найден");
            return;
        }

        Region region = plugin.getRegionManager().findOrCreateRegionForPlayer(targetPlayer);
        if (region != null) {
            sender.sendMessage(ChatColor.GREEN + "Регион для игрока " + playerName + " создан: " + region.getId());
        } else {
            sender.sendMessage(ChatColor.RED + "Не удалось создать регион для игрока " + playerName);
        }
    }

    /**
     * Принудительно создать регион для игрока
     */
    private void createForcedRegionForPlayer(CommandSender sender, String playerName) {
        if (!sender.hasPermission("regionmanager.admin")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для выполнения этой команды");
            return;
        }

        Player targetPlayer = plugin.getServer().getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Игрок " + playerName + " не найден");
            return;
        }

        Region region = plugin.getRegionManager().findOrCreateRegionForPlayer(targetPlayer);
        if (region != null) {
            region.setForcedRegion(true);
            sender.sendMessage(ChatColor.GREEN + "Принудительный регион для игрока " + playerName + " создан: " + region.getId());
        } else {
            sender.sendMessage(ChatColor.RED + "Не удалось создать регион для игрока " + playerName);
        }
    }

    /**
     * Показать информацию о предсказаниях для игрока
     */
    private void showPredictionInfo(CommandSender sender, String playerName) {
        if (!sender.hasPermission("regionmanager.admin")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для выполнения этой команды");
            return;
        }

        Player targetPlayer = plugin.getServer().getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Игрок " + playerName + " не найден");
            return;
        }

        MovementPredictor predictor = plugin.getMovementPredictor();
        if (predictor == null) {
            sender.sendMessage(ChatColor.RED + "Система предиктов не инициализирована");
            return;
        }

        PlayerMovementData movementData = predictor.getPlayerMovementData(targetPlayer);
        if (movementData == null) {
            sender.sendMessage(ChatColor.YELLOW + "Нет данных о движении для игрока " + playerName);
            return;
        }

        Location predictedLocation = predictor.getPredictedLocation(targetPlayer);
        
        sender.sendMessage(ChatColor.GOLD + "=== Предсказания для " + playerName + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Средняя скорость: " + ChatColor.WHITE + 
            String.format("%.2f", movementData.getAverageSpeed()) + " блоков/сек");
        sender.sendMessage(ChatColor.YELLOW + "Последняя скорость: " + ChatColor.WHITE + 
            String.format("%.2f", movementData.getLastSpeed()) + " блоков/сек");
        sender.sendMessage(ChatColor.YELLOW + "Движется: " + ChatColor.WHITE + 
            (movementData.isMoving() ? "Да" : "Нет"));
        sender.sendMessage(ChatColor.YELLOW + "Предсказанная позиция: " + ChatColor.WHITE + 
            String.format("%.1f, %.1f, %.1f", predictedLocation.getX(), predictedLocation.getY(), predictedLocation.getZ()));
        sender.sendMessage(ChatColor.YELLOW + "Размер истории: " + ChatColor.WHITE + movementData.getHistorySize());
    }

    /**
     * Принудительно выгрузить регион
     */
    private void forceUnloadRegion(CommandSender sender, String regionId) {
        if (!sender.hasPermission("regionmanager.admin")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для выполнения этой команды");
            return;
        }

        Map<String, Region> regions = plugin.getRegionManager().getRegions();
        Region region = regions.get(regionId);
        
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Регион " + regionId + " не найден");
            return;
        }

        if (region.getPlayerCount() > 0) {
            sender.sendMessage(ChatColor.RED + "Нельзя выгрузить регион с игроками");
            return;
        }

        region.forceUnload();
        sender.sendMessage(ChatColor.GREEN + "Регион " + regionId + " принудительно выгружен");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subcommands = List.of("info", "stats", "reload", "optimize", "list", "player", "debug");
            for (String subcommand : subcommands) {
                if (subcommand.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subcommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            // Автодополнение имен игроков
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        }
        
        return completions;
    }
} 
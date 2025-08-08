package com.regionmanager.listeners;

import com.regionmanager.RegionManagerPlugin;
import com.regionmanager.region.Region;
import com.regionmanager.prediction.MovementPredictor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Слушатель событий игроков
 * Управляет перемещением игроков между регионами с поддержкой предиктов
 */
public class PlayerListener implements Listener {
    
    private final RegionManagerPlugin plugin;
    private final MovementPredictor movementPredictor;
    private final int regionCheckDistance;
    private final Map<UUID, Boolean> sleepingPlayers = new HashMap<>();
    private final boolean preventSleepRegionChange;
    private final double sleepExpansionMultiplier;
    private final int sleepExpansionBuffer;
    
    /**
     * Конструктор слушателя
     */
    public PlayerListener(RegionManagerPlugin plugin) {
        this.plugin = plugin;
        this.movementPredictor = plugin.getMovementPredictor();
        // Используем настройку из конфигурации для расстояния проверки
        this.regionCheckDistance = plugin.getConfig().getInt("regions.check-distance", 64);
        // Настройки сна
        this.preventSleepRegionChange = plugin.getConfig().getBoolean("sleep.prevent-region-change", true);
        this.sleepExpansionMultiplier = plugin.getConfig().getDouble("sleep.expansion-multiplier", 1.5);
        this.sleepExpansionBuffer = plugin.getConfig().getInt("sleep.expansion-buffer", 16);
    }
    
    /**
     * Обработка входа игрока на сервер
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Найти или создать регион для игрока
        Region region = plugin.getRegionManager().findOrCreateRegionForPlayer(player);
        if (region != null) {
            plugin.getRegionManager().addPlayerToRegion(player, region);
            
            if (plugin.getConfig().getBoolean("debug.show-region-info", false)) {
                player.sendMessage("§aВы присоединились к региону: " + region.getId());
            }
        }
    }
    
    /**
     * Обработка выхода игрока с сервера
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Очистить данные движения игрока
        if (movementPredictor != null) {
            movementPredictor.onPlayerQuit(player);
        }
        
        // Удалить игрока из региона
        plugin.getRegionManager().removePlayerFromRegion(player);
        
        // Очистить флаг сна
        sleepingPlayers.remove(player.getUniqueId());
    }
    
    /**
     * Обработка входа игрока в кровать
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        sleepingPlayers.put(player.getUniqueId(), true);
        
        plugin.getPluginLogger().info("Игрок " + player.getName() + " лег спать в " + player.getLocation());
        
        if (preventSleepRegionChange && plugin.getConfig().getBoolean("debug.show-region-info", false)) {
            player.sendMessage("§eВы легли спать. Регион не изменится при пробуждении.");
        }
    }
    
    /**
     * Обработка выхода игрока из кровати
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        sleepingPlayers.remove(player.getUniqueId());
        
        plugin.getPluginLogger().info("Игрок " + player.getName() + " проснулся в " + player.getLocation());
        
        // При пробуждении проверяем, нужно ли изменить регион
        // Но делаем это более осторожно, чтобы избежать ненужных изменений
        if (preventSleepRegionChange) {
            handleSleepWakeRegionCheck(player, player.getLocation());
        }
    }
    
    /**
     * Обработка перемещения игрока
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (to == null) {
            return;
        }
        
        // Обновить данные движения в предикторе
        if (movementPredictor != null) {
            movementPredictor.onPlayerMove(player, from, to);
        }
        
        // Проверить, изменился ли мир
        if (!from.getWorld().equals(to.getWorld())) {
            handleWorldChange(player, to);
            return;
        }
        
        // Всегда проверяем смену региона при движении
        handleRegionChange(player, to);
    }
    
    /**
     * Обработка телепортации игрока
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        
        if (to == null) {
            return;
        }
        
        // При телепортации всегда принудительно пересчитываем регион
        plugin.getPluginLogger().info("Игрок " + player.getName() + " телепортирован в " + to);
        
        // Удалить игрока из текущего региона
        Region currentRegion = plugin.getRegionManager().getPlayerRegion(player);
        if (currentRegion != null) {
            plugin.getRegionManager().removePlayerFromRegion(player);
            plugin.getPluginLogger().debug("Игрок " + player.getName() + " удален из региона " + currentRegion.getId());
        }
        
        // Найти или создать новый регион
        Region newRegion = plugin.getRegionManager().findOrCreateRegionForPlayer(player);
        if (newRegion != null) {
            plugin.getRegionManager().addPlayerToRegion(player, newRegion);
            
            if (plugin.getConfig().getBoolean("debug.show-region-info", false)) {
                player.sendMessage("§aТелепортация: вы в регионе " + newRegion.getId());
            }
            
            plugin.getPluginLogger().info("Игрок " + player.getName() + " телепортирован в регион " + newRegion.getId());
        } else {
            plugin.getPluginLogger().error("Не удалось создать регион для игрока " + player.getName() + " при телепортации");
        }
    }
    
    /**
     * Проверить, нужно ли проверять смену региона
     */
    private boolean shouldCheckRegionChange(Location from, Location to) {
        // Проверяем чаще для более точного создания регионов
        double distance = from.distance(to);
        return distance >= regionCheckDistance;
    }
    
    /**
     * Обработать смену мира
     */
    private void handleWorldChange(Player player, Location newLocation) {
        Region currentRegion = plugin.getRegionManager().getPlayerRegion(player);
        
        if (currentRegion != null) {
            // Удалить игрока из текущего региона
            plugin.getRegionManager().removePlayerFromRegion(player);
        }
        
        // Найти или создать регион в новом мире
        Region newRegion = plugin.getRegionManager().findOrCreateRegionForPlayer(player);
        if (newRegion != null) {
            plugin.getRegionManager().addPlayerToRegion(player, newRegion);
            
            if (plugin.getConfig().getBoolean("debug.show-region-info", false)) {
                player.sendMessage("§aВы перешли в регион: " + newRegion.getId());
            }
        }
    }
    
    /**
     * Обработать смену региона
     */
    private void handleRegionChange(Player player, Location newLocation) {
        // Проверяем, не спит ли игрок
        if (preventSleepRegionChange && isPlayerSleeping(player)) {
            plugin.getPluginLogger().debug("Игрок " + player.getName() + " спит, пропускаем смену региона");
            return;
        }
        
        Region currentRegion = plugin.getRegionManager().getPlayerRegion(player);
        
        // Подробная отладочная информация
        plugin.getPluginLogger().debug("=== Проверка смены региона для " + player.getName() + " ===");
        plugin.getPluginLogger().debug("Позиция игрока: " + newLocation.getBlockX() + ", " + newLocation.getBlockZ());
        
        if (currentRegion != null) {
            plugin.getPluginLogger().debug("Текущий регион: " + currentRegion.getId());
            plugin.getPluginLogger().debug("Центр региона: " + currentRegion.getCenter().getBlockX() + ", " + currentRegion.getCenter().getBlockZ());
            plugin.getPluginLogger().debug("Размер региона: " + currentRegion.getSize() + " блоков");
            plugin.getPluginLogger().debug("Игрок в регионе: " + currentRegion.contains(newLocation));
        } else {
            plugin.getPluginLogger().debug("У игрока нет текущего региона");
        }
        
        // Проверить, находится ли игрок в пределах текущего региона
        if (currentRegion != null && currentRegion.contains(newLocation)) {
            // Игрок все еще в том же регионе
            plugin.getPluginLogger().debug("Игрок " + player.getName() + " остается в регионе " + currentRegion.getId());
            return;
        }
        
        // Игрок вышел за пределы текущего региона или не находится в регионе
        if (currentRegion != null) {
            plugin.getPluginLogger().info("Игрок " + player.getName() + " вышел за границы региона " + 
                currentRegion.getId() + " (позиция: " + newLocation.getBlockX() + ", " + newLocation.getBlockZ() + ")");
            // Удалить игрока из текущего региона
            plugin.getRegionManager().removePlayerFromRegion(player);
        }
        
        // Найти подходящий регион для новой позиции
        plugin.getPluginLogger().debug("Поиск подходящего региона для позиции " + newLocation.getBlockX() + ", " + newLocation.getBlockZ());
        Region suitableRegion = findSuitableRegionForLocation(newLocation);
        
        if (suitableRegion != null) {
            // Добавить игрока в найденный регион
            plugin.getPluginLogger().info("Найден подходящий регион " + suitableRegion.getId() + " для игрока " + player.getName());
            plugin.getRegionManager().addPlayerToRegion(player, suitableRegion);
            
            if (plugin.getConfig().getBoolean("debug.show-region-info", false)) {
                player.sendMessage("§aВы перешли в регион: " + suitableRegion.getId());
            }
            
            plugin.getPluginLogger().info("Игрок " + player.getName() + " добавлен в регион " + 
                suitableRegion.getId() + " (позиция: " + newLocation.getBlockX() + ", " + newLocation.getBlockZ() + ")");
        } else {
            // Создать новый регион
            plugin.getPluginLogger().info("Подходящий регион не найден, создаем новый для игрока " + player.getName());
            createNewRegionForPlayer(player, newLocation);
        }
    }
    
    /**
     * Найти подходящий регион для локации
     */
    private Region findSuitableRegionForLocation(Location location) {
        plugin.getPluginLogger().debug("Поиск подходящего региона для локации " + location.getBlockX() + ", " + location.getBlockZ());
        
        // Сначала попробуем найти регион, который содержит эту локацию
        for (Region region : plugin.getRegionManager().getRegions().values()) {
            if (!region.isActive() || !region.canAcceptPlayers()) {
                plugin.getPluginLogger().debug("Регион " + region.getId() + " пропущен (неактивен или не может принять игроков)");
                continue;
            }
            
            if (region.contains(location)) {
                plugin.getPluginLogger().debug("Найден регион " + region.getId() + " содержащий локацию");
                return region;
            }
        }
        
        // Если не найден содержащий регион, ищем ближайший
        plugin.getPluginLogger().debug("Содержащий регион не найден, ищем ближайший");
        Region nearestRegion = plugin.getRegionManager().findNearestRegion(location);
        if (nearestRegion != null) {
            plugin.getPluginLogger().debug("Найден ближайший регион " + nearestRegion.getId() + 
                " для локации " + location.getBlockX() + ", " + location.getBlockZ());
        } else {
            plugin.getPluginLogger().debug("Ближайший регион не найден");
        }
        
        return nearestRegion;
    }
    
    /**
     * Обработать проверку региона при пробуждении
     */
    private void handleSleepWakeRegionCheck(Player player, Location wakeLocation) {
        Region currentRegion = plugin.getRegionManager().getPlayerRegion(player);
        
        // Если у игрока нет региона, создаем его
        if (currentRegion == null) {
            plugin.getPluginLogger().info("Игрок " + player.getName() + " проснулся без региона, создаем новый");
            createNewRegionForPlayer(player, wakeLocation);
            return;
        }
        
        // Проверяем, находится ли игрок в пределах текущего региона
        if (currentRegion.contains(wakeLocation)) {
            plugin.getPluginLogger().debug("Игрок " + player.getName() + " проснулся в том же регионе " + currentRegion.getId());
            return;
        }
        
        // Игрок проснулся в другом месте, но не слишком далеко от региона
        double distanceToRegion = currentRegion.getCenter().distance(wakeLocation);
        int regionSize = currentRegion.getSize();
        
        // Если игрок проснулся не слишком далеко от центра региона, расширяем регион
        if (distanceToRegion <= regionSize * sleepExpansionMultiplier) {
            plugin.getPluginLogger().info("Игрок " + player.getName() + " проснулся рядом с регионом " + 
                currentRegion.getId() + ", расширяем регион");
            
            // Расширяем регион, чтобы включить новую позицию
            int newRadius = (int) Math.ceil(distanceToRegion + sleepExpansionBuffer);
            currentRegion.expandRadius(newRadius);
            
            if (plugin.getConfig().getBoolean("debug.show-region-info", false)) {
                player.sendMessage("§aРегион " + currentRegion.getId() + " расширен для включения вашей позиции");
            }
        } else {
            // Игрок проснулся слишком далеко, создаем новый регион
            plugin.getPluginLogger().info("Игрок " + player.getName() + " проснулся далеко от региона " + 
                currentRegion.getId() + ", создаем новый регион");
            
            // Удаляем из старого региона
            plugin.getRegionManager().removePlayerFromRegion(player);
            
            // Создаем новый регион
            createNewRegionForPlayer(player, wakeLocation);
        }
    }
    
    /**
     * Проверить, спит ли игрок
     */
    private boolean isPlayerSleeping(Player player) {
        return sleepingPlayers.getOrDefault(player.getUniqueId(), false);
    }
    
    /**
     * Создать новый регион для игрока
     */
    private void createNewRegionForPlayer(Player player, Location location) {
        Region newRegion = plugin.getRegionManager().createNewRegion(location);
        if (newRegion != null) {
            plugin.getRegionManager().addPlayerToRegion(player, newRegion);
            
            if (plugin.getConfig().getBoolean("debug.show-region-info", false)) {
                player.sendMessage("§aСоздан новый регион: " + newRegion.getId());
            }
            
            plugin.getPluginLogger().info("Создан новый регион " + newRegion.getId() + 
                " для игрока " + player.getName() + " в " + location.toString());
        } else {
            plugin.getPluginLogger().error("Не удалось создать регион для игрока " + player.getName() + 
                " в " + location.toString());
        }
    }
} 
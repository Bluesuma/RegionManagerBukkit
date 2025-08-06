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

/**
 * Слушатель событий игроков
 * Управляет перемещением игроков между регионами с поддержкой предиктов
 */
public class PlayerListener implements Listener {
    
    private final RegionManagerPlugin plugin;
    private final MovementPredictor movementPredictor;
    private final int regionCheckDistance;
    
    /**
     * Конструктор слушателя
     */
    public PlayerListener(RegionManagerPlugin plugin) {
        this.plugin = plugin;
        this.movementPredictor = plugin.getMovementPredictor();
        // Используем настройку из конфигурации для расстояния проверки
        this.regionCheckDistance = plugin.getConfig().getInt("regions.check-distance", 64);
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
        
        // Проверить, изменился ли мир
        if (!from.getWorld().equals(to.getWorld())) {
            handleWorldChange(player, to);
            return;
        }
        
        // Проверить, нужно ли пересчитать регион
        if (shouldCheckRegionChange(from, to)) {
            handleRegionChange(player, to);
        }
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
        Region currentRegion = plugin.getRegionManager().getPlayerRegion(player);
        
        // Проверить, находится ли игрок в пределах текущего региона
        if (currentRegion != null && currentRegion.contains(newLocation)) {
            // Игрок все еще в том же регионе
            return;
        }
        
        // Игрок вышел за пределы текущего региона или не находится в регионе
        if (currentRegion != null) {
            // Удалить игрока из текущего региона
            plugin.getRegionManager().removePlayerFromRegion(player);
        }
        
        // Найти ближайший регион или создать новый
        Region nearestRegion = plugin.getRegionManager().findNearestRegion(newLocation);
        
        if (nearestRegion != null && nearestRegion.canAcceptPlayers() && 
            nearestRegion.contains(newLocation)) {
            // Добавить игрока в ближайший существующий регион
            plugin.getRegionManager().addPlayerToRegion(player, nearestRegion);
            
            if (plugin.getConfig().getBoolean("debug.show-region-info", false)) {
                player.sendMessage("§aВы перешли в регион: " + nearestRegion.getId());
            }
            
            // Логирование перемещения
            if (plugin.getConfig().getBoolean("logging.player-movements", false)) {
                plugin.getPluginLogger().debug("Игрок " + player.getName() + 
                    " перешел в регион " + nearestRegion.getId());
            }
        } else {
            // Создать новый регион для игрока
            Region newRegion = plugin.getRegionManager().findOrCreateRegionForPlayer(player);
            if (newRegion != null) {
                plugin.getRegionManager().addPlayerToRegion(player, newRegion);
                
                if (plugin.getConfig().getBoolean("debug.show-region-info", false)) {
                    player.sendMessage("§aСоздан новый регион: " + newRegion.getId());
                }
                
                // Логирование создания нового региона
                if (plugin.getConfig().getBoolean("logging.region-operations", true)) {
                    plugin.getPluginLogger().info("Создан новый регион " + newRegion.getId() + 
                        " для игрока " + player.getName() + " в " + newLocation.toString());
                }
            }
        }
    }
} 
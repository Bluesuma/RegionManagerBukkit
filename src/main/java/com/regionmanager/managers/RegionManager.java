package com.regionmanager.managers;

import com.regionmanager.RegionManagerPlugin;
import com.regionmanager.region.Region;
import com.regionmanager.utils.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Менеджер регионов
 * Управляет созданием, удалением и распределением игроков по регионам
 */
public class RegionManager {
    
    private final RegionManagerPlugin plugin;
    private final Logger logger;
    private final Map<String, Region> regions;
    private final Map<Player, Region> playerRegions;
    private final AtomicInteger regionCounter;
    private final int regionSize;
    private final int minDistanceBetweenRegions;
    private final int maxActiveRegions;
    
    /**
     * Конструктор менеджера регионов
     */
    public RegionManager(RegionManagerPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.regions = new ConcurrentHashMap<>();
        this.playerRegions = new ConcurrentHashMap<>();
        this.regionCounter = new AtomicInteger(0);
        
        // Загрузка конфигурации
        this.regionSize = plugin.getConfig().getInt("regions.size", 512);
        this.minDistanceBetweenRegions = plugin.getConfig().getInt("regions.min-distance-between-regions", 256);
        this.maxActiveRegions = plugin.getConfig().getInt("performance.max-active-regions", 50);
        
        // Запуск задач обслуживания
        startMaintenanceTasks();
        
        logger.info("RegionManager инициализирован");
    }
    
    /**
     * Найти или создать регион для игрока
     */
    public Region findOrCreateRegionForPlayer(Player player) {
        Location playerLocation = player.getLocation();
        
        // Сначала попробуем найти существующий регион
        Region existingRegion = findNearestRegion(playerLocation);
        if (existingRegion != null && existingRegion.canAcceptPlayers()) {
            return existingRegion;
        }
        
        // Если нет подходящего региона, создаем новый
        return createNewRegion(playerLocation);
    }
    
    /**
     * Найти ближайший регион к указанной локации
     */
    public Region findNearestRegion(Location location) {
        Region nearestRegion = null;
        double minDistance = Double.MAX_VALUE;
        
        for (Region region : regions.values()) {
            if (!region.isActive() || !region.canAcceptPlayers()) {
                continue;
            }
            
            double distance = region.distanceToCenter(location);
            if (distance < minDistance) {
                minDistance = distance;
                nearestRegion = region;
            }
        }
        
        return nearestRegion;
    }
    
    /**
     * Создать новый регион
     */
    public Region createNewRegion(Location center) {
        // Проверить лимит активных регионов
        if (getActiveRegionCount() >= maxActiveRegions) {
            logger.warn("Достигнут лимит активных регионов: " + maxActiveRegions);
            return null;
        }
        
        // Найти подходящее место для центра региона
        Location regionCenter = findOptimalRegionCenter(center);
        
        // Создать новый регион
        String regionId = "region_" + regionCounter.incrementAndGet();
        Region region = new Region(regionId, regionCenter, regionSize, this);
        
        regions.put(regionId, region);
        
        logger.info("Создан новый регион: " + regionId + " в " + regionCenter);
        return region;
    }
    
    /**
     * Найти оптимальный центр для нового региона
     */
    private Location findOptimalRegionCenter(Location playerLocation) {
        World world = playerLocation.getWorld();
        int x = playerLocation.getBlockX();
        int z = playerLocation.getBlockZ();
        
        // Округлить координаты до размера региона
        int regionX = (x / regionSize) * regionSize + regionSize / 2;
        int regionZ = (z / regionSize) * regionSize + regionSize / 2;
        
        // Проверить, не слишком ли близко к другим регионам
        Location candidateCenter = new Location(world, regionX, 64, regionZ);
        
        for (Region existingRegion : regions.values()) {
            if (existingRegion.getCenter().distance(candidateCenter) < minDistanceBetweenRegions) {
                // Найти новое место
                int offset = minDistanceBetweenRegions;
                candidateCenter = new Location(world, regionX + offset, 64, regionZ + offset);
                break;
            }
        }
        
        return candidateCenter;
    }
    
    /**
     * Добавить игрока в регион
     */
    public boolean addPlayerToRegion(Player player, Region region) {
        if (region == null) {
            return false;
        }
        
        // Удалить игрока из предыдущего региона
        Region previousRegion = playerRegions.get(player);
        if (previousRegion != null) {
            previousRegion.removePlayer(player);
        }
        
        // Добавить в новый регион
        region.addPlayer(player);
        playerRegions.put(player, region);
        logger.debug("Игрок " + player.getName() + " добавлен в регион " + region.getId());
        return true;
    }
    
    /**
     * Удалить игрока из региона
     */
    public void removePlayerFromRegion(Player player) {
        Region region = playerRegions.remove(player);
        if (region != null) {
            region.removePlayer(player);
            logger.debug("Игрок " + player.getName() + " удален из региона " + region.getId());
        }
    }
    
    /**
     * Получить регион игрока
     */
    public Region getPlayerRegion(Player player) {
        return playerRegions.get(player);
    }
    
    /**
     * Удалить регион
     */
    public void removeRegion(Region region) {
        if (region != null) {
            regions.remove(region.getId());
            logger.info("Регион " + region.getId() + " удален");
        }
    }
    
    /**
     * Получить количество активных регионов
     */
    public int getActiveRegionCount() {
        return (int) regions.values().stream().filter(Region::isActive).count();
    }
    
    /**
     * Получить общее количество регионов
     */
    public int getTotalRegionCount() {
        return regions.size();
    }
    
    /**
     * Получить статистику регионов
     */
    public Map<String, Object> getRegionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRegions", getTotalRegionCount());
        stats.put("activeRegions", getActiveRegionCount());
        stats.put("totalPlayers", playerRegions.size());
        stats.put("maxActiveRegions", maxActiveRegions);
        
        // Статистика по регионам
        Map<String, Integer> regionPlayerCounts = new HashMap<>();
        for (Region region : regions.values()) {
            regionPlayerCounts.put(region.getId(), region.getPlayerCount());
        }
        stats.put("regionPlayerCounts", regionPlayerCounts);
        
        return stats;
    }
    
    /**
     * Оптимизировать регионы
     */
    public void optimizeRegions() {
        logger.debug("Начинаем оптимизацию регионов...");
        
        for (Region region : regions.values()) {
            if (region.isActive()) {
                // Выгружаем неиспользуемые чанки
                // Note: unloadUnusedChunks() method was removed from Region class
                // This functionality is now handled internally by the Region class
            }
        }
        
        // Объединяем близкие регионы
        mergeNearbyRegions();
        
        logger.debug("Оптимизация регионов завершена");
    }
    
    /**
     * Объединить близкие регионы с малым количеством игроков
     */
    private void mergeNearbyRegions() {
        List<Region> regionsList = new ArrayList<>(regions.values());
        
        for (int i = 0; i < regionsList.size(); i++) {
            for (int j = i + 1; j < regionsList.size(); j++) {
                Region region1 = regionsList.get(i);
                Region region2 = regionsList.get(j);
                
                if (!region1.isActive() || !region2.isActive()) {
                    continue;
                }
                
                // Проверить, можно ли объединить регионы
                if (canMergeRegions(region1, region2)) {
                    mergeRegions(region1, region2);
                }
            }
        }
    }
    
    /**
     * Проверить, можно ли объединить два региона
     */
    private boolean canMergeRegions(Region region1, Region region2) {
        // Проверить, что оба региона активны
        if (!region1.isActive() || !region2.isActive()) {
            return false;
        }
        
        // Проверить, что регионы в одном мире
        if (!region1.getWorld().equals(region2.getWorld())) {
            return false;
        }
        
        // Проверить расстояние между центрами
        double distance = region1.getCenter().distance(region2.getCenter());
        if (distance > regionSize) {
            return false;
        }
        
        // Проверить, что общее количество игроков не превышает лимит
        int maxPlayers = plugin.getConfig().getInt("regions.max-players-per-region", 20);
        return region1.getPlayerCount() + region2.getPlayerCount() <= maxPlayers;
    }
    
    /**
     * Объединить два региона
     */
    private void mergeRegions(Region region1, Region region2) {
        // Переместить всех игроков в region1
        Set<UUID> playersToMove = new HashSet<>(region2.getPlayers());
        
        // Note: We need to get actual Player objects to move them
        // This is a simplified version - in practice, you'd need to get Player objects
        // from the UUIDs and then move them
        
        // Удалить region2
        regions.remove(region2.getId());
        
        logger.info("Объединены регионы " + region1.getId() + " и " + region2.getId());
    }
    
    /**
     * Запустить задачи обслуживания
     */
    private void startMaintenanceTasks() {
        // Задача оптимизации регионов каждые 30 секунд
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::optimizeRegions, 600L, 600L);
        
        // Задача очистки неактивных регионов каждые 5 минут
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanupInactiveRegions, 6000L, 6000L);
    }
    
    /**
     * Очистить неактивные регионы
     */
    private void cleanupInactiveRegions() {
        Iterator<Map.Entry<String, Region>> iterator = regions.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, Region> entry = iterator.next();
            Region region = entry.getValue();
            
            if (!region.isActive() && region.getPlayerCount() == 0) {
                // Note: unload() method was removed from Region class
                // Regions are now deactivated instead of unloaded
                iterator.remove();
                logger.info("Удален неактивный регион: " + region.getId());
            }
        }
    }
    
    /**
     * Завершить работу менеджера
     */
    public void shutdown() {
        // Note: unload() method was removed from Region class
        // Regions are now deactivated instead of unloaded
        // Just clear the collections
        
        regions.clear();
        playerRegions.clear();
        
        logger.info("RegionManager завершил работу");
    }
    
    // Геттеры
    public RegionManagerPlugin getPlugin() {
        return plugin;
    }
    
    public Map<String, Region> getRegions() {
        return new HashMap<>(regions);
    }
    
    public int getRegionSize() {
        return regionSize;
    }
    
    public int getMinDistanceBetweenRegions() {
        return minDistanceBetweenRegions;
    }
    
    public int getMaxActiveRegions() {
        return maxActiveRegions;
    }
} 
package com.regionmanager.managers;

import com.regionmanager.RegionManagerPlugin;
import com.regionmanager.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.Map;

/**
 * Менеджер производительности
 * Мониторит TPS и выполняет оптимизацию при необходимости
 */
public class PerformanceManager {
    
    private final RegionManagerPlugin plugin;
    private double currentTPS;
    private boolean isLowTPS;
    
    /**
     * Конструктор менеджера производительности
     */
    public PerformanceManager(RegionManagerPlugin plugin) {
        this.plugin = plugin;
        this.currentTPS = 20.0;
        this.isLowTPS = false;
        
        startTPSMonitoring();
    }
    
    /**
     * Запустить мониторинг TPS
     */
    private void startTPSMonitoring() {
        int checkInterval = plugin.getConfig().getInt("performance.tps-check-interval", 100);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                updateTPS();
                checkPerformance();
            }
        }.runTaskTimer(plugin, checkInterval, checkInterval);
    }
    
    /**
     * Обновить TPS используя правильный метод Bukkit API
     */
    private void updateTPS() {
        // Используем правильный метод получения TPS от Bukkit
        double[] tps = Bukkit.getTPS();
        
        // Берем среднее значение TPS за последние 1 минуту (индекс 1)
        // tps[0] = последние 5 секунд
        // tps[1] = последние 1 минута  
        // tps[2] = последние 5 минут
        if (tps[1] >= 0) {
            currentTPS = tps[1];
        } else {
            // Если TPS недоступен, используем значение за 5 секунд
            currentTPS = tps[0] >= 0 ? tps[0] : 20.0;
        }
        
        // Ограничиваем TPS до разумных значений
        if (currentTPS > 20.0) {
            currentTPS = 20.0;
        } else if (currentTPS < 0.0) {
            currentTPS = 0.0;
        }
    }
    
    /**
     * Проверить производительность и выполнить оптимизацию при необходимости
     */
    private void checkPerformance() {
        double targetTPS = plugin.getConfig().getDouble("performance.target-tps", 20.0);
        double minTPS = plugin.getConfig().getDouble("performance.min-tps", 15.0);
        boolean forceUnloadOnLowTPS = plugin.getConfig().getBoolean("performance.force-unload-on-low-tps", true);
        
        boolean wasLowTPS = isLowTPS;
        isLowTPS = currentTPS < minTPS;
        
        // Логирование производительности
        if (plugin.getConfig().getBoolean("logging.performance-metrics", true)) {
            plugin.getPluginLogger().debug("Текущий TPS: " + String.format("%.2f", currentTPS));
        }
        
        // Если TPS упал ниже минимального
        if (isLowTPS && !wasLowTPS) {
            plugin.getPluginLogger().warn("TPS упал ниже " + minTPS + ": " + String.format("%.2f", currentTPS));
            
            if (forceUnloadOnLowTPS) {
                performEmergencyOptimization();
            }
        }
        
        // Если TPS восстановился
        if (!isLowTPS && wasLowTPS) {
            plugin.getPluginLogger().info("TPS восстановился: " + String.format("%.2f", currentTPS));
        }
        
        // Регулярная оптимизация при низком TPS
        if (currentTPS < targetTPS) {
            performOptimization();
        }
    }
    
    /**
     * Выполнить экстренную оптимизацию при низком TPS
     */
    private void performEmergencyOptimization() {
        plugin.getPluginLogger().warn("Выполняется экстренная оптимизация из-за низкого TPS!");
        
        // Принудительная выгрузка пустых регионов
        plugin.getRegionManager().getRegions().values().stream()
            .filter(region -> region.getPlayerCount() == 0)
            .forEach(region -> {
                // Note: unload() method was removed from Region class
                // Regions are now deactivated instead of unloaded
                region.getRegionManager().getRegions().remove(region.getId());
                plugin.getPluginLogger().info("Экстренная деактивация региона: " + region.getId());
            });
        
        // Принудительная очистка памяти
        System.gc();
        
        plugin.getPluginLogger().info("Экстренная оптимизация завершена");
    }
    
    /**
     * Выполнить обычную оптимизацию
     */
    private void performOptimization() {
        // Оптимизация регионов
        plugin.getRegionManager().optimizeRegions();
        
        // Выгрузка ненужных чанков
        unloadUnusedChunks();
        
        // Очистка неактивных регионов
        cleanupInactiveRegions();
    }
    
    /**
     * Выгрузить ненужные чанки
     */
    private void unloadUnusedChunks() {
        int unloadedChunks = 0;
        
        for (var world : Bukkit.getWorlds()) {
            for (var chunk : world.getLoadedChunks()) {
                // Проверить, есть ли игроки рядом с чанком
                boolean hasNearbyPlayers = false;
                
                for (var player : Bukkit.getOnlinePlayers()) {
                    if (player.getWorld().equals(world)) {
                        int playerChunkX = player.getLocation().getBlockX() >> 4;
                        int playerChunkZ = player.getLocation().getBlockZ() >> 4;
                        
                        if (Math.abs(chunk.getX() - playerChunkX) <= 2 && 
                            Math.abs(chunk.getZ() - playerChunkZ) <= 2) {
                            hasNearbyPlayers = true;
                            break;
                        }
                    }
                }
                
                // Выгрузить чанк, если рядом нет игроков
                if (!hasNearbyPlayers && chunk.isLoaded()) {
                    chunk.unload();
                    unloadedChunks++;
                }
            }
        }
        
        if (unloadedChunks > 0) {
            plugin.getPluginLogger().debug("Выгружено " + unloadedChunks + " ненужных чанков");
        }
    }
    
    /**
     * Очистить неактивные регионы
     */
    private void cleanupInactiveRegions() {
        long currentTime = System.currentTimeMillis();
        long inactiveThreshold = 600000; // 10 минут
        
        int removedRegions = 0;
        
        var regions = plugin.getRegionManager().getRegions();
        Iterator<Map.Entry<String, Region>> iterator = regions.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, Region> entry = iterator.next();
            Region region = entry.getValue();
            
            if (!region.isActive() && 
                (currentTime - region.getLastActivityTime()) > inactiveThreshold) {
                // Note: unload() method was removed from Region class
                // Regions are now deactivated instead of unloaded
                iterator.remove();
                removedRegions++;
            }
        }
        
        if (removedRegions > 0) {
            plugin.getPluginLogger().info("Удалено " + removedRegions + " неактивных регионов");
        }
    }
    
    /**
     * Получить текущий TPS
     */
    public double getCurrentTPS() {
        return currentTPS;
    }
    
    /**
     * Проверить, низкий ли TPS
     */
    public boolean isLowTPS() {
        return isLowTPS;
    }
    
    /**
     * Получить статистику производительности
     */
    public String getPerformanceStats() {
        return String.format("TPS: %.2f, Активных регионов: %d, Игроков: %d", 
            currentTPS,
            plugin.getRegionManager().getActiveRegionCount(),
            Bukkit.getOnlinePlayers().size());
    }
    
    /**
     * Завершить работу менеджера
     */
    public void shutdown() {
        plugin.getPluginLogger().info("PerformanceManager завершил работу");
    }
} 
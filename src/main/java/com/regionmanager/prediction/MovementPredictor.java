package com.regionmanager.prediction;

import com.regionmanager.RegionManagerPlugin;
import com.regionmanager.region.Region;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Система предсказания движения игроков
 * Предугадывает направление движения и адаптивно управляет регионами
 */
public class MovementPredictor {
    
    private final RegionManagerPlugin plugin;
    private final Map<UUID, PlayerMovementData> playerMovements;
    private final Map<UUID, Location> lastPositions;
    private final Map<UUID, Long> lastMoveTimes;
    private final int predictionDistance;
    private final int predictionTimeSeconds;
    private final double speedThreshold;
    
    /**
     * Конструктор предиктора движения
     */
    public MovementPredictor(RegionManagerPlugin plugin) {
        this.plugin = plugin;
        this.playerMovements = new ConcurrentHashMap<>();
        this.lastPositions = new ConcurrentHashMap<>();
        this.lastMoveTimes = new ConcurrentHashMap<>();
        
        // Загрузка конфигурации
        this.predictionDistance = plugin.getConfig().getInt("prediction.distance", 128);
        this.predictionTimeSeconds = plugin.getConfig().getInt("prediction.time-seconds", 10);
        this.speedThreshold = plugin.getConfig().getDouble("prediction.speed-threshold", 0.1);
        
        startPredictionTask();
        plugin.getLogger().info("MovementPredictor инициализирован");
    }
    
    /**
     * Обработать движение игрока
     */
    public void onPlayerMove(Player player, Location from, Location to) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Сохранить текущую позицию
        lastPositions.put(playerId, to);
        lastMoveTimes.put(playerId, currentTime);
        
        // Вычислить скорость и направление
        double distance = from.distance(to);
        long timeDiff = currentTime - lastMoveTimes.getOrDefault(playerId, currentTime);
        
        if (timeDiff > 0 && distance > speedThreshold) {
            double speed = distance / (timeDiff / 1000.0); // блоков в секунду
            
            // Вычислить направление движения
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            double direction = Math.atan2(dz, dx);
            
            // Обновить данные движения
            PlayerMovementData movementData = playerMovements.computeIfAbsent(playerId, 
                k -> new PlayerMovementData());
            
            movementData.updateMovement(speed, direction, to, currentTime);
            
            // Предсказать будущую позицию
            Location predictedLocation = predictFutureLocation(player, speed, direction);
            
            // Проверить, нужно ли предварительно загрузить регион
            checkAndPreloadRegion(player, predictedLocation);
        }
    }
    
    /**
     * Предсказать будущую позицию игрока
     */
    private Location predictFutureLocation(Player player, double speed, double direction) {
        Location currentLocation = player.getLocation();
        
        // Вычислить расстояние, которое игрок пройдет за время предсказания
        double distanceToTravel = speed * predictionTimeSeconds;
        
        // Ограничить максимальное расстояние предсказания
        distanceToTravel = Math.min(distanceToTravel, predictionDistance);
        
        // Вычислить новые координаты
        double newX = currentLocation.getX() + (distanceToTravel * Math.cos(direction));
        double newZ = currentLocation.getZ() + (distanceToTravel * Math.sin(direction));
        
        return new Location(currentLocation.getWorld(), newX, currentLocation.getY(), newZ);
    }
    
    /**
     * Проверить и предварительно загрузить регион
     */
    private void checkAndPreloadRegion(Player player, Location predictedLocation) {
        Region currentRegion = plugin.getRegionManager().getPlayerRegion(player);
        
        if (currentRegion != null && currentRegion.contains(predictedLocation)) {
            // Игрок останется в том же регионе
            return;
        }
        
        // Игрок может перейти в другой регион
        Region targetRegion = plugin.getRegionManager().findNearestRegion(predictedLocation);
        
        if (targetRegion != null && targetRegion.canAcceptPlayers()) {
            // Предварительно расширить целевой регион
            expandRegionForPlayer(targetRegion, player, predictedLocation);
        } else {
            // Создать новый регион в предсказанной позиции
            plugin.getLogger().info("Предсказание: создание нового региона для " + player.getName() + 
                " в " + predictedLocation);
            plugin.getRegionManager().createNewRegion(predictedLocation);
        }
    }
    
    /**
     * Расширить регион для игрока
     */
    private void expandRegionForPlayer(Region region, Player player, Location playerLocation) {
        // Проверить, нужно ли расширение
        double distanceToCenter = region.distanceToCenter(playerLocation);
        int currentRadius = region.getSize() / 2;
        
        if (distanceToCenter > currentRadius * 0.8) { // Расширяем если игрок ближе к границе
            int newRadius = (int) Math.ceil(distanceToCenter * 1.5); // Расширяем с запасом
            int maxRadius = plugin.getConfig().getInt("regions.max-size", 1024) / 2;
            
            if (newRadius <= maxRadius) {
                region.expandRadius(newRadius);
                plugin.getLogger().info("Регион " + region.getId() + " расширен до " + newRadius + 
                    " блоков для игрока " + player.getName());
            }
        }
    }
    
    /**
     * Получить предсказанную позицию игрока
     */
    public Location getPredictedLocation(Player player) {
        PlayerMovementData movementData = playerMovements.get(player.getUniqueId());
        if (movementData == null) {
            return player.getLocation();
        }
        
        return predictFutureLocation(player, movementData.getAverageSpeed(), movementData.getAverageDirection());
    }
    
    /**
     * Получить данные движения игрока
     */
    public PlayerMovementData getPlayerMovementData(Player player) {
        return playerMovements.get(player.getUniqueId());
    }
    
    /**
     * Очистить данные игрока при выходе
     */
    public void onPlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();
        playerMovements.remove(playerId);
        lastPositions.remove(playerId);
        lastMoveTimes.remove(playerId);
    }
    
    /**
     * Запустить задачу предсказания
     */
    private void startPredictionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Очистить устаревшие данные движения
                long currentTime = System.currentTimeMillis();
                long maxAge = 30000; // 30 секунд
                
                playerMovements.entrySet().removeIf(entry -> 
                    currentTime - entry.getValue().getLastUpdateTime() > maxAge);
                
                // Проверить игроков, которые долго не двигались
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    UUID playerId = player.getUniqueId();
                    Long lastMoveTime = lastMoveTimes.get(playerId);
                    
                    if (lastMoveTime != null && currentTime - lastMoveTime > 10000) { // 10 секунд
                        // Игрок не двигается, можно оптимизировать его регион
                        optimizeRegionForInactivePlayer(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 100L); // Каждые 5 секунд
    }
    
    /**
     * Оптимизировать регион для неактивного игрока
     */
    private void optimizeRegionForInactivePlayer(Player player) {
        Region region = plugin.getRegionManager().getPlayerRegion(player);
        if (region != null && region.getPlayerCount() == 1) {
            // Если в регионе только один игрок и он не двигается, можно уменьшить регион
            int minRadius = plugin.getConfig().getInt("regions.min-size", 256) / 2;
            int currentRadius = region.getSize() / 2;
            
            if (currentRadius > minRadius) {
                int newRadius = Math.max(minRadius, currentRadius / 2);
                region.expandRadius(newRadius);
                plugin.getLogger().info("Регион " + region.getId() + " уменьшен до " + newRadius + 
                    " блоков для неактивного игрока " + player.getName());
            }
        }
    }
    
    /**
     * Получить статистику предсказаний
     */
    public Map<String, Object> getPredictionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("trackedPlayers", playerMovements.size());
        stats.put("predictionDistance", predictionDistance);
        stats.put("predictionTimeSeconds", predictionTimeSeconds);
        
        // Средняя скорость игроков
        double avgSpeed = playerMovements.values().stream()
            .mapToDouble(PlayerMovementData::getAverageSpeed)
            .average()
            .orElse(0.0);
        stats.put("averagePlayerSpeed", avgSpeed);
        
        return stats;
    }
} 
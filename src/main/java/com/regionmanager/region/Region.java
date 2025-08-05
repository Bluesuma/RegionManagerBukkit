package com.regionmanager.region;

import com.regionmanager.RegionManagerPlugin;
import com.regionmanager.managers.RegionManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Представляет регион в мире, который управляет игроками и загрузкой чанков
 */
public class Region {
    private final String id;
    private final Location center;
    private final int size;
    private final World world;
    private final Set<UUID> players;
    private final Set<String> loadedChunks;
    private int playerCount;
    private final long creationTime;
    private long lastActivityTime;
    private boolean isActive;
    private final RegionManager regionManager;

    public Region(String id, Location center, int size, RegionManager regionManager) {
        this.id = id;
        this.center = center;
        this.size = size;
        this.world = center.getWorld();
        this.players = new HashSet<>();
        this.loadedChunks = new HashSet<>();
        this.playerCount = 0;
        this.creationTime = System.currentTimeMillis();
        this.lastActivityTime = System.currentTimeMillis();
        this.isActive = true;
        this.regionManager = regionManager;
    }

    /**
     * Добавляет игрока в регион
     */
    public void addPlayer(Player player) {
        if (players.add(player.getUniqueId())) {
            playerCount++;
            lastActivityTime = System.currentTimeMillis();
            
            // Загружаем чанки вокруг игрока
            loadChunksAroundPlayer(player);
            
            RegionManagerPlugin.getInstance().getLogger().info(
                "Игрок " + player.getName() + " добавлен в регион " + id
            );
        }
    }

    /**
     * Удаляет игрока из региона
     */
    public void removePlayer(Player player) {
        if (players.remove(player.getUniqueId())) {
            playerCount--;
            lastActivityTime = System.currentTimeMillis();
            
            // Выгружаем чанки, если игроков больше нет
            if (playerCount == 0) {
                scheduleRegionUnload();
            }
            
            RegionManagerPlugin.getInstance().getLogger().info(
                "Игрок " + player.getName() + " удален из региона " + id
            );
        }
    }

    /**
     * Загружает чанки вокруг игрока
     */
    private void loadChunksAroundPlayer(Player player) {
        // Получаем настройки из конфигурации
        int maxViewDistance = RegionManagerPlugin.getInstance().getConfig()
            .getInt("chunk-loading.max-view-distance", 4);
        int chunkLoadDelay = RegionManagerPlugin.getInstance().getConfig()
            .getInt("chunk-loading.chunk-load-delay", 10);
        boolean asyncChunkLoading = RegionManagerPlugin.getInstance().getConfig()
            .getBoolean("chunk-loading.async-chunk-loading", true);
        int maxChunksPerBatch = RegionManagerPlugin.getInstance().getConfig()
            .getInt("chunk-loading.max-chunks-per-batch", 16);
        boolean chunkLoadingLogging = RegionManagerPlugin.getInstance().getConfig()
            .getBoolean("logging.chunk-loading", false);
        
        // Используем меньший радиус для предотвращения блокировки
        int viewDistance = Math.min(player.getViewDistance(), maxViewDistance);
        Location playerLoc = player.getLocation();
        
        if (chunkLoadingLogging) {
            RegionManagerPlugin.getInstance().getLogger().info(
                "Загрузка чанков для игрока " + player.getName() + 
                " с радиусом " + viewDistance + " чанков"
            );
        }
        
        if (asyncChunkLoading) {
            // Загружаем чанки асинхронно, чтобы не блокировать главный поток
            RegionManagerPlugin.getInstance().getServer().getScheduler().runTaskAsynchronously(
                RegionManagerPlugin.getInstance(),
                () -> {
                    try {
                        int chunksLoaded = 0;
                        
                        for (int x = -viewDistance; x <= viewDistance; x++) {
                            for (int z = -viewDistance; z <= viewDistance; z++) {
                                // Ограничиваем количество чанков за раз
                                if (chunksLoaded >= maxChunksPerBatch) {
                                    if (chunkLoadingLogging) {
                                        RegionManagerPlugin.getInstance().getLogger().info(
                                            "Достигнут лимит чанков за раз для игрока " + player.getName()
                                        );
                                    }
                                    break;
                                }
                                
                                int chunkX = (playerLoc.getBlockX() >> 4) + x;
                                int chunkZ = (playerLoc.getBlockZ() >> 4) + z;
                                String chunkKey = chunkX + "," + chunkZ;
                                
                                if (!loadedChunks.contains(chunkKey)) {
                                    // Загружаем чанк синхронно в главном потоке
                                    RegionManagerPlugin.getInstance().getServer().getScheduler().runTask(
                                        RegionManagerPlugin.getInstance(),
                                        () -> {
                                            try {
                                                world.getChunkAt(chunkX, chunkZ);
                                                loadedChunks.add(chunkKey);
                                                
                                                if (chunkLoadingLogging) {
                                                    RegionManagerPlugin.getInstance().getLogger().info(
                                                        "Загружен чанк (" + chunkX + "," + chunkZ + ") для игрока " + player.getName()
                                                    );
                                                }
                                            } catch (Exception e) {
                                                RegionManagerPlugin.getInstance().getLogger().warning(
                                                    "Ошибка при загрузке чанка (" + chunkX + "," + chunkZ + "): " + e.getMessage()
                                                );
                                            }
                                        }
                                    );
                                    
                                    chunksLoaded++;
                                    
                                    // Задержка между загрузкой чанков
                                    if (chunkLoadDelay > 0) {
                                        try {
                                            Thread.sleep(chunkLoadDelay);
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (chunkLoadingLogging) {
                            RegionManagerPlugin.getInstance().getLogger().info(
                                "Завершена загрузка " + chunksLoaded + " чанков для игрока " + player.getName()
                            );
                        }
                    } catch (Exception e) {
                        RegionManagerPlugin.getInstance().getLogger().severe(
                            "Ошибка при асинхронной загрузке чанков для игрока " + player.getName() + ": " + e.getMessage()
                        );
                    }
                }
            );
        } else {
            // Синхронная загрузка (не рекомендуется)
            try {
                int chunksLoaded = 0;
                
                for (int x = -viewDistance; x <= viewDistance; x++) {
                    for (int z = -viewDistance; z <= viewDistance; z++) {
                        if (chunksLoaded >= maxChunksPerBatch) {
                            break;
                        }
                        
                        int chunkX = (playerLoc.getBlockX() >> 4) + x;
                        int chunkZ = (playerLoc.getBlockZ() >> 4) + z;
                        String chunkKey = chunkX + "," + chunkZ;
                        
                        if (!loadedChunks.contains(chunkKey)) {
                            world.getChunkAt(chunkX, chunkZ);
                            loadedChunks.add(chunkKey);
                            chunksLoaded++;
                        }
                    }
                }
                
                if (chunkLoadingLogging) {
                    RegionManagerPlugin.getInstance().getLogger().info(
                        "Синхронно загружено " + chunksLoaded + " чанков для игрока " + player.getName()
                    );
                }
            } catch (Exception e) {
                RegionManagerPlugin.getInstance().getLogger().severe(
                    "Ошибка при синхронной загрузке чанков для игрока " + player.getName() + ": " + e.getMessage()
                );
            }
        }
    }

    /**
     * Планирует выгрузку региона
     */
    private void scheduleRegionUnload() {
        int unloadDelay = RegionManagerPlugin.getInstance().getConfig()
            .getInt("regions.auto-unload-delay", 300);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (playerCount == 0) {
                    isActive = false;
                    RegionManagerPlugin.getInstance().getLogger().info(
                        "Регион " + id + " деактивирован (нет игроков)"
                    );
                }
            }
        }.runTaskLater(RegionManagerPlugin.getInstance(), unloadDelay * 20L);
    }

    /**
     * Проверяет, находится ли локация в пределах региона
     */
    public boolean contains(Location location) {
        if (!location.getWorld().equals(world)) {
            return false;
        }
        
        int dx = Math.abs(location.getBlockX() - center.getBlockX());
        int dz = Math.abs(location.getBlockZ() - center.getBlockZ());
        
        return dx <= size / 2 && dz <= size / 2;
    }

    /**
     * Вычисляет расстояние до центра региона
     */
    public double distanceToCenter(Location location) {
        return location.distance(center);
    }

    /**
     * Проверяет, может ли регион принять новых игроков
     */
    public boolean canAcceptPlayers() {
        int maxPlayers = RegionManagerPlugin.getInstance().getConfig()
            .getInt("regions.max-players-per-region", 20);
        return playerCount < maxPlayers && isActive;
    }

    // Геттеры
    public String getId() { return id; }
    public Location getCenter() { return center; }
    public int getSize() { return size; }
    public World getWorld() { return world; }
    public Set<UUID> getPlayers() { return players; }
    public int getPlayerCount() { return playerCount; }
    public long getCreationTime() { return creationTime; }
    public long getLastActivityTime() { return lastActivityTime; }
    public boolean isActive() { return isActive; }
    public RegionManager getRegionManager() { return regionManager; }
} 
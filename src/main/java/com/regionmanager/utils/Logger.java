package com.regionmanager.utils;

import com.regionmanager.RegionManagerPlugin;
import org.bukkit.Bukkit;

import java.util.logging.Level;

/**
 * Утилита для логирования
 * Поддерживает разные уровни логирования и форматирование сообщений
 */
public class Logger {
    
    private final RegionManagerPlugin plugin;
    private final java.util.logging.Logger logger;
    private final String prefix;
    
    /**
     * Конструктор логгера
     */
    public Logger(RegionManagerPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.prefix = "[RegionManager] ";
    }
    
    /**
     * Логировать информационное сообщение
     */
    public void info(String message) {
        logger.info(prefix + message);
    }
    
    /**
     * Логировать предупреждение
     */
    public void warn(String message) {
        logger.warning(prefix + message);
    }
    
    /**
     * Логировать ошибку
     */
    public void error(String message) {
        logger.severe(prefix + message);
    }
    
    /**
     * Логировать отладочное сообщение
     */
    public void debug(String message) {
        if (plugin.getConfig().getString("logging.level", "INFO").equalsIgnoreCase("DEBUG")) {
            logger.info(prefix + "[DEBUG] " + message);
        }
    }
    
    /**
     * Логировать сообщение с указанным уровнем
     */
    public void log(Level level, String message) {
        logger.log(level, prefix + message);
    }
    
    /**
     * Логировать исключение
     */
    public void logException(String message, Throwable throwable) {
        logger.log(Level.SEVERE, prefix + message, throwable);
    }
    
    /**
     * Отправить сообщение в консоль сервера
     */
    public void broadcastToConsole(String message) {
        Bukkit.getConsoleSender().sendMessage(prefix + message);
    }
    
    /**
     * Отправить сообщение всем администраторам
     */
    public void broadcastToAdmins(String message) {
        Bukkit.broadcast(prefix + message, "regionmanager.admin");
    }
    
    /**
     * Проверить, включено ли логирование операций с регионами
     */
    public boolean isRegionOperationsLoggingEnabled() {
        return plugin.getConfig().getBoolean("logging.region-operations", true);
    }
    
    /**
     * Проверить, включено ли логирование производительности
     */
    public boolean isPerformanceLoggingEnabled() {
        return plugin.getConfig().getBoolean("logging.performance-metrics", true);
    }
    
    /**
     * Проверить, включено ли логирование перемещений игроков
     */
    public boolean isPlayerMovementLoggingEnabled() {
        return plugin.getConfig().getBoolean("logging.player-movements", false);
    }
    
    /**
     * Получить уровень логирования
     */
    public Level getLogLevel() {
        String levelStr = plugin.getConfig().getString("logging.level", "INFO");
        try {
            return Level.parse(levelStr);
        } catch (IllegalArgumentException e) {
            return Level.INFO;
        }
    }
} 
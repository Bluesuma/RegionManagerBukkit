package com.regionmanager;

import com.regionmanager.commands.RegionCommand;
import com.regionmanager.listeners.PlayerListener;
import com.regionmanager.managers.RegionManager;
import com.regionmanager.managers.PerformanceManager;
import com.regionmanager.prediction.MovementPredictor;
import com.regionmanager.utils.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Основной класс плагина RegionManagerBukkit
 * Плагин для распределения игроков по регионам для оптимизации TPS
 */
public class RegionManagerPlugin extends JavaPlugin {
    
    private static RegionManagerPlugin instance;
    private RegionManager regionManager;
    private PerformanceManager performanceManager;
    private MovementPredictor movementPredictor;
    private Logger logger;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Инициализация логгера
        logger = new Logger(this);
        logger.info("Запуск RegionManagerBukkit v" + getDescription().getVersion());
        
        // Сохранение конфигурации по умолчанию
        saveDefaultConfig();
        
        // Инициализация менеджеров
        initializeManagers();
        
        // Регистрация команд
        registerCommands();
        
        // Регистрация слушателей событий
        registerListeners();
        
        logger.info("RegionManagerBukkit успешно запущен!");
    }
    
    @Override
    public void onDisable() {
        if (regionManager != null) {
            regionManager.shutdown();
        }
        
        if (performanceManager != null) {
            performanceManager.shutdown();
        }
        
        logger.info("RegionManagerBukkit выключен!");
    }
    
    /**
     * Инициализация менеджеров
     */
    private void initializeManagers() {
        try {
            // Инициализация менеджера производительности
            performanceManager = new PerformanceManager(this);
            
            // Инициализация менеджера регионов
            regionManager = new RegionManager(this);
            
            // Инициализация системы предиктов движения
            movementPredictor = new MovementPredictor(this);
            
            logger.info("Менеджеры инициализированы успешно");
        } catch (Exception e) {
            logger.error("Ошибка при инициализации менеджеров: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    /**
     * Регистрация команд
     */
    private void registerCommands() {
        try {
            getCommand("region").setExecutor(new RegionCommand(this));
            logger.info("Команды зарегистрированы");
        } catch (Exception e) {
            logger.error("Ошибка при регистрации команд: " + e.getMessage());
        }
    }
    
    /**
     * Регистрация слушателей событий
     */
    private void registerListeners() {
        try {
            getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
            logger.info("Слушатели событий зарегистрированы");
        } catch (Exception e) {
            logger.error("Ошибка при регистрации слушателей: " + e.getMessage());
        }
    }
    
    /**
     * Получить экземпляр плагина
     */
    public static RegionManagerPlugin getInstance() {
        return instance;
    }
    
    /**
     * Получить менеджер регионов
     */
    public RegionManager getRegionManager() {
        return regionManager;
    }
    
    /**
     * Получить менеджер производительности
     */
    public PerformanceManager getPerformanceManager() {
        return performanceManager;
    }
    
    /**
     * Получить систему предиктов движения
     */
    public MovementPredictor getMovementPredictor() {
        return movementPredictor;
    }
    
    /**
     * Получить логгер
     */
    public Logger getPluginLogger() {
        return logger;
    }
} 
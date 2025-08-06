package com.regionmanager.prediction;

import org.bukkit.Location;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Данные движения игрока для предсказания
 */
public class PlayerMovementData {
    
    private final Queue<MovementSample> movementHistory;
    private final int maxHistorySize;
    private long lastUpdateTime;
    private Location lastLocation;
    
    /**
     * Конструктор данных движения
     */
    public PlayerMovementData() {
        this.movementHistory = new LinkedList<>();
        this.maxHistorySize = 10; // Хранить последние 10 измерений
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Обновить данные движения
     */
    public void updateMovement(double speed, double direction, Location location, long timestamp) {
        // Добавить новое измерение
        movementHistory.offer(new MovementSample(speed, direction, timestamp));
        
        // Ограничить размер истории
        while (movementHistory.size() > maxHistorySize) {
            movementHistory.poll();
        }
        
        this.lastLocation = location;
        this.lastUpdateTime = timestamp;
    }
    
    /**
     * Получить среднюю скорость
     */
    public double getAverageSpeed() {
        if (movementHistory.isEmpty()) {
            return 0.0;
        }
        
        return movementHistory.stream()
            .mapToDouble(MovementSample::getSpeed)
            .average()
            .orElse(0.0);
    }
    
    /**
     * Получить среднее направление
     */
    public double getAverageDirection() {
        if (movementHistory.isEmpty()) {
            return 0.0;
        }
        
        // Вычислить среднее направление с учетом циклической природы углов
        double sinSum = 0.0;
        double cosSum = 0.0;
        
        for (MovementSample sample : movementHistory) {
            sinSum += Math.sin(sample.getDirection());
            cosSum += Math.cos(sample.getDirection());
        }
        
        return Math.atan2(sinSum, cosSum);
    }
    
    /**
     * Получить последнюю скорость
     */
    public double getLastSpeed() {
        MovementSample lastSample = getLastSample();
        return lastSample != null ? lastSample.getSpeed() : 0.0;
    }
    
    /**
     * Получить последнее направление
     */
    public double getLastDirection() {
        MovementSample lastSample = getLastSample();
        return lastSample != null ? lastSample.getDirection() : 0.0;
    }
    
    /**
     * Получить последнее измерение
     */
    private MovementSample getLastSample() {
        return movementHistory.isEmpty() ? null : 
            ((LinkedList<MovementSample>) movementHistory).getLast();
    }
    
    /**
     * Получить время последнего обновления
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    /**
     * Получить последнюю позицию
     */
    public Location getLastLocation() {
        return lastLocation;
    }
    
    /**
     * Проверить, движется ли игрок
     */
    public boolean isMoving() {
        return getAverageSpeed() > 0.1; // Порог минимальной скорости
    }
    
    /**
     * Получить размер истории движения
     */
    public int getHistorySize() {
        return movementHistory.size();
    }
    
    /**
     * Очистить историю движения
     */
    public void clearHistory() {
        movementHistory.clear();
    }
    
    /**
     * Внутренний класс для хранения измерений движения
     */
    private static class MovementSample {
        private final double speed;
        private final double direction;
        private final long timestamp;
        
        public MovementSample(double speed, double direction, long timestamp) {
            this.speed = speed;
            this.direction = direction;
            this.timestamp = timestamp;
        }
        
        public double getSpeed() {
            return speed;
        }
        
        public double getDirection() {
            return direction;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
} 
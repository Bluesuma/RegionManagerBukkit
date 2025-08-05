package com.regionmanager;

import com.regionmanager.managers.RegionManager;
import com.regionmanager.region.Region;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegionManagerTest {

    @Mock
    private RegionManagerPlugin plugin;
    
    @Mock
    private World world;
    
    @Mock
    private Player player;
    
    private RegionManager regionManager;

    @BeforeEach
    void setUp() {
        // Настройка моков
        when(plugin.getConfig()).thenReturn(mock(org.bukkit.configuration.file.FileConfiguration.class));
        when(plugin.getConfig().getInt("regions.size", 512)).thenReturn(512);
        when(plugin.getConfig().getInt("regions.min-distance-between-regions", 256)).thenReturn(256);
        when(plugin.getConfig().getInt("performance.max-active-regions", 50)).thenReturn(50);
        when(plugin.getConfig().getInt("regions.max-players-per-region", 20)).thenReturn(20);
        
        regionManager = new RegionManager(plugin);
    }

    @Test
    void testCreateRegion() {
        // Arrange
        Location center = new Location(world, 100, 64, 100);
        
        // Act
        Region region = regionManager.createNewRegion(center);
        
        // Assert
        assertNotNull(region);
        assertEquals(center.getWorld(), region.getWorld());
        assertEquals(512, region.getSize());
        assertTrue(region.isActive());
    }

    @Test
    void testFindOrCreateRegionForPlayer() {
        // Arrange
        Location playerLocation = new Location(world, 100, 64, 100);
        when(player.getLocation()).thenReturn(playerLocation);
        when(player.getWorld()).thenReturn(world);
        
        // Act
        Region region = regionManager.findOrCreateRegionForPlayer(player);
        
        // Assert
        assertNotNull(region);
        assertTrue(region.canAcceptPlayers());
    }

    @Test
    void testRemovePlayerFromRegion() {
        // Arrange
        Location playerLocation = new Location(world, 100, 64, 100);
        when(player.getLocation()).thenReturn(playerLocation);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        
        Region region = regionManager.findOrCreateRegionForPlayer(player);
        int initialPlayerCount = region.getPlayerCount();
        
        // Act
        regionManager.removePlayerFromRegion(player);
        
        // Assert
        assertEquals(0, region.getPlayerCount());
    }

    @Test
    void testGetRegionStats() {
        // Act
        var stats = regionManager.getRegionStats();
        
        // Assert
        assertNotNull(stats);
        assertTrue(stats.containsKey("totalRegions"));
        assertTrue(stats.containsKey("activeRegions"));
        assertTrue(stats.containsKey("totalPlayers"));
    }

    @Test
    void testRegionContainsLocation() {
        // Arrange
        Location center = new Location(world, 100, 64, 100);
        Region region = regionManager.createNewRegion(center);
        
        Location insideLocation = new Location(world, 150, 64, 150);
        Location outsideLocation = new Location(world, 500, 64, 500);
        
        // Act & Assert
        assertTrue(region.contains(insideLocation));
        assertFalse(region.contains(outsideLocation));
    }

    @Test
    void testOptimizeRegions() {
        // Arrange
        Location center1 = new Location(world, 100, 64, 100);
        Location center2 = new Location(world, 200, 64, 200);
        
        Region region1 = regionManager.createNewRegion(center1);
        Region region2 = regionManager.createNewRegion(center2);
        
        // Act
        regionManager.optimizeRegions();
        
        // Assert - проверяем, что оптимизация не выбросила исключение
        assertNotNull(region1);
        assertNotNull(region2);
    }

    @Test
    void testFindNearestRegion() {
        // Arrange
        Location center1 = new Location(world, 100, 64, 100);
        Location center2 = new Location(world, 500, 64, 500);
        
        Region region1 = regionManager.createNewRegion(center1);
        Region region2 = regionManager.createNewRegion(center2);
        
        Location testLocation = new Location(world, 120, 64, 120);
        
        // Act
        Region nearest = regionManager.findNearestRegion(testLocation);
        
        // Assert
        assertNotNull(nearest);
        // region1 должен быть ближе к testLocation
        assertTrue(region1.distanceToCenter(testLocation) < region2.distanceToCenter(testLocation));
    }

    @Test
    void testRegionCanAcceptPlayers() {
        // Arrange
        Location center = new Location(world, 100, 64, 100);
        Region region = regionManager.createNewRegion(center);
        
        // Act & Assert
        assertTrue(region.canAcceptPlayers());
        
        // Добавляем максимальное количество игроков
        for (int i = 0; i < 20; i++) {
            Player mockPlayer = mock(Player.class);
            when(mockPlayer.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
            region.addPlayer(mockPlayer);
        }
        
        // Теперь регион не должен принимать новых игроков
        assertFalse(region.canAcceptPlayers());
    }
} 
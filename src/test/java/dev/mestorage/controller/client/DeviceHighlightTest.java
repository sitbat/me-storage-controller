package dev.mestorage.controller.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DeviceHighlightTest {
    @Test
    void markerKeepsSubBlockPrecisionNearBothWorldBorders() {
        var target = new BlockPos(4, 100, -6);
        var camera = new Vec3(0.25, 98.625, -0.875);
        var local = DeviceHighlight.cameraRelativeBounds(target, camera);
        for (int offset : new int[] {-29_999_980, 29_999_980}) {
            var distant = DeviceHighlight.cameraRelativeBounds(target.offset(offset, 0, offset),
                    camera.add(offset, 0, offset));
            assertBounds(local, distant);
        }
        assertEquals(1.01, local.getXsize(), 1.0e-9);
        assertEquals(1.01, local.getYsize(), 1.0e-9);
        assertEquals(1.01, local.getZsize(), 1.0e-9);
    }

    @Test
    void cameraMovementChangesMarkerByExactlyOppositeDisplacement() {
        var target = new BlockPos(-21, 87, 38);
        var camera = new Vec3(-15.25, 89.125, 36.75);
        var movement = new Vec3(1.125, -0.5, 2.75);
        var original = DeviceHighlight.cameraRelativeBounds(target, camera);
        var moved = DeviceHighlight.cameraRelativeBounds(target, camera.add(movement));
        assertBounds(original.move(-movement.x, -movement.y, -movement.z), moved);
    }

    private static void assertBounds(AABB expected, AABB actual) {
        assertEquals(expected.minX, actual.minX, 1.0e-9);
        assertEquals(expected.minY, actual.minY, 1.0e-9);
        assertEquals(expected.minZ, actual.minZ, 1.0e-9);
        assertEquals(expected.maxX, actual.maxX, 1.0e-9);
        assertEquals(expected.maxY, actual.maxY, 1.0e-9);
        assertEquals(expected.maxZ, actual.maxZ, 1.0e-9);
    }
}

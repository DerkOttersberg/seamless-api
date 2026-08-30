package com.derko.seamlessapi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.derko.seamlessapi.api.deconstruction.DeconstructionRegistration;
import com.derko.seamlessapi.api.meteor.MeteorShowerRegistration;
import com.derko.seamlessapi.api.visual.SeamlessVec3;
import com.derko.seamlessapi.api.visual.TrailMath;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiContractsTest {
    @Test
    void foodRegistrationIsImmutable() {
        var source = new java.util.ArrayList<>(List.of("walk_speed"));
        var registration = FoodBuffRegistration.builder().buffs(source).duration(45).build();
        source.add("regeneration");

        assertEquals(List.of("walk_speed"), registration.buffs());
        assertEquals(45, registration.durationSeconds());
        assertThrows(UnsupportedOperationException.class, () -> registration.buffs().add("mining_speed"));
    }

    @Test
    void deconstructionRequiresPositiveIngredients() {
        assertThrows(IllegalStateException.class, () -> DeconstructionRegistration.builder().build());
        assertThrows(
            IllegalArgumentException.class,
            () -> DeconstructionRegistration.builder().ingredient("minecraft:stick", 0.0)
        );

        var registration = DeconstructionRegistration.builder()
            .ingredient("minecraft:stick", 1.5)
            .damageScalingEnabled(false)
            .build();

        assertEquals(1.5, registration.ingredientUnits().get("minecraft:stick"));
        assertFalse(registration.damageScalingEnabled());
    }

    @Test
    void meteorBuilderPreservesOverrides() {
        var registration = MeteorShowerRegistration.builder(MeteorShowerRegistration.ShowerSize.MEDIUM)
            .durationSeconds(45)
            .meteorsPerSecond(2.0F)
            .angularSpreadDegrees(30.0F)
            .build();

        assertEquals(MeteorShowerRegistration.ShowerSize.MEDIUM, registration.size());
        assertEquals(45, registration.durationSeconds());
        assertEquals(2.0F, registration.meteorsPerSecond());
        assertEquals(30.0F, registration.angularSpreadDegrees());
    }

    @Test
    void trailSmoothingUsesLatestPointAndExpectedSampleCount() {
        var points = List.of(
            new SeamlessVec3(0.0, 0.0, 0.0),
            new SeamlessVec3(1.0, 1.0, 0.0),
            new SeamlessVec3(2.0, 0.0, 0.0)
        );
        var latest = new SeamlessVec3(3.0, 0.0, 0.0);
        var smoothed = TrailMath.smoothCatmullRom(points, 4, latest);

        assertEquals(9, smoothed.size());
        assertEquals(points.getFirst(), smoothed.getFirst());
        assertEquals(latest, smoothed.getLast());
        assertEquals(1.0F, TrailMath.widthAtProgress(1.0F, 1.0F));
        assertEquals(0.0F, TrailMath.alphaAtProgress(1.0F, 0.0F));
    }
}

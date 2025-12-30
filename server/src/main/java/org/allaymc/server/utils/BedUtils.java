package org.allaymc.server.utils;

import lombok.experimental.UtilityClass;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.world.Dimension;
import org.joml.Vector3ic;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class BedUtils {
    public static boolean isBedOccupiedByPlayer(Dimension dimension, Vector3ic bedPos) {
        return !getSleepingPlayersAtBed(dimension, bedPos).isEmpty();
    }

    public static List<EntityPlayer> getSleepingPlayersAtBed(Dimension dimension, Vector3ic bedPos) {
        var sleepers = new ArrayList<EntityPlayer>();
        for (var viewer : dimension.getPlayers()) {
            var player = viewer.getControlledEntity();
            if (player == null || !player.isSleeping()) {
                continue;
            }

            var sleepingPos = player.getSleepingPos();
            if (sleepingPos != null &&
                sleepingPos.x() == bedPos.x() &&
                sleepingPos.y() == bedPos.y() &&
                sleepingPos.z() == bedPos.z()) {
                sleepers.add(player);
            }
        }

        return sleepers;
    }
}

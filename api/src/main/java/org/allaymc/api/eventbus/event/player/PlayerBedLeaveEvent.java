package org.allaymc.api.eventbus.event.player;

import lombok.Getter;
import org.allaymc.api.annotation.CallerThread;
import org.allaymc.api.annotation.ThreadType;
import org.allaymc.api.block.dto.Block;
import org.allaymc.api.entity.interfaces.EntityPlayer;

/**
 * Called when a player leaves a bed.
 *
 * @author ClexaGod
 */
@Getter
@CallerThread(ThreadType.WORLD)
public class PlayerBedLeaveEvent extends PlayerEvent {
    protected final Block bedBlock;

    public PlayerBedLeaveEvent(EntityPlayer player, Block bedBlock) {
        super(player);
        this.bedBlock = bedBlock;
    }
}

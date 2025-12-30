package org.allaymc.api.eventbus.event.player;

import lombok.Getter;
import org.allaymc.api.annotation.CallerThread;
import org.allaymc.api.annotation.ThreadType;
import org.allaymc.api.block.dto.Block;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.eventbus.event.CancellableEvent;

/**
 * Called when a player tries to enter a bed.
 *
 * @author ClexaGod
 */
@Getter
@CallerThread(ThreadType.WORLD)
public class PlayerBedEnterEvent extends PlayerEvent implements CancellableEvent {
    protected final Block bedBlock;

    public PlayerBedEnterEvent(EntityPlayer player, Block bedBlock) {
        super(player);
        this.bedBlock = bedBlock;
    }
}

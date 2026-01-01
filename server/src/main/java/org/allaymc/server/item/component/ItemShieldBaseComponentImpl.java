package org.allaymc.server.item.component;

import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.item.ItemStackInitInfo;

/**
 * @author AllayPlus Team
 */
public class ItemShieldBaseComponentImpl extends ItemBaseComponentImpl {

    public ItemShieldBaseComponentImpl(ItemStackInitInfo initInfo) {
        super(initInfo);
    }

    @Override
    public boolean canUseItemInAir(EntityPlayer player) {
        // Shield can always be raised
        return true;
    }

    @Override
    public boolean useItemInAir(EntityPlayer player, long usedTime) {
        // Shield doesn't have "use" action like bow, it's toggled by sneaking
        // So this method just returns false
        return false;
    }
}

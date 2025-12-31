package org.allaymc.server.utils;

import org.allaymc.api.container.Container;
import org.allaymc.api.item.ItemStack;
import org.allaymc.api.item.interfaces.ItemAirStack;

/**
 * @author ClexaGod
 */
public final class ContainerUtils {

    private ContainerUtils() {
    }

    @FunctionalInterface
    public interface MoveValidator {
        boolean canMove(Container target, int targetSlot, ItemStack sourceStack, int moveCount);
    }

    public static int insertIntoContainer(ItemStack sourceStack, Container target, int maxMoveCount, int[] allowedSlots, MoveValidator validator) {
        if (target == null || sourceStack == null || sourceStack == ItemAirStack.AIR_STACK || maxMoveCount <= 0) {
            return 0;
        }
        if (sourceStack.getCount() <= 0 || target.isFull()) {
            return 0;
        }

        int remaining = Math.min(sourceStack.getCount(), maxMoveCount);
        int movedCount = 0;

        if (allowedSlots == null) {
            var size = target.getItemStackArray().length;
            for (int slot = 0; slot < size; slot++) {
                if (remaining <= 0) {
                    break;
                }

                movedCount += tryMoveToSlot(target, sourceStack, slot, remaining, validator);
                remaining = Math.min(sourceStack.getCount(), maxMoveCount) - movedCount;
            }
        } else {
            for (int slot : allowedSlots) {
                if (remaining <= 0) {
                    break;
                }

                movedCount += tryMoveToSlot(target, sourceStack, slot, remaining, validator);
                remaining = Math.min(sourceStack.getCount(), maxMoveCount) - movedCount;
            }
        }

        if (movedCount > 0) {
            sourceStack.reduceCount(movedCount);
        }
        return movedCount;
    }

    private static int tryMoveToSlot(Container target, ItemStack sourceStack, int slot, int remaining, MoveValidator validator) {
        var targetStack = target.getItemStack(slot);
        int maxStackSize = sourceStack.getItemType().getItemData().maxStackSize();
        if (targetStack == ItemAirStack.AIR_STACK) {
            int moveCount = Math.min(remaining, maxStackSize);
            if (validator != null && !validator.canMove(target, slot, sourceStack, moveCount)) {
                return 0;
            }

            var newStack = sourceStack.copy();
            newStack.setCount(moveCount);
            target.setItemStack(slot, newStack);
            return moveCount;
        }

        if (targetStack.canMerge(sourceStack, true) && !targetStack.isFull()) {
            int targetMax = targetStack.getItemType().getItemData().maxStackSize();
            int space = targetMax - targetStack.getCount();
            if (space <= 0) {
                return 0;
            }

            int moveCount = Math.min(remaining, space);
            if (validator != null && !validator.canMove(target, slot, sourceStack, moveCount)) {
                return 0;
            }

            targetStack.increaseCount(moveCount);
            target.notifySlotChange(slot);
            return moveCount;
        }

        return 0;
    }
}

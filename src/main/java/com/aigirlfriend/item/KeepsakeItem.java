package com.aigirlfriend.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Romantic presents (wedding ring, flowers, gifts). Plain items with a one-line hint tooltip
 * ({@code item.aigirlfriend.<id>.desc}); what she does with them lives in the companion controller.
 */
public class KeepsakeItem extends Item {
    public KeepsakeItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}

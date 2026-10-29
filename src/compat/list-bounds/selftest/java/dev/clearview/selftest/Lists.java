package dev.clearview.selftest;

import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;

final class Lists {
    private Lists() {
    }

    static boolean shows(AbstractSelectionList<?> list, AbstractWidget widget) {
        return widget.getY() >= list.getY() && widget.getY() + widget.getHeight() <= list.getY() + list.getHeight();
    }
}

package dev.clearview.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
    @Accessor("value")
    Object clearview$value();

    @Accessor("value")
    void clearview$setValue(Object value);
}

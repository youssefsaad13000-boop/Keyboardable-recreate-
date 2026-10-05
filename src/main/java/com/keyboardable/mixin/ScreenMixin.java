package com.keyboardable.mixin;

import com.keyboardable.KeyboardableClient;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Focusable;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow
    public abstract void setFocused(net.minecraft.client.gui.components.events.GuiEventListener listener);

    @Inject(method = "setFocused", at = @At("HEAD"))
    private void keyboardable$onFocusChanged(net.minecraft.client.gui.components.events.GuiEventListener listener, CallbackInfo info) {
        KeyboardableClient.handleScreenFocus((Screen) (Object) this);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void keyboardable$onScreenRemoved(CallbackInfo info) {
        KeyboardableClient.handleScreenClose((Screen) (Object) this);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void keyboardable$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (KeyboardableClient.getOverlay().isVisible()) {
            KeyboardableClient.getOverlay().handleMouseClick(mouseX, mouseY, button);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void keyboardable$onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (KeyboardableClient.getOverlay().isVisible() && keyCode == 256) {
            KeyboardableClient.getOverlay().close();
            cir.setReturnValue(true);
        }
    }
}

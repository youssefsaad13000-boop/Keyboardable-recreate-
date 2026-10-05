package com.keyboardable;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

public class KeyboardableClient implements ClientModInitializer {
    public static final String MOD_ID = "keyboardable";
    private static final KeyboardableOverlay OVERLAY = new KeyboardableOverlay();
    private static KeyMapping toggleLayout;

    @Override
    public void onInitializeClient() {
        toggleLayout = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.keyboardable.toggle_layout",
                org.lwjgl.glfw.GLFW.GLFW_KEY_GRAVE,
                "category.keyboardable"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Screen screen = client.screen;
            if (screen == null) {
                if (OVERLAY.isVisible()) {
                    OVERLAY.close();
                }
                return;
            }

            if (toggleLayout.consumeClick()) {
                OVERLAY.toggleLayout();
            }

            if (screen.getFocused() != null && shouldAutoOpen(screen)) {
                OVERLAY.open(screen);
            }

            if (OVERLAY.isVisible() && !shouldAutoOpen(screen)) {
                OVERLAY.close();
            }
        });

        HudRenderCallback.EVENT.register((guiGraphics, deltaTick) -> {
            if (OVERLAY.isVisible()) {
                OVERLAY.render(guiGraphics, deltaTick);
            }
        });
    }

    public static boolean shouldAutoOpen(Screen screen) {
        if (screen == null) {
            return false;
        }
        if (screen.getFocused() instanceof EditBox) {
            return true;
        }
        return screen.children().stream().anyMatch(child -> child instanceof EditBox);
    }

    public static KeyboardableOverlay getOverlay() {
        return OVERLAY;
    }

    public static void handleScreenFocus(Screen screen) {
        if (screen == null) {
            return;
        }
        if (screen.getFocused() != null && shouldAutoOpen(screen)) {
            OVERLAY.open(screen);
        } else if (OVERLAY.isVisible() && screen != OVERLAY.getScreen()) {
            OVERLAY.close();
        }
    }

    public static void handleScreenClose(Screen screen) {
        if (OVERLAY.isVisible() && screen == OVERLAY.getScreen()) {
            OVERLAY.close();
        }
    }
}

package com.krson.keyboardablecontrollableaddon;

import com.mojang.blaze3d.platform.InputConstants;
import com.mrcrayfish.controllable.client.binding.BindingRegistry;
import com.mrcrayfish.controllable.client.binding.ButtonBinding;
import com.mrcrayfish.controllable.client.binding.IBindingContext;
import com.mrcrayfish.controllable.client.input.Buttons;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

public final class ClientHooks {
    private static final String KEY_CATEGORY = "key.categories.keyboardable";
    private static final String CONTROLLABLE_CATEGORY = "category.controllable.keyboardable";
    private static final int CONTROLLER_REOPEN_HOLD_TICKS = 8;

    public static final KeyMapping TOGGLE_KEYBOARD = new KeyMapping("key.keyboardable.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, KEY_CATEGORY);
    public static final KeyMapping NAV_UP = new KeyMapping("key.keyboardable.nav_up", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    public static final KeyMapping NAV_DOWN = new KeyMapping("key.keyboardable.nav_down", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    public static final KeyMapping NAV_LEFT = new KeyMapping("key.keyboardable.nav_left", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    public static final KeyMapping NAV_RIGHT = new KeyMapping("key.keyboardable.nav_right", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    public static final KeyMapping SELECT = new KeyMapping("key.keyboardable.select", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, KEY_CATEGORY);
    public static final KeyMapping SHIFT = new KeyMapping("key.keyboardable.shift", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    public static final KeyMapping CAPS = new KeyMapping("key.keyboardable.caps", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);

    private static final KeyboardableOverlay OVERLAY = new KeyboardableOverlay();
    private static final IBindingContext OVERLAY_CONTEXT = new IBindingContext() {
        @Override
        public boolean isActive() {
            return OVERLAY.isVisible();
        }

        @Override
        public boolean conflicts(IBindingContext context) {
            return context == this;
        }
    };

    public static final ButtonBinding CONTROLLER_NAV_UP = new ButtonBinding(Buttons.DPAD_UP, "key.keyboardable.nav_up", CONTROLLABLE_CATEGORY, OVERLAY_CONTEXT);
    public static final ButtonBinding CONTROLLER_NAV_DOWN = new ButtonBinding(Buttons.DPAD_DOWN, "key.keyboardable.nav_down", CONTROLLABLE_CATEGORY, OVERLAY_CONTEXT);
    public static final ButtonBinding CONTROLLER_NAV_LEFT = new ButtonBinding(Buttons.DPAD_LEFT, "key.keyboardable.nav_left", CONTROLLABLE_CATEGORY, OVERLAY_CONTEXT);
    public static final ButtonBinding CONTROLLER_NAV_RIGHT = new ButtonBinding(Buttons.DPAD_RIGHT, "key.keyboardable.nav_right", CONTROLLABLE_CATEGORY, OVERLAY_CONTEXT);
    public static final ButtonBinding CONTROLLER_SELECT = new ButtonBinding(Buttons.A, "key.keyboardable.select", CONTROLLABLE_CATEGORY, OVERLAY_CONTEXT);
    public static final ButtonBinding CONTROLLER_CLOSE = new ButtonBinding(Buttons.B, "key.keyboardable.close", CONTROLLABLE_CATEGORY, OVERLAY_CONTEXT);
    public static final ButtonBinding CONTROLLER_PAGE = new ButtonBinding(Buttons.LEFT_TRIGGER, "key.keyboardable.page", CONTROLLABLE_CATEGORY, OVERLAY_CONTEXT);
    public static final ButtonBinding CONTROLLER_BACKSPACE = new ButtonBinding(Buttons.X, "key.keyboardable.backspace", CONTROLLABLE_CATEGORY, OVERLAY_CONTEXT);
    public static final ButtonBinding CONTROLLER_SHIFT = new ButtonBinding(Buttons.Y, "key.keyboardable.shift", CONTROLLABLE_CATEGORY, OVERLAY_CONTEXT);
    public static final ButtonBinding CONTROLLER_CAPS = new ButtonBinding(Buttons.LEFT_THUMB_STICK, "key.keyboardable.caps", CONTROLLABLE_CATEGORY, OVERLAY_CONTEXT);

    private static boolean autoOpenSuppressed;
    private static Screen suppressedScreen;
    private static Screen pendingMouseOpenScreen;
    private static double pendingMouseOpenX;
    private static double pendingMouseOpenY;
    private static final Set<Integer> consumedControllerButtons = new HashSet<>();
    private static boolean overlayVisibleLastTick;

    private ClientHooks() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientHooks::registerKeyMappings);
        registerControllerBindings();
        registerControllableInputHook();
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_KEYBOARD);
        event.register(NAV_UP);
        event.register(NAV_DOWN);
        event.register(NAV_LEFT);
        event.register(NAV_RIGHT);
        event.register(SELECT);
        event.register(SHIFT);
        event.register(CAPS);
    }

    private static void registerControllerBindings() {
        BindingRegistry registry = BindingRegistry.getInstance();
        registry.register(CONTROLLER_NAV_UP);
        registry.register(CONTROLLER_NAV_DOWN);
        registry.register(CONTROLLER_NAV_LEFT);
        registry.register(CONTROLLER_NAV_RIGHT);
        registry.register(CONTROLLER_SELECT);
        registry.register(CONTROLLER_CLOSE);
        registry.register(CONTROLLER_PAGE);
        registry.register(CONTROLLER_BACKSPACE);
        registry.register(CONTROLLER_SHIFT);
        registry.register(CONTROLLER_CAPS);
    }

    private static void registerControllableInputHook() {
        try {
            Class<?> controllerEventsClass = Class.forName("com.mrcrayfish.controllable.event.ControllerEvents");
            Object inputEvent = controllerEventsClass.getField("INPUT").get(null);
            Class<?> inputListenerClass = Class.forName("com.mrcrayfish.controllable.event.ControllerEvents$Input");
            Class<?> frameworkEventClass = Class.forName("com.mrcrayfish.framework.api.event.IFrameworkEvent");

            Object listener = Proxy.newProxyInstance(
                    ClientHooks.class.getClassLoader(),
                    new Class<?>[]{inputListenerClass},
                    (proxy, method, args) -> {
                        if (!"handle".equals(method.getName()) || args == null || args.length < 4) {
                            return false;
                        }

                        int buttonId = -1;
                        Object wrappedValue = args[1];
                        if (wrappedValue != null) {
                            Method getMethod = wrappedValue.getClass().getMethod("get");
                            Object buttonValue = getMethod.invoke(wrappedValue);
                            if (buttonValue instanceof Integer integerButton) {
                                buttonId = integerButton;
                            }
                        }

                        boolean pressed = args[3] instanceof Boolean b && b;
                        int holdTime = args[2] instanceof Integer integerHold ? integerHold : 0;
                        return handleControllerInput(buttonId, holdTime, pressed);
                    }
            );

            Method registerMethod = inputEvent.getClass().getMethod("register", frameworkEventClass);
            registerMethod.invoke(inputEvent, listener);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static boolean handleControllerInput(int buttonId, int holdTime, boolean pressed) {
        if (!pressed) {
            // Never consume release events; Controllable needs them to clear held button state.
            consumedControllerButtons.remove(buttonId);
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        boolean consumed = false;
        if (!OVERLAY.isVisible()) {
            // Use a short hold on A to intentionally reopen on persistent text-focus screens.
            if (buttonId == CONTROLLER_SELECT.getButton()
                    && holdTime >= CONTROLLER_REOPEN_HOLD_TICKS
                    && autoOpenSuppressed
                    && screen != null
                    && OVERLAY.hasTextTarget(screen)) {
                OVERLAY.open(mc, screen);
                autoOpenSuppressed = false;
                suppressedScreen = null;
                consumed = true;
            }
            if (consumed) {
                consumedControllerButtons.add(buttonId);
            }
            return consumed;
        }

        if (buttonId == CONTROLLER_NAV_UP.getButton()) {
            OVERLAY.navigateUp();
            consumed = true;
        } else if (buttonId == CONTROLLER_NAV_DOWN.getButton()) {
            OVERLAY.navigateDown();
            consumed = true;
        } else if (buttonId == CONTROLLER_NAV_LEFT.getButton()) {
            OVERLAY.navigateLeft();
            consumed = true;
        } else if (buttonId == CONTROLLER_NAV_RIGHT.getButton()) {
            OVERLAY.navigateRight();
            consumed = true;
        } else if (buttonId == CONTROLLER_SELECT.getButton()) {
            OVERLAY.activateFocused();
            consumed = true;
        } else if (buttonId == CONTROLLER_CLOSE.getButton()) {
            OVERLAY.close(true);
            autoOpenSuppressed = true;
            suppressedScreen = Minecraft.getInstance().screen;
            consumed = true;
        } else if (buttonId == CONTROLLER_PAGE.getButton()) {
            OVERLAY.togglePage();
            consumed = true;
        } else if (buttonId == CONTROLLER_BACKSPACE.getButton()) {
            OVERLAY.backspace();
            consumed = true;
        } else if (buttonId == CONTROLLER_SHIFT.getButton()) {
            OVERLAY.toggleShift();
            consumed = true;
        } else if (buttonId == CONTROLLER_CAPS.getButton()) {
            OVERLAY.toggleCaps();
            consumed = true;
        }

        if (consumed) {
            consumedControllerButtons.add(buttonId);
        }
        return consumed;
    }

    private static void clearStuckInputState() {
        // If an input-up event is missed while the overlay is consuming input,
        // force clear held mappings so gameplay does not get stuck (e.g. jumping).
        KeyMapping.releaseAll();
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.keyJump.setDown(false);
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
            mc.options.keyShift.setDown(false);
            mc.options.keySprint.setDown(false);
            mc.options.keyAttack.setDown(false);
            mc.options.keyUse.setDown(false);
        }
        consumedControllerButtons.clear();
    }

    @Mod.EventBusSubscriber(modid = KeyboardableControllableAddon.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            Screen screen = mc.screen;
            if (screen == null) {
                if (OVERLAY.isVisible() || overlayVisibleLastTick) {
                    clearStuckInputState();
                }
                OVERLAY.close(false);
                autoOpenSuppressed = false;
                suppressedScreen = null;
                overlayVisibleLastTick = false;
                return;
            }

            boolean overlayVisibleNow = OVERLAY.isVisible();
            if (overlayVisibleLastTick && !overlayVisibleNow) {
                clearStuckInputState();
            }
            overlayVisibleLastTick = overlayVisibleNow;

            if (suppressedScreen != null && suppressedScreen != screen) {
                autoOpenSuppressed = false;
                suppressedScreen = null;
            }

            if (!OVERLAY.isVisible() && pendingMouseOpenScreen == screen) {
                if (OVERLAY.handleTextTargetClick(screen, pendingMouseOpenX, pendingMouseOpenY)) {
                    OVERLAY.open(mc, screen);
                    autoOpenSuppressed = false;
                    suppressedScreen = null;
                }
                pendingMouseOpenScreen = null;
            } else if (pendingMouseOpenScreen != null && pendingMouseOpenScreen != screen) {
                pendingMouseOpenScreen = null;
            }

            boolean wasVisible = OVERLAY.isVisible();

            while (TOGGLE_KEYBOARD.consumeClick()) {
                if (OVERLAY.isVisible()) {
                    OVERLAY.close(true);
                    autoOpenSuppressed = true;
                    suppressedScreen = screen;
                } else {
                    OVERLAY.open(mc, screen);
                    autoOpenSuppressed = false;
                    suppressedScreen = null;
                }
            }

            if (wasVisible && !OVERLAY.isVisible()) {
                autoOpenSuppressed = true;
                suppressedScreen = screen;
            }

            if (!OVERLAY.isVisible()) {
                overlayVisibleLastTick = false;
                return;
            }

            OVERLAY.refreshTarget(screen);

            while (NAV_UP.consumeClick()) {
                OVERLAY.navigateUp();
            }
            while (NAV_DOWN.consumeClick()) {
                OVERLAY.navigateDown();
            }
            while (NAV_LEFT.consumeClick()) {
                OVERLAY.navigateLeft();
            }
            while (NAV_RIGHT.consumeClick()) {
                OVERLAY.navigateRight();
            }
            while (SHIFT.consumeClick()) {
                OVERLAY.toggleShift();
            }
            while (CAPS.consumeClick()) {
                OVERLAY.toggleCaps();
            }
            while (SELECT.consumeClick()) {
                OVERLAY.activateFocused();
            }

            overlayVisibleLastTick = OVERLAY.isVisible();
        }

        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            if (OVERLAY.isVisible()) {
                OVERLAY.rebuild(Minecraft.getInstance(), event.getScreen());
            }
        }

        @SubscribeEvent
        public static void onScreenRender(ScreenEvent.Render.Post event) {
            if (!OVERLAY.isVisible()) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            int mouseX = (int) (mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth());
            int mouseY = (int) (mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight());
            OVERLAY.mouseMoved(mouseX, mouseY);
            OVERLAY.render(event.getGuiGraphics(), mouseX, mouseY, event.getPartialTick());
        }

        @SubscribeEvent
        public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
            if (!OVERLAY.isVisible()) {
                pendingMouseOpenScreen = event.getScreen();
                pendingMouseOpenX = event.getMouseX();
                pendingMouseOpenY = event.getMouseY();
                return;
            }

            OVERLAY.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton());
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onMousePressedPost(ScreenEvent.MouseButtonPressed.Post event) {
            if (OVERLAY.isVisible()) {
                pendingMouseOpenScreen = null;
            }
        }

        @SubscribeEvent
        public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
            if (OVERLAY.isVisible()) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
            if (OVERLAY.isVisible()) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
            if (!OVERLAY.isVisible()) {
                return;
            }

            OVERLAY.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onTooltip(RenderTooltipEvent.Pre event) {
            if (OVERLAY.isVisible()) {
                event.setCanceled(true);
            }
        }
    }
}

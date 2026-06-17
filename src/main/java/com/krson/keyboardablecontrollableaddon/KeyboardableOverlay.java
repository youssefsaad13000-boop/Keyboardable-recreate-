package com.krson.keyboardablecontrollableaddon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class KeyboardableOverlay {
    private static final Logger LOGGER = LogManager.getLogger("keyboardable");
    private enum Page {
        LETTERS,
        SYMBOLS
    }

    private enum Action {
        CHAR,
        SPACE,
        KEY,
        SHIFT,
        CAPS,
        PAGE,
        CLOSE
    }

    private record KeySpec(String label, String output, float widthUnits, Action action, int keyCode) {
        static KeySpec ch(String value) {
            return new KeySpec(value, value, 1.0f, Action.CHAR, 0);
        }

        static KeySpec key(String label, float widthUnits, int keyCode) {
            return new KeySpec(label, null, widthUnits, Action.KEY, keyCode);
        }

        static KeySpec action(String label, float widthUnits, Action action) {
            return new KeySpec(label, null, widthUnits, action, 0);
        }
    }

    private static final KeySpec[][] LETTER_LAYOUT = new KeySpec[][] {
            {
                    KeySpec.ch("1"), KeySpec.ch("2"), KeySpec.ch("3"), KeySpec.ch("4"), KeySpec.ch("5"), KeySpec.ch("6"), KeySpec.ch("7"), KeySpec.ch("8"), KeySpec.ch("9"), KeySpec.ch("0"),
                    KeySpec.ch("-"), KeySpec.ch("="), KeySpec.key("BKSP", 2.0f, GLFW.GLFW_KEY_BACKSPACE)
            },
            {
                    KeySpec.ch("q"), KeySpec.ch("w"), KeySpec.ch("e"), KeySpec.ch("r"), KeySpec.ch("t"), KeySpec.ch("y"), KeySpec.ch("u"), KeySpec.ch("i"), KeySpec.ch("o"), KeySpec.ch("p"),
                    KeySpec.ch("["), KeySpec.ch("]"), KeySpec.ch("\\")
            },
            {
                    KeySpec.ch("a"), KeySpec.ch("s"), KeySpec.ch("d"), KeySpec.ch("f"), KeySpec.ch("g"), KeySpec.ch("h"), KeySpec.ch("j"), KeySpec.ch("k"), KeySpec.ch("l"), KeySpec.ch(";"),
                    KeySpec.ch("'"), KeySpec.key("ENTER", 2.0f, GLFW.GLFW_KEY_ENTER)
            },
            {
                    KeySpec.action("SHIFT", 1.5f, Action.SHIFT), KeySpec.ch("z"), KeySpec.ch("x"), KeySpec.ch("c"), KeySpec.ch("v"), KeySpec.ch("b"), KeySpec.ch("n"), KeySpec.ch("m"),
                    KeySpec.ch(","), KeySpec.ch("."), KeySpec.ch("/"), KeySpec.action("SHIFT", 1.5f, Action.SHIFT)
            },
            {
                    KeySpec.action("123!", 2.25f, Action.PAGE), KeySpec.key("TAB", 1.25f, GLFW.GLFW_KEY_TAB), KeySpec.action("CAPS", 1.5f, Action.CAPS),
                    new KeySpec("SPACE", " ", 3.5f, Action.SPACE, 0), KeySpec.action("CLOSE", 2.25f, Action.CLOSE)
            }
    };

    private static final KeySpec[][] SYMBOL_LAYOUT = new KeySpec[][] {
            {
                KeySpec.ch("1"), KeySpec.ch("2"), KeySpec.ch("3"), KeySpec.ch("4"), KeySpec.ch("5"), KeySpec.ch("6"), KeySpec.ch("7"), KeySpec.ch("8"), KeySpec.ch("9"), KeySpec.ch("0"),
                KeySpec.ch("_"), KeySpec.ch("+"), KeySpec.key("BKSP", 2.0f, GLFW.GLFW_KEY_BACKSPACE)
            },
            {
                    KeySpec.ch("~"), KeySpec.ch("`"), KeySpec.ch("{"), KeySpec.ch("}"), KeySpec.ch("|"), KeySpec.ch(":"), KeySpec.ch("\""), KeySpec.ch("<"), KeySpec.ch(">"), KeySpec.ch("?"),
                    KeySpec.ch("/"), KeySpec.ch("\\"), KeySpec.ch("=")
            },
            {
                    KeySpec.ch("["), KeySpec.ch("]"), KeySpec.ch(";"), KeySpec.ch("'"), KeySpec.ch(","), KeySpec.ch("."), KeySpec.ch("-"), KeySpec.ch("+"), KeySpec.ch("*"), KeySpec.ch("$"),
                    KeySpec.ch("#"), KeySpec.key("ENTER", 2.0f, GLFW.GLFW_KEY_ENTER)
            },
            {
                    KeySpec.ch("!"), KeySpec.ch("@"), KeySpec.ch("#"), KeySpec.ch("$"), KeySpec.ch("%"), KeySpec.ch("^"), KeySpec.ch("&"), KeySpec.ch("*"), KeySpec.ch("("), KeySpec.ch(")"),
                    KeySpec.ch("~"), KeySpec.action("ABC", 1.5f, Action.PAGE)
            },
            {
                    KeySpec.action("ABC", 2.25f, Action.PAGE), KeySpec.key("TAB", 1.25f, GLFW.GLFW_KEY_TAB), KeySpec.action("CAPS", 1.5f, Action.CAPS),
                    new KeySpec("SPACE", " ", 3.5f, Action.SPACE, 0), KeySpec.action("CLOSE", 2.25f, Action.CLOSE)
            }
    };

    private final List<Button> buttons = new ArrayList<>();
    private final List<List<Button>> buttonGrid = new ArrayList<>();

    private Minecraft minecraft;
    private Screen screen;
    private GuiEventListener targetWidget;
    private boolean visible;
    private boolean shift;
    private boolean caps;
    private Page page = Page.LETTERS;
    private boolean dpadFocus;
    private int focusedRow;
    private int focusedCol;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private double lastMouseX = -1;
    private double lastMouseY = -1;

    public boolean isVisible() {
        return this.visible;
    }

    public void open(Minecraft minecraft, Screen screen) {
        this.visible = true;
        this.minecraft = minecraft;
        this.shift = false;
        this.caps = false;
        GuiEventListener existingTarget = this.targetWidget;
        Screen existingScreen = this.screen;
        this.screen = screen;
        if (existingScreen != screen || existingTarget == null || isKeyboardButton(existingTarget)) {
            GuiEventListener textTarget = findTextInputTarget(screen);
            this.targetWidget = textTarget != null ? textTarget : screen.getFocused();
        }
        this.dpadFocus = true;
        this.focusedRow = 0;
        this.focusedCol = 0;
        rebuild(minecraft, screen);
    }

    public void close(boolean clearModifiers) {
        focusTarget();
        this.visible = false;
        this.buttons.clear();
        this.buttonGrid.clear();
        this.dpadFocus = false;
        if (clearModifiers) {
            this.shift = false;
            this.caps = false;
            this.page = Page.LETTERS;
        }
    }

    public void rebuild(Minecraft minecraft, Screen screen) {
        this.minecraft = minecraft;
        this.screen = screen;
        this.buttons.clear();
        this.buttonGrid.clear();

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        this.panelWidth = Math.min(430, screenWidth - 12);
        this.panelHeight = Math.min(170, screenHeight / 2);
        this.panelX = (screenWidth - this.panelWidth) / 2;
        this.panelY = Math.max(8, screenHeight - this.panelHeight - 28);

        KeySpec[][] layout = this.page == Page.LETTERS ? LETTER_LAYOUT : SYMBOL_LAYOUT;
        int rowHeight = 20;
        int rowGap = 4;
        int top = this.panelY + 26;
        int sidePadding = 8;

        for (int rowIndex = 0; rowIndex < layout.length; rowIndex++) {
            KeySpec[] row = layout[rowIndex];
            float totalUnits = 0.0f;
            for (KeySpec key : row) {
                totalUnits += key.widthUnits;
            }

            int usableWidth = this.panelWidth - sidePadding * 2;
            int gap = 3;
            int totalGap = gap * (row.length - 1);
            float unitWidth = (float) (usableWidth - totalGap) / totalUnits;
            int x = this.panelX + sidePadding;
            int y = top + rowIndex * (rowHeight + rowGap);
            List<Button> buttonRow = new ArrayList<>();

            for (KeySpec spec : row) {
                int width = Math.round(spec.widthUnits * unitWidth);
                String label = resolveLabel(spec);
                Button button = Button.builder(Component.literal(label), b -> onPress(spec)).bounds(x, y, width, rowHeight).build();
                this.buttons.add(button);
                buttonRow.add(button);
                x += width + gap;
            }

            this.buttonGrid.add(buttonRow);
        }

        this.dpadFocus = true;
        clampFocus();
    }

    public void refreshTarget(Screen screen) {
        this.screen = screen;
        if (isJeiSearchField(this.targetWidget)) {
            // Preserve explicit JEI targeting; its focus can differ from Screen#getFocused.
            return;
        }
        GuiEventListener textTarget = findTextInputTarget(screen);
        if (textTarget != null && !isKeyboardButton(textTarget)) {
            this.targetWidget = textTarget;
        }
    }

    public boolean hasTextTarget(Screen screen) {
        if (screen == null) {
            return false;
        }
        if (isSignScreen(screen)) {
            return true;
        }
        return findTextInputTarget(screen) != null;
    }

    public boolean handleTextTargetClick(Screen screen, double mouseX, double mouseY) {
        if (screen == null) {
            return false;
        }
        if (isSignScreen(screen)) {
            return true;
        }

        GuiEventListener clickedTarget = findClickedTextInputTarget(screen, mouseX, mouseY);
        if (clickedTarget == null || isKeyboardButton(clickedTarget)) {
            return false;
        }

        this.screen = screen;
        this.targetWidget = clickedTarget;
        return true;
    }

    private boolean isSignScreen(Screen screen) {
        String name = screen.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.contains("signedit");
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 500.0D);

        graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, 0xD0202020);
        graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + 18, 0xE0303030);

        int previewHeight = 18;
        int previewY = Math.max(4, this.panelY - previewHeight - 6);
        int previewX = this.panelX;
        int previewWidth = this.panelWidth;
        graphics.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xD0181818);
        graphics.fill(previewX + 1, previewY + 1, previewX + previewWidth - 1, previewY + previewHeight - 1, 0xE0282828);

        String previewText = getPreviewText();
        int textAreaX = previewX + 6;
        int textAreaWidth = Math.max(0, previewWidth - 12);
        String trimmedPreview = this.minecraft.font.plainSubstrByWidth(previewText, textAreaWidth);
        graphics.drawString(this.minecraft.font, trimmedPreview, textAreaX, previewY + 5, 0xF0F0F0, false);

        String title = this.page == Page.LETTERS ? "Keyboardable" : "Keyboardable - Symbols";
        graphics.drawString(this.minecraft.font, title, this.panelX + 8, this.panelY + 6, 0xFFFFFF, false);
        graphics.drawString(this.minecraft.font, "D-Pad Move  A Select  B Close", this.panelX + this.panelWidth - 158, this.panelY + 6, 0xC8C8C8, false);

        clampFocus();
        for (Button button : this.buttons) {
            button.setFocused(false);
        }
        if (this.dpadFocus && !this.buttonGrid.isEmpty()) {
            this.buttonGrid.get(this.focusedRow).get(this.focusedCol).setFocused(true);
        }

        for (Button button : this.buttons) {
            button.render(graphics, mouseX, mouseY, partialTick);
        }

        graphics.pose().popPose();
    }

    public void mouseMoved(double mouseX, double mouseY) {
        if (mouseX != this.lastMouseX || mouseY != this.lastMouseY) {
            this.dpadFocus = false;
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
        }
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible) {
            return;
        }
        this.dpadFocus = false;
        for (Button keyboardButton : new ArrayList<>(this.buttons)) {
            if (keyboardButton.mouseClicked(mouseX, mouseY, button)) {
                return;
            }
        }
        // Click didn't hit a keyboard button — check if it hit a text input so we can retarget
        if (this.screen != null) {
            boolean outsidePanel = mouseX < this.panelX || mouseX > this.panelX + this.panelWidth
                    || mouseY < this.panelY || mouseY > this.panelY + this.panelHeight;
            if (outsidePanel) {
                GuiEventListener clicked = findClickedTextInputTarget(this.screen, mouseX, mouseY);
                if (clicked != null && clicked != this.targetWidget) {
                    this.targetWidget = clicked;
                    LOGGER.info("[keyboardable] Retargeted to {} via hit detection", clicked.getClass().getSimpleName());
                } else if (clicked == null) {
                    // Hit detection failed (common for JEI) — if JEI search exists and click is outside
                    // the current target's area, switch to JEI
                    GuiEventListener jeiField = findJeiSearchField();
                    if (jeiField != null && jeiField != this.targetWidget && isTextInputListener(jeiField)) {
                        this.targetWidget = jeiField;
                        LOGGER.info("[keyboardable] Retargeted to JEI search field via fallback");
                    }
                }
            }
        }
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.visible) {
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            navigateUp();
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            navigateDown();
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            navigateLeft();
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            navigateRight();
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            activateFocused();
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close(true);
        }
    }

    public void navigateUp() {
        this.dpadFocus = true;
        if (this.buttonGrid.isEmpty()) {
            return;
        }
        int rows = this.buttonGrid.size();
        this.focusedRow = (this.focusedRow - 1 + rows) % rows;
        clampFocus();
    }

    public void navigateDown() {
        this.dpadFocus = true;
        if (this.buttonGrid.isEmpty()) {
            return;
        }
        int rows = this.buttonGrid.size();
        this.focusedRow = (this.focusedRow + 1) % rows;
        clampFocus();
    }

    public void navigateLeft() {
        this.dpadFocus = true;
        if (this.buttonGrid.isEmpty()) {
            return;
        }
        clampFocus();
        int maxCol = this.buttonGrid.get(this.focusedRow).size() - 1;
        this.focusedCol = (this.focusedCol - 1 + maxCol + 1) % (maxCol + 1);
        clampFocus();
    }

    public void navigateRight() {
        this.dpadFocus = true;
        if (this.buttonGrid.isEmpty()) {
            return;
        }
        clampFocus();
        int maxCol = this.buttonGrid.get(this.focusedRow).size() - 1;
        this.focusedCol = (this.focusedCol + 1) % (maxCol + 1);
    }

    public void activateFocused() {
        this.dpadFocus = true;
        clampFocus();
        if (!this.buttonGrid.isEmpty()) {
            this.buttonGrid.get(this.focusedRow).get(this.focusedCol).onPress();
        }
    }

    public void backspace() {
        sendKey(GLFW.GLFW_KEY_BACKSPACE);
    }

    public void toggleShift() {
        this.shift = !this.shift;
        rebuild(this.minecraft, this.screen);
    }

    public void toggleCaps() {
        this.caps = !this.caps;
        rebuild(this.minecraft, this.screen);
    }

    public void togglePage() {
        this.page = this.page == Page.LETTERS ? Page.SYMBOLS : Page.LETTERS;
        rebuild(this.minecraft, this.screen);
    }

    private void onPress(KeySpec spec) {
        switch (spec.action) {
            case CHAR -> sendText(resolveOutput(spec));
            case SPACE -> sendText(" ");
            case KEY -> sendKey(spec.keyCode);
            case SHIFT -> toggleShift();
            case CAPS -> toggleCaps();
            case PAGE -> togglePage();
            case CLOSE -> close(true);
        }
    }

    private String resolveLabel(KeySpec spec) {
        if (spec.action == Action.CAPS) {
            return this.caps ? "LS CAPS*" : "LS CAPS";
        }
        if (spec.action == Action.SHIFT) {
            return this.shift ? "Y SHIFT*" : "Y SHIFT";
        }
        if (spec.action == Action.PAGE) {
            return this.page == Page.LETTERS ? "LT 123!" : "LT ABC";
        }
        if (spec.action == Action.CLOSE) {
            return "B CLOSE";
        }
        if (spec.action != Action.CHAR || spec.output == null || spec.output.length() != 1) {
            return spec.label;
        }
        char ch = spec.output.charAt(0);
        if (!Character.isLetter(ch)) {
            return spec.label;
        }
        return useUppercase() ? spec.output.toUpperCase(Locale.ROOT) : spec.output.toLowerCase(Locale.ROOT);
    }

    private String resolveOutput(KeySpec spec) {
        if (spec.output == null) {
            return "";
        }
        if (spec.output.length() != 1) {
            return spec.output;
        }
        char ch = spec.output.charAt(0);
        if (!Character.isLetter(ch)) {
            return spec.output;
        }
        return useUppercase() ? spec.output.toUpperCase(Locale.ROOT) : spec.output.toLowerCase(Locale.ROOT);
    }

    private boolean useUppercase() {
        return this.caps ^ this.shift;
    }

    private void sendText(String text) {
        if (this.minecraft == null || this.screen == null) {
            return;
        }

        if (isJeiSearchField(this.targetWidget)) {
            String current = getJeiFilterText();
            // current may be null if read failed; treat as empty so we still use the JEI path
            String baseline = current != null ? current : "";
            if (!setJeiFilterText(baseline + text)) {
                LOGGER.warn("[keyboardable] setJeiFilterText also failed; input lost");
            }
            if (this.shift) {
                this.shift = false;
                rebuild(this.minecraft, this.screen);
            }
            return;
        }

        focusTarget();
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (!this.screen.charTyped(character, 0)) {
                sendCharToTarget(character);
            }
        }

        if (this.shift) {
            this.shift = false;
            rebuild(this.minecraft, this.screen);
        }
    }

    private void sendKey(int keyCode) {
        if (this.minecraft == null || this.screen == null) {
            return;
        }

        if (isJeiSearchField(this.targetWidget) && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            String current = getJeiFilterText();
            if (current != null && !current.isEmpty()) {
                int newEnd = current.offsetByCodePoints(current.length(), -1);
                setJeiFilterText(current.substring(0, newEnd));
            }
            return;
        }

        focusTarget();
        if (!this.screen.keyPressed(keyCode, 0, 0)) {
            sendKeyToTarget(keyCode);
        }
    }

    private void focusTarget() {
        if (this.screen != null && this.targetWidget != null && !isKeyboardButton(this.targetWidget)) {
            if (isJeiSearchField(this.targetWidget)) {
                return;
            }

            this.screen.setFocused(this.targetWidget);
            if (this.targetWidget instanceof EditBox editBox && !editBox.isFocused()) {
                editBox.setFocused(true);
            } else {
                setListenerFocused(this.targetWidget, true);
            }
        }
    }

    private GuiEventListener findTextInputTarget(Screen screen) {
        if (screen == null) {
            return null;
        }

        GuiEventListener jeiSearchField = findJeiSearchField();
        if (isTextInputListener(jeiSearchField) && isListenerFocused(jeiSearchField)) {
            return jeiSearchField;
        }

        GuiEventListener focused = screen.getFocused();
        if (isTextInputListener(focused)) {
            return focused;
        }

        for (GuiEventListener child : screen.children()) {
            if (isTextInputListener(child) && isListenerFocused(child)) {
                return child;
            }
        }

        if (isTextInputListener(jeiSearchField)) {
            return jeiSearchField;
        }

        return null;
    }

    private GuiEventListener findClickedTextInputTarget(Screen screen, double mouseX, double mouseY) {
        GuiEventListener jeiSearchField = findJeiSearchField();
        if (isTextInputListener(jeiSearchField) && isListenerHit(jeiSearchField, mouseX, mouseY)) {
            return jeiSearchField;
        }

        GuiEventListener focused = screen.getFocused();
        if (isTextInputListener(focused) && isListenerHit(focused, mouseX, mouseY)) {
            return focused;
        }

        for (GuiEventListener child : screen.children()) {
            if (isTextInputListener(child) && isListenerHit(child, mouseX, mouseY)) {
                return child;
            }
        }

        return null;
    }

    private GuiEventListener findJeiSearchField() {
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Method getRuntimeMethod = internalClass.getMethod("getJeiRuntime");
            Object runtime = getRuntimeMethod.invoke(null);
            if (runtime == null) {
                return null;
            }

            Method getIngredientListOverlayMethod = runtime.getClass().getMethod("getIngredientListOverlay");
            Object ingredientListOverlay = getIngredientListOverlayMethod.invoke(runtime);
            if (ingredientListOverlay == null) {
                return null;
            }

            Field searchField = ingredientListOverlay.getClass().getDeclaredField("searchField");
            searchField.setAccessible(true);
            Object value = searchField.get(ingredientListOverlay);
            if (value instanceof GuiEventListener listener) {
                return listener;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }

    private boolean sendCharToTarget(char character) {
        return this.targetWidget != null && !isKeyboardButton(this.targetWidget) && this.targetWidget.charTyped(character, 0);
    }

    private boolean sendKeyToTarget(int keyCode) {
        return this.targetWidget != null && !isKeyboardButton(this.targetWidget) && this.targetWidget.keyPressed(keyCode, 0, 0);
    }

    private String getPreviewText() {
        if (isJeiSearchField(this.targetWidget)) {
            String jeiText = getJeiFilterText();
            return jeiText != null ? jeiText : "";
        }

        GuiEventListener previewTarget = this.targetWidget;
        if (!isTextInputListener(previewTarget)) {
            previewTarget = findTextInputTarget(this.screen);
        }

        String value = readTextFromListener(previewTarget);
        return value != null ? value : "";
    }

    private String readTextFromListener(GuiEventListener listener) {
        if (listener == null) {
            return "";
        }
        if (listener instanceof EditBox editBox) {
            return editBox.getValue();
        }

        String directValue = invokeStringGetter(listener, "getValue");
        if (directValue != null) {
            return directValue;
        }

        String directText = invokeStringGetter(listener, "getText");
        if (directText != null) {
            return directText;
        }

        String obfValue = invokeStringGetter(listener, "m_94155_");
        if (obfValue != null) {
            return obfValue;
        }

        String obfText = invokeStringGetter(listener, "m_94173_");
        if (obfText != null) {
            return obfText;
        }

        String textFromMessage = invokeComponentGetter(listener, "getMessage");
        if (textFromMessage != null) {
            return textFromMessage;
        }

        return "";
    }

    private boolean writeTextToListener(GuiEventListener listener, String value) {
        if (listener == null) {
            return false;
        }
        if (listener instanceof EditBox editBox) {
            editBox.setValue(value);
            return true;
        }

        if (invokeStringSetter(listener, "setValue", value)) {
            return true;
        }
        if (invokeStringSetter(listener, "setText", value)) {
            return true;
        }

        // Obfuscated fallback for environments where MCP names are unavailable.
        if (invokeStringSetter(listener, "m_94164_", value)) {
            return true;
        }

        return invokeAnyStringSetter(listener, value);
    }

    private String invokeStringGetter(GuiEventListener listener, String methodName) {
        try {
            Method method = listener.getClass().getMethod(methodName);
            Object value = method.invoke(listener);
            if (value instanceof String text) {
                return text;
            }
            if (value instanceof CharSequence sequence) {
                return sequence.toString();
            }
        } catch (ReflectiveOperationException ignored) {
            try {
                Method method = listener.getClass().getDeclaredMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(listener);
                if (value instanceof String text) {
                    return text;
                }
                if (value instanceof CharSequence sequence) {
                    return sequence.toString();
                }
            } catch (ReflectiveOperationException ignoredToo) {
            }
        }
        return null;
    }

    private String invokeComponentGetter(GuiEventListener listener, String methodName) {
        try {
            Method method = listener.getClass().getMethod(methodName);
            Object value = method.invoke(listener);
            if (value instanceof Component component) {
                return component.getString();
            }
            if (value instanceof String text) {
                return text;
            }
            if (value instanceof CharSequence sequence) {
                return sequence.toString();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private boolean invokeStringSetter(GuiEventListener listener, String methodName, String value) {
        try {
            Method method = listener.getClass().getMethod(methodName, String.class);
            method.invoke(listener, value);
            return true;
        } catch (ReflectiveOperationException ignored) {
            try {
                Method method = listener.getClass().getDeclaredMethod(methodName, String.class);
                method.setAccessible(true);
                method.invoke(listener, value);
                return true;
            } catch (ReflectiveOperationException ignoredToo) {
                return false;
            }
        }
    }

    private boolean invokeAnyStringSetter(GuiEventListener listener, String value) {
        String before = readTextFromListener(listener);
        for (Method method : listener.getClass().getMethods()) {
            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != String.class) {
                continue;
            }
            try {
                method.invoke(listener, value);
                String after = readTextFromListener(listener);
                if (value.equals(after) || (!value.isEmpty() && after != null && after.contains(value))) {
                    return true;
                }
                if (!String.valueOf(before).equals(String.valueOf(after))) {
                    return true;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return false;
    }

    private boolean isTextInputListener(GuiEventListener listener) {
        if (listener == null || isKeyboardButton(listener)) {
            return false;
        }
        if (listener instanceof EditBox) {
            return true;
        }

        String className = listener.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (className.contains("editbox")
                || className.contains("textfield")
                || className.contains("searchfield")
                || className.contains("searchbar")
                || className.contains("filter")) {
            return true;
        }

        return hasMethod(listener.getClass(), "setValue", String.class)
                || hasMethod(listener.getClass(), "setText", String.class)
                || hasMethod(listener.getClass(), "getValue")
                || hasMethod(listener.getClass(), "getText");
    }

    private boolean isListenerFocused(GuiEventListener listener) {
        if (listener instanceof EditBox editBox) {
            return editBox.isFocused();
        }

        try {
            Method method = listener.getClass().getMethod("isFocused");
            Object result = method.invoke(listener);
            return result instanceof Boolean focused && focused;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean isListenerHit(GuiEventListener listener, double mouseX, double mouseY) {
        if (listener == null) {
            return false;
        }

        if (isJeiSearchFieldHit(listener, mouseX, mouseY)) {
            return true;
        }

        try {
            return listener.isMouseOver(mouseX, mouseY);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isJeiSearchFieldHit(GuiEventListener listener, double mouseX, double mouseY) {
        if (!isJeiSearchField(listener)) {
            return false;
        }

        try {
            Field areaField = listener.getClass().getDeclaredField("area");
            areaField.setAccessible(true);
            Object area = areaField.get(listener);
            if (area == null) {
                return false;
            }

            Method containsMethod = area.getClass().getMethod("contains", double.class, double.class);
            Object result = containsMethod.invoke(area, mouseX, mouseY);
            return result instanceof Boolean contains && contains;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean isJeiSearchField(GuiEventListener listener) {
        return listener != null && "mezz.jei.gui.input.GuiTextFieldFilter".equals(listener.getClass().getName());
    }

    private String getJeiFilterText() {
        GuiEventListener jeiSearchField = findJeiSearchField();
        if (jeiSearchField == null) {
            return null;
        }
        return readTextFromListener(jeiSearchField);
    }

    private boolean setJeiFilterText(String text) {
        GuiEventListener jeiSearchField = findJeiSearchField();
        if (jeiSearchField == null) {
            return false;
        }
        return writeTextToListener(jeiSearchField, text);
    }

    private void setListenerFocused(GuiEventListener listener, boolean focused) {
        if (listener == null) {
            return;
        }

        try {
            Method method = listener.getClass().getMethod("setFocused", boolean.class);
            method.invoke(listener, focused);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private boolean hasMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method = type.getMethod(name, parameterTypes);
            return method != null;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private boolean isKeyboardButton(GuiEventListener listener) {
        return this.buttons.contains(listener);
    }

    private void clampFocus() {
        if (this.buttonGrid.isEmpty()) {
            this.focusedRow = 0;
            this.focusedCol = 0;
            return;
        }
        this.focusedRow = Math.max(0, Math.min(this.focusedRow, this.buttonGrid.size() - 1));
        int maxCol = this.buttonGrid.get(this.focusedRow).size() - 1;
        this.focusedCol = Math.max(0, Math.min(this.focusedCol, maxCol));
    }
}

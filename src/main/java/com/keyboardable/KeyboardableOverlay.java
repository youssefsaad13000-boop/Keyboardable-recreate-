package com.keyboardable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class KeyboardableOverlay {
    private static final String[] ENGLISH_LAYOUT = {
            "1234567890",
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm",
            "  ",
    };

    private static final String[] ARABIC_LAYOUT = {
            "١٢٣٤٥٦٧٨٩٠",
            "ضصثقفغعهخ",
            "حجشسيبلاتن",
            "مكطئءؤإأآ",
            "  ",
    };

    private final List<Button> buttons = new ArrayList<>();
    private final List<List<Button>> rows = new ArrayList<>();
    private Screen screen;
    private boolean visible;
    private boolean arabicLayout;
    private int focusedRow;
    private int focusedCol;

    public boolean isVisible() {
        return visible;
    }

    public Screen getScreen() {
        return screen;
    }

    public void open(Screen screen) {
        this.screen = screen;
        this.visible = true;
        rebuild();
    }

    public void close() {
        this.visible = false;
        this.buttons.clear();
        this.rows.clear();
    }

    public void toggleLayout() {
        this.arabicLayout = !this.arabicLayout;
        if (visible) {
            rebuild();
        }
    }

    public void render(DrawContext context, float delta) {
        if (!visible || screen == null) {
            return;
        }

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        int panelWidth = Math.min(800, width - 40);
        int panelHeight = 180;
        int panelX = (width - panelWidth) / 2;
        int panelY = height - panelHeight - 16;

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xC0000000);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 20, 0xD0303030);
        context.drawCenteredTextWithShadow(Minecraft.getInstance().textRenderer, Component.literal(arabicLayout ? "Arabic" : "English"), panelX + panelWidth / 2, panelY + 6, 0xFFFFFF);

        if (buttons.isEmpty()) {
            rebuild();
        }

        for (Button button : buttons) {
            button.render(context, 0, 0, delta);
        }
    }

    private void rebuild() {
        buttons.clear();
        rows.clear();

        if (screen == null) {
            return;
        }

        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int panelWidth = Math.min(800, width - 40);
        int panelHeight = 180;
        int panelX = (width - panelWidth) / 2;
        int panelY = height - panelHeight - 16;

        String[] rowsDef = arabicLayout ? ARABIC_LAYOUT : ENGLISH_LAYOUT;
        int keySize = 48;
        int gap = 6;
        int totalContentWidth = 0;
        for (String row : rowsDef) {
            totalContentWidth = Math.max(totalContentWidth, row.length() * keySize + (row.length() - 1) * gap);
        }

        int startX = panelX + (panelWidth - totalContentWidth) / 2;
        int startY = panelY + 26;

        for (int rowIndex = 0; rowIndex < rowsDef.length; rowIndex++) {
            String rowText = rowsDef[rowIndex];
            List<Button> rowButtons = new ArrayList<>();
            for (int i = 0; i < rowText.length(); i++) {
                char ch = rowText.charAt(i);
                String label = String.valueOf(ch);
                String text = label;
                int x = startX + i * (keySize + gap);
                int y = startY + rowIndex * (keySize + gap);
                int w = keySize;
                int h = keySize;

                Button button = Button.builder(Component.literal(text), b -> onButtonPress(text)).bounds(x, y, w, h).build();
                buttons.add(button);
                rowButtons.add(button);
            }
            rows.add(rowButtons);
        }
    }

    private void onButtonPress(String text) {
        if (screen == null) {
            return;
        }
        String output = arabicLayout ? ArabicTextUtils.formatForDisplay(text) : text;
        injectText(output);
    }

    private void injectText(String text) {
        if (screen == null) {
            return;
        }
        if (screen.getFocused() instanceof EditBox box) {
            box.insertText(text);
            if (box.isFocused()) {
                box.setCursorPosition(box.getCursorPosition() + text.length());
            }
            return;
        }

        if (screen.getFocused() != null) {
            try {
                var method = screen.getFocused().getClass().getMethod("insertText", String.class);
                method.invoke(screen.getFocused(), text);
            } catch (ReflectiveOperationException ignored) {}
        }
    }

    public void handleMouseClick(double mouseX, double mouseY, int button) {
        if (!visible || screen == null) {
            return;
        }
        for (Button buttonWidget : buttons) {
            if (buttonWidget.isMouseOver(mouseX, mouseY)) {
                buttonWidget.onPress();
                return;
            }
        }
    }

    public void onFocused(Screen screen) {
        if (screen == null) {
            return;
        }
        this.screen = screen;
        if (screen.getFocused() != null && KeyboardableClient.shouldAutoOpen(screen)) {
            open(screen);
        }
    }
}

package com.keyboardable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.StringUtil;

import java.text.Bidi;
import java.util.Locale;

public final class ArabicTextUtils {
    private ArabicTextUtils() {}

    public static boolean containsArabic(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isArabicLetter(ch)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isArabicLetter(char ch) {
        return ch >= 0x0621 && ch <= 0x06FF
                || ch >= 0x0750 && ch <= 0x077F
                || ch >= 0x08A0 && ch <= 0x08FF;
    }

    public static String shapeText(String text) {
        if (text == null || text.isEmpty() || !containsArabic(text)) {
            return text;
        }

        StringBuilder output = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isArabicLetter(ch)) {
                output.append(shapeGlyph(ch, i, text));
            } else {
                output.append(ch);
            }
        }
        return output.toString();
    }

    private static char shapeGlyph(char ch, int index, String text) {
        if (index > 0 && isArabicLetter(text.charAt(index - 1))) {
            return ch;
        }
        if (index + 1 < text.length() && isArabicLetter(text.charAt(index + 1))) {
            return ch;
        }
        return ch;
    }

    public static String applyBidi(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (!containsArabic(text)) {
            return text;
        }

        StringBuilder builder = new StringBuilder();
        int start = 0;
        while (start < text.length()) {
            int end = start;
            while (end < text.length() && isArabicLetter(text.charAt(end))) {
                end++;
            }
            if (end > start) {
                String segment = text.substring(start, end);
                builder.append("\u202B");
                builder.append(new StringBuilder(segment).reverse());
                builder.append("\u202C");
                start = end;
            } else {
                builder.append(text.charAt(start));
                start++;
            }
        }
        return builder.toString();
    }

    public static String formatForDisplay(String text) {
        if (text == null) {
            return "";
        }
        String shaped = shapeText(text);
        return applyBidi(shaped);
    }
}

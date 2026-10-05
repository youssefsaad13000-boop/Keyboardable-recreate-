# Keyboardable - Touch Keyboard for Fabric 1.21.x

A redesigned client-side mod for Minecraft Fabric that provides a touchscreen-friendly on-screen keyboard, automatic text field detection, and Arabic/RTL support.

## Features
- Automatic keyboard opening when a text field receives focus
- Automatic dismissal when focus is lost or the screen changes
- Touch-friendly keyboard buttons that work with taps/clicks and pointer events
- English and Arabic keyboard layouts with a quick toggle
- Automatic Arabic shaping and RTL display helpers for connected Arabic text
- No Controllable dependency

## Supported versions
- Minecraft: 1.21.x family (targeting 1.21.1 / 1.21.11 compatibility expectations)
- Fabric Loader: 0.16.10+
- Fabric API: 0.115.4+

## Notes
This version has been redesigned around Fabric and removes the previous Forge/Controllable architecture from the original project.

## Usage
1. Open a text field in-game.
2. The keyboard appears automatically.
3. Tap keys to type.
4. Use the language toggle to switch between English and Arabic.
5. When the field loses focus, the keyboard closes automatically.

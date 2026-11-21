# Changelog

## 0.5.7

- new Update goome button: checks the newest goome for your project and bumps it, it is in the goome tool window and Tools > goome, asks before it changes anything and before it runs the restore command
- needs goome-cli 0.5.7, the button offers to update the cli for you

## 0.5.5

- the docs buttons, quick docs and the vendor link point at goome.wtf now, the old github.io links were broken
- Build for Web tells you it only works for C apps when you run it on another language, and says why
- new window flag GOOME_WINDOW_NO_CONSOLE
- quick docs no longer sends a few functions to a page that doesnt exist

## 0.5.3

- Build for Web is in the goome tool window now, under Quick actions after Open UI builder. it builds the .c file open in the editor, or lets goome-cli find main.c, src/main.c or app.c when none is open, and a CLI error shows up as a notification
- the right click menu entry also shows for .h files and in Rider

## 0.5.2

- live templates for Rust, JavaScript and TypeScript (Node), Go, Zig, Lua and GDScript, with the real names and argument orders of each binding. the same goome-* prefixes as the other languages, 58 of them (GDScript has goome-view instead of goome-offscreen, a GoomeView cant push a click)
- they show up in the IDE that has that languages plugin: RustRover or the Rust plugin, WebStorm or the JavaScript plugin, GoLand or the Go plugin, ZigBrains for Zig, EmmyLua for Lua and Godot Support (built into Rider) for GDScript. without the plugin they just arent loaded and nothing breaks
- goome: New Project offers all ten templates now: C, C#, Python, Java, Godot, Rust, Node, Go, Zig and Lua
- the goome tool window lists the templates for all ten languages and the cheat sheet follows .rs, .js, .ts, .go, .zig, .lua and .gd files
- quick docs also work on the binding names (ui.slider_int, ui.sliderInt, ui.SliderInt) in Rust, JavaScript, TypeScript, Go, Zig, Lua and GDScript files that mention goome
- GDScript, Rust, Node, Go, Zig and Lua buttons next to the other Copy code buttons in the preview, so all ten languages
- the readme has a table per language of what it gets and which IDE it needs. Build for Web is still C only
- new goome: Build for Web in Tools > goome, on the right click menu of .c files and in the project view. It runs goome build --web in the background and puts index.html, goome.js and goome.wasm in a web folder
- errors show up as a notification and the whole text goes to a goome build window at the bottom
- when it works the notification has Open in Browser, which starts goome serve and opens the page, and Show Folder
- goome: Stop Web Server in Tools > goome, the server also stops when you close the project
- new c-goome checkout setting in Settings > Tools > goome, it goes to goome build --web as --goome. Needed until the downloaded c-goome releases have the web build
- web builds need clang and lld with the wasm target, the error tells you how to install them

## 0.5.1

- the readme points at the marketplace now instead of saying its coming soon

## 0.5.0

- live templates for the 0.5 stuff in all four languages: goome-anim, goome-anim-color and goome-ease for animations, goome-text-input for ime text (GOOME_TEXT and GOOME_PREEDIT), goome-a11y for screen reader roles and values, goome-font-fallback and goome-text-dir
- hover docs and the builder come from goome 0.5.0

## 0.4.2

- the goome tool window finally has its own icon on the left stripe, the goo square with the drips. it was showing a blank placeholder in the new UI
- proper 20x20 and compact 16x16 icons for the new UI, light and dark, plus the old 13x13 one for the classic UI
- update notes show up on the marketplace now

## 0.4.1

- new goome tool window on the left, right by Project. quick actions, every .goome.json and .goome-theme.json in the project, templates per language and a snippet cheat sheet
- clicking a template opens New Project with that language and template already picked
- search docs can pop up from the tool window too
- dark version of the plugin icon
- releases go to the JetBrains marketplace on their own now

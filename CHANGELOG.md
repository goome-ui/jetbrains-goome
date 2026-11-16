# Changelog

## Unreleased

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

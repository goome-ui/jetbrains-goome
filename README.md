<p align="center">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="goome" width="150">
</p>

<h1 align="center">🧩 jetbrains-goome 🧩</h1>

<p align="center">
<b>goome in Rider, CLion, IntelliJ IDEA, PyCharm, RustRover, GoLand and WebStorm. Live templates for ten languages, a new project action with all ten templates, a live preview for your .goome.json and quick docs.</b>
</p>

<p align="center">
  <img src="https://goome.wtf/badges/jetbrains-2024-3.svg" alt="JetBrains 2024.3+">
  <a href="https://plugins.jetbrains.com/plugin/36174-goome"><img src="https://goome.wtf/badges/jetbrains-marketplace.svg" alt="JetBrains Marketplace"></a>
  <img src="https://goome.wtf/badges/langs-10.svg" alt="10 languages">
  <img src="https://goome.wtf/badges/license-gpl-3.svg" alt="GPL-3.0">
</p>

<p align="center">
  Type <code>goome</code> in a C, C#, Python, Java, Rust, JavaScript, TypeScript, Go, Zig, Lua or GDScript file and you get the usual goome stuff, already using the real names and argument orders of that binding. For C, C#, Python and Java its the same set as <a href="https://github.com/goome-ui/vscode-goome">vscode-goome</a>, so the names you learn there work here too. Which IDE gets what is in <b>Languages</b> below. Open a <code>.goome.json</code> from the UI builder and you can see it, and copy the code out of it, without leaving the IDE.<br> <br>
  Do you like this project? If so, please let me know!
  <a href="https://sevvy-wevvy.com/discord">Discord</a>
</p>

<p align="center">
  <a href="https://goome.wtf/docs/">Docs</a> &nbsp;·&nbsp;
  <a href="https://github.com/goome-ui/c-goome">goome</a> &nbsp;·&nbsp;
  <a href="https://goome.wtf/builder/">UI builder</a> &nbsp;·&nbsp;
  <a href="https://github.com/goome-ui/goome-cli">goome-cli</a>
</p>

<details>
<summary align="center"><b>⬇️ Easy Install ⬇️</b></summary>

<br>

<p align="center">
Its on the <a href="https://plugins.jetbrains.com/plugin/36174-goome">JetBrains Marketplace</a>. In your IDE open <b>Settings > Plugins</b>, search <b>goome</b> on the Marketplace tab, install it and restart when it asks.<br> <br>
Or from disk: grab <code>jetbrains-goome-x.y.z.zip</code> from the <a href="https://github.com/goome-ui/jetbrains-goome/releases/latest">latest release</a>, dont unzip it, then in <b>Settings > Plugins</b> click the gear and pick <b>Install Plugin from Disk...</b><br> <br>
Works in 2024.3 or newer.
</p>
</details>

<details>
<summary align="center"><b>🚀 How To Use 🚀</b></summary>

<br>

<p align="center">
<b>goome tool window</b> - the goome icon on the left stripe, right by Project. Quick actions, every <code>.goome.json</code> and <code>.goome-theme.json</code> in the project (click one to open it with the preview), the templates for each language (click one and New Project opens with it picked) and a snippet cheat sheet that drops the snippet into the file youre in<br>
<b>Live templates</b> - in all ten languages, as long as your IDE has that languages plugin. Type <code>goome</code> and pick one, or hit Tab after the full name. <code>goome-overlay</code> and <code>goome-app</code> are a whole program, <code>goome-loop</code> is the poll and pace loop, and theres one for every common widget, plus <code>goome-style</code>, <code>goome-custom</code>, <code>goome-offscreen</code>, <code>goome-hotkey</code>, <code>goome-theme-preset</code>, <code>goome-theme-make</code>, <code>goome-anim</code>, <code>goome-anim-color</code>, <code>goome-ease</code>, <code>goome-text-input</code>, <code>goome-a11y</code>, <code>goome-font-fallback</code> and <code>goome-text-dir</code>. Every language has 58 of them. GDScript has no <code>goome-offscreen</code> (a GoomeView cant push a click) and has <code>goome-view</code> instead<br>
<b>goome: New Project</b> - in <b>Tools > goome</b> or on the welcome screen. Pick one of the ten languages (C, C#, Python, Java, Godot, Rust, Node, Go, Zig, Lua), one of the four templates (app, overlay, mod-menu, hud), a folder and a name. It runs <a href="https://github.com/goome-ui/goome-cli">goome-cli</a> and opens what it made. If <code>goome</code> isnt on your PATH it downloads goome.py from the latest goome-cli release and runs that with Python, so you dont have to install anything besides Python<br>
<b>Preview</b> - open a <code>.goome.json</code> and you get the text on the left and the preview on the right. It draws the project with the UI builders own code and updates while you type. Its read only, <b>Open in UI builder</b> takes you to the real thing<br>
<b>Copy code</b> - the C, C#, Python, Java, GDScript, Rust, Node, Go, Zig and Lua buttons at the top of the preview put the builders generated code on your clipboard<br>
<b>Quick docs</b> - put the caret on any <code>goome_*</code> function and hit Ctrl+Q (F1 on mac) for its signature, the note from goome.h and a link to the docs. In Rust, JavaScript, TypeScript, Go, Zig, Lua and GDScript files that mention goome it works on the binding names too (<code>ui.slider_int</code>, <code>ui.sliderInt</code>, <code>ui.SliderInt</code>), matched to the C function by name<br>
<b>goome: Build for Web</b> - in <b>Tools > goome</b>, under <b>Quick actions</b> in the goome tool window, on the right click menu of a <code>.c</code> or <code>.h</code> file and in the project view. Runs <code>goome build --web</code> in the background, only for C apps, anything else gets a message saying so and leaves <code>index.html</code>, <code>goome.js</code> and <code>goome.wasm</code> in a <code>web</code> folder in your project. Errors come up as a notification and the full text goes to the <b>goome build</b> window at the bottom. When it works the notification has <b>Open in Browser</b> (starts <code>goome serve</code> on a free port and opens it) and <b>Show Folder</b>. <b>goome: Stop Web Server</b> stops it, closing the project does too. C only for now (the other languages are on the list), normal app windows only (no overlays or mod menus), and the app needs <code>goome_web_init</code> and <code>goome_web_frame</code>. You need clang and lld with the wasm target. Until the downloaded c-goome releases have the web build, point the c-goome checkout setting at a checkout of c-goome<br>
<b>goome: Search Docs</b> - in <b>Tools > goome</b>. A searchable list of every goome function, picks the one under the caret, opens its docs page<br>
<b>Schemas</b> - <code>.goome.json</code> and <code>.goome-theme.json</code> get checked while you edit them, so typos in widget types, theme fields and colors show up right away<br> <br>
Settings are in <b>Settings > Tools > goome</b>: your own <code>goome</code> or <code>goome.py</code>, the Python it runs with and the c-goome checkout for Build for Web.
</p>
</details>

<details>
<summary align="center"><b>🌍 Languages 🌍</b></summary>

<br>

<p align="center">
New Project works in every IDE and makes any of the ten. Live templates need the plugin that gives your IDE that language, without it the plugin just loads without them. Build for Web is C only for now. Quick docs for the C names work everywhere, the binding names only in the languages marked below. The goome tool window cheat sheet has all the snippets of the language of the file youre in.
</p>

<div align="center">

**C**

| What | Status | Needs |
| --- | --- | --- |
| Live templates | 58, in C and C++ files | CLion, or Rider (C++ layer) |
| Quick docs | yes, `goome_*` | CLion, any IDE except Rider (ReSharper keeps C there, use Search Docs) |
| Copy code | yes | any |
| New Project | yes | any |
| Build for Web | yes | any, needs clang and lld with wasm |

**C#**

| What | Status | Needs |
| --- | --- | --- |
| Live templates | 58, in C# files | Rider |
| Quick docs | no | n/a |
| Copy code | yes | any |
| New Project | yes | any |
| Build for Web | no | n/a |

**Python**

| What | Status | Needs |
| --- | --- | --- |
| Live templates | 58 | PyCharm, or IDEA with the Python plugin |
| Quick docs | no | n/a |
| Copy code | yes | any |
| New Project | yes | any |
| Build for Web | no | n/a |

**Java**

| What | Status | Needs |
| --- | --- | --- |
| Live templates | 58 | IntelliJ IDEA, or any IDE with the Java plugin |
| Quick docs | no | n/a |
| Copy code | yes | any |
| New Project | yes | any |
| Build for Web | no | n/a |

**Rust**

| What | Status | Needs |
| --- | --- | --- |
| Live templates | 58, in items and statements | RustRover, or CLion or IDEA Ultimate with the Rust plugin |
| Quick docs | yes, by name | any |
| Copy code | yes | any |
| New Project | yes | any |
| Build for Web | no | n/a |

**JavaScript and TypeScript (Node)**

| What | Status | Needs |
| --- | --- | --- |
| Live templates | 58, in .js and .ts files | WebStorm, or IDEA Ultimate with the JavaScript plugin |
| Quick docs | yes, by name | any |
| Copy code | yes | any |
| New Project | yes | any |
| Build for Web | no | n/a |

**Go**

| What | Status | Needs |
| --- | --- | --- |
| Live templates | 58, in files and statements | GoLand, or IDEA Ultimate with the Go plugin |
| Quick docs | yes, by name | any |
| Copy code | yes | any |
| New Project | yes | any |
| Build for Web | no | n/a |

**Zig**

| What | Status | Needs |
| --- | --- | --- |
| Live templates | 58, the plugin has no zig context so they sit in Other | the ZigBrains plugin |
| Quick docs | yes, by name | any |
| Copy code | yes | any |
| New Project | yes | any |
| Build for Web | no | n/a |

**Lua**

| What | Status | Needs |
| --- | --- | --- |
| Live templates | 58 | the EmmyLua plugin |
| Quick docs | yes, by name | any |
| Copy code | yes | any |
| New Project | yes | any |
| Build for Web | no | n/a |

**GDScript (Godot)**

| What | Status | Needs |
| --- | --- | --- |
| Live templates | 58 | Rider, which has Godot Support built in |
| Quick docs | yes, by name | any |
| Copy code | yes | any |
| New Project | yes | any |
| Build for Web | no | n/a |

</div>
</details>

<details>
<summary align="center"><b>🧭 What Works Where 🧭</b></summary>

<br>

<p align="center">
<b>Rider</b> - C#, C++ and GDScript templates (the GDScript ones through Godot Support, which Rider has built in), they ship as a ReSharper settings layer inside the plugin so the ReSharper side picks them up by itself. The C# and C++ ones are in the goome category. New Project, preview, copy code, schemas and Search Docs work too. Quick docs for C stay with ReSharper there, use Search Docs instead<br>
<b>CLion</b> - C templates in C and C++ files, quick docs, and everything else<br>
<b>IntelliJ IDEA</b> - Java templates, and everything else. With the Python plugin you get the Python ones too<br>
<b>PyCharm</b> - Python templates, and everything else<br>
<b>RustRover</b> - Rust templates, and everything else<br>
<b>GoLand</b> - Go templates, and everything else<br>
<b>WebStorm</b> - JavaScript and TypeScript templates, and everything else<br>
<b>Any of them</b> - plus the Zig templates with ZigBrains and the Lua ones with EmmyLua installed<br> <br>
The preview needs JCEF, which every JetBrains IDE has unless you turned it off. Without it .goome.json just opens as normal JSON.
</p>
</details>

<details>
<summary align="center"><b>🔨 Building It 🔨</b></summary>

<br>

<p align="center">
It wants <a href="https://github.com/goome-ui/docs">goome-ui/docs</a> and <a href="https://github.com/goome-ui/c-goome">c-goome</a> next to this folder, or in <code>deps/</code> (or <code>GOOME_DOCS</code> and <code>GOOME_C</code> set). The build copies the builder out of docs, makes the quick docs list out of goome.h, and turns <code>snippets/</code> into the live templates and the Rider settings layer. You need Java 21 or newer.
</p>

```sh
./gradlew buildPlugin
./gradlew test
./gradlew runIde
```

<p align="center">
The .zip ends up in <code>build/distributions/</code>. <code>runIde</code> opens a sandbox IntelliJ with the plugin loaded.
</p>
</details>

<details>
<summary align="center"><b>🛠 Pull Requests 🛠</b></summary>

<br>

<p align="center">
Want to help with jetbrains-goome? Make A pull request to <b>dev</b>!<br>
Pull request allow for anyone to request a change to this code. In turn meaning that <b>YOU</b> can contribute to this project.<br> <br>
As long as it works, and doesnt vary off too far from the main idea its very likely it get accepted!<br>
Read <a href="CONTRIBUTING.md">CONTRIBUTING.md</a> before you send one.
</p>
</details>

<details>
<summary align="center"><b>⚖️ License ⚖️</b></summary>

<br>

<p align="center">
jetbrains-goome is under the <a href="LICENSE">GPL-3.0</a>.<br> <br>
<sub>
• You can use it, change it and share it<br>
• If you share something made with it, you have to share your source too, under the same license<br>
• Keep the credits
</sub><br> <br>
In short: play fair, keep it open, and give credit where it's due.
</p>
</details>

<details>
<summary align="center"><b>🩷 Donate 🩷</b></summary>

<br>

<p align="center">
<a href="https://sevvy-wevvy.com/kofi">Ko-fi</a> (Preferred)<br>
<a href="https://sevvy-wevvy.com/patreon">Patreon</a><br>
<a href="https://sevvy-wevvy.com/throne">Throne</a>
</p>
</details>

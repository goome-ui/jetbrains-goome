<p align="center">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="goome" width="150">
</p>

<h1 align="center">🧩 jetbrains-goome 🧩</h1>

<p align="center">
<b>goome in Rider, CLion, IntelliJ IDEA and PyCharm. Live templates, a new project action, a live preview for your .goome.json and quick docs.</b>
</p>

<p align="center">
  <img src="https://goome-ui.github.io/docs/assets/badges/jetbrains-2024-3.svg" alt="JetBrains 2024.3+">
  <img src="https://goome-ui.github.io/docs/assets/badges/langs-c-csharp-python-java.svg" alt="C, C#, Python, Java">
  <img src="https://goome-ui.github.io/docs/assets/badges/license-gpl-3.svg" alt="GPL-3.0">
</p>

<p align="center">
  Type <code>goome</code> in a C, C#, Python or Java file and you get the usual goome stuff, already using the real names and argument orders. Its the same set as <a href="https://github.com/goome-ui/vscode-goome">vscode-goome</a>, so the names you learn there work here too. Open a <code>.goome.json</code> from the UI builder and you can see it, and copy the code out of it, without leaving the IDE.<br> <br>
  Do you like this project? If so, please let me know!
  <a href="https://sevvy-wevvy.com/discord">Discord</a>
</p>

<p align="center">
  <a href="https://goome-ui.github.io/docs/">Docs</a> &nbsp;·&nbsp;
  <a href="https://github.com/goome-ui/c-goome">goome</a> &nbsp;·&nbsp;
  <a href="https://goome-ui.github.io/docs/builder/">UI builder</a> &nbsp;·&nbsp;
  <a href="https://github.com/goome-ui/goome-cli">goome-cli</a>
</p>

<details>
<summary align="center"><b>⬇️ Easy Install ⬇️</b></summary>

<br>

<p align="center">
1. Grab <code>jetbrains-goome-x.y.z.zip</code> from the <a href="https://github.com/goome-ui/jetbrains-goome/releases/latest">latest release</a>, dont unzip it<br>
2. In your IDE open <b>Settings > Plugins</b>, click the gear at the top and pick <b>Install Plugin from Disk...</b><br>
3. Pick the .zip you downloaded and restart when it asks, done<br> <br>
Works in 2024.3 or newer. JetBrains Marketplace is coming soon.
</p>
</details>

<details>
<summary align="center"><b>🚀 How To Use 🚀</b></summary>

<br>

<p align="center">
<b>Live templates</b> - type <code>goome</code> and pick one, or hit Tab after the full name. <code>goome-overlay</code> and <code>goome-app</code> are a whole program, <code>goome-loop</code> is the poll and pace loop, and theres one for every common widget, plus <code>goome-style</code>, <code>goome-custom</code>, <code>goome-offscreen</code>, <code>goome-hotkey</code>, <code>goome-theme-preset</code> and <code>goome-theme-make</code><br>
<b>goome: New Project</b> - in <b>Tools > goome</b> or on the welcome screen. Pick a language, a template, a folder and a name. It runs <a href="https://github.com/goome-ui/goome-cli">goome-cli</a> and opens what it made. If <code>goome</code> isnt on your PATH it downloads goome.py from the latest goome-cli release and runs that with Python, so you dont have to install anything besides Python<br>
<b>Preview</b> - open a <code>.goome.json</code> and you get the text on the left and the preview on the right. It draws the project with the UI builders own code and updates while you type. Its read only, <b>Open in UI builder</b> takes you to the real thing<br>
<b>Copy code</b> - the C, C#, Python and Java buttons at the top of the preview put the builders generated code on your clipboard<br>
<b>Quick docs</b> - put the caret on any <code>goome_*</code> function and hit Ctrl+Q (F1 on mac) for its signature, the note from goome.h and a link to the docs<br>
<b>goome: Search Docs</b> - in <b>Tools > goome</b>. A searchable list of every goome function, picks the one under the caret, opens its docs page<br>
<b>Schemas</b> - <code>.goome.json</code> and <code>.goome-theme.json</code> get checked while you edit them, so typos in widget types, theme fields and colors show up right away<br> <br>
Settings are in <b>Settings > Tools > goome</b>: your own <code>goome</code> or <code>goome.py</code>, and the Python it runs with.
</p>
</details>

<details>
<summary align="center"><b>🧭 What Works Where 🧭</b></summary>

<br>

<p align="center">
<b>Rider</b> - C# and C++ templates, they ship as a ReSharper settings layer inside the plugin so the ReSharper side picks them up by itself. Theyre in the goome category. New Project, preview, copy code, schemas and Search Docs work too. Quick docs for C stay with ReSharper there, use Search Docs instead<br>
<b>CLion</b> - C templates in C and C++ files, quick docs, and everything else<br>
<b>IntelliJ IDEA</b> - Java templates, and everything else. With the Python plugin you get the Python ones too<br>
<b>PyCharm</b> - Python templates, and everything else<br> <br>
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

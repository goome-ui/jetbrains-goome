<h1 align="center">🛠 Contributing to jetbrains-goome 🛠</h1>

<p align="center">
Small PRs that fix one thing are the easiest for me to check.<br>
Bugs in the preview or the generated code usually live in the builder, those go to <a href="https://github.com/goome-ui/docs">goome-ui/docs</a>. Snippet and schema changes go to <a href="https://github.com/goome-ui/vscode-goome">vscode-goome</a> first, this repo copies them. Bugs in goome itself go to <a href="https://github.com/goome-ui/c-goome">c-goome</a>.
</p>

<details>
<summary align="center"><b>📋 The short version 📋</b></summary>

<p align="center">
• One thing per PR<br>
• Open your PR against the <b>dev</b> branch, not main<br>
• Kotlin, no new dependencies, the plugin only uses what the IDE already has<br>
• Keep it working in every JetBrains IDE, put language stuff behind an optional depends like the templates are<br>
• Dont touch the protected files, a bot closes the PR if you do<br>
• CI has to pass, thats the build, the tests and the plugin verifier<br>
• AI is fine, but say you used it and know what every line does
</p>
</details>

<details>
<summary align="center"><b>🚫 What gets closed automatically 🚫</b></summary>

<p align="center">
A bot checks every PR and closes it with a comment if it hits any of these.<br> <br>
• PRs that arent against <b>dev</b><br>
• Anything in <code>.github/</code><br>
• <code>build.gradle.kts</code>, <code>settings.gradle.kts</code>, <code>gradle.properties</code>, the gradle wrapper or <code>buildSrc/build.gradle.kts</code><br>
• <code>LICENSE</code> or the icons<br>
• <code>snippets/</code> or <code>schemas/</code>, those come from vscode-goome<br> <br>
Need one changed? <a href="https://github.com/goome-ui/jetbrains-goome/issues">Open an issue</a>.
</p>
</details>

<details>
<summary align="center"><b>🧪 Testing 🧪</b></summary>

```sh
./gradlew buildPlugin
./gradlew test
./gradlew verifyPlugin -PverifyIde=RD:2026.3
```

<p align="center">
Put <code>docs</code> and <code>c-goome</code> next to this folder (or in <code>deps/</code>), the build needs them. With <code>vscode-goome</code> and <code>goome-cli</code> there too the tests also check the snippets didnt drift and run the real cli.<br>
The tests check every live template and the Rider settings layer against <code>snippets/</code>, expand a template in a Java file, check the schemas catch typos, the quick docs and the preview page.<br>
<code>verifyPlugin</code> downloads the whole IDE you name (<code>IC</code>, <code>IU</code>, <code>RD</code>, <code>PY</code> or <code>CL</code> and a version), so CI runs it and you probably dont need to.
</p>
</details>

<details>
<summary align="center"><b>💬 Discussions and first issues 💬</b></summary>
<p align="center">
Questions and stuff you made with goome go in <a href="https://github.com/goome-ui/c-goome/discussions">Discussions</a>.<br>
If you want something to work on, the <a href="https://github.com/goome-ui/jetbrains-goome/labels/good%20first%20issue">good first issue</a> ones are small. Comment on one before you start.
</p>
</details>

<details>
<summary align="center"><b>🛡️ Security 🛡️</b></summary>

<p align="center">
Found a security hole? Dont open a public issue or PR for it, read <a href="https://github.com/goome-ui/.github/blob/main/SECURITY.md">SECURITY.md</a> and tell me privately first.
</p>
</details>

<p align="center">
By sending a PR you agree your code goes out under the same <a href="LICENSE">GPL-3.0 license</a> as the rest of jetbrains-goome.
</p>

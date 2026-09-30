from pathlib import Path

path = Path("app/src/main/java/com/yagay/YagaYHub/MainActivity.kt")
text = path.read_text()

start = text.index("private fun openChatPopup(")
end = text.index("\nprivate fun openUrl(", start)
block = text[start:end]

if "YBROWSER_AI_WORKSPACE_ACTIVITY" not in block and "YBROWSER_OPEN_AI_ACTION" not in block:
    raise SystemExit("openChatPopup is already migrated or source layout changed")

replacement = '''private fun openChatPopup(
    context: Context,
    url: String?,
    bindingRepoKey: String? = null,
    bindingProject: String? = null,
    bindingTitle: String? = null,
) {
    val compactIntent = Intent(YBROWSER_OPEN_BROWSER_ACTION).apply {
        setClassName(
            YBROWSER_PACKAGE,
            YBROWSER_EMBEDDED_ACTIVITY,
        )

        url?.takeIf { it.isNotBlank() }?.let {
            putExtra(YBROWSER_EXTRA_URL, it)
        }

        putExtra(YBROWSER_EXTRA_YAGAYHUB_BINDING_MODE, true)
        putExtra(YBROWSER_EXTRA_YAGAYHUB_COMPACT_MODE, true)
        putExtra(YBROWSER_EXTRA_YAGAYHUB_EMBEDDED, true)
        putExtra(EXTRA_CHAT_TARGETS_JSON, chatTargetsJson(context))

        if (!bindingRepoKey.isNullOrBlank()) {
            putExtra(EXTRA_CHAT_BIND_REPO, bindingRepoKey)
            putExtra(EXTRA_CHAT_BIND_PROJECT, bindingProject.orEmpty())
            putExtra(EXTRA_CHAT_BIND_TITLE, bindingTitle.orEmpty())
        }

        // Prefer an already-existing compact host. Bound pages live in
        // YBrowser's retained session pool, so returning to AI should reuse
        // the live tab/session instead of rebuilding or reloading the page.
        addFlags(
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
    }

    try {
        context.startActivity(compactIntent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "请先安装或更新 YBrowser",
            Toast.LENGTH_SHORT,
        ).show()
    }
}
'''

text = text[:start] + replacement + text[end:]
text = text.replace(
    'private const val YBROWSER_OPEN_AI_ACTION =\n    "com.yagay.YBrowser.action.OPEN_AI"\n',
    '',
)
text = text.replace(
    'private const val YBROWSER_AI_WORKSPACE_ACTIVITY =\n    "com.yagay.ybrowser.ai.AiWorkspaceActivity"\n',
    '',
)

path.write_text(text)

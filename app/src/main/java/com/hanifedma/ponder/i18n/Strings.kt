package com.hanifedma.ponder.i18n

/**
 * The whole interface can switch English ⇄ Korean, exactly like the web app.
 *
 * Only UI chrome is translated — saved entries, their sources, and the raw tag
 * values stored in Firestore are never touched, so the two clients stay in sync
 * whichever language each is showing.
 *
 * Keys are kept identical to the web app's `I18N` dictionary so the two stay
 * easy to diff.
 */
enum class Lang(val code: String, val label: String) {
    EN("en", "EN"),
    KO("ko", "한국어");

    companion object {
        fun from(code: String?): Lang = entries.firstOrNull { it.code == code } ?: EN
    }
}

object Strings {

    private val EN_MAP: Map<String, String> = mapOf(
        "tab.ponder" to "❝ Ponder", "tab.health" to "🌿 Healthy Tips",
        "space.ponder.name" to "Ponder", "space.health.name" to "Healthy Tips",
        "ph.ponder" to "Write a quote or a thought…", "ph.health" to "Write a healthy tip…",
        "add.ponder" to "Add quote", "add.health" to "Add tip",
        "empty.ponder.title" to "Nothing here yet.",
        "empty.ponder.sub" to "Add your first quote or thought above ☝️",
        "empty.health.title" to "No tips yet.",
        "empty.health.sub" to "Add your first healthy tip above ☝️",
        "theme.title" to "Toggle light / dark mode", "theme.light" to "Light", "theme.dark" to "Dark",
        "lang.title" to "Change language",
        "settings.title" to "Settings", "settings.open" to "Open settings",
        "settings.startSpace" to "Open on start",
        "settings.startSpace.hint" to "Which section the app opens when you launch it.",
        "settings.startSpace.last" to "Last one I used",
        "settings.sort" to "Default sort",
        "settings.sort.hint" to "The order entries are listed in when a section opens.",
        "settings.close" to "Done",
        "signin.short" to "Sign in", "signin.long" to " with Google", "signout" to "Sign out",
        "signin.setup" to "Google sign-in needs a one-time Firebase setup for Android (free, ~5 min — see SETUP.md). Your notes stay on this device until then.",
        "chip.local" to "🖥️ This device",
        "login.h1" to "Your quotes & thoughts.",
        "login.sub" to "Sign in to keep quotes and thoughts only you can see. Fast, private, backed up.",
        "login.google" to "Continue with Google",
        "login.local" to "or use on this device without an account",
        "err.auth.cancelled" to "Sign-in was cancelled.",
        "err.auth.network" to "No internet connection. You can keep using this device without an account.",
        "err.auth.noAccount" to "No Google account is available on this device. Add one in Settings and try again.",
        "err.auth.notAllowed" to "Google sign-in isn't enabled in Firebase yet. Enable it under Authentication → Sign-in method.",
        "err.auth.config" to "This app build isn't registered in Firebase yet. Add an Android app with this package name and SHA-1 — see SETUP.md.",
        "err.auth.noClientId" to "This build has no Google client ID, so sign-in can't start. Put google-services.json in app/ and rebuild — see SETUP.md.",
        "err.auth.generic" to "Couldn't sign in. Please try again.",
        "ph.source" to "Source (optional: author, book…)", "aria.tag" to "Tag",
        "ph.search" to "Search…",
        "sort.newest" to "Newest first", "sort.oldest" to "Oldest first", "sort.tag" to "By tag",
        "aria.sort" to "Sort entries", "aria.more" to "More options",
        "btn.shuffle" to "Shuffle", "btn.dup" to "Find duplicates", "btn.export" to "Export PDF",
        "badge.local" to "saved on this device", "badge.offline" to "offline · showing saved copy",
        "count.one" to "1 entry", "count.all" to "{n} entries", "count.of" to "{n} of {m}",
        "empty.nomatch.title" to "No matches.", "empty.nomatch.sub" to "Try a different search or tag.",
        "aria.shuffleTag" to "Random from tag", "shuffle.all" to "All tags",
        "aria.shuffleClose" to "Close shuffle",
        "shuffle.next" to "Next random →", "shuffle.hint" to "Tap the card or swipe for another",
        "shuffle.empty" to "No entries for this tag yet.", "shuffle.needAdd" to "Add some entries first.",
        "err.load" to "Couldn't load your data. Check your connection, or that the Firestore security rules are published.",
        "err.save" to "Couldn't save. Please try again.",
        "deleted" to "Deleted", "undo" to "Undo", "err.undo" to "Couldn't undo.",
        "err.delete" to "Couldn't delete. Please try again.",
        "migrate.confirm" to "You have {n} item(s) saved on this device.\n\nMove them into your account so they sync across devices?",
        "migrate.move" to "Move to my account", "migrate.keep" to "Keep on device",
        "migrate.title" to "Move your entries?",
        "migrate.busy" to "Moving your entries…",
        "migrate.moved" to "Moved {n} item(s) into your account",
        "migrate.err" to "Couldn't move everything — it's still safe on this device.",
        "dup.title" to "Possible duplicate", "dup.title.plural" to "Possible duplicates",
        "dup.sub" to "You already have a similar entry. Add this new one anyway?",
        "dup.sub.plural" to "You already have similar entries. Add this new one anyway?",
        "dup.match" to "{n}% match", "cancel" to "Cancel", "dup.addAnyway" to "Add anyway",
        "dup.need2" to "You need at least two entries to check for duplicates.",
        "dup.scanning" to "Scanning for duplicates…", "dup.none" to "No similar entries found 🎉",
        "dup.groups" to "{g} group(s) of similar entries ({n} items). Delete the ones you don't want to keep.",
        "done" to "Done", "dup.noMore" to "No more duplicates 🎉",
        "pdf.nothing" to "You have nothing to export yet.", "pdf.building" to "Building your PDF…",
        "pdf.fetching" to "Fetching media…", "pdf.done" to "PDF saved ({n} entries)",
        "pdf.err" to "Couldn't build the PDF. Please check your connection and try again.",
        "pdf.open" to "Open", "pdf.noViewer" to "No PDF viewer app is installed.",
        "pdf.exported" to "Exported",
        "local.note" to "📵 Local-only mode. Your notes are saved on this device only. To turn on Google login & sync, do the one-time Firebase setup for Android — see SETUP.md.",
        "media.play" to "Play {label}", "media.open" to "Open {label}",
        "media.noEmbed" to "This video can't be played inside the app.",
        "delete" to "Delete", "aria.delete" to "Delete entry", "aria.back" to "Back",
        "tag.extraterrestrial" to "Extraterrestrial",
        "tag.try to read this everyday" to "Try to read this everyday",
        "tag.very important" to "Very important", "tag.pretty important" to "Pretty important",
        "tag.interesting" to "Interesting", "tag.pretty sure" to "Pretty sure",
        "tag.not really" to "Not really",
    )

    private val KO_MAP: Map<String, String> = mapOf(
        "tab.ponder" to "❝ Ponder", "tab.health" to "🌿 건강 팁",
        "space.ponder.name" to "Ponder", "space.health.name" to "건강 팁",
        "ph.ponder" to "명언이나 생각을 적어보세요…", "ph.health" to "건강 팁을 적어보세요…",
        "add.ponder" to "명언 추가", "add.health" to "팁 추가",
        "empty.ponder.title" to "아직 아무것도 없어요.",
        "empty.ponder.sub" to "위에서 첫 명언이나 생각을 추가하세요 ☝️",
        "empty.health.title" to "아직 팁이 없어요.",
        "empty.health.sub" to "위에서 첫 건강 팁을 추가하세요 ☝️",
        "theme.title" to "라이트 / 다크 모드 전환", "theme.light" to "라이트", "theme.dark" to "다크",
        "lang.title" to "언어 변경",
        "settings.title" to "설정", "settings.open" to "설정 열기",
        "settings.startSpace" to "시작 화면",
        "settings.startSpace.hint" to "앱을 열 때 처음 보이는 섹션입니다.",
        "settings.startSpace.last" to "마지막으로 본 화면",
        "settings.sort" to "기본 정렬",
        "settings.sort.hint" to "섹션을 열 때 항목이 정렬되는 순서입니다.",
        "settings.close" to "완료",
        "signin.short" to "로그인", "signin.long" to " (Google)", "signout" to "로그아웃",
        "signin.setup" to "Google 로그인을 사용하려면 Android용 Firebase 설정이 한 번 필요합니다 (무료, 약 5분 — SETUP.md 참고). 그때까지 메모는 이 기기에 저장됩니다.",
        "chip.local" to "🖥️ 이 기기",
        "login.h1" to "나의 명언과 생각.",
        "login.sub" to "나만 볼 수 있는 명언과 생각을 저장하세요. 빠르고, 비공개이며, 백업됩니다.",
        "login.google" to "Google로 계속하기",
        "login.local" to "또는 계정 없이 이 기기에서 사용하기",
        "err.auth.cancelled" to "로그인이 취소되었습니다.",
        "err.auth.network" to "인터넷 연결이 없습니다. 계정 없이 이 기기에서 계속 사용할 수 있습니다.",
        "err.auth.noAccount" to "이 기기에 사용할 수 있는 Google 계정이 없습니다. 설정에서 계정을 추가한 후 다시 시도하세요.",
        "err.auth.notAllowed" to "Firebase에서 Google 로그인이 아직 활성화되지 않았습니다. Authentication → Sign-in method 에서 활성화하세요.",
        "err.auth.config" to "이 앱 빌드가 아직 Firebase에 등록되지 않았습니다. 같은 패키지 이름과 SHA-1로 Android 앱을 추가하세요 — SETUP.md 참고.",
        "err.auth.noClientId" to "이 빌드에는 Google 클라이언트 ID가 없어 로그인을 시작할 수 없습니다. google-services.json을 app/ 폴더에 넣고 다시 빌드하세요 — SETUP.md 참고.",
        "err.auth.generic" to "로그인하지 못했습니다. 다시 시도해 주세요.",
        "ph.source" to "출처 (선택: 저자, 책…)", "aria.tag" to "태그",
        "ph.search" to "검색…",
        "sort.newest" to "최신순", "sort.oldest" to "오래된순", "sort.tag" to "태그별",
        "aria.sort" to "정렬", "aria.more" to "더보기",
        "btn.shuffle" to "랜덤", "btn.dup" to "중복 찾기", "btn.export" to "PDF 내보내기",
        "badge.local" to "이 기기에 저장됨", "badge.offline" to "오프라인 · 저장본 표시 중",
        "count.one" to "1개", "count.all" to "{n}개", "count.of" to "{m}개 중 {n}개",
        "empty.nomatch.title" to "일치하는 항목이 없어요.",
        "empty.nomatch.sub" to "다른 검색어나 태그로 시도해 보세요.",
        "aria.shuffleTag" to "태그에서 무작위", "shuffle.all" to "모든 태그",
        "aria.shuffleClose" to "랜덤 닫기",
        "shuffle.next" to "다음 →", "shuffle.hint" to "카드를 탭하거나 스와이프하세요",
        "shuffle.empty" to "이 태그에는 아직 항목이 없어요.", "shuffle.needAdd" to "먼저 항목을 추가하세요.",
        "err.load" to "데이터를 불러오지 못했습니다. 연결 상태나 Firestore 보안 규칙 게시 여부를 확인하세요.",
        "err.save" to "저장하지 못했습니다. 다시 시도해 주세요.",
        "deleted" to "삭제됨", "undo" to "실행 취소", "err.undo" to "실행 취소하지 못했습니다.",
        "err.delete" to "삭제하지 못했습니다. 다시 시도해 주세요.",
        "migrate.confirm" to "이 기기에 {n}개의 항목이 저장되어 있습니다.\n\n계정으로 옮겨 모든 기기에서 동기화하시겠어요?",
        "migrate.move" to "계정으로 옮기기", "migrate.keep" to "기기에 두기",
        "migrate.title" to "항목을 옮길까요?",
        "migrate.busy" to "항목을 옮기는 중…",
        "migrate.moved" to "{n}개의 항목을 계정으로 옮겼습니다",
        "migrate.err" to "일부를 옮기지 못했습니다 — 항목은 이 기기에 그대로 안전합니다.",
        "dup.title" to "중복 가능성", "dup.title.plural" to "중복 가능성",
        "dup.sub" to "비슷한 항목이 이미 있습니다. 그래도 추가할까요?",
        "dup.sub.plural" to "비슷한 항목들이 이미 있습니다. 그래도 추가할까요?",
        "dup.match" to "{n}% 일치", "cancel" to "취소", "dup.addAnyway" to "그래도 추가",
        "dup.need2" to "중복을 확인하려면 항목이 두 개 이상 필요합니다.",
        "dup.scanning" to "중복을 검사하는 중…", "dup.none" to "비슷한 항목이 없습니다 🎉",
        "dup.groups" to "비슷한 항목 {g}개 그룹 ({n}개 항목). 남기지 않을 항목을 삭제하세요.",
        "done" to "완료", "dup.noMore" to "더 이상 중복이 없습니다 🎉",
        "pdf.nothing" to "아직 내보낼 항목이 없습니다.", "pdf.building" to "PDF를 만드는 중…",
        "pdf.fetching" to "미디어를 가져오는 중…", "pdf.done" to "PDF를 저장했습니다 ({n}개 항목)",
        "pdf.err" to "PDF를 만들지 못했습니다. 연결을 확인하고 다시 시도해 주세요.",
        "pdf.open" to "열기", "pdf.noViewer" to "PDF 뷰어 앱이 설치되어 있지 않습니다.",
        "pdf.exported" to "내보낸 날짜",
        "local.note" to "📵 로컬 전용 모드. 메모가 이 기기에만 저장됩니다. Google 로그인과 동기화를 켜려면 Android용 Firebase 설정을 한 번 진행하세요 — SETUP.md 참고.",
        "media.play" to "{label} 재생", "media.open" to "{label} 열기",
        "media.noEmbed" to "이 영상은 앱 안에서 재생할 수 없습니다.",
        "delete" to "삭제", "aria.delete" to "항목 삭제", "aria.back" to "뒤로",
        "tag.extraterrestrial" to "외계", "tag.try to read this everyday" to "매일 읽기",
        "tag.very important" to "매우 중요", "tag.pretty important" to "꽤 중요",
        "tag.interesting" to "흥미로움", "tag.pretty sure" to "확실함", "tag.not really" to "글쎄",
    )

    private fun mapFor(lang: Lang) = if (lang == Lang.KO) KO_MAP else EN_MAP

    /** Look up a translation; falls back to English, then to [fallback], then the key. */
    fun t(lang: Lang, key: String, fallback: String? = null): String =
        mapFor(lang)[key] ?: EN_MAP[key] ?: fallback ?: key

    /** Same as [t] but fills `{token}` placeholders. */
    fun tf(lang: Lang, key: String, params: Map<String, Any>): String {
        var s = t(lang, key)
        for ((k, v) in params) s = s.replace("{$k}", v.toString())
        return s
    }

    /**
     * Display label for a tag. The value stored in the database is always the raw
     * English tag — only the label changes with the language.
     */
    fun tagLabel(lang: Lang, tag: String?): String =
        t(lang, "tag." + (tag ?: ""), capitalize(tag ?: ""))

    fun capitalize(s: String): String =
        if (s.isEmpty()) s else s[0].uppercaseChar() + s.substring(1)
}

/** Small translator bound to one language, handed down the UI through a CompositionLocal. */
class Tr(val lang: Lang) {
    operator fun invoke(key: String, fallback: String? = null): String =
        Strings.t(lang, key, fallback)

    fun f(key: String, vararg params: Pair<String, Any>): String =
        Strings.tf(lang, key, params.toMap())

    fun tag(tag: String?): String = Strings.tagLabel(lang, tag)
}

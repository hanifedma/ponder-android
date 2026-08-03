package com.hanifedma.ponder.ui

import android.app.Activity
import com.hanifedma.ponder.ui.screens.HomeCallbacks

/**
 * Binds the screen's callbacks to the ViewModel in one place, so the composables
 * take plain lambdas and stay previewable and testable.
 */
object HomeCallbacksFactory {

    fun create(
        viewModel: PonderViewModel,
        activity: Activity?,
        openUrl: (String) -> Unit,
    ): HomeCallbacks = HomeCallbacks(
        switchSpace = viewModel::switchSpace,
        toggleLang = viewModel::toggleLang,
        toggleTheme = viewModel::toggleTheme,
        // Credential Manager needs a real Activity to show the account picker.
        signIn = { activity?.let(viewModel::signIn) },
        signOut = viewModel::signOut,
        setSearch = viewModel::setSearch,
        setSort = viewModel::setSort,
        setComposerText = viewModel::setComposerText,
        setComposerSource = viewModel::setComposerSource,
        setComposerTag = viewModel::setComposerTag,
        submit = viewModel::submitComposer,
        delete = viewModel::delete,
        openShuffle = viewModel::openShuffle,
        scanDuplicates = viewModel::scanForDuplicates,
        export = viewModel::requestExport,
        openUrl = openUrl,
    )
}

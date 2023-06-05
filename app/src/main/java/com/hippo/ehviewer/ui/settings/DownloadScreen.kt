package com.hippo.ehviewer.ui.settings

import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hippo.ehviewer.AppConfig
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.download.downloadLocation
import com.hippo.ehviewer.ui.compose.observed
import com.hippo.ehviewer.ui.compose.rememberedAccessor
import com.hippo.ehviewer.ui.keepNoMediaFileStatus
import com.hippo.ehviewer.ui.legacy.BaseDialogBuilder
import com.hippo.ehviewer.ui.login.LocalNavController
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.lang.launchNonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DownloadScreen() {
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope { Dispatchers.IO }
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.settings_download)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        Column(modifier = Modifier.padding(it).nestedScroll(scrollBehavior.nestedScrollConnection)) {
            var downloadLocationState by ::downloadLocation.observed
            val cannotGetDownloadLocation = stringResource(id = R.string.settings_download_cant_get_download_location)
            val selectDownloadDirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
                treeUri?.run {
                    coroutineScope.launch {
                        context.runCatching {
                            contentResolver.takePersistableUriPermission(treeUri, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION)
                            UniFile.fromTreeUri(context, treeUri)!!.run {
                                downloadLocationState = this
                                coroutineScope.launchNonCancellable {
                                    keepNoMediaFileStatus()
                                }
                            }
                        }.onFailure {
                            snackbarHostState.showSnackbar(cannotGetDownloadLocation)
                        }
                    }
                }
            }
            Preference(
                title = stringResource(id = R.string.settings_download_download_location),
                summary = downloadLocationState.uri.toString(),
            ) {
                val file = downloadLocationState
                if (!UniFile.isFileUri(downloadLocationState.uri)) {
                    BaseDialogBuilder(context)
                        .setTitle(R.string.settings_download_download_location)
                        .setMessage(file.uri.toString())
                        .setPositiveButton(R.string.pick_new_download_location) { _, _ -> selectDownloadDirLauncher.launch(null) }
                        .setNeutralButton(R.string.reset_download_location) { _, _ ->
                            val uniFile = UniFile.fromFile(AppConfig.defaultDownloadDir)
                            if (uniFile != null) {
                                downloadLocationState = uniFile
                                coroutineScope.launchNonCancellable { keepNoMediaFileStatus() }
                            } else {
                                coroutineScope.launch { snackbarHostState.showSnackbar(cannotGetDownloadLocation) }
                            }
                        }
                        .show()
                } else {
                    selectDownloadDirLauncher.launch(null)
                }
            }
            val mediaScan = Settings::mediaScan.observed
            SwitchPreference(
                title = stringResource(id = R.string.settings_download_media_scan),
                summary = if (mediaScan.value) stringResource(id = R.string.settings_download_media_scan_summary_on) else stringResource(id = R.string.settings_download_media_scan_summary_off),
                value = mediaScan.rememberedAccessor,
            )
            val multiThreadDownload = Settings::multiThreadDownload.observed
            SimpleMenuPreferenceInt(
                title = stringResource(id = R.string.settings_download_concurrency),
                summary = stringResource(id = R.string.settings_download_concurrency_summary, multiThreadDownload.value),
                entry = R.array.multi_thread_download_entries,
                entryValueRes = R.array.multi_thread_download_entry_values,
                value = multiThreadDownload,
            )
            val downloadDelay = Settings::downloadDelay.observed
            SimpleMenuPreferenceInt(
                title = stringResource(id = R.string.settings_download_download_delay),
                summary = stringResource(id = R.string.settings_download_download_delay_summary, downloadDelay.value),
                entry = R.array.download_delay_entries,
                entryValueRes = R.array.download_delay_entry_values,
                value = downloadDelay,
            )
            val preloadImage = Settings::preloadImage.observed
            SimpleMenuPreferenceInt(
                title = stringResource(id = R.string.settings_download_preload_image),
                summary = stringResource(id = R.string.settings_download_preload_image_summary, preloadImage.value),
                entry = R.array.preload_image_entries,
                entryValueRes = R.array.preload_image_entry_values,
                value = preloadImage,
            )
            SwitchPreference(
                title = stringResource(id = R.string.settings_download_download_origin_image),
                summary = stringResource(id = R.string.settings_download_download_origin_image_summary),
                value = Settings::downloadOriginImage,
            )
            Preference(
                title = stringResource(id = R.string.settings_download_restore_download_items),
                summary = stringResource(id = R.string.settings_download_restore_download_items_summary),
            )
            Preference(
                title = stringResource(id = R.string.settings_download_clean_redundancy),
                summary = stringResource(id = R.string.settings_download_clean_redundancy_summary),
            )
        }
    }
}

package packagelist.android.moukaeritai.work

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.util.*

class IntentSurfaceDiscoveryRunner(private val context: Context) {

    private val events = mutableListOf<DiagnosticEvent>()
    private val errors = mutableListOf<DiagnosticError>()
    private val probes = mutableListOf<IntentSurfaceProbeResult>()

    fun runDiscovery(runId: String, fileName: String, progressCallback: (String) -> Unit): IntentSurfaceReport {
        val startTime = System.currentTimeMillis()

        fun logEvent(name: String, message: String) {
            val ts = java.time.Instant.now().toString()
            events.add(DiagnosticEvent(name, ts, message))
        }

        fun logProgress(stage: String) {
            progressCallback(stage)
            logEvent("stage_$stage", "Running stage: $stage")
        }

        fun logError(stage: String, probeId: String?, family: String?, mode: String?, pkg: String?, act: String?, e: Exception) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            val trace = sw.toString().lines().take(5).joinToString("\n")
            errors.add(DiagnosticError(stage, null, probeId, family, mode, pkg, act, e.javaClass.simpleName, e.message ?: "Unknown", trace))
        }

        logProgress("preparing_report")

        val appPackageName = context.packageName
        val pm = context.packageManager
        val pi = try { pm.getPackageInfo(appPackageName, PackageManager.GET_PERMISSIONS) } catch (e: Exception) { null }
        val appVersionName = pi?.versionName ?: "unknown"
        val appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi?.longVersionCode ?: 0L else pi?.versionCode?.toLong() ?: 0L
        val targetSdk = context.applicationInfo.targetSdkVersion
        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) context.applicationInfo.minSdkVersion else 24

        val reqPerms = pi?.requestedPermissions?.toList() ?: emptyList()
        val reqFlags = pi?.requestedPermissionsFlags ?: IntArray(0)
        val grantedPerms = reqPerms.filterIndexed { index, _ ->
            if (index < reqFlags.size) {
                (reqFlags[index] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            } else false
        }

        val canQueryAll = context.checkSelfPermission(android.Manifest.permission.QUERY_ALL_PACKAGES) == PackageManager.PERMISSION_GRANTED

        val appInfo = AppInfo(
            app_package_name = appPackageName,
            app_version_name = appVersionName,
            app_version_code = appVersionCode,
            target_sdk_version = targetSdk,
            min_sdk_version = minSdk,
            build_type = if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) "debug" else "release",
            build_time_utc = packagelist.android.moukaeritai.work.BuildConfig.BUILD_TIME_UTC,
            git_commit_short = packagelist.android.moukaeritai.work.BuildConfig.GIT_COMMIT_SHORT,
            git_commit_full = packagelist.android.moukaeritai.work.BuildConfig.GIT_COMMIT_FULL,
            json_report_schema_version = packagelist.android.moukaeritai.work.BuildConfig.JSON_REPORT_SCHEMA_VERSION,
            debug_build = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            installer_package_name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { pm.getInstallSourceInfo(appPackageName).installingPackageName } catch (e: Exception) { null }
            } else {
                @Suppress("DEPRECATION") pm.getInstallerPackageName(appPackageName)
            },
            requested_permissions = reqPerms,
            granted_permissions = grantedPerms,
            query_all_packages_granted = canQueryAll
        )

        logProgress("collecting_environment")
        val deviceInfo = DeviceInfo(
            android_sdk_int = Build.VERSION.SDK_INT,
            android_release = Build.VERSION.RELEASE,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            supported_abis = Build.SUPPORTED_ABIS.toList(),
            locale = Locale.getDefault().toString(),
            time_zone = TimeZone.getDefault().id,
            screen_width_px = context.resources.displayMetrics.widthPixels,
            screen_height_px = context.resources.displayMetrics.heightPixels,
            density_dpi = context.resources.displayMetrics.densityDpi
        )

        val visibilityInfo = PackageVisibilityInfo(
            visibility_config = "intent_queries_expanded_v1",
            query_all_packages_granted = canQueryAll,
            can_query_all_packages_by_permission_check = canQueryAll,
            requested_permissions = reqPerms,
            declared_intent_queries_hardcoded_description = "WEB_LINK http/https, FILE_VIEW common MIME types, SHARE common MIME types, DOCUMENT_PICKER, COMMUNICATION mailto/tel, MAPS geo/maps URL, MARKET market/play URL, SEARCH, MAIN_SELECTOR"
        )
        
        // Generate dummy files for content:// probes
        val dummyDir = File(context.cacheDir, "dummy")
        if (!dummyDir.exists()) dummyDir.mkdirs()
        
        fun getDummyUri(mimeType: String): Uri {
            val ext = when (mimeType) {
                "text/plain" -> "txt"
                "text/html" -> "html"
                "text/csv" -> "csv"
                "application/json" -> "json"
                "application/pdf" -> "pdf"
                "application/zip" -> "zip"
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "application/msword" -> "doc"
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
                "application/vnd.ms-excel" -> "xls"
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
                "application/vnd.ms-powerpoint" -> "ppt"
                "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
                else -> "bin"
            }
            val file = File(dummyDir, "sample.$ext")
            if (!file.exists()) file.writeText("dummy content")
            return FileProvider.getUriForFile(context, "$appPackageName.fileprovider", file)
        }
        
        val pkgInfoCache = mutableMapOf<String, android.content.pm.PackageInfo?>()
        fun getPkgInfo(pkg: String): android.content.pm.PackageInfo? {
            return pkgInfoCache.getOrPut(pkg) {
                try { pm.getPackageInfo(pkg, 0) } catch (e: Exception) { null }
            }
        }

        val flagMap = mapOf(
            PackageManager.GET_ACTIVITIES to "GET_ACTIVITIES",
            PackageManager.GET_RECEIVERS to "GET_RECEIVERS",
            PackageManager.GET_SERVICES to "GET_SERVICES",
            PackageManager.GET_PROVIDERS to "GET_PROVIDERS",
            PackageManager.GET_RESOLVED_FILTER to "GET_RESOLVED_FILTER",
            PackageManager.GET_META_DATA to "GET_META_DATA",
            PackageManager.MATCH_ALL to "MATCH_ALL",
            PackageManager.MATCH_DEFAULT_ONLY to "MATCH_DEFAULT_ONLY",
            524288 to "MATCH_DIRECT_BOOT_AWARE",
            262144 to "MATCH_DIRECT_BOOT_UNAWARE",
            1048576 to "MATCH_SYSTEM_ONLY",
            8192 to "MATCH_UNINSTALLED_PACKAGES",
            512 to "MATCH_DISABLED_COMPONENTS"
        )
        fun decodeFlags(raw: Int): List<String> {
            if (raw == 0) return emptyList()
            val labels = mutableListOf<String>()
            for ((flag, label) in flagMap.entries) {
                if ((raw and flag) != 0) {
                    labels.add(label)
                }
            }
            if (labels.isEmpty()) labels.add("RAW_$raw")
            return labels
        }

        fun runProbe(
            id: String, family: String, baseIntent: Intent, 
            dataUriKind: String? = null, extrasSchema: Map<String, String> = emptyMap(),
            queryFlagsRaw: Int = PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA or PackageManager.MATCH_DEFAULT_ONLY,
            queryFlagsLabels: List<String> = emptyList()
        ) {
            logEvent("probe_begin", id)
            val candidates = mutableListOf<CandidateResult>()
            val pkgAssess = mutableListOf<TargetAssessmentResult>()
            val compAssess = mutableListOf<TargetAssessmentResult>()

            var queryFlags = queryFlagsRaw
            val dynamicLabels = decodeFlags(queryFlagsRaw).let { decoded -> 
                if (queryFlagsLabels.isNotEmpty() && decoded.isEmpty()) queryFlagsLabels else decoded 
            }
            val pStart = System.currentTimeMillis()
            var resObj: ResolveActivityResult? = null
            try {
                // Ensure GET_RESOLVED_FILTER is used so resolved_filter_summary is populated
                val qFlagsInt = queryFlagsRaw or PackageManager.GET_RESOLVED_FILTER
                
                // Overall resolveActivity
                val rOverall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try { pm.resolveActivity(baseIntent, PackageManager.ResolveInfoFlags.of(qFlagsInt.toLong())) } catch (e: Exception) { null }
                } else {
                    try { @Suppress("DEPRECATION") pm.resolveActivity(baseIntent, qFlagsInt) } catch (e: Exception) { null }
                }
                
                if (rOverall != null) {
                    val roP = rOverall.activityInfo?.packageName
                    val roA = rOverall.activityInfo?.name
                    val isRes = roP == "android" || roA == "com.android.internal.app.ResolverActivity"
                    resObj = ResolveActivityResult(roP, roA, isRes, if (isRes) "RESOLVED_VIA_RESOLVER" else "RESOLVED_DIRECT")
                } else {
                    resObj = ResolveActivityResult(null, null, false, "NOT_RESOLVED")
                }

                val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try { pm.queryIntentActivities(baseIntent, PackageManager.ResolveInfoFlags.of(qFlagsInt.toLong())) } catch (e: Exception) {
                        try { pm.queryIntentActivities(baseIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())) } catch (e2: Exception) { emptyList() }
                    }
                } else {
                    try { @Suppress("DEPRECATION") pm.queryIntentActivities(baseIntent, qFlagsInt) } catch (e: Exception) { emptyList() }
                }

                val sorted = resolveInfoList.sortedWith(compareBy({ it.activityInfo?.packageName }, { it.activityInfo?.name }))

                sorted.forEachIndexed { idx, resInfo ->
                    val actInfo = resInfo.activityInfo ?: return@forEachIndexed
                    val pkg = actInfo.packageName
                    val act = actInfo.name
                    val cPkgInfo = getPkgInfo(pkg)
                    val cVersionName = cPkgInfo?.versionName
                    val cVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cPkgInfo?.longVersionCode else cPkgInfo?.versionCode?.toLong()
                    val cTargetSdk = cPkgInfo?.applicationInfo?.targetSdkVersion

                    val filter = resInfo.filter
                    val filterSummary = if (filter != null) {
                        val actions = mutableListOf<String>()
                        for (i in 0 until filter.countActions()) actions.add(filter.getAction(i))
                        val cats = mutableListOf<String>()
                        for (i in 0 until filter.countCategories()) cats.add(filter.getCategory(i))
                        val schemes = mutableListOf<String>()
                        for (i in 0 until filter.countDataSchemes()) schemes.add(filter.getDataScheme(i))
                        val types = mutableListOf<String>()
                        for (i in 0 until filter.countDataTypes()) types.add(filter.getDataType(i))
                        val auths = mutableListOf<String>()
                        for (i in 0 until filter.countDataAuthorities()) auths.add(filter.getDataAuthority(i).host)
                        
                        ResolvedFilterSummary(
                            filter_actions = actions,
                            filter_categories = cats,
                            filter_schemes = schemes,
                            filter_mime_types = types,
                            filter_authorities = auths,
                            filter_priority = filter.priority
                        )
                    } else null

                    candidates.add(
                        CandidateResult(
                            index = idx + 1,
                            package_name = pkg,
                            activity_name = act,
                            component_name = "$pkg/$act",
                            label = try { actInfo.loadLabel(pm).toString() } catch (e: Exception) { "" },
                            exported = actInfo.exported,
                            enabled = actInfo.enabled,
                            permission = actInfo.permission,
                            process_name = actInfo.processName,
                            target_activity = actInfo.targetActivity,
                            task_affinity = actInfo.taskAffinity,
                            package_version_name = cVersionName,
                            package_version_code = cVersionCode,
                            package_target_sdk_version = cTargetSdk,
                            resolved_filter_summary = filterSummary,
                            priority = resInfo.priority,
                            preferred_order = resInfo.preferredOrder,
                            match = resInfo.match,
                            is_default = resInfo.isDefault
                        )
                    )

                    // PACKAGE_TARGETED
                    try {
                        val tgtIntent = Intent(baseIntent).apply { setPackage(pkg) }
                        val rTarget = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try { pm.resolveActivity(tgtIntent, PackageManager.ResolveInfoFlags.of(qFlagsInt.toLong())) } catch (e: Exception) { null }
                        } else {
                            try { @Suppress("DEPRECATION") pm.resolveActivity(tgtIntent, PackageManager.MATCH_DEFAULT_ONLY) } catch (e: Exception) { null }
                        }
                        
                        val rPkg = rTarget?.activityInfo?.packageName
                        val rAct = rTarget?.activityInfo?.name
                        
                        val pkgStatus = if (rTarget == null) {
                            "NOT_RESOLVABLE"
                        } else if (rPkg == pkg) {
                            "RESOLVABLE_DIRECT"
                        } else if (rPkg == "android" || rAct == "com.android.internal.app.ResolverActivity") {
                            "RESOLVABLE_VIA_RESOLVER"
                        } else {
                            "RESOLVABLE_OTHER"
                        }
                        
                        pkgAssess.add(
                            TargetAssessmentResult(
                                mode = "PACKAGE_TARGETED_RESOLUTION",
                                target_package = pkg,
                                target_activity = null,
                                component_name = null,
                                can_construct_component_name = null,
                                start_activity_attempted = null,
                                candidate_count = 1,
                                resolved_package = rPkg,
                                resolved_activity = rAct,
                                status = pkgStatus,
                                note = null
                            )
                        )
                    } catch (e: Exception) {
                        logError("package_targeted_assessment", id, family, "PACKAGE_TARGETED_RESOLUTION", pkg, act, e)
                    }

                    // COMPONENT_EXPLICIT
                    try {
                        val compStatus = if (!actInfo.enabled) {
                            "DISABLED"
                        } else if (!actInfo.exported) {
                            "NOT_EXPORTED"
                        } else if (actInfo.permission != null) {
                            "REQUIRES_PERMISSION"
                        } else {
                            "COMPONENT_SPEC_BUILT"
                        }

                        compAssess.add(
                            TargetAssessmentResult(
                                mode = "COMPONENT_EXPLICIT_STATIC_ASSESSMENT",
                                target_package = pkg,
                                target_activity = act,
                                component_name = "$pkg/$act",
                                can_construct_component_name = true,
                                start_activity_attempted = false,
                                candidate_count = null,
                                resolved_package = pkg,
                                resolved_activity = act,
                                status = compStatus,
                                note = "Static assessment only; not launched."
                            )
                        )
                    } catch (e: Exception) {
                        logError("component_explicit_assessment", id, family, "COMPONENT_EXPLICIT_STATIC_ASSESSMENT", pkg, act, e)
                    }
                }
            } catch (e: Exception) {
                logError("probe_processing", id, family, null, null, null, e)
            }

            val cCount = candidates.size
            val cEnabled = candidates.count { it.enabled }
            val cDisabled = candidates.count { !it.enabled }
            val uniquePkgs = candidates.map { it.package_name }.distinct().size

            val intentFlagsRaw = baseIntent.flags
            val intentFlagsLabels = mutableListOf<String>()
            if ((intentFlagsRaw and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) intentFlagsLabels.add("FLAG_GRANT_READ_URI_PERMISSION")
            if ((intentFlagsRaw and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) intentFlagsLabels.add("FLAG_GRANT_WRITE_URI_PERMISSION")
            // Can add more if needed
            
            val intentSpec = IntentSpec(
                action = baseIntent.action,
                data_uri_kind = dataUriKind,
                data_scheme = baseIntent.data?.scheme,
                data_display_redacted = baseIntent.dataString,
                mime_type = baseIntent.type,
                categories = baseIntent.categories?.toList() ?: emptyList(),
                intent_flags_raw = intentFlagsRaw,
                intent_flags_labels = intentFlagsLabels,
                extras_schema = extrasSchema,
                clip_data_schema = baseIntent.clipData?.let { "ClipData(itemCount=${it.itemCount})" }
            )

            probes.add(IntentSurfaceProbeResult(
                probe_id = id,
                family = family,
                action = baseIntent.action,
                data_uri_kind = dataUriKind,
                data_scheme = baseIntent.data?.scheme,
                data_display_redacted = baseIntent.dataString,
                mime_type = baseIntent.type,
                categories = baseIntent.categories?.toList() ?: emptyList(),
                flags = baseIntent.flags,
                extras_schema = extrasSchema,
                intent_spec = intentSpec,
                query_flags = queryFlags,
                query_flags_raw = queryFlagsRaw,
                query_flags_labels = dynamicLabels,
                candidate_count = cCount,
                enabled_candidate_count = cEnabled,
                disabled_candidate_count = cDisabled,
                unique_package_count = uniquePkgs,
                candidates = candidates,
                resolve_activity_result = resObj,
                package_targeted_assessments = pkgAssess,
                component_explicit_assessments = compAssess,
                duration_ms = System.currentTimeMillis() - pStart,
                errors = errors.filter { it.probe_id == id }
            ))
            logEvent("probe_end", id)
        }

        // ============================================
        // WEB_LINK
        // ============================================
        logProgress("running_web_link")
        logEvent("family_begin", "WEB_LINK")
        listOf("http", "https").forEach { scheme ->
            val uri = Uri.parse("$scheme://example.com/")
            val catVariants = listOf(
                "NO_CATEGORY" to emptySet<String>(),
                "DEFAULT" to setOf(Intent.CATEGORY_DEFAULT),
                "BROWSABLE" to setOf(Intent.CATEGORY_BROWSABLE),
                "DEFAULT_AND_BROWSABLE" to setOf(Intent.CATEGORY_DEFAULT, Intent.CATEGORY_BROWSABLE)
            )
            catVariants.forEach { (catName, cats) ->
                val intent = Intent(Intent.ACTION_VIEW, uri)
                cats.forEach { intent.addCategory(it) }
                
                runProbe(
                    id = "WEB_LINK_${scheme.uppercase()}_${catName}_FLAGS_0", 
                    family = "WEB_LINK", baseIntent = intent, dataUriKind = "url", 
                    queryFlagsRaw = 0, queryFlagsLabels = emptyList()
                )
                runProbe(
                    id = "WEB_LINK_${scheme.uppercase()}_${catName}_MATCH_DEFAULT_ONLY", 
                    family = "WEB_LINK", baseIntent = intent, dataUriKind = "url", 
                    queryFlagsRaw = PackageManager.MATCH_DEFAULT_ONLY, queryFlagsLabels = listOf("MATCH_DEFAULT_ONLY")
                )
            }
        }
        logEvent("family_end", "WEB_LINK")

        // ============================================
        // FILE_VIEW
        // ============================================
        logProgress("running_file_view")
        logEvent("family_begin", "FILE_VIEW")
        val mimeTypes = listOf(
            "text/plain", "text/html", "text/csv", "application/json", "application/pdf", 
            "application/zip", "image/jpeg", "image/png", "image/*", "video/*", "audio/*",
            "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
        mimeTypes.forEachIndexed { i, mime ->
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(getDummyUri(mime), mime)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            runProbe("FILE_VIEW_${i}_NO_CAT_FLAGS_0", "FILE_VIEW", intent, "content", mapOf(), 0, emptyList())
            runProbe("FILE_VIEW_${i}_NO_CAT_MATCH_DEFAULT", "FILE_VIEW", intent, "content", mapOf(), PackageManager.MATCH_DEFAULT_ONLY, listOf("MATCH_DEFAULT_ONLY"))
            
            val intentDef = Intent(intent).addCategory(Intent.CATEGORY_DEFAULT)
            runProbe("FILE_VIEW_${i}_DEFAULT_FLAGS_0", "FILE_VIEW", intentDef, "content", mapOf(), 0, emptyList())
            runProbe("FILE_VIEW_${i}_DEFAULT_MATCH_DEFAULT", "FILE_VIEW", intentDef, "content", mapOf(), PackageManager.MATCH_DEFAULT_ONLY, listOf("MATCH_DEFAULT_ONLY"))
        }
        logEvent("family_end", "FILE_VIEW")

        // ============================================
        // SHARE
        // ============================================
        logProgress("running_share")
        logEvent("family_begin", "SHARE")
        fun runShareProbes(baseId: String, intent: Intent, schema: Map<String, String>) {
            runProbe("${baseId}_NO_CAT_FLAGS_0", "SHARE", intent, null, schema, 0, emptyList())
            runProbe("${baseId}_NO_CAT_MATCH_DEFAULT", "SHARE", intent, null, schema, PackageManager.MATCH_DEFAULT_ONLY, listOf("MATCH_DEFAULT_ONLY"))
            val intentDef = Intent(intent).addCategory(Intent.CATEGORY_DEFAULT)
            runProbe("${baseId}_DEFAULT_FLAGS_0", "SHARE", intentDef, null, schema, 0, emptyList())
            runProbe("${baseId}_DEFAULT_MATCH_DEFAULT", "SHARE", intentDef, null, schema, PackageManager.MATCH_DEFAULT_ONLY, listOf("MATCH_DEFAULT_ONLY"))
        }

        runShareProbes("SHARE_TEXT_PLAIN", Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "test").putExtra(Intent.EXTRA_SUBJECT, "sub"), mapOf("EXTRA_TEXT" to "String", "EXTRA_SUBJECT" to "String"))
        runShareProbes("SHARE_TEXT_HTML", Intent(Intent.ACTION_SEND).setType("text/html").putExtra(Intent.EXTRA_TEXT, "test"), mapOf("EXTRA_TEXT" to "String"))
        runShareProbes("SHARE_IMG_JPEG", Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, getDummyUri("image/jpeg")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), mapOf("EXTRA_STREAM" to "Uri"))
        runShareProbes("SHARE_APP_JSON", Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, getDummyUri("application/json")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), mapOf("EXTRA_STREAM" to "Uri"))
        runShareProbes("SHARE_APP_PDF", Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, getDummyUri("application/pdf")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), mapOf("EXTRA_STREAM" to "Uri"))
        runShareProbes("SHARE_MUL_JPEG", Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(getDummyUri("image/jpeg"), getDummyUri("image/jpeg"))).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), mapOf("EXTRA_STREAM" to "ArrayList<Uri>"))
        runShareProbes("SHARE_MUL_PDF", Intent(Intent.ACTION_SEND_MULTIPLE).setType("application/pdf").putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(getDummyUri("application/pdf"), getDummyUri("application/pdf"))).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), mapOf("EXTRA_STREAM" to "ArrayList<Uri>"))
        logEvent("family_end", "SHARE")

        // ============================================
        // DOCUMENT_PICKER
        // ============================================
        logProgress("running_document_picker")
        logEvent("family_begin", "DOCUMENT_PICKER")
        runProbe("DOC_GET_CONTENT_ALL", "DOCUMENT_PICKER", Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), null)
        runProbe("DOC_GET_CONTENT_PDF", "DOCUMENT_PICKER", Intent(Intent.ACTION_GET_CONTENT).setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE), null)
        runProbe("DOC_GET_CONTENT_IMG", "DOCUMENT_PICKER", Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), null)
        runProbe("DOC_GET_CONTENT_TXT", "DOCUMENT_PICKER", Intent(Intent.ACTION_GET_CONTENT).setType("text/*").addCategory(Intent.CATEGORY_OPENABLE), null)

        runProbe("DOC_OPEN_DOC_ALL", "DOCUMENT_PICKER", Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), null)
        runProbe("DOC_OPEN_DOC_PDF", "DOCUMENT_PICKER", Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE), null)
        runProbe("DOC_OPEN_DOC_IMG", "DOCUMENT_PICKER", Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), null)
        runProbe("DOC_OPEN_DOC_TXT", "DOCUMENT_PICKER", Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/*").addCategory(Intent.CATEGORY_OPENABLE), null)

        runProbe("DOC_CREATE_DOC_TXT", "DOCUMENT_PICKER", Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE), null)
        runProbe("DOC_CREATE_DOC_JSON", "DOCUMENT_PICKER", Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE), null)
        logEvent("family_end", "DOCUMENT_PICKER")

        // ============================================
        // COMMUNICATION
        // ============================================
        logProgress("running_communication")
        logEvent("family_begin", "COMMUNICATION")
        runProbe("COMM_MAILTO", "COMMUNICATION", Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:test@example.com")), "mailto")
        runProbe("COMM_DIAL", "COMMUNICATION", Intent(Intent.ACTION_DIAL, Uri.parse("tel:0000000000")), "tel")
        logEvent("family_end", "COMMUNICATION")

        // ============================================
        // MAPS
        // ============================================
        logProgress("running_maps")
        logEvent("family_begin", "MAPS")
        runProbe("MAPS_GEO", "MAPS", Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Tokyo")), "geo")
        runProbe("MAPS_WEB", "MAPS", Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=Tokyo")), "url")
        logEvent("family_end", "MAPS")

        // ============================================
        // MARKET
        // ============================================
        logProgress("running_market")
        logEvent("family_begin", "MARKET")
        runProbe("MARKET_SCHEME", "MARKET", Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.android.chrome")), "market")
        runProbe("MARKET_WEB", "MARKET", Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.android.chrome")), "url")
        logEvent("family_end", "MARKET")

        // ============================================
        // SEARCH
        // ============================================
        logProgress("running_search")
        logEvent("family_begin", "SEARCH")
        runProbe("SEARCH_WEB", "SEARCH", Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", "example"), null, mapOf("query" to "String"))
        runProbe("SEARCH_APP", "SEARCH", Intent(Intent.ACTION_SEARCH).putExtra("query", "example"), null, mapOf("query" to "String"))
        logEvent("family_end", "SEARCH")

        // ============================================
        // MAIN_SELECTOR
        // ============================================
        logProgress("running_main_selector")
        logEvent("family_begin", "MAIN_SELECTOR")
        val mainCats = listOf(
            Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_APP_BROWSER, Intent.CATEGORY_APP_EMAIL,
            Intent.CATEGORY_APP_GALLERY, Intent.CATEGORY_APP_MARKET, Intent.CATEGORY_APP_MAPS,
            Intent.CATEGORY_APP_MESSAGING, Intent.CATEGORY_APP_MUSIC, Intent.CATEGORY_APP_CALENDAR,
            Intent.CATEGORY_APP_CONTACTS, "android.intent.category.APP_FILES", "android.intent.category.APP_WEATHER", "android.intent.category.APP_CALCULATOR"
        )
        mainCats.forEach { cat ->
            runProbe("MAIN_${cat.substringAfterLast('.')}", "MAIN_SELECTOR", Intent(Intent.ACTION_MAIN).addCategory(cat), null)
        }
        logEvent("family_end", "MAIN_SELECTOR")

        logEvent("component_summary_begin", "COMPONENT_SUMMARY")
        logProgress("building_component_surface_summary")
        // Build ComponentSurfaceSummary... Create a merged record per component
        val componentMap = mutableMapOf<String, ComponentSurfaceEntry>()
        
        probes.forEach { probe ->
            probe.candidates.forEach { c ->
                val compName = c.component_name
                val entry = componentMap.getOrPut(compName) {
                    ComponentSurfaceEntry(
                        component_name = c.component_name,
                        package_name = c.package_name,
                        activity_name = c.activity_name,
                        label = c.label,
                        exported = c.exported,
                        enabled = c.enabled,
                        permission = c.permission,
                        application_label = try { pm.getApplicationInfo(c.package_name, 0).loadLabel(pm).toString() } catch (e: Exception) { null },
                        package_version_name = c.package_version_name,
                        package_version_code = c.package_version_code,
                        package_target_sdk_version = c.package_target_sdk_version,
                        seen_in_probe_ids = emptyList(),
                        seen_families = emptyList(),
                        seen_actions = emptyList(),
                        seen_mime_types = emptyList(),
                        seen_schemes = emptyList(),
                        seen_category_sets = emptyList(),
                        seen_extras_keys = emptyList(),
                        package_target_statuses = emptyList(),
                        component_explicit_assessment = "",
                        risk_note = if (!c.enabled) "disabled" else if (!c.exported) "not_exported" else if (c.permission != null) "requires_permission" else "likely_safe_query_only"
                    )
                }
                
                // Merge data
                val newProbeIds = (entry.seen_in_probe_ids + probe.probe_id).distinct()
                val newFamilies = (entry.seen_families + probe.family).distinct()
                val newActions = if (probe.action != null) (entry.seen_actions + probe.action).distinct() else entry.seen_actions
                val newMimes = if (probe.mime_type != null) (entry.seen_mime_types + probe.mime_type).distinct() else entry.seen_mime_types
                val newSchemes = if (probe.data_scheme != null) (entry.seen_schemes + probe.data_scheme).distinct() else entry.seen_schemes
                val newCats = (entry.seen_category_sets + listOf(probe.categories)).distinct()
                val newExtras = (entry.seen_extras_keys + probe.extras_schema.keys).distinct()
                
                val pkgAssess = probe.package_targeted_assessments.find { it.target_package == c.package_name }?.status
                val newPkgStatuses = if (pkgAssess != null) (entry.package_target_statuses + pkgAssess).distinct() else entry.package_target_statuses
                val compAssess = probe.component_explicit_assessments.find { it.target_package == c.package_name && it.target_activity == c.activity_name }?.status ?: entry.component_explicit_assessment

                componentMap[compName] = entry.copy(
                    seen_in_probe_ids = newProbeIds,
                    seen_families = newFamilies,
                    seen_actions = newActions,
                    seen_mime_types = newMimes,
                    seen_schemes = newSchemes,
                    seen_category_sets = newCats,
                    seen_extras_keys = newExtras,
                    package_target_statuses = newPkgStatuses,
                    component_explicit_assessment = compAssess
                )
            }
        }

        val componentSurfaceSummary = componentMap.values.toList().sortedBy { it.component_name }
        logEvent("component_summary_end", "COMPONENT_SUMMARY")

        logProgress("building_summary")
        val families = probes.map { it.family }.distinct()
        val familySummaries = families.map { fam ->
            val famProbes = probes.filter { it.family == fam }
            val cands = famProbes.flatMap { it.candidates }
            val uniqueComps = cands.map { it.component_name }.distinct()
            
            // To properly match what is technically flags0, since we now add GET_RESOLVED_FILTER (64) we should check if raw is just MATCH_DEFAULT_ONLY.
            // But query_flags_raw before our mod is what it was. We added GET_RESOLVED_FILTER to qFlagsInt, but maybe queryFlagsRaw parameter is 0, getting saved.
            val compsFlags0 = famProbes.filter { it.query_flags_raw == 0 }.flatMap { it.candidates }.map { it.component_name }.toSet()
            val compsMatchDefault = famProbes.filter { (it.query_flags_raw and PackageManager.MATCH_DEFAULT_ONLY) != 0 }.flatMap { it.candidates }.map { it.component_name }.toSet()

            ProbeFamilySummary(
                family = fam,
                probe_count = famProbes.size,
                total_candidate_rows = cands.size,
                unique_component_count = uniqueComps.size,
                duplicate_candidate_row_count = cands.size - uniqueComps.size,
                unique_package_count = cands.map { it.package_name }.distinct().size,
                unique_components_seen_with_flags_0 = compsFlags0.size,
                unique_components_seen_with_match_default_only = compsMatchDefault.size,
                components_only_seen_with_flags_0 = compsFlags0.minus(compsMatchDefault).size,
                components_only_seen_with_match_default_only = compsMatchDefault.minus(compsFlags0).size,
                disabled_candidate_count = cands.count { !it.enabled },
                resolver_activity_assessment_count = famProbes.flatMap { it.package_targeted_assessments }.count { it.status == "RESOLVABLE_VIA_RESOLVER" },
                error_count = errors.count { it.family == fam },
                duration_ms = famProbes.sumOf { it.duration_ms }
            )
        }

        val totalCands = probes.sumOf { it.candidate_count }
        val allPkgsTargeted = probes.flatMap { it.package_targeted_assessments }
        val allCompsTargeted = probes.flatMap { it.component_explicit_assessments }

        val probeComparisons = mutableMapOf<String, ProbeComparisonSummary>()
        listOf("WEB_LINK", "FILE_VIEW", "SHARE").forEach { fam ->
            val fProbes = probes.filter { it.family == fam }
            if (fProbes.isNotEmpty()) {
                val noCat = fProbes.filter { it.categories.isEmpty() }.flatMap { it.candidates }.map { it.component_name }.toSet()
                val defCat = fProbes.filter { it.categories.contains("android.intent.category.DEFAULT") }.flatMap { it.candidates }.map { it.component_name }.toSet()
                val flags0 = fProbes.filter { it.query_flags_raw == 0 }.flatMap { it.candidates }.map { it.component_name }.toSet()
                val matchDef = fProbes.filter { (it.query_flags_raw and PackageManager.MATCH_DEFAULT_ONLY) != 0 }.flatMap { it.candidates }.map { it.component_name }.toSet()

                probeComparisons[fam] = ProbeComparisonSummary(
                    same_results_between_no_category_and_default = noCat == defCat,
                    same_results_between_flags_0_and_match_default_only = flags0 == matchDef,
                    components_added_by_default_category = defCat.minus(noCat).toList().sorted(),
                    components_removed_by_default_category = noCat.minus(defCat).toList().sorted(),
                    components_added_by_match_default_only = matchDef.minus(flags0).toList().sorted(),
                    components_removed_by_match_default_only = flags0.minus(matchDef).toList().sorted()
                )
            }
        }

        val summary = SurfaceDiagnosticSummary(
            schema = 5,
            report_kind = "moukaeritai_intent_surface",
            run_id = runId,
            probe_family_count = families.size,
            probe_count = probes.size,
            total_candidate_rows = totalCands,
            unique_component_count = componentSurfaceSummary.size,
            unique_package_count = componentSurfaceSummary.map { it.package_name }.distinct().size,
            candidates_by_family = familySummaries.associate { it.family to it.total_candidate_rows },
            candidates_by_probe = probes.associate { it.probe_id to it.candidate_count },
            disabled_candidates_by_family = familySummaries.associate { it.family to it.disabled_candidate_count },
            unique_components_by_family = familySummaries.associate { it.family to it.unique_component_count },
            package_target_resolvable_direct_count = allPkgsTargeted.count { it.status == "RESOLVABLE_DIRECT" },
            package_target_resolvable_via_resolver_count = allPkgsTargeted.count { it.status == "RESOLVABLE_VIA_RESOLVER" },
            package_target_not_resolvable_count = allPkgsTargeted.count { it.status == "NOT_RESOLVABLE" },
            component_spec_built_count = allCompsTargeted.count { it.status == "COMPONENT_SPEC_BUILT" },
            component_disabled_count = allCompsTargeted.count { it.status == "DISABLED" },
            component_not_exported_count = allCompsTargeted.count { it.status == "NOT_EXPORTED" },
            component_requires_permission_count = allCompsTargeted.count { it.status == "REQUIRES_PERMISSION" },
            error_count = errors.size,
            report_duration_ms = System.currentTimeMillis() - startTime,
            invocation_mode_summary = InvocationModeSummary(
                implicit_resolution_candidate_count = totalCands,
                package_targeted_resolvable_direct_count = allPkgsTargeted.count { it.status == "RESOLVABLE_DIRECT" },
                package_targeted_resolvable_via_resolver_count = allPkgsTargeted.count { it.status == "RESOLVABLE_VIA_RESOLVER" },
                package_targeted_not_resolvable_count = allPkgsTargeted.count { it.status == "NOT_RESOLVABLE" },
                component_explicit_spec_built_count = allCompsTargeted.count { it.status == "COMPONENT_SPEC_BUILT" },
                component_explicit_disabled_count = allCompsTargeted.count { it.status == "DISABLED" },
                component_explicit_not_exported_count = allCompsTargeted.count { it.status == "NOT_EXPORTED" },
                component_explicit_requires_permission_count = allCompsTargeted.count { it.status == "REQUIRES_PERMISSION" },
                component_explicit_launch_tested_count = 0
            ),
            probe_comparisons = probeComparisons
        )

        val catalog = IntentInvocationCatalogBuilder().build(probes)

        return IntentSurfaceReport(
            schema = 5,
            schema_version = 5,
            schema_id = "work.moukaeritai.intent-surface-report.schema.v5",
            report_kind = "moukaeritai_intent_surface",
            run_id = runId,
            file_name = fileName,
            generated_at_epoch_millis = System.currentTimeMillis(),
            generated_at_utc = java.time.Instant.now().toString(),
            app = appInfo,
            device = deviceInfo,
            package_visibility = visibilityInfo,
            intent_invocation_catalog = catalog,
            probe_families = familySummaries,
            intent_surface_probes = probes,
            component_surface_summary = componentSurfaceSummary,
            summary = summary,
            errors = errors,
            events = events
        )
    }
}

package packagelist.android.moukaeritai.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IntentInvocationCatalogBuilderTest {

    @Test
    fun testBuilderMapping() {
        val probes = listOf(
            IntentSurfaceProbeResult(
                probe_id = "test_probe_1",
                family = "test_family",
                action = "android.intent.action.VIEW",
                data_uri_kind = "URL",
                data_scheme = "https",
                data_display_redacted = "https://example.com/redacted",
                mime_type = null,
                categories = listOf("android.intent.category.DEFAULT"),
                flags = 0,
                extras_schema = mapOf("test_extra" to "String"),
                intent_spec = IntentSpec(
                    action = "android.intent.action.VIEW",
                    data_uri_kind = "URL",
                    data_scheme = "https",
                    data_display_redacted = "https://example.com/redacted",
                    mime_type = null,
                    categories = listOf("android.intent.category.DEFAULT"),
                    intent_flags_raw = 0,
                    intent_flags_labels = listOf("FLAG_ACTIVITY_NEW_TASK"),
                    extras_schema = mapOf("test_extra" to "String"),
                    clip_data_schema = null
                ),
                query_flags = 0,
                query_flags_raw = 0,
                query_flags_labels = emptyList(),
                candidate_count = 1,
                enabled_candidate_count = 1,
                disabled_candidate_count = 0,
                unique_package_count = 1,
                candidates = listOf(
                    CandidateResult(
                        index = 0,
                        package_name = "com.example.browser",
                        activity_name = "com.example.browser.MainActivity",
                        component_name = "com.example.browser/com.example.browser.MainActivity",
                        label = "Browser",
                        exported = true,
                        enabled = true,
                        permission = null
                    )
                ),
                resolve_activity_result = null,
                package_targeted_assessments = listOf(
                    TargetAssessmentResult(
                        mode = "PACKAGE",
                        target_package = "com.example.browser",
                        target_activity = null,
                        candidate_count = 1,
                        resolved_package = "com.example.browser",
                        resolved_activity = "com.example.browser.MainActivity",
                        status = "RESOLVABLE_DIRECT",
                        note = null
                    )
                ),
                component_explicit_assessments = listOf(
                    TargetAssessmentResult(
                        mode = "COMPONENT",
                        target_package = "com.example.browser",
                        target_activity = "com.example.browser.MainActivity",
                        candidate_count = 1,
                        resolved_package = "com.example.browser",
                        resolved_activity = "com.example.browser.MainActivity",
                        status = "COMPONENT_SPEC_BUILT",
                        note = null
                    )
                ),
                duration_ms = 10,
                errors = emptyList()
            )
        )

        val builder = IntentInvocationCatalogBuilder()
        val catalog = builder.build(probes)

        assertEquals("moukaeritai.intent_invocation_catalog", catalog.catalog_kind)
        assertEquals(1, catalog.candidate_count)
        assertEquals(1, catalog.candidates.size)

        val candidate = catalog.candidates.first()
        assertEquals("cand.test_probe_1.com_example_browser.com_example_browser_MainActivity", candidate.candidate_id)
        assertEquals("com.example.browser", candidate.target.package_name)
        assertEquals("com.example.browser.MainActivity", candidate.target.activity_name)
        assertEquals("setData", candidate.intent_recipe.data.set_api)
        assertEquals("EXPLICIT_COMPONENT_STATIC_OK", candidate.evidence.component_static_assessment)
        assertEquals("PACKAGE_TARGETED_RESOLVABLE_DIRECT", candidate.evidence.package_targeted_resolution_status)
        assertEquals(false, candidate.evidence.start_activity_attempted)
        assertEquals("START_ACTIVITY_NOT_TESTED", candidate.evidence.launch_result)
        assertEquals(false, candidate.safety.auto_launch_allowed)
    }
}

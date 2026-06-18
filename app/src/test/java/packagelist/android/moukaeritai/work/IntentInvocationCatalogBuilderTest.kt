package packagelist.android.moukaeritai.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentInvocationCatalogBuilderTest {

    private fun createSpec(
        dataUriKind: String? = null,
        dataScheme: String? = null,
        dataRedacted: String? = null,
        mimeType: String? = null,
        extras: Map<String, String> = emptyMap(),
        flags: List<String> = emptyList()
    ) = IntentSpec(
        action = "android.intent.action.VIEW",
        data_uri_kind = dataUriKind,
        data_scheme = dataScheme,
        data_display_redacted = dataRedacted,
        mime_type = mimeType,
        categories = emptyList(),
        intent_flags_raw = 0,
        intent_flags_labels = flags,
        extras_schema = extras,
        clip_data_schema = null
    )

    private fun createProbeResult(spec: IntentSpec, explicitStatus: String = "COMPONENT_SPEC_BUILT") = IntentSurfaceProbeResult(
        probe_id = "test_probe",
        family = "test_family",
        action = spec.action ?: "android.intent.action.VIEW",
        data_uri_kind = spec.data_uri_kind,
        data_scheme = spec.data_scheme,
        data_display_redacted = spec.data_display_redacted,
        mime_type = spec.mime_type,
        categories = spec.categories,
        flags = 0,
        extras_schema = spec.extras_schema,
        intent_spec = spec,
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
        package_targeted_assessments = emptyList(),
        component_explicit_assessments = listOf(
            TargetAssessmentResult(
                mode = "COMPONENT",
                target_package = "com.example.browser",
                target_activity = "com.example.browser.MainActivity",
                candidate_count = 1,
                resolved_package = "com.example.browser",
                resolved_activity = "com.example.browser.MainActivity",
                status = explicitStatus,
                note = null
            )
        ),
        duration_ms = 1,
        errors = emptyList()
    )

    @Test
    fun testBuilderMapping_Basic() {
        val spec = createSpec(
            dataUriKind = "URL",
            dataScheme = "https",
            dataRedacted = "https://example.com/item/123",
            flags = listOf("FLAG_ACTIVITY_NEW_TASK")
        )
        val builder = IntentInvocationCatalogBuilder()
        val catalog = builder.build(listOf(createProbeResult(spec)))
        
        assertEquals(1, catalog.candidate_count)
        val candidate = catalog.candidates.first()
        assertEquals("cand.test_probe.com_example_browser.com_example_browser_MainActivity", candidate.candidate_id)
        assertEquals("setData", candidate.intent_recipe.data.set_api)
        assertEquals("https://example.com/item/123", candidate.intent_recipe.data.uri)
    }

    // 1. Catalog builder does not emit "content://example".
    @Test
    fun testBuilderDoesNotEmitContentExample() {
        val spec = createSpec(
            dataUriKind = "FILE_PROVIDER_CONTENT_URI",
            dataScheme = "content",
            dataRedacted = "content://com.example.fileprovider/file"
        )
        val builder = IntentInvocationCatalogBuilder()
        val catalog = builder.build(listOf(createProbeResult(spec)))
        val candidate = catalog.candidates.first()
        
        // Ensure no "content://example" fallback string nor any content:// address is present in URI
        assertNull(candidate.intent_recipe.data.uri)
        assertTrue(candidate.intent_recipe.data.set_api == CatalogConstants.SET_API_RUNTIME_PROVIDED_DATA)
    }

    // 2. Catalog builder uses runtime requirements for content URI cases.
    @Test
    fun testBuilderUsesRuntimeRequirementsForContentUri() {
        val spec = createSpec(
            dataUriKind = "FILE_PROVIDER_CONTENT_URI",
            dataScheme = "content",
            dataRedacted = "content://com.example.fileprovider/file"
        )
        val builder = IntentInvocationCatalogBuilder()
        val catalog = builder.build(listOf(createProbeResult(spec)))
        val candidate = catalog.candidates.first()
        
        assertNull(candidate.intent_recipe.data.uri)
        assertEquals(CatalogConstants.SET_API_RUNTIME_PROVIDED_DATA, candidate.intent_recipe.data.set_api)
        
        val reqs = candidate.intent_recipe.runtime_requirements
        assertEquals(1, reqs.size)
        val req = reqs.first()
        assertEquals(CatalogConstants.REQ_GENERATED_TEMP_URI, req.requirement_type)
        assertEquals(CatalogConstants.VAL_TYPE_URI_STRING, req.expected_value_type)
        assertTrue(req.required)
    }

    // 3. Catalog builder maps extras types explicitly.
    @Test
    fun testBuilderMapsExtrasExplicitly() {
        val schema = mapOf(
            "txt" to "String",
            "valLong" to "long",
            "flag" to "boolean",
            "url" to "Uri"
        )
        val spec = createSpec(extras = schema)
        val builder = IntentInvocationCatalogBuilder()
        val catalog = builder.build(listOf(createProbeResult(spec)))
        val candidate = catalog.candidates.first()
        
        val extras = candidate.intent_recipe.extras
        assertEquals(4, extras.size)
        assertEquals("STRING", extras.find { it.key == "txt" }?.value_type)
        assertEquals("LONG", extras.find { it.key == "valLong" }?.value_type)
        assertEquals("BOOLEAN", extras.find { it.key == "flag" }?.value_type)
        assertEquals("URI_STRING", extras.find { it.key == "url" }?.value_type)
    }

    // 4. Catalog builder maps unknown extras types to "UNKNOWN".
    @Test
    fun testBuilderMapsUnknownExtrasTypesToUnknown() {
        val schema = mapOf(
            "customObj" to "com.example.MyParcelable"
        )
        val spec = createSpec(extras = schema)
        val builder = IntentInvocationCatalogBuilder()
        val catalog = builder.build(listOf(createProbeResult(spec)))
        val candidate = catalog.candidates.first()
        
        val extras = candidate.intent_recipe.extras
        val extra = extras.find { it.key == "customObj" }!!
        assertEquals("UNKNOWN", extra.value_type)
        assertTrue(extra.notes.any { it.contains("unknown extra type") })
    }

    // 5. Catalog builder filters PackageManager query flags out of consumer-facing recipe flags.
    @Test
    fun testBuilderFiltersQueryFlags() {
        val spec = createSpec(
            flags = listOf("FLAG_ACTIVITY_NEW_TASK", "GET_ACTIVITIES", "FLAG_GRANT_READ_URI_PERMISSION", "MATCH_DEFAULT_ONLY")
        )
        val builder = IntentInvocationCatalogBuilder()
        val catalog = builder.build(listOf(createProbeResult(spec)))
        val candidate = catalog.candidates.first()
        
        val flags = candidate.intent_recipe.flags
        val grantFlags = candidate.intent_recipe.grant_flags
        
        // Allowed safe flags only
        assertEquals(listOf("FLAG_ACTIVITY_NEW_TASK"), flags)
        assertEquals(listOf("FLAG_GRANT_READ_URI_PERMISSION"), grantFlags)
        
        // GET_ACTIVITIES and MATCH_DEFAULT_ONLY should be filtered out
        assertFalse(flags.contains("GET_ACTIVITIES"))
        assertFalse(flags.contains("MATCH_DEFAULT_ONLY"))
        assertFalse(grantFlags.contains("GET_ACTIVITIES"))
    }

    // 6. Catalog builder maps component statuses safely.
    @Test
    fun testBuilderMapsComponentStatusesSafely() {
        val spec = createSpec()
        val builder = IntentInvocationCatalogBuilder()
        
        val cat1 = builder.build(listOf(createProbeResult(spec, "COMPONENT_SPEC_BUILT")))
        assertEquals("EXPLICIT_COMPONENT_STATIC_OK", cat1.candidates.first().evidence.component_static_assessment)
        
        val cat2 = builder.build(listOf(createProbeResult(spec, "DISABLED")))
        assertEquals("EXPLICIT_COMPONENT_DISABLED", cat2.candidates.first().evidence.component_static_assessment)
    }

    // 7. Unknown component status does not become "EXPLICIT_COMPONENT_STATIC_OK".
    @Test
    fun testBuilderMapsUnknownComponentStatusToUnknown() {
        val spec = createSpec()
        val builder = IntentInvocationCatalogBuilder()
        val catalog = builder.build(listOf(createProbeResult(spec, "SOME_TOTALLY_NEW_OR_CORRUPT_STATUS")))
        val candidate = catalog.candidates.first()
        
        assertEquals("UNKNOWN", candidate.evidence.component_static_assessment)
    }
}

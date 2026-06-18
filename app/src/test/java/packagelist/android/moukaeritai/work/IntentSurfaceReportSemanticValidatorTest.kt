package packagelist.android.moukaeritai.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentSurfaceReportSemanticValidatorTest {

    private fun createMinimalReport(catalog: IntentInvocationCatalog? = null): IntentSurfaceReport {
        return IntentSurfaceReport(
            schema = 5,
            schema_version = 5,
            schema_id = "work.moukaeritai.intent-surface-report.schema.v5",
            report_kind = "moukaeritai_intent_surface",
            run_id = "run_123",
            file_name = "report.json",
            generated_at_epoch_millis = 12345L,
            generated_at_utc = "Z",
            app = AppInfo(""," ",0,0,0,"",null,null,null,null,false,null, emptyList(),emptyList(),false),
            device = DeviceInfo(0,"","","","","","",emptyList(),"","",0,0,0),
            package_visibility = PackageVisibilityInfo("",false,false,emptyList(),""),
            intent_invocation_catalog = catalog,
            probe_families = emptyList(),
            intent_surface_probes = emptyList(),
            component_surface_summary = emptyList(),
            summary = SurfaceDiagnosticSummary(5,"test","run",0,0,0,0,0,emptyMap(),emptyMap(),emptyMap(),emptyMap(),0,0,0,0,0,0,0,0,0,null,emptyMap()),
            errors = emptyList(),
            events = emptyList()
        )
    }

    @Test
    fun testValidSchema() {
        val validator = IntentSurfaceReportSemanticValidator()
        val catalog = IntentInvocationCatalog(
            candidate_count = 0,
            candidates = emptyList()
        )
        val report = createMinimalReport(catalog)
        val result = validator.validate(report)
        assertTrue(result.errors.joinToString(","), result.isValid)
    }

    @Test
    fun testMissingCatalog() {
        val validator = IntentSurfaceReportSemanticValidator()
        val report = createMinimalReport(catalog = null)
        val result = validator.validate(report)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("intent_invocation_catalog is missing") })
    }

    @Test
    fun testInvalidLaunchAttempted() {
        val candidate = IntentInvocationCandidate(
            "id_1", "probe_1", "family", "label",
            IntentInvocationTarget("pkg", "act", "pkg/act"),
            IntentInvocationRecipe("COMPONENT_EXPLICIT", "setComponent", null, IntentInvocationData("none", null,"NONE",null,null,null), emptyList(), emptyList(), null, emptyList(), emptyList()),
            IntentInvocationEvidence(true, "status", 0, "status", "status", true, "FOO"),
            IntentInvocationSafety(true, true, "UNKNOWN", emptyList())
        )
        val catalog = IntentInvocationCatalog(candidate_count = 1, candidates = listOf(candidate))
        val report = createMinimalReport(catalog)
        
        val validator = IntentSurfaceReportSemanticValidator()
        val result = validator.validate(report)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("start_activity_attempted must be false") })
        assertTrue(result.errors.any { it.contains("launch_result must be START_ACTIVITY_NOT_TESTED") })
        assertTrue(result.errors.any { it.contains("auto_launch_allowed must be false") })
    }
}

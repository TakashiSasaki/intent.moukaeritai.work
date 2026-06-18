package packagelist.android.moukaeritai.work

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun json_serialization_is_correct() {
      val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
      val adapter = moshi.adapter(IntentSurfaceReport::class.java).indent("  ")

      val emptyReport = IntentSurfaceReport(
          run_id = "test-run",
          file_name = "test.json",
          generated_at_epoch_millis = 0L,
          generated_at_utc = "1970",
          app = AppInfo(
              app_package_name = "pkg", 
              app_version_name = "1.0", 
              app_version_code = 1, 
              target_sdk_version = 30, 
              min_sdk_version = 24, 
              build_type = "release", 
              debug_build = false, 
              installer_package_name = null, 
              requested_permissions = emptyList(), 
              granted_permissions = emptyList(), 
              query_all_packages_granted = false
          ),
          device = DeviceInfo(30, "11", "M", "B", "M", "D", "P", emptyList(), "en", "UTC", 0, 0, 0),
          package_visibility = PackageVisibilityInfo("test", false, false, emptyList(), "desc"),
          probe_families = emptyList(),
          intent_surface_probes = emptyList(),
          component_surface_summary = emptyList(),
          summary = SurfaceDiagnosticSummary(
              schema = 5, run_id = "test",
              probe_family_count = 0, probe_count = 0, total_candidate_rows = 0,
              unique_component_count = 0, unique_package_count = 0, candidates_by_family = emptyMap(),
              candidates_by_probe = emptyMap(), disabled_candidates_by_family = emptyMap(),
              unique_components_by_family = emptyMap(), package_target_resolvable_direct_count = 0,
              package_target_resolvable_via_resolver_count = 0, package_target_not_resolvable_count = 0,
              component_spec_built_count = 0, component_disabled_count = 0, component_not_exported_count = 0,
              component_requires_permission_count = 0, error_count = 0, report_duration_ms = 0,
              invocation_mode_summary = InvocationModeSummary(0, 0, 0, 0, 0, 0, 0, 0, 0)
          ),
          errors = emptyList(),
          events = emptyList()
      )

      val jsonString = adapter.toJson(emptyReport)
      assertTrue(jsonString.contains(""""run_id": "test-run""""))
      assertTrue(jsonString.contains(""""file_name": "test.json""""))
      
      val parsedInfo = adapter.fromJson(jsonString)
      assertNotNull(parsedInfo)
      assertEquals("test-run", parsedInfo?.run_id)
  }
}    

package com.example.dhruvar

import com.example.dhruvar.data.map.PlaceSearchGateway
import com.example.dhruvar.data.repository.InMemoryLayoutRepository
import com.example.dhruvar.domain.location.DeviceLocationFix
import com.example.dhruvar.domain.location.FakeDeviceLocationController
import com.example.dhruvar.domain.model.PlaceSearchHit
import com.example.dhruvar.ui.map.MapCameraBridge
import com.example.dhruvar.viewmodel.PlanningViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Search debounce, cancellation and failure handling. The stale-response case in particular is
 * not something manual QA can reliably catch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceSearchStateTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeGateway(
        override val isConfigured: Boolean = true,
        private val latencyMillis: Long = 0,
        private val respond: (String) -> Result<List<PlaceSearchHit>> = { Result.success(emptyList()) }
    ) : PlaceSearchGateway {
        val queries = mutableListOf<String>()
        var lastNear: Pair<Double, Double>? = null

        override suspend fun search(
            query: String,
            near: Pair<Double, Double>?
        ): Result<List<PlaceSearchHit>> {
            queries += query
            lastNear = near
            if (latencyMillis > 0) delay(latencyMillis)
            return respond(query)
        }
    }

    private fun hit(id: String, name: String) = PlaceSearchHit(
        id = id,
        name = name,
        englishName = null,
        kind = "place",
        subtype = "city",
        rank = 1,
        latitude = 28.6,
        longitude = 77.2
    )

    private fun viewModel(gateway: PlaceSearchGateway) = PlanningViewModel(
        repository = InMemoryLayoutRepository(),
        locationController = FakeDeviceLocationController(initialPermissionGranted = true),
        placeSearch = gateway,
        searchDebounceMillis = 250L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `typing a word issues one request, not one per character`() = runTest(dispatcher) {
        val gateway = FakeGateway(respond = { Result.success(listOf(hit("1", "Delhi"))) })
        val vm = viewModel(gateway)

        vm.setSearchExpanded(true)
        "delhi".forEachIndexed { index, _ -> vm.onSearchQueryChange("delhi".take(index + 1)) }
        advanceUntilIdle()

        assertEquals(listOf("delhi"), gateway.queries)
        assertEquals(listOf("Delhi"), vm.uiState.value.placeSearch.results.map { it.displayName })
    }

    @Test
    fun `nothing is requested until the debounce elapses`() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val vm = viewModel(gateway)

        vm.setSearchExpanded(true)
        vm.onSearchQueryChange("delhi")
        advanceTimeBy(200)

        assertTrue(gateway.queries.isEmpty())
        assertTrue(vm.uiState.value.placeSearch.isSearching)

        advanceUntilIdle()
        assertEquals(listOf("delhi"), gateway.queries)
        assertFalse(vm.uiState.value.placeSearch.isSearching)
    }

    @Test
    fun `a slow earlier response cannot overwrite a newer query`() = runTest(dispatcher) {
        val gateway = FakeGateway(
            latencyMillis = 1_000,
            respond = { query -> Result.success(listOf(hit(query, query.uppercase()))) }
        )
        val vm = viewModel(gateway)

        vm.setSearchExpanded(true)
        vm.onSearchQueryChange("del")
        advanceTimeBy(300) // debounce elapsed, request in flight
        vm.onSearchQueryChange("mumbai")
        advanceUntilIdle()

        assertEquals(listOf("del", "mumbai"), gateway.queries)
        assertEquals(
            listOf("MUMBAI"),
            vm.uiState.value.placeSearch.results.map { it.displayName }
        )
    }

    @Test
    fun `clearing the query drops results without another request`() = runTest(dispatcher) {
        val gateway = FakeGateway(respond = { Result.success(listOf(hit("1", "Delhi"))) })
        val vm = viewModel(gateway)

        vm.setSearchExpanded(true)
        vm.onSearchQueryChange("delhi")
        advanceUntilIdle()
        vm.onSearchQueryChange("")
        advanceUntilIdle()

        assertEquals(listOf("delhi"), gateway.queries)
        assertTrue(vm.uiState.value.placeSearch.results.isEmpty())
        assertFalse(vm.uiState.value.placeSearch.isSearching)
    }

    @Test
    fun `a failed request reports unavailable and keeps no stale results`() = runTest(dispatcher) {
        val gateway = FakeGateway(respond = { Result.failure(java.io.IOException("offline")) })
        val vm = viewModel(gateway)

        vm.setSearchExpanded(true)
        vm.onSearchQueryChange("delhi")
        advanceUntilIdle()

        val state = vm.uiState.value.placeSearch
        assertEquals(PlanningViewModel.SEARCH_UNAVAILABLE, state.error)
        assertTrue(state.results.isEmpty())
        assertFalse(state.isSearching)
    }

    @Test
    fun `an unconfigured server says so and never issues a request`() = runTest(dispatcher) {
        val gateway = FakeGateway(isConfigured = false)
        val vm = viewModel(gateway)

        vm.setSearchExpanded(true)
        assertEquals(
            PlanningViewModel.SEARCH_NOT_CONFIGURED,
            vm.uiState.value.placeSearch.error
        )

        vm.onSearchQueryChange("delhi")
        advanceUntilIdle()

        assertTrue(gateway.queries.isEmpty())
        assertFalse(vm.uiState.value.placeSearch.isSearching)
        assertEquals(
            PlanningViewModel.SEARCH_NOT_CONFIGURED,
            vm.uiState.value.placeSearch.error
        )
    }

    @Test
    fun `collapsing cancels the in-flight request and clears the query`() = runTest(dispatcher) {
        val gateway = FakeGateway(latencyMillis = 1_000)
        val vm = viewModel(gateway)

        vm.setSearchExpanded(true)
        vm.onSearchQueryChange("delhi")
        advanceTimeBy(300)
        vm.setSearchExpanded(false)
        advanceUntilIdle()

        val state = vm.uiState.value.placeSearch
        assertFalse(state.isExpanded)
        assertEquals("", state.query)
        assertTrue(state.results.isEmpty())
        assertFalse(state.isSearching)
        assertNull(state.error)
    }

    @Test
    fun `selecting a hit flies the camera and collapses search`() = runTest(dispatcher) {
        val chosen = hit("1", "Delhi")
        val vm = viewModel(FakeGateway(respond = { Result.success(listOf(chosen)) }))

        vm.setSearchExpanded(true)
        vm.onSearchQueryChange("delhi")
        advanceUntilIdle()
        vm.onSearchResultSelected(chosen)

        val target = MapCameraBridge.target.value
        assertEquals(chosen.latitude, target?.latitude)
        assertEquals(chosen.longitude, target?.longitude)
        assertFalse(vm.uiState.value.placeSearch.isExpanded)
        assertEquals("Centered on Delhi", vm.uiState.value.userFeedbackMessage)

        target?.let { MapCameraBridge.consumed(it) }
    }

    @Test
    fun `a device fix biases results toward the operator`() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val vm = PlanningViewModel(
            repository = InMemoryLayoutRepository(),
            locationController = FakeDeviceLocationController(
                initialPermissionGranted = true
            ).also {
                it.emitFix(
                    DeviceLocationFix(
                        latitude = 28.61,
                        longitude = 77.20,
                        horizontalAccuracyMeters = 5f
                    )
                )
            },
            placeSearch = gateway,
            searchDebounceMillis = 250L
        )

        vm.setSearchExpanded(true)
        vm.onSearchQueryChange("delhi")
        advanceUntilIdle()

        assertEquals(28.61 to 77.20, gateway.lastNear)
    }

    @Test
    fun `selecting a hit does not disturb the layout origin or region`() = runTest(dispatcher) {
        val chosen = hit("1", "Delhi")
        val vm = viewModel(FakeGateway())
        val before = vm.uiState.value.currentLayout

        vm.onSearchResultSelected(chosen)

        assertEquals(before.anchor, vm.uiState.value.currentLayout.anchor)
        assertEquals(before.regionBounds, vm.uiState.value.currentLayout.regionBounds)

        MapCameraBridge.target.value?.let { MapCameraBridge.consumed(it) }
    }
}

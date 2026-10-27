package com.factswallpaper.feature.settings

import com.factswallpaper.core.ai.AiModelInfo
import com.factswallpaper.core.ai.ConnectionTestResult
import com.factswallpaper.core.ai.ModelCatalogResult
import com.factswallpaper.core.ai.ProviderDiagnosticsService
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.DiagnosticsRepository
import com.factswallpaper.core.data.repository.ProviderProfileRepository
import com.factswallpaper.core.datastore.AppPreferences
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.AiRequestLogEntry
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.ProviderKeyStatus
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.ProviderType
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.model.UsageEntry
import com.factswallpaper.core.model.UsageSummary
import com.factswallpaper.core.security.ApiKeyStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val fakeProfileRepo = FakeProviderProfileRepository()
    private val fakeSettingsRepo = FakeSettingsRepository()
    private val fakeDiagnosticsRepo = FakeDiagnosticsRepository()
    private val fakeApiKeyStore = FakeApiKeyStore()
    private val fakeDiagnosticsService = FakeProviderDiagnosticsService()

    private lateinit var viewModel: ProviderSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ProviderSettingsViewModel(
            profileRepository = fakeProfileRepo,
            settingsRepository = fakeSettingsRepo,
            apiKeyStore = fakeApiKeyStore,
            diagnosticsService = fakeDiagnosticsService,
            diagnosticsRepository = fakeDiagnosticsRepo,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads profiles and configured keys`() = runTest {
        val collectJob = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val testProfile = ProviderProfile(
            id = "test-1",
            name = "Test Provider",
            providerType = ProviderType.OPENAI,
            baseUrl = "https://api.openai.com/v1",
            modelName = "gpt-4o",
            maxContextTokens = 128000,
        )
        fakeProfileRepo.profilesFlow.value = listOf(testProfile)
        fakeApiKeyStore.keys["test-1"] = "sk-test-key"

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ProviderSettingsUiState.Content::class.java)
        val content = state as ProviderSettingsUiState.Content
        assertThat(content.profiles).containsExactly(testProfile)
        assertThat(content.configuredKeyProfileIds).containsExactly("test-1")
    }

    @Test
    fun `setRoutingStrategy updates settings repository`() = testScope.runTest {
        viewModel.setRoutingStrategy(RoutingStrategy.CHEAPEST)
        advanceUntilIdle()

        assertThat(fakeSettingsRepo.preferencesFlow.value.routingStrategy)
            .isEqualTo(RoutingStrategy.CHEAPEST)
        assertThat(viewModel.message.value?.textRes).isEqualTo(R.string.provider_routing_saved)
    }

    @Test
    fun `saveProfile saves profile and key to keystore`() = testScope.runTest {
        viewModel.saveProfile(
            existing = null,
            name = "New Anthropic",
            providerType = ProviderType.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            modelName = "claude-3-5-sonnet",
            maxContextTokens = 200000,
            apiKey = "sk-ant-test-1234",
        )
        advanceUntilIdle()

        val saved = fakeProfileRepo.profilesFlow.value
        assertThat(saved).hasSize(1)
        val profile = saved.first()
        assertThat(profile.name).isEqualTo("New Anthropic")
        assertThat(profile.providerType).isEqualTo(ProviderType.ANTHROPIC)
        assertThat(fakeApiKeyStore.keys[profile.id]).isEqualTo("sk-ant-test-1234")
    }

    @Test
    fun `saveProfile persists configured maximum output tokens`() = testScope.runTest {
        viewModel.saveProfile(
            existing = null,
            name = "Reasoning provider",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://integrate.api.nvidia.com/v1",
            modelName = "nvidia/nemotron-3.5-super-vl-120b-a12b",
            maxContextTokens = 128_000,
            maxOutputTokens = 12_288,
        )
        advanceUntilIdle()

        assertThat(fakeProfileRepo.profilesFlow.value.single().maxOutputTokens).isEqualTo(12_288)
    }

    @Test
    fun `deleteApiKey removes key and updates message`() = testScope.runTest {
        fakeApiKeyStore.keys["test-id"] = "test-key"

        viewModel.deleteApiKey("test-id")
        advanceUntilIdle()

        assertThat(fakeApiKeyStore.keys["test-id"]).isNull()
        assertThat(viewModel.message.value?.textRes).isEqualTo(R.string.provider_key_cleared)
    }

    @Test
    fun `testConnection calls diagnostics and shows success`() = testScope.runTest {
        val testProfile = ProviderProfile(
            id = "test-1",
            name = "Test Provider",
            providerType = ProviderType.OPENAI,
            baseUrl = "https://api.openai.com/v1",
            modelName = "gpt-4o",
            maxContextTokens = 128000,
        )
        fakeDiagnosticsService.testResult = ConnectionTestResult(
            success = true,
            latencyMs = 120,
            message = "Connected in 120 ms.",
            keyStatus = ProviderKeyStatus.VALID,
        )

        viewModel.testConnection(testProfile)
        advanceUntilIdle()

        assertThat(viewModel.message.value?.textRes).isEqualTo(R.string.provider_test_success)
        assertThat(viewModel.message.value?.args).containsExactly(120L)
    }
}

private class FakeProviderDiagnosticsService : ProviderDiagnosticsService {
    var testResult: ConnectionTestResult = ConnectionTestResult(
        success = true,
        latencyMs = 100,
        message = "Connected in 100 ms.",
        keyStatus = ProviderKeyStatus.VALID,
    )
    var catalogResult: ModelCatalogResult = ModelCatalogResult(models = emptyList())

    override suspend fun testConnection(profile: ProviderProfile): ConnectionTestResult = testResult
    override suspend fun fetchModels(profile: ProviderProfile): ModelCatalogResult = catalogResult
}

private class FakeProviderProfileRepository : ProviderProfileRepository {
    val profilesFlow = MutableStateFlow<List<ProviderProfile>>(emptyList())

    override fun observeProfiles(): Flow<List<ProviderProfile>> = profilesFlow
    override suspend fun getProfiles(): List<ProviderProfile> = profilesFlow.value
    override suspend fun getProfile(profileId: String): ProviderProfile? =
        profilesFlow.value.firstOrNull { it.id == profileId }

    override suspend fun saveProfile(profile: ProviderProfile) {
        val current = profilesFlow.value.filter { it.id != profile.id }
        profilesFlow.value = current + profile
    }

    override suspend fun setDefaultProfile(profileId: String) {
        profilesFlow.value = profilesFlow.value.map {
            it.copy(isDefault = it.id == profileId)
        }
    }

    override suspend fun deleteProfile(profileId: String): AppResult<Unit> {
        profilesFlow.value = profilesFlow.value.filter { it.id != profileId }
        return AppResult.Success(Unit)
    }
}

private class FakeSettingsRepository : SettingsRepository {
    val preferencesFlow = MutableStateFlow(AppPreferences())

    override fun observePreferences(): Flow<AppPreferences> = preferencesFlow
    override suspend fun setOnboardingCompleted(completed: Boolean) {}
    override suspend fun setAppThemeMode(mode: ThemeMode) {}
    override suspend fun setWallpaperThemeMode(mode: ThemeMode) {}
    override suspend fun setRoutingStrategy(strategy: RoutingStrategy) {
        preferencesFlow.value = preferencesFlow.value.copy(routingStrategy = strategy)
    }
    override suspend fun setTaskProfileOverrides(overrides: Map<AiTask, String>) {
        preferencesFlow.value = preferencesFlow.value.copy(taskProfileOverrides = overrides)
    }
    override suspend fun setTaskProfileOverride(task: AiTask, profileId: String?) {
        val updated = preferencesFlow.value.taskProfileOverrides.toMutableMap()
        if (profileId == null) updated.remove(task) else updated[task] = profileId
        preferencesFlow.value = preferencesFlow.value.copy(taskProfileOverrides = updated)
    }
    override suspend fun setCustomTaskPromptSuffixes(suffixes: Map<AiTask, String>) {}
    override suspend fun setCustomTaskPromptSuffix(task: AiTask, instruction: String?) {}
    override suspend fun setDefaultOcrPageLimit(limit: Int) {}
    override suspend fun setAllowAdvancedOcrEngines(allowed: Boolean) {}
    override suspend fun setPreRenderQueueSize(size: Int) {}
    override suspend fun setEncryptedDatabaseEnabled(enabled: Boolean) {}
    override suspend fun setEnableAiDiagnosticsLog(enabled: Boolean) {}
    override suspend fun setEnableDoubleTapWallpaperAdvance(enabled: Boolean) {}
    override suspend fun setAiCostGuardThresholdUsd(thresholdUsd: Double) {}
}

private class FakeDiagnosticsRepository : DiagnosticsRepository {
    private val summary = MutableStateFlow(UsageSummary())

    override fun observeUsage(): Flow<List<UsageEntry>> = MutableStateFlow(emptyList())
    override fun observeUsageSummary(): Flow<UsageSummary> = summary
    override fun observeRecentLogs(limit: Int): Flow<List<AiRequestLogEntry>> =
        MutableStateFlow(emptyList())

    override suspend fun insertUsage(entry: UsageEntry) {}
    override suspend fun insertLog(log: AiRequestLogEntry) {}
    override suspend fun clearLogs() {}
}

private class FakeApiKeyStore : ApiKeyStore {
    val keys = mutableMapOf<String, String>()

    override suspend fun getApiKey(profileId: String): String? = keys[profileId]
    override suspend fun setApiKey(profileId: String, apiKey: String) {
        keys[profileId] = apiKey
    }
    override suspend fun deleteApiKey(profileId: String) {
        keys.remove(profileId)
    }
    override suspend fun hasApiKey(profileId: String): Boolean = keys.containsKey(profileId)
    override suspend fun configuredProfileIds(candidateProfileIds: Collection<String>): Set<String> =
        candidateProfileIds.filter { keys.containsKey(it) }.toSet()
}

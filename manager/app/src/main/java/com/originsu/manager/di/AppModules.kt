package com.originsu.manager.di

import coil.ImageLoader
import com.originsu.manager.BuildConfig
import com.originsu.manager.data.AppSettingsRepository
import com.originsu.manager.data.application.ApplicationControlRepository
import com.originsu.manager.data.application.DynamicManagerRepository
import com.originsu.manager.data.download.DownloadRepository
import com.originsu.manager.data.file.ModuleFileRepository
import com.originsu.manager.data.flash.FlashRepository
import com.originsu.manager.data.kernel.KernelRepository
import com.originsu.manager.data.kernel.UmountRepository
import com.originsu.manager.data.logging.BugreportRepository
import com.originsu.manager.data.logging.SulogRepository
import com.originsu.manager.data.module.ModuleActionRepository
import com.originsu.manager.data.module.ModuleCatalogRepository
import com.originsu.manager.data.module.ModulePreferencesRepository
import com.originsu.manager.data.module.ModuleRepository
import com.originsu.manager.data.network.NetworkRequestRepository
import com.originsu.manager.data.network.NetworkStatusRepository
import com.originsu.manager.data.network.WebResourceRepository
import com.originsu.manager.data.packageinfo.AppIconDataSource
import com.originsu.manager.data.packageinfo.InstalledPackageCache
import com.originsu.manager.data.packageinfo.InstalledPackageRepository
import com.originsu.manager.data.packageinfo.RootServiceRepository
import com.originsu.manager.data.packageinfo.SuperUserRepository
import com.originsu.manager.data.profile.ProfileRepository
import com.originsu.manager.data.profile.ProfileTemplateRepository
import com.originsu.manager.data.settings.LocaleHelper
import com.originsu.manager.data.settings.LocaleRepository
import com.originsu.manager.data.settings.SettingsPlatformRepository
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.data.shell.ShortcutRepository
import com.originsu.manager.data.startup.ApplicationInitializationRepository
import com.originsu.manager.data.startup.StartupRepository
import com.originsu.manager.data.susfs.SuSFSConfigHelper
import com.originsu.manager.data.susfs.SuSFSRepository
import com.originsu.manager.data.system.HomeRuntimeRepository
import com.originsu.manager.data.system.HomeStateRepository
import com.originsu.manager.data.text.HanziToPinyin
import com.originsu.manager.data.theme.MonetCompatColorSource
import com.originsu.manager.data.theme.ThemeRepository
import com.originsu.manager.data.update.ManagerUpdateRepository
import com.originsu.manager.data.webui.WebUiRepository
import com.originsu.manager.domain.text.TextTransliterator
import com.originsu.manager.domain.usecase.AddUmountPathUseCase
import com.originsu.manager.domain.usecase.ApplyLanguageUseCase
import com.originsu.manager.domain.usecase.BackupAllowlistUseCase
import com.originsu.manager.domain.usecase.CalculateInstalledModuleSizeUseCase
import com.originsu.manager.domain.usecase.CheckFlashModuleMountUseCase
import com.originsu.manager.domain.usecase.CheckManagerUpdateUseCase
import com.originsu.manager.domain.usecase.CleanSulogUseCase
import com.originsu.manager.domain.usecase.ClearDynamicManagerUseCase
import com.originsu.manager.domain.usecase.ConfigureSuLogUseCase
import com.originsu.manager.domain.usecase.ControlAppUseCase
import com.originsu.manager.domain.usecase.DeleteProfileTemplateUseCase
import com.originsu.manager.domain.usecase.EnableSulogUseCase
import com.originsu.manager.domain.usecase.EnqueueDownloadUseCase
import com.originsu.manager.domain.usecase.EnqueueManagerUpdateUseCase
import com.originsu.manager.domain.usecase.EnsureManagerInstalledUseCase
import com.originsu.manager.domain.usecase.ExecuteFlashOperationUseCase
import com.originsu.manager.domain.usecase.ExecuteModuleActionUseCase
import com.originsu.manager.domain.usecase.ExportProfileTemplatesUseCase
import com.originsu.manager.domain.usecase.ExtractModuleIdUseCase
import com.originsu.manager.domain.usecase.ExtractModuleNameUseCase
import com.originsu.manager.domain.usecase.FetchRemoteTextUseCase
import com.originsu.manager.domain.usecase.GenerateBugreportUseCase
import com.originsu.manager.domain.usecase.GetAppProfileUseCase
import com.originsu.manager.domain.usecase.GetAppSepolicyUseCase
import com.originsu.manager.domain.usecase.GetBooleanPreferenceUseCase
import com.originsu.manager.domain.usecase.GetCatalogModuleUseCase
import com.originsu.manager.domain.usecase.GetDefaultUmountModulesUseCase
import com.originsu.manager.domain.usecase.GetHomeBasicInfoUseCase
import com.originsu.manager.domain.usecase.GetInstallEnvironmentUseCase
import com.originsu.manager.domain.usecase.GetKernelFeatureSettingsUseCase
import com.originsu.manager.domain.usecase.GetKernelStatusUseCase
import com.originsu.manager.domain.usecase.GetManagerRuntimeInfoUseCase
import com.originsu.manager.domain.usecase.GetPlatformFeatureStatusUseCase
import com.originsu.manager.domain.usecase.GetProfileTemplateUseCase
import com.originsu.manager.domain.usecase.GetStringPreferenceUseCase
import com.originsu.manager.domain.usecase.GetStringSetPreferenceUseCase
import com.originsu.manager.domain.usecase.GetSuSFSStatusUseCase
import com.originsu.manager.domain.usecase.GetSuperUserAppGroupUseCase
import com.originsu.manager.domain.usecase.ImportAllowlistUseCase
import com.originsu.manager.domain.usecase.ImportProfileTemplatesUseCase
import com.originsu.manager.domain.usecase.InitializeApplicationUseCase
import com.originsu.manager.domain.usecase.IsLateLoadModeUseCase
import com.originsu.manager.domain.usecase.IsModuleUriAccessibleUseCase
import com.originsu.manager.domain.usecase.IsNetworkAvailableUseCase
import com.originsu.manager.domain.usecase.IsSystemLanguageSettingsUseCase
import com.originsu.manager.domain.usecase.LaunchSystemLanguageSettingsUseCase
import com.originsu.manager.domain.usecase.LoadSettingsPlatformUseCase
import com.originsu.manager.domain.usecase.ObserveCatalogModulesUseCase
import com.originsu.manager.domain.usecase.ObserveDownloadUseCase
import com.originsu.manager.domain.usecase.ObserveDynamicManagerStateUseCase
import com.originsu.manager.domain.usecase.ObserveInstalledModulesUseCase
import com.originsu.manager.domain.usecase.ObserveKernelFlashUseCase
import com.originsu.manager.domain.usecase.ObserveModuleCatalogOfflineUseCase
import com.originsu.manager.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import com.originsu.manager.domain.usecase.ObserveProfileTemplateOfflineUseCase
import com.originsu.manager.domain.usecase.ObserveProfileTemplateRefreshingUseCase
import com.originsu.manager.domain.usecase.ObserveProfileTemplatesUseCase
import com.originsu.manager.domain.usecase.ObserveStartupStateUseCase
import com.originsu.manager.domain.usecase.ObserveSulogStateUseCase
import com.originsu.manager.domain.usecase.ObserveSuperUserStateUseCase
import com.originsu.manager.domain.usecase.ObserveUmountStateUseCase
import com.originsu.manager.domain.usecase.RebootUseCase
import com.originsu.manager.domain.usecase.RefreshDynamicManagerUseCase
import com.originsu.manager.domain.usecase.RefreshInstalledModulesUseCase
import com.originsu.manager.domain.usecase.RefreshModuleCatalogUseCase
import com.originsu.manager.domain.usecase.RefreshProfileTemplatesUseCase
import com.originsu.manager.domain.usecase.RefreshSulogUseCase
import com.originsu.manager.domain.usecase.RefreshSuperUsersUseCase
import com.originsu.manager.domain.usecase.RefreshUmountPathsUseCase
import com.originsu.manager.domain.usecase.RemovePreferenceUseCase
import com.originsu.manager.domain.usecase.RemoveUmountPathUseCase
import com.originsu.manager.domain.usecase.SaveModuleActionLogUseCase
import com.originsu.manager.domain.usecase.SaveProfileTemplateUseCase
import com.originsu.manager.domain.usecase.SelectDynamicManagerUseCase
import com.originsu.manager.domain.usecase.SetAppProfileUseCase
import com.originsu.manager.domain.usecase.SetAppSepolicyUseCase
import com.originsu.manager.domain.usecase.SetBooleanPreferenceUseCase
import com.originsu.manager.domain.usecase.SetDefaultUmountModulesUseCase
import com.originsu.manager.domain.usecase.SetKernelUmountEnabledUseCase
import com.originsu.manager.domain.usecase.SetManualDynamicManagerUseCase
import com.originsu.manager.domain.usecase.SetModuleEnabledUseCase
import com.originsu.manager.domain.usecase.SetModuleRemovedUseCase
import com.originsu.manager.domain.usecase.SetSelinuxHideEnabledUseCase
import com.originsu.manager.domain.usecase.SetStringPreferenceUseCase
import com.originsu.manager.domain.usecase.SetStringSetPreferenceUseCase
import com.originsu.manager.domain.usecase.SetSuEnabledUseCase
import com.originsu.manager.domain.usecase.StartKernelFlashUseCase
import com.originsu.manager.domain.usecase.SuSFSConfigUseCase
import com.originsu.manager.domain.usecase.TakeModuleUriPermissionUseCase
import com.originsu.manager.domain.usecase.TransliterateTextUseCase
import com.originsu.manager.domain.usecase.UpdateAppearanceUseCase
import com.originsu.manager.domain.usecase.UpdateCachedModuleEnabledUseCase
import com.originsu.manager.domain.usecase.UpdatePlatformSettingUseCase
import com.originsu.manager.domain.usecase.ValidateSepolicyUseCase
import com.originsu.manager.ui.activity.util.ThemeUtils
import com.originsu.manager.ui.component.ZipFileDetector
import com.originsu.manager.ui.theme.BackgroundManager
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.util.module.Shortcut
import com.originsu.manager.ui.viewmodel.AppProfileViewModel
import com.originsu.manager.ui.viewmodel.DynamicManagerViewModel
import com.originsu.manager.ui.viewmodel.ExecuteModuleActionViewModel
import com.originsu.manager.ui.viewmodel.FlashViewModel
import com.originsu.manager.ui.viewmodel.HomeViewModel
import com.originsu.manager.ui.viewmodel.InstallViewModel
import com.originsu.manager.ui.viewmodel.KernelFlashViewModel
import com.originsu.manager.ui.viewmodel.MainIntentViewModel
import com.originsu.manager.ui.viewmodel.ModuleDetailViewModel
import com.originsu.manager.ui.viewmodel.ModuleRepoViewModel
import com.originsu.manager.ui.viewmodel.ModuleViewModel
import com.originsu.manager.ui.viewmodel.SettingsViewModel
import com.originsu.manager.ui.viewmodel.SuSFSViewModel
import com.originsu.manager.ui.viewmodel.SulogViewModel
import com.originsu.manager.ui.viewmodel.SuperUserViewModel
import com.originsu.manager.ui.viewmodel.TemplateEditorViewModel
import com.originsu.manager.ui.viewmodel.TemplateViewModel
import com.originsu.manager.ui.viewmodel.UmountManagerScreenViewModel
import com.originsu.manager.ui.webui.MonetColorsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

val applicationScopeQualifier = named("applicationScope")

val coreModule = module {
    single<CoroutineScope>(applicationScopeQualifier) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    single {
        OkHttpClient.Builder()
            .cache(Cache(File(androidApplication().cacheDir, "okhttp"), 10L * 1024L * 1024L))
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "OriginSU/${BuildConfig.VERSION_CODE}")
                        .header("Accept-Language", Locale.getDefault().toLanguageTag())
                        .build()
                )
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    single {
        val application = androidApplication()
        val iconSize = application.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        ImageLoader.Builder(application)
            .components {
                add(AppIconKeyer())
                add(AppIconFetcher.Factory(iconSize, false, application))
            }
            .build()
    }
}

val repositoryModule = module {
    single { KsuCliRepository(androidApplication()) }
    singleOf(::InstalledPackageCache)
    singleOf(::AppIconDataSource)
    singleOf(::RootServiceRepository)
    singleOf(::InstalledPackageRepository)
    single {
        SuperUserRepository(
            application = get(),
            cache = get(),
            installedPackageRepository = get(),
            profileRepository = get(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    single {
        AppSettingsRepository(
            context = androidApplication(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    singleOf(::StartupRepository)
    single {
        ApplicationInitializationRepository(
            application = get(),
            imageLoader = get(),
            applicationScope = get(applicationScopeQualifier),
            flashRepository = get(),
            ksuCliRepository = get(),
            monetCompatColorSource = get(),
        )
    }
    singleOf(::ManagerUpdateRepository)
    singleOf(::ApplicationControlRepository)
    singleOf(::DownloadRepository)
    single { FlashRepository(get(), get(applicationScopeQualifier), get(), get(), get()) }
    singleOf(::KernelRepository)
    singleOf(::HomeRuntimeRepository)
    singleOf(::HomeStateRepository)
    singleOf(::NetworkStatusRepository)
    singleOf(::NetworkRequestRepository)
    singleOf(::DynamicManagerRepository)
    singleOf(::SulogRepository)
    singleOf(::BugreportRepository)
    singleOf(::UmountRepository)
    singleOf(::ModuleCatalogRepository)
    singleOf(::ModuleRepository)
    singleOf(::ModulePreferencesRepository)
    singleOf(::ModuleActionRepository)
    singleOf(::WebResourceRepository)
    singleOf(::WebUiRepository)
    singleOf(::ModuleFileRepository)
    singleOf(::ProfileRepository)
    singleOf(::ProfileTemplateRepository)
    singleOf(::SuSFSConfigHelper)
    singleOf(::SuSFSRepository)
    singleOf(::MonetCompatColorSource)
    singleOf(::ThemeRepository)
    single {
        val themeRepository = get<ThemeRepository>()
        ThemeConfig(themeRepository::defaultSeedColor)
    }
    singleOf(::CardConfig)
    singleOf(::BackgroundManager)
    singleOf(::ThemeUtils)
    singleOf(::LocaleHelper)
    singleOf(::LocaleRepository)
    singleOf(::SettingsPlatformRepository)
    singleOf(::ShortcutRepository)
    singleOf(::Shortcut)
    singleOf(::MonetColorsProvider)
    singleOf(::ZipFileDetector)
    single { HanziToPinyin.create() } bind TextTransliterator::class
}

val useCaseModule = module {
    factoryOf(::InitializeApplicationUseCase)
    factoryOf(::GetHomeBasicInfoUseCase)
    factoryOf(::IsNetworkAvailableUseCase)
    factoryOf(::LoadSettingsPlatformUseCase)
    factoryOf(::UpdateAppearanceUseCase)
    factoryOf(::UpdatePlatformSettingUseCase)
    factoryOf(::GetPlatformFeatureStatusUseCase)
    factoryOf(::CheckManagerUpdateUseCase)
    factoryOf(::EnsureManagerInstalledUseCase)
    factoryOf(::RebootUseCase)
    factoryOf(::EnqueueDownloadUseCase)
    factoryOf(::EnqueueManagerUpdateUseCase)
    factoryOf(::ObserveDownloadUseCase)
    factoryOf(::GetKernelStatusUseCase)
    factoryOf(::GetInstallEnvironmentUseCase)
    factoryOf(::ExecuteFlashOperationUseCase)
    factoryOf(::CheckFlashModuleMountUseCase)
    factoryOf(::GetManagerRuntimeInfoUseCase)
    factoryOf(::GetKernelFeatureSettingsUseCase)
    factoryOf(::SetSuEnabledUseCase)
    factoryOf(::SetKernelUmountEnabledUseCase)
    factoryOf(::ConfigureSuLogUseCase)
    factoryOf(::SetSelinuxHideEnabledUseCase)
    factoryOf(::SetDefaultUmountModulesUseCase)
    factoryOf(::IsLateLoadModeUseCase)
    factoryOf(::GetAppProfileUseCase)
    factoryOf(::SetAppProfileUseCase)
    factoryOf(::GetAppSepolicyUseCase)
    factoryOf(::SetAppSepolicyUseCase)
    factoryOf(::ControlAppUseCase)
    factoryOf(::ValidateSepolicyUseCase)
    factoryOf(::GetDefaultUmountModulesUseCase)
    factoryOf(::GetSuSFSStatusUseCase)
    factoryOf(::SuSFSConfigUseCase)
    factoryOf(::ApplyLanguageUseCase)
    factoryOf(::IsSystemLanguageSettingsUseCase)
    factoryOf(::LaunchSystemLanguageSettingsUseCase)
    factoryOf(::GenerateBugreportUseCase)
    factoryOf(::ObserveStartupStateUseCase)
    factoryOf(::GetSuperUserAppGroupUseCase)
    factoryOf(::ObserveCatalogModulesUseCase)
    factoryOf(::ObserveModuleCatalogRefreshingUseCase)
    factoryOf(::ObserveModuleCatalogOfflineUseCase)
    factoryOf(::RefreshModuleCatalogUseCase)
    factoryOf(::GetCatalogModuleUseCase)
    factoryOf(::ObserveProfileTemplatesUseCase)
    factoryOf(::ObserveProfileTemplateRefreshingUseCase)
    factoryOf(::ObserveProfileTemplateOfflineUseCase)
    factoryOf(::RefreshProfileTemplatesUseCase)
    factoryOf(::GetProfileTemplateUseCase)
    factoryOf(::SaveProfileTemplateUseCase)
    factoryOf(::DeleteProfileTemplateUseCase)
    factoryOf(::ImportProfileTemplatesUseCase)
    factoryOf(::ExportProfileTemplatesUseCase)
    factoryOf(::GetBooleanPreferenceUseCase)
    factoryOf(::SetBooleanPreferenceUseCase)
    factoryOf(::GetStringPreferenceUseCase)
    factoryOf(::SetStringPreferenceUseCase)
    factoryOf(::GetStringSetPreferenceUseCase)
    factoryOf(::SetStringSetPreferenceUseCase)
    factoryOf(::ObserveDynamicManagerStateUseCase)
    factoryOf(::RefreshDynamicManagerUseCase)
    factoryOf(::SelectDynamicManagerUseCase)
    factoryOf(::SetManualDynamicManagerUseCase)
    factoryOf(::ClearDynamicManagerUseCase)
    factoryOf(::ObserveSulogStateUseCase)
    factoryOf(::RefreshSulogUseCase)
    factoryOf(::EnableSulogUseCase)
    factoryOf(::CleanSulogUseCase)
    factoryOf(::ObserveUmountStateUseCase)
    factoryOf(::RefreshUmountPathsUseCase)
    factoryOf(::AddUmountPathUseCase)
    factoryOf(::RemoveUmountPathUseCase)
    factoryOf(::ObserveKernelFlashUseCase)
    factoryOf(::StartKernelFlashUseCase)
    factoryOf(::RemovePreferenceUseCase)
    factoryOf(::GetLongPreferenceUseCase)
    factoryOf(::SetLongPreferenceUseCase)
    factoryOf(::ObserveSuperUserStateUseCase)
    factoryOf(::RefreshSuperUsersUseCase)
    factoryOf(::BackupAllowlistUseCase)
    factoryOf(::ImportAllowlistUseCase)
    factoryOf(::FetchRemoteTextUseCase)
    factoryOf(::IsModuleUriAccessibleUseCase)
    factoryOf(::TakeModuleUriPermissionUseCase)
    factoryOf(::ExtractModuleNameUseCase)
    factoryOf(::ExtractModuleIdUseCase)
    factoryOf(::ObserveInstalledModulesUseCase)
    factoryOf(::RefreshInstalledModulesUseCase)
    factoryOf(::CalculateInstalledModuleSizeUseCase)
    factoryOf(::UpdateCachedModuleEnabledUseCase)
    factoryOf(::ExecuteModuleActionUseCase)
    factoryOf(::SaveModuleActionLogUseCase)
    factoryOf(::SetModuleEnabledUseCase)
    factoryOf(::SetModuleRemovedUseCase)
    factoryOf(::TransliterateTextUseCase)
}

val viewModelModule = module {
    viewModel { parameters ->
        AppProfileViewModel(
            uid = parameters[0],
            packageName = parameters[1],
            getAppGroup = get(),
            getProfile = get(),
            getDefaultUmountModules = get(),
            setProfile = get(),
            getSepolicy = get(),
            setSepolicy = get(),
            controlApp = get(),
            validateSepolicy = get(),
            getBooleanPreference = get(),
        )
    }
    viewModelOf(::HomeViewModel)
    viewModelOf(::InstallViewModel)
    viewModelOf(::MainIntentViewModel)
    viewModelOf(::KernelFlashViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ModuleViewModel)
    viewModelOf(::SuperUserViewModel)
    viewModelOf(::SuSFSViewModel)
    viewModelOf(::ModuleRepoViewModel)
    viewModel { parameters -> ModuleDetailViewModel(parameters[0], get()) }
    viewModelOf(::TemplateViewModel)
    viewModel { parameters ->
        TemplateEditorViewModel(
            templateId = parameters[0],
            readOnly = parameters[1],
            isCreation = parameters[2],
            getTemplate = get(),
            saveTemplate = get(),
            deleteTemplate = get(),
        )
    }
    viewModelOf(::SulogViewModel)
    viewModelOf(::DynamicManagerViewModel)
    viewModelOf(::FlashViewModel)
    viewModelOf(::UmountManagerScreenViewModel)
    viewModel { parameters ->
        ExecuteModuleActionViewModel(
            moduleId = parameters[0],
            executeModuleAction = get(),
            saveModuleActionLog = get(),
        )
    }
}

val appModules = listOf(coreModule, repositoryModule, useCaseModule, viewModelModule)

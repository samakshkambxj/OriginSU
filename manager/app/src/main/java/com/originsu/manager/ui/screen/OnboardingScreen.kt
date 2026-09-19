package com.originsu.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.originsu.manager.R
import com.originsu.manager.ui.navigation.LocalNavigator
import com.originsu.manager.ui.navigation.Route
import com.originsu.manager.ui.viewmodel.HomeViewModel
import com.originsu.manager.ui.viewmodel.MainIntentViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val mainIntentViewModel = koinViewModel<MainIntentViewModel>()
    val mainIntentState by mainIntentViewModel.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (step) {
                0 -> OnboardingStep(
                    icon = Icons.TwoTone.Info,
                    title = stringResource(R.string.onboarding_title_welcome),
                    description = stringResource(R.string.onboarding_desc_welcome),
                )

                1 -> {
                    val rooted = mainIntentState.rootAvailable
                    OnboardingStep(
                        icon = if (rooted) Icons.TwoTone.CheckCircle else Icons.TwoTone.Warning,
                        title = stringResource(R.string.onboarding_title_status),
                        description = stringResource(
                            if (rooted) {
                                R.string.onboarding_desc_rooted
                            } else {
                                R.string.onboarding_desc_noroot
                            }
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    RootMethodLines()
                    Spacer(modifier = Modifier.height(16.dp))
                    if (rooted) {
                        Button(
                            onClick = {
                                onDone()
                                navigator.push(Route.Module)
                            }
                        ) {
                            Text(stringResource(R.string.onboarding_action_modules))
                        }
                    } else {
                        Button(
                            onClick = {
                                onDone()
                                navigator.push(Route.Install(null))
                            }
                        ) {
                            Text(stringResource(R.string.onboarding_action_install))
                        }
                    }
                }

                else -> OnboardingStep(
                    icon = Icons.TwoTone.CheckCircle,
                    title = stringResource(R.string.onboarding_title_ready),
                    description = stringResource(R.string.onboarding_desc_ready),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    TextButton(onClick = { step-- }) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                } else {
                    TextButton(onClick = onDone) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }
                if (step < 2) {
                    Button(onClick = { step++ }) {
                        Text(stringResource(R.string.onboarding_next))
                    }
                } else {
                    Button(onClick = onDone) {
                        Text(stringResource(R.string.onboarding_get_started))
                    }
                }
            }
        }
    }
}

@Composable
private fun RootMethodLines() {
    val homeViewModel = koinViewModel<HomeViewModel>()
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val status = homeUiState.systemStatus
    val isLkm = status.lkmMode == true
    Text(
        text = stringResource(
            if (isLkm) {
                R.string.onboarding_method_lkm
            } else {
                R.string.onboarding_method_builtin
            }
        ),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!isLkm) {
        Text(
            text = stringResource(
                R.string.onboarding_kernel,
                homeUiState.systemInfo.kernelRelease.ifEmpty { "—" }
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(64.dp)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

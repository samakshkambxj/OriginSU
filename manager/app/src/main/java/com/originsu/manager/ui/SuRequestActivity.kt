package com.originsu.manager.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.originsu.manager.R
import com.originsu.manager.data.su.SuRequestRepository
import com.originsu.manager.ui.theme.KernelSUTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SuRequestActivity : FragmentActivity() {
    private val repository: SuRequestRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Magisk-style: visible over lock screen, turns screen on.
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1L)
        val uid = intent.getIntExtra(EXTRA_UID, -1)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { packageName }

        if (requestId < 0 || uid < 0) {
            finish()
            return
        }

        setContent {
            KernelSUTheme {
                var remember by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.padding(24.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = getString(R.string.su_request_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = getString(R.string.su_request_message, label, uid),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (packageName.isNotBlank()) {
                            Text(
                                text = packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = remember,
                                onCheckedChange = { remember = it },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = getString(R.string.su_request_remember))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(
                                onClick = {
                                    answer(requestId, uid, packageName, allow = false, remember)
                                },
                            ) {
                                Text(text = getString(R.string.su_request_deny))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    answer(requestId, uid, packageName, allow = true, remember)
                                },
                            ) {
                                Text(text = getString(R.string.su_request_allow))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun answer(requestId: Long, uid: Int, packageName: String, allow: Boolean, remember: Boolean) {
        lifecycleScope.launch {
            val ok = repository.answer(requestId, uid, packageName, allow, remember)
            if (!ok && allow) {
                Toast.makeText(
                    this@SuRequestActivity,
                    getString(R.string.grant_root_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_REQUEST_ID = "extra_request_id"
        const val EXTRA_UID = "extra_uid"
        const val EXTRA_PID = "extra_pid"
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_LABEL = "extra_label"
    }
}

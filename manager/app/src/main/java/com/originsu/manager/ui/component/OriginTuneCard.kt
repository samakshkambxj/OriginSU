package com.originsu.manager.ui.component

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.originsu.manager.R

/**
 * Hero card for OriginTune, styled after [WorkingStatusCard] but with the
 * OriginSU flower mark instead of the check icon. Tapping opens OriginTune.
 *
 * When [locked] is true (no root / driver not installed) the feature preview
 * is blurred into a teaser and tapping should route to the installer instead.
 */
@Composable
fun OriginTuneCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!locked) {
                            Modifier
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(14.dp)
                        } else {
                            Modifier.alpha(0.35f)
                        }
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(27.dp, 31.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(
                        modifier = Modifier.size(110.dp),
                        painter = painterResource(R.drawable.topbar_leaf),
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 14.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.kernel_tuning),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(id = R.string.kernel_tuning_tagline),
                        fontSize = 15.sp,
                    )
                }
            }
            if (locked) {
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.kernel_tuning_locked_title),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(id = R.string.kernel_tuning_locked_summary),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

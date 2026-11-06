package com.mobile.enerjeep.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.navigation.AppNavigator
import com.mobile.enerjeep.navigation.Screen
import com.mobile.enerjeep.ui.theme.FieldBorderColor
import com.mobile.enerjeep.ui.theme.ErrorRedLight
import com.mobile.enerjeep.ui.theme.TextSecondary
import com.mobile.enerjeep.ui.theme.WarningOrangeLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementScreen(
    appNavigator: AppNavigator,
    viewModel: AccountManagementViewModel = hiltViewModel(),
    superadminViewModel: SuperadminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var accountPendingDeletion by remember {
        mutableStateOf<AccountItem?>(null)
    }

    /*
     * Superadmin toolbox: the emergency list, the accountability log
     * and the settings open INSIDE this screen (no new module needed).
     * 0 = Mga Account, 1 = Emergency List, 2 = Talaan, 3 = Settings
     */
    var superadminView by remember { mutableStateOf(0) }
    val isSuperadmin = viewModel.isSuperadmin()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        appNavigator.navigateBack()
                    }
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "Pamamahala ng Account",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        appNavigator.navigateTo(Screen.CreateAccount)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1B49B5)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(
                        horizontal = 16.dp
                    )
                ) {
                    Text(
                        "+ Bago",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        },
        containerColor = Color(0xFFF9FAFB)
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            /*
             * Superadmin-only toolbox button. An admin never sees
             * this - that is how the higher level shows itself.
             * (Ang Settings at Talaan ay nasa Profile tab na -
             * ilalim ng "Seguridad".)
             */
            if (isSuperadmin) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuperadminTabButton(
                        modifier = Modifier.weight(1f),
                        text = "Emergency",
                        selected = superadminView == 1
                    ) { superadminView = 1 }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            when {
                isSuperadmin && superadminView == 1 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { superadminView = 0 }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Bumalik"
                            )
                        }
                        Text("Bumalik sa Mga Account", fontSize = 12.sp)
                    }
                    EmergencyListView(superadminViewModel)
                }

                else -> {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = {
                    viewModel.updateSearchQuery(it)
                },
                placeholder = {
                    Text(
                        "Hanapin ayon sa pangalan o numero",
                        color = TextSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Hanapin",
                        tint = TextSecondary
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = FieldBorderColor,
                    focusedBorderColor = Color(0xFF1B49B5),
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                ),
                singleLine = true
            )

            uiState.errorMessage?.let { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = message,
                        color = Color.Red,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = viewModel::retryLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1B49B5)
                        )
                    ) {
                        Text("I-refresh ang listahan", maxLines = 1)
                    }
                }
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            if (
                !uiState.isLoading &&
                uiState.filteredAccounts.isEmpty()
            ) {
                Text(
                    text = if (uiState.searchQuery.isBlank()) {
                        "Wala pang account. Pindutin ang \"+ Bago\" para gumawa."
                    } else {
                        "Walang nahanap na account."
                    },
                    color = TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.filteredAccounts.forEach { account ->

                    val canDeleteAccount =
                        !viewModel.isCurrentAccount(account) &&
                                account.role != "Superadmin" &&
                                (isSuperadmin || account.role == "Driver")

                    AccountCard(
                        account = account,
                        onDelete = {
                            accountPendingDeletion = account
                        },
                        canDelete = canDeleteAccount,
                        isBusy = account.id in uiState.busyAccountIds
                    )
                }
            }
                }
            }
        }
    }

    accountPendingDeletion?.let { account ->
        AlertDialog(
            onDismissRequest = {
                accountPendingDeletion = null
            },
            title = {
                Text("Burahin ang account?")
            },
            text = {
                Text(
                    "Aalisin si ${account.name} sa mga account. " +
                            "Pananatilihin ang mga nakaraang tala ng dispatch at kita."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(account.id)
                        accountPendingDeletion = null
                    }
                ) {
                    Text(
                        "Burahin",
                        color = Color.Red
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        accountPendingDeletion = null
                    }
                ) {
                    Text("Kanselahin")
                }
            }
        )
    }
}


@Composable
fun AccountCard(
    account: AccountItem,
    onDelete: () -> Unit,
    canDelete: Boolean,
    isBusy: Boolean = false
) {
    val roleBackground = when {
        account.role == "Superadmin" -> Color(0xFF6D28D9)
        account.role.startsWith("Admin") -> Color(0xFF1B49B5)
        else -> WarningOrangeLight
    }
    val roleForeground =
        if (account.role == "Driver") Color(0xFFD97706) else Color.White

    val statusBackground =
        if (account.isActive) Color(0xFFDCFCE7) else Color(0xFFF3F4F6)
    val statusForeground =
        if (account.isActive) Color(0xFF166534) else TextSecondary
    val statusLabel = if (account.isActive) "Aktibo" else "Hindi Aktibo"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            /*
             * Keep the name, role, account status, and delete action on
             * one header row to match the account cards in the manuscript.
             * The name is the flexible item, so long names truncate rather
             * than pushing the status and delete controls off-screen.
             */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Keep the driver's full name and role together as one group.
                // Long names can wrap to a second line instead of being forced
                // into a narrow single-line slot.
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = account.name,
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis
                    )

                    Surface(
                        color = roleBackground,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(22.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = account.role,
                                color = roleForeground,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Keep status and delete at the trailing edge, without
                // inserting a spacer between the name and the role badge.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = statusBackground,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(22.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                color = statusForeground,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (canDelete) {
                        IconButton(
                            onClick = onDelete,
                            enabled = !isBusy,
                            modifier = Modifier
                                .size(38.dp)
                                .border(
                                    width = 1.dp,
                                    color = ErrorRedLight,
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Burahin ang account ni ${account.name}",
                                tint = Color.Red,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = account.phone,
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Ginawa noong ${account.dateCreated}",
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

    @Composable
    fun SuperadminTabButton(
        modifier: Modifier = Modifier,
        text: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        Surface(
            modifier = modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            color = if (selected) Color(0xFF1B49B5) else Color(0xFFEAF1FF),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                color = Color(0xFF1B49B5)
            ),
            shadowElevation = 1.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Color.White else Color(0xFF1B49B5)
                )
            }
        }
    }

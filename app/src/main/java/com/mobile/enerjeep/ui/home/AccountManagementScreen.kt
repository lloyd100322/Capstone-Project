package com.mobile.enerjeep.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.navigation.AppNavigator
import com.mobile.enerjeep.navigation.Screen

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
                    shape = RoundedCornerShape(24.dp),
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
                .padding(horizontal = 16.dp)
        ) {
            /*
             * Superadmin-only toolbox buttons. An admin never sees
             * these - that is how the higher level shows itself.
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
                    SuperadminTabButton(
                        modifier = Modifier.weight(1f),
                        text = "Talaan",
                        selected = superadminView == 2
                    ) { superadminView = 2 }
                    SuperadminTabButton(
                        modifier = Modifier.weight(1f),
                        text = "Settings",
                        selected = superadminView == 3
                    ) { superadminView = 3 }
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

                isSuperadmin && superadminView == 2 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { superadminView = 0 }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Bumalik"
                            )
                        }
                        Text("Bumalik sa Mga Account", fontSize = 12.sp)
                    }
                    ActivityLogView(superadminViewModel)
                }

                isSuperadmin && superadminView == 3 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { superadminView = 0 }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Bumalik"
                            )
                        }
                        Text("Bumalik sa Mga Account", fontSize = 12.sp)
                    }
                    SettingsView(superadminViewModel)
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
                        color = Color.Gray
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.Gray
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.LightGray,
                    focusedBorderColor = Color(0xFF1B49B5),
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                ),
                singleLine = true
            )

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = Color.Red,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
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
                        "Wala pang account."
                    } else {
                        "Walang nahanap na account."
                    },
                    color = Color.Gray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    items = uiState.filteredAccounts,
                    key = { it.id }
                ) { account ->
                    AccountCard(
                        account = account,
                        onToggleStatus = {
                            viewModel.toggleAccountStatus(account.id)
                        },
                        onDelete = {
                            accountPendingDeletion = account
                        },
                        onChangeRole = if (
                            isSuperadmin && !viewModel.isCurrentAccount(account)
                        ) {
                            {
                                viewModel.changeUserRole(
                                    account.id,
                                    if (account.role == "Driver") {
                                        "admin"
                                    } else {
                                        "driver"
                                    }
                                )
                            }
                        } else {
                            null
                        },
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
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit,
    onChangeRole: (() -> Unit)? = null,
    isBusy: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = account.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.width(8.dp))

                val roleBgColor =
                    when (account.role) {
                        "Superadmin" -> Color(0xFF6D28D9)
                        "Admin" -> Color(0xFF1B49B5)
                        else -> Color(0xFFFEF3C7)
                    }

                val roleTextColor =
                    if (account.role == "Driver") {
                        Color(0xFFD97706)
                    } else {
                        Color.White
                    }

                Surface(
                    color = roleBgColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(
                            horizontal = 10.dp
                        )
                    ) {
                        Text(
                            text = account.role,
                            color = roleTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = account.phone,
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Ginawa noong ${account.dateCreated}",
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            val statusBgColor =
                if (account.isActive) {
                    Color(0xFFDCFCE7)
                } else {
                    Color(0xFFF3F4F6)
                }

            val statusTextColor =
                if (account.isActive) {
                    Color(0xFF166534)
                } else {
                    Color.Gray
                }

            val statusText =
                if (account.isActive) {
                    "Aktibo"
                } else {
                    "Hindi Aktibo"
                }

            Surface(
                color = statusBgColor,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(
                        horizontal = 10.dp
                    )
                ) {
                    Text(
                        text = statusText,
                        color = statusTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onToggleStatus,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        Color.LightGray
                    )
                ) {
                    Text(
                        text = if (account.isActive) {
                            "Alisin"
                        } else {
                            "Ibalik"
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                /*
                 * Superadmin only: promote a driver to admin or demote
                 * an admin back to driver.
                 */
                if (onChangeRole != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onChangeRole,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            Color(0xFF1B49B5)
                        )
                    ) {
                        Text(
                            text = if (account.role == "Driver") {
                                "Gawing Admin"
                            } else {
                                "Gawing Driver"
                            },
                            color = Color(0xFF1B49B5),
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                IconButton(
                    onClick = onDelete,
                    enabled = !isBusy,
                    modifier = Modifier
                        .size(48.dp)
                        .border(
                            1.dp,
                            Color(0xFFFEE2E2),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red
                    )
                }
            }
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
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFF6D28D9) else Color.LightGray
        )
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = if (selected) Color(0xFF6D28D9) else Color.Black
        )
    }
}

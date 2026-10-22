package com.mobile.enerjeep.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.R

private val ProfileLightGray =
    Color(0xFFF8F9FA)

private val ProfileBlue =
    Color(0xFF194287)

private val ProfileBorder =
    Color(0xFFE5E7EB)

private val ProfileError =
    Color(0xFFB91C1C)

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {

    val uiState by
    viewModel.uiState
        .collectAsStateWithLifecycle()

    var currentView by
    remember {
        mutableStateOf("Main")
    }

    LaunchedEffect(uiState) {
        val state = uiState

        if (
            state is ProfileUiState.Error
        ) {
            // Keep the current screen visible.
            // The error is still displayed.
        }
    }

    Scaffold(
        containerColor = ProfileLightGray
    ) { innerPadding ->

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
        ) {

            when (val state = uiState) {

                ProfileUiState.Loading -> {

                    Box(
                        modifier =
                            Modifier.fillMaxSize(),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = ProfileBlue
                        )
                    }
                }

                is ProfileUiState.Error -> {

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.Center
                    ) {

                        Text(
                            text = state.message,
                            color = ProfileError,
                            textAlign = TextAlign.Center
                        )

                        Spacer(
                            modifier =
                                Modifier.height(16.dp)
                        )

                        Button(
                            onClick = {
                                viewModel.clearMessage()
                            },
                            colors =
                                ButtonDefaults
                                    .buttonColors(
                                        containerColor =
                                            ProfileBlue
                                    )
                        ) {
                            Text("Subukan muli")
                        }
                    }
                }

                is ProfileUiState.Success -> {

                    when (currentView) {

                        "Main" -> {

                            ProfileMainContent(
                                profile =
                                    state.profile,
                                updateMessage =
                                    state.updateMessage,
                                onEditClick = {
                                    currentView =
                                        "Edit"
                                    viewModel.clearMessage()
                                },
                                onSecurityClick = {
                                    currentView =
                                        "Security"
                                    viewModel.clearMessage()
                                },
                                onSignOutClick = {
                                    viewModel.signOut()
                                    onSignOut()
                                }
                            )
                        }

                        "Edit" -> {

                            EditProfileContent(
                                profile =
                                    state.profile,
                                onBack = {
                                    currentView =
                                        "Main"
                                },
                                onSave = {
                                        first,
                                        last,
                                        phone ->

                                    viewModel.updateProfile(
                                        first,
                                        last,
                                        phone
                                    )

                                    currentView =
                                        "Main"
                                }
                            )
                        }

                        "Security" -> {

                            SecurityContent(
                                onBack = {
                                    currentView =
                                        "Main"
                                },
                                onSave = {
                                        old,
                                        new,
                                        confirm ->

                                    viewModel
                                        .updatePassword(
                                            old,
                                            new,
                                            confirm
                                        ) {
                                            currentView =
                                                "Main"
                                        }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileMainContent(
    profile: ProfileData,
    updateMessage: String?,
    onEditClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onSignOutClick: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(24.dp)
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Image(
                painter =
                    painterResource(
                        id = R.drawable.app_icon
                    ),
                contentDescription =
                    "Enerjeep Logo",
                modifier =
                    Modifier.size(56.dp)
            )

            Spacer(
                modifier =
                    Modifier.width(12.dp)
            )

            Text(
                text = "Enerjeep",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(
            modifier =
                Modifier.height(32.dp)
        )

        Text(
            text = "Profile",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        if (!updateMessage.isNullOrBlank()) {

            Surface(
                color = Color(0xFFDCFCE7),
                shape = RoundedCornerShape(12.dp),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    text = updateMessage,
                    color = Color(0xFF166534),
                    modifier =
                        Modifier.padding(16.dp)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )
        }

        Card(
            modifier =
                Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Color.White
                ),
            shape =
                RoundedCornerShape(16.dp)
        ) {

            Row(
                modifier =
                    Modifier.padding(20.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier =
                        Modifier
                            .size(60.dp)
                            .background(
                                ProfileBlue,
                                CircleShape
                            ),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        text =
                            profile.initials,
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            20.sp
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(16.dp)
                )

                Column {

                    Text(
                        text =
                            profile.fullName,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            16.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    /*
                     * Phone number is already stored as +639...
                     * so do NOT prepend another +63 here.
                     */
                    Text(
                        text =
                            profile.phoneNumber,
                        color =
                            Color.Gray,
                        fontSize =
                            13.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Surface(
                        color =
                            ProfileBlue,
                        shape =
                            RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text =
                                profile.role,
                            color =
                                Color.White,
                            fontSize =
                                10.sp,
                            fontWeight =
                                FontWeight.Bold,
                            modifier =
                                Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 3.dp
                                )
                        )
                    }
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        Card(
            modifier =
                Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Color.White
                ),
            shape =
                RoundedCornerShape(16.dp)
        ) {

            Column {

                MenuRow(
                    icon =
                        Icons.Default.Person,
                    title =
                        "I-edit ang Profile",
                    subtitle =
                        "I-update ang iyong impormasyon",
                    onClick =
                        onEditClick
                )

                HorizontalDivider(
                    color =
                        ProfileBorder
                )

                MenuRow(
                    icon =
                        Icons.Default.Security,
                    title =
                        "Seguridad",
                    subtitle =
                        "Password at privacy",
                    onClick =
                        onSecurityClick
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        OutlinedButton(
            onClick =
                onSignOutClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            shape =
                RoundedCornerShape(28.dp)
        ) {

            Icon(
                Icons.Default.ExitToApp,
                contentDescription = null,
                tint = ProfileError
            )

            Spacer(
                modifier =
                    Modifier.width(8.dp)
            )

            Text(
                text = "Mag-sign Out",
                color = ProfileError,
                fontWeight =
                    FontWeight.Bold
            )
        }

        Spacer(
            modifier =
                Modifier.height(32.dp)
        )

        Text(
            text = "Enerjeep v1.0.0",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileContent(
    profile: ProfileData,
    onBack: () -> Unit,
    onSave: (
        String,
        String,
        String
    ) -> Unit
) {

    var firstName by remember(profile) {
        mutableStateOf(profile.firstName)
    }

    var lastName by remember(profile) {
        mutableStateOf(profile.lastName)
    }

    /*
     * The UI accepts the 10-digit local number while the database
     * stores +639...
     */
    var phone by remember(profile) {
        mutableStateOf(
            profile.phoneNumber
                .removePrefix("+63")
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
    ) {

        TopAppBar(
            title = {
                Text(
                    "I-edit ang Profile",
                    fontWeight =
                        FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Color.White
                ),
            shape =
                RoundedCornerShape(24.dp)
        ) {

            Column(
                modifier =
                    Modifier.padding(24.dp)
            ) {

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    OutlinedTextField(
                        value =
                            firstName,
                        onValueChange = {
                            firstName = it
                        },
                        modifier =
                            Modifier.weight(1f),
                        label = {
                            Text("Pangalan")
                        },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value =
                            lastName,
                        onValueChange = {
                            lastName = it
                        },
                        modifier =
                            Modifier.weight(1f),
                        label = {
                            Text("Apelyido")
                        },
                        singleLine = true
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        if (
                            it.length <= 10 &&
                            it.all { char ->
                                char.isDigit()
                            }
                        ) {
                            phone = it
                        }
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text("Telepono")
                    },
                    leadingIcon = {
                        Text("+63")
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Phone
                        ),
                    singleLine = true,
                    enabled = false
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        "Ang phone number ay naka-lock dahil ginagamit ito ng Firebase Phone Authentication.",
                    color =
                        Color.Gray,
                    fontSize =
                        12.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(32.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    OutlinedButton(
                        onClick =
                            onBack,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                    ) {
                        Text("Kanselahin")
                    }

                    Spacer(
                        modifier =
                            Modifier.width(16.dp)
                    )

                    Button(
                        onClick = {
                            onSave(
                                firstName,
                                lastName,
                                profile.phoneNumber
                            )
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(56.dp),
                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        ProfileBlue
                                )
                    ) {
                        Text("I-save")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityContent(
    onBack: () -> Unit,
    onSave: (
        String,
        String,
        String
    ) -> Unit
) {

    var oldPass by remember {
        mutableStateOf("")
    }

    var newPass by remember {
        mutableStateOf("")
    }

    var confirmPass by remember {
        mutableStateOf("")
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
    ) {

        TopAppBar(
            title = {
                Text(
                    "Seguridad",
                    fontWeight =
                        FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Color.White
                ),
            shape =
                RoundedCornerShape(24.dp)
        ) {

            Column(
                modifier =
                    Modifier.padding(24.dp)
            ) {

                PasswordTextField(
                    label = "Lumang Password",
                    value = oldPass,
                    onValueChange = {
                        oldPass = it
                    }
                )

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                PasswordTextField(
                    label = "Bagong Password",
                    value = newPass,
                    onValueChange = {
                        newPass = it
                    }
                )

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                PasswordTextField(
                    label = "Kumpirmahin",
                    value = confirmPass,
                    onValueChange = {
                        confirmPass = it
                    }
                )

                Spacer(
                    modifier =
                        Modifier.height(32.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    OutlinedButton(
                        onClick =
                            onBack,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                    ) {
                        Text("Kanselahin")
                    }

                    Spacer(
                        modifier =
                            Modifier.width(16.dp)
                    )

                    Button(
                        onClick = {
                            onSave(
                                oldPass,
                                newPass,
                                confirmPass
                            )
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(56.dp),
                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        ProfileBlue
                                )
                    ) {
                        Text("I-save")
                    }
                }
            }
        }
    }
}

@Composable
fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                }
                .padding(20.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ProfileBlue
        )

        Spacer(
            modifier =
                Modifier.width(16.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text = title,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.LightGray
        )
    }
}

@Composable
fun PasswordTextField(
    label: String,
    value: String,
    onValueChange: (
        String
    ) -> Unit
) {

    var passwordVisible by remember {
        mutableStateOf(false)
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            Modifier.fillMaxWidth(),
        label = {
            Text(label)
        },
        singleLine = true,
        visualTransformation =
            if (passwordVisible)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.Password
            ),
        trailingIcon = {

            IconButton(
                onClick = {
                    passwordVisible =
                        !passwordVisible
                }
            ) {
                Icon(
                    if (passwordVisible)
                        Icons.Default.Visibility
                    else
                        Icons.Default.VisibilityOff,
                    contentDescription = null
                )
            }
        }
    )
}
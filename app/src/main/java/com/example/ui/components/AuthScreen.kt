package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AuthManager
import com.example.data.local.AuthUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modern Liquid Glass Authentication Screen featuring fluid animated organic orbs,
 * iridescent chromatic borders, frosted glass refractions, and spring physics.
 */
@Composable
fun AuthScreen(
    onLoginSuccess: (AuthUser) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var isSignUpMode by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var isAccessGranted by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetEmailInput by remember { mutableStateOf("") }
    var isResetLoading by remember { mutableStateOf(false) }

    // Auto-dismiss banners
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(4200L)
            errorMessage = null
        }
    }
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(4200L)
            statusMessage = null
        }
    }

    // Continuous dynamic fluid animation for card's liquid chromatic edge
    val infiniteTransition = rememberInfiniteTransition(label = "auth_liquid_ambient")
    val liquidAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquid_angle"
    )

    // Breathing float animation for the logo badge
    val logoFloat by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_float"
    )

    val accessPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "access_pulse"
    )

    fun handleAuthSubmit() {
        focusManager.clearFocus()
        errorMessage = null
        statusMessage = null

        if (isSignUpMode) {
            if (nameInput.trim().isBlank()) {
                errorMessage = "Please enter your name"
                return
            }
            if (emailInput.trim().isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput.trim()).matches()) {
                errorMessage = "Please enter a valid email address"
                return
            }
            if (passwordInput.trim().length < 6) {
                errorMessage = "Password must be at least 6 characters"
                return
            }

            isLoading = true
            scope.launch {
                val result = AuthManager.signUp(nameInput, emailInput, passwordInput)
                isLoading = false
                result.fold(
                    onSuccess = { user ->
                        isAccessGranted = true
                        delay(1100L)
                        AuthManager.commitLogin(user)
                        onLoginSuccess(user)
                    },
                    onFailure = { err ->
                        errorMessage = err.message ?: "Sign up failed. Please try again."
                    }
                )
            }
        } else {
            if (emailInput.trim().isBlank()) {
                errorMessage = "Please enter your email address"
                return
            }
            if (passwordInput.trim().length < 6) {
                errorMessage = "Password must be at least 6 characters"
                return
            }

            isLoading = true
            scope.launch {
                val result = AuthManager.signIn(emailInput, passwordInput)
                isLoading = false
                result.fold(
                    onSuccess = { user ->
                        isAccessGranted = true
                        delay(1100L)
                        AuthManager.commitLogin(user)
                        onLoginSuccess(user)
                    },
                    onFailure = { err ->
                        errorMessage = err.message ?: "Incorrect email or password"
                    }
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // --- 1. Dynamic Liquid Aurora Background with Morphing Fluid Orbs & Particles ---
        LiquidGlassBackground(modifier = Modifier.fillMaxSize())

        // --- 2. Top Floating Glassmorphic Alert Banner ---
        AnimatedVisibility(
            visible = errorMessage != null || statusMessage != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(220)
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 20.dp, end = 20.dp)
        ) {
            val isSuccess = statusMessage != null
            val bannerColor = if (isSuccess) Color(0xDC0C271E) else Color(0xDC220E18)
            val borderColor = if (isSuccess) Color(0xFF10B981).copy(alpha = 0.7f) else Color(0xFFF43F5E).copy(alpha = 0.65f)
            val iconTint = if (isSuccess) Color(0xFF34D399) else Color(0xFFFB7185)
            val textColor = if (isSuccess) Color(0xFFD1FAE5) else Color(0xFFFECDD3)
            val displayText = statusMessage ?: errorMessage ?: ""

            Surface(
                color = bannerColor,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, borderColor),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { 
                        errorMessage = null 
                        statusMessage = null
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = displayText,
                        color = textColor,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- 3. Centered Liquid Glassmorphism Container with Dynamic Refractions ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // The Liquid Glass Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(30.dp),
                        ambientColor = Color(0x607C3AED),
                        spotColor = Color(0x9906B6D4)
                    )
                    .clip(RoundedCornerShape(30.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xDE0E1428),
                                Color(0xCE131A33),
                                Color(0xE50B1020)
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.sweepGradient(
                                listOf(
                                    Color(0x80A78BFA),
                                    Color(0x5006B6D4),
                                    Color(0x70EC4899),
                                    Color(0x403B82F6),
                                    Color(0x80A78BFA)
                                )
                            )
                        ),
                        shape = RoundedCornerShape(30.dp)
                    )
                    .animateContentSize(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioLowBouncy
                        )
                    )
            ) {
                // Top Specular Highlight Line (Mimicking light hitting convex liquid glass)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.TopCenter)
                ) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.55f),
                                Color(0xFF67E8F9).copy(alpha = 0.70f),
                                Color.White.copy(alpha = 0.55f),
                                Color.Transparent
                            )
                        )
                    )
                }

                // Inner content layout
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // --- App Logo: Floating Liquid Glass Emblem ---
                    Box(
                        modifier = Modifier
                            .graphicsLayer { translationY = logoFloat }
                            .size(62.dp)
                            .shadow(14.dp, RoundedCornerShape(18.dp), spotColor = Color(0xFFA78BFA))
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF8B5CF6),
                                        Color(0xFF6366F1),
                                        Color(0xFFD946EF)
                                    )
                                )
                            )
                            .border(
                                1.5.dp,
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.65f),
                                        Color(0x30FFFFFF),
                                        Color(0xFF67E8F9).copy(alpha = 0.5f)
                                    )
                                ),
                                RoundedCornerShape(18.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Glass specular reflection inside badge
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.40f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.35f, size.height * 0.35f),
                                    radius = size.width * 0.5f
                                )
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "SAIF AI Logo",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Brand Title
                    Text(
                        text = "SAIF AI",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Subtitle badge
                    Surface(
                        color = Color(0x308B5CF6),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.8.dp, Color(0x40A78BFA))
                    ) {
                        Text(
                            text = "Next-Gen Intelligence",
                            color = Color(0xFFC4B5FD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isAccessGranted) {
                        // --- Access Granted State with Liquid Pulse Ring ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                color = Color(0x358B5CF6),
                                shape = RoundedCornerShape(22.dp),
                                border = BorderStroke(1.2.dp, Color(0xFFA78BFA).copy(alpha = accessPulse)),
                                modifier = Modifier.padding(bottom = 18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Access Granted",
                                        color = Color(0xFFE0E7FF),
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            Button(
                                onClick = {},
                                enabled = false,
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = Color(0xFF7C3AED),
                                    disabledContentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = "Entering Workspace...",
                                    color = Color.White.copy(alpha = accessPulse),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        // --- Segmented Liquid Glass Tab Switcher (Sign In <-> Sign Up) ---
                        LiquidGlassSegmentedTabs(
                            isSignUp = isSignUpMode,
                            onTabSelected = { signUp ->
                                if (isSignUpMode != signUp) {
                                    focusManager.clearFocus()
                                    errorMessage = null
                                    isSignUpMode = signUp
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // --- Input Fields with Frosted Glass & Neon Focus ---
                        AnimatedVisibility(
                            visible = isSignUpMode,
                            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(animationSpec = tween(220)),
                            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
                        ) {
                            Column {
                                LiquidGlassInputField(
                                    value = nameInput,
                                    onValueChange = { nameInput = it; errorMessage = null },
                                    placeholder = "Your Name",
                                    leadingIcon = Icons.Default.Person,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Next
                                    )
                                )
                                Spacer(modifier = Modifier.height(13.dp))
                            }
                        }

                        LiquidGlassInputField(
                            value = emailInput,
                            onValueChange = { emailInput = it; errorMessage = null },
                            placeholder = "Email Address",
                            leadingIcon = Icons.Default.Email,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            )
                        )

                        Spacer(modifier = Modifier.height(13.dp))

                        LiquidGlassInputField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it; errorMessage = null },
                            placeholder = "Password",
                            leadingIcon = Icons.Default.Lock,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { handleAuthSubmit() }),
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                        tint = if (isPasswordVisible) Color(0xFFA78BFA) else Color(0xFF64748B),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        )

                        if (!isSignUpMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, end = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "Forgot Password?",
                                    color = Color(0xFFA78BFA),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            resetEmailInput = emailInput.trim()
                                            showForgotPasswordDialog = true
                                        }
                                        .padding(vertical = 4.dp, horizontal = 6.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Liquid Glass Shimmer Action Button ---
                        LiquidGlassButton(
                            text = if (isSignUpMode) "Sign Up" else "Sign In",
                            isLoading = isLoading,
                            onClick = { handleAuthSubmit() }
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // --- Mode Switch Footer Link ---
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSignUpMode) "Already have an account? " else "Don't have an account? ",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (isSignUpMode) "Sign In" else "Sign Up",
                                color = Color(0xFFA78BFA),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        focusManager.clearFocus()
                                        errorMessage = null
                                        isSignUpMode = !isSignUpMode
                                    }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Divider with OR ---
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color.White.copy(alpha = 0.12f),
                                thickness = 1.dp
                            )
                            Text(
                                text = "OR",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color.White.copy(alpha = 0.12f),
                                thickness = 1.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // --- Continue as Guest Liquid Glass Pill Button ---
                        Surface(
                            onClick = {
                                focusManager.clearFocus()
                                val guest = AuthManager.signInAsGuest()
                                onLoginSuccess(guest)
                            },
                            color = Color(0x188B5CF6),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Guest Access",
                                    tint = Color(0xFFC4B5FD),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Continue as Guest",
                                    color = Color(0xFFEDE9FE),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Forgot Password Dialog ---
        if (showForgotPasswordDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isResetLoading) showForgotPasswordDialog = false
                },
                containerColor = Color(0xFF131127),
                titleContentColor = Color.White,
                textContentColor = Color(0xFF94A3B8),
                shape = RoundedCornerShape(24.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Password Reset",
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Reset Password",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Enter your registered email address to receive a secure Firebase password reset link.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LiquidGlassInputField(
                            value = resetEmailInput,
                            onValueChange = { resetEmailInput = it },
                            placeholder = "Registered Email",
                            leadingIcon = Icons.Default.Email,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (resetEmailInput.isNotBlank() && !isResetLoading) {
                                        isResetLoading = true
                                        scope.launch {
                                            val res = AuthManager.sendPasswordReset(resetEmailInput)
                                            isResetLoading = false
                                            res.fold(
                                                onSuccess = { msg ->
                                                    statusMessage = msg
                                                    showForgotPasswordDialog = false
                                                },
                                                onFailure = { err ->
                                                    errorMessage = err.message ?: "Failed to send reset link"
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (resetEmailInput.isBlank()) {
                                errorMessage = "Please enter your email"
                                return@Button
                            }
                            isResetLoading = true
                            scope.launch {
                                val res = AuthManager.sendPasswordReset(resetEmailInput)
                                isResetLoading = false
                                res.fold(
                                    onSuccess = { msg ->
                                        statusMessage = msg
                                        showForgotPasswordDialog = false
                                    },
                                    onFailure = { err ->
                                        errorMessage = err.message ?: "Failed to send reset link"
                                    }
                                )
                            }
                        },
                        enabled = !isResetLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isResetLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Send Link", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showForgotPasswordDialog = false },
                        enabled = !isResetLoading
                    ) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                }
            )
        }
    }
}

/**
 * Animated Segmented Tab Switcher with sliding liquid glass pill indicator
 */
@Composable
private fun LiquidGlassSegmentedTabs(
    isSignUp: Boolean,
    onTabSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabOffset by animateFloatAsState(
        targetValue = if (!isSignUp) 0f else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "tab_pill_offset"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x350F1626))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(14.dp))
            .padding(3.dp)
    ) {
        val tabWidth = maxWidth / 2

        // Sliding liquid glass active pill indicator
        Box(
            modifier = Modifier
                .offset(x = tabWidth * tabOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .shadow(8.dp, RoundedCornerShape(11.dp), spotColor = Color(0xFF8B5CF6))
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF7C3AED),
                            Color(0xFF6366F1),
                            Color(0xFFD946EF)
                        )
                    )
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(11.dp)
                )
        )

        // Tab Text Buttons
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(11.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabSelected(false) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sign In",
                    color = if (!isSignUp) Color.White else Color(0xFF94A3B8),
                    fontSize = 13.5.sp,
                    fontWeight = if (!isSignUp) FontWeight.Bold else FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(11.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabSelected(true) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sign Up",
                    color = if (isSignUp) Color.White else Color(0xFF94A3B8),
                    fontSize = 13.5.sp,
                    fontWeight = if (isSignUp) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Frosted Glass Input Field with dynamic animated neon liquid glow on focus
 */
@Composable
private fun LiquidGlassInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.20f,
        animationSpec = tween(250),
        label = "input_border_alpha"
    )

    Surface(
        color = Color(0x35141D30),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.2.dp,
            if (isFocused) {
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFFA78BFA),
                        Color(0xFF38BDF8),
                        Color(0xFFF472B6)
                    )
                )
            } else {
                SolidColor(Color.White.copy(alpha = borderAlpha))
            }
        ),
        shadowElevation = if (isFocused) 6.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading Icon Container with Frosted Glass Badge
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isFocused) Color(0x308B5CF6) else Color(0x18FFFFFF)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isFocused) Color(0xFFC4B5FD) else Color(0xFF64748B),
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Color(0xFF64748B),
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    cursorBrush = SolidColor(Color(0xFF38BDF8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused }
                )
            }

            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}

/**
 * Liquid Glass Action Button with animated iridescent light sweep shimmer
 */
@Composable
private fun LiquidGlassButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth press scaling bounce
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btn_scale"
    )

    // Animated diagonal shimmer light sweep
    val infiniteTransition = rememberInfiniteTransition(label = "btn_shimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "btn_shimmer_progress"
    )

    Button(
        onClick = onClick,
        enabled = !isLoading,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .scale(buttonScale)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(14.dp),
                spotColor = Color(0xFFA78BFA),
                ambientColor = Color(0x6006B6D4)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF7C3AED),
                        Color(0xFF6366F1),
                        Color(0xFFD946EF)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.Transparent
                    )
                ),
                RoundedCornerShape(14.dp)
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Liquid light beam sweep across button surface
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sweepWidth = size.width * 0.40f
                val startX = (size.width + sweepWidth * 2f) * shimmerProgress - sweepWidth
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        startX = startX,
                        endX = startX + sweepWidth
                    )
                )
            }

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Authenticating...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

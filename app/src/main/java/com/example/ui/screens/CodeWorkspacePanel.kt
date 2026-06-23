package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.MoshiHelper
import com.example.ui.editor.CodeSyntaxHighlighter
import java.io.File

@Composable
fun CodeWorkspacePanel(
    file: File,
    initialContent: String,
    isSaving: Boolean,
    onSave: (String) -> Unit,
    onClose: () -> Unit
) {
    var codeText by remember(file.absolutePath) { mutableStateOf(initialContent) }
    val syntaxHighlighter = remember(file.extension) { CodeSyntaxHighlighter(file.extension) }
    
    // Count newlines efficiently without memory splits for maximum speed on heavy files
    val lineCount = remember(codeText) {
        var count = 1
        for (i in 0 until codeText.length) {
            if (codeText[i] == '\n') count++
        }
        count
    }

    val isJson = remember(file.extension) { file.extension.lowercase() == "json" }
    var jsonErrorMsg by remember(codeText) { mutableStateOf<String?>(null) }

    // On typo check for JSON validation
    LaunchedEffect(codeText) {
        if (isJson) {
            val valid = MoshiHelper.isValidJson(codeText)
            jsonErrorMsg = if (!valid && codeText.isNotBlank()) {
                "Malformed JSON Syntax"
            } else {
                null
            }
        } else {
            jsonErrorMsg = null
        }
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RectangleShape
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header panel controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Text(
                        text = "Path: projects/../${file.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (jsonErrorMsg != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = jsonErrorMsg ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { onSave(codeText) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (jsonErrorMsg == null) Color(0xFF007ACC) else Color.DarkGray
                        ),
                        enabled = jsonErrorMsg == null && !isSaving,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isSaving) "Saving.." else "Save", style = MaterialTheme.typography.labelLarge)
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close file",
                            tint = Color.LightGray
                        )
                    }
                }
            }

            // Editor workspace body
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Line numbers column
                Column(
                    modifier = Modifier
                        .width(40.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF181818))
                        .padding(top = 12.dp, end = 4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    val scrollState = rememberScrollState()
                    (1..lineCount).forEach { index ->
                        Text(
                            text = index.toString(),
                            color = Color(0xFF858585),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.height(18.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }

                // Code Input Column
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF1E1E1E))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = codeText,
                        onValueChange = { codeText = it },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFD4D4D4),
                            lineHeight = 18.sp
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        cursorBrush = SolidColor(Color.White),
                        visualTransformation = syntaxHighlighter,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false
                        )
                    )
                }
            }

            // Status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF007ACC))
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lines: $lineCount | Chars: ${codeText.length}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "UTF-8 | ${file.extension.uppercase()}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

package com.shohan.bokeya.ui.screens.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.domain.model.Category
import com.shohan.bokeya.domain.model.CategoryKind
import com.shohan.bokeya.ui.components.BokeyaCard
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.SectionHeader
import com.shohan.bokeya.ui.screens.accounts.ConfirmDialog
import com.shohan.bokeya.ui.theme.BokeyaTheme
import com.shohan.bokeya.ui.viewmodel.CategoriesViewModel

/**
 * Manage income/expense categories.
 *
 * Built-in categories can be renamed but not deleted — they are the fallback
 * buckets that entries fall back to, so removing one would orphan history.
 */
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addingKind by remember { mutableStateOf<CategoryKind?>(null) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }
    var renaming by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            BokeyaTopBar(
                title = stringResource(R.string.money_manage_categories),
                onBack = onBack,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addingKind = CategoryKind.EXPENSE },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.money_new_category))
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("expenseHeader") {
                SectionHeader(
                    title = stringResource(R.string.money_expense),
                    actionLabel = stringResource(R.string.action_add),
                    onActionClick = { addingKind = CategoryKind.EXPENSE },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(state.expense, key = { "exp_${it.id}" }) { category ->
                CategoryRow(
                    category = category,
                    fallbackColor = BokeyaTheme.colors.expense,
                    onRename = { renaming = category },
                    onDelete = { pendingDelete = category },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item("incomeHeader") {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    title = stringResource(R.string.money_income),
                    actionLabel = stringResource(R.string.action_add),
                    onActionClick = { addingKind = CategoryKind.INCOME },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(state.income, key = { "inc_${it.id}" }) { category ->
                CategoryRow(
                    category = category,
                    fallbackColor = BokeyaTheme.colors.income,
                    onRename = { renaming = category },
                    onDelete = { pendingDelete = category },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    addingKind?.let { kind ->
        NameDialog(
            title = stringResource(R.string.money_new_category),
            initialValue = "",
            onConfirm = { name ->
                viewModel.addCategory(name, kind)
                addingKind = null
            },
            onDismiss = { addingKind = null },
        )
    }

    renaming?.let { category ->
        NameDialog(
            title = stringResource(R.string.action_edit),
            initialValue = category.name,
            onConfirm = { name ->
                viewModel.renameCategory(category.id, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    pendingDelete?.let { category ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_category_title),
            message = stringResource(R.string.confirm_delete_category_msg),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.deleteCategory(category.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    fallbackColor: Color,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = category.color?.let { Color(it) } ?: fallbackColor

    BokeyaCard(modifier = modifier.fillMaxWidth(), onClick = onRename, contentPadding = 13.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (!category.isDefault) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.money_category_name)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.large,
    )
}

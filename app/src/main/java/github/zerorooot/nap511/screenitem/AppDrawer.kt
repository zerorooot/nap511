package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.bean.DrawerMenuItem
import github.zerorooot.nap511.bean.RemainingSpaceBean
import github.zerorooot.nap511.bean.Route

@Composable
fun AppDrawer(
    drawerState: DrawerState,
    gesturesEnabled: Boolean,
    remainingSpaceBean: RemainingSpaceBean,
    avatarBean: AvatarBean,
    menuItems: List<DrawerMenuItem>,
    currentRoute: Route?,
    onMenuItemClick: (Route) -> Unit,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        gesturesEnabled = gesturesEnabled || drawerState.isOpen,
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(6.dp))
                Avatar(remainingSpaceBean, avatarBean)
                Spacer(Modifier.height(6.dp))

                menuItems.forEach { item ->
                    val isSelected = currentRoute == item.route

                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                item.iconVector, contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        selected = isSelected,
                        onClick = {
                            onMenuItemClick(item.route)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        },
        content = content
    )
}

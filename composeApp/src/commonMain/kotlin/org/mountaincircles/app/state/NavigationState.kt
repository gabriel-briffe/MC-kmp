package org.mountaincircles.app.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.PopupClosable
import org.mountaincircles.app.modules.ModuleManager

/**
 * Unified sheet type system for all bottom sheets in the app
 */
sealed class SheetType {
    object MainMenu : SheetType()
    object Settings : SheetType()
    object About : SheetType()
    object Widget : SheetType()
    data class ImportSheet(val moduleId: String) : SheetType()
}

/**
 * Unified popup ID system for exclusive popup management
 */
data class PopupId(
    val moduleId: String,
    val dataId: String? = null  // Optional identifier for specific popup data (e.g., feature ID, device ID)
)

class NavigationState {
    private val _sidebarVisible = MutableStateFlow(false)
    val sidebarVisible: StateFlow<Boolean> = _sidebarVisible

    // Target module for sidebar expansion/scrolling when opened
    private val _sidebarTargetModule = MutableStateFlow<String?>(null)
    val sidebarTargetModule: StateFlow<String?> = _sidebarTargetModule

    // Unified sheet state - replaces multiple boolean flags
    private val _currentSheet = MutableStateFlow<SheetType?>(null)
    val currentSheet: StateFlow<SheetType?> = _currentSheet

    // Legacy compatibility - computed from currentSheet using map
    val mainMenuVisible: StateFlow<Boolean> = _currentSheet.map { it == SheetType.MainMenu }.stateIn(
        kotlinx.coroutines.GlobalScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(),
        false
    )

    val settingsVisible: StateFlow<Boolean> = _currentSheet.map { it == SheetType.Settings }.stateIn(
        kotlinx.coroutines.GlobalScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(),
        false
    )

    
    // Submenu visibility - tracks which module submenu is open
    private val _submenuVisible = MutableStateFlow<String?>(null) // module ID or null
    val submenuVisible: StateFlow<String?> = _submenuVisible

    // Popup visibility - tracks which module popup is open (exclusive like submenus)
    private val _popupVisible = MutableStateFlow<PopupId?>(null)
    val popupVisible: StateFlow<PopupId?> = _popupVisible

    // Import sheet visibility - tracks which module import sheet is open
    
    fun toggleSidebar() {
        val newValue = !_sidebarVisible.value
        _sidebarVisible.value = newValue
        Logger.log("UI", LogLevel.INFO, "NavigationState: Sidebar toggled to $newValue")

        // Close main menu when opening sidebar
        if (newValue && _currentSheet.value == SheetType.MainMenu) {
            _currentSheet.value = null
            Logger.log("UI", LogLevel.INFO, "NavigationState: Main menu closed due to sidebar opening")
        }
    }
    
    fun toggleMainMenu() {
        val newSheet = if (_currentSheet.value == SheetType.MainMenu) null else SheetType.MainMenu
        _currentSheet.value = newSheet
        Logger.log("UI", LogLevel.INFO, "NavigationState: Main menu toggled to ${newSheet != null}")

        // Close sidebar when opening main menu
        if (newSheet != null && _sidebarVisible.value) {
            _sidebarVisible.value = false
            Logger.log("UI", LogLevel.INFO, "NavigationState: Sidebar closed due to main menu opening")
        }
    }
    
    fun openSettings() {
        _currentSheet.value = SheetType.Settings
        Logger.log("UI", LogLevel.INFO, "NavigationState: Settings opened")

        // Close other menus when opening settings
        if (_sidebarVisible.value) {
            _sidebarVisible.value = false
            Logger.log("UI", LogLevel.INFO, "NavigationState: Sidebar closed due to settings opening")
        }
    }
    
    
    fun closeSettings() {
        if (_currentSheet.value == SheetType.Settings) {
            _currentSheet.value = null
            Logger.log("UI", LogLevel.INFO, "NavigationState: Settings closed")
        }
    }
    
    fun openSidebar(targetModule: String? = null) {
        val wasClosed = !_sidebarVisible.value
        if (wasClosed) {
            _sidebarVisible.value = true
            Logger.log("UI", LogLevel.INFO, "NavigationState: Sidebar opened")
        }

    // Set target module for expansion/scrolling
    if (targetModule != null) {
        _sidebarTargetModule.value = targetModule
        Logger.log("UI", LogLevel.INFO, "NavigationState: Sidebar target module set to '$targetModule'")
    } else {
        // Clear target when opening without specific target
        _sidebarTargetModule.value = null
        Logger.log("UI", LogLevel.INFO, "NavigationState: Sidebar target cleared (opened without specific target)")
    }
    }

    fun closeSidebar() {
        if (_sidebarVisible.value) {
            _sidebarVisible.value = false
            _sidebarTargetModule.value = null // Clear target when closing
            Logger.log("UI", LogLevel.INFO, "NavigationState: Sidebar closed")
        }
    }

    fun clearSidebarTarget() {
        _sidebarTargetModule.value = null
        Logger.log("UI", LogLevel.DEBUG, "NavigationState: Sidebar target cleared")
    }
    
    fun closeMainMenu() {
        if (_currentSheet.value == SheetType.MainMenu) {
            _currentSheet.value = null
            Logger.log("UI", LogLevel.INFO, "NavigationState: Main menu closed")
        }
    }

    fun openAbout() {
        _currentSheet.value = SheetType.About
        Logger.log("UI", LogLevel.INFO, "NavigationState: About sheet opened")
    }

    fun closeAbout() {
        if (_currentSheet.value == SheetType.About) {
            _currentSheet.value = null
            Logger.log("UI", LogLevel.INFO, "NavigationState: About sheet closed")
        }
    }

    fun openWidget() {
        _currentSheet.value = SheetType.Widget
        Logger.log("UI", LogLevel.INFO, "NavigationState: Widget sheet opened")
    }

    fun closeWidget() {
        if (_currentSheet.value == SheetType.Widget) {
            _currentSheet.value = null
            Logger.log("UI", LogLevel.INFO, "NavigationState: Widget sheet closed")
        }
    }
    
    fun closeAll() {
        val sidebarWasOpen = _sidebarVisible.value
        val currentSheet = _currentSheet.value
        val submenuWasOpen = _submenuVisible.value != null
        val popupWasOpen = _popupVisible.value != null
        val importSheetWasOpen = currentSheet is SheetType.ImportSheet

        _sidebarVisible.value = false
        _currentSheet.value = null
        _submenuVisible.value = null
        _popupVisible.value = null

        if (sidebarWasOpen || currentSheet != null || submenuWasOpen || popupWasOpen || importSheetWasOpen) {
            Logger.log("UI", LogLevel.INFO, "NavigationState: All navigation panels closed")
        }
    }
    
    // Submenu management functions
    fun toggleSubmenu(moduleId: String) {
        val isCurrentlyOpen = _submenuVisible.value == moduleId
        _submenuVisible.value = if (isCurrentlyOpen) null else moduleId
        Logger.log("UI", LogLevel.INFO, "NavigationState: Submenu for $moduleId ${if (isCurrentlyOpen) "closed" else "opened"}")
        
        // Close other UI elements when opening submenu
        if (!isCurrentlyOpen) {
            if (_currentSheet.value == SheetType.MainMenu) {
                _currentSheet.value = null
                Logger.log("UI", LogLevel.INFO, "NavigationState: Main menu closed due to submenu opening")
            }
        }
    }
    
    fun openSubmenu(moduleId: String) {
        if (_submenuVisible.value != moduleId) {
            _submenuVisible.value = moduleId
            Logger.log("UI", LogLevel.INFO, "NavigationState: Submenu opened for module: $moduleId")
            
            // Close other UI elements
            if (_currentSheet.value == SheetType.MainMenu) {
                _currentSheet.value = null
                Logger.log("UI", LogLevel.INFO, "NavigationState: Main menu closed due to submenu opening")
            }
        }
    }
    
    fun closeSubmenu() {
        if (_submenuVisible.value != null) {
            val previousModule = _submenuVisible.value
            _submenuVisible.value = null
            Logger.log("UI", LogLevel.INFO, "NavigationState: Submenu closed for module: $previousModule")
        }
    }

    // Popup management functions (exclusive like submenus)
    fun openPopup(popupId: PopupId) {
        if (_popupVisible.value != popupId) {
            // Close any existing popup first (with proper cleanup)
            val previousPopup = _popupVisible.value
            if (previousPopup != null) {
                Logger.log("UI", LogLevel.DEBUG, "NavigationState: Auto-closing previous popup ${previousPopup.moduleId}:${previousPopup.dataId} before opening new one")
                closePopupForModule(previousPopup.moduleId)
            }

            // Open the new popup
            _popupVisible.value = popupId
            Logger.log("UI", LogLevel.INFO, "NavigationState: Popup opened for ${popupId.moduleId}:${popupId.dataId}")
        }
    }

    fun closePopup() {
        if (_popupVisible.value != null) {
            val previousPopup = _popupVisible.value
            _popupVisible.value = null
            Logger.log("UI", LogLevel.INFO, "NavigationState: Popup closed for ${previousPopup?.moduleId}:${previousPopup?.dataId}")
        }
    }

    fun closePopupForModule(moduleId: String) {
        val currentPopup = _popupVisible.value
        if (currentPopup?.moduleId == moduleId) {
            closePopup()

            // Trigger module-specific cleanup if the module implements PopupClosable
            val module = getGlobalState().moduleManager.getModule(moduleId)
            if (module is PopupClosable) {
                Logger.log("UI", LogLevel.DEBUG, "NavigationState: Calling onPopupClosed for module $moduleId")
                module.onPopupClosed()
            }
        }
    }

    // Import sheet management functions
    fun openImportSheet(moduleId: String) {
        _currentSheet.value = SheetType.ImportSheet(moduleId)
        Logger.log("UI", LogLevel.INFO, "NavigationState: Import sheet opened for $moduleId")

        // Close other menus when opening import sheet
        if (_sidebarVisible.value) {
            _sidebarVisible.value = false
            Logger.log("UI", LogLevel.INFO, "NavigationState: Sidebar closed due to import sheet opening")
        }
        if (_submenuVisible.value != null) {
            _submenuVisible.value = null
            Logger.log("UI", LogLevel.INFO, "NavigationState: Submenu closed due to import sheet opening")
        }
    }

    fun closeImportSheet() {
        if (_currentSheet.value is SheetType.ImportSheet) {
            val currentModule = (_currentSheet.value as SheetType.ImportSheet).moduleId
            _currentSheet.value = null
            Logger.log("UI", LogLevel.INFO, "NavigationState: Import sheet closed for $currentModule")
        }
    }
}

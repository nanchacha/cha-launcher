package com.example.chalauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_usage_prefs", Context.MODE_PRIVATE)

    fun isSetupCompleted(): Boolean {
        return prefs.getBoolean("setup_completed", false)
    }

    fun setSetupCompleted(completed: Boolean) {
        prefs.edit().putBoolean("setup_completed", completed).apply()
    }

    fun resetSetupState() {
        prefs.edit().putBoolean("setup_completed", false).apply()
    }

    fun getSelectedAppPackages(): Set<String> {
        return prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()
    }

    fun saveSelectedAppPackages(packages: Set<String>) {
        prefs.edit().putStringSet("selected_apps", packages).apply()
    }

    fun addAppToSelected(packageName: String) {
        val current = getSelectedAppPackages().toMutableSet()
        if (current.add(packageName)) {
            saveSelectedAppPackages(current)
        }
    }

    fun removeAppFromSelected(packageName: String) {
        val current = getSelectedAppPackages().toMutableSet()
        if (current.remove(packageName)) {
            saveSelectedAppPackages(current)
        }
    }

    suspend fun getInstalledApps(filterSelected: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        
        val selectedPackages = getSelectedAppPackages()

        val realApps = resolveInfos.mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            // Do not show the launcher itself
            if (packageName == context.packageName) return@mapNotNull null
            
            if (filterSelected && !selectedPackages.contains(packageName)) return@mapNotNull null
            
            val appName = resolveInfo.loadLabel(pm).toString()
            val icon = resolveInfo.loadIcon(pm)
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            val clickCount = prefs.getInt(packageName, 1) // default weight is 1
            
            AppInfo(packageName, appName, icon, launchIntent, clickCount)
        }

        return@withContext realApps.sortedByDescending { it.clickCount }
    }

    fun incrementClickCount(packageName: String) {
        val currentCount = prefs.getInt(packageName, 1)
        prefs.edit().putInt(packageName, currentCount + 1).apply()
    }
}

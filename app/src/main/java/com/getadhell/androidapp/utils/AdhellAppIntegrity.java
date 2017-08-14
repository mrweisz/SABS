package com.getadhell.androidapp.utils;

import android.app.enterprise.AppPermissionControlInfo;
import android.app.enterprise.ApplicationPermissionControlPolicy;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;
import android.util.Log;

import com.getadhell.androidapp.App;
import com.getadhell.androidapp.db.AppDatabase;
import com.getadhell.androidapp.db.entity.AppInfo;
import com.getadhell.androidapp.db.entity.AppPermission;
import com.getadhell.androidapp.db.entity.DisabledPackage;
import com.getadhell.androidapp.db.entity.FirewallWhitelistedPackage;
import com.getadhell.androidapp.db.entity.PolicyPackage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

public class AdhellAppIntegrity {
    private final static String DEFAULT_POLICY_ID = "default-policy";
    private static final String TAG = AdhellAppIntegrity.class.getCanonicalName();

    private static String DEFAULT_POLICY_CHECKED = "adhell_default_policy_checked";


    @Inject
    AppDatabase appDatabase;

    @Inject
    SharedPreferences sharedPreferences;

    @Nullable
    @Inject
    ApplicationPermissionControlPolicy applicationPermissionControlPolicy;

    public AdhellAppIntegrity() {
        App.get().getAppComponent().inject(this);
    }

    public void checkDefaultPolicyExists() {
        PolicyPackage policyPackage = appDatabase.policyPackageDao().getPolicyById(DEFAULT_POLICY_ID);
        if (policyPackage != null) {
            Log.d(TAG, "Default PolicyPackage exists");
            return;
        }
        Log.d(TAG, "Default PolicyPackage does not exist. Creating default policy.");
        policyPackage = new PolicyPackage();
        policyPackage.id = DEFAULT_POLICY_ID;
        policyPackage.name = "Default Policy";
        policyPackage.description = "Automatically generated policy from current Adhell app settings";
        policyPackage.active = true;
        policyPackage.createdAt = policyPackage.updatedAt = new Date();
        appDatabase.policyPackageDao().insert(policyPackage);
        Log.d(TAG, "Default PolicyPackage has been added");
    }

    public void copyDataFromAppInfoToDisabledPackage() {
        List<DisabledPackage> disabledPackages = appDatabase.disabledPackageDao().getAll();
        if (disabledPackages.size() > 0) {
            Log.d(TAG, "DisabledPackages is not empty. No need to move data from AppInfo table");
            return;
        }
        List<AppInfo> disabledApps = appDatabase.applicationInfoDao().getDisabledApps();
        if (disabledApps.size() == 0) {
            Log.d(TAG, "No disabledgetDisabledApps apps in AppInfo table");
            return;
        }
        Log.d(TAG, "There is " + disabledApps.size() + " to move to DisabledPackage table");
        disabledPackages = new ArrayList<>();
        for (AppInfo appInfo : disabledApps) {
            DisabledPackage disabledPackage = new DisabledPackage();
            disabledPackage.packageName = appInfo.packageName;
            disabledPackage.policyPackageId = DEFAULT_POLICY_ID;
            disabledPackages.add(disabledPackage);
        }
        appDatabase.disabledPackageDao().insertAll(disabledPackages);
    }

    public void copyDataFromAppInfoToFirewallWhitelistedPackage() {
        List<FirewallWhitelistedPackage> firewallWhitelistedPackages
                = appDatabase.firewallWhitelistedPackageDao().getAll();
        if (firewallWhitelistedPackages.size() > 0) {
            Log.d(TAG, "FirewallWhitelist package size is: " + firewallWhitelistedPackages.size() + ". No need to move data");
            return;
        }
        List<AppInfo> whitelistedApps = appDatabase.applicationInfoDao().getWhitelistedApps();
        if (whitelistedApps.size() == 0) {
            Log.d(TAG, "No whitelisted apps in AppInfo table");
            return;
        }
        Log.d(TAG, "There is " + whitelistedApps.size() + " to move");
        firewallWhitelistedPackages = new ArrayList<>();
        for (AppInfo appInfo : whitelistedApps) {
            FirewallWhitelistedPackage whitelistedPackage = new FirewallWhitelistedPackage();
            whitelistedPackage.packageName = appInfo.packageName;
            whitelistedPackage.policyPackageId = DEFAULT_POLICY_ID;
            firewallWhitelistedPackages.add(whitelistedPackage);
        }
        appDatabase.firewallWhitelistedPackageDao().insertAll(firewallWhitelistedPackages);
    }

    public void moveAppPermissionsToAppPermissionTable() {
        if (applicationPermissionControlPolicy == null) {
            Log.w(TAG, "applicationPermissionControlPolicy is null");
            return;
        }
        List<AppPermission> appPermissions = appDatabase.appPermissionDao().getAll();
        if (appPermissions.size() > 0) {
            Log.d(TAG, "AppPermission size is " + appPermissions.size() + ". No need to move data");
        }

        List<AppPermissionControlInfo> appPermissionControlInfos
                = applicationPermissionControlPolicy.getPackagesFromPermissionBlackList();
        if (appPermissionControlInfos == null || appPermissionControlInfos.size() == 0) {
            Log.d(TAG, "No blacklisted packages in applicationPermissionControlPolicy");
            return;
        }
        appPermissions = new ArrayList<>();
        AppPermissionControlInfo appPermissionControlInfo = appPermissionControlInfos.get(0);
        for (String permissionName : appPermissionControlInfo.mapEntries.keySet()) {
            Set<String> packageNames = appPermissionControlInfo.mapEntries.get(permissionName);
            if (packageNames == null) {
                continue;
            }
            for (String packageName : packageNames) {
                AppPermission appPermission = new AppPermission();
                appPermission.packageName = packageName;
                appPermission.permissionName = permissionName;
                appPermission.permissionStatus = AppPermission.STATUS_DISALLOW;
                appPermissions.add(appPermission);
            }
        }
        appDatabase.appPermissionDao().insertAll(appPermissions);
    }

    // TODO: add adblock whitelist packages
    public void addDefaultAdblockWhitelist() {
        // TODO: Is it right
        List<FirewallWhitelistedPackage> firewallWhitelistedPackages = appDatabase.firewallWhitelistedPackageDao().getAll();
        if (firewallWhitelistedPackages.size() > 0) {
            Log.d(TAG, "User already added firewall whitelist packages. Assuming he/she knows how whitelist works. ");
            return;
        }
        // TODO: Maybe check if app installed
        firewallWhitelistedPackages = new ArrayList<>();
        firewallWhitelistedPackages.add(new FirewallWhitelistedPackage("com.google.android.music", DEFAULT_POLICY_ID));
        firewallWhitelistedPackages.add(new FirewallWhitelistedPackage("com.google.android.apps.fireball", DEFAULT_POLICY_ID));
        firewallWhitelistedPackages.add(new FirewallWhitelistedPackage("com.nttdocomo.android.ipspeccollector2", DEFAULT_POLICY_ID));
        appDatabase.firewallWhitelistedPackageDao().insertAll(firewallWhitelistedPackages);
    }
}

// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import org.dolphinemu.dolphinemu.BuildConfig;
import org.dolphinemu.dolphinemu.NativeLibrary;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Locale;

public class VirtualReality
{
  private static final String VR_PACKAGE = "org.dolphinemu.dolphinemu.vr";

  private static boolean isInitialized = false;

  private static boolean isRestored = false;

  public static boolean isActive()
  {
    return BuildConfig.BUILD_TYPE.equals("vr");
  }

  public static boolean isInstalled(Context context)
  {
    PackageManager pm = context.getPackageManager();
    for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA))
    {
      if (app.packageName.equals(VR_PACKAGE))
      {
        return true;
      }
    }
    return false;
  }

  public static boolean isLegacyPath(String filePath)
  {
    return filePath.startsWith("/");
  }

  public static void linkLoader()
  {
    if (isActive())
    {
      String manufacturer = Build.MANUFACTURER.toLowerCase(Locale.ROOT);
      // rename oculus to meta as this will probably happen in the future anyway
      if (manufacturer.contains("oculus"))
        manufacturer = "meta";

      // Load manufacturer specific loader
      try
      {
        System.loadLibrary("openxr_" + manufacturer);
      }
      catch (UnsatisfiedLinkError e)
      {
        Log.error("Unsupported VR device: " + manufacturer);
        System.exit(0);
      }
    }
  }

  public static void openIntent(Context context, String[] filePaths)
  {
    Intent launcher = context.getPackageManager().getLaunchIntentForPackage(VR_PACKAGE);
    launcher.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

    if (VirtualReality.isLegacyPath(filePaths[0]))
    {
      launcher.putExtra("AutoStartFiles", filePaths);
    }
    else
    {
      Uri uri;
      try
      {
        uri = ContentHandler.unmangle(filePaths[0]);
      }
      catch (FileNotFoundException e)
      {
        uri = Uri.parse(filePaths[0]);
      }
      launcher.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      launcher.setAction(Intent.ACTION_GET_CONTENT);
      launcher.setData(uri);
    }

    serializeConfigs(launcher);
    context.getApplicationContext().startActivity(launcher);
  }

  public static boolean isInitialized()
  {
    return isInitialized;
  }

  public static void setInitialized()
  {
    isInitialized = true;
  }

  public static void restoreConfig(Context context, Bundle extras)
  {
    // Do not allow opening a second instance over
    if (isRestored)
    {
      isRestored = false;
      NativeLibrary.finishEmulationActivity();
      return;
    }

    File root = DirectoryInitialization.getUserDirectoryPath(context);
    File config = new File(root, "/Config/");
    config.mkdirs();

    for (String filename : extras.getStringArrayList("ConfigFiles"))
    {
      File file = new File(config, filename);

      try
      {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
        {
          byte[] bytes = extras.getByteArray(file.getName());
          if (bytes != null)
            Files.write(file.toPath(), bytes);
        }
      }
      catch (IOException e)
      {
        e.printStackTrace();
      }
    }
    isRestored = true;
  }

  private static void serializeConfigs(Intent intent)
  {
    ArrayList<String> files = new ArrayList<>();
    File root = new File(DirectoryInitialization.getUserDirectory() + "/Config/");
    for (File file : root.listFiles())
    {
      if (file.isDirectory())
        continue;

      try
      {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
        {
          intent.putExtra(file.getName(), Files.readAllBytes(file.toPath()));
          files.add(file.getName());
        }
      }
      catch (IOException e)
      {
        e.printStackTrace();
      }
    }
    intent.putStringArrayListExtra("ConfigFiles", files);
  }
}

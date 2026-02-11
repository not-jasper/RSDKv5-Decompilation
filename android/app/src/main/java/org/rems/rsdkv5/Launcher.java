package org.rems.rsdkv5;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class Launcher extends AppCompatActivity {

    private static final int RSDK_VER = 5;
    private static Uri basePath = null;
    public static Launcher instance = null;
    private static File basePathStore;
    private static ActivityResultLauncher<Intent> folderLauncher = null;
    private static ActivityResultLauncher<Intent> gameLauncher = null;

    private static int takeFlags = (Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
            Intent.FLAG_GRANT_READ_URI_PERMISSION);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        basePathStore = new File(getFilesDir(), "basePathStore");

        folderLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        basePath = result.getData().getData();
                        
                        getContentResolver().takePersistableUriPermission(basePath, takeFlags);
                        
                        refreshStore();
                        startGame(true);
                    } else {
                        quit(0);
                    }
                });

        gameLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> quit(0));

        boolean canRun = true;
        if (RSDK_VER == 5) {
            if (((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE))
                    .getDeviceConfigurationInfo().reqGlEsVersion < 0x20000) {
                canRun = false;
                new AlertDialog.Builder(this)
                        .setTitle("GLES 2.0 unsupported")
                        .setMessage("This device does not support GLES 2.0, which is required for running RSDKv5.")
                        .setNegativeButton("OK", (dialog, i) -> {
                            dialog.cancel();
                            quit(2);
                        })
                        .setCancelable(false)
                        .show();
            }
        }

        if (canRun)
            startGame(false);
    }

    private void quit(int code) {
        finishAffinity();
        System.exit(code);
    }

    public static Uri refreshStore() {
        if (basePathStore.exists() && basePath == null) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(basePathStore));
                String uri = reader.readLine();
                if (uri != null) basePath = Uri.parse(uri);
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (basePath != null) {
            try {
                FileWriter writer = new FileWriter(basePathStore);
                writer.write(basePath.toString() + "\n");
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return basePath;
    }

    private void startGame(boolean fromPicker) {
        refreshStore();

        boolean found = false;
        if (basePath != null) {
            for (UriPermission uriPermission : getContentResolver().getPersistedUriPermissions()) {
                if (uriPermission.getUri().toString().equals(basePath.toString())) {
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            folderPicker();
        } else {
            try {
                if (DocumentFile.fromTreeUri(this, basePath).findFile(".nomedia") == null)
                    createFile(".nomedia");
            } catch (Exception e) {}

            Intent intent = new Intent(this, RSDK.class);
            intent.setData(basePath);
            intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            
            grantUriPermission(getPackageName() + ".RSDK", basePath,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

            instance = this;
            gameLauncher.launch(intent);
        }
    }

    private void folderPicker() {
        refreshStore();
        
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        
        folderLauncher.launch(intent);
    }

    public Uri createFile(String filename) throws FileNotFoundException {
        DocumentFile path = DocumentFile.fromTreeUri(getApplicationContext(), basePath);
        while (filename.indexOf('/') != -1) {
            String sub = filename.substring(0, filename.indexOf('/'));
            if (!sub.isEmpty()) {
                DocumentFile find = path.findFile(sub);
                if (find == null) path = path.createDirectory(sub);
                else path = find;    
            }
            filename = filename.substring(filename.indexOf('/') + 1);
        }

        DocumentFile find = path.findFile(filename);
        if (find == null) return path.createFile("application/octet-stream", filename).getUri();
        else return find.getUri();
    }
}

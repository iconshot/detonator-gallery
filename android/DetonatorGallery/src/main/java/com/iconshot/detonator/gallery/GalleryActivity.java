package com.iconshot.detonator.gallery;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.function.Consumer;

public class GalleryActivity extends Activity {

    public static Consumer<Boolean> permissionResultCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                100
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        boolean hasGrantedReadPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED;

        boolean permissionGranted = hasGrantedReadPermission;

        if (permissionResultCallback != null) {
            permissionResultCallback.accept(permissionGranted);
        }

        finish();
    }
}
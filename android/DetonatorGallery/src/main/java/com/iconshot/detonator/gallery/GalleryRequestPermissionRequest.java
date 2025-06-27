package com.iconshot.detonator.gallery;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import com.iconshot.detonator.Detonator;
import com.iconshot.detonator.helpers.ContextHelper;
import com.iconshot.detonator.request.Request;

public class GalleryRequestPermissionRequest extends Request {
    public GalleryRequestPermissionRequest(Detonator detonator, IncomingRequest incomingRequest) {
        super(detonator, incomingRequest);
    }

    @Override
    public void run() {
        Context context = ContextHelper.context;

        boolean hasReadPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;

        boolean permissionGranted = hasReadPermission;

        if (permissionGranted) {
            end(true);

            return;
        }

        GalleryActivity.permissionResultCallback = granted -> {
            end(granted);
        };

        context.startActivity(new Intent(context, GalleryActivity.class));
    }
}

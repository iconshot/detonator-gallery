package com.iconshot.detonator.gallery;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.util.Size;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class GalleryHelper {
    public static File getThumbnailFile(Context context, Uri uri) {
        String thumbnailFileName = null;

        try {
            thumbnailFileName = md5(uri.toString()) + ".png";
        } catch (Exception e) {}

        File thumbnailDirectory = new File(context.getCacheDir(), "com.iconshot.detonator.gallery");

        File thumbnailFile = new File(thumbnailDirectory, thumbnailFileName);

        return thumbnailFile;
    }

    public static String generateThumbnail(Context context, Uri contentUri) {
        ContentResolver contentResolver = context.getContentResolver();

        File thumbnailFile = getThumbnailFile(context, contentUri);

        String thumbnail = "file://" + thumbnailFile.getAbsolutePath();

        ensureParentDir(thumbnailFile);

        boolean generateThumbnail = !thumbnailFile.exists();

        if (generateThumbnail && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Bitmap bitmap = contentResolver.loadThumbnail(
                        contentUri,
                        new Size(500, 500),
                        null
                );

                FileOutputStream out = new FileOutputStream(thumbnailFile);

                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);

                out.flush();
            } catch (IOException e) {}
        }

        return thumbnail;
    }

    public static void ensureParentDir(File file) {
        File directory = file.getParentFile();

        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    public static String md5(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");

        byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder();

        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }
}

package com.iconshot.detonator.gallery;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.core.content.ContextCompat;

import com.iconshot.detonator.Detonator;
import com.iconshot.detonator.module.Module;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class GalleryModule extends Module {
    public Map<Integer, List<GalleryAlbum>> albumListMap = new HashMap<>();

    public GalleryModule(Detonator detonator) {
        super(detonator);
    }

    @Override
    public void setUp() {
        detonator.setRequestListener("com.iconshot.detonator.gallery::requestPermission", (promise, value, edge) -> {
            boolean hasReadPermission = ContextCompat.checkSelfPermission(
                    detonator.context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED;

            boolean permissionGranted = hasReadPermission;

            if (permissionGranted) {
                promise.resolve(true);

                return;
            }

            GalleryActivity.permissionResultCallback = granted -> {
                promise.resolve(granted);
            };

            detonator.context.startActivity(new Intent(detonator.context, GalleryActivity.class));
        });

        detonator.setRequestListener("com.iconshot.detonator.gallery.albumreader::read", (promise, value, edge) -> {
            AlbumReadData data = detonator.decode(value, AlbumReadData.class);

            ContentResolver contentResolver = detonator.context.getContentResolver();

            new Thread(() -> {
                List<GalleryAlbum> albumList = albumListMap.get(data.id);

                if (albumList == null) {
                    ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

                    ConcurrentHashMap<String, GalleryAlbum> albums = new ConcurrentHashMap<>();

                    HashSet<String> albumIds = new LinkedHashSet<>();

                    Uri collection = MediaStore.Files.getContentUri("external");

                    String[] projection = {
                            MediaStore.Files.FileColumns._ID,
                            MediaStore.Files.FileColumns.MEDIA_TYPE,
                            MediaStore.MediaColumns.BUCKET_ID,
                            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
                    };

                    String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE +
                            " OR " + MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

                    String sortOrder = MediaStore.MediaColumns.DATE_ADDED + " DESC";

                    try (Cursor cursor = contentResolver.query(
                            collection,
                            projection,
                            selection,
                            null,
                            sortOrder
                    )) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
                            int typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE);
                            int bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID);
                            int bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME);

                            List<Future<?>> futures = new ArrayList<>();

                            do {
                                long id = cursor.getLong(idColumn);
                                int mediaType = cursor.getInt(typeColumn);
                                String bucketId = cursor.getString(bucketIdColumn);
                                String bucketName = cursor.getString(bucketNameColumn);

                                Uri contentUri;

                                if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
                                    contentUri = ContentUris.withAppendedId(
                                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                                } else if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                                    contentUri = ContentUris.withAppendedId(
                                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                                } else {
                                    continue;
                                }

                                albumIds.add(bucketId);

                                futures.add(executor.submit(() -> {
                                    albums.compute(bucketId, (key, currentAlbum) -> {
                                        if (currentAlbum != null) {
                                            currentAlbum.mediaCount++;

                                            return currentAlbum;
                                        } else {
                                            String thumbnail = GalleryHelper.generateThumbnail(
                                                    detonator.context, contentUri);

                                            GalleryAlbum album = new GalleryAlbum();

                                            album.id = bucketId;
                                            album.name = bucketName;
                                            album.mediaCount = 1;
                                            album.thumbnail = thumbnail;

                                            return album;
                                        }
                                    });
                                }));
                            } while (cursor.moveToNext());

                            for (Future<?> future : futures) {
                                try {
                                    future.get();
                                } catch (Exception e) {}
                            }

                            executor.shutdown();
                        }
                    }

                    albumList = albumIds.stream()
                            .map(albums::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    albumListMap.put(data.id, albumList);
                }

                int fromIndex = data.offset;
                int toIndex = Math.min(fromIndex + data.limit, albumList.size());

                List<GalleryAlbum> tmpAlbumList = fromIndex < albumList.size()
                        ? albumList.subList(fromIndex, toIndex)
                        : new ArrayList<>();

                promise.resolve(tmpAlbumList);
            }).start();
        });

        detonator.setRequestListener("com.iconshot.detonator.gallery.albumreader::close", (promise, value, edge) -> {
            AlbumCloseData data = detonator.decode(value, AlbumCloseData.class);

            albumListMap.remove(data.id);

            promise.resolve();
        });

        detonator.setRequestListener("com.iconshot.detonator.gallery.mediareader::read", (promise, value, edge) -> {
            MediaReadData data = detonator.decode(value, MediaReadData.class);

            ContentResolver contentResolver = detonator.context.getContentResolver();

            List<GalleryMedia> mediaList = new ArrayList<>();

            new Thread(() -> {
                ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

                List<Future<GalleryMedia>> futures = new ArrayList<>();

                String[] projection = {
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.MEDIA_TYPE
                };

                String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE +
                        " OR " + MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

                List<String> selectionArgs = new ArrayList<>();

                if (data.albumId != null) {
                    selection = "(" + selection + ") AND " + MediaStore.MediaColumns.BUCKET_ID + "=?";

                    selectionArgs.add(data.albumId);
                }

                Uri collection = MediaStore.Files.getContentUri("external");

                String sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC";

                try (Cursor cursor = contentResolver.query(
                        collection,
                        projection,
                        selection,
                        selectionArgs.isEmpty() ? null : selectionArgs.toArray(new String[0]),
                        sortOrder
                )) {
                    if (cursor != null && cursor.moveToPosition(data.offset)) {
                        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
                        int typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE);

                        int count = 0;

                        do {
                            if (count++ >= data.limit) {
                                break;
                            }

                            long id = cursor.getLong(idColumn);
                            int mediaType = cursor.getInt(typeColumn);

                            Uri contentUri;

                            if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
                                contentUri = ContentUris.withAppendedId(
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                            } else if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                                contentUri = ContentUris.withAppendedId(
                                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                            } else {
                                continue;
                            }

                            futures.add(executor.submit(() -> createGalleryMedia(contentUri)));
                        } while (cursor.moveToNext());
                    }
                }

                for (Future<GalleryMedia> future : futures) {
                    try {
                        GalleryMedia media = future.get();

                        mediaList.add(media);
                    } catch (Exception e) {}
                }

                executor.shutdown();

                promise.resolve(mediaList);
            }).start();
        });

        detonator.setRequestListener("com.iconshot.detonator.gallery.mediareader::close", (promise, value, edge) -> {
            promise.resolve();
        });
    }

    private GalleryMedia createGalleryMedia(Uri contentUri) {
        ContentResolver contentResolver = detonator.context.getContentResolver();

        String mimeType = contentResolver.getType(contentUri);

        boolean isVideo = mimeType != null && mimeType.startsWith("video");

        String id = "";
        int width = 0;
        int height = 0;
        int rotation = 0;
        Float duration = null;

        String thumbnail = GalleryHelper.generateThumbnail(detonator.context, contentUri);

        // try MediaStore metadata (works on Android 10+ typically)

        try (Cursor cursor = contentResolver.query(
                contentUri,
                new String[]{
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.WIDTH,
                        MediaStore.MediaColumns.HEIGHT,
                },
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                id = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
                width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH));
                height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT));
            }
        }

        // fallback if dimensions are invalid

        if (width <= 0 || height <= 0) {
            if (isVideo) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();

                try {
                    retriever.setDataSource(detonator.context, contentUri);

                    String w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                    String h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

                    width = w != null ? Integer.parseInt(w) : 0;
                    height = h != null ? Integer.parseInt(h) : 0;
                } catch (Exception e) {} finally {
                    try {
                        retriever.release();
                    } catch (Exception e) {}
                }
            } else {
                BitmapFactory.Options opts = new BitmapFactory.Options();

                opts.inJustDecodeBounds = true;

                try (InputStream stream = contentResolver.openInputStream(contentUri)) {
                    BitmapFactory.decodeStream(stream, null, opts);

                    width = opts.outWidth;
                    height = opts.outHeight;
                } catch (Exception e) {}
            }
        }

        // get rotation and duration

        if (isVideo) {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

            try {
                retriever.setDataSource(detonator.context, contentUri);

                String r = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                String d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

                rotation = r != null ? Integer.parseInt(r) : 0;

                if (d != null) {
                    try {
                        long tmpDuration = Long.parseLong(d);

                        if (tmpDuration > 0) {
                            duration = tmpDuration / 1000f;
                        }
                    } catch (NumberFormatException e) {}
                }
            } catch (Exception e) {} finally {
                try {
                    retriever.release();
                } catch (Exception e) {}
            }
        } else {
            try (InputStream input = contentResolver.openInputStream(contentUri)) {
                ExifInterface exif = new ExifInterface(input);

                int orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                );

                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90: {
                        rotation = 90;

                        break;
                    }
                    case ExifInterface.ORIENTATION_ROTATE_180: {
                        rotation = 180;

                        break;
                    }
                    case ExifInterface.ORIENTATION_ROTATE_270: {
                        rotation = 270;

                        break;
                    }
                    default: {
                        rotation = 0;

                        break;
                    }
                }
            } catch (Exception e) {}
        }

        GalleryMedia media = new GalleryMedia();

        media.id = id;
        media.type = isVideo ? "video" : "image";
        media.source = contentUri.toString();
        media.thumbnail = thumbnail;
        media.width = width;
        media.height = height;
        media.rotation = rotation;
        media.duration = duration;

        return media;
    }

    public static class AlbumReadData {
        int id;
        int limit;
        int offset;
    }

    public static class AlbumCloseData {
        int id;
    }

    public static class GalleryAlbum {
        String id;
        String name;
        int mediaCount;
        String thumbnail;
    }

    public static class MediaReadData {
        int limit;
        int offset;
        String albumId;
    }

    public static class GalleryMedia {
        String id;
        String type;
        String source;
        String thumbnail;
        int width;
        int height;
        int rotation;
        Float duration;
    }
}

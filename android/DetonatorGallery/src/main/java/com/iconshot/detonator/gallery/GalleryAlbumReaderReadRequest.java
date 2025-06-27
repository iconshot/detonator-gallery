package com.iconshot.detonator.gallery;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.iconshot.detonator.Detonator;
import com.iconshot.detonator.request.Request;

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

public class GalleryAlbumReaderReadRequest extends Request<GalleryAlbumReaderReadRequest.ReadData> {
    public static Map<Integer, List<GalleryAlbum>> albumListMap = new HashMap<>();

    public GalleryAlbumReaderReadRequest(Detonator detonator, IncomingRequest incomingRequest) {
        super(detonator, incomingRequest);
    }

    @Override
    public void run() {
        ReadData data = decodeData(ReadData.class);

        ContentResolver contentResolver = detonator.context.getContentResolver();

        new Thread(() -> {
            List<GalleryAlbum> albumList = GalleryAlbumReaderReadRequest.albumListMap.get(data.id);

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

                GalleryAlbumReaderReadRequest.albumListMap.put(data.id, albumList);
            }

            int fromIndex = data.offset;
            int toIndex = Math.min(fromIndex + data.limit, albumList.size());

            List<GalleryAlbum> tmpAlbumList = fromIndex < albumList.size()
                    ? albumList.subList(fromIndex, toIndex)
                    : new ArrayList<>();

            detonator.uiHandler.post(() -> {
                end(tmpAlbumList);
            });
        }).start();
    }

    protected static class ReadData {
        int id;
        int limit;
        int offset;
    }

    public static class GalleryAlbum {
        String id;
        String name;
        int mediaCount;
        String thumbnail;
    }
}

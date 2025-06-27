package com.iconshot.detonator.gallery;

import com.iconshot.detonator.Detonator;
import com.iconshot.detonator.request.Request;

public class GalleryAlbumReaderCloseRequest extends Request<GalleryAlbumReaderCloseRequest.CloseData> {
    public GalleryAlbumReaderCloseRequest(Detonator detonator, IncomingRequest incomingRequest) {
        super(detonator, incomingRequest);
    }

    @Override
    public void run() {
        CloseData data = decodeData(CloseData.class);

        GalleryAlbumReaderReadRequest.albumListMap.remove(data.id);

        end();
    }

    protected static class CloseData {
        int id;
    }
}

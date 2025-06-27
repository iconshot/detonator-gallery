package com.iconshot.detonator.gallery;

import com.iconshot.detonator.Detonator;
import com.iconshot.detonator.request.Request;

public class GalleryMediaReaderCloseRequest extends Request {
    public GalleryMediaReaderCloseRequest(Detonator detonator, IncomingRequest incomingRequest) {
        super(detonator, incomingRequest);
    }

    @Override
    public void run() {
        end();
    }
}

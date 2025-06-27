package com.iconshot.detonator.gallery;

import com.iconshot.detonator.Detonator;
import com.iconshot.detonator.request.Request;

public class GallerySaveRequest extends Request<String> {
    public GallerySaveRequest(Detonator detonator, IncomingRequest incomingRequest) {
        super(detonator, incomingRequest);
    }

    @Override
    public void run() {

    }
}

package com.iconshot.detonator.gallery;

import com.iconshot.detonator.Detonator;
import com.iconshot.detonator.module.Module;

public class GalleryModule extends Module {
    public GalleryModule(Detonator detonator) {
        super(detonator);
    }

    @Override
    public void setUp() {
        detonator.setRequestClass("com.iconshot.detonator.gallery::requestPermission", GalleryRequestPermissionRequest.class);

        detonator.setRequestClass("com.iconshot.detonator.gallery.albumreader::read", GalleryAlbumReaderReadRequest.class);
        detonator.setRequestClass("com.iconshot.detonator.gallery.albumreader::close", GalleryAlbumReaderCloseRequest.class);

        detonator.setRequestClass("com.iconshot.detonator.gallery.mediareader::read", GalleryMediaReaderReadRequest.class);
        detonator.setRequestClass("com.iconshot.detonator.gallery.mediareader::close", GalleryMediaReaderCloseRequest.class);
    }
}

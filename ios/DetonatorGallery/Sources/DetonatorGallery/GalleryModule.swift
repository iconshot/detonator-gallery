import Detonator

public class GalleryModule: Module {
    public override func register() -> Void {
        detonator.setRequestClass("com.iconshot.detonator.gallery::requestPermission", GalleryRequestPermissionRequest.self)
        
        detonator.setRequestClass("com.iconshot.detonator.gallery.albumreader::read", GalleryAlbumReaderReadRequest.self)
        detonator.setRequestClass("com.iconshot.detonator.gallery.albumreader::close", GalleryAlbumReaderCloseRequest.self)
        
        detonator.setRequestClass("com.iconshot.detonator.gallery.mediareader::read", GalleryMediaReaderReadRequest.self)
        detonator.setRequestClass("com.iconshot.detonator.gallery.mediareader::close", GalleryMediaReaderCloseRequest.self)
    }
}

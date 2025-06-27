import Photos

import Detonator

public class GalleryRequestPermissionRequest: Request {
    public override func run() -> Void {
        let status = PHPhotoLibrary.authorizationStatus()

        switch status {
        case .authorized:
            end(data: true)

        case .limited:
            if #available(iOS 14, *) {
                end(data: true)
            } else {
                end(data: false)
            }

        case .notDetermined:
            PHPhotoLibrary.requestAuthorization { newStatus in
                DispatchQueue.main.async {
                    if newStatus == .authorized {
                        self.end(data: true)
                    } else if #available(iOS 14, *), newStatus == .limited {
                        self.end(data: true)
                    } else {
                        self.end(data: false)
                    }
                }
            }

        case .denied, .restricted:
            end(data: false)

        @unknown default:
            end(data: false)
        }
    }
}

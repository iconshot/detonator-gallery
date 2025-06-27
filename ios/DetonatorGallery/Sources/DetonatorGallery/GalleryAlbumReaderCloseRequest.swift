import Photos

import Detonator

public class GalleryAlbumReaderCloseRequest: Request {
    public override func run() -> Void {
        let data: CloseData = decodeData()!
                
        GalleryAlbumReaderReadRequest.fetchResults[data.id] = nil
        
        end()
    }
    
    public struct CloseData: Decodable {
        let id: Int
    }
}

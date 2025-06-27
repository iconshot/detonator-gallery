import Photos

import Detonator

public class GalleryMediaReaderCloseRequest: Request {
    public override func run() -> Void {
        let data: CloseData = decodeData()!
                
        GalleryMediaReaderReadRequest.fetchResults[data.id] = nil
                
        end()
    }
    
    public struct CloseData: Decodable {
        let id: Int
    }
}

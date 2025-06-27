import Photos

import Detonator

public class GalleryMediaReaderReadRequest: Request {
    public static var fetchResults: [Int: PHFetchResult<PHAsset>] = [:]
    
    public override func run() -> Void {
        let data: ReadData = decodeData()!
        
        DispatchQueue.global(qos: .userInitiated).async {
            var fetchResult = GalleryMediaReaderReadRequest.fetchResults[data.id]
            
            if fetchResult == nil {
                let fetchOptions = PHFetchOptions()
                
                fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
                
                if let albumId = data.albumId {
                    let collections = PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [albumId], options: nil)
                    
                    guard let collection = collections.firstObject else {
                        DispatchQueue.main.async {
                            self.error(message: "Unable to load collection.")
                        }
                        
                        return
                    }
                    
                    fetchResult = PHAsset.fetchAssets(in: collection, options: fetchOptions)
                } else {
                    fetchResult = PHAsset.fetchAssets(with: fetchOptions)
                }
                
                GalleryMediaReaderReadRequest.fetchResults[data.id] = fetchResult
            }
            
            guard let fetchResult = fetchResult else {
                DispatchQueue.main.async {
                    self.error(message: "Null fetch result.")
                }
                
                return
            }
            
            let start = data.offset
            let end = min(data.offset + data.limit, fetchResult.count)
                        
            var mediaItems: [GalleryMedia] = []
            
            var orderedMediaItems: [GalleryMedia?] = Array(repeating: nil, count: end - start)
            
            if start >= end {
                DispatchQueue.main.async {
                    self.end(data: mediaItems)
                }
                
                return
            }
            
            let group = DispatchGroup()
            
            for (index, i) in (start..<end).enumerated() {
                let asset = fetchResult.object(at: i)
                
                group.enter()
                
                GalleryHelper.generateThumbnail(asset: asset) { thumbnail in
                    let type: String = asset.mediaType == .video ? "video" : "image"
                    
                    let duration = asset.mediaType == .video ? asset.duration : nil
                    
                    let media = GalleryMedia(
                        id: asset.localIdentifier,
                        type: type,
                        source: "content://\(asset.localIdentifier)",
                        thumbnail: thumbnail ?? "",
                        width: asset.pixelWidth,
                        height: asset.pixelHeight,
                        rotation: nil,
                        duration: duration
                    )
                    
                    orderedMediaItems[index] = media
                    
                    group.leave()
                }
            }
            
            group.notify(queue: .main) {
                mediaItems = orderedMediaItems.compactMap { $0 }
                
                self.end(data: mediaItems)
            }
        }
    }
    
    struct GalleryMedia: Encodable {
        let id: String
        let type: String
        let source: String
        let thumbnail: String
        let width: Int
        let height: Int
        let rotation: Int?
        let duration: Double?
        
        func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            
            try container.encodeIfPresent(id, forKey: .id)
            try container.encodeIfPresent(type, forKey: .type)
            try container.encodeIfPresent(source, forKey: .source)
            try container.encodeIfPresent(thumbnail, forKey: .thumbnail)
            try container.encodeIfPresent(width, forKey: .width)
            try container.encodeIfPresent(height, forKey: .height)
            
            if rotation == nil {
                try container.encodeNil(forKey: .rotation)
            } else {
                try container.encode(rotation, forKey: .rotation)
            }
            
            if duration == nil {
                try container.encodeNil(forKey: .duration)
            } else {
                try container.encode(duration, forKey: .duration)
            }
        }

        enum CodingKeys: String, CodingKey {
            case id, type, source, thumbnail, width, height, rotation, duration
        }
    }
    
    public struct ReadData: Decodable {
        let id: Int
        let limit: Int
        let offset: Int
        let albumId: String?
    }
}

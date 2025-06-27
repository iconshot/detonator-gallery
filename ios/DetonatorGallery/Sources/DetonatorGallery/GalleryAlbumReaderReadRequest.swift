import Photos

import Detonator

public class GalleryAlbumReaderReadRequest: Request {
    public static var fetchResults: [Int: PHFetchResult<PHAssetCollection>] = [:]
    
    public override func run() -> Void {
        let data: ReadData = decodeData()!
        
        DispatchQueue.global(qos: .userInitiated).async {
            let fetchOptions = PHFetchOptions()
            
            var fetchResult = GalleryAlbumReaderReadRequest.fetchResults[data.id]
            
            if fetchResult == nil {
                fetchResult = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: nil)
                
                GalleryAlbumReaderReadRequest.fetchResults[data.id] = fetchResult
            }
            
            guard let fetchResult = fetchResult else {
                DispatchQueue.main.async {
                    self.error(message: "Null fetch result.")
                }
                
                return
            }
            
            let start = data.offset
            let end = min(data.offset + data.limit, fetchResult.count)
            
            var albums: [GalleryAlbum] = []
            
            var orderedAlbums: [GalleryAlbum?] = Array(repeating: nil, count: end - start)
            
            guard start < end else {
                DispatchQueue.main.async {
                    self.end(data: albums)
                }
                
                return
            }
            
            let group = DispatchGroup()

            for (index, i) in (start..<end).enumerated() {
                let collection = fetchResult.object(at: i)
                
                let assets = PHAsset.fetchAssets(in: collection, options: nil)
                
                guard let latestAsset = assets.lastObject else {
                    let album = GalleryAlbum(
                        id: collection.localIdentifier,
                        name: collection.localizedTitle ?? "Untitled",
                        mediaCount: assets.count,
                        thumbnail: nil
                    )
                    
                    orderedAlbums[index] = album
                    
                    continue
                }
                
                group.enter()

                GalleryHelper.generateThumbnail(asset: latestAsset) { thumbnail in
                    let album = GalleryAlbum(
                        id: collection.localIdentifier,
                        name: collection.localizedTitle ?? "Untitled",
                        mediaCount: assets.count,
                        thumbnail: thumbnail
                    )
                    
                    orderedAlbums[index] = album
                    
                    group.leave()
                }
            }
            
            group.notify(queue: .main) {
                albums = orderedAlbums.compactMap { $0 }
                
                self.end(data: albums)
            }
        }
    }
    
    struct GalleryAlbum: Encodable {
        let id: String
        let name: String
        let mediaCount: Int
        let thumbnail: String?
        
        func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            
            try container.encodeIfPresent(id, forKey: .id)
            try container.encodeIfPresent(name, forKey: .name)
            try container.encodeIfPresent(mediaCount, forKey: .mediaCount)
            
            if thumbnail == nil {
                try container.encodeNil(forKey: .thumbnail)
            } else {
                try container.encode(thumbnail, forKey: .thumbnail)
            }
        }

        enum CodingKeys: String, CodingKey {
            case id, name, mediaCount, thumbnail
        }
    }
    
    public struct ReadData: Decodable {
        let id: Int
        let limit: Int
        let offset: Int
    }
}

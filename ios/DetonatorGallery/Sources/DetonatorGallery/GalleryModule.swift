import Photos

import Detonator

public class GalleryModule: Module {
    private var albumFetchResults: [Int: PHFetchResult<PHAssetCollection>] = [:]
    private var mediaFetchResults: [Int: PHFetchResult<PHAsset>] = [:]
    
    public override func setUp() -> Void {
        detonator.setRequestListener("com.iconshot.detonator.gallery::requestPermission") { promise, value, edge in
            let status = PHPhotoLibrary.authorizationStatus()

            switch status {
            case .authorized:
                promise.resolve(true)

            case .limited:
                if #available(iOS 14, *) {
                    promise.resolve(true)
                } else {
                    promise.resolve(false)
                }

            case .notDetermined:
                PHPhotoLibrary.requestAuthorization { newStatus in
                    if newStatus == .authorized {
                        promise.resolve(true)
                    } else if #available(iOS 14, *), newStatus == .limited {
                        promise.resolve(true)
                    } else {
                        promise.resolve(false)
                    }
                }

            case .denied, .restricted:
                promise.resolve(false)

            @unknown default:
                promise.resolve(false)
            }
        }
        
        detonator.setRequestListener("com.iconshot.detonator.gallery.albumreader::read") { promise, value, edge in
            let data: AlbumReadData = self.detonator.decode(value)!
            
            DispatchQueue.global(qos: .userInitiated).async {
                let fetchOptions = PHFetchOptions()
                
                var fetchResult = self.albumFetchResults[data.id]
                
                if fetchResult == nil {
                    fetchResult = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: nil)
                    
                    self.albumFetchResults[data.id] = fetchResult
                }
                
                guard let fetchResult = fetchResult else {
                    promise.reject("Null fetch result.")
                    
                    return
                }
                
                let start = data.offset
                let end = min(data.offset + data.limit, fetchResult.count)
                
                var albums: [GalleryAlbum] = []
                
                var orderedAlbums: [GalleryAlbum?] = Array(repeating: nil, count: end - start)
                
                guard start < end else {
                    promise.resolve(albums)
                    
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
                    
                    promise.resolve(albums)
                }
            }
        }
        
        detonator.setRequestListener("com.iconshot.detonator.gallery.albumreader::close") { promise, value, edge in
            let data: AlbumCloseData = self.detonator.decode(value)!
                    
            self.albumFetchResults[data.id] = nil
            
            promise.resolve()
        }
        
        detonator.setRequestListener("com.iconshot.detonator.gallery.mediareader::read") { promise, value, edge in
            let data: MediaReadData = self.detonator.decode(value)!
            
            DispatchQueue.global(qos: .userInitiated).async {
                var fetchResult = self.mediaFetchResults[data.id]
                
                if fetchResult == nil {
                    let fetchOptions = PHFetchOptions()
                    
                    fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
                    
                    if let albumId = data.albumId {
                        let collections = PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [albumId], options: nil)
                        
                        guard let collection = collections.firstObject else {
                            promise.reject("Unable to load collection.")
                            
                            return
                        }
                        
                        fetchResult = PHAsset.fetchAssets(in: collection, options: fetchOptions)
                    } else {
                        fetchResult = PHAsset.fetchAssets(with: fetchOptions)
                    }
                    
                    self.mediaFetchResults[data.id] = fetchResult
                }
                
                guard let fetchResult = fetchResult else {
                    promise.reject("Null fetch result.")
                    
                    return
                }
                
                let start = data.offset
                let end = min(data.offset + data.limit, fetchResult.count)
                            
                var mediaItems: [GalleryMedia] = []
                
                var orderedMediaItems: [GalleryMedia?] = Array(repeating: nil, count: end - start)
                
                if start >= end {
                    promise.resolve(mediaItems)
                    
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
                    
                    promise.resolve(mediaItems)
                }
            }
        }
        
        detonator.setRequestListener("com.iconshot.detonator.gallery.mediareader::close") { promise, value, edge in
            let data: MediaCloseData = self.detonator.decode(value)!
                    
            self.mediaFetchResults[data.id] = nil
                    
            promise.resolve()
        }
    }
    
    public struct GalleryAlbum: Encodable {
        let id: String
        let name: String
        let mediaCount: Int
        let thumbnail: String?
        
        public func encode(to encoder: Encoder) throws {
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
    
    public struct AlbumReadData: Decodable {
        let id: Int
        let limit: Int
        let offset: Int
    }
    
    public struct AlbumCloseData: Decodable {
        let id: Int
    }
    
    public struct GalleryMedia: Encodable {
        let id: String
        let type: String
        let source: String
        let thumbnail: String
        let width: Int
        let height: Int
        let rotation: Int?
        let duration: Double?
        
        public func encode(to encoder: Encoder) throws {
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
    
    public struct MediaReadData: Decodable {
        let id: Int
        let limit: Int
        let offset: Int
        let albumId: String?
    }
    
    public struct MediaCloseData: Decodable {
        let id: Int
    }
}

import Foundation
import CommonCrypto
import Photos

import Detonator

public class GalleryHelper {
    public static func generateThumbnail(asset: PHAsset, completion: @escaping (String?) -> Void) {
        let fileManager = FileManager.default
        
        let cacheDirectory = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first!
        
        let directory = cacheDirectory.appendingPathComponent("com.iconshot.detonator.gallery", isDirectory: true)
        
        if !fileManager.fileExists(atPath: directory.path) {
            try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true, attributes: nil)
        }
        
        let fileName = "\(getFileName(localIdentifier: asset.localIdentifier)).png"
        
        let fileURL = directory.appendingPathComponent(fileName)
        
        let thumbnail = "file://\(fileURL.path)"
        
        if fileManager.fileExists(atPath: fileURL.path) {
            completion(thumbnail)
            
            return
        }
        
        let manager = PHImageManager.default()
        
        let options = PHImageRequestOptions()
        
        options.isSynchronous = false
        options.deliveryMode = .highQualityFormat
        options.isNetworkAccessAllowed = true
        
        let targetSize = CGSize(width: 600, height: 600)
        
        manager.requestImage(
            for: asset,
            targetSize: targetSize,
            contentMode: .aspectFit,
            options: options
        ) { image, _ in
            guard let data = image?.pngData() else {
                completion(nil)
                
                return
            }

            do {
                try data.write(to: fileURL)
                
                completion(thumbnail)
            } catch {
                completion(nil)
            }
        }
    }
    
    public static func getFileName(localIdentifier: String) -> String {
        if let data = SHA256Hash(localIdentifier) {
            let hexString = data.map { String(format: "%02hhx", $0) }.joined()
            
            return hexString
        }
        
        return (localIdentifier as NSString).lastPathComponent
    }
    
    public static func SHA256Hash(_ string: String) -> Data? {
        guard let data = string.data(using: .utf8) else {
            return nil
        }
        
        var digest = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        
        _ = data.withUnsafeBytes {
            CC_SHA256($0.baseAddress, CC_LONG(data.count), &digest)
        }
        
        return Data(digest)
    }
}

import SwiftRs
import Tauri
#if canImport(UIKit)
import UIKit
#endif
import WebKit
import KeychainSwift

class PingArgs: Decodable {
    let value: String?
}

class SecureStoragePlugin: Plugin {
    
    let kKeyOption = "prefixedKey"
    let kDataOption = "data"
    let kSyncOption = "sync"
    let kAccessOption = "access"
    let keychain = KeychainSwift()
    
    @objc func setSynchronizeKeychain(_ call: Invoke) {
        keychain.synchronizable = getSyncParam(from: call)
        call.resolve()
    }
    
    @objc func setItem(_ call: Invoke) {
        // The getters will reject the call if the param is missing
        guard let key = getKeyParam(from: call),
              let data = getDataParam(from: call) else {
            return
        }
        
        let access = getAccessParam(from: call)
        
        tryKeychainOp(call, getSyncParam(from: call)) {
            try storeData(data, withKey: key, access: access)
            call.resolve()
        }
    }
    
    @objc func getItem(_ call: Invoke)  {
        guard let key = getKeyParam(from: call) else {
            return
        }
        
        tryKeychainOp(call, getSyncParam(from: call)) {
            let data = getData(withKey: key)
            call.resolve(["data": data])
        }
    }
    
    func getKeyParam(from call: Invoke) -> String? {
        do {
            let args = try call.getArgs()
            
            if let key = args[kKeyOption] as? String,
               !key.isEmpty {
                return key
            }
            
            KeychainError.reject(call: call, kind: .missingKey)
            return nil
        } catch {
            return nil
        }
    }
    
    func getDataParam(from call: Invoke) -> String? {
        do {
            let args = try call.getArgs()
            
            if let data = args[kDataOption] as? String {
                return data
            }
            
            KeychainError.reject(call: call, kind: .invalidData)
            return nil
        } catch {
            return nil
        }
    }
    
    func getSyncParam(from call: Invoke) -> Bool {
        do {
            let args = try call.getArgs()
            if let value = args[kSyncOption] as? Bool {
                return value
            }
            
            return keychain.synchronizable
        } catch {
            return keychain.synchronizable
        }
    }
    
    func getAccessParam(from call: Invoke) -> KeychainSwiftAccessOptions? {
        do {
            let args = try call.getArgs()
            if let value = args[kAccessOption] as? Int {
                switch value {
                case 0:
                    return KeychainSwiftAccessOptions.accessibleWhenUnlocked
                    
                case 1:
                    return KeychainSwiftAccessOptions.accessibleWhenUnlockedThisDeviceOnly
                    
                case 2:
                    return KeychainSwiftAccessOptions.accessibleAfterFirstUnlock
                    
                case 3:
                    return KeychainSwiftAccessOptions.accessibleAfterFirstUnlockThisDeviceOnly
                    
                case 4:
                    return KeychainSwiftAccessOptions.accessibleWhenPasscodeSetThisDeviceOnly
                    
                default:
                    return nil
                }
            }
        } catch{
            
        }
        
        return nil
    }
    
    func tryKeychainOp(_ call: Invoke, _ sync: Bool, _ operation: () throws  -> Void) {
        var err: KeychainError?
        
        let saveSync = keychain.synchronizable
        keychain.synchronizable = sync
        
        do {
            try operation()
        } catch let error as KeychainError {
            err = error
        } catch {
            err = KeychainError(.unknownError)
        }
    }
    
    func storeData(_ data: String, withKey key: String, access: KeychainSwiftAccessOptions?) throws {
      let success = keychain.set(data, forKey: key, withAccess: access)

      if !success {
        throw KeychainError(.osError)
      }
    }

    func getData(withKey key: String) -> Any {
      keychain.get(key) as Any
    }

    func deleteData(withKey key: String) throws -> Bool {
      let success = keychain.delete(key)

      if !success && keychain.lastResultCode != 0 && keychain.lastResultCode != errSecItemNotFound {
        throw KeychainError(.osError)
      }

      return success
    }
}

@_cdecl("init_plugin_secure_storage")
func initPlugin() -> Plugin {
    return SecureStoragePlugin()
}

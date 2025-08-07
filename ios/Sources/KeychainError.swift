import Tauri

public class KeychainError: Error {
  enum ErrorKind: String {
    case missingKey
    case invalidData
    case osError
    case unknownError
  }

  private static let errorMap: [KeychainError.ErrorKind: String] = [
    .missingKey: "Empty key",
    .invalidData: "The data is in an invalid format",
    .osError: "An OS error occurred (%d)",
    .unknownError: "An unknown error occurred"
  ]

  var message: String = ""
  var code: String = ""

  init(_ kind: ErrorKind) {
    _init(kind)
  }

  private func _init(_ kind: ErrorKind) {
    if let message = KeychainError.errorMap[kind] {
      switch kind {
      case .osError:
        self.message = String(format: message)

      default:
        self.message = message
      }

      code = kind.rawValue
    }
  }

  public func rejectCall(_ call: Invoke) {
    call.reject(message)
  }

  static func reject(call: Invoke, kind: ErrorKind) {
    let err = KeychainError(kind)
    err.rejectCall(call)
  }
}

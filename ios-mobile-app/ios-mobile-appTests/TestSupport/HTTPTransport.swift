import Foundation
import Synchronization
@testable import ios_mobile_app

/// Intercepta HTTP solo dentro del target de pruebas. Nunca se enlaza con la app.
nonisolated final class TestHTTPProtocol: URLProtocol, @unchecked Sendable {
    typealias Handler = @Sendable (URLRequest) throws -> (Int, String)
    static let handlers = Mutex<[String: Handler]>([:])

    override class func canInit(with request: URLRequest) -> Bool {
        request.url?.host()?.hasSuffix(".test") == true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        do {
            let handler = Self.handlers.withLock { $0[request.url!.host()!]! }
            let (status, body) = try handler(request)
            let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil,
                                           headerFields: ["Content-Type": "application/json"])!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: Data(body.utf8))
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

nonisolated struct TestHTTPTransport {
    let configuration: AuthConfiguration
    let session: URLSession

    init(handler: @escaping TestHTTPProtocol.Handler) {
        let host = UUID().uuidString.lowercased() + ".test"
        configuration = AuthConfiguration(server: URL(string: "https://\(host)")!)
        TestHTTPProtocol.handlers.withLock { $0[host] = handler }
        let options = URLSessionConfiguration.ephemeral
        options.protocolClasses = [TestHTTPProtocol.self]
        session = URLSession(configuration: options)
    }

    func tokens(expired: Bool = false, onExpired: @escaping @Sendable () -> Void = {}) -> TokenProvider {
        TokenProvider(
            tokens: TokenSet(accessToken: "original-token", refreshToken: "refresh-token", idToken: nil,
                             expiresAt: .now.addingTimeInterval(expired ? -60 : 3600)),
            client: OIDCClient(configuration: configuration, session: session), onSessionExpired: onExpired
        )
    }

    func service() -> LiveDoggyCreditService {
        LiveDoggyCreditService(http: HTTPClient(baseURL: configuration.apiBaseURL, tokens: tokens(), session: session))
    }

    static func body(of request: URLRequest) -> Data {
        if let body = request.httpBody { return body }
        guard let stream = request.httpBodyStream else { return Data() }
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 1024)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            guard count > 0 else { break }
            data.append(contentsOf: buffer.prefix(count))
        }
        return data
    }
}

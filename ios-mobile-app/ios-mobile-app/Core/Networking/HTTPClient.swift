import Foundation

nonisolated struct APIRequest: Sendable {
    enum Method: String, Sendable {
        case get = "GET", post = "POST", put = "PUT", patch = "PATCH", delete = "DELETE"
    }

    var method: Method = .get
    var path: String
    var query: [URLQueryItem] = []
    var headers: [String: String] = [:]
    var body: Data?

    static func json(_ method: Method, _ path: String, body: some Encodable, headers: [String: String] = [:]) throws -> APIRequest {
        APIRequest(method: method, path: path, headers: headers, body: try JSONEncoder.doggyCredit.encode(body))
    }
}

/// Cliente HTTP de la API v1: añade el bearer token, traduce errores RFC 9457 y
/// decodifica fuera del hilo principal.
nonisolated struct HTTPClient: Sendable {
    let baseURL: URL
    let tokens: TokenProvider
    var session: URLSession = .doggyCredit

    @concurrent
    func send<Response: Decodable & Sendable>(_ request: APIRequest, as type: Response.Type = Response.self) async throws -> Response {
        let data = try await perform(request)
        do {
            return try JSONDecoder.doggyCredit.decode(Response.self, from: data)
        } catch {
            throw APIError.decoding
        }
    }

    @concurrent
    private func perform(_ request: APIRequest) async throws -> Data {
        do {
            let token = try await tokens.accessToken()
            var (data, response) = try await session.httpData(for: urlRequest(request, token: token))
            if response.statusCode == 401 {
                // Un 401 con token "vigente" (revocado o reloj desfasado): se renueva una sola vez.
                let renewed = try await tokens.refreshAfterRejection(rejectedToken: token)
                (data, response) = try await session.httpData(for: urlRequest(request, token: renewed))
                if response.statusCode == 401 {
                    await tokens.reject(accessToken: renewed)
                }
            }
            guard (200..<300).contains(response.statusCode) else {
                throw Self.error(status: response.statusCode, data: data)
            }
            return data
        } catch {
            throw APIError(error)
        }
    }

    private func urlRequest(_ request: APIRequest, token: String) throws -> URLRequest {
        var components = URLComponents(url: baseURL.appending(path: request.path), resolvingAgainstBaseURL: false)
        if !request.query.isEmpty { components?.queryItems = request.query }
        guard let url = components?.url else { throw URLError(.badURL) }
        var urlRequest = URLRequest(url: url)
        urlRequest.cachePolicy = .reloadIgnoringLocalCacheData
        urlRequest.httpMethod = request.method.rawValue
        urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        urlRequest.setValue("application/json, application/problem+json", forHTTPHeaderField: "Accept")
        urlRequest.setValue("no-store", forHTTPHeaderField: "Cache-Control")
        if request.body != nil {
            urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        for (name, value) in request.headers {
            urlRequest.setValue(value, forHTTPHeaderField: name)
        }
        urlRequest.httpBody = request.body
        return urlRequest
    }

    private static func error(status: Int, data: Data) -> APIError {
        if status == 401 { return .unauthenticated }
        if let problem = try? JSONDecoder.doggyCredit.decode(ProblemDetail.self, from: data) {
            return .problem(problem)
        }
        return .http(status: status)
    }
}

nonisolated extension URLSession {
    /// Los documentos, perfiles y tokens no se guardan en la caché de disco.
    static let doggyCredit: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.timeoutIntervalForRequest = 30
        configuration.timeoutIntervalForResource = 60
        return URLSession(configuration: configuration)
    }()

    func httpData(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await data(for: request, delegate: nil)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        return (data, http)
    }
}

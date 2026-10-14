import Foundation

nonisolated struct Consent: Codable, Hashable, Identifiable, Sendable {
    let id: UUID
    let holderDocument: String
    let sources: [DataSource]
    let purpose: String
    let validityDays: Int?
    let status: Status
    let createdAt: Date?
    let expiresAt: Date?
    /// Solo si el estado es REVOCADO.
    let revokedAt: Date?

    enum Status: String, Codable, Sendable {
        case active = "ACTIVO"
        case revoked = "REVOCADO"
        case expired = "EXPIRADO"
    }

    enum CodingKeys: String, CodingKey {
        case id
        case holderDocument = "titularDocumento"
        case sources = "fuentes"
        case purpose = "finalidad"
        case validityDays = "vigenciaDias"
        case status = "estado"
        case createdAt = "creadoEn"
        case expiresAt = "expiraEn"
        case revokedAt = "revocadoEn"
    }
}

nonisolated struct NewConsent: Encodable, Sendable {
    let holderDocument: String
    let sources: [DataSource]
    let purpose: String
    let validityDays: Int

    enum CodingKeys: String, CodingKey {
        case holderDocument = "titularDocumento"
        case sources = "fuentes"
        case purpose = "finalidad"
        case validityDays = "vigenciaDias"
    }
}

nonisolated struct Integration: Codable, Hashable, Identifiable, Sendable {
    let source: IntegrationSource
    let status: IntegrationStatus

    var id: IntegrationSource { source }

    enum CodingKeys: String, CodingKey {
        case source = "fuente"
        case status = "estado"
    }
}

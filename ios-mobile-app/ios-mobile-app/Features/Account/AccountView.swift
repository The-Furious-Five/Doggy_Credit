import SwiftUI

/// Cuenta: quién soy, mi institución, mi plan y la seguridad de la app.
struct AccountView: View {
    @Environment(AppModel.self) private var app
    @Environment(SessionStore.self) private var session
    @Environment(\.dismiss) private var dismiss
    @State private var isConfirmingSignOut = false
    @State private var isSigningOut = false

    var body: some View {
        NavigationStack {
            List {
                profileSection
                institutionSection
                usageSection
                securitySection
                consoleSection
                aboutSection
                Section {
                    Button(role: .destructive) {
                        isConfirmingSignOut = true
                    } label: {
                        HStack {
                            Text(isSigningOut ? "Cerrando sesión…" : "Cerrar sesión")
                            Spacer()
                            if isSigningOut { ProgressView() }
                        }
                    }
                    .disabled(isSigningOut)
                }
            }
            .navigationTitle("Cuenta")
            .refreshable { await session.bootstrap() }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    SheetCloseButton { dismiss() }
                }
            }
            .confirmationDialog("¿Cerrar sesión?", isPresented: $isConfirmingSignOut, titleVisibility: .visible) {
                Button("Cerrar sesión", role: .destructive) {
                    Task {
                        isSigningOut = true
                        await app.signOut()
                    }
                }
            } message: {
                Text(session.evaluation.isInProgress
                     ? "La evaluación en curso se descarta. Lo ya registrado se conserva en el servidor."
                     : "Para volver a entrar tendrás que iniciar sesión con tu institución.")
            }
        }
    }

    private var profileSection: some View {
        Section {
            HStack(spacing: 14) {
                AvatarView(initials: session.user.initials, size: 56)
                VStack(alignment: .leading, spacing: 3) {
                    Text(session.user.name)
                        .font(.title3.weight(.semibold))
                    Text(session.user.roles.first?.label ?? "Sin rol asignado")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    if !session.user.username.isEmpty {
                        Text(session.user.username)
                            .font(.caption)
                            .fontDesign(.monospaced)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding(.vertical, 6)
            .accessibilityElement(children: .combine)
        }
    }

    @ViewBuilder
    private var institutionSection: some View {
        Section("Institución") {
            if let tenant = session.tenant {
                LabeledContent("Nombre", value: tenant.name)
                LabeledContent("Plan", value: tenant.plan.name)
            } else {
                Text(session.tenantError?.message ?? "Cargando institución…").foregroundStyle(.secondary)
                Button("Reintentar") { Task { await session.refreshTenant() } }
            }
        }
    }

    @ViewBuilder
    private var usageSection: some View {
        if let usage = session.usage {
            Section {
                VStack(alignment: .leading, spacing: 10) {
                    LabeledContent("Evaluaciones del mes") {
                        Text(usage.fractionUsed.map { Format.percent($0 * 100) } ?? "Sin límite")
                    }
                    if let fraction = usage.fractionUsed {
                        ProgressView(value: fraction)
                            .tint(usage.level == .ok ? Color.accentColor : (usage.level == .warning ? Tone.warning.color : Tone.danger.color))
                            .accessibilityHidden(true)
                    }
                    Text(usage.summary)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 2)
            } header: {
                Text("Uso del plan")
            } footer: {
                if !usage.isUnlimited {
                    Text("Al llegar a \(Format.number(usage.monthlyLimit)) se pausan las nuevas evaluaciones hasta el \(Format.nextMonthStart()).")
                }
            }
        } else if let error = session.usageError {
            Section("Uso del plan") {
                Text(error.message).foregroundStyle(.secondary)
                Button("Reintentar") { Task { await session.refreshUsage() } }
            }
        }
    }

    private var securitySection: some View {
        Section {
            Toggle(isOn: Binding(
                get: { app.lock.isEnabled },
                set: { enabled in Task { await app.lock.setEnabled(enabled) } }
            )) {
                Label("Bloquear con \(app.lock.methodName)", systemImage: app.lock.methodSymbol)
            }
            .disabled(!app.lock.isAvailable)
            LabeledContent {
                Text(session.privacy.shouldMask ? "Enmascarados" : "Visibles")
            } label: {
                Label("Documentos y montos", systemImage: session.privacy.shouldMask ? "eye.slash" : "eye")
            }
        } header: {
            Text("Seguridad y privacidad")
        } footer: {
            Text(securityFooter)
            if let error = session.preferencesError {
                Text("No pudimos cargar las preferencias de privacidad. \(error.message)")
                Button("Reintentar") { Task { await session.refreshPreferences() } }
            }
        }
    }

    private var securityFooter: String {
        var text = app.lock.isAvailable
            ? "Al volver a la app se pide \(app.lock.methodName). El contenido se oculta en el selector de apps."
            : "Configura Face ID o un código en tu iPhone para proteger la app."
        text += session.privacy.shouldMask
            ? " Tu institución enmascara los datos sensibles para tu rol."
            : " Tu rol ve los datos completos: evita mostrarlos en lugares públicos."
        return text
    }

    private var consoleSection: some View {
        Section {
            Link(destination: session.webConsoleURL) {
                Label("Abrir consola web", systemImage: "macbook.and.iphone")
            }
        } footer: {
            Text("Productos, políticas, integraciones, claves de API, webhooks y equipo se administran desde la consola web.")
        }
    }

    private var aboutSection: some View {
        Section("Acerca de") {
            LabeledContent("Entorno", value: session.environmentDescription)
            LabeledContent("Versión", value: Bundle.main.versionDescription)
        }
    }
}

private extension Bundle {
    var versionDescription: String {
        let version = infoDictionary?["CFBundleShortVersionString"] as? String ?? "—"
        let build = infoDictionary?["CFBundleVersion"] as? String ?? "—"
        return "\(version) (\(build))"
    }
}

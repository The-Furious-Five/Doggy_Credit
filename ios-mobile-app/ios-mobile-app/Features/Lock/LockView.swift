import SwiftUI

/// Bloqueo al volver a la app con la protección activada.
struct LockView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        ZStack {
            Color.brandSurface.ignoresSafeArea()
            VStack(spacing: 20) {
                Spacer()
                BrandMark(size: 88)
                VStack(spacing: 6) {
                    Text("DoggyCredit está bloqueado")
                        .font(.title2.bold())
                        .foregroundStyle(Brand.ivory)
                    Text("Protege los datos financieros de tus clientes.")
                        .font(.subheadline)
                        .foregroundStyle(Brand.ivory.opacity(0.75))
                }
                .multilineTextAlignment(.center)
                Spacer()
                Button {
                    Task { await app.lock.unlock() }
                } label: {
                    Label("Desbloquear con \(app.lock.methodName)", systemImage: app.lock.methodSymbol)
                        .font(.headline)
                        .foregroundStyle(Brand.pine)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .dcProminentButtonStyle()
                .tint(Brand.ivory)
                .controlSize(.large)
                .disabled(app.lock.isAuthenticating)

                Button("Cerrar sesión", role: .destructive) {
                    Task { await app.signOut() }
                }
                .foregroundStyle(Brand.ivory.opacity(0.8))
                .font(.footnote)
            }
            .padding(24)
            .frame(maxWidth: 480)
        }
        .environment(\.colorScheme, .dark)
    }
}

import SwiftUI

/// Emblema sobre placa marfil, el icono de aplicación de la marca (`ds-logo`).
struct BrandMark: View {
    var size: CGFloat = 96

    var body: some View {
        Image(.brandMark)
            .resizable()
            .scaledToFit()
            .padding(size * 0.08)
            .frame(width: size, height: size)
            .background(Brand.ivory, in: .rect(cornerRadius: size * 0.24, style: .continuous))
            .accessibilityHidden(true)
    }
}

/// Wordmark en los colores del logo: "Doggy" en pino (marfil sobre fondos oscuros) y "Credit" en miel.
struct Wordmark: View {
    var size: CGFloat = 28
    var onBrandSurface = false

    var body: some View {
        let doggy = Text("Doggy").foregroundStyle(onBrandSurface ? Brand.ivory : Brand.pine)
        let credit = Text("Credit").foregroundStyle(Brand.honey)
        Text("\(doggy)\(credit)")
            .font(.system(size: size, weight: .heavy, design: .rounded))
            .accessibilityLabel("DoggyCredit")
    }
}

/// Iniciales de la persona en marfil sobre el pino de la marca, para el botón de cuenta.
struct AvatarView: View {
    let initials: String
    var size: CGFloat = 34

    var body: some View {
        Text(initials)
            .font(.system(size: size * 0.38, weight: .semibold, design: .rounded))
            .foregroundStyle(Brand.ivory)
            .frame(width: size, height: size)
            .background(Brand.pine, in: Circle())
            .accessibilityHidden(true)
    }
}

#Preview {
    VStack(spacing: 20) {
        BrandMark(size: 120)
        Wordmark()
        AvatarView(initials: "MQ", size: 44)
    }
    .padding()
}
